document.querySelectorAll('[data-confirm]').forEach(el => {
  el.addEventListener('click', e => {
    if (!confirm(el.dataset.confirm)) e.preventDefault();
  });
});

// ── Verification modal ─────────────────────────────────────────────────────

function showVerificationModal({ status, domain, prefix, siteId, verifyDate }) {
  const verifyUrl  = `https://${domain}${prefix ? '/' + prefix : ''}/_verify`;
  const siteUrl    = `https://${domain}${prefix ? '/' + prefix : ''}`;
  const expired    = status !== 'VERIFIED' && verifyDate &&
                     (Date.now() - new Date(verifyDate).getTime() > 2 * 24 * 60 * 60 * 1000);

  let title, body, action = null;

  if (status === 'VERIFIED') {
    title = '✓ Domain verified';
    body  = `Your site is live and accessible at <a href="${siteUrl}" target="_blank" rel="noopener">${siteUrl}</a>.`;
  } else if (expired) {
    title = 'Verification expired';
    body  = `The verification window has passed for <strong>${domain}</strong>.<br><br>
             To verify, make sure <a href="${verifyUrl}" target="_blank" rel="noopener">${verifyUrl}</a>
             is reachable (i.e. your domain is pointing to this server). Then request a new verification.`;
    action = { label: 'Request re-verification', siteId };
  } else {
    const msLeft    = verifyDate ? (new Date(verifyDate).getTime() + 2 * 24 * 60 * 60 * 1000 - Date.now()) : null;
    const hoursLeft = msLeft ? Math.max(0, Math.floor(msLeft / 3600000)) : null;
    const timeLeft  = hoursLeft != null ? ` You have <strong>${hoursLeft}h</strong> remaining.` : '';
    title = 'Pending verification';
    body  = `Point <strong>${domain}</strong> to this server and make sure
             <a href="${verifyUrl}" target="_blank" rel="noopener">${verifyUrl}</a>
             is reachable. The scheduler checks automatically every few minutes.${timeLeft}`;
  }

  const modal = document.getElementById('verificationModal');
  modal.querySelector('.modal__title').textContent = title;
  modal.querySelector('.modal__body').innerHTML    = body;

  const btn = modal.querySelector('.modal__action');
  if (action) {
    btn.textContent  = action.label;
    btn.style.display = '';
    btn.onclick = async () => {
      const res = await api(`/sites/${action.siteId}`, {
        method: 'PATCH',
        body: JSON.stringify({ requestVerification: true })
      });
      if (res?.ok) location.reload();
      else alert('Failed to request re-verification');
    };
  } else {
    btn.style.display = 'none';
  }

  modal.style.display = 'flex';
}

document.addEventListener('click', e => {
  const modal = document.getElementById('verificationModal');
  if (!modal) return;
  if (e.target === modal || e.target.closest('.modal__close')) {
    modal.style.display = 'none';
  }
});
