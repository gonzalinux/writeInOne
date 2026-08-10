# AGENTS.md

This file provides critical context for AI agents working in this repository. For comprehensive guidance, see
`CLAUDE.md`.

## Critical Gotchas

- **Database port is 5433** (not standard 5432). All connections must use this port.
- **Entire stack is reactive** (Spring WebFlux + R2DBC). Never use blocking calls. Always return `Mono<T>` or `Flux<T>`.
- **Integration tests require a running database** on port 5433. Use `make db` before running tests.
- **Multi-tenancy is enforced via site_id**. All post/tag queries must be scoped by site. Never query across sites.

## Architecture Essentials

- **Context propagation**: `RequestContext` (userId, requestId) and `SiteContext` (resolved Site) flow through Reactor's
  `ContextView`. Access via `Mono.deferContextual { ctx -> ctx.getRequestContext() }`.
- **Site resolution**: `HostFilter` resolves domain to Site from `X-Site-Host` (falling back to `Host`) on every blog
  request, and reads the mount path from `X-Forwarded-Prefix`.
- **Two hosting modes**: a managed subdomain (`myblog.writeinone.com` — created `VERIFIED`, always `prefix = ''`) or the
  user's own domain behind their reverse proxy (`example.com/blog` — needs `/_verify` DNS verification). Never add
  per-user subdomains of *their* domain; the gateway cannot provision certs for those.
- **Home-domain checks go through `SubdomainProperties.isHomeDomain()`**. Never compare a host to `"writeinone.com"`
  inline — the base domain is configurable and `www` counts too.
- **Route groups**: Six distinct groups in `Router.kt` with different auth requirements (public, protected, admin
  public/protected, blog UI/API).

## Testing

- **Unit tests**: `src/test/kotlin/domain/`, `blogs/` — use mockk, no database required
- **Integration tests**: `src/test/kotlin/api/` — require real database, use `@SpringBootTest` with `WebTestClient`
- **Test cleanup**: Integration tests clean up via SQL deletes scoped to `%@integrationtest.com` email patterns
- **Run single test**: `./gradlew test --tests "TestClassName"` or `./gradlew test --tests "TestClassName.methodName*"`

## Commands

```bash
make db           # Start database (required for tests)
make test         # Run all tests
make run          # Start app (pair with make watch)
make watch        # Auto-recompile on changes
```

## Database

- **Migrations**: `src/main/resources/db/migration/` (Flyway)
- **Runtime queries**: R2DBC via `DatabaseClient`
- **Flyway only**: Uses separate JDBC datasource
- **Key tables**: `users`, `sites`, `posts`, `post_translations`, `tags`, `post_tags`, `refresh_tokens`,
  `subdomain_reservations`
- **Reservation expiry is enforced by query**, not by the purge scheduler — always filter on `released_at`

## Frontend

- **Admin UI**: static HTML in `src/main/resources/static/admin/` + JS in `static/js/`, shared `/css/admin.css`. Served
  by `AdminHandler.serve`, **not** Thymeleaf — `templates/admin/` is empty.
- **Public blog**: Thymeleaf fragments, default `/css/blog.css` + user-provided `stylesUrl` (loaded after, can override)
- **Never use inline `<style>` blocks** in templates

## Authentication

- JWT via HttpOnly cookies (`access_token`, `refresh_token`)
- Access tokens: 15 minutes
- Refresh tokens: 30 days, stored hashed in DB, rotated on use
