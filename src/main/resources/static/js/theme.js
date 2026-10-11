(function () {
  var DARK = 'dark';
  var LIGHT = 'light';
  var HLJS_LIGHT = 'https://cdn.jsdelivr.net/npm/highlight.js@11.11.1/styles/github.min.css';
  var HLJS_DARK = 'https://cdn.jsdelivr.net/npm/highlight.js@11.11.1/styles/github-dark.min.css';

  // Normalise SITE_THEMES to lowercase ('light' | 'dark')
  var themes = (window.SITE_THEMES || ['LIGHT']).map(function (t) {
    return t.toLowerCase();
  });
  var defaultTheme = themes[0];
  var supportsToggle = themes.indexOf(LIGHT) >= 0 && themes.indexOf(DARK) >= 0;

  function getTheme() {
    if (!supportsToggle) return defaultTheme;
    return localStorage.getItem('theme') || systemTheme() || defaultTheme;
  }

  // Follow the OS preference until the reader picks a theme explicitly
  function systemTheme() {
    if (!window.matchMedia) return null;
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? DARK : LIGHT;
  }

  function applyTheme(theme) {
    document.documentElement.classList.toggle('dark', theme === DARK);
    var hljs = document.getElementById('hljs-theme');
    if (hljs) hljs.href = theme === DARK ? HLJS_DARK : HLJS_LIGHT;
  }

  function updateBtn() {
    var btn = document.getElementById('themeBtn');
    if (!btn) return;
    var isDark = document.documentElement.classList.contains('dark');
    btn.setAttribute('aria-label', isDark ? 'Switch to light' : 'Switch to dark');
  }

  // Set html.dark immediately to prevent colour flash
  applyTheme(getTheme());

  document.addEventListener('DOMContentLoaded', function () {
    applyTheme(getTheme()); // also updates hljs link, which exists now
    updateBtn();
  });

  if (supportsToggle && window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function () {
      if (localStorage.getItem('theme')) return;
      applyTheme(getTheme());
      updateBtn();
    });
  }

  window.toggleTheme = function () {
    var next = document.documentElement.classList.contains('dark') ? LIGHT : DARK;
    localStorage.setItem('theme', next);
    applyTheme(next);
    updateBtn();
  };
})();
