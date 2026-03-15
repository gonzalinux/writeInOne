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
- [x] Spring Security (disabled form login/basic auth, permit all — auth enforced at router level)
- [x] JWT auth filter (reads `access_token` cookie, populates security context)
- [x] Router with `authenticated {}` wrapper for protected routes
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
- [x] `TokenService` — JWT generation (HS256), refresh token generation, SHA-256 hashing
- [ ] `POST /auth/refresh` — rotate refresh token, issue new access token
- [ ] `POST /auth/logout` — delete refresh token, clear cookies

## Sites
- [ ] `POST /sites` — create site
- [ ] `PUT /sites/{id}` — update site
- [ ] `DELETE /sites/{id}` — delete site (cascades to posts)
- [ ] Route by `Host` header → match domain

## Posts
- [ ] `POST /sites/{id}/posts` — create post
- [ ] `PUT /sites/{id}/posts/{postId}` — update post
- [ ] `DELETE /sites/{id}/posts/{postId}` — delete post
- [ ] `POST /sites/{id}/posts/{postId}/publish` — publish immediately
- [ ] `POST /sites/{id}/posts/{postId}/schedule` — schedule for future date
- [ ] Scheduled job to publish pending posts
- [ ] Slug auto-generation from title

## Tags
- [ ] Created implicitly when assigned to a post
- [ ] Delete tag (removes from posts, not posts themselves)

## Public API
- [ ] `GET /{lang}` — post list (published only, paginated, sorted by date/views)
- [ ] `GET /{lang}/{slug}` — single post rendered as HTML
- [ ] `GET /tags` — tags with post counts
- [ ] Route incoming requests by `Host` header

## Frontend (Thymeleaf)
- [ ] Public blog templates
- [ ] Admin UI with Markdown preview
