# Getting Started

WriteInOne lets you run one or more blogs from a single account. Each blog is a **site**,
sites own **posts**, and posts can be written in multiple languages.

This guide walks through the smallest path from a new account to a published post.

## 1. Create an account

```
POST /auth/register
{
  "email": "you@example.com",
  "displayName": "Your Name",
  "password": "at-least-4-chars"
}
```

This sends a verification code to your email. Confirm it before logging in:

```
POST /auth/verify-email
{
  "email": "you@example.com",
  "code": "123456"
}
```

A successful verification logs you in immediately — the response sets the `access_token`
and `refresh_token` cookies described in [Authentication](/docs/api/authentication).

## 2. Create a site

```
POST /sites
{
  "name": "My Blog",
  "domain": "my-blog",
  "languages": ["ENGLISH"]
}
```

If `domain` doesn't already look like a full domain, it's treated as a subdomain label and
the site becomes reachable at `my-blog.writeinone.com` right away — no DNS setup required.
See [Custom Domains](/docs/guides/custom-domains) if you'd rather host under your own domain.

## 3. Write a post

```
POST /sites/{siteId}/posts
{
  "translations": {
    "en": {
      "title": "Hello, world",
      "body": "# Hello\n\nThis is my first post.",
      "excerpt": "A short introduction."
    }
  },
  "tags": ["intro"]
}
```

`body` is Markdown — it's rendered to HTML when the post is served.

## 4. Publish it

```
POST /sites/{siteId}/posts/{postId}/publish
```

The post is now live at `https://my-blog.writeinone.com/en/articles/hello-world` (the slug
is derived from the title unless you set one explicitly on the translation).

## Next steps

- [Custom Domains](/docs/guides/custom-domains) — host under your own domain instead of a subdomain.
- [Sites API](/docs/api/sites) — full reference for site management, including themes and per-language nav/footer config.
