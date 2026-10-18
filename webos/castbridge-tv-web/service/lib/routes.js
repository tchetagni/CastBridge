'use strict';
// Routes HTTP de CastBridge-TV Web : le contrat de ReceiverServer.route() que le téléphone appelle (TvClient), en plus petit.
// Module pur : handle(requête, contexte) -> { status, body } ; l'entrée/sortie réseau est dans http.js.
//
//   GET  /api/hello                      -> {"app":"castbridge-tv","v":...,"pinRequired":false,...}
//   GET  /api/info                       -> {"files":[],"free":0,"used":0,"quota":0,"player":{"state","name","pos","dur"}}
//   POST /api/playurl?url=&title=&pos=   -> lit un lien http(s) (rien n'est copié) ; 400 / 409 sinon
//   POST /api/pause | /api/resume | /api/stop | /api/seek?pos=   -> commande puis état du lecteur (info)
//   le reste -> 404 {"error":"not found"}

var C = require('./constants');
var player = require('./player');

function decode(s) {
  s = s.replace(/\+/g, ' ');
  try { return decodeURIComponent(s); } catch (e) { return s; }
}

/** « /api/playurl?url=a%20b&pos=3 » -> { path, query } ; la première valeur d'un paramètre répété gagne (comme NanoHTTPD). */
function parseTarget(raw) {
  var i = raw.indexOf('?');
  var path = i < 0 ? raw : raw.slice(0, i);
  var search = i < 0 ? '' : raw.slice(i + 1);
  try { path = decodeURIComponent(path); } catch (e) { /* chemin laissé tel quel */ }
  var query = Object.create(null);
  search.split('&').forEach(function (pair) {
    if (!pair) return;
    var j = pair.indexOf('=');
    var k = decode(j < 0 ? pair : pair.slice(0, j));
    var v = j < 0 ? '' : decode(pair.slice(j + 1));
    if (!(k in query)) query[k] = v;
  });
  return { path: path, query: query };
}

function json(status, obj) { return { status: status, body: JSON.stringify(obj) }; }

/** ReceiverServer.safeName : ni vide, ni plus de 200 caractères, ni point initial, ni « / », « \ » ou NUL ; sinon null. */
function safeName(n) {
  var t = String(n === undefined || n === null ? '' : n).trim();
  if (t === '' || t.length > C.MAX_TITLE || t.charAt(0) === '.') return null;
  if (t.indexOf('/') >= 0 || t.indexOf('\\') >= 0 || t.indexOf('\u0000') >= 0) return null;
  return t;
}

/** toLongOrNull() de Kotlin : un entier signé entier, sinon null. */
function toLong(s) {
  return typeof s === 'string' && /^[+-]?\d{1,15}$/.test(s) ? Number(s) : null;
}

function hello() {
  // app, v et pinRequired sont les trois clés de la CastBridge-TV Android ; platform et build sont additives.
  return { app: 'castbridge-tv', v: C.API_VERSION, pinRequired: false, platform: 'webos', build: C.VERSION };
}

function infoReply(ctx) { return { status: 200, body: player.infoJson(ctx.bridge.snapshot()) }; }

function control(ctx, cmd) {
  if (ctx.bridge.isAlive()) ctx.bridge.send(cmd);       // app absente : rien à commander, l'état au repos est rendu
  return infoReply(ctx);
}

function playUrl(q, ctx) {
  var url = q.url || '';
  var at = url.indexOf('://');
  var scheme = at < 0 ? '' : url.slice(0, at).toLowerCase();
  if ((scheme !== 'http' && scheme !== 'https') || url.length > C.MAX_URL || /\s/.test(url)) return json(400, { error: 'http(s) url required' });
  var title = safeName(q.title) || C.DEFAULT_TITLE;
  var pos = Math.max(0, toLong(q.pos) || 0);
  if (!ctx.bridge.isAlive()) return json(409, { error: 'foreground', message: C.FOREGROUND_MESSAGE });
  ctx.bridge.send({ cmd: 'play', url: url, title: title, pos: pos });
  return infoReply(ctx);
}

/**
 * req : { method, path, query, headers }. ctx : { bridge }.
 * Un en-tête Origin (navigateur) est refusé : le téléphone et curl n'en envoient pas, une page web ouverte sur le même
 * Wi-Fi ne doit pas pouvoir lancer une lecture (la TV n'a pas de code, voir docs/TV-WEB-WEBOS.md, D-WEB-3).
 */
function handle(req, ctx) {
  var method = String(req.method || 'GET').toUpperCase();
  var q = req.query || {};
  if (req.headers && req.headers.origin) return json(403, { error: 'origine non autorisée' });
  switch (req.path) {
    case '/api/hello': return method === 'GET' ? json(200, hello()) : json(405, { error: 'use GET' });
    case '/api/info': return method === 'GET' ? infoReply(ctx) : json(405, { error: 'use GET' });
    case '/api/playurl': return method === 'POST' ? playUrl(q, ctx) : json(405, { error: 'use POST' });
    case '/api/pause': return method === 'POST' ? control(ctx, { cmd: 'pause' }) : json(405, { error: 'use POST' });
    case '/api/resume': return method === 'POST' ? control(ctx, { cmd: 'resume' }) : json(405, { error: 'use POST' });
    case '/api/stop': return method === 'POST' ? control(ctx, { cmd: 'stop' }) : json(405, { error: 'use POST' });
    case '/api/seek': return method === 'POST' ? control(ctx, { cmd: 'seek', pos: Math.max(0, toLong(q.pos) || 0) }) : json(405, { error: 'use POST' });
    default: return json(404, { error: 'not found' });
  }
}

module.exports = { handle: handle, parseTarget: parseTarget, safeName: safeName, toLong: toLong, hello: hello };
