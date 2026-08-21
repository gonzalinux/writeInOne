# MCP server

WriteInOne exposes an [MCP](https://modelcontextprotocol.io) server at `POST /mcp` so AI
agents (Claude Code, Cursor, Codex, etc.) can create posts directly. It's hosted
alongside the rest of the API — there's nothing separate to install or run.

Authentication is the same [bearer token](/docs/api/authentication) as the REST API. A
key inherits whatever access its owner has on a site, same as everywhere else — an MCP
tool call for a site the key's owner isn't a member of behaves exactly like the REST API
would: a `404`-equivalent error, not a `403`, so the tool call never reveals whether the
site exists.

## Connecting a client

Point your MCP client at:

```
https://writeinone.com/mcp
```

with an `Authorization: Bearer <key>` header, generated from the **Service Accounts**
page in the admin UI. The exact config shape depends on your client:

### Claude Code

```bash
claude mcp add --transport http writeinone https://writeinone.com/mcp \
  --header "Authorization: Bearer a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9"
```

This writes the server to local scope. Add `--scope project` to share it via `.mcp.json`
instead, so the rest of the team gets it when they clone the repo (they'll still need
their own key).

### Claude Cowork (also claude.ai and Claude Desktop)

These share one connector UI. Go to **Customize > Connectors > Add custom connector**,
paste `https://writeinone.com/mcp` as the URL, then open **Request headers** and add:

| Field | Value |
|-------|-------|
| Header name | `authorization` |
| Header value | `Bearer a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9` |
| Required | yes |

Request header authentication is currently in beta on claude.ai — if it isn't available
on your account yet, ask your Anthropic contact for access.

### Cursor

Add to `.cursor/mcp.json` (project) or `~/.cursor/mcp.json` (global):

```json
{
  "mcpServers": {
    "writeinone": {
      "url": "https://writeinone.com/mcp",
      "headers": { "Authorization": "Bearer a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9" }
    }
  }
}
```

### Codex

Add to `~/.codex/config.toml` (or `.codex/config.toml` for a trusted project), with the
key in an environment variable rather than the file itself:

```toml
[mcp_servers.writeinone]
url = "https://writeinone.com/mcp"
bearer_token_env_var = "WRITEINONE_MCP_KEY"
```

or equivalently from the CLI:

```bash
codex mcp add writeinone --url https://writeinone.com/mcp \
  --bearer-token-env-var WRITEINONE_MCP_KEY
```

### opencode

Add to `opencode.json`:

```json
{
  "mcp": {
    "writeinone": {
      "type": "remote",
      "url": "https://writeinone.com/mcp",
      "headers": { "Authorization": "Bearer a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9" }
    }
  }
}
```

The server is stateless: it never issues a session ID and responds to every request
with a single JSON object rather than opening an SSE stream. This is standard
[Streamable HTTP](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports)
behavior — session assignment is optional in the spec — and every major client already
supports it.

## Tools

| Tool | Does |
|------|------|
| `list_sites` | List every site the key's owner can access, with their role on each |
| `list_posts` | List posts for a site, filterable by `status`, `tag`, `search` |
| `get_post` | Fetch a single post's **live (published)** content by `siteId` + `lang` + `slug` |
| `list_tags` | List tags for a site |
| `create_draft` | Create a new draft post (`status: draft`) with one or more language translations |
| `edit` | Edit an existing post's translation(s) — creates a new **draft version** per [post versioning](/docs/api/posts#post-versions) instead of touching live content |
| `list_versions` | List the version history (draft and published) for one translation of a post |
| `publish` | Publish a post — sets it live and publishes any translation that's never gone live before |
| `publish_version` | Publish a specific draft version, making it the live content (also how rollback works) |
| `schedule` | Schedule a post to publish automatically at a future time |

Call `tools/list` on a connected client for the full JSON Schema of each tool's
arguments.

**`create_draft` only creates brand-new posts** — it can't edit an existing one. Use
`edit` for that: for a language the post already has a live translation in, it adds a new
draft version on top without touching what's published; for a language the post doesn't
have yet, that translation is created directly, same as `create_draft`, since there's
nothing published yet to protect. `edit` never changes a post's cover image or tags.

**Publishing is not gated by which tools exist — it's gated by role**, same as the REST
API: `publish`, `publish_version`, and `schedule` all require `editor` or `admin` on the
site (`PostService`'s existing `requirePublish()` check). Grant a service account the
`writer` role if you want it to create and edit drafts but never take anything live —
calling any of the three publish tools then fails with a normal `-32002` permission
error, the same as it would over the REST API.

## Errors

MCP errors use the standard JSON-RPC envelope, not the `{error, details}` shape the rest
of the [API](/docs/api/errors) uses:

```json
{ "jsonrpc": "2.0", "id": 1, "error": { "code": -32001, "message": "Site with id 42 not found" } }
```

| Code | Meaning |
|------|---------|
| `-32001` | Not found — the site/post doesn't exist, or the key's owner has no access to it |
| `-32002` | Forbidden — the key's owner has access but not the role the action needs |
| `-32601` | Method or tool not found |
| `-32602` | Invalid params — missing/malformed arguments, or an unsupported combination (e.g. passing `postId` to `create_draft`) |
| `-32603` | Internal error — safe to retry, report if persistent |
