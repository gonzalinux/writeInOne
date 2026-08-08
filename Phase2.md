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

## Domain modes ✓

Two ways a user can expose their blog, both Free — the earlier CNAME + dynamic-ACME plan (Mode 2 below, historical) was dropped in favor of the reverse-proxy mode, so there's no longer a domain-based Pro differentiator.

### Mode 1 — Managed subdomain (`label.writeinone.com`) — Free ✓

User picks a label in the site form; `SubdomainService` validates it (length, charset, reserved-word list) and checks live availability against `sites` and `subdomain_reservations` before it's claimed. Created `VERIFIED` immediately — no ownership proof needed since we control the DNS and the wildcard cert.

- Released labels are held in `subdomain_reservations` for a grace window so the previous owner can reclaim them; nobody else can take it during that window
- HostFilter routes by Host header — nothing further needed

### Mode 2 — User's own domain behind their reverse proxy — Free ✓

Covers both a full domain and a path-mounted blog (`mysite.com` or `mysite.com/blog`) — the user's proxy sends `X-Site-Host` (which domain to resolve) and `X-Forwarded-Prefix` (the mount path), and `HostFilter` reads both.

- DNS/SSL: entirely the user's responsibility — their proxy holds the cert, the platform provisions nothing
- Ownership proof: `VerifyClientImpl` hits `GET {prefix}/_verify` on the user's domain with a token and checks it round-trips before the site goes `VERIFIED`; `sites.status`/`verify_date` (V7) track this
- No support for subdomains of a *user's* domain — would need per-site cert provisioning at the gateway, which is exactly the cost this mode was chosen to avoid

~~### Mode 2 (historical) — Custom domain via CNAME — Pro~~

Dropped. Would have required the platform to provision Let's Encrypt certs per user domain (dynamic ACME) — real cost and operational surface for a feature the reverse-proxy mode achieves for free.

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

### 5. Collaboration & roles

Sites move from single-owner to multi-member. `site_members (site_id, user_id, role, created_at)` replaces the implicit owner — the creating user becomes the first `admin`.

Roles:
- `admin` — manage site settings, invite/remove members, plus everything `editor` can do
- `editor` — create, edit, and publish any post; cannot manage members or site settings
- `writer` — create and edit post drafts (see #17); cannot publish

- Invite by generating a token (UUID, 48h expiry) + role in `site_invitations`; email is never stored, only optionally passed in at creation to send with Gonemail
- If email is given, Gonemail sends the accept link synchronously as part of that call: `GET /invitations/accept?token=xxx`; otherwise the admin copies that same link from the UI and shares it manually
- If invitee has no account, they are taken to register first, then the invitation is applied
- An AI agent is invited the same way, except it's backed by a **service account** (#15) instead of an email — added directly to `site_members`, and only under sites its owner administers
- Any admin can revoke a member's access at any time

Full design (including the service-account invite constraint) in **[Collaboration.md](Collaboration.md)**.

### 6. Public registration

- Registration page already exists — just needs to be open to everyone
- After register → prompt to verify email before creating a site

### 7. Free subdomain assignment ✓

Done, see Domain modes → Mode 1. Built as user-chosen + validated + reserved (`SubdomainService`), not silent auto-generation from the site name as originally sketched here — same outcome (unique subdomain per site), better UX (user can see and correct their own label).

### 8. User's own domain (reverse proxy) ✓

Done, see Domain modes → Mode 2. Superseded the CNAME/dynamic-ACME plan entirely — no `custom_domain` column, no per-site cert provisioning on our end. Free for everyone, not Pro-gated, since it costs the platform nothing.

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

Allows programmatic access to the REST API without browser-based JWT cookies. Target users: the MCP server and AI agent accounts (#5) — **human users never get a key**, only service accounts do.

- A service account is a login-less `users` row with `service_account_token_hash` populated (its presence *is* the identity marker, no separate flag), created for exactly one integration (one MCP connection = one service account = one token) — not a separate table
- Token is generated server-side, shown once in the admin UI, stored as a SHA-256 hash on the `users` row itself
- Passed as `Authorization: Bearer <secret>` on every request
- New auth filter alongside `JwtAuthFilter` — checks bearer token, loads the service-account user, writes `RequestContext` same as JWT filter
- Permissions are exactly whatever the service account has via `site_members` — no separate scope system to maintain
- Revoking = deleting the `users` row (cascades to `site_members`)

Full design in **[Collaboration.md](Collaboration.md)** — why this reuses `users` instead of a new table, why the token isn't stored in the `password` column, and the invite-time ownership constraint.

### 16. MCP server

A standalone TypeScript package (distributed, not hosted) that wraps the REST API and exposes it as MCP tools for AI agents. Target users: SEO agencies automating content creation across sites and languages, and individual users writing posts via Claude/Cursor/etc.

**Auth:** the MCP server authenticates to WriteInOne using a service account token (#15), typically with `writer` role on the target site(s) — no special protocol. Users paste the token into the MCP server config.

**Distribution:** users run the MCP server locally or in their own infra. Zero hosting cost on our end.

**Tools exposed:**
- `list_sites` — list all sites the authenticated account can access
- `create_draft` — create a new draft version of a post (new post or new translation)
- `propose_edit` — create a new draft version on top of an already-published translation
- `list_versions` — list version history for a post translation (status, `published_at`, `updated_at`)
- `list_posts` — list posts for a site, filterable by status, tag, language
- `get_post` — fetch a single post's live content by slug
- `list_tags` — list tags for a site

Publishing and rollback are deliberately **not** exposed as MCP tools — only `editor`/`admin` roles can publish (#17), and that stays a human action in the admin UI. This is the approval step the whole feature is built around.

**Also expose the OpenAPI spec** (already generated by springdoc-openapi) as a fallback for agencies using non-MCP AI frameworks — they can load it directly as tools without any extra service.

### 17. Post versioning & review workflow

Every post translation keeps a full history of versions, enabling multiple concurrent drafts, editor-driven publishing, and rollback. This is what makes it safe to mix AI-authored drafts (#16) and human-authored drafts on the same post.

- `post_translation_versions` — one full content snapshot per row (title, slug, body, excerpt), scoped to a `post_translation_id`, with a strictly incrementing `version_number`. No diffs/patches, always whole snapshots.
- Status is just `draft` or `published` — there's no separate "pending review" gate; any draft can be published by an editor/admin at any time
- Multiple drafts can coexist on the same translation (e.g. two writers, or a writer plus an AI agent) — no locking, `version_number` just keeps incrementing
- Editor/admin sees every version for a translation in a picker and publishes whichever one they choose
- Publishing copies the chosen version's content into the live `post_translations` row and sets `post_translations.current_version_id` — the public blog read path is untouched, it still reads `post_translations` directly
- Rollback is not a special operation — it's publishing an older version again
- `published_at` on a version is set once, the first time it ever goes live; `updated_at` is bumped every subsequent time it's (re)published — both shown next to each version in the admin UI's picker
- Only `editor`/`admin` roles can publish; `writer` (human or AI, #5) can only create/edit drafts

---

## Database migrations needed

- `V7` — add `prefix`, `verify_date`, `status` to `sites` ✓
- `V8` — add `email_verified` to `users`; create `email_verification_tokens (token_hash, user_id, expires_at)` and `password_reset_tokens (token_hash, user_id, expires_at)` ✓
- `V9` — add `styles_url` to `sites` (custom CSS) ✓
- `V10` — `post_events (post_id, event_type, value, fingerprint_hash, created_at)` (view tracking) ✓
- `V11` — `subdomain_reservations` ✓
- `V12` — add `owner_id`, `service_account_token_hash` to `users`; make `password` nullable; index on `owner_id` (login-less service accounts for AI agents, #5/#15)
- `V13` — `site_invitations (id, site_id, role, token_hash, expires_at, accepted_at, created_at)`; index on `site_id`
- `V14` — `site_members (site_id, user_id, role, created_at)` — `role` is `admin` | `editor` | `writer`; accepted invitations land here; index on `site_id`; backfills existing site owners (`sites.user_id`) as `admin`
- `V15` — add `plan`, `plan_expires_at`, `stripe_customer_id` to `users`
- `V16` — add `search_vector tsvector` to `post_translations`; GIN index; backfill
- `V17` — `post_translation_versions (id, post_translation_id, version_number, status, title, slug, body, excerpt, author_id, created_at, published_at, updated_at)` + `current_version_id` on `post_translations`; backfill a `published`/`draft` version 1 for every existing translation (#17)

Note: `V9`–`V11` above are already taken by shipped work (custom CSS, post view events, subdomain reservations) unrelated to this doc's original plan for those numbers — renumbered starting at `V12` to avoid collisions with the real migration folder.

---

## Order of implementation

1. ~~Email service~~ ✓
2. ~~Email verification + password reset~~ ✓
3. ~~Custom 404 page~~ ✓
4. ~~Free subdomain assignment + own-domain reverse proxy~~ ✓
5. Full-text search
6. Image uploads (local disk)
7. Collaboration / invites (roles: admin / editor / writer)
8. Post versioning & review workflow
9. API keys
10. MCP server (TypeScript, separate repo)
11. Docs site
12. Plan enforcement (free tier limits)
13. Usage dashboard
14. Stripe integration

---

## Open questions

- Grace period when pro subscription lapses (e.g. 7 days before enforcing free limits)? -> yes
- Should API key access be a paid feature, or available on the free tier -> PAID  
- Should the MCP server be open source? -> Yes same as the rest
- Should there be a cap on how many concurrent drafts can pile up on one translation, or is unlimited fine? -> each blog can only have 30 versions available, if more are added the oldest not published ones are removed
- Can one service account (AI agent) be invited to multiple sites, or is it one agent per site? -> Yes, but only under the owner sites.
