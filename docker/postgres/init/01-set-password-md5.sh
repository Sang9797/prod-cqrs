#!/bin/bash
# Re-set the user password within a session that explicitly uses md5 encryption.
# The postgres Docker entrypoint creates the user with whatever the default
# password_encryption is (scram-sha-256 in postgres 14+), so we override it here.
# PgBouncer requires md5 because its userlist.txt holds an md5 hash and cannot
# compute a scram-sha-256 response without the plaintext password.
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SET password_encryption = 'md5';
    ALTER USER "$POSTGRES_USER" WITH PASSWORD '$POSTGRES_PASSWORD';
EOSQL
