#!/usr/bin/env bash
# =============================================================================
# Start all Spring Boot services sequentially in a tmux session.
#
# Each service gets its own tmux window and is launched via ./run-service.sh.
# The script waits for each service's Actuator health endpoint to report "UP"
# before starting the next one, guaranteeing the correct boot order:
#   config-service -> eureka-service -> everything else -> graphql-gateway
#
# Usage:
#   ./start-all.sh                 # start everything, then attach to the session
#   ./start-all.sh --debug         # run every service with remote debugging on
#   ./start-all.sh --no-attach     # start everything, leave session detached
#   ./start-all.sh --kill          # kill an existing session and start fresh
#   ./start-all.sh --session foo   # use a custom tmux session name (default: bkg)
#   ./start-all.sh --help
#
# In --debug mode each service exposes its own JDWP port (see run-service.sh):
#   config-service 5005  eureka 5006  gateway 5007  user 5008  event 5009
#   booking 5010  payment 5011  ticket 5012  notification 5013  analytics 5014
#
# Note: start the infrastructure first (see INSTALLATION.md step 2):
#   docker compose -f infrastructure/docker/docker-compose.startup.yaml up -d
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ─── Colors ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# ─── Config ──────────────────────────────────────────────────────────────────
SESSION="bkg"
ATTACH=true
KILL_EXISTING=false
DEBUG_MODE=false
HEALTH_TIMEOUT=180   # seconds to wait per service before giving up
POLL_INTERVAL=3      # seconds between health checks

# Ordered list of "service-name:health-port". Order matters: config and eureka
# must come first; graphql-gateway comes last (it depends on the others).
SERVICES=(
    "config-service:8888"
    "eureka-service:8761"
    "user-service:8081"
    "event-service:8082"
    "booking-service:8083"
    "payment-service:8084"
    "notification-service:8086"
    "analytics-service:8087"
    "ticket-service:8088"
    "graphql-gateway:8080"
)

# ─── Args ────────────────────────────────────────────────────────────────────
show_usage() {
    echo "Usage: ./start-all.sh [options]"
    echo ""
    echo "Options:"
    echo "  --debug            Run every service with remote debugging enabled"
    echo "                     (each gets a unique JDWP port, 5005-5014)"
    echo "  --no-attach        Start services but do not attach to the tmux session"
    echo "  --kill             Kill an existing session with the same name first"
    echo "  --session <name>   tmux session name (default: bkg)"
    echo "  --timeout <secs>   Health-check timeout per service (default: 180)"
    echo "  --help, -h         Show this help"
    echo ""
    echo "Services are started in this order, each waiting for the previous"
    echo "to report health UP before launching:"
    for entry in "${SERVICES[@]}"; do
        echo "  ${entry%%:*}"
    done
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --debug|-d)      DEBUG_MODE=true; shift ;;
        --no-attach)     ATTACH=false; shift ;;
        --kill)          KILL_EXISTING=true; shift ;;
        --session)       SESSION="$2"; shift 2 ;;
        --timeout)       HEALTH_TIMEOUT="$2"; shift 2 ;;
        --help|-h)       show_usage; exit 0 ;;
        *)               echo -e "${RED}Unknown option: $1${NC}"; show_usage; exit 1 ;;
    esac
done

# ─── Preflight ───────────────────────────────────────────────────────────────
command -v tmux >/dev/null 2>&1 || { echo -e "${RED}tmux is not installed. Install it (brew install tmux) and retry.${NC}"; exit 1; }
command -v curl >/dev/null 2>&1 || { echo -e "${RED}curl is not installed.${NC}"; exit 1; }

if tmux has-session -t "$SESSION" 2>/dev/null; then
    if [ "$KILL_EXISTING" = true ]; then
        echo -e "${YELLOW}Killing existing tmux session '$SESSION'...${NC}"
        tmux kill-session -t "$SESSION"
    else
        echo -e "${RED}A tmux session named '$SESSION' already exists.${NC}"
        echo -e "Attach with: ${CYAN}tmux attach -t $SESSION${NC}"
        echo -e "Or start fresh with: ${CYAN}./start-all.sh --kill${NC}"
        exit 1
    fi
fi

# ─── Health check ────────────────────────────────────────────────────────────
# Poll <host>:<port>/actuator/health until it reports "UP" or the timeout hits.
wait_for_health() {
    local name="$1" port="$2"
    local url="http://localhost:${port}/actuator/health"
    local elapsed=0

    echo -e "${CYAN}Waiting for ${name} to become healthy (${url})...${NC}"
    while (( elapsed < HEALTH_TIMEOUT )); do
        if curl -sf --max-time 2 "$url" 2>/dev/null | grep -q '"status":"UP"'; then
            echo -e "${GREEN}✓ ${name} is UP (after ${elapsed}s)${NC}"
            return 0
        fi
        sleep "$POLL_INTERVAL"
        elapsed=$(( elapsed + POLL_INTERVAL ))
        printf '.'
    done

    echo ""
    echo -e "${RED}✗ ${name} did not become healthy within ${HEALTH_TIMEOUT}s.${NC}"
    echo -e "${YELLOW}  Check its logs:  tmux attach -t ${SESSION} \\; select-window -t ${name}${NC}"
    return 1
}

# ─── Launch ──────────────────────────────────────────────────────────────────
echo -e "${GREEN}Creating tmux session '${SESSION}'...${NC}"
# First service seeds the session (created detached).
first_entry="${SERVICES[0]}"
first_name="${first_entry%%:*}"
tmux new-session -d -s "$SESSION" -n "$first_name" -c "$SCRIPT_DIR"

for i in "${!SERVICES[@]}"; do
    entry="${SERVICES[$i]}"
    name="${entry%%:*}"
    port="${entry##*:}"

    if (( i == 0 )); then
        # Window already exists (created with the session).
        window="$SESSION:$name"
    else
        tmux new-window -t "$SESSION" -n "$name" -c "$SCRIPT_DIR"
        window="$SESSION:$name"
    fi

    run_cmd="./run-service.sh ${name}"
    if [ "$DEBUG_MODE" = true ]; then
        run_cmd="${run_cmd} --debug"
        echo -e "${YELLOW}▶ Starting ${name} (port ${port}, debug enabled)...${NC}"
    else
        echo -e "${YELLOW}▶ Starting ${name} (port ${port})...${NC}"
    fi
    tmux send-keys -t "$window" "${run_cmd}" C-m

    if ! wait_for_health "$name" "$port"; then
        echo -e "${RED}Aborting: ${name} failed to start. Remaining services were not launched.${NC}"
        echo -e "Inspect the session with: ${CYAN}tmux attach -t ${SESSION}${NC}"
        exit 1
    fi
done

echo ""
echo -e "${GREEN}All services started in tmux session '${SESSION}'.${NC}"
echo -e "Switch windows with ${CYAN}Ctrl-b n${NC} / ${CYAN}Ctrl-b p${NC}, or ${CYAN}Ctrl-b w${NC} to list them."
echo ""

if [ "$ATTACH" = true ]; then
    tmux select-window -t "$SESSION:$first_name"
    tmux attach -t "$SESSION"
else
    echo -e "Attach any time with: ${CYAN}tmux attach -t ${SESSION}${NC}"
fi
