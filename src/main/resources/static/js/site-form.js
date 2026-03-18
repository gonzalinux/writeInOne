const parts  = location.pathname.split('/');
const siteId = parts[4] === 'edit' ? parts[3] : null;

const form          = document.getElementById('siteForm');
const err           = document.getElementById('error');
const pageTitle     = document.getElementById('pageTitle');
const submitBtn     = document.getElementById('submitBtn');
const nameInput     = document.getElementById('name');
const descInput     = document.getElementById('description');
const stylesInput       = document.getElementById('stylesUrl');
const defaultThemeSelect = document.getElementById('defaultTheme');
const enableSwitcherCb  = document.getElementById('enableSwitcher');
const faviconInput      = document.getElementById('faviconUrl');
const domainField   = document.getElementById('domainField');
const domainReadonly = document.getElementById('domainReadonly');
const domainDisplay = document.getElementById('domainDisplay');
const domainInput   = document.getElementById('domain');
const englishCb     = document.getElementById('lang-ENGLISH');
const spanishCb     = document.getElementById('lang-SPANISH');
const enFooter      = document.getElementById('en-footer');
const esFooter      = document.getElementById('es-footer');
const enNav         = document.getElementById('en-nav');
const esNav         = document.getElementById('es-nav');

// ── Language toggles ──────────────────────────────────────────────────────

function syncLangConfig(checkbox) {
  const config = document.getElementById(`config-${checkbox.value}`);
  if (config) config.style.display = checkbox.checked ? '' : 'none';
}

document.querySelectorAll('input[id^="lang-"]').forEach(cb => {
  syncLangConfig(cb);
  cb.addEventListener('change', () => syncLangConfig(cb));
});

// ── Nav link rows ─────────────────────────────────────────────────────────

function makeNavRow(label = '', url = '') {
  const row = document.createElement('div');
  row.className = 'nav-link-row';
  row.innerHTML = `
    <input type="text" class="nav-label" placeholder="Label" value="${label}"/>
    <input type="url" class="nav-url" placeholder="https://..." value="${url}"/>
    <button type="button" class="btn-icon remove-nav-link" title="Remove">×</button>`;
  return row;
}

function readNavLinks(container) {
  return Array.from(container.querySelectorAll('.nav-link-row'))
    .map(row => ({
      label: row.querySelector('.nav-label').value.trim(),
      url:   row.querySelector('.nav-url').value.trim(),
    }))
    .filter(link => link.label || link.url);
}

document.querySelectorAll('[data-add-nav]').forEach(btn => {
  btn.addEventListener('click', () => {
    document.getElementById(btn.dataset.addNav).appendChild(makeNavRow());
  });
});

document.addEventListener('click', e => {
  if (e.target.classList.contains('remove-nav-link')) {
    e.target.closest('.nav-link-row').remove();
  }
});

// ── Load existing site (edit mode) ────────────────────────────────────────

async function loadSite() {
  const res = await api(`/sites/${siteId}`);
  if (!res) return;
  if (!res.ok) return;

  const site = await res.json();

  pageTitle.textContent = 'Edit site';
  document.title        = 'Edit site — WriteInOne';
  submitBtn.textContent = 'Save changes';

  nameInput.value    = site.name        || '';
  descInput.value    = site.description || '';
  stylesInput.value  = site.stylesUrl   || '';
  const themes = site.availableThemes || ['LIGHT'];
  defaultThemeSelect.value = themes[0].toLowerCase();
  enableSwitcherCb.checked = themes.length > 1;
  faviconInput.value = site.config?.faviconUrl || '';

  domainField.style.display    = 'none';
  domainReadonly.style.display = '';
  domainDisplay.value          = site.domain || '';
  if (domainInput) domainInput.removeAttribute('required');

  englishCb.checked = site.languages?.includes('ENGLISH') ?? true;
  spanishCb.checked = site.languages?.includes('SPANISH') ?? false;
  syncLangConfig(englishCb);
  syncLangConfig(spanishCb);

  enFooter.value  = site.config?.en?.footer || '';
  enNav.innerHTML = '';
  (site.config?.en?.nav || []).forEach(link => enNav.appendChild(makeNavRow(link.label, link.url)));

  esFooter.value  = site.config?.es?.footer || '';
  esNav.innerHTML = '';
  (site.config?.es?.nav || []).forEach(link => esNav.appendChild(makeNavRow(link.label, link.url)));
}

if (siteId) {
  loadSite();
} else {
  pageTitle.textContent = 'New site';
  document.title        = 'New site — WriteInOne';
  submitBtn.textContent = 'Create site';
}

// ── Submit ────────────────────────────────────────────────────────────────

form.addEventListener('submit', async e => {
  e.preventDefault();
  err.style.display = 'none';

  const languages = ['ENGLISH', 'SPANISH'].filter(lang => document.getElementById(`lang-${lang}`)?.checked);

  const defaultT = defaultThemeSelect.value.toUpperCase();
  const otherT   = defaultT === 'LIGHT' ? 'DARK' : 'LIGHT';
  const availableThemes = enableSwitcherCb.checked ? [defaultT, otherT] : [defaultT];

  const body = {
    name:        nameInput.value.trim(),
    description: descInput.value.trim() || null,
    stylesUrl:   stylesInput.value.trim() || null,
    availableThemes,
    languages,
    config: {
      faviconUrl: faviconInput.value.trim() || null,
      en: { footer: enFooter.value.trim(), nav: readNavLinks(enNav) },
      es: { footer: esFooter.value.trim(), nav: readNavLinks(esNav) },
    },
  };

  if (!siteId) body.domain = domainInput.value.trim();

  const url    = siteId ? `/sites/${siteId}` : '/sites/';
  const method = siteId ? 'PUT' : 'POST';

  const res = await api(url, { method, body: JSON.stringify(body) });
  if (!res) return;

  if (res.ok) {
    location.href = '/admin';
  } else {
    const data = await res.json().catch(() => ({}));
    err.textContent   = data.message || 'Something went wrong';
    err.style.display = 'block';
  }
});
