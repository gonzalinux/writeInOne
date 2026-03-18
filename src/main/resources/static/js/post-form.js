const parts  = location.pathname.split('/');
const siteId = parts[3];
const postId = (parts[5] && parts[5] !== 'new') ? parts[5] : null;

const form         = document.getElementById('postForm');
const err          = document.getElementById('error');
const publishBtn   = document.getElementById('publishBtn');
const pageTitle    = document.getElementById('pageTitle');
const backLink     = document.getElementById('backLink');
const cancelLink   = document.getElementById('cancelLink');
const langSelect   = document.getElementById('lang');
const titleInput   = document.getElementById('title');
const slugInput    = document.getElementById('slug');
const excerptInput = document.getElementById('excerpt');
const bodyInput    = document.getElementById('body');
const coverInput   = document.getElementById('coverUrl');
const tagsInput    = document.getElementById('tags');

if (backLink)   backLink.href   = `/admin/sites/${siteId}/posts`;
if (cancelLink) cancelLink.href = `/admin/sites/${siteId}/posts`;

function buildBody() {
  const lang = langSelect.value;
  const tags = tagsInput.value.split(',').map(t => t.trim()).filter(Boolean);
  return {
    coverUrl: coverInput.value.trim() || null,
    translations: {
      [lang]: {
        title:   titleInput.value.trim(),
        body:    bodyInput.value,
        slug:    slugInput.value.trim() || null,
        excerpt: excerptInput.value.trim() || null,
      },
    },
    tags,
  };
}

async function handleError(res) {
  const data = await res.json().catch(() => ({}));
  err.textContent   = data.message || 'Something went wrong';
  err.style.display = 'block';
}

async function loadPost() {
  const res = await api(`/sites/${siteId}/posts/${postId}`);
  if (!res) return;
  if (!res.ok) return;

  const data        = await res.json();
  const translation = data.translations?.[0];
  const post        = data.post;

  pageTitle.textContent = 'Edit post';
  document.title        = 'Edit post — WriteInOne';

  if (translation) {
    for (const opt of langSelect.options) {
      if (opt.value === translation.lang) { opt.selected = true; break; }
    }
    titleInput.value   = translation.title   || '';
    slugInput.value    = translation.slug    || '';
    excerptInput.value = translation.excerpt || '';
    bodyInput.value    = translation.body    || '';
  }

  if (post) {
    coverInput.value = post.coverUrl || '';
    tagsInput.value  = (data.tags || []).map(t => t.name).join(', ');
    if (post.status !== 'PUBLISHED' && publishBtn) publishBtn.style.display = '';
  }
}

if (postId) {
  loadPost();
} else {
  pageTitle.textContent = 'New post';
  document.title        = 'New post — WriteInOne';
}

form.addEventListener('submit', async e => {
  e.preventDefault();
  err.style.display = 'none';

  const url    = postId ? `/sites/${siteId}/posts/${postId}` : `/sites/${siteId}/posts/`;
  const method = postId ? 'PUT' : 'POST';

  const res = await api(url, { method, body: JSON.stringify(buildBody()) });
  if (!res) return;

  if (res.ok) {
    location.href = `/admin/sites/${siteId}/posts`;
  } else {
    await handleError(res);
  }
});

if (publishBtn) {
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
}
