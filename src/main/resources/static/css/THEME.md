# WriteInOne — Theme CSS Reference

Your site's custom stylesheet is loaded **after** the default styles, so any selector you define there will override the
defaults. You only need to override what you want to change.

Set your stylesheet URL in the site settings under **Styles URL**.

---

## CSS variables

The easiest way to restyle your blog is to override the design tokens in `:root`. Changing a variable
propagates automatically to every element that uses it.

```css
:root {
    --bg:   #fff;     /* page background */
    --fg:   #1a1a1a;  /* primary text and links */
    --border: #e5e5e5; /* lines: header, footer, post dividers, table borders */
    --muted:   #888;  /* timestamps, back link, footer text, subtle labels */
    --muted-2: #555;  /* nav links, excerpt, secondary metadata */
    --muted-3: #444;  /* post card excerpt */
    --surface: #f6f6f6; /* table header cell background */

    /* Tag pills */
    --tag-bg: #f0f0f0;
    --tag-fg: #555;

    /* Blockquotes */
    --blockquote-border: #ddd;
    --blockquote-fg:     #666;

    /* Code blocks */
    --code-bg:        #f6f6f6; /* fenced code block background */
    --inline-code-bg: #f0f0f0; /* inline code background */

    /* Copy button inside code blocks */
    --copy-btn-bg:     #fff;
    --copy-btn-border: #ddd;
    --copy-btn-fg:     #555;
}
```

For dark mode, redefine the same variables under `html.dark`:

```css
html.dark {
    --bg:     #111;
    --fg:     #e0e0e0;
    --border: #2a2a2a;
    /* … and so on */
}
```

---

## Global elements

| Selector     | What it styles                                                        |
|--------------|-----------------------------------------------------------------------|
| `body`       | Page background, base font family, size, line height, and text colour |
| `a`          | Default link colour (inherits from parent by default)                 |
| `a:hover`    | Link hover state                                                      |
| `.container` | The centred content column (max-width 720 px with side padding)       |

---

## Header & navigation

The header appears at the top of every page.

| Selector             | What it styles                                  |
|----------------------|-------------------------------------------------|
| `header`             | The top bar — background, border, padding       |
| `nav`                | Flex row that holds the site name and nav links |
| `.site-name`         | The site title/logo link in the top-left        |
| `.nav-links`         | The `<ul>` list of navigation links             |
| `.nav-links a`       | Individual nav link text style                  |
| `.nav-links a:hover` | Nav link hover state                            |
| `.theme-btn`         | The light/dark toggle button in the nav bar     |
| `.theme-btn:hover`   | Theme button hover state                        |

---

## Footer

| Selector | What it styles                                      |
|----------|-----------------------------------------------------|
| `footer` | The bottom bar — border, spacing, font size, colour |

---

## Post list page

The index page that shows all published posts.

| Selector                    | What it styles                                                           |
|-----------------------------|--------------------------------------------------------------------------|
| `.site-hero`                | Wrapper around the site title / description shown at the top of the list |
| `.site-hero h1`             | Site title inside the hero                                               |
| `.site-description`         | Short description text below the site title                              |
| `.post-list`                | The `<ul>` that wraps all post cards                                     |
| `.post-card`                | A single post entry in the list                                          |
| `.post-card__cover`         | The cover image shown above the post card content                        |
| `.post-card__meta`          | Row containing the publication date (and any other metadata)             |
| `.post-card__title`         | The post title heading inside the card                                   |
| `.post-card__title a`       | The link wrapping the post title                                         |
| `.post-card__title a:hover` | Post title link hover state                                              |
| `.post-card__excerpt`       | The short excerpt text below the title                                   |
| `.post-card__tags`          | Flex row that wraps the tag pills on a card                              |
| `.empty-state`              | Message shown when no published posts exist yet                          |

---

## Post detail page

The full post view.

| Selector                   | What it styles                                                      |
|----------------------------|---------------------------------------------------------------------|
| `.post-header`             | Wrapper for all post header content (back link, meta, title, cover) |
| `.post-header__back`       | The "← All posts" back link                                         |
| `.post-header__back:hover` | Back link hover state                                               |
| `.post-header__meta`       | Row containing the publication date                                 |
| `.post-header__title`      | The large post title (`<h1>`)                                       |
| `.post-header__excerpt`    | The italic excerpt / subtitle below the title                       |
| `.post-header__tags`       | Flex row of tag pills shown in the post header                      |
| `.post-header__cover`      | The full-width cover image below the post header                    |
| `.post-body`               | Wrapper `<div>` around the rendered Markdown content                |

### Post body — Markdown elements

These selectors let you style the rendered content of your posts. All are scoped inside `.post-body` so they don't
affect the rest of the page.

| Selector                                                        | What it styles                                                 |
|-----------------------------------------------------------------|----------------------------------------------------------------|
| `.post-body h1` `.post-body h2` `.post-body h3` `.post-body h4` | Headings inside the post                                       |
| `.post-body p`                                                  | Paragraphs                                                     |
| `.post-body ul` `.post-body ol`                                 | Bulleted and numbered lists                                    |
| `.post-body li`                                                 | Individual list items                                          |
| `.post-body blockquote`                                         | Block quotes (has a left border by default)                    |
| `.post-body pre`                                                | Fenced code blocks                                             |
| `.post-body code`                                               | Inline code snippets                                           |
| `.post-body pre code`                                           | Code inside a fenced block (resets the inline code background) |
| `.post-body img`                                                | Images embedded in the post                                    |
| `.post-body a`                                                  | Links inside the post body                                     |
| `.post-body hr`                                                 | Horizontal rules (`---`)                                       |
| `.post-body table`                                              | Tables                                                         |
| `.post-body th` `.post-body td`                                 | Table header and data cells                                    |
| `.post-body th`                                                 | Table header cell background                                   |

---

## Shared components

| Selector | What it styles                                                           |
|----------|--------------------------------------------------------------------------|
| `.tag`   | A tag pill — used on both the post list cards and the post detail header |

---

## Dark mode

When the site has `availableThemes` set to both `LIGHT` and `DARK`, the blog adds `class="dark"` to the `<html>` element
whenever dark mode is active (either via the toggle button or `prefers-color-scheme`). You can target this class in your
custom stylesheet to apply dark-mode overrides.

## Example: light defaults with dark overrides

```css
/* Light defaults */
body {
    background: #fff;
    color: #1a1a1a;
}

header, footer {
    border-color: #e5e5e5;
}

.tag {
    background: #f0f0f0;
    color: #555;
}

.post-body pre {
    background: #f6f6f6;
}

/* Dark overrides — applied when html.dark is set */
html.dark body {
    background: #0f0f0f;
    color: #e8e8e8;
}

html.dark header,
html.dark footer {
    border-color: #2a2a2a;
}

html.dark .site-name {
    color: #fff;
}

html.dark .nav-links a {
    color: #aaa;
}

html.dark .tag {
    background: #2a2a2a;
    color: #aaa;
}

html.dark .post-body blockquote {
    border-left-color: #444;
    color: #aaa;
}

html.dark .post-body pre {
    background: #1a1a1a;
}

html.dark .post-body code {
    background: #1e1e1e;
    color: #e8e8e8;
}
```
