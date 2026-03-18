const siteId        = location.pathname.split('/')[3];
const postTableBody = document.getElementById('postTableBody');
const postTable     = document.getElementById('postTable');
const empty         = document.getElementById('empty');
const newPostLink   = document.getElementById('newPostLink');
const emptyNewLink  = document.getElementById('emptyNewPostLink');

if (newPostLink)  newPostLink.href  = `/admin/sites/${siteId}/posts/new`;
if (emptyNewLink) emptyNewLink.href = `/admin/sites/${siteId}/posts/new`;

function formatDate(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function escHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g,  '&lt;')
        .replace(/>/g,  '&gt;')
        .replace(/"/g,  '&quot;');
}

function attachPublishListeners() {
    postTableBody.querySelectorAll('[data-publish-post]').forEach(btn => {
        btn.addEventListener('click', async () => {
            const postId = btn.dataset.publishPost;
            const res = await api(`/sites/${siteId}/posts/${postId}/publish`, { method: 'POST' });
            if (!res) return;
            if (res.ok) loadPosts();
            else alert('Failed to publish post');
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
        const langs      = item.translations.map(t => t.lang).join(' ');
        const status     = item.post.status.toLowerCase();
        const publishBtn = item.post.status !== 'PUBLISHED'
            ? `<button class="action-btn" data-publish-post="${item.post.id}">Publish</button>`
            : '';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${escHtml(item.translations[0]?.title || '')}</td>
            <td>${escHtml(langs)}</td>
            <td><span class="status status--${status}">${status}</span></td>
            <td>${formatDate(item.post.publishedAt)}</td>
            <td class="td-actions">
                <a class="action-link" href="/admin/sites/${siteId}/posts/${item.post.id}/edit">Edit</a>
                ${publishBtn}
            </td>`;
        postTableBody.appendChild(tr);
    });

    attachPublishListeners();
}

loadPosts();
