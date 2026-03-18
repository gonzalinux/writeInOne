document.addEventListener('DOMContentLoaded', () => {
    // Confirm before destructive actions
    document.querySelectorAll('[data-confirm]').forEach(el => {
        el.addEventListener('click', e => {
            if (!confirm(el.dataset.confirm)) {
                e.preventDefault();
            }
        });
    });
});
