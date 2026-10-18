'use strict';
// CastBridge-TV Web : service JS webOS. Fait de la TV LG une « CastBridge-TV » minimale pour le téléphone CastBridge :
//  1. répondeur mDNS (_castbridge._tcp, role=receiver) : le téléphone la découvre ;
//  2. API HTTP sur le port 8765 (hello, info, playurl, pause, resume, stop, seek) : le contrat de la CastBridge-TV Android ;
//  3. pont Luna avec l'app : méthode « events » (abonnement : les commandes du téléphone), « state » (l'app pousse l'état du lecteur),
//     « status » (diagnostic).
// Hors TV : « node service.js --standalone » (HTTP + mDNS sans Luna, lecteur simulé). Compatible Node 8 (pas de ?? ni de ?.).

var os = require('os');
var C = require('./lib/constants');
var args = require('./lib/args');
var netutil = require('./lib/netutil');
var routes = require('./lib/routes');
var bridgeLib = require('./lib/bridge');
var httpLib = require('./lib/http');
var responderLib = require('./lib/responder');
var simulatorLib = require('./lib/simulator');

var Service = null;
try { Service = require('webos-service'); } catch (e) { Service = null; }       // absent hors TV

var standalone = process.argv.indexOf('--standalone') >= 0 || !Service;
// Sur la TV, le lanceur de services peut passer ses propres options : seule la ligne de commande du mode autonome est lue.
var opts = args.parse(standalone ? process.argv.slice(2) : [], process.env);

function log(message) { console.log('[castbridge-tv-web] ' + message); }
function debug(message) { if (opts.verbose) log(message); }

if (opts.help) { console.log(args.USAGE); process.exit(0); }
if (opts.errors.length) { console.error(opts.errors.join('\n') + '\n\n' + args.USAGE); process.exit(2); }
if (standalone && !process.argv.some(function (a) { return a === '--standalone'; })) log('webos-service introuvable : mode autonome (--standalone)');

var bridge = bridgeLib.createBridge({ presenceMs: C.PRESENCE_MS });
var httpState = { listening: false, error: null };
var hostLabel = 'castbridge-tv-web' + (netutil.macSuffix(os.networkInterfaces()) ? '-' + netutil.macSuffix(os.networkInterfaces()) : '');
var svc = { instance: opts.name, type: C.MDNS_SERVICE, host: hostLabel, port: opts.port, txt: C.TXT };

// ---------------------------------------------------------------------------------------------- HTTP
var server = httpLib.create({
  handle: function (req) { return routes.handle(req, { bridge: bridge }); },
  log: debug
});

function listen() {
  server.listen(opts.port, '0.0.0.0', function () {
    httpState.listening = true;
    httpState.error = null;
    log('HTTP : port ' + opts.port);
  });
}

server.on('error', function (e) {
  httpState.listening = false;
  httpState.error = e.message;
  log('HTTP : ' + e.message);
  if (standalone) {
    console.error('Le port ' + opts.port + ' est pris ou refusé : essayez --port <autre>.');
    process.exit(1);
  }
  setTimeout(function () { try { server.close(); } catch (x) { /* pas encore ouvert */ } listen(); }, 5000);      // sur la TV : on réessaie
});

// ---------------------------------------------------------------------------------------------- mDNS
var responder = opts.mdns ? responderLib.create({ svc: svc, address: opts.ip, log: log }) : null;

function status() {
  return {
    returnValue: true, running: true, name: opts.name, version: C.VERSION, apiVersion: C.API_VERSION, port: opts.port,
    addresses: opts.ip ? [opts.ip] : netutil.pickAddresses(os.networkInterfaces()),
    appConnected: bridge.isAlive(), seq: bridge.seq(), http: httpState,
    mdns: responder ? responder.status() : { running: false, disabled: true }
  };
}

// ---------------------------------------------------------------------------------------------- webOS : pont Luna avec l'app
function keepAlive(service) {
  // Un service JS webOS s'arrête quand il est inactif : une activité « keepAlive » le maintient (sockets mDNS et HTTP ouverts).
  try {
    if (service.activityManager && typeof service.activityManager.create === 'function') {
      service.activityManager.create('keepAlive', function () { log('keepAlive actif'); });
    }
  } catch (e) {
    log('keepAlive indisponible : ' + e.message);
  }
}

function registerLuna(ServiceClass) {
  var service = new ServiceClass(C.SERVICE_ID);
  keepAlive(service);
  var subs = {};
  function drop(token) { if (subs[token]) { subs[token](); delete subs[token]; } }

  // events : abonnement. Première réponse « hello » (avec seq, le numéro de la dernière commande déjà émise), puis une réponse par commande.
  service.register('events', function (message) {
    message.respond({ returnValue: true, event: 'hello', subscribed: !!message.isSubscription, seq: bridge.seq(), name: opts.name, version: C.VERSION, port: opts.port });
    if (!message.isSubscription) return;
    var token = message.uniqueToken;
    drop(token);
    subs[token] = bridge.subscribe(function (cmd) {
      var reply = { returnValue: true, event: 'command' };
      Object.keys(cmd).forEach(function (k) { reply[k] = cmd[k]; });
      message.respond(reply);
    });
  }, function (message) { drop(message.uniqueToken); });

  // state : l'app pousse { state, name, pos, dur, ack } (pos et dur en ms) ; la réponse rend les commandes qu'elle n'a pas encore appliquées.
  service.register('state', function (message) {
    var p = message.payload || {};
    var r = bridge.report(p, p.ack);
    message.respond({ returnValue: true, seq: r.seq, resync: r.resync, commands: r.commands });
  });

  service.register('status', function (message) { message.respond(status()); });
  log('Luna : ' + C.SERVICE_ID + ' (events, state, status)');
}

// ---------------------------------------------------------------------------------------------- mode autonome : lecteur simulé
function runSimulator() {
  var sim = simulatorLib.create({});
  bridge.subscribe(function (cmd) {
    sim.apply(cmd);
    bridge.report(sim.current(), sim.ack());            // comme l'app : elle confirme la commande aussitôt
  });
  setInterval(function () { bridge.report(sim.current(), sim.ack()); }, 1000);
}

function banner() {
  var addrs = opts.ip ? [opts.ip] : netutil.pickAddresses(os.networkInterfaces());
  console.log('CastBridge-TV Web ' + C.VERSION + (standalone ? ' (mode autonome, lecteur simulé)' : ''));
  console.log('  nom        : ' + opts.name);
  console.log('  HTTP       : ' + (addrs.length ? addrs : ['127.0.0.1']).map(function (a) { return 'http://' + a + ':' + opts.port + '/api/hello'; }).join('  '));
  console.log('  mDNS       : ' + (responder ? C.MDNS_SERVICE + '.local (role=' + C.TXT.role + ', v=' + C.TXT.v + ')' : 'désactivé (--no-mdns)'));
  if (standalone) console.log('  essai      : curl -s http://127.0.0.1:' + opts.port + '/api/info   (Ctrl+C pour arrêter)');
}

// ---------------------------------------------------------------------------------------------- démarrage et arrêt
function shutdown() {
  var done = false;
  function finish() { if (!done) { done = true; process.exit(0); } }
  setTimeout(finish, 1500).unref();
  var closeHttp = function () { try { server.close(finish); } catch (e) { finish(); } };
  if (responder) responder.stop(closeHttp); else closeHttp();
}

process.on('uncaughtException', function (e) {
  log('erreur non interceptée : ' + (e && e.stack ? e.stack : e));
  if (standalone) process.exit(1);
});
if (standalone) {
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

listen();
if (responder) responder.start();
if (standalone) runSimulator(); else registerLuna(Service);
banner();
