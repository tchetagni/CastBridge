'use strict';
// Répondeur mDNS (entrée/sortie) : socket UDP 5353 en multicast 224.0.0.251. La logique des paquets est dans mdns.js.
// Annonce au démarrage, périodiquement, à chaque changement d'adresse, répond aux requêtes, dit « au revoir » à l'arrêt.

var dgram = require('dgram');
var os = require('os');
var mdns = require('./mdns');
var netutil = require('./netutil');

/**
 * opts : { svc: { instance, type, host, port, txt }, address (IPv4 imposée, facultative), interfaces (() -> os.networkInterfaces()), log }.
 * Rien ne casse si le réseau manque : sans interface IPv4 ou sans le port 5353, le répondeur le dit et réessaie, le HTTP continue.
 */
function create(opts) {
  var svc = opts.svc;
  var log = opts.log || function () {};
  var sock = null;
  var timers = [];
  var joined = {};
  var running = false;
  var lastError = null;
  var replies = { second: 0, count: 0 };

  function ifaces() {
    try { return opts.interfaces ? opts.interfaces() : os.networkInterfaces(); } catch (e) { return {}; }
  }

  function allAddresses() { return opts.address ? [opts.address] : netutil.pickAddresses(ifaces()); }

  function addressesFor(peer) { return opts.address ? [opts.address] : netutil.pickAddresses(ifaces(), peer); }

  function send(buf, port, address, viaIp) {
    if (!sock) return;
    try { if (viaIp && typeof sock.setMulticastInterface === 'function') sock.setMulticastInterface(viaIp); } catch (e) { /* interface partie */ }
    sock.send(buf, 0, buf.length, port, address, function (err) { if (err) log('mDNS : envoi vers ' + address + ' : ' + err.message); });
  }

  /** Rejoint le groupe sur chaque adresse utilisable, quitte celles qui ont disparu. Retourne vrai si l'ensemble a changé. */
  function syncMemberships() {
    var addrs = allAddresses();
    var changed = false;
    addrs.forEach(function (a) {
      if (joined[a]) return;
      try { sock.addMembership(mdns.ADDRESS, a); joined[a] = true; changed = true; }
      catch (e) {
        try { sock.addMembership(mdns.ADDRESS); joined[a] = true; changed = true; }      // adresse imposée qui n'est pas celle d'une interface
        catch (e2) { log('mDNS : adhésion au groupe impossible sur ' + a + ' : ' + e2.message); }
      }
    });
    Object.keys(joined).forEach(function (a) {
      if (addrs.indexOf(a) >= 0) return;
      try { sock.dropMembership(mdns.ADDRESS, a); } catch (e) { /* interface déjà partie */ }
      delete joined[a];
      changed = true;
    });
    return changed;
  }

  function announce(goodbye) {
    Object.keys(joined).forEach(function (a) {
      try { send(mdns.announcement(svc, [a], goodbye), mdns.PORT, mdns.ADDRESS, a); }
      catch (e) { log('mDNS : annonce : ' + e.message); }
    });
  }

  /** Trois annonces espacées (RFC 6762 § 8.3 en demande au moins deux), puis une toutes les minutes. */
  function announceBurst() {
    announce(false);
    timers.push(setTimeout(function () { announce(false); }, 1000));
    timers.push(setTimeout(function () { announce(false); }, 3000));
  }

  function underLimit() {
    var s = Math.floor(Date.now() / 1000);
    if (replies.second !== s) { replies.second = s; replies.count = 0; }
    return ++replies.count <= 30;           // garde-fou : au plus 30 réponses par seconde
  }

  function onMessage(buf, rinfo) {
    var query = mdns.parseMessage(buf);
    if (!query || query.response) return;
    var addrs = addressesFor(rinfo.address);
    var out = mdns.respond(query, svc, addrs);
    if (!out || !underLimit()) return;
    try {
      if (rinfo.port !== mdns.PORT) {
        send(mdns.legacyResponse(query, out), rinfo.port, rinfo.address);      // outil de diagnostic (dig, dns-sd ancien…)
      } else {
        send(mdns.multicastResponse(out), mdns.PORT, mdns.ADDRESS, addrs[0]);
        if (out.unicast) send(mdns.multicastResponse(out), rinfo.port, rinfo.address);      // QU : aussi en direct au demandeur
      }
    } catch (e) {
      log('mDNS : réponse : ' + e.message);
    }
  }

  /** Toutes les 20 s : réadhésion si le Wi-Fi ou le câble a changé d'adresse, et nouvelle annonce dans ce cas. */
  function watchNetwork() {
    if (!sock) return;
    if (syncMemberships()) { log('mDNS : adresses ' + (Object.keys(joined).join(', ') || '(aucune)')); announceBurst(); }
  }

  function start() {
    if (sock) return;
    sock = dgram.createSocket({ type: 'udp4', reuseAddr: true });     // reuseAddr : partager le port 5353 avec le démon mDNS du système
    sock.on('error', function (e) {
      lastError = e.message;
      log('mDNS : ' + e.message + ' (l\'annonce est inactive ; l\'API HTTP continue)');
      running = false;
    });
    sock.on('message', onMessage);
    sock.on('listening', function () {
      running = true;
      try { sock.setMulticastTTL(255); } catch (e) { /* facultatif */ }
      try { sock.setMulticastLoopback(true); } catch (e) { /* facultatif */ }
      syncMemberships();
      log('mDNS : annonce de « ' + svc.instance + ' » (' + (Object.keys(joined).join(', ') || 'aucune adresse pour l\'instant') + ')');
      announceBurst();
      timers.push(setInterval(watchNetwork, 20000));
      timers.push(setInterval(function () { announce(false); }, 60000));      // annonce périodique
    });
    try { sock.bind(mdns.PORT); }
    catch (e) { lastError = e.message; log('mDNS : bind impossible : ' + e.message); }
  }

  function stop(done) {
    timers.forEach(function (t) { clearTimeout(t); clearInterval(t); });
    timers = [];
    if (!sock) { if (done) done(); return; }
    var s = sock;
    try { announce(true); } catch (e) { /* au revoir best effort */ }
    setTimeout(function () {
      try { s.close(); } catch (e) { /* déjà fermé */ }
      if (done) done();
    }, 150);
    sock = null;
    running = false;
    joined = {};
  }

  return {
    start: start,
    stop: stop,
    announce: function () { announce(false); },
    status: function () { return { running: running, groups: Object.keys(joined), error: lastError }; }
  };
}

module.exports = { create: create };
