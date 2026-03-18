async function api(url, options = {}) {
  const headers = {...options.headers};
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  const res = await fetch(url, {...options, credentials: 'include', headers});

  if (res.status === 401) {
    location.href = '/admin/login';
    return null;
  }

  return res;
}
