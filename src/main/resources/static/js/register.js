const registerForm = document.getElementById('registerForm');
const otpForm = document.getElementById('otpForm');
const err = document.getElementById('error');
const otpErr = document.getElementById('otpError');
const emailDisplay = document.getElementById('emailDisplay');

let submittedEmail = '';

registerForm.addEventListener('submit', async e => {
  e.preventDefault();
  err.style.display = 'none';

  submittedEmail = document.getElementById('email').value;

  const res = await fetch('/auth/register', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({
      displayName: document.getElementById('displayName').value,
      email: submittedEmail,
      password: document.getElementById('password').value,
    }),
  });

  if (res.ok) {
    emailDisplay.textContent = submittedEmail;
    registerForm.style.display = 'none';
    otpForm.style.display = '';
    document.getElementById('code').focus();
  } else {
    const data = await res.json().catch(() => ({}));
    err.textContent = data.message || 'Registration failed';
    err.style.display = 'block';
  }
});

otpForm.addEventListener('submit', async e => {
  e.preventDefault();
  otpErr.style.display = 'none';

  const res = await fetch('/auth/verify-email', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    credentials: 'include',
    body: JSON.stringify({
      email: submittedEmail,
      code: document.getElementById('code').value,
    }),
  });

  if (res.ok) {
    location.href = '/admin';
  } else {
    const data = await res.json().catch(() => ({}));
    otpErr.textContent = data.message || 'Invalid or expired code';
    otpErr.style.display = 'block';
  }
});

document.getElementById('resendBtn').addEventListener('click', async () => {
  otpErr.style.display = 'none';

  const res = await fetch('/auth/resend-verification', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({email: submittedEmail}),
  });

  if (res.ok) {
    otpErr.textContent = 'A new code has been sent to your email.';
    otpErr.style.color = '#2a7d2a';
    otpErr.style.display = 'block';
  } else {
    otpErr.textContent = 'Could not resend code. Please try again.';
    otpErr.style.color = '#c00';
    otpErr.style.display = 'block';
  }
});

document.getElementById('backBtn').addEventListener('click', () => {
  otpErr.style.display = 'none';
  otpForm.style.display = 'none';
  registerForm.style.display = '';
});
