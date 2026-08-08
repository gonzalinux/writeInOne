# Sites

A site is the top-level object you own — a single blog, with its own domain, languages,
theme, and nav/footer chrome. See [Subdomains](/docs/guides/subdomains) and
[Your Own Domain](/docs/guides/proxy-domains) for how `domain` and `prefix` determine
where a site is actually reachable.

Every endpoint below requires the `Authorization: Bearer <key>` header (see
[Authentication](/docs/api/authentication)) and only ever operates on sites the key's
owner has access to.

## Create a site

```
POST /sites
{
  "name": "My Blog",
  "domain": "my-blog",
  "description": "Optional description",
  "stylesUrl": null,
  "customCss": null,
  "availableThemes": ["LIGHT"],
  "languages": ["ENGLISH"],
  "config": {}
}
```

`domain` without dots is treated as a subdomain label (`my-blog.writeinone.com`) and the
site is created `VERIFIED` immediately. A full domain (containing a dot) is treated as a
custom domain and starts out `NOT_VERIFIED` — see [Your Own Domain](/docs/guides/proxy-domains).

`availableThemes` is any of `LIGHT`, `DARK`; `languages` is any of `ENGLISH`, `SPANISH`.
Both default to a single-item list (`LIGHT`, `ENGLISH`) if omitted.

Returns the created [`Site`](#the-site-object).

## List your sites

```
GET /sites
```

Returns every site you have access to, as a plain JSON array (no pagination).

## Get a site

```
GET /sites/{id}
```

## Update a site

```
PATCH /sites/{id}
{
  "name": "New name",
  "domain": "example.com",
  "prefix": "/blog",
  "requestVerification": true,
  "stylesUrl": "https://example.com/blog.css",
  "customCss": null,
  "availableThemes": ["LIGHT", "DARK"],
  "languages": ["ENGLISH", "SPANISH"],
  "config": {}
}
```

All fields are optional — only send what changes; omitted fields are left untouched.
`customCss` is the one exception: sending `""` explicitly clears it, since `null` means
"don't touch."

Set `requestVerification: true` when moving to a custom domain, or to force
re-verification of one that's already set, to kick off DNS/proxy verification (see
[Your Own Domain](/docs/guides/proxy-domains)). Changing `domain` on a subdomain site
parks the old label in a reservation for other users (see
[Subdomains](/docs/guides/subdomains)).

`prefix` only applies to custom domains — it's forced back to `""` for managed
subdomains regardless of what you send. Must match
`^/?[a-zA-Z0-9-]{0,20}$` (alphanumeric and dashes, max 20 characters).

## Delete a site

```
DELETE /sites/{id}
```

Deletes the site and its posts. If the site used a subdomain label, that label is parked
in a reservation window before it can be claimed by anyone else.

## Check subdomain availability

```
GET /sites/subdomain             # rules: min/max length, base domain
GET /sites/subdomain?name=foo    # whether "foo" is available
```

Always returns `200`, even when unavailable — see
[Subdomains](/docs/guides/subdomains#checking-availability-first) for the response
shape. Useful for validating a label client-side before calling `POST /sites` or
`PATCH /sites/{id}`.

## The `Site` object

```json
{
  "id": 42,
  "userId": 7,
  "name": "My Blog",
  "domain": "my-blog.writeinone.com",
  "prefix": "",
  "description": null,
  "stylesUrl": null,
  "customCss": null,
  "availableThemes": ["LIGHT"],
  "languages": ["ENGLISH"],
  "config": { "faviconUrl": null, "headHtml": null, "bodyHtml": null, "en": {}, "es": {} },
  "status": "VERIFIED",
  "createdAt": "2026-01-10T09:00:00Z",
  "updatedAt": "2026-01-10T09:00:00Z",
  "verifyDate": "2026-01-10T09:00:00Z"
}
```

`status` is `VERIFIED` or `NOT_VERIFIED`.

## `config`

A small JSON object for per-language site chrome:

```json
{
  "faviconUrl": "https://example.com/favicon.ico",
  "headHtml": null,
  "bodyHtml": null,
  "en": {
    "title": "My Blog",
    "description": "Notes on building things",
    "footer": "© 2026 My Blog",
    "nav": [{ "label": "About", "url": "/en/about" }]
  },
  "es": { "footer": "", "nav": [] }
}
```

Every `nav[].url` must start with `http://`, `https://`, or `/` — anything else is
rejected with `400 BAD_REQUEST`. It's intentionally minimal; anything more elaborate
belongs in `customCss` (max 25,000 characters) or `stylesUrl`.

## Errors

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `POST /sites` body fails field validation (e.g. blank `name`) |
| 400 | `BAD_REQUEST` | invalid nav link URL, `customCss` too long, domain is a reserved/home domain |
| 404 | `SITE_NOT_FOUND` | site doesn't exist or you don't have access to it |
| 409 | `SITE_DOMAIN_TAKEN` | custom domain already claimed by another site |
| 409 | `SUBDOMAIN_NOT_ALLOWED` | label fails length/charset rules or is reserved |
| 409 | `SUBDOMAIN_HELD` | label is inside another user's reservation window |

See [Errors](/docs/api/errors) for the response envelope and the full list of codes.
