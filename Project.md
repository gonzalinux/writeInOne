# WriteInOne — Requirements

## What is it

A personal blogging backend I can deploy and point my own sites to.
Each site gets its own subdomain (`blog.mysite.com`) with isolated content and its own look.
Posts are written in Markdown, stored in a database, and rendered on request.

---

## Stack

- **Kotlin + Spring Boot 3 (WebFlux)** — reactive, non-blocking, suited for read-heavy public blog traffic
- **PostgreSQL** — posts stored as plain `TEXT` (Markdown)
- **R2DBC** — reactive database driver (required for WebFlux; replaces JPA)
- **Flyway** — schema migrations
- **Spring Security + JWT** — auth
- **flexmark-java** — Markdown → HTML rendering
- **Spring @Scheduled** — scheduled publishing
- **Thymeleaf** — server-side HTML rendering for public blog and admin UI
- **Vanilla JS + CSS** — minimal frontend, no build step, no framework
- **springdoc-openapi** — Swagger UI
- **JUnit 5 + MockK + Testcontainers** — testing (note: reactive testing with WebFlux has more friction, use `StepVerifier`)
- **Docker + docker-compose** — local setup
- **GitHub Actions** — CI

---

## Phase 1 — Personal Blog (MVP)

The goal here is simple: a working blog I can use for my own products.
One account, one or more sites, write posts, publish them.

### Auth
- Register with email + password
- Login → receive JWT access token + refresh token
- Logout → invalidate refresh token
- Rate limiting on login and register endpoints

### Sites
- Create a site with a name and unique full domain (e.g. `blog.gonzalo.com`)
- Update site name, description, and styles
- Delete a site (removes all posts)
- Per-site config stored as JSONB (nav links, footer text) keyed by language
- Route incoming requests by `Host` header → match full domain

### Posts
- Create a post with title and Markdown body
- Optional fields: excerpt, cover image URL, tags
- Post statuses: `draft`, `published`, `archived`
- Publish immediately or schedule for a future date
- Scheduled job picks up and publishes pending posts
- Slugs auto-generated from title, overridable, unique per site
- Update any field of an existing post
- Paginated post list, filterable by status and tag

### Tags
- Scoped per site
- Created implicitly when assigned to a post
- Deleting a tag removes it from posts but not the posts themselves

### Public API
- Post list (published only, paginated)
- Single post by slug (rendered as HTML)
- Tags with post counts
- Draft and archived posts never returned

### Dev experience
- `docker-compose.yml` to spin up everything locally
- Swagger UI at `/swagger-ui.html`
- Seed script for local dev data
- GitHub Actions CI on every push

---

## Design Decisions

### Database
- IDs are `BIGSERIAL` (auto-increment) instead of UUID — easier to work with during development
- `updated_at` is set explicitly in update queries, not via triggers

### Auth
- No Spring Data JPA — using `DatabaseClient` with raw SQL for full control and visibility over queries
- Flyway runs on startup using a JDBC datasource; app uses R2DBC for reactive access

### Sites
- Styling is done via `styles_url` — users host their own CSS and provide the URL; no CSS stored server-side
- Site layout (nav, footer, buttons) is configured via a JSONB `config` column keyed by language — the app controls HTML structure, users control content and appearance
- Sites support multiple languages via a `TEXT[]` languages column (e.g. `{en, es}`); default is English
- For now only English and Spanish are supported

### Posts
- Posts are split into two tables: `posts` (metadata) and `post_translations` (content per language)
- Before publishing, all languages defined in `site.languages` must have a translation
- Cover image is a URL (`cover_url`) stored on the `posts` table — same image across all languages
- Posts have a `view_count` counter incremented on every public view — used for sorting popular posts on the home screen
- Image uploads are not supported in Phase 1; users provide URLs to images they host elsewhere

---

## Phase 2 — Path Forward (Turn it into a product)

After the personal version is solid, the goal shifts:
let others use it too — starting with friends who have a site but no easy way to blog.

### Multi-tenancy & custom domains
- Support custom domains in addition to subdomains (`blog.friend.com` pointing here)
- Route by `Host` header matching `custom_domain` in the DB
- Users bring their own domain and set a CNAME — the platform doesn't manage DNS

### Collaboration
- Invite users by email to a site with a role (`editor`)
- Invitation tokens, 48h expiry, accept flow
- Editors can write and publish posts but can't change site settings

### Self-service onboarding
- Public registration (anyone can sign up)
- Email verification before creating a site
- Password reset via email

### Content
- RSS feed per site at `/rss.xml`
- SEO metadata in API responses (OG tags, canonical URL)
- Full-text search on posts within a site
- Comments on posts (with moderation)

### Frontend
- **Public blog** — Thymeleaf templates rendered server-side + one CSS file per theme. No JS framework, no build step.
- **Admin UI** — Thymeleaf + vanilla JS for Markdown live preview (using marked.js via CDN). Everything lives in one project, no separate frontend repo, no CORS to deal with.

### Open questions before starting Phase 2
- Where does this get hosted? (Railway, Fly.io, VPS) — affects how SSL for custom domains is handled
- Who handles SSL certs for custom domains? (Caddy/Traefik auto-TLS vs manual Let's Encrypt)
- Image uploads or URLs only? (S3/R2 vs just storing cover image URLs) — Phase 1 uses URLs only; image uploads would be a nice addition in Phase 2