document.querySelectorAll('[data-publish-post]').forEach(btn => {
    btn.addEventListener('click', async () => {
        const siteId = btn.dataset.siteId;
        const postId = btn.dataset.publishPost;

        const res = await fetch(`/sites/${siteId}/posts/${postId}/publish`, {
            method: 'POST',
            credentials: 'include',
        });

        if (res.ok) {
            location.reload();
        } else if (res.status === 401) {
            location.href = '/admin/login';
        } else {
            alert('Failed to publish post');
        }
    });
});
