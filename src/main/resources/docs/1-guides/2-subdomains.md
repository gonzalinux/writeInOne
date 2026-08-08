# Subdomains

The fastest way to get a blog live: pick a label, and it's reachable immediately at
`{label}.writeinone.com` — no DNS, no certificate, no verification step. This is
covered by our wildcard certificate and DNS, so there's no proof of ownership to
provide.

## Creating one

On the site form's **General** tab, select **Free subdomain** and type a label into the
**Subdomain** field. As you type, the form checks availability and shows either
"`{label}.writeinone.com` is available" or the reason it isn't — the same checker used
when you rename a site later.

Click **Create site**. It comes back verified immediately, live as soon as you publish
a post — nothing else to configure.

## Label rules

- 3–30 characters
- lowercase letters, digits, and inner hyphens only (can't start or end with a hyphen)
- a handful of labels are reserved and can't be claimed: `www`, `admin`, `api`, `blog`,
  `mail`, `email`, `smtp`, `imap`, `verify`

## Renaming or releasing a label

Changing a site's subdomain (edit the site, change the **Subdomain** field, save) or
deleting the site parks the old label in a reservation held for **7 days**. During that
window nobody else can claim it — including you, from a different account. Reclaim it
yourself by creating or renaming a site to that same label again before the window
closes; after it closes, it's open to anyone.

## Moving to your own domain later

Nothing about a subdomain site is special or locked-in — switch it to a custom domain
at any time by editing the site and choosing **My own domain** instead. See
[Your Own Domain](/docs/guides/proxy-domains).
