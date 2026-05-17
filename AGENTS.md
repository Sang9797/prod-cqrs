# Repository Guidelines

## Project Structure & Module Organization

This is a Java 25 / Spring Boot 4 CQRS order service. Main code lives under
`src/main/java/com/company/orders`, organized by responsibility:
`domain` for aggregate and value-object logic, `application` for commands,
queries, and handlers, `bus` for command/query dispatching, `infrastructure`
for persistence adapters, and `presentation` for REST, GraphQL, auth, and
configuration. Runtime configuration is in `src/main/resources`, including
profile files, Liquibase changelogs under `db/changelog`, and GraphQL schemas
under `graphql`. Tests live in `src/test/java`; test seed data is in
`src/test/resources/data.sql`. Operational assets are in `docker`, `nginx`,
`k6`, `postman`, `scripts`, and `docs`.

## Build, Test, and Development Commands

- `make run`: start the app with the `local` profile; expects Docker infra and
  `.env` values.
- `make docker-up-infra`: start PostgreSQL, PgBouncer, Prometheus, and Grafana.
- `make docker-up`: build and start the full application stack.
- `make test`: run Maven tests with the `test` profile.
- `make build`: build the production JAR with tests skipped.
- `mvn spotless:apply`: format Java sources.
- `mvn validate`: run formatting and Checkstyle gates.
- `make load-test` / `make stress-test`: run k6 performance scenarios.

## Coding Style & Naming Conventions

Java formatting is enforced by Spotless with Google Java Format. Checkstyle
also runs during Maven `validate` and fails on violations. Use spaces, keep
lines at or below 100 characters, avoid wildcard imports, and keep one
top-level type per file. Production methods, fields, variables, and parameters
use `lowerCamelCase`; types use `UpperCamelCase`; constants use
`UPPER_SNAKE_CASE`; packages stay lowercase.

## Testing Guidelines

Use Spring Boot test tooling and JUnit via `spring-boot-starter-test`. Name test
classes with a clear `*Test` or `*IntegrationTest` suffix. Test methods may use
underscores for readability, such as `placeOrder_success`. Run `make test`
before submitting changes, and add focused tests for command/query handlers,
security behavior, persistence changes, and GraphQL field authorization.

## Commit & Pull Request Guidelines

Git history uses Conventional Commit-style prefixes such as `feat:`, `fix:`,
and `chore:`. Keep subjects imperative and scoped to one change. Pull requests
should include a short summary, test evidence such as `make test` or
`mvn validate`, linked issues when applicable, and screenshots or curl examples
for API, GraphQL, dashboard, or documentation changes.

## Security & Configuration Tips

Do not commit local secrets or generated `.env` files. Keep JWT, database, and
Grafana credentials in environment-specific configuration. When changing
database structure, add Liquibase changelog files and matching rollback SQL
where the existing pattern does so.
