const siteId = location.pathname.split('/')[3];
const postGrid = document.getElementById('postGrid');
const empty = document.getElementById('empty');
const newPostLink = document.getElementById('newPostLink');
const emptyNewLink = document.getElementById('emptyNewPostLink');

if (newPostLink) newPostLink.href = `/admin/sites/${siteId}/posts/new`;
if (emptyNewLink) emptyNewLink.href = `/admin/sites/${siteId}/posts/new`;

let currentPage = 0;
const PAGE_SIZE = 20;
let siteUrl = '';

async function loadSite() {
  const res = await api(`/sites/${siteId}`);
  if (!res?.ok) return;
  const site = await res.json();
  const prefix = site.prefix ? `/${site.prefix}` : '';
  siteUrl = `https://${site.domain}${prefix}`;
}

const filterSearch = document.getElementById('filterSearch');
const filterStatus = document.getElementById('filterStatus');
const filterTag = document.getElementById('filterTag');
const tagSelect = document.getElementById('tagSelect');
const tagSelectControl = document.getElementById('tagSelectControl');
const tagSelectMenu = document.getElementById('tagSelectMenu');

let selectedTags = [];

function applyFilters() {
  currentPage = 0;
  loadPosts();
}

function clearFilters() {
  filterSearch.value = '';
  filterStatus.value = '';
  filterTag.value = '';
  selectedTags = [];
  renderTagBadges();
  currentPage = 0;
  loadPosts();
}

let searchDebounce = null;
filterSearch?.addEventListener('input', () => {
  clearTimeout(searchDebounce);
  searchDebounce = setTimeout(applyFilters, 300);
});
filterStatus?.addEventListener('change', applyFilters);

// ── Tag search combobox (multi-select) ─────────────────────────────────────

async function searchTags(query) {
  const params = new URLSearchParams();
  if (query) params.set('search', query);
  const res = await api(`/sites/${siteId}/tags?${params}`);
  if (!res?.ok) return [];
  return res.json();
}

function renderTagBadges() {
  tagSelectControl.querySelectorAll('.tag-badge--removable').forEach(el => el.remove());
  selectedTags.forEach(name => {
    const badge = document.createElement('span');
    badge.className = 'tag-badge tag-badge--removable';
    badge.innerHTML = `${escHtml(name)} <button type="button" class="tag-badge__remove" data-remove-tag="${escHtml(name)}">&times;</button>`;
    tagSelectControl.insertBefore(badge, filterTag);
  });
  tagSelectControl.querySelectorAll('[data-remove-tag]').forEach(btn => {
    btn.addEventListener('click', () => {
      selectedTags = selectedTags.filter(t => t !== btn.dataset.removeTag);
      renderTagBadges();
      applyFilters();
    });
  });
}

function renderTagMenu(tags) {
  const options = tags.filter(tag => !selectedTags.includes(tag.name));
  tagSelectMenu.innerHTML = options.length
    ? options.map(tag => `<div class="tag-select__item" data-tag-name="${escHtml(tag.name)}">${escHtml(tag.name)}</div>`).join('')
    : '<div class="tag-select__empty">No matching tags</div>';
  tagSelectMenu.hidden = false;

  tagSelectMenu.querySelectorAll('[data-tag-name]').forEach(item => {
    item.addEventListener('click', () => {
      selectedTags.push(item.dataset.tagName);
      filterTag.value = '';
      renderTagBadges();
      tagSelectMenu.hidden = true;
      filterTag.focus();
      applyFilters();
    });
  });
}

let tagSearchDebounce = null;
filterTag?.addEventListener('input', () => {
  clearTimeout(tagSearchDebounce);
  tagSearchDebounce = setTimeout(async () => {
    renderTagMenu(await searchTags(filterTag.value.trim()));
  }, 250);
});

filterTag?.addEventListener('focus', async () => {
  renderTagMenu(await searchTags(filterTag.value.trim()));
});

document.addEventListener('click', e => {
  if (!tagSelect.contains(e.target)) tagSelectMenu.hidden = true;
});

function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-US', {month: 'short', day: 'numeric', year: 'numeric'});
}

function escHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function attachActionListeners() {
  postGrid.querySelectorAll('[data-publish-post]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const postId = btn.dataset.publishPost;
      const res = await api(`/sites/${siteId}/posts/${postId}/publish`, {method: 'POST'});
      if (!res) return;
      if (res.ok) loadPosts();
      else await alertModal('Failed to publish post');
    });
  });

  postGrid.querySelectorAll('[data-unpublish-post]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const postId = btn.dataset.unpublishPost;
      const res = await api(`/sites/${siteId}/posts/${postId}/unpublish`, {method: 'POST'});
      if (!res) return;
      if (res.ok) loadPosts();
      else await alertModal('Failed to unpublish post');
    });
  });

  postGrid.querySelectorAll('[data-unschedule-post]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const postId = btn.dataset.unschedulePost;
      const res = await api(`/sites/${siteId}/posts/${postId}/unpublish`, {method: 'POST'});
      if (!res) return;
      if (res.ok) loadPosts();
      else await alertModal('Failed to unschedule post');
    });
  });

  postGrid.querySelectorAll('[data-delete-post]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const ok = await confirmModal('Delete this post? This cannot be undone.', {
        title: 'Delete post', confirmLabel: 'Delete', danger: true
      });
      if (!ok) return;
      const postId = btn.dataset.deletePost;
      const res = await api(`/sites/${siteId}/posts/${postId}`, {method: 'DELETE'});
      if (!res) return;
      if (res.ok) loadPosts();
      else await alertModal('Failed to delete post');
    });
  });
}

function renderPagination(page, totalPages) {
  let nav = document.getElementById('postPagination');
  if (!nav) {
    nav = document.createElement('div');
    nav.id = 'postPagination';
    nav.className = 'table-pagination';
    postGrid.insertAdjacentElement('afterend', nav);
  }
  if (totalPages <= 1) {
    nav.innerHTML = '';
    return;
  }
  nav.innerHTML = `
    <button class="btn btn-ghost btn--small" id="pgPrev" ${page === 0 ? 'disabled' : ''}>← Prev</button>
    <span>${page + 1} / ${totalPages}</span>
    <button class="btn btn-ghost btn--small" id="pgNext" ${page + 1 >= totalPages ? 'disabled' : ''}>Next →</button>`;
  nav.querySelector('#pgPrev')?.addEventListener('click', () => {
    currentPage--;
    loadPosts();
  });
  nav.querySelector('#pgNext')?.addEventListener('click', () => {
    currentPage++;
    loadPosts();
  });
}

async function loadPosts() {
  const params = new URLSearchParams({page: currentPage, size: PAGE_SIZE});
  const search = filterSearch?.value.trim();
  const status = filterStatus?.value;
  if (search) params.set('search', search);
  if (status) params.set('status', status);
  selectedTags.forEach(tag => params.append('tag', tag));

  const res = await api(`/sites/${siteId}/posts/?${params}`);
  if (!res) return;

  const data = await res.json();
  const posts = data.content;
  postGrid.innerHTML = '';

  if (posts.length === 0 && currentPage === 0) {
    postGrid.style.display = 'none';
    empty.style.display = '';
    return;
  }

  postGrid.style.display = '';
  empty.style.display = 'none';
  renderPagination(data.page, data.totalPages);

  posts.forEach(item => {
    const status = item.post.status.toLowerCase();
    const t = item.translations[0];
    const langBadges = item.translations.map(tr => `<span class="tag-badge tag-badge--lang">${escHtml(tr.lang)}</span>`).join(' ');
    const tags = (item.tags || []).map(tag => `<span class="tag-badge">${escHtml(tag.name)}</span>`).join(' ');

    const dateDisplay = status === 'scheduled'
      ? formatDate(item.post.scheduledAt)
      : formatDate(item.post.publishedAt);

    const postUrl = t ? `${siteUrl}/${t.lang}/articles/${t.slug}` : '';
    const titleEl = t
      ? `<a class="post-card__title" href="${postUrl}" target="_blank" rel="noopener">${escHtml(t.title)}</a>`
      : `<span class="post-card__title">(untitled)</span>`;

    const previewBtn = t
      ? `<a class="action-btn action-btn--view" href="/admin/preview/${siteId}/${t.lang}/${t.slug}" target="_blank" rel="noopener">Preview</a>`
      : '';
    const publishBtn = status === 'draft'
      ? `<button class="action-btn" data-publish-post="${item.post.id}">Publish</button>`
      : '';
    const unpublishBtn = status === 'published'
      ? `<button class="action-btn" data-unpublish-post="${item.post.id}">Unpublish</button>`
      : '';
    const unscheduleBtn = status === 'scheduled'
      ? `<button class="action-btn" data-unschedule-post="${item.post.id}">Unschedule</button>`
      : '';
    const deleteBtn = `<button class="action-btn action-btn--danger" data-delete-post="${item.post.id}">Delete</button>`;

    const card = document.createElement('div');
    card.className = 'post-card';
    card.innerHTML = `
      <div class="post-card__header">
        ${titleEl}
        <span class="status status--${status}">${status}</span>
      </div>
      <div class="post-card__badges">${langBadges} ${tags}</div>
      <div class="post-card__meta">${dateDisplay} · ${item.post.viewCount ?? 0} views</div>
      <div class="post-card__actions">
        <a class="action-btn action-btn--edit" href="/admin/sites/${siteId}/posts/${item.post.id}/edit">Properties</a>
        ${previewBtn}
        ${publishBtn}
        ${unpublishBtn}
        ${unscheduleBtn}
        ${deleteBtn}
      </div>`;
    postGrid.appendChild(card);
  });

  attachActionListeners();
}

async function init() {
  await loadSite();
  loadPosts();
}

init();
