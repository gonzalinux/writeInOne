const form = document.getElementById('loginForm');
const err = document.getElementById('error');

form.addEventListener('submit', async e => {
  e.preventDefault();
  err.style.display = 'none';

  const res = await fetch('/auth/login', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    credentials: 'include',
    body: JSON.stringify({
      email: document.getElementById('email').value,
      password: document.getElementById('password').value,
    }),
  });

  if (res.ok) {
    // setTimeout(0) defers the navigation to the next event-loop tick so that
    // browsers which commit HttpOnly cookies asynchronously (e.g. older Safari)
    // have time to flush the Set-Cookie headers before the next request fires.
    setTimeout(() => { location.href = '/admin'; }, 0);
  } else {
    const data = await res.json().catch(() => ({}));
    err.textContent = data.message || 'Invalid credentials';
    err.style.display = 'block';
  }
});
