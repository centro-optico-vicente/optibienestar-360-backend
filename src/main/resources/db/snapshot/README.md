# `db/snapshot/` — final database after every migration (generated)

These files show what the database looks like after applying every migration in
[`../migration/`](../migration/). **Read them instead of replaying the
migrations one by one**, and use them to import the database without running
each SQL file.

| File | What it is |
|---|---|
| [`schema-map.md`](schema-map.md) | Every table: columns (type, null, default, comment), PK, FKs, UNIQUE, CHECK (enum values), indexes (with partial predicates), triggers and "referenced by". It also has a flat **child → parent relationship list**, views and functions. Start here; search it by `## <table>`. |
| [`schema.sql`](schema.sql) | DDL of the final database: extensions, schema `app`, grants and the `flyway_schema_history` table. Loads with `psql`. |
| [`data.sql`](data.sql) | Rows the migrations seed (catalogs, roles, permissions, plans, seeded allies…) plus `flyway_schema_history`. |
| [`seed-catalog.md`](seed-catalog.md) | Row counts per table and the codes/names of small catalogs. |

This is the **final state**, not the history. If a migration does
`ALTER TABLE … RENAME`, only the new name appears here.

## The rule: every new migration ships its regenerated snapshot

Each PR that adds or changes a file in `db/migration/` must also commit the
regenerated snapshot:

```bash
scripts/db/snapshot.sh --fresh      # ~15 s, needs Docker; never touches your dev database
git add src/main/resources/db/snapshot
```

The same is available as Gradle tasks (group `database`; they wrap the
scripts and compile nothing):

| Task | Writes |
|---|---|
| `./gradlew dbSnapshot` | all four files |
| `./gradlew dbSnapshotSchema` | `schema.sql` + `schema-map.md` |
| `./gradlew dbSnapshotData` | `data.sql` + `seed-catalog.md` |

Every task accepts `-PdbHost -PdbPort -PdbName -PdbUser -PdbPassword`. Without
them the snapshot uses an ephemeral Docker Postgres (`--fresh`); with them it
runs `--migrate` on that database, which **must be empty** (the script refuses
one that already has schema `app`). The script equivalent of a partial run is
`--only schema,data,map,catalog`. Prefer an exported `PGPASSWORD` over
`-PdbPassword`, which stays in shell history.

If it is forgotten, the `db-snapshot` job in
[`ci.yaml`](../../../../../.github/workflows/ci.yaml) regenerates it and
commits it to the PR branch as `github-actions[bot]`. **Never edit these files
by hand.**

### Why the diff stays small

The generator runs everything inside pinned images (`postgres:15-alpine`,
`flyway/flyway`). It applies all migrations to an empty database, then
[`scripts/db/normalize.sql`](../../../../../scripts/db/normalize.sql) makes the
seeded data deterministic:

- uuids from `gen_random_uuid()` become `md5('<table>:<pk>')`, and references to them are updated to match;
- timestamps and dates produced by `now()`/`current_date` while migrating become `2026-01-01`;
- rows are ordered by primary key;
- `flyway_schema_history` gets fixed install metadata (checksums stay real).

Two runs produce identical files. A migration's PR diff therefore shows only
what that migration changed: a rename touches its column lines plus one
history row.

These files are excluded from the JAR (`build.gradle` → `processResources`),
so nothing on the classpath can mistake them for a migration or an init script.

## Import the database without running every migration

```bash
scripts/db/restore.sh                                          # from this folder (drops + recreates the local DB)
scripts/db/restore.sh --seed scripts/dev-seed-demo-user.sql    # + dev seeds, in the order given
scripts/db/restore.sh --release <tag>                          # from a GitHub release asset instead
./gradlew dbRestore -PdbSeeds=scripts/dev-seed-demo-user.sql   # same via Gradle (no prompt; -PdbRelease / -PdbFile;
                                                               #   -PdbHost/-PdbPort/-PdbName/-PdbUser/-PdbPassword)
./gradlew bootRun                                              # Flyway applies only the newer migrations
```

Each GitHub release also attaches `optibienestar360-<tag>.dump` (`pg_dump -Fc` of
the same final database) plus these files, through the `attach-db-to-release`
job in [`publish.yaml`](../../../../../.github/workflows/publish.yaml).

Note: `app.immutable_unaccent()` calls `unaccent()` unqualified. Any manual
load must run with `search_path = app, public` (the restore script does this);
otherwise building the trigram indexes fails.
