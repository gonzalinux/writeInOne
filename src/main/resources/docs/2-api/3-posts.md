# Posts

A post belongs to a [site](/docs/api/sites) and holds one **translation** per language,
plus a shared set of tags and publication state. All endpoints below are nested under
the site: `/sites/{siteId}/posts`, and require the caller to have access to that site.

## Create a post

```
POST /sites/{siteId}/posts
{
  "coverUrl": null,
  "translations": {
    "en": { "title": "Hello, world", "body": "# Hi\n\nFirst post.", "excerpt": "Intro" },
    "es": { "title": "Hola, mundo", "body": "# Hola\n\nPrimer post." }
  },
  "tags": ["intro", "meta"]
}
```

- `translations` is keyed by language code (`en` / `es`). Each entry needs `title` and
  `body`; `slug` and `excerpt` are optional.
- If `slug` is omitted it's generated from `title`: lowercased, non-alphanumeric
  characters stripped, whitespace collapsed to single hyphens. A slug that collides with
  an existing one for the same site+language returns `409 SLUG_ALREADY_EXISTS`.
- `tags` is a list of **names**, not IDs. A tag that doesn't exist yet for the site is
  created on the spot; an existing one is reused. There's no separate "create tag"
  endpoint — this is the only way tags come into existence.
- The post is created in `draft` status.

Returns the created post with its translations and tags — see
[the response shape](#the-post-response-shape) below.

## List posts

```
GET /sites/{siteId}/posts?page=0&size=10&status=published&tag=intro&search=hello
```

All query params are optional:

| Param    | Default | Notes                                                            |
|----------|---------|------------------------------------------------------------------|
| `page`   | `0`     | zero-indexed                                                     |
| `size`   | `10`    | no upper bound enforced                                          |
| `status` | —       | one of `draft`, `scheduled`, `published`, `archived` (lowercase) |
| `tag`    | —       | exact tag name                                                   |
| `search` | —       | case-insensitive match against translation titles, any language  |

Multiple filters combine with AND. Results are ordered newest-first by creation date.

```json
{
  "content": [
    {
      "post": { "id": 1, "siteId": 42, "status": "PUBLISHED", "coverUrl": null, "viewCount": 12, "publishedAt": "2026-01-10T09:00:00Z", "scheduledAt": null, "createdAt": "...", "updatedAt": "..." },
      "translations": [{ "postId": 1, "lang": "en", "slug": "hello-world", "title": "Hello, world" }],
      "tags": [{ "id": 5, "siteId": 42, "name": "intro", "createdAt": "..." }]
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

Note the list response's translations are a lighter shape (`postId`, `lang`, `slug`,
`title`) than the full translation object returned by the single-post endpoints below —
fetch the post by ID for `body`/`excerpt`.

## Get a post

```
GET /sites/{siteId}/posts/{postId}
```

Returns the full post — see [the response shape](#the-post-response-shape).

## Update a post

```
PUT /sites/{siteId}/posts/{postId}
{
  "coverUrl": "https://example.com/cover.jpg",
  "translations": {
    "en": { "title": "Hello, world (updated)", "body": "..." }
  },
  "tags": ["intro"]
}
```

- Every field is optional and `null` means "leave unchanged" — **except** `tags`: if you
  include it at all (even `[]`), it fully replaces the post's tag set. Omit `tags`
  entirely to leave tags untouched.
- `translations` is keyed by language, same as create. A language with no existing
  translation yet is created and becomes visible immediately, same as create. A language
  that already has a translation is **not** overwritten — this call instead creates a new
  **draft version** on top of it (see [Post versions](#post-versions) below) and leaves
  `post_translations` (what `GET`/the public blog serve) untouched. There's no way to
  delete a single translation through this endpoint.
- Same slug-collision rule as create (`409 SLUG_ALREADY_EXISTS`).

Returns the post with its full, current set of *live* translations and tags (not just
the ones you sent) — for an existing translation, that's still the old content until the
new draft version is published. Check `latestVersions` in
[the response shape](#the-post-response-shape) to see the version your edit just
created.

## Post versions

Every translation keeps a full history of versions — one whole content snapshot per row,
never a diff. Creating a post, or adding a new language to one, makes version 1 and
publishes it immediately. Editing an *existing* translation through `PUT` above adds
another version in `draft` status without touching the live content — this is the
review gate that keeps an editor (human or AI) from silently overwriting what's already
published. Multiple drafts can coexist on the same translation; nothing is locked.

```
GET /sites/{siteId}/posts/{postId}/translations/{lang}/versions
GET /sites/{siteId}/posts/{postId}/translations/{lang}/versions/{versionId}
POST /sites/{siteId}/posts/{postId}/translations/{lang}/versions/{versionId}/publish
```

- The list endpoint returns every version for that translation, newest-first, `draft`
  and `published` mixed together.
- `publish` copies that version's `title`/`slug`/`body`/`excerpt` into the live
  `post_translations` row and marks the version `published` — this is also how rollback
  works: publish an older version again. It requires `editor` or `admin` on the site
  (`writer` can create drafts but not publish them), and returns `409
  SLUG_ALREADY_EXISTS` if the version's slug now collides with another translation's.
- On that translation's first-ever publish, this endpoint also sets the **post's**
  `status` to `published` (stamping `publishedAt`) if it wasn't already — otherwise the
  translation would have a live version but stay invisible, since both blog queries
  require `posts.status = 'published'` too. It only ever does this for the post the
  published translation belongs to, and never publishes a *different* translation on the
  same post that's still a draft (e.g. publishing `en` never exposes a still-drafted
  `es`). Post-level `publish` below remains the one-click way to launch every
  never-published translation on a post at once.
- Only the 30 most recent **draft** versions per translation are kept; older drafts are
  pruned automatically. Published versions are never pruned.

```json
{
  "id": 9, "postTranslationId": 1, "versionNumber": 2, "status": "DRAFT",
  "title": "Hello, world (updated)", "slug": "hello-world", "body": "...", "excerpt": null,
  "authorId": 7, "createdAt": "2026-01-11T09:00:00Z", "publishedAt": null, "updatedAt": "2026-01-11T09:00:00Z"
}
```

`status` is `DRAFT` or `PUBLISHED`. `publishedAt` is set once, the first time a version
ever goes live; `updatedAt` moves on every later (re)publish of that same version.

## Delete a post

```
DELETE /sites/{siteId}/posts/{postId}
```

## Publish / unpublish / schedule

```
POST /sites/{siteId}/posts/{postId}/publish
POST /sites/{siteId}/posts/{postId}/unpublish
POST /sites/{siteId}/posts/{postId}/schedule
{ "scheduledAt": "2026-03-01T09:00:00Z" }
```

- `publish` sets `status` to `published` and stamps `publishedAt` to now.
- `unpublish` sets `status` back to `draft`. `publishedAt` is preserved (republishing
  later doesn't reset it).
- `schedule` sets `status` to `scheduled` with the given `scheduledAt` (ISO-8601, must
  include an offset). A background job flips scheduled posts to `published` once
  `scheduledAt` has passed — there's nothing else to call.

All three return the bare [`Post`](#the-post-response-shape) object (no translations or
tags array).

## The post response shape

`GET`, `POST`, and `PUT` return:

```json
{
  "post": {
    "id": 1, "siteId": 42, "status": "DRAFT", "coverUrl": null,
    "viewCount": 0, "publishedAt": null, "scheduledAt": null,
    "createdAt": "2026-01-10T09:00:00Z", "updatedAt": "2026-01-10T09:00:00Z"
  },
  "translations": [
    { "id": 1, "postId": 1, "siteId": 42, "lang": "en", "title": "Hello, world", "slug": "hello-world", "body": "# Hi\n\nFirst post.", "excerpt": "Intro", "currentVersionId": 3, "createdAt": "...", "updatedAt": "..." }
  ],
  "tags": [{ "id": 5, "siteId": 42, "name": "intro", "createdAt": "..." }],
  "latestVersions": {
    "en": { "id": 9, "postTranslationId": 1, "versionNumber": 2, "status": "DRAFT", "title": "...", "slug": "hello-world", "body": "...", "excerpt": null, "authorId": 7, "createdAt": "...", "publishedAt": null, "updatedAt": "..." }
  }
}
```

`status` is one of `DRAFT`, `SCHEDULED`, `PUBLISHED`, `ARCHIVED` in JSON responses
(uppercase) — but the `status` **query filter** on list takes the lowercase form
(`draft`, `scheduled`, `published`, `archived`). `ARCHIVED` currently has no endpoint
that sets it.

`latestVersions` is keyed by language, one entry per translation that has at least one
version — see [Post versions](#post-versions). It's the most recent version whether or
not it's published, so it's how you tell a translation has an unpublished draft sitting
on top of it without a second call. `POST` (create) is the one exception: it always
returns `{}` here, since a freshly created translation's live content and its version 1
are identical. A translation's own `currentVersionId` points at whichever version is
currently live — `null` until the translation has been published at least once.

## Errors

| Status | Code                     | When                                                                         |
|--------|--------------------------|------------------------------------------------------------------------------|
| 403    | `FORBIDDEN`              | caller lacks the role the action needs (e.g. `writer` calling version-publish) |
| 404    | `SITE_NOT_FOUND`         | site doesn't exist or you don't have access to it                            |
| 404    | `POST_NOT_FOUND`         | post doesn't exist under that site                                           |
| 404    | `POST_VERSION_NOT_FOUND` | version doesn't exist for that translation                                  |
| 409    | `SLUG_ALREADY_EXISTS` | a translation's slug collides with an existing one in the same site+language |

See [Errors](/docs/api/errors) for the response envelope and the full list of codes.
