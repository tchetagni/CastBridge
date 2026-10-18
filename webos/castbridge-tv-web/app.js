/* CastBridge-TV Web 0.1.0-beta : application webOS (JavaScript ES5, sans dépendance).
 *
 * L'application affiche l'accueil et le lecteur plein écran. Le service (service/service.js) parle au téléphone CastBridge
 * (annonce mDNS + API HTTP 8765) et lui rend l'état du lecteur. Pont app <-> service : appels Luna
 *   events (abonnement)  : le service y pousse les commandes du téléphone (play, pause, resume, stop, seek) ;
 *   state                : l'app y pousse l'état du lecteur chaque seconde ({ state, name, pos, dur, ack }, pos et dur en ms)
 *                          et reçoit en réponse les commandes qu'elle n'a pas encore appliquées (filet si l'abonnement saute) ;
 *   status               : diagnostic du service (adresses, nom, port).
 * Hors TV (navigateur), l'écran s'affiche sans Luna ; « index.html?demo=<lien> » lit directement un lien pour essayer le lecteur.
 */
(function (root, factory) {
  var api = factory(root);
  if (typeof module === 'object' && module && module.exports) {
    module.exports = api;                       // tests Node : seules les aides pures sont utilisées
  } else {
    root.CastBridgeTvWeb = api;
    api.start();
  }
}(typeof window !== 'undefined' ? window : this, function (globalRoot) {
  'use strict';

  var VERSION = '0.1.0-beta';
  var APP_NAME = 'CastBridge-TV Web';
  var SERVICE_URI = 'luna://com.sticm.castbridge.tv.service';
  var DEFAULT_TITLE = 'Depuis le téléphone';
  var HEARTBEAT_MS = 1000;
  var SEEK_MS = 10000;
  var SEEK_LONG_MS = 30000;
  var OVERLAY_MS = 4000;
  var ENDED_MS = 6000;
  var ERROR_MS = 8000;
  var HAVE_FUTURE_DATA = 3;

  // Codes de touches webOS (Retour = 461) et de navigateur (Échap, Retour arrière) pour essayer sur un ordinateur.
  var KEY = { BACKSPACE: 8, ENTER: 13, PAUSE: 19, ESC: 27, LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, STOP: 413, REWIND: 412, PLAY: 415, FORWARD: 417, BACK: 461 };

  var STATE_LABEL = { idle: '', buffering: 'Chargement…', playing: 'Lecture', paused: 'Pause', ended: 'Lecture terminée', error: 'Lecture impossible' };

  // ------------------------------------------------------------------------------------------ aides pures (testées sous Node)

  /** 3725000 -> « 1:02:05 », 65000 -> « 1:05 ». */
  function formatTime(ms) {
    var s = Math.max(0, Math.floor((Number(ms) || 0) / 1000));
    var h = Math.floor(s / 3600);
    var m = Math.floor((s % 3600) / 60);
    var r = s % 60;
    function two(n) { return (n < 10 ? '0' : '') + n; }
    return h > 0 ? h + ':' + two(m) + ':' + two(r) : m + ':' + two(r);
  }

  function progressPercent(pos, dur) {
    if (!(dur > 0)) return 0;
    return Math.max(0, Math.min(100, Math.round((pos / dur) * 1000) / 10));
  }

  /**
   * Action d'une touche de télécommande. screen : 'home' ou 'player'. Retour quitte l'accueil et arrête la lecture ailleurs.
   * Retourne null si la touche n'est pas utilisée (le système la traite alors).
   */
  function keyAction(code, screen) {
    if (code === KEY.BACK || code === KEY.ESC || code === KEY.BACKSPACE) return screen === 'player' ? 'stop' : 'exit';
    if (screen === 'home') {
      if (code === KEY.LEFT || code === KEY.UP) return 'focusPrev';
      if (code === KEY.RIGHT || code === KEY.DOWN) return 'focusNext';
      if (code === KEY.ENTER) return 'activate';
      return null;
    }
    switch (code) {
      case KEY.ENTER: return 'toggle';
      case KEY.PLAY: return 'resume';
      case KEY.PAUSE: return 'pause';
      case KEY.STOP: return 'stop';
      case KEY.LEFT: return 'seekBack';
      case KEY.RIGHT: return 'seekForward';
      case KEY.REWIND: return 'seekBackLong';
      case KEY.FORWARD: return 'seekForwardLong';
      case KEY.UP:
      case KEY.DOWN: return 'overlay';
      default: return null;
    }
  }

  /** Adresse IPv4 de la TV d'après la réponse de luna://com.webos.service.connectionmanager/getStatus (câble d'abord, puis Wi-Fi). */
  function pickIp(status) {
    if (!status) return null;
    var nets = [status.wired, status.wifi];
    for (var i = 0; i < nets.length; i++) {
      var n = nets[i];
      if (n && n.state === 'connected' && /^\d{1,3}(\.\d{1,3}){3}$/.test(n.ipAddress || '')) return n.ipAddress;
    }
    return null;
  }

  /**
   * État à rapporter au téléphone d'après l'élément <video> : m = { hasSrc, error, ended, paused, readyState }.
   * Le vocabulaire est celui que CastSession.poll() du téléphone comprend : idle, buffering, playing, paused, ended, error.
   */
  function stateFrom(m) {
    if (!m.hasSrc) return 'idle';
    if (m.error) return 'error';
    if (m.ended) return 'ended';
    if (m.paused) return 'paused';
    return m.readyState >= HAVE_FUTURE_DATA ? 'playing' : 'buffering';
  }

  function isPlayableUrl(url) {
    return typeof url === 'string' && url.length <= 4096 && /^https?:\/\/[^\s]+$/i.test(url);
  }

  /** Commande reçue du service -> commande sûre, ou null (inconnue ou lien non http(s)). */
  function normalizeCommand(c) {
    if (!c || typeof c.cmd !== 'string') return null;
    var out = { cmd: c.cmd, seq: typeof c.seq === 'number' ? c.seq : null };
    if (c.cmd === 'play') {
      if (!isPlayableUrl(c.url)) return null;
      out.url = c.url;
      out.title = typeof c.title === 'string' && c.title !== '' ? c.title : DEFAULT_TITLE;
      out.pos = Math.max(0, Number(c.pos) || 0);
    } else if (c.cmd === 'seek') {
      out.pos = Math.max(0, Number(c.pos) || 0);
    } else if (c.cmd !== 'pause' && c.cmd !== 'resume' && c.cmd !== 'stop') {
      return null;
    }
    return out;
  }

  /** Une commande numérotée n'est appliquée qu'une fois (elle arrive par l'abonnement ET dans la réponse de state). */
  function isNewCommand(ack, seq) {
    return typeof seq !== 'number' || seq > ack;
  }

  /** Recale le numéro de commande : app neuve (ack < 0) ou service redémarré (resync) -> on prend celui du service. */
  function adoptSeq(ack, res) {
    if (!res || typeof res.seq !== 'number') return ack;
    return ack < 0 || res.resync === true ? res.seq : ack;
  }

  /** Texte du toast webOS (le toast accepte du HTML : on retire < > &). */
  function toastText(title) {
    var t = String(title || DEFAULT_TITLE).replace(/[<>&]/g, ' ').replace(/\s+/g, ' ').replace(/^ | $/g, '');
    if (t.length > 80) t = t.slice(0, 77) + '…';
    return 'Lecture : ' + t;
  }

  function describeMediaError(code) {
    switch (code) {
      case 1: return 'Lecture interrompue.';
      case 2: return 'La TV n\'atteint pas le lien : le téléphone doit rester allumé, sur le même Wi-Fi.';
      case 3: return 'Vidéo illisible (fichier abîmé ou mal décodé).';
      case 4: return 'Format non pris en charge par cette TV, ou lien injoignable (le téléphone doit rester allumé, sur le même Wi-Fi).';
      default: return 'Lecture impossible.';
    }
  }

  /**
   * Appel Luna : webOS.service.request (webOSTV.js) s'il est présent, sinon PalmServiceBridge, sinon rien (navigateur).
   * Retourne call(uri, method, params, cb, subscribe) -> { cancel() } ; cb(err, res).
   */
  function makeLuna(env) {
    return function call(uri, method, params, cb, subscribe) {
      function done(err, res) { try { cb(err, res); } catch (e) { if (env.console && env.console.log) env.console.log('callback Luna : ' + e); } }
      if (env.webOS && env.webOS.service && typeof env.webOS.service.request === 'function') {
        var req = env.webOS.service.request(uri, {
          method: method,
          parameters: params || {},
          subscribe: !!subscribe,
          resubscribe: false,
          onSuccess: function (res) { done(null, res); },
          onFailure: function (err) { done(err || { errorText: 'échec Luna' }, null); }
        });
        return { cancel: function () { try { if (req && typeof req.cancel === 'function') req.cancel(); } catch (e) { /* déjà annulé */ } } };
      }
      if (typeof env.PalmServiceBridge === 'function') {
        var bridge = new env.PalmServiceBridge();
        bridge.onservicecallback = function (msg) {
          var res;
          try { res = JSON.parse(msg); } catch (e) { res = { returnValue: false, errorText: 'réponse illisible' }; }
          if (res && res.returnValue === false) done(res, null); else done(null, res);
        };
        var payload = {};
        var src = params || {};
        for (var k in src) { if (Object.prototype.hasOwnProperty.call(src, k)) payload[k] = src[k]; }
        if (subscribe) payload.subscribe = true;
        bridge.call(uri + '/' + method, JSON.stringify(payload));
        return { cancel: function () { try { bridge.cancel(); } catch (e) { /* déjà annulé */ } } };
      }
      done({ errorText: 'Luna indisponible (hors TV)', unavailable: true }, null);
      return { cancel: function () {} };
    };
  }

  // ------------------------------------------------------------------------------------------ l'application (navigateur)

  /** env : l'objet « window » (essais : un faux window). */
  function start(env) {
    var root = env || globalRoot;
    var doc = root.document;
    if (!doc) return;
    function $(id) { return doc.getElementById(id); }
    var luna = makeLuna(root);
    var lunaAvailable = !!(root.webOS && root.webOS.service && root.webOS.service.request) || typeof root.PalmServiceBridge === 'function';
    var ui = {
      home: $('home'), player: $('player'), video: $('video'), tvName: $('tv-name'), version: $('version'), address: $('address'),
      statusText: $('status-text'), dot: $('dot'), help: $('help'), serviceLine: $('service-line'), btnHelp: $('btn-help'), btnExit: $('btn-exit'),
      overlay: $('overlay'), oTitle: $('overlay-title'), oState: $('overlay-state'), barFill: $('bar-fill'), oTime: $('overlay-time'), message: $('message')
    };
    var buttons = [ui.btnHelp, ui.btnExit];
    var S = {
      screen: 'home', ack: -1, serviceOk: null, ip: null, port: 8765, name: APP_NAME, current: null, errorText: null, ended: false,
      pendingSeek: null, lastState: 'idle', focus: 0, overlayUntil: 0, endTimer: 0, overlayTimer: 0, resubTimer: 0, events: null
    };

    function hasClass(el, c) { return (' ' + el.className + ' ').indexOf(' ' + c + ' ') >= 0; }
    function setClass(el, c, on) {
      var has = hasClass(el, c);
      if (on && !has) el.className = (el.className ? el.className + ' ' : '') + c;
      else if (!on && has) el.className = (' ' + el.className + ' ').replace(' ' + c + ' ', ' ').replace(/^\s+|\s+$/g, '');
    }

    // ---- accueil
    function refreshHome() {
      ui.tvName.textContent = S.name;
      ui.version.textContent = 'version ' + VERSION;
      ui.address.textContent = S.ip ? S.ip + '  ·  port ' + S.port : 'adresse non disponible';
      var text;
      var cls;
      if (S.serviceOk === true) { text = 'En attente du téléphone CastBridge…'; cls = 'ok'; }
      else if (S.serviceOk === false) { text = 'Service indisponible : fermez puis rouvrez l\'application.'; cls = 'ko'; }
      else { text = lunaAvailable ? 'Démarrage du service…' : 'Mode navigateur (hors TV) : pas de service, le téléphone ne peut pas se connecter.'; cls = ''; }
      ui.statusText.textContent = text;
      ui.dot.className = 'dot' + (cls ? ' ' + cls : '');
      ui.serviceLine.textContent = S.serviceOk === true ? 'Service actif · API HTTP ' + (S.ip || '') + ':' + S.port : '';
    }

    function applyFocus() {
      for (var i = 0; i < buttons.length; i++) setClass(buttons[i], 'focused', i === S.focus && S.screen === 'home');
      if (S.screen === 'home' && buttons[S.focus] && buttons[S.focus].focus) buttons[S.focus].focus();
    }

    function showScreen(name) {
      S.screen = name;
      setClass(ui.home, 'hidden', name !== 'home');
      setClass(ui.player, 'hidden', name !== 'player');
      applyFocus();
    }

    function setService(ok) {
      if (S.serviceOk === ok) return;
      S.serviceOk = ok;
      refreshHome();
    }

    // ---- lecteur : état mesuré, aperçu, rapport au service
    function measure() {
      var v = ui.video;
      var st = stateFrom({ hasSrc: !!S.current, error: !!S.errorText, ended: S.ended || v.ended === true, paused: v.paused, readyState: v.readyState });
      if (st === 'idle') return { state: 'idle', name: null, pos: 0, dur: 0 };
      var dur = isFinite(v.duration) ? Math.round(v.duration * 1000) : 0;
      var pos = Math.round((v.currentTime || 0) * 1000);
      if (S.pendingSeek !== null && pos === 0) pos = S.pendingSeek;        // la position demandée n'est pas encore appliquée
      return { state: st, name: S.current.title, pos: pos, dur: dur > 0 ? dur : 0 };
    }

    function refreshOverlay() {
      var m = measure();
      ui.oState.textContent = STATE_LABEL[m.state] || '';
      ui.oTime.textContent = formatTime(m.pos) + (m.dur > 0 ? ' / ' + formatTime(m.dur) : '');
      ui.barFill.style.width = progressPercent(m.pos, m.dur) + '%';
      var pinned = m.state === 'paused' || m.state === 'buffering' || m.state === 'error' || m.state === 'ended';
      setClass(ui.overlay, 'visible', pinned || S.overlayUntil > new Date().getTime());
    }

    function showOverlay(ms) {
      S.overlayUntil = new Date().getTime() + ms;
      refreshOverlay();
      root.clearTimeout(S.overlayTimer);
      S.overlayTimer = root.setTimeout(refreshOverlay, ms + 50);
    }

    function showMessage(text) { ui.message.textContent = text; setClass(ui.message, 'hidden', false); }
    function hideMessage() { setClass(ui.message, 'hidden', true); }

    function report() {
      if (!lunaAvailable) return;
      var m = measure();
      S.lastState = m.state;
      luna(SERVICE_URI, 'state', { state: m.state, name: m.name, pos: m.pos, dur: m.dur, ack: S.ack }, function (err, res) {
        if (err || !res) { setService(false); return; }
        setService(true);
        S.ack = adoptSeq(S.ack, res);
        var cmds = res.commands || [];
        for (var i = 0; i < cmds.length; i++) onCommand(cmds[i]);
      });
    }

    function toast(text) {
      if (!lunaAvailable) return;
      luna('luna://com.webos.notification', 'createToast', { message: text }, function () { /* le toast est facultatif */ });
    }

    // ---- lecteur : actions
    function clearEndTimer() { root.clearTimeout(S.endTimer); S.endTimer = 0; }

    function playCommand(c) {
      clearEndTimer();
      var v = ui.video;
      S.current = { url: c.url, title: c.title };
      S.errorText = null;
      S.ended = false;
      S.pendingSeek = c.pos > 0 ? c.pos : null;
      ui.oTitle.textContent = c.title;
      hideMessage();
      showScreen('player');
      v.src = c.url;
      try { v.load(); } catch (e) { /* le chargement démarre de toute façon avec src */ }
      var p = null;
      try { p = v.play(); } catch (e2) { p = null; }
      if (p && typeof p.catch === 'function') p.catch(function () { /* l'événement error le dira */ });
      toast(toastText(c.title));
    }

    function stopPlayback() {
      clearEndTimer();
      var v = ui.video;
      S.current = null;
      S.errorText = null;
      S.ended = false;
      S.pendingSeek = null;
      try { v.pause(); } catch (e) { /* rien à arrêter */ }
      try { v.removeAttribute('src'); v.load(); } catch (e2) { /* rien à vider */ }
      hideMessage();
      showScreen('home');
    }

    function resumePlayback() {
      if (!S.current || S.ended || S.errorText) return;
      var p = null;
      try { p = ui.video.play(); } catch (e) { p = null; }
      if (p && typeof p.catch === 'function') p.catch(function () { /* ignoré */ });
    }

    function seekTo(ms) {
      if (!S.current) return;
      var v = ui.video;
      var target = Math.max(0, ms);
      if (isFinite(v.duration) && v.duration > 0) target = Math.min(target, Math.floor(v.duration * 1000));
      if (v.readyState >= 1) {
        try { v.currentTime = target / 1000; } catch (e) { /* position refusée : on garde la courante */ }
        S.pendingSeek = null;
      } else {
        S.pendingSeek = target;
      }
    }

    function seekBy(deltaMs) { seekTo(measure().pos + deltaMs); }

    function onCommand(raw) {
      var seq = raw && typeof raw.seq === 'number' ? raw.seq : null;
      if (!isNewCommand(S.ack, seq)) return;
      if (seq !== null) S.ack = seq;
      var c = normalizeCommand(raw);
      if (!c) return;
      switch (c.cmd) {
        case 'play': playCommand(c); break;
        case 'pause': ui.video.pause(); break;
        case 'resume': resumePlayback(); break;
        case 'seek': seekTo(c.pos); break;
        case 'stop': stopPlayback(); break;
        default: break;
      }
      if (S.screen === 'player') showOverlay(OVERLAY_MS);
      report();                                  // l'ack à jour confirme aussitôt la commande au service
    }

    function onMedia(ev) {
      if (!S.current) return;                    // événements du nettoyage (src retirée) : ignorés
      var v = ui.video;
      if (ev.type === 'loadedmetadata' && S.pendingSeek !== null) {
        try { v.currentTime = S.pendingSeek / 1000; } catch (e) { /* ignoré */ }
        S.pendingSeek = null;
      }
      if (ev.type === 'ended') {
        S.ended = true;
        showMessage('Lecture terminée');
        clearEndTimer();
        S.endTimer = root.setTimeout(function () { stopPlayback(); report(); }, ENDED_MS);
      }
      if (ev.type === 'error') {
        S.errorText = describeMediaError(v.error && v.error.code);
        showMessage(S.errorText);
        clearEndTimer();
        S.endTimer = root.setTimeout(function () { stopPlayback(); report(); }, ERROR_MS);
      }
      refreshOverlay();
      if (ev.type !== 'timeupdate' && measure().state !== S.lastState) report();
    }

    // ---- service et réseau
    function subscribeEvents() {
      if (!lunaAvailable) return;
      if (S.events) S.events.cancel();
      S.events = luna(SERVICE_URI, 'events', {}, function (err, res) {
        if (err || !res) { setService(false); scheduleResubscribe(); return; }
        setService(true);
        if (res.event === 'hello') {
          S.ack = adoptSeq(S.ack, res);
          if (typeof res.name === 'string' && res.name) S.name = res.name;
          if (typeof res.port === 'number') S.port = res.port;
          refreshHome();
        } else if (res.event === 'command') {
          onCommand(res);
        }
      }, true);
    }

    function scheduleResubscribe() {
      if (S.resubTimer) return;
      S.resubTimer = root.setTimeout(function () { S.resubTimer = 0; subscribeEvents(); }, 3000);     // rappeler Luna relance aussi le service s'il s'est arrêté
    }

    function watchNetwork() {
      if (!lunaAvailable) return;
      luna('luna://com.webos.service.connectionmanager', 'getStatus', {}, function (err, res) {
        if (err || !res) return;
        var ip = pickIp(res);
        if (ip !== S.ip) { S.ip = ip; refreshHome(); }
      }, true);
    }

    function askStatus() {
      luna(SERVICE_URI, 'status', {}, function (err, res) {
        if (err || !res) return;
        if (!S.ip && res.addresses && res.addresses.length) S.ip = res.addresses[0];
        if (typeof res.name === 'string' && res.name) S.name = res.name;
        if (typeof res.port === 'number') S.port = res.port;
        refreshHome();
      });
    }

    function exitApp() {
      try { if (root.webOS && typeof root.webOS.platformBack === 'function') { root.webOS.platformBack(); return; } } catch (e) { /* essai suivant */ }
      try { if (root.webOSSystem && typeof root.webOSSystem.platformBack === 'function') { root.webOSSystem.platformBack(); return; } } catch (e2) { /* essai suivant */ }
      try { root.close(); } catch (e3) { /* navigateur : rien à fermer */ }
    }

    // ---- télécommande
    function moveFocus(delta) {
      S.focus = (S.focus + delta + buttons.length) % buttons.length;
      applyFocus();
    }

    function toggleHelp() { setClass(ui.help, 'hidden', !hasClass(ui.help, 'hidden')); }

    doc.addEventListener('keydown', function (e) {
      var action = keyAction(e.keyCode || e.which, S.screen);
      if (!action) return;
      e.preventDefault();
      switch (action) {
        case 'exit': exitApp(); break;
        case 'stop': stopPlayback(); report(); break;
        case 'toggle': if (ui.video.paused) resumePlayback(); else ui.video.pause(); break;
        case 'resume': resumePlayback(); break;
        case 'pause': ui.video.pause(); break;
        case 'seekBack': seekBy(-SEEK_MS); break;
        case 'seekForward': seekBy(SEEK_MS); break;
        case 'seekBackLong': seekBy(-SEEK_LONG_MS); break;
        case 'seekForwardLong': seekBy(SEEK_LONG_MS); break;
        case 'focusPrev': moveFocus(-1); break;
        case 'focusNext': moveFocus(1); break;
        case 'activate': if (buttons[S.focus]) buttons[S.focus].click(); break;
        default: break;
      }
      if (S.screen === 'player') { showOverlay(OVERLAY_MS); report(); }
    }, false);

    // Souris de la Magic Remote
    ui.btnHelp.addEventListener('click', toggleHelp, false);
    ui.btnExit.addEventListener('click', exitApp, false);

    var mediaEvents = ['loadstart', 'loadedmetadata', 'durationchange', 'canplay', 'playing', 'pause', 'waiting', 'seeking', 'seeked', 'ended', 'error', 'stalled', 'timeupdate'];
    for (var i = 0; i < mediaEvents.length; i++) ui.video.addEventListener(mediaEvents[i], onMedia, false);

    // Une autre application passe devant : la lecture se met en pause (le service rapporte « pause » au téléphone)
    doc.addEventListener('visibilitychange', function () { if (doc.hidden && S.current) ui.video.pause(); }, false);

    // ---- démarrage
    refreshHome();
    showScreen('home');
    subscribeEvents();
    watchNetwork();
    askStatus();
    root.setInterval(report, HEARTBEAT_MS);
    report();

    var demo = /[?&]demo=([^&]+)/.exec(root.location ? root.location.search : '');
    if (demo) {
      var link = decodeURIComponent(demo[1]);
      onCommand({ cmd: 'play', url: link, title: 'Démonstration', pos: 0 });
    }
  }

  return {
    VERSION: VERSION, KEY: KEY, formatTime: formatTime, progressPercent: progressPercent, keyAction: keyAction, pickIp: pickIp, stateFrom: stateFrom,
    isPlayableUrl: isPlayableUrl, normalizeCommand: normalizeCommand, isNewCommand: isNewCommand, adoptSeq: adoptSeq, toastText: toastText,
    describeMediaError: describeMediaError, makeLuna: makeLuna, start: start
  };
}));
