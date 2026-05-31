`# WriteInOne — Phase 2 Specification

## Goal

Turn the personal blogging platform into a multi-user product.
Anyone can sign up, create sites, and publish posts.
Pricing is subscription-based with a free tier.

---

## Infrastructure assumptions

- Hosted on home server (SAI-backed) with a failover VPS
- Emails sent via **Gonemail** — internal transactional email microservice (wraps Oracle Cloud SMTP). Registered app: `writeinone`.

---

## Domain modes

Three ways a user can expose their blog, with different tiers:

### Mode 1 — Free subdomain (`slug.writeinone.com`) — Free

Every user gets a subdomain under `writeinone.com` on signup (e.g. `gonblog.writeinone.com`).

- DNS: `*.writeinone.com CNAME writeinone.com` — one-time setup, covers all users forever
- SSL: existing wildcard cert for `*.writeinone.com` covers all subdomains automatically
- Platform work per user: generate a unique slug, store it as `site.domain`, HostFilter routes by Host header — nothing else

### Mode 2 — Custom domain (`blog.mysite.com`) — Pro

User points their own domain to the platform via CNAME (`blog.mysite.com CNAME writeinone.com`).

- DNS: user's responsibility — they add the CNAME record on their DNS provider
- SSL: API gateway detects the new hostname on first request and provisions a Let's Encrypt cert automatically (dynamic ACME)
- Platform work per user: store `custom_domain` in DB, gate behind `plan = pro` check
- HostFilter already routes by Host header — no code change needed

### Mode 3 — Path proxy (`mysite.com/blog`) — Free

User runs their own reverse proxy and forwards a path prefix to WriteInOne. SSL and routing are entirely the user's responsibility.

- DNS/SSL: handled by the user's own server — platform does nothing
- Platform work: `prefix` is already stored per site and threaded through all templates and links — this works today
- Free because the platform provides zero additional infrastructure; the user brings their own server

---

## Pricing

| Tier | Price    | Sites | Posts       |
|------|----------|-------|-------------|
| Free | €0       | 1     | 30          |
| Pro  | €5/month | ∞     | ∞           |

- Plan enforced at post creation and site creation
- Payment via Stripe

---

## Features

### 1. Email service ✓

Emails sent via **Gonemail** (`GonemailClientImpl`). Templates managed in the Gonemail UI.

Emails needed:
- Email verification (on register) ✓
- Password reset ✓
- Site invitation (Phase 2 collaboration)

### 2. Self-service onboarding ✓

**Email verification**
- On register, send a verification email with a 6-digit OTP (2-min expiry, `SecureRandom`)
- OTP stored hashed (SHA-256) in `email_verification_tokens` table, scoped by `user_id`
- Expired tokens cleaned up by `ExpiredEmailTokenScheduler` (runs every 30 min)
- Until verified, user cannot create a site
- Gonemail template: `email-verification` — variable: `{{ verification_code }}`
- Endpoints:
  - `POST /auth/verify-email` — body: `{ email, code }`
  - `POST /auth/resend-verification` — JWT-protected, no body

**Password reset**
- User requests reset via email; response is always 200 (no email enumeration)
- 6-digit OTP sent (2-min expiry, `SecureRandom`), stored hashed in `password_reset_tokens` table, scoped by `user_id`
- Gonemail template: `password-reset` — variable: `{{ reset_code }}`
- Endpoints:
  - `POST /auth/forgot-password` — body: `{ email }`
  - `POST /auth/reset-password` — body: `{ email, code, password }`

### 3. Plan enforcement

- `users` table gets a `plan` column (`free` / `pro`) and `plan_expires_at`
- On site creation: check site count against plan limit
- On post creation: check post count against plan limit
- Return `403 PLAN_LIMIT_REACHED` with a clear message when limit hit

### 4. Stripe integration

- `POST /billing/checkout` — create Stripe Checkout session → redirect to payment
- `POST /billing/portal` — Stripe Customer Portal (manage/cancel subscription)
- `POST /billing/webhook` — handle `checkout.session.completed`, `customer.subscription.deleted`
- On successful payment: set `plan = pro`, set `plan_expires_at`
- On cancellation: revert to `free` at period end

### 5. Collaboration (editor invites)

- Site owner can invite a user by email to a site with role `editor`
- Invitation token (UUID, 48h expiry) stored in `site_invitations` table
- Email sent with accept link: `GET /invitations/accept?token=xxx`
- If invitee has no account, they are taken to register first, then invitation is applied
- Editors can create, edit, and publish posts but cannot change site settings or delete the site
- Owner can revoke access at any time

### 6. Public registration

- Registration page already exists — just needs to be open to everyone
- After register → prompt to verify email before creating a site

### 7. Free subdomain assignment

On site creation, auto-generate a unique `slug.writeinone.com` subdomain and store it as `site.domain`.

- Slug derived from site name, lowercased, non-alphanumeric replaced with `-`, uniqueness checked against DB
- Append a short random suffix on collision (e.g. `myblog-a3f`)
- HostFilter already routes by Host header — no routing change needed
- DNS: `*.writeinone.com CNAME writeinone.com` — one-time infrastructure setup, not per user

### 8. Custom domain (Pro)

Allow pro users to attach their own domain to a site.

- Store `custom_domain` on the `sites` table (nullable, unique)
- Admin UI: input field in site settings, only shown/enabled for pro users
- On save: validate format, check uniqueness, store
- HostFilter matches `custom_domain` as a secondary lookup after the primary `domain`
- SSL: handled by the API gateway (dynamic ACME) — no platform code needed
- Gate behind `plan = pro`; return clear error if free user tries to set one

### 9. Image uploads

Local disk storage for post images. No S3 or external dependency.

- Upload endpoint: `POST /api/sites/{siteId}/uploads` — accepts multipart, returns the public URL
- Files stored under a configurable base directory (e.g. `/data/uploads/{siteId}/`)
- Served as static files via a mapped route (e.g. `/uploads/{siteId}/{filename}`)
- Validate file type (JPEG, PNG, WebP, GIF) and size limit (e.g. 10 MB) on upload
- Filename stored as UUID to avoid collisions and path traversal
- Storage: 1 TB primary disk, expandable with additional 2 TB disks
- Failover note: the VPS failover won't have image files — brief image unavailability during failover is acceptable
- Admin UI: image picker in the post editor that uploads and inserts the URL

### 10. Custom 404 page ✓

Blog routes now render a site-styled 404 page (`blog-not-found.html`) with the site's nav, footer, and custom CSS instead of the generic error page. `BlogExceptionFilter` handles this automatically for all `NOT_FOUND` exceptions on blog routes.

### 11. Full-text search

PostgreSQL native full-text search — no external service.

- Add `search_vector tsvector` column to `post_translations`
- Populate via Flyway migration using `to_tsvector('english', title || ' ' || body)`
- GIN index on `search_vector`
- Keep in sync via `@afterUpdate` trigger or explicit update in `PostRepository`
- Query with `plainto_tsquery`, rank results with `ts_rank`
- Blog search bar already exists and wires into `listPublished` — just needs the DB-side implementation

### 12. Usage dashboard

Show free-tier users how close they are to their limits so they're never surprised by a `PLAN_LIMIT_REACHED` error.

- Admin site list: show post count vs limit (e.g. "18 / 30 posts") per site
- Admin header or settings page: show plan badge (`FREE` / `PRO`) and upgrade CTA for free users
- No new DB queries needed — post count is already available

### 13. Docs site

Public documentation for writeinone.com, needed before opening registration.

- Hosted as a static site or as a dedicated WriteInOne blog on the platform itself
- Minimum pages: Getting started, Custom domains, CSS theming, API / MCP
- CSS theming reference already exists at `src/main/resources/static/css/theme.md` — can be adapted

### 15. API keys

Allows programmatic access to the REST API without browser-based JWT cookies. Target users: developers, automation pipelines, and the MCP server.

- User generates named key+secret pairs in the admin UI; secret shown once, stored hashed (SHA-256) in DB
- Passed as `Authorization: Bearer <secret>` on every request
- New auth filter alongside `JwtAuthFilter` — checks bearer token, loads user, writes `RequestContext` same as JWT filter
- Keys can be revoked at any time from the admin UI
- `api_keys (id, user_id, name, key_hash, created_at, last_used_at, revoked_at)`

### 16. MCP server

A standalone TypeScript package (distributed, not hosted) that wraps the REST API and exposes it as MCP tools for AI agents. Target users: SEO agencies automating content creation across sites and languages.

**Auth:** the MCP server authenticates to WriteInOne using a regular API key (feature 7) — no special protocol. Users paste their API key into the MCP server config.

**Distribution:** users run the MCP server locally or in their own infra. Zero hosting cost on our end.

**Tools exposed:**
- `list_sites` — list all sites for the authenticated user
- `create_post` — create a post with title, body (Markdown), excerpt, tags, cover URL, language
- `update_post` — update any field of a post by slug
- `publish_post` — publish a draft post (optionally at a scheduled date)
- `list_posts` — list posts for a site, filterable by status, tag, language
- `get_post` — fetch a single post by slug
- `list_tags` — list tags for a site

**Also expose the OpenAPI spec** (already generated by springdoc-openapi) as a fallback for agencies using non-MCP AI frameworks — they can load it directly as tools without any extra service.

---

## Database migrations needed

- `V7` — add `prefix`, `verify_date`, `status` to `sites` ✓
- `V8` — add `email_verified` to `users`; create `email_verification_tokens (token_hash, user_id, expires_at)` and `password_reset_tokens (token_hash, user_id, expires_at)` ✓
- `V9` — add `plan`, `plan_expires_at`, `stripe_customer_id` to `users`
- `V10` — `site_invitations (id, site_id, email, role, token_hash, expires_at, accepted_at, created_at)`
- `V11` — `site_members (site_id, user_id, role, created_at)` — accepted invitations land here
- `V12` — add `custom_domain` (nullable, unique) to `sites`
- `V13` — add `search_vector tsvector` to `post_translations`; GIN index; backfill
- `V14` — `api_keys (id, user_id, name, key_hash, created_at, last_used_at, revoked_at)`

---

## Order of implementation

1. ~~Email service~~ ✓
2. ~~Email verification + password reset~~ ✓
3. ~~Custom 404 page~~ ✓
4. Free subdomain assignment
5. Plan enforcement (free tier limits)
6. Usage dashboard
7. Full-text search
8. Image uploads (local disk)
9. Stripe integration
10. Custom domain (Pro)
11. Collaboration / invites
12. API keys
13. MCP server (TypeScript, separate repo)
14. Docs site

---

## Open questions

- Grace period when pro subscription lapses (e.g. 7 days before enforcing free limits)?
- Should editors be able to invite other editors, or only the owner?
- Should API key access be a paid feature, or available on the free tier?
- Should the MCP server be open source?
