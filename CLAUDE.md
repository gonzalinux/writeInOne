# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
make run          # Start the app (pair with make watch for auto-reload)
make watch        # Continuously recompile Kotlin and copy resources
make db           # Start only the database via Docker Compose
make test         # Run tests locally
make docker-test  # Run tests inside Docker Compose
make prod         # Load .env and start app + db via Docker Compose
make down         # Stop all containers
make logs         # Show Docker app logs (make logs f=1 to follow)
make psql         # Open interactive psql session in postgres container
```

To run a single test class or pattern:
```bash
./gradlew test --tests "UserServiceTest"
./gradlew test --tests "*IntegrationTest"
./gradlew test --tests "PostServiceTest.create*"
```

The database runs on **port 5433** (non-standard). Default credentials: `postgres / secret`, database `writeinone`.

## Architecture

### Request flow

```
HTTP Request
  → Router (functional RouterFunction, no @RestController)
  → Filters: HostFilter → JwtAuthFilter → AdminExceptionFilter / BlogExceptionFilter
  → Handlers (api/) — parse request, call service, render response
  → Services (domain/) — business logic
  → Repositories (domain/) — R2DBC queries via DatabaseClient
  → PostgreSQL
```

### Route groups (Router.kt)

| Group | Auth | Purpose |
|---|---|---|
| `publicRoutes` | none | `/auth/*` (register, login, refresh, logout) |
| `protectedRoutes` | JWT | REST API for sites, posts, tags |
| `adminPublicRoutes` | none | Admin login/register pages |
| `adminProtectedRoutes` | JWT | Admin UI (Thymeleaf pages) |
| `blogUiRoutes` | HostFilter | Public blog pages (domain-resolved) |
| `blogApiRoutes` | HostFilter | Public JSON post list |

### Context propagation

Two custom context objects flow through Reactor's `ContextView`:
- **`RequestContext`** — holds `userId` and `requestId`; set by `JwtAuthFilter`
- **`SiteContext`** — holds the resolved `Site` entity; set by `HostFilter` using the request's `Host` header

Access them inside a handler with `Mono.deferContextual { ctx -> ctx.getRequestContext() }`.

### Multi-tenancy

Sites are user-owned and domain-isolated. `HostFilter` resolves the incoming domain to a `Site` at the start of every blog request. All post and tag queries are scoped by `site_id`. The REST API scopes by `userId` via site ownership.

### Multi-language

Posts have one `PostTranslation` per language (`en` / `es`). Site config (JSONB) stores per-language nav links and footer text. Blog routes are prefixed with `/{lang:es|en}`.

### Reactive rules

The entire stack is non-blocking (Spring WebFlux + R2DBC). Never use blocking calls. Always return `Mono<T>` or `Flux<T>` from services and repositories. Background schedulers use Kotlin Coroutines (`SchedulerBase`).

## Database

Migrations are in `src/main/resources/db/migration/` (Flyway). R2DBC is used for runtime queries; a separate JDBC datasource is configured only for Flyway.

Key tables: `users`, `sites`, `posts`, `post_translations`, `tags`, `post_tags`, `refresh_tokens`.

`sites.config` is a JSONB column mapped to `SiteConfig` (favicon URL, per-language nav/footer).
`sites.styles_url` is the user-provided CSS URL loaded by public blog pages.

## Frontend

Admin UI uses **Thymeleaf** server-rendered templates (`src/main/resources/templates/admin/`).
Public blog uses Thymeleaf fragments (`templates/fragments/layout.html`) with a shared default stylesheet (`/css/blog.css`).

The blog stylesheet loads first, then the site's custom `stylesUrl` after it — so user-provided CSS can override any class. All overridable selectors are documented in `src/main/resources/static/css/THEME.md`.

Admin pages share `/css/admin.css`. Never use inline `<style>` blocks in templates.

## Authentication

JWT delivered via HttpOnly cookies (`access_token`, `refresh_token`). Access tokens expire in 15 minutes; refresh tokens in 30 days and are stored hashed in the DB. `JwtAuthFilter` validates the cookie and writes `RequestContext` into the Reactor context.

## Tests

Tests split into two groups:
- **Unit tests** (`src/test/kotlin/domain/`, `blogs/`) — use mockk, test services in isolation
- **Integration tests** (`src/test/kotlin/api/`) — use `@SpringBootTest` with `WebTestClient` and a real local database (port 5433)

Integration tests clean up after themselves in `@AfterEach` using direct SQL deletes scoped to test-specific email patterns (e.g. `%@integrationtest.com`).

## Adding a new admin page

1. Add handler method in `AdminHandler.kt`
2. Register the route in `adminProtectedRoutes()` in `Router.kt`
3. Create the Thymeleaf template in `templates/admin/`, importing `/css/admin.css`
