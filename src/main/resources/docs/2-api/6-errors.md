# Errors

Every error response — across [Sites](/docs/api/sites), [Posts](/docs/api/posts),
[Tags](/docs/api/tags), and the [Public Blog API](/docs/api/public-blog) — uses the
same envelope:

```json
{ "error": "SITE_NOT_FOUND", "details": "Site with id 42 not found" }
```

`error` is a stable machine-readable code — match on this, not on `details` or the HTTP
status text. `details` is a human-readable string for logs/debugging and its exact
wording isn't guaranteed to stay stable between releases.

## All codes

| Status | Code                    | Where it can happen                                                               |
|--------|-------------------------|-----------------------------------------------------------------------------------|
| 400    | `VALIDATION_ERROR`      | `POST /sites` — request body fails field validation (e.g. blank `name`)           |
| 400    | `BAD_REQUEST`           | invalid nav link URL, `customCss` too long, domain is reserved/is the home domain |
| 401    | `UNAUTHORIZED`          | missing, invalid, or expired credentials on any authenticated endpoint            |
| 404    | `SITE_NOT_FOUND`        | site doesn't exist, or the caller doesn't have access to it                       |
| 404    | `POST_NOT_FOUND`        | post doesn't exist under the given site                                           |
| 404    | `NOT_FOUND`             | generic fallback for routes with no more specific not-found case                  |
| 409    | `SITE_DOMAIN_TAKEN`     | custom domain is already claimed by another site                                  |
| 409    | `SUBDOMAIN_NOT_ALLOWED` | subdomain label fails length/charset rules, or is reserved                        |
| 409    | `SUBDOMAIN_HELD`        | subdomain label is inside another user's reservation window                       |
| 409    | `SLUG_ALREADY_EXISTS`   | a post translation's slug collides with an existing one in the same site+language |
| 500    | `INTERNAL_SERVER_ERROR` | unhandled server error — safe to retry, report if persistent                      |

The [Public Blog API](/docs/api/public-blog) is unauthenticated, so it never returns
`401` — a domain that doesn't resolve to any site, or a slug with no published match,
comes back as a plain `404` instead.

The [MCP server](/docs/api/mcp) is the one exception to this envelope — it returns
standard JSON-RPC errors instead, since that's the format MCP clients expect.
