(function () {
    const form = document.getElementById('postForm');
    const siteId = form.dataset.siteId;
    const postId = form.dataset.postId || null;
    const err = document.getElementById('error');

    function buildBody() {
        const lang = document.getElementById('lang').value;
        const tags = document.getElementById('tags').value
            .split(',').map(t => t.trim()).filter(Boolean);
        return {
            coverUrl: document.getElementById('coverUrl').value.trim() || null,
            translations: {
                [lang]: {
                    title: document.getElementById('title').value.trim(),
                    body: document.getElementById('body').value,
                    slug: document.getElementById('slug').value.trim() || null,
                    excerpt: document.getElementById('excerpt').value.trim() || null,
                },
            },
            tags,
        };
    }

    async function handleError(res) {
        if (res.status === 401) {
            location.href = '/admin/login';
            return;
        }
        const data = await res.json().catch(() => ({}));
        err.textContent = data.message || 'Something went wrong';
        err.style.display = 'block';
    }

    form.addEventListener('submit', async e => {
        e.preventDefault();
        err.style.display = 'none';

        const url = postId ? `/sites/${siteId}/posts/${postId}` : `/sites/${siteId}/posts/`;
        const method = postId ? 'PUT' : 'POST';

        const res = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(buildBody()),
        });

        if (res.ok) {
            location.href = `/admin/sites/${siteId}/posts`;
        } else {
            await handleError(res);
        }
    });

    const publishBtn = document.getElementById('publishBtn');
    if (publishBtn) {
        publishBtn.addEventListener('click', async () => {
            err.style.display = 'none';
            const res = await fetch(`/sites/${siteId}/posts/${postId}/publish`, {
                method: 'POST',
                credentials: 'include',
            });
            if (res.ok) {
                location.href = `/admin/sites/${siteId}/posts`;
            } else {
                await handleError(res);
            }
        });
    }
})();
