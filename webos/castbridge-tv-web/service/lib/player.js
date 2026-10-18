'use strict';
// État du lecteur tel que le téléphone le lit dans GET /api/info (player.state, name, pos, dur ; pos et dur en MILLISECONDES,
// comme TvClient.seek(posMs) et TvInfo.pos/dur). Module pur.

// idle, buffering, playing, paused, ended, error : le vocabulaire que CastSession.poll() du téléphone comprend.
var STATES = ['idle', 'buffering', 'playing', 'paused', 'ended', 'error'];

function idle() { return { state: 'idle', name: null, pos: 0, dur: 0 }; }

function toMs(n) {
  n = Number(n);
  return isFinite(n) && n > 0 ? Math.round(n) : 0;
}

/** Rend un état rapporté par l'app sûr et complet : état connu, durées en entiers >= 0, pos <= dur si dur est connue. */
function normalize(raw) {
  raw = raw || {};
  var state = STATES.indexOf(raw.state) >= 0 ? raw.state : 'idle';
  if (state === 'idle') return idle();
  var name = typeof raw.name === 'string' && raw.name !== '' ? raw.name.slice(0, 200) : null;
  var dur = toMs(raw.dur);
  var pos = toMs(raw.pos);
  if (dur > 0 && pos > dur) pos = dur;
  return { state: state, name: name, pos: pos, dur: dur };
}

function copy(p, changes) {
  var out = { state: p.state, name: p.name, pos: p.pos, dur: p.dur };
  Object.keys(changes).forEach(function (k) { out[k] = changes[k]; });
  return out;
}

/**
 * L'état que le téléphone doit voir tout de suite après une commande, avant que l'app ne confirme (la CastBridge-TV
 * Android répond « ok(info()) » avec l'état déjà changé). cmd : { cmd: 'play'|'pause'|'resume'|'stop'|'seek', title, pos }.
 */
function applyCommand(p, cmd) {
  switch (cmd.cmd) {
    case 'play': return { state: 'buffering', name: cmd.title, pos: toMs(cmd.pos), dur: 0 };
    case 'pause': return p.state === 'playing' || p.state === 'buffering' ? copy(p, { state: 'paused' }) : p;
    case 'resume': return p.state === 'paused' ? copy(p, { state: 'playing' }) : p;
    case 'stop': return idle();
    case 'seek':
      if (p.state === 'idle') return p;
      var pos = toMs(cmd.pos);
      if (p.dur > 0 && pos > p.dur) pos = p.dur;
      return copy(p, { pos: pos });
    default: return p;
  }
}

/** Position estimée elapsedMs après le dernier rapport : seulement en lecture (pas en pause, pas en chargement). */
function extrapolate(p, elapsedMs) {
  if (p.state !== 'playing' || !(elapsedMs > 0)) return p;
  var pos = p.pos + Math.round(elapsedMs);
  if (p.dur > 0 && pos > p.dur) pos = p.dur;
  return copy(p, { pos: pos });
}

/** Le JSON de GET /api/info : exactement les clés que TvInfo.parse lit (files, free, used, quota, player{state,name,pos,dur}). */
function info(p) {
  return { files: [], free: 0, used: 0, quota: 0, player: { state: p.state, name: p.name, pos: p.pos, dur: p.dur } };
}

function infoJson(p) { return JSON.stringify(info(p)); }

module.exports = {
  STATES: STATES, idle: idle, normalize: normalize, applyCommand: applyCommand, extrapolate: extrapolate, info: info, infoJson: infoJson
};
