document.getElementById('registerForm').addEventListener('submit', async e => {
    e.preventDefault();
    const err = document.getElementById('error');
    err.style.display = 'none';
    const res = await fetch('/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
            displayName: document.getElementById('displayName').value,
            email: document.getElementById('email').value,
            password: document.getElementById('password').value
        })
    });
    if (res.ok) {
        location.href = '/admin';
    } else {
        const data = await res.json().catch(() => ({}));
        err.textContent = data.message || 'Registration failed';
        err.style.display = 'block';
    }
});
