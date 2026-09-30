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
  var palette = ['#4ea1ff', '#3ecf8e', '#f5b841', '#ff6b6b', '#b388ff', '#4dd0e1', '#ff9f68', '#a3e635'];
  Chart.defaults.color = '#8b95a3';
  Chart.defaults.borderColor = '#2a3039';
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
