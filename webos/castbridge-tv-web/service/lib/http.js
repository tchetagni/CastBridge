'use strict';
// Serveur HTTP de CastBridge-TV Web (entrée/sortie) : traduit une requête Node en { method, path, query, headers } pour routes.handle().

var http = require('http');
var routes = require('./routes');

var MAX_BODY = 1024 * 1024;

/**
 * opts : { handle(req) -> { status, body }, log(message) }.
 * Chaque réponse porte « Connection: close » : le client Android (HttpURLConnection) garde ses connexions en réserve, et un POST
 * lancé sur une connexion que le serveur a déjà fermée n'est pas rejoué par TvClient ; sans connexion persistante, ce cas n'existe pas.
 * Le journal ne contient jamais la requête (paramètres) : un lien du téléphone peut porter un jeton.
 */
function create(opts) {
  var log = opts.log || function () {};
  var server = http.createServer(function (req, res) {
    var target = routes.parseTarget(req.url || '/');
    var out;
    var length = Number(req.headers['content-length'] || 0);
    if (length > MAX_BODY) {
      out = { status: 413, body: '{"error":"body too large"}' };
    } else {
      try {
        out = opts.handle({ method: req.method, path: target.path, query: target.query, headers: req.headers });
      } catch (e) {
        log('erreur de route ' + target.path + ' : ' + (e && e.message));
        out = { status: 500, body: '{"error":"internal error"}' };
      }
    }
    req.resume();                                  // le corps éventuel est ignoré
    var body = Buffer.from(out.body, 'utf8');
    res.writeHead(out.status, {
      'Content-Type': 'application/json; charset=utf-8',      // sans charset, certains clients liraient l'ASCII
      'Content-Length': body.length,
      'Cache-Control': 'no-store',
      'Connection': 'close'
    });
    res.end(body);
    log(req.method + ' ' + target.path + ' -> ' + out.status);
  });
  server.on('clientError', function (err, socket) {
    try { socket.end('HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n'); } catch (e) { /* socket déjà fermé */ }
  });
  server.setTimeout(15000);
  return server;
}

module.exports = { create: create };
