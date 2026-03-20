// ── Read data ──────────────────────────────────────────────
const data = document.getElementById('preview-data').dataset;
const postId  = data.postId;
const siteId  = data.siteId;
const lang    = data.lang;
let   status  = data.status; // mutable: updated after publish/unpublish/schedule

// ── Elements ──────────────────────────────────────────────
const badge              = document.getElementById('status-badge');
const btnEditMode        = document.getElementById('btn-edit-mode');
const btnPreviewMode     = document.getElementById('btn-preview-mode');
const btnSave            = document.getElementById('btn-save');
const btnPublish         = document.getElementById('btn-publish');
const btnSchedule        = document.getElementById('btn-schedule');
const schedulePanel      = document.getElementById('schedule-panel');
const scheduleInput      = document.getElementById('schedule-input');
const btnScheduleConfirm = document.getElementById('btn-schedule-confirm');
const btnScheduleCancel  = document.getElementById('btn-schedule-cancel');
const editSection        = document.getElementById('edit-section');
const previewSection     = document.getElementById('preview-section');
const errorStrip         = document.getElementById('preview-error');
const unsavedBadge       = document.getElementById('unsaved-badge');

// ── Status badge ──────────────────────────────────────────
function applyStatus(s) {
  status = s;
  badge.textContent = s.toUpperCase();
  badge.className = 'status status--' + s;
  btnPublish.textContent = s === 'published' ? 'Unpublish' : 'Publish';
}

applyStatus(status);

// ── Error helpers ─────────────────────────────────────────
function showError(msg) {
  errorStrip.textContent = msg;
  errorStrip.hidden = false;
}

function clearError() {
  errorStrip.hidden = true;
  errorStrip.textContent = '';
}

// ── Sync preview DOM from edit inputs ─────────────────────
function syncPreview() {
  const title   = document.getElementById('edit-title').value;
  const excerpt = document.getElementById('edit-excerpt').value;
  const body    = document.getElementById('edit-body').value;
  const cover   = document.getElementById('edit-cover').value.trim();

  const previewTitle = previewSection.querySelector('.post-header__title');
  if (previewTitle) previewTitle.textContent = title;

  const previewExcerpt = previewSection.querySelector('.post-header__excerpt');
  if (previewExcerpt) previewExcerpt.textContent = excerpt;

  const previewCover = previewSection.querySelector('.post-header__cover');
  if (previewCover) {
    previewCover.src    = cover;
    previewCover.hidden = !cover;
  }

  const previewBody = previewSection.querySelector('.post-body');
  if (previewBody) previewBody.innerHTML = marked.parse(body);
}

// ── Mode toggle ───────────────────────────────────────────
function setMode(mode) {
  if (mode === 'edit') {
    editSection.hidden    = false;
    previewSection.hidden = true;
    btnEditMode.classList.add('preview-bar__btn--active');
    btnPreviewMode.classList.remove('preview-bar__btn--active');
  } else {
    syncPreview();
    editSection.hidden    = true;
    previewSection.hidden = false;
    btnPreviewMode.classList.add('preview-bar__btn--active');
    btnEditMode.classList.remove('preview-bar__btn--active');
  }
}

btnEditMode.addEventListener('click', () => setMode('edit'));
btnPreviewMode.addEventListener('click', () => setMode('preview'));

// ── Auto-grow textarea ────────────────────────────────────
const bodyEditor = document.getElementById('edit-body');
function autoGrow() {
  bodyEditor.style.height = 'auto';
  bodyEditor.style.height = bodyEditor.scrollHeight + 'px';
}
bodyEditor.addEventListener('input', autoGrow);
btnEditMode.addEventListener('click', autoGrow);

// ── Unsaved indicator ─────────────────────────────────────
const editInputs = document.querySelectorAll('#edit-section .edit-input');
editInputs.forEach(el => el.addEventListener('input', () => {
  unsavedBadge.hidden = false;
  window.onbeforeunload = () => true;
}));

// ── Save ──────────────────────────────────────────────────
btnSave.addEventListener('click', async () => {
  clearError();
  const title    = document.getElementById('edit-title').value;
  const body     = document.getElementById('edit-body').value;
  const excerpt  = document.getElementById('edit-excerpt').value;
  const slug     = document.getElementById('edit-slug').value;
  const coverUrl = document.getElementById('edit-cover').value.trim() || null;
  const tagsRaw  = document.getElementById('edit-tags').value;
  const tags     = tagsRaw.split(',').map(t => t.trim()).filter(Boolean);

  const payload = {
    coverUrl,
    translations: {
      [lang]: { title, body, slug, excerpt }
    },
    tags
  };

  const res = await api(`/sites/${siteId}/posts/${postId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });

  if (!res) return; // 401 → redirected
  if (res.ok) {
    unsavedBadge.hidden = true;
    window.onbeforeunload = null;
    location.reload();
  } else {
    const text = await res.text().catch(() => res.statusText);
    showError('Save failed: ' + text);
  }
});

// ── Publish / Unpublish ───────────────────────────────────
btnPublish.addEventListener('click', async () => {
  clearError();
  const action = status === 'published' ? 'unpublish' : 'publish';

  const res = await api(`/sites/${siteId}/posts/${postId}/${action}`, {
    method: 'POST'
  });

  if (!res) return;
  if (res.ok) {
    applyStatus(action === 'publish' ? 'published' : 'draft');
  } else {
    const text = await res.text().catch(() => res.statusText);
    showError(action.charAt(0).toUpperCase() + action.slice(1) + ' failed: ' + text);
  }
});

// ── Schedule toggle ───────────────────────────────────────
btnSchedule.addEventListener('click', () => {
  schedulePanel.hidden = !schedulePanel.hidden;
});

btnScheduleCancel.addEventListener('click', () => {
  schedulePanel.hidden = true;
});

// ── Schedule confirm ──────────────────────────────────────
btnScheduleConfirm.addEventListener('click', async () => {
  clearError();
  const value = scheduleInput.value;
  if (!value) {
    showError('Please select a date and time.');
    return;
  }
  const scheduledAt = new Date(value).toISOString();

  const res = await api(`/sites/${siteId}/posts/${postId}/schedule`, {
    method: 'POST',
    body: JSON.stringify({ scheduledAt })
  });

  if (!res) return;
  if (res.ok) {
    applyStatus('scheduled');
    schedulePanel.hidden = true;
  } else {
    const text = await res.text().catch(() => res.statusText);
    showError('Schedule failed: ' + text);
  }
});
