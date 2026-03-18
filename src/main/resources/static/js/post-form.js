const parts  = location.pathname.split('/');
const siteId = parts[3];
const postId = (parts[5] && parts[5] !== 'new') ? parts[5] : null;

const form             = document.getElementById('postForm');
const err              = document.getElementById('error');
const publishBtn       = document.getElementById('publishBtn');
const scheduleToggle   = document.getElementById('scheduleToggle');
const scheduleSection  = document.getElementById('scheduleSection');
const scheduleBtn      = document.getElementById('scheduleBtn');
const clearScheduleBtn = document.getElementById('clearScheduleBtn');
const scheduledAtInput = document.getElementById('scheduledAt');
const pageTitle        = document.getElementById('pageTitle');
const backLink         = document.getElementById('backLink');
const cancelLink       = document.getElementById('cancelLink');
const slugInput        = document.getElementById('slug');
const coverInput       = document.getElementById('coverUrl');
const tagsInput        = document.getElementById('tags');

if (backLink)   backLink.href   = `/admin/sites/${siteId}/posts`;
if (cancelLink) cancelLink.href = `/admin/sites/${siteId}/posts`;

let siteLanguages = [];

// ── Panel creation ────────────────────────────────────────────────────────

function createPanel(lang) {
  const panel = document.createElement('div');
  panel.id = `panel-${lang}`;
  panel.className = 'lang-panel';
  panel.innerHTML = `
    <div class="field">
      <label for="title-${lang}">Title</label>
      <input id="title-${lang}" type="text" placeholder="Post title"/>
    </div>
    <div class="field">
      <label for="excerpt-${lang}">Excerpt <span style="color:#aaa">(optional)</span></label>
      <textarea id="excerpt-${lang}" rows="2" placeholder="A short summary shown in post listings"></textarea>
    </div>
    <div class="field">
      <label for="body-${lang}">Body <span style="color:#aaa">(Markdown)</span></label>
      <textarea id="body-${lang}" class="body-editor" placeholder="Write your post in Markdown..."></textarea>
    </div>`;
  return panel;
}

// ── Tab UI ────────────────────────────────────────────────────────────────

function switchTab(activeLang) {
  document.querySelectorAll('.lang-tab').forEach(btn => {
    btn.classList.toggle('lang-tab--active', btn.dataset.lang === activeLang);
  });
  document.querySelectorAll('.lang-panel').forEach(panel => {
    panel.style.display = panel.id === `panel-${activeLang}` ? '' : 'none';
  });
}

function updateDot(lang) {
  const filled = !!document.getElementById(`title-${lang}`)?.value.trim();
  document.getElementById(`dot-${lang}`)?.classList.toggle('lang-tab__dot--filled', filled);
}

function buildUI(languages) {
  const tabsContainer   = document.getElementById('langTabsContainer');
  const panelsContainer = document.getElementById('langPanels');

  languages.forEach((lang, i) => {
    const panel = createPanel(lang);
    if (i !== 0) panel.style.display = 'none';
    panelsContainer.appendChild(panel);
  });

  if (languages.length > 1) {
    const tabsDiv = document.createElement('div');
    tabsDiv.className = 'lang-tabs';

    languages.forEach((lang, i) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = `lang-tab${i === 0 ? ' lang-tab--active' : ''}`;
      btn.dataset.lang = lang;
      btn.innerHTML = `${lang.toUpperCase()} <span class="lang-tab__dot" id="dot-${lang}"></span>`;
      btn.addEventListener('click', () => switchTab(lang));
      tabsDiv.appendChild(btn);
    });

    tabsContainer.appendChild(tabsDiv);
  }

  languages.forEach(lang => {
    document.getElementById(`title-${lang}`)
      ?.addEventListener('input', () => updateDot(lang));
  });
}

// ── Data ──────────────────────────────────────────────────────────────────

function buildBody() {
  const slug         = slugInput.value.trim() || null;
  const tags         = tagsInput.value.split(',').map(t => t.trim()).filter(Boolean);
  const translations = {};

  siteLanguages.forEach(lang => {
    const title = document.getElementById(`title-${lang}`)?.value.trim();
    if (!title) return;
    translations[lang] = {
      title,
      slug,
      body:    document.getElementById(`body-${lang}`).value,
      excerpt: document.getElementById(`excerpt-${lang}`).value.trim() || null,
    };
  });

  return { coverUrl: coverInput.value.trim() || null, translations, tags };
}

async function handleError(res) {
  const data = await res.json().catch(() => ({}));
  err.textContent   = data.message || 'Something went wrong';
  err.style.display = 'block';
}

async function loadPost() {
  const res = await api(`/sites/${siteId}/posts/${postId}`);
  if (!res || !res.ok) return;

  const data = await res.json();
  const post = data.post;

  pageTitle.textContent = 'Edit post';
  document.title        = 'Edit post — WriteInOne';

  data.translations?.forEach(t => {
    const titleEl = document.getElementById(`title-${t.lang}`);
    if (!titleEl) return;
    titleEl.value = t.title || '';
    document.getElementById(`excerpt-${t.lang}`).value = t.excerpt || '';
    document.getElementById(`body-${t.lang}`).value    = t.body    || '';
    if (t.slug) slugInput.value = t.slug;
    updateDot(t.lang);
  });

  if (post) {
    coverInput.value = post.coverUrl || '';
    tagsInput.value  = (data.tags || []).map(t => t.name).join(', ');

    const isPublished  = post.status === 'PUBLISHED';
    const isScheduled  = post.status === 'SCHEDULED';

    if (!isPublished) publishBtn.style.display   = '';
    if (!isPublished) scheduleToggle.style.display = '';

    if (isScheduled && post.scheduledAt) {
      scheduledAtInput.value = toDatetimeLocal(post.scheduledAt);
      scheduleSection.style.display = '';
      clearScheduleBtn.style.display = '';
      scheduleToggle.textContent = 'Update schedule';
    }
  }
}

// ── Init ──────────────────────────────────────────────────────────────────

async function init() {
  const siteRes = await api(`/sites/${siteId}`);
  if (!siteRes?.ok) return;

  const site = await siteRes.json();
  siteLanguages = (site.languages || []).map(l => l === 'ENGLISH' ? 'en' : 'es');
  if (siteLanguages.length === 0) siteLanguages = ['en'];

  buildUI(siteLanguages);

  if (postId) {
    await loadPost();
  } else {
    pageTitle.textContent = 'New post';
    document.title        = 'New post — WriteInOne';
  }
}

init();

// ── Schedule helpers ──────────────────────────────────────────────────────

function toDatetimeLocal(iso) {
  return new Date(iso).toISOString().slice(0, 16);
}

scheduleToggle.addEventListener('click', () => {
  const open = scheduleSection.style.display !== 'none';
  scheduleSection.style.display = open ? 'none' : '';
});

scheduleBtn.addEventListener('click', async () => {
  err.style.display = 'none';
  const value = scheduledAtInput.value;
  if (!value) {
    err.textContent   = 'Please pick a date and time.';
    err.style.display = 'block';
    return;
  }

  const res = await api(`/sites/${siteId}/posts/${postId}/schedule`, {
    method: 'POST',
    body: JSON.stringify({ scheduledAt: new Date(value).toISOString() }),
  });
  if (!res) return;

  if (res.ok) {
    location.href = `/admin/sites/${siteId}/posts`;
  } else {
    await handleError(res);
  }
});

clearScheduleBtn.addEventListener('click', async () => {
  err.style.display = 'none';
  const res = await api(`/sites/${siteId}/posts/${postId}/unpublish`, { method: 'POST' });
  if (!res) return;

  if (res.ok) {
    location.href = `/admin/sites/${siteId}/posts`;
  } else {
    await handleError(res);
  }
});

// ── Form actions ──────────────────────────────────────────────────────────

form.addEventListener('submit', async e => {
  e.preventDefault();
  err.style.display = 'none';

  const body = buildBody();
  if (Object.keys(body.translations).length === 0) {
    err.textContent   = 'At least one translation must have a title.';
    err.style.display = 'block';
    return;
  }

  const url    = postId ? `/sites/${siteId}/posts/${postId}` : `/sites/${siteId}/posts/`;
  const method = postId ? 'PUT' : 'POST';

  const res = await api(url, { method, body: JSON.stringify(body) });
  if (!res) return;

  if (res.ok) {
    location.href = `/admin/sites/${siteId}/posts`;
  } else {
    await handleError(res);
  }
});

publishBtn.addEventListener('click', async () => {
  err.style.display = 'none';
  const res = await api(`/sites/${siteId}/posts/${postId}/publish`, { method: 'POST' });
  if (!res) return;

  if (res.ok) {
    location.href = `/admin/sites/${siteId}/posts`;
  } else {
    await handleError(res);
  }
});
