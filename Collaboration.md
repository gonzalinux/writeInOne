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

| Piece                                                                     | State                                                                                                     |
|---------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `site_members` schema + owner backfill (V14)                              | ✓ shipped                                                                                                 |
| Membership-based authorization + role enforcement                         | ✓ shipped                                                                                                 |
| Creator added as `ADMIN` on site creation                                 | ✓ shipped                                                                                                 |
| `site_invitations` schema (V13)                                           | ✓ shipped (table only, nothing reads it)                                                                  |
| Invite create / list / revoke / accept (backend)                          | ✓ shipped                                                                                                 |
| Member list, removal (backend)                                            | ✓ shipped (`SiteHandler`/`SiteService`: `getAllUsers`, `deleteUser`)                                      |
| Member role change                                                        | ✓ shipped (`SiteHandler`/`SiteService`: `updateUserRole`), admin-only, owner/self excluded                |
| Admin UI — People tab (invite creation, member list, member removal)      | ✓ shipped, see [Frontend](#frontend--people-tab--accept-page) below                                       |
| Admin UI — pending-invitations list (view/copy/revoke an existing invite) | not started — see note below                                                                              |
| Admin UI — service accounts page                                          | ✓ shipped — see [Implementation notes](#implementation-notes) below                                      |
| `site-invitation` email template (local reference copy)                   | ✓ shipped (`email-templates/site-invitation.html`) — **not yet registered with Gonemail**, see note below |
| Service accounts — `User` model + CRUD (repo/service/handler/routes)      | ✓ shipped, tested — see [Implementation notes](#implementation-notes) below                               |
| Service accounts — bearer-token auth filter                               | ✓ shipped, tested — see [Implementation notes](#implementation-notes) below                               |
| Invite a service account into `site_members`                              | ✓ shipped, tested                                                                                          |
| Expired `site_invitations` cleanup                                        | ✓ shipped — `ExpiredInvitationScheduler`, see [Implementation notes](#implementation-notes) below         |

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

One consequence: a future pending-invitations list could never show "invited alice@example.com" — only "pending
invite, role: writer, expires in 40h". Acceptable given the invitation is disposable and short-lived. (No such list
exists in the UI yet — see [Frontend](#frontend--people-tab--accept-page) below.)

### Frontend — People tab & accept page

Shipped in the People tab of `site-form.html` (admin-only; hidden entirely for `editor`/`writer` members):

- **Invite creation** (`site-form.js`): pick a role, optionally an email. Blank email → `delivery: LINK`, filled →
  `delivery: EMAIL`. On success, a one-time modal shows the accept link with a copy button.
- **Member removal**: a "×" button on each member row, admin-only, hidden for the site owner (the backend rejects
  removing the owner or removing yourself regardless — see `SiteService.deleteUser` — the UI just avoids the
  round-trip for the always-rejected owner case).
- **Accept page** (`invitation-accept.html` / `invitation-accept.js`, routed at `/admin/invitations/accept` in
  `AdminHandler`): reads `?token=`, calls `POST /invitations/accept`, shows success (with a link into the site) or a
  specific error (expired / invalid / missing token).
- **Redirect-back-after-login**: `api.js`'s 401 handler now appends `?redirect=<path>` when bouncing to
  `/admin/login`, and `login.js`/`register.js` honor that param after a successful login or OTP verification instead
  of hardcoding `/admin`. Needed because the accept call requires an authenticated session — a logged-out visitor who
  clicks an invite link gets sent to log in/register and lands back on the accept page afterward. The `redirect`
  target is validated to be a same-origin relative path (must start with `/`, not `//`) before use, to avoid an open
  redirect.

**Known limitation — no pending-invitations list.** `GET /sites/{id}/invitations` and
`DELETE /sites/{id}/invitations/{invitationId}` exist and work, but nothing in the UI calls them: a created invite is
shown once in the confirmation modal and then effectively forgotten by the frontend (it stays live in the DB until it
expires or someone accepts it). There is currently no way to see what invites are outstanding for a site, re-copy an
old link, or revoke one, except by calling the API directly. Deliberate scope cut for the first pass — revisit if this
turns out to matter in practice.

**Known gap — email template not registered.** `GonemailClientImpl.sendInvitationEmail` POSTs to the external
Gonemail service with `template: "site-invitation"`. The local `email-templates/site-invitation.html` file is a
reference copy only — nothing in this repo reads it — so until that HTML is registered as the `site-invitation`
template on the Gonemail side, an email-delivery invite will silently fail to send (caught and logged in
`SiteInvitationService.deliver`) while the call still succeeds overall, since the accept link is always returned in
the API response regardless of whether the email went out.

### Manual test checklist (human invite frontend)

None of this shipped with automated tests, so it needs a manual pass before relying on it:

- [ ] As a site admin, create a **link-only** invite (leave email blank) for each role (writer/editor/admin); confirm
  the modal's link and copy button work and the role/expiry text matches what was picked.
- [ ] Create an **email** invite; confirm the accept link still appears in the modal even though delivery currently
  fails silently until Gonemail has the `site-invitation` template registered (see gap above).
- [ ] Open an invite link **while logged out**: confirm it redirects to `/admin/login?redirect=...`, and that
  completing login lands back on the accept page and completes the accept automatically.
- [ ] Same, but choose "Create one" from the login page instead: confirm the `redirect` param survives into
  `/admin/register`, through OTP verification, and back to the accept page.
- [ ] Open an invite link **while already logged in** as a different eligible user: confirm immediate accept and a
  success screen with a working link into the site.
- [ ] Re-open an already-accepted invite link: should still succeed without changing the existing member's role (see
  `SiteInvitationService.accept` — following the link twice must never downgrade someone).
- [ ] Open an **expired** or **garbage** token: confirm a specific, non-generic error message rather than a crash or
  blank page.
- [ ] As a non-admin member (`editor`/`writer`), open the People tab: confirm the invite box is hidden and no member
  row shows a remove button.
- [ ] As an admin, remove a non-owner member: their row disappears, and (separately, e.g. by logging in as that user)
  confirm they've actually lost access to the site.
- [ ] Confirm the owner's row never shows a remove button, and that the currently logged-in admin never sees one on
  their own row either.

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

### Implementation notes

- `Mono<User>.requireNotServiceAccount()` guard (next to `User` in `domain/user/User.kt`), applied in the **service**
  layer, not the repository — so a service account row is never filtered out by a query predicate but stays readable by
  code that has a legitimate reason to touch it (e.g. the CRUD below). Wired into `UserService`: `login`,
  `verifyEmail`, `resetPassword` throw `UnauthorizedException` on a service account, matching those methods' existing
  explicit-error behavior; `requestPasswordReset`/`resendVerificationEmail` use an inline
  `.filter { it.serviceAccountTokenHash == null }` instead, to preserve their existing silent-no-op-on-unknown-email
  behavior (throwing there would leak that an email belongs to a service account).
- **Deviates from the spec below:** `email` is not reused as the human-readable name — a throwaway unique value
  (`sa-<uuid>@service.internal`) is generated instead, and `display_name` carries the name the user typed. Revisit if
  the literal spec matters later.
- `SiteInvitationService.inviteServiceAccount(siteId, userId, serviceAccountId, role)` implements
  [Inviting a service account](#inviting-a-service-account-ai-agent) above: admin-on-site via the existing
  `requireAdminOn`, then owner-of-service-account via `ServiceAccountRepository.existsByIdAndOwnerId`, then a direct
  insert via `SiteRepository.addMember` (no `site_invitations` row). Routed at
  `POST /sites/{id}/invitations/service-account`, driven from the "Grant access" button on the service-accounts page
  below.
- Tested: `ServiceAccountServiceTest`, `SiteInvitationServiceTest` (new file, covers only `inviteServiceAccount` — the
  human-invite flow has no test file of its own), plus service-account-rejection cases in `UserServiceTest`. Full
  `./gradlew test` (unit + integration against the real DB) is green.
- `ServiceAccountAuthFilter` (`config/`) implements the [Auth flow](#auth-flow) below. It sits in front of
  `JwtAuthFilter` on `protectedRoutes()` only (the REST API) — `JwtAuthFilter` itself is untouched and still guards the
  admin-UI preview route directly, since a service account has no admin-UI use case. When the request carries
  `Authorization: Bearer <token>`, it hashes the token, looks the row up via new
  `UserRepository.findByServiceAccountTokenHash`, and writes `userId` into the Reactor context exactly like
  `JwtAuthFilter` does; anything else (no header, or a non-Bearer scheme) delegates straight through to
  `JwtAuthFilter.filter(...)`, so cookie-based human sessions are unaffected. Tested in `ServiceAccountAuthFilterTest`.
- Admin UI: new standalone page (`static/admin/service-accounts.html` + `service-accounts.js`, routed at
  `/admin/service-accounts`, linked from the dashboard header) rather than a tab on a site — service accounts belong
  to the user (`owner_id`), not a site. Covers create (token shown once), list, rotate, revoke, and "Grant access"
  (site + role picker, filtered client-side to sites the user administers, calling `inviteServiceAccount` above).
  **Known gap:** no way to see which sites a service account already has access to — there's no
  `GET /service-accounts/{id}/sites`-shaped endpoint yet, so "Grant access" is one-directional. Manually verified
  end-to-end in the browser (create → grant → confirmed the `site_members` row in Postgres → rotate → revoke →
  confirmed the cascade deleted it), not covered by automated browser tests.
- While reviewing the service-accounts page, replaced native `confirm()`/`alert()` with a proper modal
  (`static/js/confirm-modal.js`, `confirmModal()`/`alertModal()`, Promise-based) everywhere in the admin UI —
  dashboard's delete-site, the People tab's remove-member/role-change, post-list's publish/unpublish/delete, and
  style-editor's error dialogs, in addition to the new service-accounts page. Motivated by more than aesthetics: a
  native `confirm()` blocks the page's JS thread entirely while open, which was actively breaking browser-automation
  testing (Chrome DevTools Protocol mouse dispatch times out against a blocked renderer).
- `ExpiredInvitationScheduler` (`scheduler/`) sweeps expired `site_invitations` rows nobody accepted, reusing
  `SiteInvitationRepository.deleteExpired` and the existing `token-scheduler` cadence/limit (30 min, 100 rows) via
  `TokenCleanerProperties` — the same shape of cleanup `ExpiredTokenScheduler` already does for `refresh_tokens`. This
  closed a real gap: unlike `refresh_tokens`/`email_verification_tokens`/`password_reset_tokens`, expired invitations
  had no cleanup at all before this — they'd have accumulated in the table forever.

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
