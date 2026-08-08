# Your Own Domain

You can serve a blog from a domain you already own — either at the root
(`example.com`) or under a path (`example.com/blog`) — by putting your own reverse
proxy in front of it. We hold no certificate and provision no DNS for this mode; your
proxy owns that entirely, which is what makes it free and unlimited.

There's no support for a subdomain of *your* domain (`blog.example.com`) — only a path
prefix on a domain you already terminate TLS for. Per-user subdomains would need
per-site certificate provisioning at the gateway, which isn't available.

## 1. Point your proxy at WriteInOne

Your proxy needs to forward requests for the blog's path to WriteInOne, adding two
headers on every forwarded request:

| Header | Value |
|---|---|
| `X-Site-Host` | the domain to resolve, e.g. `example.com` |
| `X-Forwarded-Prefix` | the path the blog is mounted at, e.g. `/blog` (empty if mounted at the root) |

Get the prefix wrong and internal links on the blog will 404, since every page builds
its links from `X-Forwarded-Prefix`.

Example `nginx` config mounting the blog at `/blog`:

```nginx
location /blog/ {
    proxy_pass http://writeinone-upstream/blog/;
    proxy_set_header X-Site-Host   example.com;
    proxy_set_header X-Forwarded-Prefix /blog;
}
```

## 2. Set the domain on the site

On the site form's **General** tab, select **My own domain**, then fill in:

- **Domain** — e.g. `example.com`
- **Path prefix** (optional) — e.g. `blog`, if the blog lives at `example.com/blog`;
  leave it empty if it's served at the root

Save. The site is now **pending verification** and stays unreachable as a blog until
that passes.

## 3. Verify ownership

Once your proxy is wired up and forwarding correctly, the site card on your dashboard
shows a **Pending verification** badge. Click it — the dialog gives you the exact URL
WriteInOne needs to reach through your proxy (`https://example.com/blog/_verify`, or
without the prefix if mounted at the root). It's checked automatically every few
minutes; once it succeeds, the badge switches to **✓ Verified**.

You have 48 hours before an unverified attempt is marked **Verification expired**. If
that happens — or your proxy config changes later and breaks the header forwarding,
dropping an already-verified site back out — reopen the badge and click
**Request re-verification** once the proxy is fixed.

## Taking a domain from someone else's site

If the domain (or domain+prefix combination) is already claimed by another site, the
site form shows an error and won't save. There's no reservation window for custom
domains the way there is for [subdomain labels](/docs/guides/subdomains) — ownership is
proven by the verification round-trip, not by who claimed it first.
