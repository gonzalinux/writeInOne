(() => {
  // ── Site ID from URL ──────────────────────────────────────────────────
  const siteId = location.pathname.match(/\/sites\/([^/]+)\/style-tester/)?.[1];

  // ── DOM refs ──────────────────────────────────────────────────────────
  const frame        = document.getElementById('previewFrame');
  const cssEditor    = document.getElementById('cssEditor');
  const copyBtn      = document.getElementById('copyBtn');
  const loadBtn      = document.getElementById('loadBtn');
  const editorStatus = document.getElementById('editorStatus');
  const tooltip      = document.getElementById('selectorTooltip');
  const siteName     = document.getElementById('siteName');
  const editorTitle  = document.getElementById('editorSiteName');
  const tabs         = document.querySelectorAll('.preview-tab');

  // ── State ─────────────────────────────────────────────────────────────
  let blogCssText  = '';
  let currentView  = 'index';
  let site         = null;

  // ── Placeholder image SVG ─────────────────────────────────────────────
  const PLACEHOLDER = "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' width='720' height='315'><rect fill='%23e5e5e5' width='100%25' height='100%25'/><text x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' fill='%23888' font-size='16'>Cover Image</text></svg>";

  // ── Mock HTML ─────────────────────────────────────────────────────────
  const INDEX_MOCK_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>My Blog</title>
<style id="blog-css">__BLOG_CSS__</style>
<style id="user-css">__USER_CSS__</style>
</head>
<body data-selector="body">
<header data-selector="header">
  <div class="container">
    <nav data-selector="nav">
      <a class="site-name" href="#" data-selector=".site-name">My Blog</a>
      <div class="nav-right">
        <button class="theme-btn" onclick="document.documentElement.classList.toggle('dark')" title="Toggle dark mode">&#9680;</button>
      </div>
    </nav>
  </div>
</header>
<main class="container" data-selector=".container">
  <div class="site-hero" data-selector=".site-hero">
    <h1 class="site-hero__title" data-selector=".site-hero__title">My Blog</h1>
    <p class="site-description" data-selector=".site-description">A place where I write about things that matter to me.</p>
  </div>

  <form class="search-bar" data-selector=".search-bar" onsubmit="return false">
    <input class="search-bar__input" type="text" placeholder="Search posts…" data-selector=".search-bar__input"/>
    <button class="search-bar__btn" type="submit" data-selector=".search-bar__btn">Search</button>
    <a class="search-bar__clear" href="#" data-selector=".search-bar__clear">✕ Clear</a>
  </form>

  <p class="filter-label" data-selector=".filter-label">Filtering by tag: <strong>kotlin</strong></p>

  <ul class="post-list" data-selector=".post-list">
    <li class="post-card" data-selector=".post-card">
      <img class="post-card__cover" src="${PLACEHOLDER}" alt="" data-selector=".post-card__cover"/>
      <div class="post-card__meta" data-selector=".post-card__meta">
        <time>Mar 15, 2026</time>
      </div>
      <h2 class="post-card__title" data-selector=".post-card__title">
        <a href="#" data-selector=".post-card__title a">How I Built My First Reactive App</a>
      </h2>
      <p class="post-card__excerpt" data-selector=".post-card__excerpt">
        A deep dive into the challenges and rewards of building with Spring WebFlux and R2DBC from scratch.
      </p>
      <div class="post-card__tags" data-selector=".post-card__tags">
        <a class="tag" href="#" data-selector=".tag">webflux</a>
        <a class="tag" href="#" data-selector=".tag">kotlin</a>
        <a class="tag" href="#" data-selector=".tag">r2dbc</a>
      </div>
    </li>
    <li class="post-card" data-selector=".post-card">
      <div class="post-card__meta" data-selector=".post-card__meta">
        <time>Feb 28, 2026</time>
      </div>
      <h2 class="post-card__title" data-selector=".post-card__title">
        <a href="#" data-selector=".post-card__title a">The Joy of Plain CSS</a>
      </h2>
      <p class="post-card__excerpt" data-selector=".post-card__excerpt">
        Sometimes the best tool is no tool at all. Here's why I deleted my framework and wrote 300 lines of CSS.
      </p>
      <div class="post-card__tags" data-selector=".post-card__tags">
        <a class="tag" href="#" data-selector=".tag">css</a>
        <a class="tag" href="#" data-selector=".tag">design</a>
      </div>
    </li>
  </ul>

  <p class="empty-state" style="display:none" data-selector=".empty-state">No posts yet.</p>

  <nav class="pagination" data-selector=".pagination">
    <a class="pagination__btn" href="#" data-selector=".pagination__btn">← Newer</a>
    <span class="pagination__info" data-selector=".pagination__info">1 / 3</span>
    <a class="pagination__btn" href="#" data-selector=".pagination__btn">Older →</a>
  </nav>
</main>
<footer data-selector="footer">
  <div class="container">
    <p>© My Blog</p>
  </div>
</footer>
</body>
</html>`;

  const POST_MOCK_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>Post — My Blog</title>
<style id="blog-css">__BLOG_CSS__</style>
<style id="user-css">__USER_CSS__</style>
</head>
<body data-selector="body">
<header data-selector="header">
  <div class="container">
    <nav data-selector="nav">
      <a class="site-name" href="#" data-selector=".site-name">My Blog</a>
      <div class="nav-right">
        <button class="theme-btn" onclick="document.documentElement.classList.toggle('dark')" title="Toggle dark mode">&#9680;</button>
      </div>
    </nav>
  </div>
</header>
<main class="container" data-selector=".container">
  <article>
    <header class="post-header" data-selector=".post-header">
        <a class="post-header__back" href="#" data-selector=".post-header__back">&larr; All posts</a>
        <div class="post-header__meta" data-selector=".post-header__meta">
          <time>Mar 15, 2026</time>
        </div>
        <h1 class="post-header__title" data-selector=".post-header__title">How I Built My First Reactive App</h1>
        <p class="post-header__excerpt" data-selector=".post-header__excerpt">
          A deep dive into the challenges and rewards of building with Spring WebFlux and R2DBC from scratch.
        </p>
        <div class="post-header__tags" data-selector=".post-header__tags">
          <a class="tag" href="#" data-selector=".tag">webflux</a>
          <a class="tag" href="#" data-selector=".tag">kotlin</a>
        </div>
        <img class="post-header__cover" src="${PLACEHOLDER}" alt="" data-selector=".post-header__cover"/>
    </header>

      <div class="post-body" data-selector=".post-body">
        <h2 data-selector=".post-body h2">Getting Started</h2>
        <p data-selector=".post-body p">
          The first step was understanding the reactive paradigm. Unlike traditional blocking I/O,
          reactive streams process data asynchronously using <a href="#" data-selector=".post-body a">publishers and subscribers</a>.
        </p>

        <h3 data-selector=".post-body h3">Setting Up R2DBC</h3>
        <p data-selector=".post-body p">
          Configuring the database driver took a bit of experimentation. Here's what eventually worked:
        </p>
        <div class="code-block">
          <pre data-selector=".post-body pre"><code data-selector=".post-body pre code">spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5433/writeinone
    username: postgres
    password: secret</code></pre>
        </div>

        <h4 data-selector=".post-body h4">A smaller section heading</h4>
        <p data-selector=".post-body p">
          Inline code looks like this: <code data-selector=".post-body code">Mono&lt;ServerResponse&gt;</code>.
          It's styled differently from block code.
        </p>

        <blockquote data-selector=".post-body blockquote">
          <p>Reactive programming is about building systems that are responsive, resilient, elastic, and message-driven.</p>
        </blockquote>

        <ul data-selector=".post-body ul">
          <li data-selector=".post-body li">First item in an unordered list</li>
          <li data-selector=".post-body li">Second item with more text to show wrapping behavior</li>
          <li data-selector=".post-body li">Third item</li>
        </ul>

        <ol data-selector=".post-body ol">
          <li data-selector=".post-body li">First step</li>
          <li data-selector=".post-body li">Second step</li>
          <li data-selector=".post-body li">Third step</li>
        </ol>

        <hr data-selector=".post-body hr"/>

        <p data-selector=".post-body p">An image in the post body:</p>
        <img src="${PLACEHOLDER}" alt="Placeholder" data-selector=".post-body img"/>

        <p data-selector=".post-body p">A table with some data:</p>
        <table data-selector=".post-body table">
          <thead>
            <tr>
              <th data-selector=".post-body th">Library</th>
              <th data-selector=".post-body th">Purpose</th>
              <th data-selector=".post-body th">Version</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td data-selector=".post-body td">WebFlux</td>
              <td data-selector=".post-body td">Reactive HTTP</td>
              <td data-selector=".post-body td">3.x</td>
            </tr>
            <tr>
              <td data-selector=".post-body td">R2DBC</td>
              <td data-selector=".post-body td">Reactive SQL</td>
              <td data-selector=".post-body td">1.x</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
</main>
<footer data-selector="footer">
  <div class="container">
    <p>© My Blog</p>
  </div>
</footer>
</body>
</html>`;

  // ── Helpers ───────────────────────────────────────────────────────────

  function buildSrcdoc(view, userCss) {
    const template = view === 'post' ? POST_MOCK_HTML : INDEX_MOCK_HTML;
    return template
      .replace('__BLOG_CSS__', blogCssText)
      .replace('__USER_CSS__', userCss);
  }

  function updateCssOnly() {
    try {
      const doc = frame.contentDocument;
      if (!doc) return;
      const styleTag = doc.getElementById('user-css');
      if (styleTag) styleTag.textContent = cssEditor.value;
    } catch (_) {
      // cross-origin; fall back to full reload
      frame.srcdoc = buildSrcdoc(currentView, cssEditor.value);
    }
  }

  // ── Tooltip ───────────────────────────────────────────────────────────

  function attachTooltipListeners() {
    try {
      const doc = frame.contentDocument;
      if (!doc) return;

      doc.querySelectorAll('[data-selector]').forEach(el => {
        el.addEventListener('mouseenter', e => {
          const sel = el.dataset.selector;
          tooltip.textContent = sel;
          tooltip.style.display = 'block';
          editorStatus.textContent = sel;
          positionTooltip(e);
        });
        el.addEventListener('mousemove', positionTooltip);
        el.addEventListener('mouseleave', () => {
          tooltip.style.display = 'none';
          editorStatus.textContent = 'Hover an element in the preview to see its selector';
        });
      });
    } catch (_) {}
  }

  function positionTooltip(e) {
    const rect = frame.getBoundingClientRect();
    const x = rect.left + e.clientX;
    const y = rect.top  + e.clientY;
    tooltip.style.left = (x + 12) + 'px';
    tooltip.style.top  = (y - 28) + 'px';
  }

  // ── Debounce ──────────────────────────────────────────────────────────

  function debounce(fn, ms) {
    let timer;
    return (...args) => {
      clearTimeout(timer);
      timer = setTimeout(() => fn(...args), ms);
    };
  }

  // ── Fetch helpers ─────────────────────────────────────────────────────

  async function fetchBlogCss() {
    try {
      const res = await fetch('/css/blog.css');
      blogCssText = res.ok ? await res.text() : '';
    } catch (_) {
      blogCssText = '';
    }
  }

  async function fetchSite() {
    const res = await api(`/sites/${siteId}`);
    if (!res || !res.ok) return null;
    return res.json();
  }

  // ── Render ────────────────────────────────────────────────────────────

  function renderFrame() {
    frame.srcdoc = buildSrcdoc(currentView, cssEditor.value);
  }

  // ── Copy button ───────────────────────────────────────────────────────

  copyBtn.addEventListener('click', async () => {
    const text = cssEditor.value;
    try {
      await navigator.clipboard.writeText(text);
    } catch (_) {
      // execCommand fallback
      cssEditor.select();
      document.execCommand('copy');
      window.getSelection()?.removeAllRanges();
    }
    const orig = copyBtn.textContent;
    copyBtn.textContent = 'Copied!';
    setTimeout(() => { copyBtn.textContent = orig; }, 2000);
  });

  // ── Load button ───────────────────────────────────────────────────────

  loadBtn.addEventListener('click', async () => {
    if (!site?.stylesUrl) {
      alert('This site has no stylesUrl configured. Edit the site to add one.');
      return;
    }
    loadBtn.textContent = 'Loading…';
    loadBtn.disabled = true;
    try {
      const res = await fetch(site.stylesUrl);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      cssEditor.value = await res.text();
      updateCssOnly();
    } catch (err) {
      if (err.message.includes('Failed to fetch') || err instanceof TypeError) {
        alert('Could not fetch the CSS (CORS error or network issue). Try pasting the CSS manually.');
      } else {
        alert(`Failed to load CSS: ${err.message}`);
      }
    } finally {
      loadBtn.textContent = 'Load from site';
      loadBtn.disabled = false;
    }
  });

  // ── Tabs ──────────────────────────────────────────────────────────────

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      if (tab.dataset.view === currentView) return;
      currentView = tab.dataset.view;
      tabs.forEach(t => t.classList.toggle('active', t === tab));
      renderFrame();
    });
  });

  // ── Editor input ──────────────────────────────────────────────────────

  cssEditor.addEventListener('input', debounce(updateCssOnly, 250));

  // ── Frame load — re-attach tooltip listeners ──────────────────────────

  frame.addEventListener('load', attachTooltipListeners);

  // ── Init ──────────────────────────────────────────────────────────────

  async function init() {
    await fetchBlogCss();

    if (siteId) {
      site = await fetchSite();
      if (site) {
        const name = site.name || 'Untitled';
        siteName.textContent   = `Style Tester — ${name}`;
        editorTitle.textContent = name;
        if (!site.stylesUrl) {
          loadBtn.title = 'No stylesUrl configured for this site';
        }
      }
    }

    renderFrame();
  }

  init();
})();
