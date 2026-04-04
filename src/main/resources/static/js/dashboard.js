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
    const prefix     = site.prefix ? `/${escHtml(site.prefix)}` : '';
    const siteUrl    = `https://${escHtml(site.domain)}${prefix}`;
    const verified   = site.status === 'VERIFIED';
    const expired    = !verified && site.verifyDate && (Date.now() - new Date(site.verifyDate).getTime() > 2 * 24 * 60 * 60 * 1000);
    const badgeClass = verified ? 'badge--verified' : expired ? 'badge--expired' : 'badge--pending';
    const badgeText  = verified ? '✓ Verified' : expired ? 'Verification expired' : 'Pending verification';

    const card = document.createElement('div');
    card.className = 'site-card';
    card.innerHTML = `
            <div class="site-card__info">
                <div class="site-card__name">${escHtml(site.name)} <span class="badge ${badgeClass} badge--clickable"
                     data-verify-domain="${escHtml(site.domain)}"
                     data-verify-prefix="${escHtml(site.prefix || '')}"
                     data-verify-status="${escHtml(site.status)}"
                     data-verify-date="${escHtml(site.verifyDate || '')}"
                     data-verify-site-id="${site.id}">${badgeText}</span></div>
                <a class="site-card__domain" href="${siteUrl}" target="_blank" rel="noopener">${escHtml(site.domain)}${prefix}</a>
            </div>
            <div class="site-card__actions">
                <a class="btn btn-ghost" href="/admin/sites/${site.id}/posts">Posts</a>
                <a class="btn btn-ghost" href="/admin/sites/${site.id}/edit">Edit</a>
                <a class="btn btn-ghost" href="/admin/sites/${site.id}/style-tester">Style Tester</a>
                <button class="btn btn-ghost btn--danger" data-delete-site="${site.id}" data-site-name="${escHtml(site.name)}">Delete</button>
            </div>`;
    siteList.appendChild(card);
  });

  siteList.querySelectorAll('[data-verify-domain]').forEach(badge => {
    badge.addEventListener('click', () => {
      showVerificationModal({
        domain:     badge.dataset.verifyDomain,
        prefix:     badge.dataset.verifyPrefix,
        status:     badge.dataset.verifyStatus,
        verifyDate: badge.dataset.verifyDate || null,
        siteId:     badge.dataset.verifySiteId,
      });
    });
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
