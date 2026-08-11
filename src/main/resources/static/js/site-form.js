const parts = location.pathname.split('/');
const siteId = parts[4] === 'edit' ? parts[3] : null;

const form = document.getElementById('siteForm');
const err = document.getElementById('error');
const pageTitle = document.getElementById('pageTitle');
const submitBtn = document.getElementById('submitBtn');
const nameInput = document.getElementById('name');
const descInput = document.getElementById('description');
const stylesInput = document.getElementById('stylesUrl');
const defaultThemeSelect = document.getElementById('defaultTheme');
const enableSwitcherCb = document.getElementById('enableSwitcher');
const faviconInput = document.getElementById('faviconUrl');
const domainInput = document.getElementById('domain');
const prefixInput = document.getElementById('prefix');
const verificationBadge = document.getElementById('verificationBadge');
const requestVerificationField = document.getElementById('requestVerificationField');
const requestVerificationCb = document.getElementById('requestVerification');
const subdomainField = document.getElementById('subdomainField');
const customDomainField = document.getElementById('customDomainField');
const prefixField = document.getElementById('prefixField');
const subdomainInput = document.getElementById('subdomain');
const baseDomainLabel = document.getElementById('baseDomainLabel');
const subdomainStatus = document.getElementById('subdomainStatus');
const hostingRadios = document.querySelectorAll('input[name="hosting"]');
const englishCb = document.getElementById('lang-ENGLISH');
const spanishCb = document.getElementById('lang-SPANISH');
const headHtmlInput = document.getElementById('headHtml');
const bodyHtmlInput = document.getElementById('bodyHtml');
const cmOptions = {
  mode: 'htmlmixed',
  lineNumbers: true,
  indentWithTabs: false,
  indentUnit: 2,
  tabSize: 2,
  lineWrapping: true
};
const headEditor = CodeMirror.fromTextArea(headHtmlInput, cmOptions);
const bodyEditor = CodeMirror.fromTextArea(bodyHtmlInput, cmOptions);
headEditor.getWrapperElement().classList.add('html-codemirror');
bodyEditor.getWrapperElement().classList.add('html-codemirror');

const enTitle = document.getElementById('en-title');
const enDescription = document.getElementById('en-description');
const enFooter = document.getElementById('en-footer');
const enNav = document.getElementById('en-nav');
const esTitle = document.getElementById('es-title');
const esDescription = document.getElementById('es-description');
const esFooter = document.getElementById('es-footer');
const esNav = document.getElementById('es-nav');

// ── Hosting mode: free subdomain vs. own domain ───────────────────────────

const DEFAULT_SUBDOMAIN_HINT = subdomainStatus.textContent;

let subdomainConfig = {baseDomain: '', minLength: 3, maxLength: 30};
let currentSubdomain = null;   // the label this site already owns, in edit mode
let checkTimer = null;

function hostingMode() {
  return document.querySelector('input[name="hosting"]:checked').value;
}

function syncHostingMode() {
  const sub = hostingMode() === 'subdomain';
  subdomainField.hidden = !sub;
  customDomainField.hidden = sub;
  prefixField.hidden = sub;
  subdomainInput.required = sub;
  domainInput.required = !sub;
}

hostingRadios.forEach(radio => radio.addEventListener('change', syncHostingMode));

function setSubdomainStatus(text, modifier) {
  subdomainStatus.textContent = text;
  subdomainStatus.className = 'hint' + (modifier ? ' ' + modifier : '');
}

async function loadSubdomainConfig() {
  const res = await api('/sites/subdomain');
  if (!res || !res.ok) return;
  subdomainConfig = await res.json();
  baseDomainLabel.textContent = '.' + subdomainConfig.baseDomain;
}

async function checkSubdomain(name) {
  const res = await api(`/sites/subdomain?name=${encodeURIComponent(name)}`);
  if (!res || !res.ok) return;
  const data = await res.json();
  if (subdomainInput.value.trim() !== data.name) return;   // a newer keystroke won
  setSubdomainStatus(
    data.available ? `${data.domain} is available` : (data.reason || 'Not available'),
    data.available ? 'hint--ok' : 'hint--error'
  );
}

subdomainInput.addEventListener('input', () => {
  const name = subdomainInput.value.trim().toLowerCase();
  subdomainInput.value = name;
  clearTimeout(checkTimer);
  if (!name) return setSubdomainStatus(DEFAULT_SUBDOMAIN_HINT);
  if (name === currentSubdomain) return setSubdomainStatus('This is your current subdomain.', 'hint--ok');
  setSubdomainStatus('Checking…');
  checkTimer = setTimeout(() => checkSubdomain(name), 350);
});

// ── Tab navigation ────────────────────────────────────────────────────────

function activateTab(name) {
  document.querySelectorAll('.form-tab')
    .forEach(t => t.classList.toggle('form-tab--active', t.dataset.tab === name));
  document.querySelectorAll('.tab-panel')
    .forEach(p => p.hidden = p.id !== 'tab-' + name);
  if (name === 'code') {
    headEditor.refresh();
    bodyEditor.refresh();
  }
  // The People tab reads from its own endpoint and saves nothing through this form,
  // so the Save button would only be misleading there.
  document.querySelector('.actions').hidden = name === 'people';
}

document.querySelectorAll('.form-tab').forEach(tab => {
  tab.addEventListener('click', () => activateTab(tab.dataset.tab));
});

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
      url: row.querySelector('.nav-url').value.trim(),
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
  document.title = 'Edit site — WriteInOne';
  submitBtn.textContent = 'Save changes';

  nameInput.value = site.name || '';
  descInput.value = site.description || '';
  stylesInput.value = site.stylesUrl || '';
  const themes = site.availableThemes || ['LIGHT'];
  defaultThemeSelect.value = themes[0].toLowerCase();
  enableSwitcherCb.checked = themes.length > 1;
  faviconInput.value = site.config?.faviconUrl || '';

  const suffix = '.' + subdomainConfig.baseDomain;
  const managed = Boolean(subdomainConfig.baseDomain) && (site.domain || '').endsWith(suffix);

  if (managed) {
    currentSubdomain = site.domain.slice(0, -suffix.length);
    document.getElementById('hostingSubdomain').checked = true;
    subdomainInput.value = currentSubdomain;
    setSubdomainStatus('This is your current subdomain.', 'hint--ok');
  } else {
    document.getElementById('hostingCustom').checked = true;
    domainInput.value = site.domain || '';
    prefixInput.value = site.prefix || '';
  }
  syncHostingMode();

  // Subdomains of our own base domain are verified by construction — the badge and the
  // DNS instructions behind it would only be noise there.
  if (!managed) {
    const verified = site.status === 'VERIFIED';
    const expired = !verified && site.verifyDate && (Date.now() - new Date(site.verifyDate).getTime() > 2 * 24 * 60 * 60 * 1000);
    verificationBadge.textContent = verified ? '✓ Verified' : expired ? 'Verification expired' : 'Pending verification';
    verificationBadge.className = 'badge ' + (verified ? 'badge--verified' : expired ? 'badge--expired' : 'badge--pending');
    verificationBadge.hidden = false;
    verificationBadge.classList.add('badge--clickable');
    verificationBadge.onclick = () => showVerificationModal({
      domain: site.domain,
      prefix: site.prefix || '',
      status: site.status,
      verifyDate: site.verifyDate || null,
      siteId,
    });
    requestVerificationField.hidden = verified;
  }

  englishCb.checked = site.languages?.includes('ENGLISH') ?? true;
  spanishCb.checked = site.languages?.includes('SPANISH') ?? false;
  syncLangConfig(englishCb);
  syncLangConfig(spanishCb);

  headEditor.setValue(site.config?.headHtml || '');
  bodyEditor.setValue(site.config?.bodyHtml || '');

  enTitle.value = site.config?.en?.title || '';
  enDescription.value = site.config?.en?.description || '';
  enFooter.value = site.config?.en?.footer || '';
  enNav.innerHTML = '';
  (site.config?.en?.nav || []).forEach(link => enNav.appendChild(makeNavRow(link.label, link.url)));

  esTitle.value = site.config?.es?.title || '';
  esDescription.value = site.config?.es?.description || '';
  esFooter.value = site.config?.es?.footer || '';
  esNav.innerHTML = '';
  (site.config?.es?.nav || []).forEach(link => esNav.appendChild(makeNavRow(link.label, link.url)));

  // Every member may see who else has access, but only admins may touch the settings —
  // so for anyone else this page collapses to the People tab alone.
  document.getElementById('peopleTab').hidden = false;
  loadMembers(site);

  if (!can(site.role, 'admin')) {
    document.querySelectorAll('.form-tab').forEach(t => t.hidden = t.dataset.tab !== 'people');
    pageTitle.textContent = site.name;
    document.title = `${site.name} — WriteInOne`;
    activateTab('people');
  } else if (location.hash === '#people') {
    activateTab('people');
  }
}

// ── People ────────────────────────────────────────────────────────────────

async function loadMembers(site) {
  const memberList = document.getElementById('memberList');
  const memberEmpty = document.getElementById('memberEmpty');

  const res = await api(`/sites/${siteId}/users`);
  if (!res || !res.ok) {
    memberList.innerHTML = '<p class="hint">Could not load the member list.</p>';
    return;
  }

  const members = await res.json();
  memberList.innerHTML = '';
  memberEmpty.hidden = members.length > 1;

  members.forEach(member => {
    // sites.userId is the creator and billing owner — worth calling out, since that
    // membership is the one that cannot be given up.
    const owner = member.userId === site.userId;
    const row = document.createElement('div');
    row.className = 'member-row';
    row.innerHTML = `
      <div class="member-row__info">
        <div class="member-row__name">
          ${escHtml(member.displayName || member.email || 'Unknown user')}
          ${owner ? '<span class="badge badge--owner">Owner</span>' : ''}
        </div>
        <div class="member-row__email">${escHtml(member.email || '')}</div>
      </div>
      ${roleBadge(member.role)}`;
    memberList.appendChild(row);
  });
}

async function init() {
  await loadSubdomainConfig();   // loadSite needs baseDomain to pick the hosting mode
  if (siteId) {
    await loadSite();
    const styleEditorLink = document.getElementById('styleEditorLink');
    styleEditorLink.outerHTML = `<a href="/admin/sites/${siteId}/style-editor">Style Editor</a>`;
  } else {
    pageTitle.textContent = 'New site';
    document.title = 'New site — WriteInOne';
    submitBtn.textContent = 'Create site';
    syncHostingMode();
  }
}

init();

// ── Submit ────────────────────────────────────────────────────────────────

form.addEventListener('submit', async e => {
  e.preventDefault();
  err.style.display = 'none';

  const languages = ['ENGLISH', 'SPANISH'].filter(lang => document.getElementById(`lang-${lang}`)?.checked);

  const defaultT = defaultThemeSelect.value.toUpperCase();
  const otherT = defaultT === 'LIGHT' ? 'DARK' : 'LIGHT';
  const availableThemes = enableSwitcherCb.checked ? [defaultT, otherT] : [defaultT];

  const body = {
    name: nameInput.value.trim(),
    description: descInput.value.trim() || null,
    stylesUrl: stylesInput.value.trim() || null,
    availableThemes,
    languages,
    config: {
      faviconUrl: faviconInput.value.trim() || null,
      headHtml: headEditor.getValue().trim() || null,
      bodyHtml: bodyEditor.getValue().trim() || null,
      en: {
        title: enTitle.value.trim() || null,
        description: enDescription.value.trim() || null,
        footer: enFooter.value.trim(),
        nav: readNavLinks(enNav)
      },
      es: {
        title: esTitle.value.trim() || null,
        description: esDescription.value.trim() || null,
        footer: esFooter.value.trim(),
        nav: readNavLinks(esNav)
      },
    },
  };

  if (hostingMode() === 'subdomain') {
    body.domain = `${subdomainInput.value.trim().toLowerCase()}.${subdomainConfig.baseDomain}`;
  } else {
    body.domain = domainInput.value.trim();
    body.prefix = prefixInput.value.trim() || null;
    if (body.prefix?.startsWith("/"))
      body.prefix = body.prefix.substring(1);
    if (siteId && requestVerificationCb.checked) body.requestVerification = true;
  }

  const url = siteId ? `/sites/${siteId}` : '/sites/';
  const method = siteId ? 'PATCH' : 'POST';

  const res = await api(url, {method, body: JSON.stringify(body)});
  if (!res) return;

  if (res.ok) {
    location.href = '/admin';
  } else {
    const data = await res.json().catch(() => ({}));
    err.textContent = data.message || 'Something went wrong';
    err.style.display = 'block';
  }
});
