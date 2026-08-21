const form = document.getElementById('search-form');
const input = document.getElementById('search-input');
const container = document.getElementById('post-list-container');
const tagInput = document.getElementById('tag-input');
const tagSelect = document.getElementById('tagSelect');
const tagSelectMenu = document.getElementById('tagSelectMenu');
const sortInput = document.getElementById('sort-input');
const sortToggle = document.getElementById('sort-toggle');

const lang = form.dataset.lang;
const prefix = window.SITE_PREFIX || '';

let activeTag = tagInput.value.trim() || null;
let activeSort = sortInput.value || null;

// Hide the submit button — search is now live
form.querySelector('.search-bar__btn').hidden = true;

form.addEventListener('submit', e => e.preventDefault());

let debounceTimer = null;
input.addEventListener('input', () => {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => doSearch(input.value.trim()), 300);
});

// ── Tag search combobox ─────────────────────────────────────────────────

async function searchTags(query) {
  const params = new URLSearchParams();
  if (query) params.set('search', query);
  try {
    const res = await fetch(prefix + '/' + lang + '/tags?' + params);
    if (!res.ok) return [];
    return res.json();
  } catch {
    return [];
  }
}

function renderTagMenu(tags) {
  tagSelectMenu.innerHTML = tags.length
    ? tags.map(tag => `<div class="tag-select__item" data-tag-name="${esc(tag.name)}">${esc(tag.name)}</div>`).join('')
    : '<div class="tag-select__empty">No matching tags</div>';
  tagSelectMenu.hidden = false;

  tagSelectMenu.querySelectorAll('[data-tag-name]').forEach(item => {
    item.addEventListener('click', () => {
      activeTag = item.dataset.tagName;
      tagInput.value = activeTag;
      tagSelectMenu.hidden = true;
      doSearch(input.value.trim());
    });
  });
}

let tagSearchDebounce = null;
tagInput.addEventListener('input', () => {
  if (tagInput.value.trim() === '' && activeTag !== null) {
    activeTag = null;
    doSearch(input.value.trim());
  }
  clearTimeout(tagSearchDebounce);
  tagSearchDebounce = setTimeout(async () => {
    renderTagMenu(await searchTags(tagInput.value.trim()));
  }, 250);
});

tagInput.addEventListener('focus', async () => {
  renderTagMenu(await searchTags(tagInput.value.trim()));
});

document.addEventListener('click', e => {
  if (!tagSelect.contains(e.target)) tagSelectMenu.hidden = true;
});

// ── Sort toggle ──────────────────────────────────────────────────────────

sortToggle.addEventListener('click', e => {
  e.preventDefault();
  activeSort = activeSort === 'asc' ? null : 'asc';
  sortInput.value = activeSort || '';
  doSearch(input.value.trim());
});

function updateSortToggleHref(query) {
  const nextSort = activeSort === 'asc' ? 'desc' : 'asc';
  const params = new URLSearchParams({sort: nextSort});
  if (activeTag) params.set('tag', activeTag);
  if (query) params.set('search', query);
  sortToggle.href = prefix + '/' + lang + '?' + params;
  sortToggle.querySelector('span').textContent = activeSort === 'asc' ? '↑ Oldest first' : '↓ Newest first';
}

// ── Search / fetch / render ─────────────────────────────────────────────

function doSearch(query) {
  const params = new URLSearchParams();
  if (query) params.set('search', query);
  if (activeTag) params.set('tag', activeTag);
  if (activeSort) params.set('sort', activeSort);

  const url = new URL(window.location.href);
  ['search', 'tag', 'sort'].forEach(key => url.searchParams.delete(key));
  if (query) url.searchParams.set('search', query);
  if (activeTag) url.searchParams.set('tag', activeTag);
  if (activeSort) url.searchParams.set('sort', activeSort);
  history.replaceState(null, '', url);

  updateSortToggleHref(query);

  const clearLink = document.getElementById('search-clear');
  const hasFilters = query || activeTag || activeSort;
  if (clearLink) {
    clearLink.hidden = !hasFilters;
  } else if (hasFilters) {
    const a = document.createElement('a');
    a.id = 'search-clear';
    a.className = 'search-bar__clear';
    a.href = prefix + '/' + lang;
    a.textContent = '✕ Clear';
    form.appendChild(a);
  }

  fetch(prefix + '/' + lang + '/posts?' + params)
    .then(r => r.json())
    .then(page => render(page, query))
    .catch(() => {
    });
}

function esc(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function formatDate(iso) {
  if (!iso) return '';
  return new Intl.DateTimeFormat(lang === 'es' ? 'es-ES' : 'en-US', {
    month: 'short', day: 'numeric', year: 'numeric'
  }).format(new Date(iso));
}

function render(page, query) {
  if (page.content.length === 0) {
    container.innerHTML = '<p class="empty-state">No posts yet.</p>';
    return;
  }

  const tagFilter = activeTag
    ? `<p class="filter-label">Filtering by tag: <strong>${esc(activeTag)}</strong></p>`
    : '';

  const items = page.content.map(item => {
    const cover = item.post.coverUrl
      ? `<img class="post-card__cover" src="${esc(item.post.coverUrl)}" alt="${esc(item.translation.title)}"/>`
      : '';
    const excerpt = item.translation.excerpt
      ? `<p class="post-card__excerpt">${esc(item.translation.excerpt)}</p>`
      : '';
    const tags = item.tags.length > 0
      ? `<div class="post-card__tags">${item.tags.map(t =>
        `<a class="tag" href="${prefix}/${lang}?tag=${encodeURIComponent(t.name)}">${esc(t.name)}</a>`
      ).join('')}</div>`
      : '';
    const date = formatDate(item.post.publishedAt);
    return `<li class="post-card">
            ${cover}
            <div class="post-card__meta"><time datetime="${esc(item.post.publishedAt)}">${date}</time></div>
            <h2 class="post-card__title"><a href="${prefix}/${lang}/articles/${esc(item.translation.slug)}">${esc(item.translation.title)}</a></h2>
            ${excerpt}
            ${tags}
        </li>`;
  }).join('');

  let pagination = '';
  if (page.totalPages > 1) {
    const qPart = query ? '&search=' + encodeURIComponent(query) : '';
    const tPart = activeTag ? '&tag=' + encodeURIComponent(activeTag) : '';
    const sPart = activeSort ? '&sort=' + encodeURIComponent(activeSort) : '';
    const prev = page.page > 0
      ? `<a class="pagination__btn" href="${prefix}/${lang}?page=${page.page - 1}${qPart}${tPart}${sPart}">← Newer</a>`
      : '';
    const next = page.page + 1 < page.totalPages
      ? `<a class="pagination__btn" href="${prefix}/${lang}?page=${page.page + 1}${qPart}${tPart}${sPart}">Older →</a>`
      : '';
    pagination = `<nav class="pagination">${prev}<span class="pagination__info">${page.page + 1} / ${page.totalPages}</span>${next}</nav>`;
  }

  container.innerHTML = `${tagFilter}<ul class="post-list">${items}</ul>${pagination}`;
}
