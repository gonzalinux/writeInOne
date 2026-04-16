let _refreshPromise = null;

function doRefresh() {
  if (_refreshPromise) return _refreshPromise;
  _refreshPromise = fetch('/auth/refresh', { method: 'POST', credentials: 'include' })
    .finally(() => { _refreshPromise = null; });
  return _refreshPromise;
}

async function api(url, options = {}) {
  const headers = {...options.headers};
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  const res = await fetch(url, {...options, credentials: 'include', headers});

  if (res.status === 401) {
    const refreshRes = await doRefresh();
    if (!refreshRes.ok) {
      location.href = '/admin/login';
      return null;
    }
    return fetch(url, {...options, credentials: 'include', headers});
  }

  return res;
}

async function logout() {
  await api("/auth/logout", { method: 'POST' })
  location.href = '/admin/login';
}

// Silently refresh the access token every 5 minutes
setInterval(doRefresh, 5 * 60 * 1000);
