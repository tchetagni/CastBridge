// CastBridge admin (no inline script: strict Content-Security-Policy).

// Confirmation before destructive actions.
document.addEventListener('submit', function (e) {
  var btn = e.submitter || e.target.querySelector('[data-confirm]');
  var msg = btn && btn.getAttribute('data-confirm');
  if (msg && !window.confirm(msg)) e.preventDefault();
});

// Charts: each <canvas data-chart="{…}"> carries its data (HTML-escaped JSON), drawn with the local Chart.js.
document.addEventListener('DOMContentLoaded', function () {
  if (!window.Chart) return;
  var palette = ['#F5B025', '#35C08A', '#6CB6FF', '#FF6B6B', '#FF8A3D', '#27C7B0', '#3B82F6', '#F2C14E'];
  Chart.defaults.color = '#B7C0D4';
  Chart.defaults.borderColor = '#2A3550';
  Chart.defaults.font.family = 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';
  document.querySelectorAll('canvas[data-chart]').forEach(function (canvas) {
    var spec;
    try { spec = JSON.parse(canvas.getAttribute('data-chart')); } catch (err) { return; }
    if (!spec.labels || spec.labels.length === 0) {
      canvas.parentNode.textContent = 'Aucune donnée sur la période.';
      return;
    }
    var line = spec.type === 'line';
    var datasets = spec.datasets.map(function (d, i) {
      var c = palette[i % palette.length];
      return { label: d.label, data: d.data, backgroundColor: line ? c : c + 'cc', borderColor: c, borderWidth: line ? 2 : 0,
               tension: 0.25, pointRadius: line ? 2 : 0, borderRadius: 3 };
    });
    new Chart(canvas, {
      type: line ? 'line' : 'bar',
      data: { labels: spec.labels, datasets: datasets },
      options: {
        indexAxis: spec.horizontal ? 'y' : 'x',
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        plugins: { legend: { display: datasets.length > 1, position: 'bottom' } },
        scales: { x: { beginAtZero: true, ticks: { precision: 0 } }, y: { beginAtZero: true, ticks: { precision: 0 } } }
      }
    });
  });
});

// Licences: "copy" buttons (data-copy = id of the element whose text is copied).
document.addEventListener('click', function (e) {
  var b = e.target.closest && e.target.closest('[data-copy]');
  if (!b) return;
  var el = document.getElementById(b.getAttribute('data-copy'));
  if (!el) return;
  var text = el.value !== undefined ? el.value : el.textContent;
  if (navigator.clipboard) navigator.clipboard.writeText(text).then(function () { b.textContent = 'Copié'; });
  else { el.select && el.select(); document.execCommand('copy'); b.textContent = 'Copié'; }
});
