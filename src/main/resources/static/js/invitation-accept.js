const pendingEl = document.getElementById('pending');
const successEl = document.getElementById('success');
const failureEl = document.getElementById('failure');
const pageTitle = document.getElementById('pageTitle');

function showFailure(message) {
  pendingEl.style.display = 'none';
  failureEl.style.display = '';
  pageTitle.textContent = 'Invitation not valid';
  document.getElementById('failureBody').textContent = message;
}

async function acceptInvitation() {
  const token = new URLSearchParams(location.search).get('token');
  if (!token) {
    showFailure('This invitation link is missing its token.');
    return;
  }

  const res = await api('/invitations/accept', {method: 'POST', body: JSON.stringify({token})});
  // A logged-out visitor gets bounced to log in / register and back here by api() itself —
  // it has already redirected, so there's nothing left to do on this page load.
  if (!res) return;

  if (res.ok) {
    const invitation = await res.json();
    pendingEl.style.display = 'none';
    successEl.style.display = '';
    pageTitle.textContent = "You're in!";
    const roleLabel = (ROLE_LABELS[invitation.role] || invitation.role || '').toLowerCase();
    document.getElementById('successBody').textContent = `You've joined this site as ${roleLabel || 'a member'}.`;
    document.getElementById('successLink').href = `/admin/sites/${invitation.siteId}/edit`;
  } else {
    const data = await res.json().catch(() => ({}));
    showFailure(data.details || 'This invitation link is not valid.');
  }
}

acceptInvitation();
