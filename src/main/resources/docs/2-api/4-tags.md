# Tags

Tags are per-site labels attached to posts. There's no dedicated create endpoint —
tags come into existence the first time you reference a name in
[`POST` or `PUT /sites/{siteId}/posts/...`](/docs/api/posts): an unknown name is
created automatically, an existing one is reused.

## List tags

```
GET /sites/{siteId}/tags
```

```json
[
  { "id": 5, "siteId": 42, "name": "intro", "createdAt": "2026-01-10T09:00:00Z" },
  { "id": 6, "siteId": 42, "name": "meta", "createdAt": "2026-01-10T09:00:00Z" }
]
```

Returned alphabetically by name.

## Delete a tag

```
DELETE /sites/{siteId}/tags/{tagId}
```

Removes the tag from the site entirely (not just from one post). This call succeeds
even if `tagId` doesn't exist — it's a no-op in that case, not a `404`.

## Errors

| Status | Code | When |
|---|---|---|
| 404 | `SITE_NOT_FOUND` | site doesn't exist or you don't have access to it |

See [Errors](/docs/api/errors) for the response envelope and the full list of codes.
