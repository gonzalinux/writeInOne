# Authentication

This section documents the **external API** — the same one the [WriteInOne MCP
server](/docs/api/mcp) uses. It's separate from the admin UI you'd use by hand (see the
[Guides](/docs/guides/quickstart) section for that).

Every request is authenticated with a service-account token, sent as a bearer token:

```
Authorization: Bearer wio_live_51H8x...
```

Generate a token from the **Service Accounts** page in the admin UI. A token inherits
whatever access its owner has on a site — there's no separate scope system to
configure. Tokens can be revoked or rotated at any time; revoking one takes effect
immediately.

## Base URL

```
https://writeinone.com
```

All endpoints in this section are relative to this.
