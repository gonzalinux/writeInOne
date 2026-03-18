const siteId        = location.pathname.split('/')[3];
const postTableBody = document.getElementById('postTableBody');
const postTable     = document.getElementById('postTable');
const empty         = document.getElementById('empty');
const newPostLink   = document.getElementById('newPostLink');
const emptyNewLink  = document.getElementById('emptyNewPostLink');

if (newPostLink)  newPostLink.href  = `/admin/sites/${siteId}/posts/new`;
if (emptyNewLink) emptyNewLink.href = `/admin/sites/${siteId}/posts/new`;

let siteDomain = null;

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

async function loadPosts() {
  const res = await api(`/sites/${siteId}/posts/`);
  if (!res) return;

  const posts = await res.json();
  postTableBody.innerHTML = '';

  if (posts.length === 0) {
    postTable.style.display = 'none';
    empty.style.display = '';
    return;
  }

  postTable.style.display = '';
  empty.style.display = 'none';

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
