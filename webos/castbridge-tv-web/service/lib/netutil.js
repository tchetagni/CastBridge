'use strict';
// Choix des adresses IPv4 à annoncer (pur : la liste des interfaces est passée en paramètre, sous la forme d'os.networkInterfaces()).

function isIPv4(s) {
  var m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(String(s));
  return !!m && [m[1], m[2], m[3], m[4]].every(function (x) { return Number(x) <= 255; });
}

function toInt(ip) {
  var p = ip.split('.').map(Number);
  return ((p[0] * 16777216) + (p[1] * 65536) + (p[2] * 256) + p[3]) >>> 0;
}

/** Les adresses IPv4 joignables d'un autre appareil : ni boucle locale, ni lien local (169.254/16). */
function candidates(ifaces) {
  var out = [];
  Object.keys(ifaces || {}).forEach(function (name) {
    (ifaces[name] || []).forEach(function (f) {
      var v4 = f.family === 'IPv4' || f.family === 4;       // Node 18.0 à 18.3 donne un nombre
      if (!v4 || f.internal || !isIPv4(f.address)) return;
      if (/^169\.254\./.test(f.address) || /^127\./.test(f.address)) return;
      out.push({ name: name, address: f.address, netmask: f.netmask, mac: f.mac });
    });
  });
  return out;
}

/** Les adresses de l'interface qui est dans le même sous-réseau que peerIp ; à défaut, toutes les adresses joignables. */
function pickAddresses(ifaces, peerIp) {
  var all = candidates(ifaces);
  var picked = all;
  if (peerIp && isIPv4(peerIp)) {
    var peer = toInt(peerIp);
    var same = all.filter(function (c) {
      if (!c.netmask || !isIPv4(c.netmask)) return false;
      var mask = toInt(c.netmask);
      return ((toInt(c.address) & mask) >>> 0) === ((peer & mask) >>> 0);
    });
    if (same.length) picked = same;
  }
  var seen = {};
  return picked.map(function (c) { return c.address; }).filter(function (a) { if (seen[a]) return false; seen[a] = true; return true; });
}

/** Six chiffres hexadécimaux stables dérivés de l'adresse MAC de la première interface joignable (nom d'hôte mDNS unique). */
function macSuffix(ifaces) {
  var c = candidates(ifaces).filter(function (x) { return x.mac && x.mac !== '00:00:00:00:00:00'; })[0];
  return c ? c.mac.replace(/[^0-9a-f]/gi, '').toLowerCase().slice(-6) : '';
}

module.exports = { isIPv4: isIPv4, toInt: toInt, candidates: candidates, pickAddresses: pickAddresses, macSuffix: macSuffix };
