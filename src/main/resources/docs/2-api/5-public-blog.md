# Public Blog API

Separate from the [Sites](/docs/api/sites)/[Posts](/docs/api/posts)/[Tags](/docs/api/tags)
management API above, every blog also exposes a small **public, unauthenticated** JSON
API of its own — for building a custom frontend against a blog's published content, or
wiring up view tracking from a custom theme.

These endpoints aren't nested under `/sites/{siteId}` and take no API key. The site is
resolved from the request itself, the same way the HTML pages are: by the `Host` header
for a [managed subdomain](/docs/guides/subdomains), or by `X-Site-Host` /
`X-Forwarded-Prefix` for [your own domain](/docs/guides/proxy-domains). Call them
*against the blog's own domain*, not `writeinone.com`.

## List published posts

```
GET https://myblog.writeinone.com/en/posts?page=0&size=10&tag=intro&search=hello
```

Same filters as the management [list posts](/docs/api/posts#list-posts) endpoint
(`page`, `size` capped at 100, `tag`, `search`), scoped to whichever site the domain
resolves to, and implicitly filtered to `published` posts only.

```json
{
  "content": [
    {
      "post": { "id": 1, "siteId": 42, "status": "PUBLISHED", "coverUrl": null, "viewCount": 12, "publishedAt": "2026-01-10T09:00:00Z", "scheduledAt": null, "createdAt": "...", "updatedAt": "..." },
      "translation": { "id": 1, "postId": 1, "siteId": 42, "lang": "en", "title": "Hello, world", "slug": "hello-world", "body": "# Hi\n\nFirst post.", "excerpt": "Intro", "createdAt": "...", "updatedAt": "..." },
      "tags": [{ "id": 5, "siteId": 42, "name": "intro", "createdAt": "..." }]
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

Unlike the management list endpoint, `translation` here is singular and carries the
full translation — `body` included — since there's only ever one language per request
and no owner-only content to hide.

## Record a view

```
POST https://myblog.writeinone.com/en/posts/hello-world/event
{ "type": "view" }
```

Increments the post's view counter. Returns `204 No Content` on success, `400` if
`type` isn't `"view"` (the only event type currently supported), `404` if the slug
doesn't resolve to a published post in that language.

This is what the default post page calls automatically when a reader opens a post; a
custom frontend built against the [list endpoint above](#list-published-posts) needs to
call it itself to keep view counts accurate.
