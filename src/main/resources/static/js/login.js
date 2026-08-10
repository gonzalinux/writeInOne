const loginForm = document.getElementById('loginForm');
const forgotForm = document.getElementById('forgotForm');
const resetForm = document.getElementById('resetForm');
const pageTitle = document.getElementById('pageTitle');
const err = document.getElementById('error');
const forgotErr = document.getElementById('forgotError');
const resetErr = document.getElementById('resetError');
const resetEmailDisplay = document.getElementById('resetEmailDisplay');

let resetEmail = '';

function show(form, title) {
  loginForm.style.display = 'none';
  forgotForm.style.display = 'none';
  resetForm.style.display = 'none';
  form.style.display = '';
  pageTitle.textContent = title;
}

loginForm.addEventListener('submit', async e => {
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
    setTimeout(() => {
      location.href = '/admin';
    }, 0);
  } else {
    const data = await res.json().catch(() => ({}));
    err.textContent = data.message || 'Invalid credentials';
    err.style.display = 'block';
  }
});

document.getElementById('forgotLink').addEventListener('click', e => {
  e.preventDefault();
  show(forgotForm, 'Reset password');
  document.getElementById('forgotEmail').focus();
});

forgotForm.addEventListener('submit', async e => {
  e.preventDefault();
  forgotErr.style.display = 'none';

  resetEmail = document.getElementById('forgotEmail').value;

  const res = await fetch('/auth/forgot-password', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({email: resetEmail}),
  });

  if (res.ok) {
    resetEmailDisplay.textContent = resetEmail;
    show(resetForm, 'Reset password');
    document.getElementById('resetCode').focus();
  } else {
    const data = await res.json().catch(() => ({}));
    forgotErr.textContent = data.message || 'Could not send reset code. Please try again.';
    forgotErr.style.display = 'block';
  }
});

resetForm.addEventListener('submit', async e => {
  e.preventDefault();
  resetErr.style.display = 'none';

  const res = await fetch('/auth/reset-password', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({
      email: resetEmail,
      code: document.getElementById('resetCode').value,
      password: document.getElementById('newPassword').value,
    }),
  });

  if (res.ok) {
    resetErr.textContent = 'Password reset. Redirecting to log in…';
    resetErr.style.color = '#2a7d2a';
    resetErr.style.display = 'block';
    setTimeout(() => {
      location.href = '/admin/login';
    }, 1500);
  } else {
    const data = await res.json().catch(() => ({}));
    resetErr.textContent = data.message || 'Invalid or expired code.';
    resetErr.style.color = '#c00';
    resetErr.style.display = 'block';
  }
});

document.getElementById('resendResetBtn').addEventListener('click', async () => {
  resetErr.style.display = 'none';

  const res = await fetch('/auth/forgot-password', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({email: resetEmail}),
  });

  if (res.ok) {
    resetErr.textContent = 'A new code has been sent to your email.';
    resetErr.style.color = '#2a7d2a';
    resetErr.style.display = 'block';
  } else {
    resetErr.textContent = 'Could not resend code. Please try again.';
    resetErr.style.color = '#c00';
    resetErr.style.display = 'block';
  }
});

document.getElementById('backToLoginBtn').addEventListener('click', () => {
  forgotErr.style.display = 'none';
  show(loginForm, 'Log in');
});

document.getElementById('backToForgotBtn').addEventListener('click', () => {
  resetErr.style.display = 'none';
  show(forgotForm, 'Reset password');
  document.getElementById('forgotEmail').focus();
});
