async function api(url, options = {}) {
  const headers = {...options.headers};
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  const res = await fetch(url, {...options, credentials: 'include', headers});

  if (res.status === 401) {
    const refreshRes = await fetch('/auth/refresh', { method: 'POST', credentials: 'include' });
    if (!refreshRes.ok) {
      location.href = '/admin/login';
      return null;
    }
    return fetch(url, {...options, credentials: 'include', headers});
  }

  return res;
}

// Silently refresh the access token every 5 minutes
setInterval(async function () {
  await fetch('/auth/refresh', { method: 'POST', credentials: 'include' });
}, 5 * 60 * 1000);
