# WriteInOne — Collaboration & Service Accounts

Detailed design for Phase2.md items **#5 (Collaboration & roles)** and **#15 (API keys)** / **#16 (MCP server)**'s auth.
Phase2.md keeps the summary; this doc is the source of truth for how these two features actually fit together.

---

## Why these two are one doc

Sites move from single-owner to multi-member (`site_members`). Once membership is a table instead of a single
`sites.user_id`, "give an AI agent access to a site" and "give a human collaborator access to a site" become the same
operation — both are just a row in `site_members`. The only difference is what kind of `users` row sits on the other end
of `user_id`: a real person, or a service account. That's why service accounts are modeled as `users` rows rather than a
separate entity — see [Service accounts](#service-accounts--api-access) below.

---

## Part 1 — Collaboration & roles

Sites move from single-owner to multi-member. `site_members (site_id, user_id, role, created_at)` replaces the implicit
owner — the creating user becomes the first `admin`.

### Implementation status

| Piece                                                       | State                                    |
|-------------------------------------------------------------|------------------------------------------|
| `site_members` schema + owner backfill (V14)                | ✓ shipped                                |
| Membership-based authorization + role enforcement           | ✓ shipped                                |
| Creator added as `ADMIN` on site creation                   | ✓ shipped                                |
| `site_invitations` schema (V13)                             | ✓ shipped (table only, nothing reads it) |
| Invite create / list / revoke / accept                      | not started                              |
| Member list, role change, removal                           | not started                              |
| Admin UI page                                               | not started                              |
| Service accounts (V12 schema shipped, no code)              | not started                              |

**How enforcement works.** `SiteRepository.findById` and `findAllByUserId` INNER JOIN `site_members`, and the caller's
role rides back on `Site.role`. That field is `Roles?` **with no fallback value**: the queries that don't join
membership leave it `null`, and every guard treats `null` as deny. This is deliberate — an earlier revision defaulted it
to `WRITER`, which meant any `Site` loaded by one of those other queries silently claimed a role it had never actually
loaded.

Three extensions on `Mono<Site>` in `Roles.kt` — `requireWrite()`, `requirePublish()`, `requireAdmin()` — guard every
write path in `PostService`, `TagService` and `SiteService`. They are applied *after* `findById`, so a non-member gets
`404` and a member with an insufficient role gets `403`; the API never reveals a site's existence to someone with no
membership at all.

Two consequences worth remembering:

- `SiteRepository.create` inserts the `site_members` row in the **same statement** as the site, via a data-modifying
  CTE. The codebase has no transaction manager, and a site without a member row would be unreachable through the join —
  so atomicity here is not optional.
- `SiteRepository.update`/`delete` no longer carry `AND user_id = :userId`. Authorization lives entirely in the service
  guards now; re-adding a `user_id` predicate would silently no-op writes for any admin who didn't create the site.

`sites.user_id` survives as the creator/billing owner — it is what subdomain reservations are parked under, and what
plan enforcement (Phase2.md #12/#14) will bill.

### Roles

| Role     | Can do                                                                       |
|----------|------------------------------------------------------------------------------|
| `admin`  | Manage site settings, invite/remove members, plus everything `editor` can do |
| `editor` | Create, edit, and publish any post; cannot manage members or site settings   |
| `writer` | Create and edit post drafts only (see Phase2.md #17); cannot publish         |

Written lowercase throughout this doc, but persisted and exposed **uppercase** (`ADMIN` / `EDITOR` / `WRITER`) — that's
what V14 writes and what the `Roles` enum parses, matching how `SiteStatus` is already stored. `Roles.from` is lenient
(case-insensitive, returns `null` on an unknown value) and exists for parsing untrusted input such as an invite request
body; DB rows are read strictly.

Deletion currently sits behind `requirePublish()` rather than `requireWrite()`, for both posts and tags — a `writer`
whose remit is "create and edit drafts" should not be able to destroy a published post.

### Inviting a human

The invitation is really just a bearer token with a role attached — `email` is never stored, it's only ever an optional
argument to the create call, used inline to send an email and then discarded:

1. Admin creates an invitation with a role, and *optionally* an email.
2. Invitation token (UUID, 48h expiry) stored in
   `site_invitations (id, site_id, role, token_hash, expires_at, accepted_at, created_at)` — no `email` column.
3. If an email was given, Gonemail sends the accept link synchronously as part of that same call: `GET
   /invitations/accept?token=xxx`. If not, the admin UI just shows the link so it can be copied and shared manually
   (Slack, etc.).
4. Accepting only ever needs the token — whoever holds it and is logged in (or registers first) gets added. There's no
   check against an invited email, because none is kept; the token is the credential, same as a password-reset or
   email-verification link. Deliberate tradeoff, same as Slack/Notion-style invite links: anyone who gets hold of the
   link before it expires can join with the granted role.
5. If the invitee has no account, they register first, then the invitation is applied.
6. Accepting inserts the `site_members` row and marks `accepted_at`.

One consequence: the admin UI's pending-invitations list can't show "invited alice@example.com" — only "pending
invite, role: writer, expires in 40h". Acceptable given the invitation is disposable and short-lived.

### Inviting a service account (AI agent)

Same mechanism as a human invite, minus the email round-trip: the inviting admin picks one of their own service
accounts (see below) and a role, and the row is inserted into `site_members` directly — no `site_invitations` row, no
email.

**Constraint:** a service account can only be invited to sites owned/administered by its own `owner_id` (i.e. the human
who created it must themselves hold `admin` on the target site). This is enforced at invite time, not at the DB level —
a service account belonging to user A can never end up as a member of a site user A has no `admin` role on.

### Revocation

Any `admin` can remove a member (human or service account) from `site_members` at any time — deleting the row is the
entire operation, immediate effect on next request.

### Migrations

- `site_invitations (id, site_id, role, token_hash, expires_at, accepted_at, created_at)`
- `site_members (site_id, user_id, role, created_at)` — `role` is `admin` | `editor` | `writer`

---

## Service accounts & API access

Allows programmatic access to the REST API — no browser, no JWT cookie. Target users: the MCP server, automation
pipelines, AI agent accounts.

### Core decision: no separate table

A human user **never** gets an API key — only a service account does, and a service account is created for exactly one
integration (one MCP connection = one service account = one token). Given that, and given `site_members.user_id` needs
to point at a single homogeneous "principal" table (see [Why these two are one doc](#why-these-two-are-one-doc)), a
service account is simply another row in `users`:

- `owner_id BIGINT REFERENCES users(id) ON DELETE CASCADE` — the human who created it; used for the invite-time
  ownership check above and for listing "my service accounts" in the admin UI
- `service_account_token_hash TEXT UNIQUE` — SHA-256 of the token, same deterministic-hash pattern as
  `refresh_tokens.token_hash`, so lookup is a direct `WHERE service_account_token_hash = :hash` (O(1) via unique index).
  Its presence *is* the identity marker — `service_account_token_hash IS NOT NULL` means "this is a service account,"
  no separate boolean needed, since under the lifecycle below a service account only ever exists with a token (revoke
  deletes the row, it's never left in a tokenless limbo state)
- `email` — reused as the account's human-readable name, e.g. `claude-desktop@service.writeinone.com`. Satisfies the
  existing `NOT NULL UNIQUE` constraint for free, no schema change needed there.
- `password` — becomes nullable (`ALTER COLUMN password DROP NOT NULL`); service accounts never log in with a password.

**Rejected: reusing `password` for the token.** `password` is BCrypt-hashed (`SecurityConfig.kt`, cost 12, salted) — the
same input hashes differently every time, so it can't be looked up via `WHERE password = ?`, only verified against a row
you already found via `encoder.matches()`. A single opaque bearer token (matching the UX of every other API-key
convention — GitHub PAT, OpenAI key, etc.) needs a deterministic hash to look up by, which is exactly what
`refresh_tokens` already does for a different kind of token. Hence a dedicated `service_account_token_hash` column
instead of overloading `password`.

### Auth flow

New filter alongside `JwtAuthFilter` (not replacing it):

1. Request carries `Authorization: Bearer <token>`.
2. Filter SHA-256s the token, looks up `users WHERE service_account_token_hash = :hash`.
3. On match, writes `RequestContext(userId = that row's id)` — identical to what `JwtAuthFilter` writes for a human
   session.
4. Downstream, every handler/service is unaware whether the request came from a cookie or a token — permissions are
   exactly whatever `site_members` says for that `userId`. No separate scope system to build or maintain.
5. Login, password-reset, and email-verification endpoints all gain a `WHERE service_account_token_hash IS NULL` guard —
   a service account row must never be reachable through the password login path, and a stolen token must never be
   usable to trigger a password reset.

### Lifecycle (admin UI)

- Create: pick a name, pick which of the current user's sites to grant `writer` (or other role) on immediately or later.
  Token is generated server-side, shown once, only the hash is stored. `owner_id` = current user.
- Revoke: delete the `users` row. `ON DELETE CASCADE` on `owner_id` and on `site_members.user_id` cleans up membership
  automatically — no separate revoked-flag bookkeeping needed (mirrors how `refresh_tokens` rows are just deleted on
  logout).
- Rotate: overwrite `service_account_token_hash` with a freshly generated token's hash; old token stops working
  immediately.

### Migration

Single migration, no new table:

```sql
ALTER TABLE users
    ADD COLUMN owner_id BIGINT REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE users
    ADD COLUMN service_account_token_hash TEXT UNIQUE;
ALTER TABLE users
    ALTER COLUMN password DROP NOT NULL;
```

---

## MCP server auth (ties to Phase2.md #16)

The MCP server config takes exactly one secret: the service account token. The new bearer-token filter above resolves it
to a `userId`, and every MCP tool call is scoped by that service account's `site_members` role — same as Phase2.md #16
already specifies, just naming the mechanism concretely instead of the generic "API key" placeholder.

Publishing stays out of MCP tools regardless of role, per Phase2.md #16 — that's an application-level choice (tools
simply aren't exposed), not something enforced by the `writer` role alone, since an `editor`-scoped service account is
technically possible if someone deliberately grants it.

---

## Open questions carried over from Phase2.md

- Can one service account be invited to multiple sites? — Yes, but only under sites its `owner_id` administers (see
  invite-time constraint above).
- Should API key (service account) access be paid? — Paid, per Phase2.md.
