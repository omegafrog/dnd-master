# ADR 004: Module-Scoped Flyway for `app-all`

Status: Accepted

Context:

- `app-all` starts every backend service in one Spring Boot process.
- A single merged Flyway scan on the full runtime classpath causes version collisions between services.
- The same database can safely host several service migration streams if each module runs its own Flyway over its own migration location and its own history table.

Decision:

- Disable the default `spring.flyway` bootstrap in `app-all`.
- Create one Flyway initializer per backend service.
- Scope each initializer to that service's own `classpath:db/migration/<service>` location.
- Give each initializer a dedicated Flyway history table in `public`.
- When `app-all` starts against a database that already contains the merged legacy history, seed each module's history table from `public.flyway_schema_history` and then let Flyway continue from the module-specific history table.
- Keep module-local migration versions independent again.
- Copy each service's migration resources into its own runtime subdirectory during resource processing.
- Keep existing service migrations idempotent where the modular monolith may re-encounter already-created tables, indexes, columns, or constraints.

Consequences:

- `start-dev.sh` still boots the modular monolith, but Flyway now matches service boundaries.
- Service migration version numbers can overlap across modules again.
- Existing databases can be adopted without resetting, because each service gets its own history table and can be seeded from the legacy merged history.
