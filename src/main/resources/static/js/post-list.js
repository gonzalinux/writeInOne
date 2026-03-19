const siteId        = location.pathname.split('/')[3];
const postTableBody = document.getElementById('postTableBody');
const postTable     = document.getElementById('postTable');
const empty         = document.getElementById('empty');
const newPostLink   = document.getElementById('newPostLink');
const emptyNewLink  = document.getElementById('emptyNewPostLink');

if (newPostLink)  newPostLink.href  = `/admin/sites/${siteId}/posts/new`;
if (emptyNewLink) emptyNewLink.href = `/admin/sites/${siteId}/posts/new`;

let siteDomain = null;
let currentPage = 0;
const PAGE_SIZE = 20;

const filterSearch = document.getElementById('filterSearch');
const filterStatus = document.getElementById('filterStatus');
const filterTag    = document.getElementById('filterTag');

function applyFilters() { currentPage = 0; loadPosts(); }
function clearFilters() {
  filterSearch.value = '';
  filterStatus.value = '';
  filterTag.value    = '';
  currentPage = 0;
  loadPosts();
}

[filterSearch, filterTag].forEach(el => {
  el?.addEventListener('keydown', e => { if (e.key === 'Enter') applyFilters(); });
});
filterStatus?.addEventListener('change', applyFilters);

function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function escHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function attachActionListeners() {
  postTableBody.querySelectorAll('[data-publish-post]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const postId = btn.dataset.publishPost;
      const res = await api(`/sites/${siteId}/posts/${postId}/publish`, { method: 'POST' });
      if (!res) return;
      if (res.ok) loadPosts();
      else alert('Failed to publish post');
    });
  });

  postTableBody.querySelectorAll('[data-unpublish-post]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const postId = btn.dataset.unpublishPost;
      const res = await api(`/sites/${siteId}/posts/${postId}/unpublish`, { method: 'POST' });
      if (!res) return;
      if (res.ok) loadPosts();
      else alert('Failed to unpublish post');
    });
  });

  postTableBody.querySelectorAll('[data-unschedule-post]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const postId = btn.dataset.unschedulePost;
      const res = await api(`/sites/${siteId}/posts/${postId}/unpublish`, { method: 'POST' });
      if (!res) return;
      if (res.ok) loadPosts();
      else alert('Failed to unschedule post');
    });
  });

  postTableBody.querySelectorAll('[data-delete-post]').forEach(btn => {
    btn.addEventListener('click', async () => {
      if (!confirm('Delete this post? This cannot be undone.')) return;
      const postId = btn.dataset.deletePost;
      const res = await api(`/sites/${siteId}/posts/${postId}`, { method: 'DELETE' });
      if (!res) return;
      if (res.ok) loadPosts();
      else alert('Failed to delete post');
    });
  });
}

function renderPagination(page, totalPages) {
  let nav = document.getElementById('postPagination');
  if (!nav) {
    nav = document.createElement('div');
    nav.id = 'postPagination';
    nav.className = 'table-pagination';
    postTable.insertAdjacentElement('afterend', nav);
  }
  if (totalPages <= 1) { nav.innerHTML = ''; return; }
  nav.innerHTML = `
    <button class="btn btn-ghost btn--small" id="pgPrev" ${page === 0 ? 'disabled' : ''}>← Prev</button>
    <span>${page + 1} / ${totalPages}</span>
    <button class="btn btn-ghost btn--small" id="pgNext" ${page + 1 >= totalPages ? 'disabled' : ''}>Next →</button>`;
  nav.querySelector('#pgPrev')?.addEventListener('click', () => { currentPage--; loadPosts(); });
  nav.querySelector('#pgNext')?.addEventListener('click', () => { currentPage++; loadPosts(); });
}

async function loadPosts() {
  const params = new URLSearchParams({ page: currentPage, size: PAGE_SIZE });
  const search = filterSearch?.value.trim();
  const status = filterStatus?.value;
  const tag    = filterTag?.value.trim();
  if (search) params.set('search', search);
  if (status) params.set('status', status);
  if (tag)    params.set('tag', tag);

  const res = await api(`/sites/${siteId}/posts/?${params}`);
  if (!res) return;

  const data = await res.json();
  const posts = data.content;
  postTableBody.innerHTML = '';

  if (posts.length === 0 && currentPage === 0) {
    postTable.style.display = 'none';
    empty.style.display = '';
    return;
  }

  postTable.style.display = '';
  empty.style.display = 'none';
  renderPagination(data.page, data.totalPages);

  posts.forEach(item => {
    const langs  = item.translations.map(t => t.lang).join(' ');
    const status = item.post.status.toLowerCase();
    const t      = item.translations[0];

    const dateDisplay = status === 'scheduled'
      ? formatDate(item.post.scheduledAt)
      : formatDate(item.post.publishedAt);

    const viewBtn = (siteDomain && t)
      ? `<a class="action-btn action-btn--view" href="https://${siteDomain}/${t.lang}/${t.slug}" target="_blank" rel="noopener">View</a>`
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

    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${escHtml(t?.title || '')}</td>
      <td>${escHtml(langs)}</td>
      <td><span class="status status--${status}">${status}</span></td>
      <td>${dateDisplay}</td>
      <td class="td-actions">
        <a class="action-btn action-btn--edit" href="/admin/sites/${siteId}/posts/${item.post.id}/edit">Edit</a>
        ${viewBtn}
        ${publishBtn}
        ${unpublishBtn}
        ${unscheduleBtn}
        ${deleteBtn}
      </td>`;
    postTableBody.appendChild(tr);
  });

  attachActionListeners();
}

async function init() {
  const siteRes = await api(`/sites/${siteId}`);
  if (siteRes?.ok) {
    const site = await siteRes.json();
    siteDomain = site.domain;
  }
  loadPosts();
}

init();
