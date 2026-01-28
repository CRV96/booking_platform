#!/bin/bash
# Creates multiple databases from POSTGRES_MULTIPLE_DATABASES env var

set -e
set -u

function create_database() {
    local database=$1
    echo "Creating database '$database'..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        SELECT 'CREATE DATABASE $database'
        WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$database')\gexec
        GRANT ALL PRIVILEGES ON DATABASE $database TO $POSTGRES_USER;
EOSQL
    echo "Database '$database' created successfully."
}

if [ -n "$POSTGRES_MULTIPLE_DATABASES" ]; then
    echo "============================================"
    echo "Creating multiple databases..."
    echo "============================================"

    for db in $(echo $POSTGRES_MULTIPLE_DATABASES | tr ',' ' '); do
        create_database $db
    done

    echo "============================================"
    echo "All databases created successfully!"
    echo "============================================"
fi
