# WriteInOne

A multi-tenant blogging platform. Each site runs on its own domain, has its own posts, and renders a public blog with server-side Thymeleaf templates. Manage everything through a built-in admin UI or the REST API.

## Stack

- Kotlin + Spring Boot 4 (WebFlux) — reactive, non-blocking
- PostgreSQL — R2DBC for runtime, JDBC for Flyway migrations
- Thymeleaf — server-rendered public blog and admin UI
- flexmark-java — Markdown to HTML rendering
- JWT via HttpOnly cookies — access token (15m) + refresh token (30d)

## Features

### Sites
- Each site has a unique domain, resolved from the `Host` header on every request
- Per-language config (nav links, footer text) stored as JSONB
- Custom CSS loaded from a user-provided URL
- Multiple languages supported per site (`en`, `es`)
- Light/dark theme toggle, configurable per site

### Posts
- Written in Markdown, rendered on request via flexmark
- Translations per language — all defined languages must be translated before publishing
- Statuses: `draft`, `published`, `scheduled`
- Scheduled publishing via a background job
- Slugs auto-generated from title, unique per site
- View counter incremented on each public visit
- Tags — created implicitly, scoped per site

### Public Blog
- `GET /` — redirects to default language
- `GET /{lang}` — paginated post list with search and tag filtering
- `GET /{lang}/blogs/{slug}` — single post (rendered Markdown)
- `GET /{lang}/rss.xml` — RSS feed per language
- `GET /{lang}/posts` — JSON post list API
- OG and Twitter meta tags on post pages
- Live search via JS (debounced, no page reload)

### Reverse Proxy Prefix
Send `X-Forwarded-Prefix: blog` from your proxy to serve the blog under a subpath of your main domain (e.g. `yourdomain.com/blog/`). All internal links, OG tags, RSS autodiscovery, and JS fetch URLs are adjusted automatically. Prefix must be alphanumeric with hyphens, max 20 characters.

Example Express config:
```javascript
app.use('/blog', createProxyMiddleware({
    target: 'http://blog.yourdomain.com',
    changeOrigin: true,
    pathRewrite: { '^/blog': '' },
    headers: { 'X-Forwarded-Prefix': 'blog' }
}));
```

### Admin UI
- Login, register, dashboard
- Create and manage sites (domain, description, styles, themes, languages, nav/footer config)
- Post editor with Markdown live preview and split view
- Publish, schedule, unpublish, delete posts
- CSS style tester — live preview with hover-to-inspect class names
- Tag management

### Auth
- Register / login / logout / refresh
- JWT delivered via HttpOnly cookies
- Refresh tokens stored hashed in DB, rotated on use

## Running Locally

```bash
make db        # start PostgreSQL on port 5433
make watch     # continuously recompile Kotlin
make run       # start the app
```

Or with Docker:

```bash
make prod      # start app + db via Docker Compose
make down      # stop containers
```

Default DB credentials: `postgres / secret`, database `writeinone`, port `5433`.

Swagger UI available at `/swagger-ui.html`.

## Running Tests

```bash
make test         # run tests locally
make docker-test  # run tests inside Docker Compose
```
