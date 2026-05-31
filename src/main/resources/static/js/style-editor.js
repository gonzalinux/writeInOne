(() => {
  // ── Site ID from URL ──────────────────────────────────────────────────
  const siteId = location.pathname.match(/\/sites\/([^/]+)\/style-editor/)?.[1];

  // ── DOM refs ──────────────────────────────────────────────────────────
  const frame          = document.getElementById('previewFrame');
  const cssTextarea    = document.getElementById('cssEditor');
  const defaultsArea   = document.getElementById('defaultsEditor');
  const copyBtn        = document.getElementById('copyBtn');
  const loadBtn        = document.getElementById('loadBtn');
  const saveBtn        = document.getElementById('saveBtn');
  const charCount      = document.getElementById('charCount');
  const editorStatus   = document.getElementById('editorStatus');
  const tooltip        = document.getElementById('selectorTooltip');
  const siteName       = document.getElementById('siteName');
  const editorTitle    = document.getElementById('editorSiteName');
  const tabCustom      = document.getElementById('tabCustom');
  const tabDefaults    = document.getElementById('tabDefaults');
  const tabs           = document.querySelectorAll('.preview-tab[data-view]');

  // ── CodeMirror editor ─────────────────────────────────────────────────
  const SITE_VARS = [
    '--bg','--fg','--border','--muted','--muted-2','--muted-3','--surface',
    '--tag-bg','--tag-fg','--blockquote-border','--blockquote-fg',
    '--code-bg','--inline-code-bg','--copy-btn-bg','--copy-btn-border','--copy-btn-fg',
  ];

  const editor = CodeMirror.fromTextArea(cssTextarea, {
    mode: 'css',
    lineNumbers: true,
    indentWithTabs: false,
    indentUnit: 2,
    tabSize: 2,
    lineWrapping: false,
    autofocus: false,
    extraKeys: { 'Ctrl-Space': cm => cm.showHint({ completeSingle: false }) },
  });

  const defaultsViewer = CodeMirror.fromTextArea(defaultsArea, {
    mode: 'css',
    lineNumbers: true,
    readOnly: true,
    lineWrapping: false,
  });
  defaultsViewer.getWrapperElement().style.display = 'none';
  defaultsViewer.getWrapperElement().style.flex = '1';
  defaultsViewer.getWrapperElement().style.height = '0';

  tabCustom.addEventListener('click', () => {
    tabCustom.classList.add('active');
    tabDefaults.classList.remove('active');
    editor.getWrapperElement().style.display = '';
    defaultsViewer.getWrapperElement().style.display = 'none';
    charCount.style.display = '';
    copyBtn.onclick = () => copyText(editor.getValue());
    editor.refresh();
  });

  tabDefaults.addEventListener('click', () => {
    tabDefaults.classList.add('active');
    tabCustom.classList.remove('active');
    editor.getWrapperElement().style.display = 'none';
    defaultsViewer.getWrapperElement().style.display = '';
    charCount.style.display = 'none';
    copyBtn.onclick = () => copyText(defaultsViewer.getValue());
    defaultsViewer.refresh();
  });

  editor.on('inputRead', (cm, change) => {
    if (change.origin === '+input' && /[\w-]/.test(change.text[0])) {
      const cursor = cm.getCursor();
      const token = cm.getTokenAt(cursor);
      if (token.string.startsWith('--')) {
        const typed = token.string;
        const list = SITE_VARS.filter(v => v.startsWith(typed));
        if (list.length) {
          cm.showHint({
            completeSingle: false,
            hint: () => ({
              list,
              from: CodeMirror.Pos(cursor.line, token.start),
              to:   CodeMirror.Pos(cursor.line, token.end),
            }),
          });
        }
        return;
      }
      cm.showHint({ completeSingle: false });
    }
  });

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
  <div class="container" data-selector=".container">
    <nav data-selector="nav">
      <a class="site-name" href="#" data-selector=".site-name">My Blog</a>
      <div class="nav-right" data-selector=".nav-right">
        <a class="rss-btn" href="#" data-selector=".rss-btn" title="RSS feed">
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="6.18" cy="17.82" r="2.18"/><path d="M4 4.44v2.83c7.03 0 12.73 5.7 12.73 12.73h2.83c0-8.59-6.97-15.56-15.56-15.56zm0 5.66v2.83c3.9 0 7.07 3.17 7.07 7.07h2.83c0-5.47-4.43-9.9-9.9-9.9z"/></svg>
        </a>
        <button class="theme-btn" onclick="document.documentElement.classList.toggle('dark')" title="Toggle dark mode" data-selector=".theme-btn">&#9680;</button>
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
  <div class="container" data-selector=".container">
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
  <div class="container" data-selector=".container">
    <nav data-selector="nav">
      <a class="site-name" href="#" data-selector=".site-name">My Blog</a>
      <div class="nav-right" data-selector=".nav-right">
        <a class="rss-btn" href="#" data-selector=".rss-btn" title="RSS feed">
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="6.18" cy="17.82" r="2.18"/><path d="M4 4.44v2.83c7.03 0 12.73 5.7 12.73 12.73h2.83c0-8.59-6.97-15.56-15.56-15.56zm0 5.66v2.83c3.9 0 7.07 3.17 7.07 7.07h2.83c0-5.47-4.43-9.9-9.9-9.9z"/></svg>
        </a>
        <button class="theme-btn" onclick="document.documentElement.classList.toggle('dark')" title="Toggle dark mode" data-selector=".theme-btn">&#9680;</button>
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
        <div class="code-block" data-selector=".code-block">
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
  <div class="container" data-selector=".container">
    <p>© My Blog</p>
  </div>
</footer>
</body>
</html>`;

  const NOT_FOUND_MOCK_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>404 — My Blog</title>
<style id="blog-css">__BLOG_CSS__</style>
<style id="user-css">__USER_CSS__</style>
</head>
<body data-selector="body">
<header data-selector="header">
  <div class="container" data-selector=".container">
    <nav data-selector="nav">
      <a class="site-name" href="#" data-selector=".site-name">My Blog</a>
      <div class="nav-right" data-selector=".nav-right">
        <a class="rss-btn" href="#" data-selector=".rss-btn" title="RSS feed">
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="6.18" cy="17.82" r="2.18"/><path d="M4 4.44v2.83c7.03 0 12.73 5.7 12.73 12.73h2.83c0-8.59-6.97-15.56-15.56-15.56zm0 5.66v2.83c3.9 0 7.07 3.17 7.07 7.07h2.83c0-5.47-4.43-9.9-9.9-9.9z"/></svg>
        </a>
        <button class="theme-btn" onclick="document.documentElement.classList.toggle('dark')" title="Toggle dark mode" data-selector=".theme-btn">&#9680;</button>
      </div>
    </nav>
  </div>
</header>
<main class="container" data-selector=".container">
  <div class="not-found" data-selector=".not-found">
    <p class="not-found__code" data-selector=".not-found__code">404</p>
    <h1 class="not-found__title" data-selector=".not-found__title">Page not found</h1>
    <p class="not-found__message" data-selector=".not-found__message">The page you're looking for doesn't exist or has been moved.</p>
    <a class="not-found__back post-header__back" href="#" data-selector=".not-found__back">&larr; All posts</a>
  </div>
</main>
<footer data-selector="footer">
  <div class="container" data-selector=".container">
    <p>© My Blog</p>
  </div>
</footer>
</body>
</html>`;

  // ── Helpers ───────────────────────────────────────────────────────────

  function buildSrcdoc(view, userCss) {
    const template = view === 'post' ? POST_MOCK_HTML : view === 'not-found' ? NOT_FOUND_MOCK_HTML : INDEX_MOCK_HTML;
    return template
      .replace('__BLOG_CSS__', blogCssText)
      .replace('__USER_CSS__', userCss);
  }

  function updateCssOnly() {
    try {
      const doc = frame.contentDocument;
      if (!doc) return;
      const styleTag = doc.getElementById('user-css');
      if (styleTag) styleTag.textContent = editor.getValue();
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

      doc.querySelectorAll('a').forEach(a => {
        a.addEventListener('click', e => e.preventDefault());
      });

      function fullSelector(el) {
        const parts = [];
        let cur = el;
        while (cur && cur !== doc.documentElement) {
          if (cur.dataset && cur.dataset.selector && cur.dataset.selector !== 'body') parts.unshift(cur.dataset.selector);
          cur = cur.parentElement;
        }
        return parts.join(' ');
      }

      doc.querySelectorAll('[data-selector]').forEach(el => {
        el.addEventListener('click', e => {
          e.preventDefault();
          e.stopPropagation();
          const sel = el.dataset.selector;
          const current = editor.getValue();
          const block = `\n\n${sel} {\n  \n}`;
          editor.setValue(current + block);
          const line = editor.lineCount() - 2;
          editor.setCursor({ line, ch: 2 });
          editor.focus();
        });

        el.addEventListener('mouseenter', e => {
          const sel = fullSelector(el);
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
      defaultsViewer.setValue(blogCssText);
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
    frame.srcdoc = buildSrcdoc(currentView, editor.getValue());
  }

  // ── Copy helper ───────────────────────────────────────────────────────

  async function copyText(text) {
    try {
      await navigator.clipboard.writeText(text);
    } catch (_) {
      editor.execCommand('selectAll');
      document.execCommand('copy');
    }
    const orig = copyBtn.textContent;
    copyBtn.textContent = 'Copied!';
    setTimeout(() => { copyBtn.textContent = orig; }, 2000);
  }

  copyBtn.onclick = () => copyText(editor.getValue());

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
      editor.setValue(await res.text());
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

  // ── Save button ───────────────────────────────────────────────────────

  saveBtn.addEventListener('click', async () => {
    const css = editor.getValue();
    if (css.length > 10240) {
      alert('CSS exceeds the 10 KB limit. Please reduce it before saving.');
      return;
    }
    saveBtn.textContent = 'Saving…';
    saveBtn.disabled = true;
    try {
      const res = await api(`/sites/${siteId}`, { method: 'PATCH', body: JSON.stringify({ customCss: css }) });
      if (!res || !res.ok) throw new Error();
      saveBtn.textContent = 'Saved!';
      setTimeout(() => { saveBtn.textContent = 'Save'; }, 2000);
    } catch (_) {
      alert('Failed to save CSS. Please try again.');
      saveBtn.textContent = 'Save';
    } finally {
      saveBtn.disabled = false;
    }
  });

  // ── Editor input ──────────────────────────────────────────────────────

  function updateCharCount() {
    const len = editor.getValue().length;
    charCount.textContent = `${len} / 10240 characters`;
    charCount.style.color = len > 10240 ? '#c00' : '';
  }

  editor.on('change', debounce(updateCssOnly, 250));
  editor.on('change', updateCharCount);

  // ── Frame load — re-attach tooltip listeners ──────────────────────────

  frame.addEventListener('load', attachTooltipListeners);

  // ── Init ──────────────────────────────────────────────────────────────

  async function init() {
    await fetchBlogCss();

    if (siteId) {
      site = await fetchSite();
      if (site) {
        const name = site.name || 'Untitled';
        siteName.textContent    = `Style Editor — ${name}`;
        editorTitle.textContent = name;
        if (site.customCss) {
          editor.setValue(site.customCss);
          updateCharCount();
        }
        if (!site.stylesUrl) {
          loadBtn.title = 'No stylesUrl configured for this site';
        }
      }
    }

    renderFrame();
  }

  init();
})();
