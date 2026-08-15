// Promise-based replacement for window.confirm()/alert(), styled like every other modal in the
// app instead of the browser's native dialog. Native dialogs also block the page's JS thread
// entirely while open, which is worse than it sounds — e.g. it freezes any in-flight fetch UI.
// Self-contained: injects its own markup, so any page just needs to load this script.

const _dialogModal = document.createElement('div');
_dialogModal.className = 'modal';
_dialogModal.style.display = 'none';
_dialogModal.innerHTML = `
  <div class="modal__box">
    <button type="button" class="modal__close" aria-label="Close" id="_dialogClose">×</button>
    <h2 class="modal__title" id="_dialogTitle"></h2>
    <p class="modal__body" id="_dialogBody"></p>
    <div class="modal__footer" id="_dialogFooter"></div>
  </div>`;
document.body.appendChild(_dialogModal);

const _dialogTitle = _dialogModal.querySelector('#_dialogTitle');
const _dialogBody = _dialogModal.querySelector('#_dialogBody');
const _dialogFooter = _dialogModal.querySelector('#_dialogFooter');

let _dialogResolve = null;

function _closeDialog(result) {
  _dialogModal.style.display = 'none';
  const resolve = _dialogResolve;
  _dialogResolve = null;
  if (resolve) resolve(result);
}

function _dialogButton(text, className, result) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = className;
  btn.textContent = text;
  btn.addEventListener('click', () => _closeDialog(result));
  return btn;
}

_dialogModal.querySelector('#_dialogClose').addEventListener('click', () => _closeDialog(false));
_dialogModal.addEventListener('click', e => {
  if (e.target === _dialogModal) _closeDialog(false);
});

/** Resolves true/false — never rejects. */
function confirmModal(message, {title = 'Please confirm', confirmLabel = 'Confirm', danger = false} = {}) {
  return new Promise(resolve => {
    _dialogResolve = resolve;
    _dialogTitle.textContent = title;
    _dialogBody.textContent = message;
    _dialogFooter.innerHTML = '';
    _dialogFooter.appendChild(_dialogButton(confirmLabel, `btn${danger ? ' btn--danger' : ''}`, true));
    _dialogFooter.appendChild(_dialogButton('Cancel', 'btn btn-ghost', false));
    _dialogModal.style.display = 'flex';
  });
}

/** Resolves once dismissed; the value carries no meaning, just like native alert(). */
function alertModal(message, {title = 'Notice'} = {}) {
  return new Promise(resolve => {
    _dialogResolve = resolve;
    _dialogTitle.textContent = title;
    _dialogBody.textContent = message;
    _dialogFooter.innerHTML = '';
    _dialogFooter.appendChild(_dialogButton('OK', 'btn', undefined));
    _dialogModal.style.display = 'flex';
  });
}
