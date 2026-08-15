const saList = document.getElementById('saList');
const saEmpty = document.getElementById('saEmpty');

// Cached so the grant modal doesn't have to refetch on every open; only sites this user
// administers are offered, since a non-admin invite would just 403 on the server.
let adminSites = [];

async function loadSites() {
  const res = await api('/sites/');
  if (!res || !res.ok) return;
  adminSites = (await res.json()).filter(site => can(site.role, 'admin'));
}

async function loadServiceAccounts() {
  const res = await api('/service-accounts/');
  if (!res) return;
  if (!res.ok) {
    saList.innerHTML = '<p class="hint">Could not load your service accounts.</p>';
    return;
  }

  const accounts = await res.json();
  saList.innerHTML = '';
  saEmpty.hidden = accounts.length > 0;

  accounts.forEach(account => {
    const row = document.createElement('div');
    row.className = 'member-row';
    const created = new Date(account.createdAt).toLocaleDateString();
    row.innerHTML = `
      <div class="member-row__info">
        <div class="member-row__name">${escHtml(account.name)}</div>
        <div class="member-row__email">Created ${created}</div>
      </div>
      <div class="member-row__actions">
        <a class="btn btn-ghost" href="/admin/service-accounts/${account.id}/sites">Sites</a>
        <button type="button" class="btn btn-ghost" data-grant="${account.id}" data-grant-name="${escHtml(account.name)}">Grant access</button>
        <button type="button" class="btn btn-ghost" data-rotate="${account.id}" data-rotate-name="${escHtml(account.name)}">Rotate</button>
        <button type="button" class="btn btn-ghost btn--danger" data-revoke="${account.id}" data-revoke-name="${escHtml(account.name)}">Revoke</button>
      </div>`;
    saList.appendChild(row);
  });

  saList.querySelectorAll('[data-grant]').forEach(btn => {
    btn.addEventListener('click', () => openGrantModal(btn.dataset.grant, btn.dataset.grantName));
  });

  saList.querySelectorAll('[data-rotate]').forEach(btn => {
    btn.addEventListener('click', () => rotateServiceAccount(btn.dataset.rotate, btn.dataset.rotateName));
  });

  saList.querySelectorAll('[data-revoke]').forEach(btn => {
    btn.addEventListener('click', () => revokeServiceAccount(btn.dataset.revoke, btn.dataset.revokeName));
  });
}

// ── Create ───────────────────────────────────────────────────────────────

const saNameInput = document.getElementById('saName');
const createError = document.getElementById('createError');

document.getElementById('createSaBtn').addEventListener('click', async () => {
  createError.style.display = 'none';
  const name = saNameInput.value.trim();
  if (!name) {
    createError.textContent = 'Give it a name first.';
    createError.style.display = 'block';
    return;
  }

  const res = await api('/service-accounts/', {method: 'POST', body: JSON.stringify({name})});
  if (!res) return;
  const data = await res.json();

  if (res.ok) {
    saNameInput.value = '';
    showTokenModal('Service account created', data.token);
    loadServiceAccounts();
  } else {
    createError.textContent = data.details || 'Could not create the service account';
    createError.style.display = 'block';
  }
});

// ── Rotate / revoke ─────────────────────────────────────────────────────

async function rotateServiceAccount(id, name) {
  const ok = await confirmModal(`Rotate the token for "${name}"? The old token stops working immediately.`, {
    title: 'Rotate token', confirmLabel: 'Rotate', danger: true
  });
  if (!ok) return;
  const res = await api(`/service-accounts/${id}/rotate`, {method: 'POST'});
  if (!res) return;
  if (res.ok) {
    const data = await res.json();
    showTokenModal('Token rotated', data.token);
  } else {
    await alertModal('Failed to rotate the token');
  }
}

async function revokeServiceAccount(id, name) {
  const ok = await confirmModal(
    `Revoke "${name}"? This removes it from every site immediately and cannot be undone.`,
    {title: 'Revoke service account', confirmLabel: 'Revoke', danger: true}
  );
  if (!ok) return;
  const res = await api(`/service-accounts/${id}`, {method: 'DELETE'});
  if (!res) return;
  if (res.ok) loadServiceAccounts();
  else await alertModal('Failed to revoke the service account');
}

// ── Token modal (shared by create and rotate) ───────────────────────────

const tokenModal = document.getElementById('tokenModal');
const tokenModalTitle = document.getElementById('tokenModalTitle');
const tokenInput = document.getElementById('tokenInput');
const tokenCopyHint = document.getElementById('tokenCopyHint');

function showTokenModal(title, token) {
  tokenModalTitle.textContent = title;
  tokenInput.value = token;
  tokenCopyHint.style.display = 'none';
  tokenModal.style.display = 'flex';
}

function closeTokenModal() {
  tokenModal.style.display = 'none';
}

document.getElementById('tokenModalClose').addEventListener('click', closeTokenModal);
document.getElementById('tokenModalCloseBtn').addEventListener('click', closeTokenModal);
tokenModal.addEventListener('click', e => {
  if (e.target === tokenModal) closeTokenModal();
});

document.getElementById('copyTokenBtn').addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(tokenInput.value);
    tokenCopyHint.textContent = 'Copied!';
  } catch {
    tokenInput.select();
    tokenCopyHint.textContent = 'Press Ctrl+C / Cmd+C to copy.';
  }
  tokenCopyHint.style.display = 'block';
});

// ── Grant-access modal ──────────────────────────────────────────────────

const grantModal = document.getElementById('grantModal');
const grantModalBody = document.getElementById('grantModalBody');
const grantFields = document.getElementById('grantFields');
const grantSite = document.getElementById('grantSite');
const grantRole = document.getElementById('grantRole');
const grantError = document.getElementById('grantError');
const grantSuccess = document.getElementById('grantSuccess');
const grantSubmitBtn = document.getElementById('grantSubmitBtn');

let grantingAccountId = null;

function openGrantModal(accountId, accountName) {
  grantingAccountId = accountId;
  grantError.style.display = 'none';
  grantSuccess.style.display = 'none';
  grantModalBody.textContent = `Choose a site and role for "${accountName}".`;

  if (adminSites.length === 0) {
    grantFields.hidden = true;
    grantSubmitBtn.hidden = true;
    grantModalBody.textContent = "You don't manage any sites yet — create one first.";
  } else {
    grantFields.hidden = false;
    grantSubmitBtn.hidden = false;
    grantSite.innerHTML = adminSites.map(site => `<option value="${site.id}">${escHtml(site.name)}</option>`).join('');
  }

  grantModal.style.display = 'flex';
}

function closeGrantModal() {
  grantModal.style.display = 'none';
}

document.getElementById('grantModalClose').addEventListener('click', closeGrantModal);
document.getElementById('grantModalCloseBtn').addEventListener('click', closeGrantModal);
grantModal.addEventListener('click', e => {
  if (e.target === grantModal) closeGrantModal();
});

grantSubmitBtn.addEventListener('click', async () => {
  grantError.style.display = 'none';
  grantSuccess.style.display = 'none';

  const siteId = grantSite.value;
  const role = grantRole.value;
  const res = await api(`/sites/${siteId}/invitations/service-account`, {
    method: 'POST',
    body: JSON.stringify({serviceAccountId: Number(grantingAccountId), role})
  });
  if (!res) return;

  if (res.ok) {
    const siteName = adminSites.find(site => String(site.id) === siteId)?.name || 'the site';
    grantSuccess.textContent = `Granted ${ROLE_LABELS[role] || role} access on ${siteName}.`;
    grantSuccess.style.display = 'block';
  } else {
    const data = await res.json();
    grantError.textContent = data.details || 'Could not grant access';
    grantError.style.display = 'block';
  }
});

Promise.all([loadSites(), loadServiceAccounts()]);
