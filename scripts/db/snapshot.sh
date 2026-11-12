#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────────────
# scripts/db/snapshot.sh — regenerate the final-database snapshot in
# src/main/resources/db/snapshot/ from the migrations:
#
#   schema.sql        DDL of the final database (extensions, schema app, grants,
#                     flyway_schema_history table) — importable with psql
#   data.sql          rows the migrations seed + flyway_schema_history, normalized
#                     so it only changes when a migration changes data
#   schema-map.md     tables/columns/FKs/indexes/relationships, for humans + AI
#   seed-catalog.md   row counts and catalog codes seeded by the migrations
#
# It is the FINAL state: a renamed table/column appears only with its new name.
# Import it without running every migration: scripts/db/restore.sh
#
# Usage:
#   scripts/db/snapshot.sh --fresh       # ephemeral Postgres + all migrations + generate (local)
#   scripts/db/snapshot.sh --migrate     # same, on the EMPTY database given by PG* vars (CI)
#   ... --dump FILE                       # also write pg_dump -Fc of that database (release asset)
#   ... --out DIR                         # output directory (default: src/main/resources/db/snapshot)
#
# --migrate refuses to run on a database that already has the `app` schema: the
# normalization step rewrites uuids/timestamps and must never touch real data.
#
# Everything runs inside pinned images (postgres:15-alpine, flyway/flyway), so
# the output is byte-identical locally and in CI, and the repo's .env is ignored.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail

PG_IMAGE="${PG_IMAGE:-postgres:15-alpine}"
FLYWAY_IMAGE="${FLYWAY_IMAGE:-flyway/flyway:11.7.2-alpine}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/src/main/resources/db/snapshot"
FRESH=0 MIGRATE=0 DUMP=""

while [ $# -gt 0 ]; do
    case "$1" in
        --fresh)   FRESH=1; MIGRATE=1 ;;
        --migrate) MIGRATE=1 ;;
        --dump)    DUMP="$2"; shift ;;
        --out)     OUT="$2"; shift ;;
        -h|--help) sed -n '2,28p' "$0"; exit 0 ;;
        *) echo "Unknown option: $1" >&2; exit 2 ;;
    esac
    shift
done
[ "$MIGRATE" = 1 ] || { echo "Use --fresh (local) or --migrate (empty PG* database). See --help." >&2; exit 2; }

CONTAINER=""
cleanup() { [ -n "$CONTAINER" ] && docker rm -f "$CONTAINER" >/dev/null 2>&1 || true; }
trap cleanup EXIT

if [ "$FRESH" = 1 ]; then
    export PGHOST=127.0.0.1 PGPORT="${SNAPSHOT_PG_PORT:-55499}" PGUSER=postgres PGPASSWORD=postgres PGDATABASE=optibienestar360
    CONTAINER="db-snapshot-$$"
    echo "▶ Starting ephemeral $PG_IMAGE on port $PGPORT"
    docker run -d --rm --name "$CONTAINER" -p "$PGPORT:5432" \
        -e POSTGRES_PASSWORD="$PGPASSWORD" -e POSTGRES_DB="$PGDATABASE" "$PG_IMAGE" >/dev/null
    for _ in $(seq 1 60); do
        docker exec "$CONTAINER" pg_isready -U postgres -d "$PGDATABASE" >/dev/null 2>&1 && break
        sleep 1
    done
    sleep 1
fi

: "${PGHOST:?PGHOST is required}" "${PGPORT:=5432}" "${PGUSER:?PGUSER is required}" "${PGDATABASE:?PGDATABASE is required}"
export PGPASSWORD="${PGPASSWORD:-}"

# Run a Postgres client tool from the pinned image against the PG* database.
pgc() {
    docker run --rm -i --network host \
        -e PGHOST -e PGPORT -e PGUSER -e PGPASSWORD -e PGDATABASE \
        -v "$ROOT:/repo:ro" -w /repo "$PG_IMAGE" "$@"
}

# Drop pg_dump lines that vary per run or per client and carry no schema/data,
# then canonicalize the spacing between entries, which is not stable across
# environments (CI produced one blank line fewer than local): runs of blank
# lines are squeezed to one, and exactly one blank line is guaranteed before
# each "--" comment header that follows a statement. Blank lines inside
# function bodies/comments are kept (only squeezed); COPY data blocks are
# copied verbatim (a row can be an empty line).
strip_noise() {
    grep -vE '^(-- Dumped (from|by)|SET |SELECT pg_catalog\.set_config|\\(un)?restrict )' \
        | awk '
            incopy { print; if ($0 == "\\.") incopy = 0; prev = $0; next }
            /^COPY .* FROM stdin;$/ { print; incopy = 1; prev = $0; next }
            $0 == "" { if (prev != "") print; prev = $0; next }
            $0 == "--" && prev != "" && prev !~ /^--/ { print "" }
            { print; prev = $0 }
          '
}

if [ "$(pgc psql -X -At -c "SELECT count(*) FROM pg_namespace WHERE nspname = 'app'")" != "0" ]; then
    echo "Refusing to run: database $PGDATABASE on $PGHOST:$PGPORT already has schema 'app'." >&2
    echo "The snapshot must be built from an EMPTY database (use --fresh locally)." >&2
    exit 1
fi

echo "▶ Applying migrations with $FLYWAY_IMAGE"
# Same settings as build.gradle `flyway {}` / application.properties. Placeholder
# values are dev defaults: they only end up in role attributes (V3) and seed
# flags, never in what this snapshot prints.
docker run --rm --network host \
    -v "$ROOT/src/main/resources/db/migration:/flyway/sql:ro" "$FLYWAY_IMAGE" \
    -url="jdbc:postgresql://$PGHOST:$PGPORT/$PGDATABASE" -user="$PGUSER" -password="$PGPASSWORD" \
    -schemas=app -defaultSchema=app -createSchemas=true \
    -outOfOrder=true -validateOnMigrate=true -cleanDisabled=true -connectRetries=10 \
    -placeholders.migration_db_password=changeme-dev \
    -placeholders.app_db_password=changeme-dev \
    -placeholders.readonly_db_password=changeme-dev \
    -placeholders.app_conn_limit=50 \
    -placeholders.migration_conn_limit=5 \
    -placeholders.readonly_conn_limit=10 \
    -placeholders.app_statement_timeout=30s \
    -placeholders.app_idle_in_transaction_timeout=60s \
    -placeholders.seed_password_never_expires=false \
    -placeholders.app_timezone=America/Caracas \
    -outputType=json migrate >/dev/null

echo "▶ Normalizing seeded data (scripts/db/normalize.sql)"
pgc psql -X -q -v ON_ERROR_STOP=1 -f scripts/db/normalize.sql >/dev/null

mkdir -p "$OUT"

echo "▶ Writing $OUT/schema.sql"
# Whole database, not `-n app`: the extensions live in schema app and a
# schema-filtered dump would leave them out (the import would then fail).
pgc pg_dump --schema-only --no-owner --no-tablespaces | strip_noise > "$OUT/schema.sql"

echo "▶ Writing $OUT/data.sql"
pgc pg_dump --data-only --disable-triggers --no-owner --schema=app 2>/dev/null | strip_noise > "$OUT/data.sql"

echo "▶ Writing $OUT/schema-map.md"
pgc psql -X -q -At -v ON_ERROR_STOP=1 -f scripts/db/schema-map.sql > "$OUT/schema-map.md"

echo "▶ Writing $OUT/seed-catalog.md"
pgc psql -X -q -At -v ON_ERROR_STOP=1 -f scripts/db/seed-catalog.sql > "$OUT/seed-catalog.md"

if [ -n "$DUMP" ]; then
    echo "▶ Writing $DUMP (pg_dump -Fc of the final database)"
    mkdir -p "$(dirname "$DUMP")"
    pgc pg_dump -Fc --no-owner > "$DUMP"
fi

echo "✔ Snapshot done"
