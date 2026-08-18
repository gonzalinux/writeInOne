# Quickstart

WriteInOne lets you run one or more blogs from a single account, all managed from the
admin dashboard at `/admin`. This page walks through the smallest path from a new
account to a published post.

## The data model

```
Account
 └─ Site       (a blog — has a domain, languages, theme, nav/footer config)
     ├─ Post   (one per URL — has a status: draft / scheduled / published)
     │   └─ Translation   (one per language — title, slug, body, excerpt)
     └─ Tag    (per site, attached to posts by name)
```

A **site** is a single blog, reachable at a managed subdomain
(`myblog.writeinone.com`) or your own domain behind a reverse proxy
(`example.com/blog`) — see [Subdomains](/docs/guides/subdomains) and
[Your Own Domain](/docs/guides/proxy-domains). A **post** holds one translation per
language it's written in, written as Markdown.

## 1. Create an account

Go to `/admin/register` and fill in your name, email, and a password (at least 4
characters). We email you a 6-digit code — enter it on the next screen to verify your
address, which logs you straight in.

## 2. Create a site

From the dashboard, click **+ New site**. On the **General** tab:

- Give it a **Site name**.
- Under "Where will your blog live?", pick **Free subdomain** and type a label — the
  form checks availability as you type — or **My own domain** if you're hosting behind
  your own reverse proxy (see [Your Own Domain](/docs/guides/proxy-domains)).

Click **Create site**. A subdomain site is live immediately; a custom domain needs one
more verification step, explained on its own page.

The **Appearance**, **Languages**, and **Code** tabs let you set a styles URL, enable
extra languages with their own nav/footer, and inject head/body HTML — none of it
required to get started. See [Theming](/docs/guides/theming) for the Appearance tab.

## 3. Write a post

From the dashboard, click **Posts** on your new site's card, then **+ New post**. Each
enabled language gets its own tab with a title and a Markdown body; slug, tags, and a
cover image URL are optional (slug is generated from the title if you leave it blank).

Click **Save draft**.

## 4. Publish it

Open the post again and click **Publish** (or **Schedule** to pick a future date and
time instead — the post goes live automatically once it arrives). The post is now live
at your site's URL, e.g. `https://my-blog.writeinone.com/en/articles/hello-world`.

## Next steps

- [Subdomains](/docs/guides/subdomains) — the fastest way to get a blog live.
- [Your Own Domain](/docs/guides/proxy-domains) — host under a domain you control.
- [Theming](/docs/guides/theming) — restyle a blog with the Style Editor or your own stylesheet.
- Building an integration instead of clicking through the UI? See the
  [API reference](/docs/api/authentication) for plain HTTP calls, or the
  [MCP server](/docs/api/mcp) docs to connect an AI agent (Claude Code, Cursor, etc.)
  directly.
