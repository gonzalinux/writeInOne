# WriteInOne — Requirements

## What is it

A personal blogging backend I can deploy and point my own sites to.
Each site gets its own subdomain (`blog.mysite.com`) with isolated content and its own look.
Posts are written in Markdown, stored in a database, and rendered on request.

---

## Stack

- **Kotlin + Spring Boot 3**
- **PostgreSQL** — posts stored as plain `TEXT` (Markdown)
- **Flyway** — schema migrations
- **Spring Security + JWT** — auth
- **flexmark-java** — Markdown → HTML rendering
- **Spring @Scheduled** — scheduled publishing
- **springdoc-openapi** — Swagger UI
- **JUnit 5 + MockK + Testcontainers** — testing
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
- Create a site with a name and unique subdomain
- Update site name, description, and theme
- Delete a site (removes all posts)
- Per-site config stored as JSONB (SEO defaults, nav links, footer text)
- Route incoming requests by `Host` header → match subdomain

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

### Admin UI
- A minimal web UI for writing and managing posts
- Live Markdown preview while writing
- Decide: Thymeleaf (simpler, stays in Kotlin ecosystem) vs React frontend (better UX, more work)

### Open questions before starting Phase 2
- Where does this get hosted? (Railway, Fly.io, VPS) — affects how SSL for custom domains is handled
- Who handles SSL certs for custom domains? (Caddy/Traefik auto-TLS vs manual Let's Encrypt)
- Image uploads or URLs only? (S3/R2 vs just storing cover image URLs)