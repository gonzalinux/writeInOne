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

| Group                  | Auth       | Purpose                                      |
|------------------------|------------|----------------------------------------------|
| `publicRoutes`         | none       | `/auth/*` (register, login, refresh, logout) |
| `protectedRoutes`      | JWT        | REST API for sites, posts, tags              |
| `adminPublicRoutes`    | none       | Admin login/register pages                   |
| `adminProtectedRoutes` | JWT        | Admin UI (Thymeleaf pages)                   |
| `blogUiRoutes`         | HostFilter | Public blog pages (domain-resolved)          |
| `blogApiRoutes`        | HostFilter | Public JSON post list                        |

### Context propagation

Two custom context objects flow through Reactor's `ContextView`:

- **`RequestContext`** — holds `userId` and `requestId`; set by `JwtAuthFilter`
- **`SiteContext`** — holds the resolved `Site` entity; set by `HostFilter` using the request's `Host` header

Access them inside a handler with `Mono.deferContextual { ctx -> ctx.getRequestContext() }`.

### Multi-tenancy

Sites are member-scoped and domain-isolated. `HostFilter` resolves the incoming domain to a `Site` at the start of every
blog request. All post and tag queries are scoped by `site_id`.

The REST API authorizes through **`site_members`, not `sites.user_id`**. `SiteRepository.findById`/`findAllByUserId`
join membership and return the caller's role on `Site.role` (`Roles?` — `null` on any query that doesn't join, and every
guard treats `null` as deny). The `requireWrite()` / `requirePublish()` / `requireAdmin()` extensions on `Mono<Site>` in
`Roles.kt` guard the write paths; place them *after* `findById` so non-members get `404` and wrong-role members get
`403`. Repository write queries deliberately carry no `user_id` predicate — authorization belongs in the service layer.
`sites.user_id` remains only as the creator/billing owner. Full design in **Collaboration.md**.

### Hosting modes

A site is reachable in one of two ways, both stored the same way — as `sites.domain` plus `sites.prefix`:

1. **Managed subdomain** — `myblog.writeinone.com`. Covered by the wildcard cert and DNS we control, so it needs no
   ownership proof: it is created `VERIFIED` with `prefix = ''`, and the verification scheduler never sees it.
2. **The user's own domain, behind their reverse proxy** — `example.com/blog`. The proxy sends `X-Site-Host` (which
   domain to resolve) and `X-Forwarded-Prefix` (the path the blog is mounted at). `HostFilter` reads both, and every
   template builds links from `${prefix}`. Requires DNS verification via `/_verify` before going live.

There is no support for subdomains of a *user's* domain — that would need per-site cert provisioning at the gateway.

`SubdomainProperties` (`subdomains.*` in `application.yml`) owns the base domain, the length bounds, the reservation
window and the reserved-label list. It is also the single source of truth for "is this host our own front door?" —
`HostFilter`, `AdminHostFilter` and the main-sitemap predicate all call `isHomeDomain()` rather than comparing hosts
themselves.

`SubdomainService` validates a label **by value, not by which form field it arrived in**, so a reserved label cannot be
smuggled through the custom-domain input. Renaming or deleting a site parks its label in `subdomain_reservations` for
`reservationDays`: the previous owner can reclaim it, nobody else can.

### Multi-language

Posts have one `PostTranslation` per language (`en` / `es`). Site config (JSONB) stores per-language nav links and
footer text. Blog routes are prefixed with `/{lang:es|en}`.

### Reactive rules

The entire stack is non-blocking (Spring WebFlux + R2DBC). Never use blocking calls. Always return `Mono<T>` or
`Flux<T>` from services and repositories. Background schedulers use Kotlin Coroutines (`SchedulerBase`).

## Database

Migrations are in `src/main/resources/db/migration/` (Flyway). R2DBC is used for runtime queries; a separate JDBC
datasource is configured only for Flyway.

Key tables: `users`, `sites`, `posts`, `post_translations`, `tags`, `post_tags`, `refresh_tokens`,
`subdomain_reservations`.

`sites.config` is a JSONB column mapped to `SiteConfig` (favicon URL, per-language nav/footer).
`sites.styles_url` is the user-provided CSS URL loaded by public blog pages.

## Frontend

Admin UI is a **static HTML app** in `src/main/resources/static/admin/`, with its JS in `static/js/`. It is not
server-rendered: `AdminHandler.serve` maps every `/admin/**` path to one of those files and returns it as a
`ClassPathResource`; the page then fetches its data from the JSON API through the `api()` helper in `static/js/api.js`.

Public blog uses **Thymeleaf** fragments (`templates/fragments/layout.html`) with a shared default stylesheet (
`/css/blog.css`).

The blog stylesheet loads first, then the site's custom `stylesUrl` after it — so user-provided CSS can override any
class. All overridable selectors are documented in `src/main/resources/docs/1-guides/4-theming.md` (served at
`/docs/guides/theming`, and fetchable by an MCP client via `get_doc` with slug `guides/theming`).

Admin pages share `/css/admin.css`. Never use inline `<style>` blocks in templates.

## Authentication

JWT delivered via HttpOnly cookies (`access_token`, `refresh_token`). Access tokens expire in 15 minutes; refresh tokens
in 30 days and are stored hashed in the DB. `JwtAuthFilter` validates the cookie and writes `RequestContext` into the
Reactor context.

## Tests

Tests split into two groups:

- **Unit tests** (`src/test/kotlin/domain/`, `blogs/`) — use mockk, test services in isolation
- **Integration tests** (`src/test/kotlin/api/`) — use `@SpringBootTest` with `WebTestClient` and a real local
  database (port 5433)

Integration tests clean up after themselves in `@AfterEach` using direct SQL deletes scoped to test-specific email
patterns (e.g. `%@integrationtest.com`).

## Adding a new admin page

1. Create the HTML file in `static/admin/`, importing `/css/admin.css` and `/js/api.js`
2. Add its page script to `static/js/`
3. Map the URL path to the file in the `when` block of `AdminHandler.serve`

`/admin/**` is already routed, so no new route is needed unless the page needs its own handler (as `preview` does).
