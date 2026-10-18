'use strict';
// Lecteur simulé du mode --standalone : tient la place de l'app de la TV pour tester l'API HTTP avec curl ou avec le vrai
// téléphone CastBridge sur un Mac ou un PC. Il reçoit les mêmes commandes que l'app (bridge.subscribe) et rapporte son état
// comme elle (bridge.report). Pur : l'horloge est injectée.

var player = require('./player');

/** opts : { now, bufferMs } ; bufferMs = délai « Mise en mémoire tampon » avant de passer en lecture (400 ms par défaut). */
function create(opts) {
  opts = opts || {};
  var now = opts.now || Date.now;
  var bufferMs = opts.bufferMs === undefined ? 400 : opts.bufferMs;
  var state = 'idle';
  var name = null;
  var base = 0;        // position (ms) à l'instant t0
  var t0 = 0;
  var readyAt = 0;
  var lastSeq = -1;

  function current() {
    if (state === 'buffering' && now() >= readyAt) { state = 'playing'; t0 = readyAt; }
    var pos = state === 'playing' ? base + (now() - t0) : base;
    // dur 0 = durée inconnue (comme un flux en direct) : le téléphone garde alors la durée qu'il connaît
    return player.normalize({ state: state, name: name, pos: pos, dur: 0 });
  }

  function apply(cmd) {
    var cur = current();
    if (typeof cmd.seq === 'number') lastSeq = cmd.seq;
    switch (cmd.cmd) {
      case 'play':
        name = cmd.title; base = Math.max(0, cmd.pos || 0); state = 'buffering'; readyAt = now() + bufferMs;
        break;
      case 'pause':
        if (state === 'playing' || state === 'buffering') { base = cur.pos; state = 'paused'; }
        break;
      case 'resume':
        if (state === 'paused') { state = 'playing'; t0 = now(); }
        break;
      case 'seek':
        if (state !== 'idle') { base = Math.max(0, cmd.pos || 0); t0 = now(); }
        break;
      case 'stop':
        state = 'idle'; name = null; base = 0;
        break;
      default:
        break;
    }
  }

  return { apply: apply, current: current, ack: function () { return lastSeq; } };
}

module.exports = { create: create };
