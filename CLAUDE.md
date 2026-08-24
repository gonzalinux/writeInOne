# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
make run          # Start the app (pair with make watch for auto-reload)
make watch        # Continuously recompile Kotlin and copy resources (DevTools restarts the app)
make db           # Start only the database via Docker Compose
make test         # Run tests locally
make docker-test  # Run tests inside Docker Compose
make prod         # git pull + start app and alloy via Docker Compose (expects an external DB)
make prod-db      # Same, but also starts the db container
make down         # Stop all containers
make logs         # Follow Docker Compose logs
make psql         # Open interactive psql session in postgres container
```

To run a single test class or pattern:

```bash
./gradlew test --tests "UserServiceTest"
./gradlew test --tests "*IntegrationTest"
./gradlew test --tests "PostServiceTest.create*"
```

The database runs on **port 5433** (non-standard). Default credentials: `postgres / secret`, database `writeinone`.
Integration tests need it running (`make db`).

The app serves on `8080`; the **management server is a separate port (`8081`)** exposing `/health`, `/metrics`
(Prometheus, remapped from `/prometheus`) and the owner dashboard at `/owner`.

## Architecture

### Request flow

```
HTTP Request
  → Router (functional RouterFunction, no @RestController)
  → Route-group filters (host resolution → auth → exception mapping)
  → Handlers (api/) — parse request, call service, render response
  → Services (domain/) — business logic
  → Repositories (domain/) — R2DBC queries via DatabaseClient
  → PostgreSQL
```

Handlers never touch repositories directly — all data access goes through a service.

### Route groups (Router.kt)

Filters are applied per group with `.filter()`; the **last** `.filter()` call is the outermost/first to run.

| Group                | Filters (outermost first)                             | Purpose                                                    |
|----------------------|-------------------------------------------------------|------------------------------------------------------------|
| `publicRoutes`       | none                                                  | `/auth/*` — register, login, refresh, logout, email verify, password reset |
| `protectedRoutes`    | AdminHost → ServiceAccountAuth (→ JwtAuth)            | REST API: sites, posts, tags, invitations, service accounts |
| `mcpRoutes`          | AdminHost → ServiceAccountAuth (→ JwtAuth)            | `POST /mcp` — JSON-RPC MCP server                           |
| `adminRoutes`        | AdminHost → AdminException                            | Static admin UI (`/admin/**`) + main `/sitemap.xml`         |
| `adminPreviewRoute`  | AdminHost → JwtAuth → AdminException                  | `/admin/preview/...` — Thymeleaf render of an unpublished post |
| `docsRoutes`         | AdminHost                                             | `/docs`, `/docs/**` — rendered Markdown docs                |
| `blogUiRoutes`       | Host → JwtNotEnforced → BlogException                 | Public blog pages, RSS, sitemap, `/_verify`                 |
| `blogApiRoutes`      | Host                                                  | Public JSON post list, tag search, post events              |

Owner routes (`/owner`, `/owner/sites/{id}/verify`) are **not in `Router.kt`** — they live in
`OwnerManagementConfig`, a `@ManagementContextConfiguration`, so they are only reachable on the management port.

`AdminHostFilter` restricts a group to the platform's own front door; `HostFilter` resolves a tenant site from the
request host. Both defer to `SubdomainProperties.isHomeDomain()`.

### Context propagation

Two custom context objects flow through Reactor's `ContextView`:

- **`RequestContext`** — holds `userId` and `requestId`; set by `JwtAuthFilter` / `ServiceAccountAuthFilter` /
  `RequestIdFilter`
- **`SiteContext`** — holds the resolved `Site` entity; set by `HostFilter`

Access them inside a handler with `Mono.deferContextual { ctx -> ctx.getRequestContext() }`. `MdcConfig` +
`io.micrometer:context-propagation` copy the request id into the logging MDC.

### Multi-tenancy and authorization

Sites are member-scoped and domain-isolated. `HostFilter` resolves the incoming domain to a `Site` at the start of every
blog request. All post and tag queries are scoped by `site_id`.

The REST API authorizes through **`site_members`, not `sites.user_id`**. `SiteRepository.findById`/`findAllByUserId`
join membership and return the caller's role on `Site.role` (`Roles?` — `null` on any query that doesn't join, and every
guard treats `null` as deny). Roles are `ADMIN` (admin+publish+write), `EDITOR` (publish+write), `WRITER` (write). The
`requireWrite()` / `requirePublish()` / `requireAdmin()` extensions on `Mono<Site>` in `Roles.kt` guard the write paths;
place them *after* `findById` so non-members get `404` and wrong-role members get `403`. Repository write queries
deliberately carry no `user_id` predicate — authorization belongs in the service layer. `sites.user_id` remains only as
the creator/billing owner. Full design in **Collaboration.md**.

Membership is granted through `site_invitations` (email invites, accepted at `POST /invitations/accept`, which is
deliberately outside `/sites` because the invitee has no membership yet) or by inviting a service account directly.

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
`reservationDays`: the previous owner can reclaim it, nobody else can. Reservation expiry is enforced **by query** (
filter on `released_at`), not by the purge scheduler.

### Post versioning

A `PostTranslation` is a stable "live row" per (post, language); its content lives in `post_translation_versions`.
Editing appends a new draft version; `post_translations.current_version_id` points at the version that is actually
served. Consequences to respect:

- A translation with `current_version_id IS NULL` has never been published and is invisible to blog queries.
- Slug uniqueness is a **partial** unique index over `(site_id, lang, slug) WHERE current_version_id IS NOT NULL`, so
  draft versions may share a slug with nothing enforced until publish.
- `publishVersion` also flips the parent post live on the first publish, otherwise the translation would point at a
  version but stay unreachable.

### Multi-language

Posts have one `PostTranslation` per language (`en` / `es`). Site config (JSONB) stores per-language nav links and
footer text. Blog routes are prefixed with `/{lang:es|en}`.

### Background schedulers

`SchedulerBase` is an `ApplicationRunner` driving a `Flux.interval(...).concatMap { execute() }` — plain Reactor, one
run at a time, errors logged and swallowed. Each scheduler takes its interval from a `@ConfigurationProperties` class
and the global `schedulers.enabled` kill switch. Current jobs: publish scheduled posts, purge expired refresh tokens,
purge expired email tokens, purge expired invitations, verify pending custom domains, purge expired subdomain
reservations.

### Reactive rules

The entire stack is non-blocking (Spring WebFlux + R2DBC). Never use blocking calls. Always return `Mono<T>` or
`Flux<T>` from services and repositories.

## Database

Migrations are in `src/main/resources/db/migration/` (Flyway, `V*__name.sql`). R2DBC is used for runtime queries; a
separate JDBC datasource is configured only for Flyway (`FlywayConfig`).

Key tables: `users`, `sites`, `site_members`, `site_invitations`, `posts`, `post_translations`,
`post_translation_versions`, `post_events`, `tags`, `post_tags`, `refresh_tokens`, `subdomain_reservations`.

`sites.config` is a JSONB column mapped to `SiteConfig` (favicon URL, per-language nav/footer).
`sites.styles_url` is the user-provided CSS URL loaded by public blog pages.
`post_events` records public-blog analytics (views etc.) with a hashed fingerprint for dedup.

Don't add columns, migrations, or wrapper types beyond what a change actually requires.

## Frontend

Admin UI is a **static HTML app** in `src/main/resources/static/admin/`, with its JS in `static/js/`. It is not
server-rendered: `AdminHandler.serve` maps every `/admin/**` path to one of those files and returns it as a
`ClassPathResource`; the page then fetches its data from the JSON API through the `api()` helper in `static/js/api.js`.
`templates/admin/` does not exist — the only Thymeleaf-rendered admin route is the post preview.

Public blog uses **Thymeleaf** fragments (`templates/fragments/layout.html`) with a shared default stylesheet (
`/css/blog.css`).

The blog stylesheet loads first, then the site's custom `stylesUrl` after it — so user-provided CSS can override any
class. All overridable selectors are documented in `src/main/resources/docs/1-guides/4-theming.md` (served at
`/docs/guides/theming`, and fetchable by an MCP client via `get_doc` with slug `guides/theming`).

Admin pages share `/css/admin.css`. Never use inline `<style>` blocks in templates.

### Adding a new admin page

1. Create the HTML file in `static/admin/`, importing `/css/admin.css` and `/js/api.js`
2. Add its page script to `static/js/`
3. Map the URL path to the file in the `when` block of `AdminHandler.serve`

`/admin/**` is already routed, so no new route is needed unless the page needs its own handler (as `preview` does).

## Authentication

Two credentials reach the same endpoints:

- **Human sessions** — JWT in HttpOnly cookies (`access_token`, `refresh_token`). Access tokens expire in 15 minutes;
  refresh tokens in 30 days, stored hashed in the DB and rotated on use. `JwtAuthFilter` validates the cookie.
- **Service accounts** — `Authorization: Bearer <token>`, hashed into `users.service_account_token_hash`.
  `ServiceAccountAuthFilter` sits *in front of* `JwtAuthFilter` and only handles requests carrying a bearer header;
  everything else falls through to the cookie flow. This is what lets MCP clients and automation use the normal API.

Both write `userId` into the Reactor context, so services below the filter cannot tell the two apart — and shouldn't.

**Spring Security is not a dependency.** Auth is hand-rolled `HandlerFilterFunction`s because functional routing makes
per-route-group filters the natural place for it (rationale in `spring-security.md`). `config/SecurityConfig.kt` only
supplies a bcrypt `PasswordEncoder` — it is not a Spring Security config.

Email verification, password reset and invitation mails go out through `GonemailClientImpl` (an external service
configured by `gonemail.*`), with HTML bodies in `src/main/resources/email-templates/`.

## MCP server

`POST /mcp` (`McpHandler`) is a hand-rolled JSON-RPC 2.0 MCP endpoint: `initialize`, `tools/list`, `tools/call`. It is
**stateless** — no `Mcp-Session-Id` is ever issued — and account-scoped rather than site-scoped, restricted to the home
domain by `AdminHostFilter`.

Tools: `list_sites`, `list_posts`, `get_post`, `list_tags`, `create_draft`, `edit`, `list_versions`, `publish`,
`unpublish`, `schedule`, `update_site_config`, `list_docs`, `get_doc`. Argument shapes are data classes in
`api/data/Mcp.kt`, and the tool JSON schemas are the `toolDefs` list at the bottom of `McpHandler`. Every tool calls the
same services as the REST API, so role guards apply unchanged. Domain exceptions are mapped to JSON-RPC error codes by
`toJsonRpcError` / `codeForStatus` — add new cases there rather than throwing raw errors out of a tool.

Adding a tool means: args data class → private method → entry in the `callTool` `when` → entry in `toolDefs`.

## Docs system

`DocsService` scans `classpath:docs/**/*.md` at startup. The directory and file **number prefixes only set ordering**
and are stripped from the slug: `docs/1-guides/4-theming.md` → slug `guides/theming`, served at `/docs/guides/theming`
and via the MCP `get_doc` tool. Adding a doc is just adding a Markdown file in the right numbered group.

## Observability

Micrometer + `/metrics` (Prometheus) on the management port, `MetricsFilter` for request timing with templated paths (
`MetricsPathIntegrationTest` guards against high-cardinality path labels), structured JSON logs via
`logstash-logback-encoder` when `JSON_LOG_PATH` is set, Grafana Alloy shipping them in the compose stack. Dashboards
live in `writeinone_dashboard.json` / `generic_dashboard.json`.

## Tests

Tests live under `src/test/kotlin/com/gonzalinux/` and split into two groups:

- **Unit tests** (`domain/`, `blogs/`, `config/`, `client/`, `common/`) — use mockk, no database
- **Integration tests** (`api/`) — `@SpringBootTest` with `WebTestClient` against the real local database (port 5433);
  `AuthTestSupport` handles register/login/cookie plumbing

Integration tests clean up after themselves in `@AfterEach` using direct SQL deletes scoped to test-specific email
patterns (e.g. `%@integrationtest.com`).

## Reference docs in this repo

- **Collaboration.md** — full design of site members, roles, invitations and service-account auth
- **Phase2.md** — Phase 2 spec: turning the single-user backend into a multi-user, subscription product
- **Project.md**, **README.md** — product overview and feature list
- **spring-security.md** — write-up on why auth is hand-rolled filters rather than Spring Security
