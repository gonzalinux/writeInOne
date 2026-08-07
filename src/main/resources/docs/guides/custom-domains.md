# Custom Domains

A site is reachable in one of two ways. Both are stored the same way internally — as a
`domain` plus a `prefix` on the site — but they behave differently.

## Managed subdomain

`myblog.writeinone.com`

This is covered by our wildcard certificate and DNS, so it needs no proof of ownership.
Creating a site with a bare label (no dots) gives you this automatically:

```
POST /sites
{ "name": "My Blog", "domain": "myblog" }
```

The site is created **verified** immediately, with an empty prefix. There's nothing else
to do — it's live as soon as you publish a post.

Subdomain labels are 3–30 characters, and a handful are reserved (`www`, `admin`, `api`,
`blog`, `mail`, `email`, `smtp`, `imap`, `verify`). Renaming or deleting a site parks its
old label for a reservation window before anyone else can claim it — if it was yours, you
can reclaim it within that window.

Use `GET /sites/subdomain?name=myblog` to check whether a label is available before
creating or renaming a site.

## Your own domain

`example.com/blog`

If you'd rather serve the blog from a domain you already own — optionally under a path —
set `domain` to your full domain and, if needed, a `prefix`:

```
PATCH /sites/{id}
{
  "domain": "example.com",
  "prefix": "/blog",
  "requestVerification": true
}
```

This requires two things on your side:

1. **A reverse proxy in front of your domain** that forwards requests for the blog to
   WriteInOne, sending two headers:
   - `X-Site-Host` — the domain to resolve (`example.com`)
   - `X-Forwarded-Prefix` — the path the blog is mounted at (`/blog`)
2. **DNS ownership verification** — set `requestVerification: true` and visit
   `https://example.com/blog/_verify` once your proxy is wired up. The site stays
   unverified (and unreachable as a blog) until this check passes.

There's no support for subdomains of *your own* domain (e.g. `blog.example.com`) — that
would need per-site certificate provisioning at the gateway, which isn't available yet.
Use a path prefix on your root domain instead.

## Which one should I use?

Use a managed subdomain if you just want to start writing. Switch to a custom domain once
you have a reverse proxy you control and want the blog under your own brand.
