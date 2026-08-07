# Sites

Sites are the top-level object you own — see [Custom Domains](/docs/guides/custom-domains)
for how `domain` and `prefix` determine where a site is actually reachable.

All endpoints below require the `access_token` cookie (see [Authentication](/docs/api/authentication))
and only ever operate on sites owned by the caller.

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

`domain` without dots is treated as a subdomain label (`my-blog.writeinone.com`); a full
domain is treated as a custom domain and starts out unverified.

## List your sites

```
GET /sites
```

Returns every site owned by the caller.

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
  "availableThemes": ["LIGHT", "DARK"],
  "languages": ["ENGLISH", "SPANISH"],
  "config": {}
}
```

All fields are optional — only send what changes. Set `requestVerification: true` when
moving to a custom domain to kick off DNS verification (see
[Custom Domains](/docs/guides/custom-domains)).

## Delete a site

```
DELETE /sites/{id}
```

Deletes the site and its posts. If the site used a subdomain label, that label is parked
in a reservation window before it can be claimed by anyone else.

## Check subdomain availability

```
GET /sites/subdomain             # rules: min/max length, reserved labels
GET /sites/subdomain?name=foo    # whether "foo" is available to the caller
```

Useful for validating a label client-side before calling `POST /sites` or
`PATCH /sites/{id}`.

## `config`

`config` is a small JSON object for per-language site chrome — currently favicon URL, and
per-language nav links / footer text. It's intentionally minimal; anything more elaborate
belongs in `customCss` or `stylesUrl`.
