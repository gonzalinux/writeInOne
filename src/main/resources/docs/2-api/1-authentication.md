# Authentication

This section documents the **external API** — the same one the [WriteInOne MCP
server](/docs/api/mcp) uses. It's separate from the admin UI you'd use by hand (see the
[Guides](/docs/guides/quickstart) section for that).

Every request is authenticated with a service-account token, sent as a bearer token:

```
Authorization: Bearer a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9
```

Generate a token from the **Service Accounts** page in the admin UI — it's a random
64-character hex string with no prefix, shown to you once at creation time and stored
only as a hash server-side, so save it immediately. A token inherits
whatever access its owner has on a site — there's no separate scope system to
configure. Tokens can be revoked or rotated at any time; revoking one takes effect
immediately.

## Base URL

```
https://writeinone.com
```

All endpoints in this section are relative to this.
