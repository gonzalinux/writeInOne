(function () {
    const form = document.getElementById('siteForm');
    const siteId = form.dataset.siteId || null;
    const err = document.getElementById('error');

    // ── Language toggles ──────────────────────────────────────────────────────

    function syncLangConfig(checkbox) {
        const config = document.getElementById('config-' + checkbox.value);
        if (config) config.style.display = checkbox.checked ? '' : 'none';
    }

    document.querySelectorAll('input[id^="lang-"]').forEach(cb => {
        syncLangConfig(cb);
        cb.addEventListener('change', () => syncLangConfig(cb));
    });

    // ── Nav link rows ─────────────────────────────────────────────────────────

    function makeNavRow() {
        const row = document.createElement('div');
        row.className = 'nav-link-row';
        row.innerHTML =
            '<input type="text" class="nav-label" placeholder="Label"/>' +
            '<input type="url" class="nav-url" placeholder="https://..."/>' +
            '<button type="button" class="btn-icon remove-nav-link" title="Remove">×</button>';
        return row;
    }

    function readNavLinks(containerId) {
        return Array.from(document.querySelectorAll(`#${containerId} .nav-link-row`))
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

    // ── Submit ────────────────────────────────────────────────────────────────

    form.addEventListener('submit', async e => {
        e.preventDefault();
        err.style.display = 'none';

        const languages = ['ENGLISH', 'SPANISH']
            .filter(lang => document.getElementById(`lang-${lang}`)?.checked);

        const body = {
            name: document.getElementById('name').value.trim(),
            description: document.getElementById('description').value.trim() || null,
            stylesUrl: document.getElementById('stylesUrl').value.trim() || null,
            languages,
            config: {
                faviconUrl: document.getElementById('faviconUrl').value.trim() || null,
                en: {
                    footer: document.getElementById('en-footer').value.trim(),
                    nav: readNavLinks('en-nav'),
                },
                es: {
                    footer: document.getElementById('es-footer').value.trim(),
                    nav: readNavLinks('es-nav'),
                },
            },
        };

        let url, method;
        if (siteId) {
            url = `/sites/${siteId}`;
            method = 'PUT';
        } else {
            url = '/sites/';
            method = 'POST';
            body.domain = document.getElementById('domain').value.trim();
        }

        const res = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(body),
        });

        if (res.ok) {
            location.href = '/admin';
        } else if (res.status === 401) {
            location.href = '/admin/login';
        } else {
            const data = await res.json().catch(() => ({}));
            err.textContent = data.message || 'Something went wrong';
            err.style.display = 'block';
        }
    });
})();
