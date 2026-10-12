#!/bin/bash

# show errors and exit on error
set -euo pipefail;


# Dev profile + "fast path" DB/Redis credentials from .ai/playbooks/local-build.md
# (a plain postgres:15-alpine/redis:7-alpine superuser setup, same as ci.yaml's
# ephemeral Postgres service). ${VAR:=default} only sets the var if it isn't
# already exported in your shell, so your own setup (database-stack roles,
# different host/port) is never overridden.
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:=dev}";
export DATABASE_USER="${DATABASE_USER:=postgres}";
export DATABASE_PASSWORD="${DATABASE_PASSWORD:=postgres}";
export DATABASE_MIGRATION_USER="${DATABASE_MIGRATION_USER:=postgres}";
export DATABASE_MIGRATION_PASSWORD="${DATABASE_MIGRATION_PASSWORD:=postgres}";

# See .ai/playbooks/local-build.md for how to get Postgres/Redis running locally.
./gradlew bootRun --args="--spring.profiles.active=${SPRING_PROFILES_ACTIVE}";
