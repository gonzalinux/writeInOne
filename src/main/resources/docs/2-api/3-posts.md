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

| Param | Default | Notes |
|---|---|---|
| `page` | `0` | zero-indexed |
| `size` | `10` | no upper bound enforced |
| `status` | — | one of `draft`, `scheduled`, `published`, `archived` (lowercase) |
| `tag` | — | exact tag name |
| `search` | — | case-insensitive match against translation titles, any language |

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
- `translations` is a partial upsert per language: languages you include are
  created-or-overwritten; languages you omit are left as they are. There's no way to
  delete a single translation through this endpoint.
- Same slug-collision rule as create (`409 SLUG_ALREADY_EXISTS`).

Returns the post with its full, current set of translations and tags (not just the ones
you sent).

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
    { "id": 1, "postId": 1, "siteId": 42, "lang": "en", "title": "Hello, world", "slug": "hello-world", "body": "# Hi\n\nFirst post.", "excerpt": "Intro", "createdAt": "...", "updatedAt": "..." }
  ],
  "tags": [{ "id": 5, "siteId": 42, "name": "intro", "createdAt": "..." }]
}
```

`status` is one of `DRAFT`, `SCHEDULED`, `PUBLISHED`, `ARCHIVED` in JSON responses
(uppercase) — but the `status` **query filter** on list takes the lowercase form
(`draft`, `scheduled`, `published`, `archived`). `ARCHIVED` currently has no endpoint
that sets it.

## Errors

| Status | Code | When |
|---|---|---|
| 404 | `SITE_NOT_FOUND` | site doesn't exist or you don't have access to it |
| 404 | `POST_NOT_FOUND` | post doesn't exist under that site |
| 409 | `SLUG_ALREADY_EXISTS` | a translation's slug collides with an existing one in the same site+language |

See [Errors](/docs/api/errors) for the response envelope and the full list of codes.
