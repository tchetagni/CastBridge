'use strict';
// Analyse de la ligne de commande du service (pur). Hors TV :
//   node service.js --standalone [--port 8765] [--name "CastBridge-TV Web"] [--ip 192.168.1.20] [--no-mdns] [--verbose]

var C = require('./constants');

/** Retourne { standalone, port, name, ip, mdns, verbose, help, errors[] }. env : variables d'environnement (CASTBRIDGE_TV_NAME, CASTBRIDGE_TV_PORT). */
function parse(argv, env) {
  env = env || {};
  var out = {
    standalone: false, port: C.HTTP_PORT, name: C.INSTANCE, ip: null, mdns: true, verbose: false, help: false, errors: []
  };
  if (env.CASTBRIDGE_TV_NAME) out.name = String(env.CASTBRIDGE_TV_NAME);
  if (env.CASTBRIDGE_TV_PORT) out.port = Number(env.CASTBRIDGE_TV_PORT);
  for (var i = 0; i < argv.length; i++) {
    var a = argv[i];
    if (a === '--standalone') out.standalone = true;
    else if (a === '--no-mdns') out.mdns = false;
    else if (a === '--verbose' || a === '-v') out.verbose = true;
    else if (a === '--help' || a === '-h') out.help = true;
    else if (a === '--port') out.port = Number(argv[++i]);
    else if (a === '--name') out.name = String(argv[++i] === undefined ? '' : argv[i]);
    else if (a === '--ip') out.ip = String(argv[++i] === undefined ? '' : argv[i]);
    else if (/^--(port|name|ip)=/.test(a)) {
      var eq = a.indexOf('=');
      var key = a.slice(2, eq);
      var val = a.slice(eq + 1);
      if (key === 'port') out.port = Number(val); else if (key === 'name') out.name = val; else out.ip = val;
    }
  }
  if (!(out.port >= 1 && out.port <= 65535) || Math.floor(out.port) !== out.port) out.errors.push('--port : entier de 1 à 65535 attendu');
  if (out.name.trim() === '' || out.name.length > 63) out.errors.push('--name : 1 à 63 caractères attendus');
  if (out.ip !== null && !/^\d{1,3}(\.\d{1,3}){3}$/.test(out.ip)) out.errors.push('--ip : adresse IPv4 attendue');
  out.name = out.name.trim();
  return out;
}

var USAGE = [
  'CastBridge-TV Web : service (mDNS + API HTTP du téléphone CastBridge)',
  '',
  '  node service.js --standalone [options]    hors TV (Mac, PC) : lecteur simulé, sans Luna',
  '',
  'Options :',
  '  --port N        port HTTP (défaut ' + C.HTTP_PORT + ')',
  '  --name TEXTE    nom de la TV affiché par le téléphone (défaut « ' + C.INSTANCE + ' »)',
  '  --ip A.B.C.D    adresse IPv4 à annoncer (par défaut : celles des interfaces réseau)',
  '  --no-mdns       pas d\'annonce mDNS (le téléphone se connecte alors par adresse manuelle)',
  '  --verbose, -v   journal détaillé',
  '',
  'Variables : CASTBRIDGE_TV_NAME, CASTBRIDGE_TV_PORT.'
].join('\n');

module.exports = { parse: parse, USAGE: USAGE };
