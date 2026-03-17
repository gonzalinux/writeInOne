# WriteInOne — Progress

## Infrastructure
- [x] Spring Boot 4 + WebFlux setup
- [x] R2DBC (reactive) + JDBC (Flyway) dual datasource config
- [x] Flyway migrations
- [x] Docker Compose (app + postgres on port 5433)
- [x] `application.yml` with env variable defaults
- [x] Global exception handler (`ApiException` → HTTP response)
- [x] Request validator (annotation-based, throws `ValidationException`)
- [x] JWT config via `@ConfigurationProperties`
- [x] Custom router-based auth (no Spring Security)
- [x] JWT auth filter as `HandlerFilterFunction` on protected routes
- [x] Two-pipeline router: public auth routes + protected routes with JWT filter
- [x] Jackson configured with Kotlin module, JavaTimeModule, ISO-8601 dates
- [x] `bindNullable` extension for clean R2DBC null bindings
- [x] Reactor context propagation (`RequestContext` with userId + requestId)
- [x] MDC integration via `ThreadLocalAccessor` (requestId + userId in all logs)
- [x] Structured logging with `kotlin-logging`
- [x] `@ConfigurationPropertiesScan` for auto-discovery of config properties
- [ ] Rate limiting on auth endpoints
- [ ] GitHub Actions CI

## Database Migrations
- [x] V1 — users
- [x] V2 — sites
- [x] V3 — posts + post_translations
- [x] V4 — tags + post_tags
- [x] V5 — refresh_tokens

## Auth
- [x] `POST /auth/register` — hash password, create user, issue tokens, set cookies
- [x] `POST /auth/login` — verify credentials, issue tokens, set cookies
- [x] `POST /auth/refresh` — rotate refresh token, issue new access token
- [x] `POST /auth/logout` — delete refresh token, clear cookies
- [x] `TokenService` — JWT generation (HS256), refresh token generation, SHA-256 hashing

## Sites
- [x] `POST /sites` — create site
- [x] `GET /sites` — list user's sites
- [x] `PUT /sites/{id}` — update site
- [x] `DELETE /sites/{id}` — delete site (cascades to posts)
- [x] `findByDomain` — site lookup for Host header resolution

## Posts
- [x] `POST /sites/{siteId}/posts` — create post
- [x] `GET /sites/{siteId}/posts` — list posts
- [x] `GET /sites/{siteId}/posts/{postId}` — get post with translations
- [x] `PUT /sites/{siteId}/posts/{postId}` — update post
- [x] `DELETE /sites/{siteId}/posts/{postId}` — delete post
- [x] `POST /sites/{siteId}/posts/{postId}/publish` — publish immediately
- [x] `POST /sites/{siteId}/posts/{postId}/schedule` — schedule for future date
- [x] Slug auto-generation from title
- [x] `publishScheduled` repo query — bulk update due scheduled posts

## Schedulers
- [x] `SchedulerBase` — abstract coroutine-based scheduler with configurable interval
- [x] `ExpiredTokenScheduler` — deletes expired refresh tokens on a schedule
- [x] `PublishPostsScheduler` — publishes scheduled posts when their time is due

## Tags
- [x] Created implicitly when assigned to a post
- [x] `GET /sites/{siteId}/tags` — list tags for a site
- [x] `DELETE /sites/{siteId}/tags/{tagId}` — delete tag (removes from posts, not posts themselves)

## Public API
- [x] `HostFilter` — resolves site from `Host` header, writes to Reactor context
- [x] `SiteContextHolder` — Reactor context holder for the resolved site
- [x] Blog routes pipeline with `HostFilter` applied
- [ ] `GET /` — redirect or language picker
- [ ] `GET /{lang}` — Thymeleaf rendered page with nav/footer from SiteConfig
- [ ] `GET /{lang}/posts` — JSON list of published posts (paginated)
- [ ] `GET /{lang}/{slug}` — single post rendered as HTML via flexmark
- [ ] `GET /tags` — tags with post counts

## Frontend (Thymeleaf)
- [ ] Add `spring-boot-starter-thymeleaf` dependency
- [ ] Base layout template with nav/footer from SiteConfig
- [ ] Post list template
- [ ] Single post template
- [ ] Admin UI with Markdown preview
