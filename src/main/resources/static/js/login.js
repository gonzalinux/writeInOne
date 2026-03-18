document.getElementById('loginForm').addEventListener('submit', async e => {
    e.preventDefault();
    console.log("AA")
    const err = document.getElementById('error');
    err.style.display = 'none';
    const res = await fetch('/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
            email: document.getElementById('email').value,
            password: document.getElementById('password').value
        })
    });
    if (res.ok) {
        location.href = '/admin';
    } else {
        const data = await res.json().catch(() => ({}));
        err.textContent = data.message || 'Invalid credentials';
        err.style.display = 'block';
    }
});
