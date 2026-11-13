#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────────────
# scripts/db/restore.sh — rebuild the local database from the final-database
# snapshot instead of replaying every migration.
#
#   scripts/db/restore.sh                    # from the repo: src/main/resources/db/snapshot/
#   scripts/db/restore.sh --release <tag>    # from a GitHub release asset (optibienestar360-<tag>.dump)
#   scripts/db/restore.sh --file <dump>      # from a local pg_dump -Fc file
#   ... --seed scripts/dev-seed-X.sql        # then apply dev seeds, in the order given (repeatable)
#   ... --yes                                # do not ask before dropping the database
#
# What it does (as the postgres superuser of your local server):
#   1. DROPS and recreates the local database (DATABASE_NAME, default optibienestar360)
#   2. runs scripts/init-db-roles.sql (cluster roles are not part of a dump)
#   3. loads the snapshot: schema (extensions, schema app, grants) + seeded data
#      + flyway_schema_history
#   4. optionally applies dev seed scripts
# Then `./gradlew bootRun` lets Flyway apply only the migrations newer than the snapshot.
#
# search_path is forced to `app, public` while loading: app.immutable_unaccent()
# calls unaccent() unqualified, and pg_restore/pg_dump otherwise empty search_path,
# which breaks the trigram indexes built on it.
#
# Connection: DATABASE_HOST / DATABASE_PORT / DATABASE_NAME from the environment
# or the repo's .env; superuser via PGSUPERUSER (default postgres) + PGPASSWORD.
# Client tools run from the pinned postgres:15-alpine image (no local psql needed).
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail

PG_IMAGE="${PG_IMAGE:-postgres:15-alpine}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SNAPSHOT_DIR="src/main/resources/db/snapshot"
TAG="" FILE="" YES=0
SEEDS=()

while [ $# -gt 0 ]; do
    case "$1" in
        --release) TAG="$2"; shift ;;
        --file)    FILE="$2"; shift ;;
        --seed)    SEEDS+=("$2"); shift ;;
        --yes|-y)  YES=1 ;;
        -h|--help) sed -n '2,27p' "$0"; exit 0 ;;
        *)         echo "Unknown option: $1 (see --help)" >&2; exit 2 ;;
    esac
    shift
done

# .env values (same keys the backend uses), without overriding explicit env vars.
if [ -f "$ROOT/.env" ]; then
    while IFS='=' read -r k v; do
        v="${v%\"}"; v="${v#\"}"
        [ -z "${!k:-}" ] && export "$k=$v"
    done < <(grep -E '^(DATABASE_HOST|DATABASE_PORT|DATABASE_NAME)=' "$ROOT/.env")
fi
export PGHOST="${DATABASE_HOST:-localhost}" PGPORT="${DATABASE_PORT:-5432}"
export PGUSER="${PGSUPERUSER:-postgres}" PGPASSWORD="${PGPASSWORD:-postgres}"
DB="${DATABASE_NAME:-optibienestar360}"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if [ -n "$TAG" ]; then
    echo "▶ Downloading optibienestar360-$TAG.dump"
    gh release download "$TAG" --pattern "optibienestar360-$TAG.dump" --dir "$WORK" \
        --repo "$(cd "$ROOT" && gh repo view --json nameWithOwner -q .nameWithOwner)"
    FILE="$WORK/optibienestar360-$TAG.dump"
fi
if [ -n "$FILE" ]; then
    [ -s "$FILE" ] || { echo "Dump not found: $FILE" >&2; exit 1; }
    cp "$FILE" "$WORK/release.dump"
else
    for f in schema.sql data.sql; do
        [ -s "$ROOT/$SNAPSHOT_DIR/$f" ] || { echo "Missing $SNAPSHOT_DIR/$f (run scripts/db/snapshot.sh --fresh)" >&2; exit 1; }
    done
fi

pgc() {
    docker run --rm -i --network host -e PGHOST -e PGPORT -e PGUSER -e PGPASSWORD \
        -e PGOPTIONS="-c search_path=app,public" \
        -v "$ROOT:/repo:ro" -v "$WORK:/work:ro" -w /repo "$PG_IMAGE" "$@"
}

if [ "$YES" != 1 ]; then
    read -r -p "This DROPS database '$DB' on $PGHOST:$PGPORT. Continue? [y/N] " ans
    [ "$ans" = "y" ] || [ "$ans" = "Y" ] || { echo "Aborted."; exit 1; }
fi

echo "▶ Recreating database $DB"
pgc psql -X -q -v ON_ERROR_STOP=1 -d postgres \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB' AND pid <> pg_backend_pid();" \
    -c "DROP DATABASE IF EXISTS \"$DB\";" -c "CREATE DATABASE \"$DB\";" >/dev/null

echo "▶ Creating roles (scripts/init-db-roles.sql)"
pgc psql -X -q -v ON_ERROR_STOP=1 -d "$DB" -f scripts/init-db-roles.sql >/dev/null

if [ -n "$FILE" ]; then
    echo "▶ Restoring $(basename "$FILE")"
    # Render to SQL so the search_path that pg_restore empties can be set back.
    pgc sh -c "pg_restore --no-owner -f - /work/release.dump \
        | sed \"s/set_config('search_path', '', false)/set_config('search_path', 'app, public', false)/\" \
        | psql -X -q -v ON_ERROR_STOP=1 -d '$DB'" >/dev/null
else
    echo "▶ Loading $SNAPSHOT_DIR/schema.sql + data.sql"
    pgc psql -X -q -v ON_ERROR_STOP=1 -d "$DB" -1 -f "$SNAPSHOT_DIR/schema.sql" -f "$SNAPSHOT_DIR/data.sql" >/dev/null
fi

for f in "${SEEDS[@]}"; do
    rel="$(realpath --relative-to="$ROOT" "$f")"
    case "$rel" in ../*) echo "Seed must live inside the repo: $f" >&2; exit 1 ;; esac
    echo "▶ Seed $rel"
    pgc psql -X -q -v ON_ERROR_STOP=1 -d "$DB" -f "$rel" >/dev/null
done

echo "✔ $DB restored. Run ./gradlew bootRun (or flywayMigrate) to apply migrations newer than the snapshot."
