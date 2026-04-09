package com.booking.platform.user_service.scheduler;

import com.booking.platform.user_service.entity.UserEntity;
import com.booking.platform.user_service.properties.KeycloakProperties;
import com.booking.platform.user_service.repository.UserRepository;
import com.booking.platform.user_service.service.KeycloakUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled job that deletes unverified user accounts older than the
 * configured retention period.
 *
 * <p>Runs on the cron defined by {@code cleanup.unverified.scheduler.cron}
 * (default: daily at 02:00). Any user whose {@code emailVerified} is still
 * {@code false} and whose {@code createdTimestamp} is older than
 * {@code cleanup.unverified.retention-days} days is permanently deleted from
 * Keycloak.
 *
 * <p>Failures for individual users are logged and skipped so one bad record
 * does not block the rest of the batch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnverifiedUserCleanupScheduler {

    private final UserRepository userRepository;
    private final KeycloakUserService keycloakUserService;
    private final KeycloakProperties keycloakProperties;

    @Value("${cleanup.unverified.retention-days:7}")
    private int retentionDays;

    @Scheduled(cron = "${cleanup.unverified.scheduler.cron:0 0 2 * * *}")
    public void deleteUnverifiedUsers() {
        long cutoffMillis = Instant.now()
                .minus(retentionDays, ChronoUnit.DAYS)
                .toEpochMilli();

        List<UserEntity> staleUsers = userRepository.findUnverifiedUsersOlderThan(
                cutoffMillis, keycloakProperties.realm());

        if (staleUsers.isEmpty()) {
            log.debug("Unverified user cleanup: no stale accounts found");
            return;
        }

        log.info("Unverified user cleanup: deleting {} account(s) older than {} day(s)",
                staleUsers.size(), retentionDays);

        for (UserEntity user : staleUsers) {
            try {
                keycloakUserService.deleteUser(user.getId());
                log.info("Deleted unverified user: id='{}', email='{}'",
                        user.getId(), user.getEmail());
            } catch (Exception e) {
                log.error("Failed to delete unverified user id='{}': {}",
                        user.getId(), e.getMessage());
            }
        }
    }
}
