// Path is /admin/service-accounts/{id}/sites
const accountId = location.pathname.split('/')[3];

const pageTitle = document.getElementById('pageTitle');
const list = document.getElementById('siteAccessList');
const empty = document.getElementById('siteAccessEmpty');

async function loadAccountName() {
  const res = await api('/service-accounts/');
  if (!res || !res.ok) return;
  const accounts = await res.json();
  const account = accounts.find(a => String(a.id) === accountId);
  if (account) pageTitle.textContent = `Site access — ${account.name}`;
}

async function loadSites() {
  const res = await api(`/service-accounts/${accountId}/sites`);
  if (!res) return;
  if (!res.ok) {
    list.innerHTML = '<p class="hint">Could not load site access.</p>';
    return;
  }

  const sites = await res.json();
  list.innerHTML = '';
  empty.hidden = sites.length > 0;

  sites.forEach(site => {
    const row = document.createElement('div');
    row.className = 'member-row';
    row.innerHTML = `
      <div class="member-row__info">
        <div class="member-row__name">${escHtml(site.name)} ${roleBadge(site.role)}</div>
        <div class="member-row__email">${escHtml(site.domain)}</div>
      </div>
      <div class="member-row__actions">
        <button type="button" class="btn btn-ghost btn--danger" data-remove-site="${site.id}" data-site-name="${escHtml(site.name)}">Remove access</button>
      </div>`;
    list.appendChild(row);
  });

  list.querySelectorAll('[data-remove-site]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const ok = await confirmModal(`Remove this service account's access to "${btn.dataset.siteName}"?`, {
        title: 'Remove access', confirmLabel: 'Remove', danger: true
      });
      if (!ok) return;
      const res = await api(`/sites/${btn.dataset.removeSite}/users/${accountId}`, {method: 'DELETE'});
      if (!res) return;
      if (res.ok) {
        loadSites();
      } else {
        const data = await res.json().catch(() => ({}));
        await alertModal(data.details || 'Failed to remove access');
      }
    });
  });
}

Promise.all([loadAccountName(), loadSites()]);
