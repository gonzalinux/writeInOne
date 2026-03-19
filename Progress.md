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
- [ ] Rate limiting on auth endpoints (handled by API gateway)
- [ ] GitHub Actions CI
- [x] Swagger UI at `/swagger-ui.html` — via `springdoc-openapi-starter-webflux-ui`

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
- [x] `PUT /sites/{id}` — update site (name, description, stylesUrl, availableThemes, languages, config)
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
- [x] Paginated post list (`page` + `size` query params, default size 20)
- [x] Filter by status, tag, and title search (`status`, `tag`, `search` query params)

## Tags API
- [x] Created implicitly when assigned to a post
- [x] `GET /sites/{siteId}/tags` — list tags for a site
- [x] `DELETE /sites/{siteId}/tags/{tagId}` — delete tag (removes from posts, not posts themselves)


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
- [x] Single post template (flexmark-rendered body, back link)
- [x] OG + Twitter meta tags on post page (`og:title`, `og:description`, `og:image`, `og:url`, `article:published_time`, `twitter:card`)
- [x] Favicon and custom stylesheet loaded from site config
- [x] Theme system — `availableThemes` drives toggle button visibility; `html.dark` class toggled by `theme.js`; `prefers-color-scheme` as no-JS fallback; hljs stylesheet swapped on toggle
- [x] Paginated post list on public route (`?page=N`, 10 per page, prev/next nav)
- [x] Search and tag filtering on public home (`?search=…&tag=…`); tags are clickable links; filters preserved across pagination
- [x] RSS feed — `/{lang}/rss.xml` per language, `/rss.xml` redirects to default lang; auto-discovery `<link>` in every page head

## Admin UI
- [x] Login page — JS fetch to `POST /auth/login`, sets httpOnly cookies
- [x] Register page — JS fetch to `POST /auth/register`
- [x] Dashboard — lists sites with links to posts, new post, and edit site
- [x] New site form — name, domain, description, stylesUrl, default theme select, enable-switcher checkbox, faviconUrl, languages (checkboxes), per-language footer and nav links
- [x] Edit site form — same fields, domain read-only; language config sections collapse when unchecked
- [x] Post list — shows title, languages, status badge, published date; inline publish action
- [x] New post form — title, body (Markdown), excerpt, slug, tags, cover URL, language selector
- [x] Edit post form — pre-populated from existing post; publish button when not yet published
- [x] All forms submit via JS fetch to the REST API (no HTML form POSTs)
- [x] 401 responses redirect to login
- [x] Delete post from admin UI (with confirmation)
- [x] Delete site from admin UI (with confirmation)
- [ ] Markdown live preview in post editor (ideally renders with the site's actual `blog.css` + `stylesUrl`)
- [ ] CSS style tester — `/admin/style-tester`: example article on left, live CSS input on right, changes apply in real time; hovering an element shows the class selector to target
- [x] Token refresh — silent `POST /auth/refresh` every 5 minutes via `setInterval` in `api.js`
