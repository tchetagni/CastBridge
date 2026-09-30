// CastBridge admin: confirmation before destructive actions (no inline script: strict Content-Security-Policy).
document.addEventListener('submit', function (e) {
  var btn = e.submitter || e.target.querySelector('[data-confirm]');
  var msg = btn && btn.getAttribute('data-confirm');
  if (msg && !window.confirm(msg)) e.preventDefault();
});
