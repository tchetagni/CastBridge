'use strict';
// Pont entre l'API HTTP (téléphone) et l'app de la TV : commandes numérotées vers l'app, état rapporté par l'app, présence.
// Module pur : l'horloge est injectée. Le transport (abonnement Luna ou lecteur simulé) est branché par subscribe().

var player = require('./player');

/**
 * opts : { now, presenceMs, keep }.
 *  - send(cmd)        numérote la commande (seq), la remet aux abonnés et en déduit l'état « optimiste » à montrer au téléphone ;
 *  - report(raw, ack) état poussé par l'app ; ack = dernière commande qu'elle a appliquée. Un rapport dont l'ack est en retard
 *                     sur seq date d'avant une commande : il est ignoré (sinon l'état reviendrait en arrière un instant) ;
 *  - snapshot()       l'état du lecteur pour /api/info : au repos si l'app est absente.
 */
function createBridge(opts) {
  opts = opts || {};
  var now = opts.now || Date.now;
  var presenceMs = opts.presenceMs || 10000;
  var keep = opts.keep || 32;
  var maxAgeMs = opts.maxAgeMs || 15000;      // une commande plus vieille n'est plus rejouée à une app qui revient (pas de lecture « fantôme »)
  var seq = 0;
  var log = [];
  var lastSeen = -Infinity;
  var reported = player.idle();
  var reportedAt = 0;
  var optimistic = null;
  var optimisticAt = 0;
  var nextId = 1;
  var sinks = {};

  function isAlive() { return now() - lastSeen <= presenceMs; }

  function touch() {
    if (!isAlive()) { optimistic = null; reported = player.idle(); }      // l'app revient après une absence : on repart d'un état propre
    lastSeen = now();
  }

  function snapshot() {
    if (!isAlive()) return player.idle();
    var base = optimistic || reported;
    var at = optimistic ? optimisticAt : reportedAt;
    return player.extrapolate(base, now() - at);
  }

  function subscribe(sink) {
    var id = nextId++;
    sinks[id] = sink;
    touch();
    return function unsubscribe() { delete sinks[id]; };
  }

  function send(cmd) {
    var c = {};
    Object.keys(cmd).forEach(function (k) { c[k] = cmd[k]; });
    c.seq = ++seq;
    log.push({ cmd: c, at: now() });
    while (log.length > keep) log.shift();
    optimistic = player.applyCommand(snapshot(), c);
    optimisticAt = now();
    Object.keys(sinks).forEach(function (id) {
      try { sinks[id](c); } catch (e) { delete sinks[id]; }               // un abonné mort ne bloque pas les autres
    });
    return c;
  }

  function since(ack) {
    var t = now();
    return log.filter(function (e) { return e.cmd.seq > ack && t - e.at <= maxAgeMs; }).map(function (e) { return e.cmd; });
  }

  function report(raw, ack) {
    touch();
    var hasAck = typeof ack === 'number' && isFinite(ack) && ack >= 0 && Math.floor(ack) === ack;
    var stale = hasAck && ack < seq;
    if (!stale) {
      reported = player.normalize(raw);
      reportedAt = now();
      optimistic = null;
    }
    // ack > seq : le service a redémarré depuis ; resync dit à l'app de se recaler sur le seq rendu (rien n'est rejoué)
    return { seq: seq, resync: hasAck && ack > seq, commands: hasAck && ack <= seq ? since(ack) : [] };
  }

  return {
    isAlive: isAlive,
    touch: touch,
    snapshot: snapshot,
    subscribe: subscribe,
    send: send,
    report: report,
    since: since,
    seq: function () { return seq; },
    subscribers: function () { return Object.keys(sinks).length; }
  };
}

module.exports = { createBridge: createBridge };
