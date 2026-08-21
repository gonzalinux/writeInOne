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
  --header "Authorization: Bearer wio_live_51H8x..."
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
| Header value | `Bearer wio_live_51H8x...` |
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
      "headers": { "Authorization": "Bearer wio_live_51H8x..." }
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
      "headers": { "Authorization": "Bearer wio_live_51H8x..." }
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

Call `tools/list` on a connected client for the full JSON Schema of each tool's
arguments.

**`create_draft` only creates brand-new posts.** It can't add a translation to, or edit,
an existing post, and there's no `propose_edit` or `list_versions` tool yet — that needs
a versioning system that doesn't exist yet. Publishing and rollback are also
intentionally **not** exposed as tools: only a human, from the admin UI, can take a
draft live. This is deliberate — it's the approval step the whole feature is built
around, so an agent's output is never one API call away from being public.

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
