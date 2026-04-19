const form = document.getElementById('search-form');
const input = document.getElementById('search-input');
const container = document.getElementById('post-list-container');

const lang = form.dataset.lang;
const prefix = window.SITE_PREFIX || '';
const activeTag = new URLSearchParams(window.location.search).get('tag');

// Hide the submit button — search is now live
form.querySelector('.search-bar__btn').hidden = true;

form.addEventListener('submit', e => e.preventDefault());

let debounceTimer = null;
input.addEventListener('input', () => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => doSearch(input.value.trim()), 300);
});

function doSearch(query) {
    const params = new URLSearchParams();
    if (query) params.set('search', query);
    if (activeTag) params.set('tag', activeTag);

    const url = new URL(window.location.href);
    if (query) url.searchParams.set('search', query);
    else url.searchParams.delete('search');
    history.replaceState(null, '', url);

    const clearLink = document.getElementById('search-clear');
    if (clearLink) {
        clearLink.hidden = !query && !activeTag;
    } else if (query || activeTag) {
        const a = document.createElement('a');
        a.id = 'search-clear';
        a.className = 'search-bar__clear';
        a.href = prefix + '/' + lang;
        a.textContent = '✕ Clear';
        form.appendChild(a);
    }

    fetch(prefix + '/' + lang + '/posts?' + params)
        .then(r => r.json())
        .then(page => render(page, query))
        .catch(() => {});
}

function esc(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function formatDate(iso) {
    if (!iso) return '';
    return new Intl.DateTimeFormat(lang === 'es' ? 'es-ES' : 'en-US', {
        month: 'short', day: 'numeric', year: 'numeric'
    }).format(new Date(iso));
}

function render(page, query) {
    if (page.content.length === 0) {
        container.innerHTML = '<p class="empty-state">No posts yet.</p>';
        return;
    }

    const tagFilter = activeTag
        ? `<p class="filter-label">Filtering by tag: <strong>${esc(activeTag)}</strong></p>`
        : '';

    const items = page.content.map(item => {
        const cover = item.post.coverUrl
            ? `<img class="post-card__cover" src="${esc(item.post.coverUrl)}" alt="${esc(item.translation.title)}"/>`
            : '';
        const excerpt = item.translation.excerpt
            ? `<p class="post-card__excerpt">${esc(item.translation.excerpt)}</p>`
            : '';
        const tags = item.tags.length > 0
            ? `<div class="post-card__tags">${item.tags.map(t =>
                `<a class="tag" href="${prefix}/${lang}?tag=${encodeURIComponent(t.name)}">${esc(t.name)}</a>`
            ).join('')}</div>`
            : '';
        const date = formatDate(item.post.publishedAt);
        return `<li class="post-card">
            ${cover}
            <div class="post-card__meta"><time datetime="${esc(item.post.publishedAt)}">${date}</time></div>
            <h2 class="post-card__title"><a href="${prefix}/${lang}/articles/${esc(item.translation.slug)}">${esc(item.translation.title)}</a></h2>
            ${excerpt}
            ${tags}
        </li>`;
    }).join('');

    let pagination = '';
    if (page.totalPages > 1) {
        const qPart = query ? '&search=' + encodeURIComponent(query) : '';
        const tPart = activeTag ? '&tag=' + encodeURIComponent(activeTag) : '';
        const prev = page.page > 0
            ? `<a class="pagination__btn" href="${prefix}/${lang}?page=${page.page - 1}${qPart}${tPart}">← Newer</a>`
            : '';
        const next = page.page + 1 < page.totalPages
            ? `<a class="pagination__btn" href="${prefix}/${lang}?page=${page.page + 1}${qPart}${tPart}">Older →</a>`
            : '';
        pagination = `<nav class="pagination">${prev}<span class="pagination__info">${page.page + 1} / ${page.totalPages}</span>${next}</nav>`;
    }

    container.innerHTML = `${tagFilter}<ul class="post-list">${items}</ul>${pagination}`;
}
