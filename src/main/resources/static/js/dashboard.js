const siteList = document.getElementById('siteList');
const empty = document.getElementById('empty');

function escHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

async function loadDashboard() {
  const res = await api('/sites/');
  if (!res) return;

  const sites = await res.json();

  if (sites.length === 0) {
    empty.style.display = '';
    return;
  }

  sites.forEach(site => {
    const card = document.createElement('div');
    card.className = 'site-card';
    card.innerHTML = `
            <div class="site-card__info">
                <div class="site-card__name">${escHtml(site.name)}</div>
                <div class="site-card__domain">${escHtml(site.domain)}</div>
            </div>
            <div class="site-card__actions">
                <a class="btn btn-ghost" href="/admin/sites/${site.id}/posts">Posts</a>
                <a class="btn btn-ghost" href="/admin/sites/${site.id}/posts/new">+ New post</a>
                <a class="btn btn-ghost" href="/admin/sites/${site.id}/edit">Edit</a>
                <a class="btn btn-ghost" href="/admin/sites/${site.id}/style-tester">Style Tester</a>
                <a class="btn btn-ghost" href="https://${escHtml(site.domain)}" target="_blank" rel="noopener">Preview</a>
                <button class="btn btn-ghost btn--danger" data-delete-site="${site.id}" data-site-name="${escHtml(site.name)}">Delete</button>
            </div>`;
    siteList.appendChild(card);
  });

  siteList.querySelectorAll('[data-delete-site]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const name = btn.dataset.siteName;
      if (!confirm(`Delete "${name}" and all its posts? This cannot be undone.`)) return;
      const res = await api(`/sites/${btn.dataset.deleteSite}`, { method: 'DELETE' });
      if (!res) return;
      if (res.ok) loadDashboard();
      else alert('Failed to delete site');
    });
  });
}

loadDashboard();
