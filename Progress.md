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
- [ ] Swagger UI at `/swagger-ui.html`
- [ ] Seed script for local dev data

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

## Sites API
- [x] `POST /sites` — create site
- [x] `GET /sites` — list user's sites
- [x] `PUT /sites/{id}` — update site (name, description, stylesUrl, languages, config)
- [x] `DELETE /sites/{id}` — delete site (cascades to posts)
- [x] `findByDomain` — site lookup for Host header resolution

## Posts API
- [x] `POST /sites/{siteId}/posts` — create post
- [x] `GET /sites/{siteId}/posts` — list posts
- [x] `GET /sites/{siteId}/posts/{postId}` — get post with translations
- [x] `PUT /sites/{siteId}/posts/{postId}` — update post
- [x] `DELETE /sites/{siteId}/posts/{postId}` — delete post
- [x] `POST /sites/{siteId}/posts/{postId}/publish` — publish immediately
- [x] `POST /sites/{siteId}/posts/{postId}/schedule` — schedule for future date
- [x] Slug auto-generation from title
- [x] `publishScheduled` repo query — bulk update due scheduled posts
- [x] `view_count` incremented on every public view
- [ ] Paginated post list (query param support)
- [ ] Filter by status and tag

## Tags API
- [x] Created implicitly when assigned to a post
- [x] `GET /sites/{siteId}/tags` — list tags for a site
- [x] `DELETE /sites/{siteId}/tags/{tagId}` — delete tag (removes from posts, not posts themselves)
- [ ] `GET /tags` — public tags with post counts (blog-facing)

## Schedulers
- [x] `SchedulerBase` — abstract coroutine-based scheduler with configurable interval
- [x] `ExpiredTokenScheduler` — deletes expired refresh tokens on a schedule
- [x] `PublishPostsScheduler` — publishes scheduled posts when their time is due

## Public Blog (blog-facing routes, Host-filtered)
- [x] `HostFilter` — resolves site from `Host` header, writes to Reactor context
- [x] `SiteContextHolder` — Reactor context holder for the resolved site
- [x] `GET /` — redirects to default language
- [x] `GET /{lang}` — post list rendered via Thymeleaf (`index.html`)
- [x] `GET /{lang}/{slug}` — single post rendered as HTML via flexmark (`post.html`)
- [x] `GET /{lang}/posts` — JSON list of published posts
- [x] Base layout fragment with nav and footer driven by `SiteConfig`
- [x] Post list template (cover image, date, excerpt, tags)
- [x] Single post template (flexmark-rendered body, meta tags, back link)
- [x] Favicon and custom stylesheet loaded from site config
- [ ] Paginated post list on public route
- [ ] Tags page with post counts
- [ ] RSS feed at `/rss.xml`

## Admin UI
- [x] Login page — JS fetch to `POST /auth/login`, sets httpOnly cookies
- [x] Register page — JS fetch to `POST /auth/register`
- [x] Dashboard — lists sites with links to posts, new post, and edit site
- [x] New site form — name, domain, description, stylesUrl, faviconUrl, languages (checkboxes), per-language footer and nav links
- [x] Edit site form — same fields, domain read-only; language config sections collapse when unchecked
- [x] Post list — shows title, languages, status badge, published date; inline publish action
- [x] New post form — title, body (Markdown), excerpt, slug, tags, cover URL, language selector
- [x] Edit post form — pre-populated from existing post; publish button when not yet published
- [x] All forms submit via JS fetch to the REST API (no HTML form POSTs)
- [x] 401 responses redirect to login
- [ ] Delete post from admin UI
- [ ] Delete site from admin UI
- [ ] Archive post action
- [ ] Markdown live preview in post editor
- [ ] Token refresh handling (auto-retry on 401 with refresh before redirecting)
