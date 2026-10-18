'use strict';
// Tests de l'app (app.js, index.html, app.css) : aides pures, appel Luna, cohérence avec le service. Chargé par run.js.

var assert = require('assert');
var fs = require('fs');
var path = require('path');
var run = require('./run.js');

var ROOT = run.ROOT;
var test = run.test;
var app = require(path.join(ROOT, 'app.js'));
var bridgeLib = require(path.join(ROOT, 'service', 'lib', 'bridge.js'));
var routes = require(path.join(ROOT, 'service', 'lib', 'routes.js'));

function read(rel) { return fs.readFileSync(path.join(ROOT, rel), 'utf8'); }

test('app : touches de la télécommande (Retour = 461, Entrée, flèches, touches média)', function () {
  assert.strictEqual(app.KEY.BACK, 461);
  assert.strictEqual(app.keyAction(461, 'home'), 'exit');
  assert.strictEqual(app.keyAction(461, 'player'), 'stop');
  assert.strictEqual(app.keyAction(27, 'player'), 'stop', 'Échap = Retour sur un ordinateur');
  assert.strictEqual(app.keyAction(13, 'home'), 'activate');
  assert.strictEqual(app.keyAction(37, 'home'), 'focusPrev');
  assert.strictEqual(app.keyAction(40, 'home'), 'focusNext');
  assert.strictEqual(app.keyAction(13, 'player'), 'toggle');
  assert.strictEqual(app.keyAction(37, 'player'), 'seekBack');
  assert.strictEqual(app.keyAction(39, 'player'), 'seekForward');
  assert.strictEqual(app.keyAction(415, 'player'), 'resume');
  assert.strictEqual(app.keyAction(19, 'player'), 'pause');
  assert.strictEqual(app.keyAction(413, 'player'), 'stop');
  assert.strictEqual(app.keyAction(412, 'player'), 'seekBackLong');
  assert.strictEqual(app.keyAction(417, 'player'), 'seekForwardLong');
  assert.strictEqual(app.keyAction(38, 'player'), 'overlay');
  assert.strictEqual(app.keyAction(65, 'player'), null, 'touche inutilisée : le système la traite');
  assert.strictEqual(app.keyAction(415, 'home'), null);
});

test('app : durées affichées et barre de progression', function () {
  assert.strictEqual(app.formatTime(0), '0:00');
  assert.strictEqual(app.formatTime(65000), '1:05');
  assert.strictEqual(app.formatTime(3725000), '1:02:05');
  assert.strictEqual(app.formatTime(-5), '0:00');
  assert.strictEqual(app.formatTime('x'), '0:00');
  assert.strictEqual(app.progressPercent(30000, 60000), 50);
  assert.strictEqual(app.progressPercent(90000, 60000), 100);
  assert.strictEqual(app.progressPercent(5, 0), 0);
});

test('app : adresse IP d\'après connectionmanager/getStatus (câble puis Wi-Fi)', function () {
  assert.strictEqual(app.pickIp({ wired: { state: 'connected', ipAddress: '192.168.1.10' }, wifi: { state: 'connected', ipAddress: '192.168.1.11' } }), '192.168.1.10');
  assert.strictEqual(app.pickIp({ wired: { state: 'disconnected' }, wifi: { state: 'connected', ipAddress: '192.168.1.11' } }), '192.168.1.11');
  assert.strictEqual(app.pickIp({ wired: { state: 'disconnected' }, wifi: { state: 'disconnected', ipAddress: '10.0.0.1' } }), null);
  assert.strictEqual(app.pickIp({ wifi: { state: 'connected', ipAddress: 'pas-une-ip' } }), null);
  assert.strictEqual(app.pickIp(null), null);
});

test('app : état rapporté d\'après le lecteur (vocabulaire de CastSession.poll)', function () {
  function s(m) { return app.stateFrom({ hasSrc: m.hasSrc !== false, error: !!m.error, ended: !!m.ended, paused: !!m.paused, readyState: m.readyState === undefined ? 4 : m.readyState }); }
  assert.strictEqual(s({ hasSrc: false }), 'idle');
  assert.strictEqual(s({ readyState: 0 }), 'buffering');
  assert.strictEqual(s({ readyState: 2 }), 'buffering');
  assert.strictEqual(s({ readyState: 3 }), 'playing');
  assert.strictEqual(s({ paused: true }), 'paused');
  assert.strictEqual(s({ paused: true, readyState: 0 }), 'paused');
  assert.strictEqual(s({ ended: true, paused: true }), 'ended');
  assert.strictEqual(s({ error: true, ended: true }), 'error');
});

test('app : commandes du service validées (lien http(s) seulement), doublons ignorés, recalage du numéro', function () {
  assert.strictEqual(app.isPlayableUrl('http://192.168.1.20:8089/media/a.mp4'), true);
  assert.strictEqual(app.isPlayableUrl('HTTPS://exemple.cm/x.mp4'), true);
  ['file:///etc/passwd', 'javascript:alert(1)', 'http://a b', '', null, 42, 'ftp://x'].forEach(function (u) { assert.strictEqual(app.isPlayableUrl(u), false, String(u)); });
  assert.deepStrictEqual(app.normalizeCommand({ cmd: 'play', url: 'http://h/x.mp4', title: 'Film', pos: 1500, seq: 4 }), { cmd: 'play', seq: 4, url: 'http://h/x.mp4', title: 'Film', pos: 1500 });
  assert.strictEqual(app.normalizeCommand({ cmd: 'play', url: 'file:///x', seq: 1 }), null);
  assert.strictEqual(app.normalizeCommand({ cmd: 'play', url: 'http://h/x', pos: -3 }).pos, 0);
  assert.strictEqual(app.normalizeCommand({ cmd: 'play', url: 'http://h/x' }).title, 'Depuis le téléphone');
  assert.strictEqual(app.normalizeCommand({ cmd: 'seek', pos: 9000 }).pos, 9000);
  assert.strictEqual(app.normalizeCommand({ cmd: 'danse' }), null);
  assert.strictEqual(app.normalizeCommand(null), null);
  assert.strictEqual(app.isNewCommand(3, 4), true);
  assert.strictEqual(app.isNewCommand(3, 3), false, 'déjà appliquée (abonnement puis réponse de state)');
  assert.strictEqual(app.isNewCommand(3, null), true);
  assert.strictEqual(app.adoptSeq(-1, { seq: 5 }), 5, 'app neuve : on prend le numéro du service');
  assert.strictEqual(app.adoptSeq(6, { seq: 5 }), 6, 'réponse en retard : le numéro n\'est pas ramené en arrière');
  assert.strictEqual(app.adoptSeq(57, { seq: 0, resync: true }), 0, 'service redémarré : recalage');
  assert.strictEqual(app.adoptSeq(2, {}), 2);
});

test('app : texte du toast (HTML retiré, longueur bornée) et messages d\'erreur du lecteur', function () {
  assert.strictEqual(app.toastText('Film.mp4'), 'Lecture : Film.mp4');
  assert.strictEqual(app.toastText('<b>x</b> & y'), 'Lecture : b x /b y');
  assert.strictEqual(app.toastText(''), 'Lecture : Depuis le téléphone');
  assert.ok(app.toastText(new Array(200).join('a')).length <= 'Lecture : '.length + 80);
  assert.ok(/Format/.test(app.describeMediaError(4)));
  assert.ok(/Wi-Fi/.test(app.describeMediaError(2)));
  assert.ok(app.describeMediaError(99).length > 0);
});

test('app : appel Luna par PalmServiceBridge (abonnement, JSON invalide, échec, annulation)', function () {
  function Fake() { Fake.last = this; this.calls = []; }
  Fake.prototype.call = function (uri, payload) { this.calls.push([uri, payload]); };
  Fake.prototype.cancel = function () { this.cancelled = true; };
  var luna = app.makeLuna({ PalmServiceBridge: Fake });
  var got = [];
  var handle = luna('luna://com.sticm.castbridge.tv.service', 'events', { a: 1 }, function (err, res) { got.push([err, res]); }, true);
  var b = Fake.last;
  assert.deepStrictEqual(b.calls[0], ['luna://com.sticm.castbridge.tv.service/events', '{"a":1,"subscribe":true}']);
  b.onservicecallback('{"returnValue":true,"event":"hello","seq":3}');
  b.onservicecallback('{"returnValue":false,"errorText":"refusé"}');
  b.onservicecallback('pas du json');
  assert.strictEqual(got[0][0], null);
  assert.strictEqual(got[0][1].seq, 3);
  assert.strictEqual(got[1][0].errorText, 'refusé');
  assert.strictEqual(got[2][0].returnValue, false);
  handle.cancel();
  assert.strictEqual(b.cancelled, true);
  luna('luna://svc', 'state', { x: 1 }, function () {}, false);
  assert.strictEqual(Fake.last.calls[0][1], '{"x":1}', 'sans abonnement, pas de « subscribe »');
});

test('app : webOS.service.request (webOSTV.js) est préféré à PalmServiceBridge ; sans Luna, une erreur claire', function () {
  var seen = null;
  var env = {
    webOS: { service: { request: function (uri, p) { seen = { uri: uri, p: p }; p.onSuccess({ returnValue: true, ok: 1 }); return { cancel: function () { seen.cancelled = true; } }; } } },
    PalmServiceBridge: function () { throw new Error('ne doit pas servir'); }
  };
  var out = [];
  var h = app.makeLuna(env)('luna://svc', 'state', { x: 1 }, function (err, res) { out.push([err, res]); }, false);
  assert.strictEqual(seen.uri, 'luna://svc');
  assert.strictEqual(seen.p.method, 'state');
  assert.deepStrictEqual(seen.p.parameters, { x: 1 });
  assert.strictEqual(seen.p.subscribe, false);
  assert.strictEqual(seen.p.resubscribe, false);
  assert.strictEqual(out[0][1].ok, 1);
  h.cancel();
  assert.strictEqual(seen.cancelled, true);
  var none = [];
  app.makeLuna({})('luna://svc', 'state', {}, function (err) { none.push(err); });
  assert.strictEqual(none[0].unavailable, true);
});

test('contrat app <-> service : chaque commande émise par les routes est comprise par l\'app', function () {
  var clock = run.fakeClock();
  var bridge = bridgeLib.createBridge({ now: clock.now });
  bridge.touch();
  var sent = [];
  bridge.subscribe(function (c) { sent.push(c); });
  function post(raw) { var t = routes.parseTarget(raw); return routes.handle({ method: 'POST', path: t.path, query: t.query, headers: {} }, { bridge: bridge }); }
  post('/api/playurl?url=http%3A%2F%2Fh%2Fx.mp4&title=Film&pos=2500');
  post('/api/pause');
  post('/api/resume');
  post('/api/seek?pos=7000');
  post('/api/stop');
  assert.deepStrictEqual(sent.map(function (c) { return c.cmd; }), ['play', 'pause', 'resume', 'seek', 'stop']);
  sent.forEach(function (c) {
    var luna = { returnValue: true, event: 'command' };      // la réponse Luna que service.js construit pour l'abonné
    Object.keys(c).forEach(function (k) { luna[k] = c[k]; });
    var n = app.normalizeCommand(luna);
    assert.ok(n, 'commande comprise : ' + c.cmd);
    assert.strictEqual(n.cmd, c.cmd);
    assert.strictEqual(n.seq, c.seq);
  });
  assert.strictEqual(app.normalizeCommand(sent[0]).url, 'http://h/x.mp4');
  assert.strictEqual(app.normalizeCommand(sent[3]).pos, 7000);
});

test('contrat app <-> service : méthodes Luna appelées par l\'app = celles de services.json ; ids de index.html présents', function () {
  var code = read('app.js');
  var svc = JSON.parse(read('service/services.json'));
  var declared = svc.services[0].commands.map(function (c) { return c.name; });
  var used = [];
  var re = /luna\(SERVICE_URI, '([a-z]+)'/g;
  var m;
  while ((m = re.exec(code))) if (used.indexOf(m[1]) < 0) used.push(m[1]);
  assert.deepStrictEqual(used.sort(), ['events', 'state', 'status']);
  used.forEach(function (name) { assert.ok(declared.indexOf(name) >= 0, 'méthode non déclarée : ' + name); });
  assert.ok(code.indexOf("'luna://" + svc.id + "'") >= 0, 'SERVICE_URI = luna://<id du service>');
  var html = read('index.html');
  var ids = [];
  var reId = /\$\('([a-z0-9-]+)'\)/g;
  while ((m = reId.exec(code))) ids.push(m[1]);
  assert.ok(ids.length >= 15, 'les éléments utilisés sont repérés');
  ids.forEach(function (id) { assert.ok(html.indexOf('id="' + id + '"') >= 0, 'id absent de index.html : ' + id); });
  assert.ok(/<script src="app\.js"><\/script>/.test(html));
  assert.ok(/<link rel="stylesheet" href="app\.css">/.test(html));
  assert.ok(/lang="fr"/.test(html));
});

/** Un faux DOM et un faux Luna, assez pour faire tourner start() : l'app complète sans navigateur ni TV. */
function fakeWindow() {
  var els = {};
  var docListeners = {};
  var timers = { intervals: [], timeouts: [] };
  var bridges = [];
  var lunaCalls = [];
  var stateReplies = [];            // réponses que le faux service rend aux appels « state », dans l'ordre
  function makeEl(id) {
    var listeners = {};
    var el = {
      id: id, className: '', textContent: '', style: {}, attrs: {},
      addEventListener: function (t, f) { (listeners[t] = listeners[t] || []).push(f); },
      fire: function (t, ev) { (listeners[t] || []).forEach(function (f) { f(ev || { type: t }); }); },
      focus: function () { el.focused = true; },
      click: function () { el.fire('click'); },
      removeAttribute: function (k) { if (k === 'src') el.src = undefined; },
      load: function () { el.loads = (el.loads || 0) + 1; },
      play: function () { el.paused = false; return null; },
      pause: function () { el.paused = true; el.fire('pause'); }
    };
    if (id === 'video') {
      el.paused = true; el.readyState = 0; el.currentTime = 0; el.duration = NaN; el.ended = false; el.error = null;
    }
    return el;
  }
  function FakeBridge() {
    bridges.push(this);
    this.call = function (uri, payload) {
      var method = uri.split('/').pop();
      var p = JSON.parse(payload);
      lunaCalls.push({ method: method, params: p, bridge: this });
      if (method === 'state') {
        var reply = stateReplies.length ? stateReplies.shift() : { returnValue: true, seq: 0, resync: false, commands: [] };
        this.onservicecallback(JSON.stringify(reply));
      } else if (method === 'status') {
        this.onservicecallback(JSON.stringify({ returnValue: true, name: 'CastBridge-TV Web', port: 8765, addresses: ['192.168.1.50'] }));
      } else if (method === 'getStatus') {
        this.onservicecallback(JSON.stringify({ returnValue: true, wifi: { state: 'connected', ipAddress: '192.168.1.50' }, wired: { state: 'disconnected' } }));
      }
    };
    this.cancel = function () { this.cancelled = true; };
  }
  var win = {
    document: {
      hidden: false,
      getElementById: function (id) { return els[id] || (els[id] = makeEl(id)); },
      addEventListener: function (t, f) { (docListeners[t] = docListeners[t] || []).push(f); }
    },
    setInterval: function (f) { timers.intervals.push(f); return timers.intervals.length; },
    clearInterval: function () {},
    setTimeout: function (f) { timers.timeouts.push(f); return timers.timeouts.length; },
    clearTimeout: function () {},
    location: { search: '' },
    PalmServiceBridge: FakeBridge,
    close: function () { win.closed = true; }
  };
  return {
    win: win, els: function (id) { return win.document.getElementById(id); }, timers: timers, lunaCalls: lunaCalls, bridges: bridges, stateReplies: stateReplies,
    key: function (code) { var ev = { keyCode: code, defaultPrevented: false, preventDefault: function () { ev.defaultPrevented = true; } }; (docListeners.keydown || []).forEach(function (f) { f(ev); }); return ev; },
    states: function () { return lunaCalls.filter(function (c) { return c.method === 'state'; }).map(function (c) { return c.params; }); },
    events: function () { return bridges.filter(function (b) { return b.calls === undefined; }).length; }
  };
}

test('app : démarrage complet (faux DOM, faux Luna) : accueil, commande play, états rapportés, Retour', function () {
  var fw = fakeWindow();
  app.start(fw.win);
  var home = fw.els('home');
  var player = fw.els('player');
  var video = fw.els('video');
  assert.ok(/castbridge-tv-web|CastBridge-TV Web/i.test(fw.els('tv-name').textContent));
  assert.strictEqual(fw.els('address').textContent, '192.168.1.50  ·  port 8765', 'adresse lue sur connectionmanager');
  assert.strictEqual(fw.els('status-text').textContent, 'En attente du téléphone CastBridge…');
  assert.ok(/hidden/.test(player.className) && !/hidden/.test(home.className));
  assert.ok(fw.lunaCalls.some(function (c) { return c.method === 'events' && c.params.subscribe === true; }), 'abonnement aux commandes');
  var sub = fw.lunaCalls.filter(function (c) { return c.method === 'events'; })[0].bridge;
  sub.onservicecallback(JSON.stringify({ returnValue: true, event: 'hello', subscribed: true, seq: 4, name: 'CastBridge-TV Web', version: '0.1.0-beta', port: 8765 }));
  // commande du téléphone : lire un lien à partir de 2 s
  sub.onservicecallback(JSON.stringify({ returnValue: true, event: 'command', cmd: 'play', seq: 5, url: 'http://192.168.1.20:8089/media/a.mp4', title: 'Film.mp4', pos: 2000 }));
  assert.strictEqual(video.src, 'http://192.168.1.20:8089/media/a.mp4');
  assert.ok(!/hidden/.test(player.className) && /hidden/.test(home.className), 'le lecteur passe devant');
  assert.strictEqual(fw.els('overlay-title').textContent, 'Film.mp4');
  assert.ok(fw.lunaCalls.some(function (c) { return c.method === 'createToast' && c.params.message === 'Lecture : Film.mp4'; }), 'toast au début de la lecture');
  var last = fw.states().pop();
  assert.deepStrictEqual([last.state, last.name, last.ack], ['buffering', 'Film.mp4', 5], 'état rapporté aussitôt, avec l\'ack de la commande');
  assert.strictEqual(last.pos, 2000, 'la position demandée est rapportée avant que la vidéo ne la prenne');
  // la vidéo charge, puis joue
  video.duration = 60; video.readyState = 4; video.currentTime = 2;
  video.fire('loadedmetadata');
  assert.strictEqual(video.currentTime, 2, 'le départ à 2 s est appliqué');
  video.fire('playing');
  last = fw.states().pop();
  assert.deepStrictEqual([last.state, last.pos, last.dur], ['playing', 2000, 60000]);
  // la même commande revenant dans la réponse de « state » n'est pas rejouée
  var loads = video.loads;
  fw.stateReplies.push({ returnValue: true, seq: 5, resync: false, commands: [{ cmd: 'play', seq: 5, url: 'http://h/autre.mp4', title: 'X', pos: 0 }] });
  fw.timers.intervals[0]();
  assert.strictEqual(video.loads, loads, 'commande déjà appliquée : ignorée');
  // pause demandée par le téléphone
  sub.onservicecallback(JSON.stringify({ returnValue: true, event: 'command', cmd: 'pause', seq: 6 }));
  last = fw.states().pop();
  assert.deepStrictEqual([last.state, last.ack], ['paused', 6]);
  // la télécommande : OK reprend, Retour arrête et revient à l'accueil
  assert.strictEqual(fw.key(13).defaultPrevented, true);
  assert.strictEqual(video.paused, false, 'OK reprend la lecture');
  fw.key(461);
  assert.ok(/hidden/.test(player.className) && !/hidden/.test(home.className), 'Retour revient à l\'accueil');
  last = fw.states().pop();
  assert.deepStrictEqual([last.state, last.name, last.pos], ['idle', null, 0]);
  // fin de lecture : l'app rapporte « ended » puis revient à l'accueil
  sub.onservicecallback(JSON.stringify({ returnValue: true, event: 'command', cmd: 'play', seq: 7, url: 'http://h/b.mp4', title: 'B', pos: 0 }));
  video.readyState = 4; video.ended = true; video.paused = true;
  video.fire('ended');
  assert.strictEqual(fw.states().pop().state, 'ended');
  fw.timers.timeouts.pop()();
  assert.strictEqual(fw.states().pop().state, 'idle');
  // service perdu : l'accueil le dit
  sub.onservicecallback(JSON.stringify({ returnValue: false, errorText: 'service arrêté' }));
  assert.ok(/indisponible/.test(fw.els('status-text').textContent));
  // Retour sur l'accueil quitte l'application
  fw.key(461);
  assert.strictEqual(fw.win.closed, true);
});

test('app : un lien refusé par l\'app (pas http) ne lance rien ; un service redémarré recale le numéro', function () {
  var fw = fakeWindow();
  app.start(fw.win);
  var sub = fw.lunaCalls.filter(function (c) { return c.method === 'events'; })[0].bridge;
  var video = fw.els('video');
  sub.onservicecallback(JSON.stringify({ returnValue: true, event: 'command', cmd: 'play', seq: 1, url: 'file:///etc/passwd', title: 'X', pos: 0 }));
  assert.strictEqual(video.src, undefined);
  assert.ok(/hidden/.test(fw.els('player').className));
  // le service redémarre (seq repart de 0) alors que l'app croit en être à 1 : resync
  fw.stateReplies.push({ returnValue: true, seq: 0, resync: true, commands: [] });
  fw.timers.intervals[0]();
  sub.onservicecallback(JSON.stringify({ returnValue: true, event: 'command', cmd: 'play', seq: 1, url: 'http://h/ok.mp4', title: 'OK', pos: 0 }));
  assert.strictEqual(video.src, 'http://h/ok.mp4', 'la commande 1 du service redémarré est appliquée');
});

test('app : hors TV (sans Luna) l\'écran s\'affiche et le dit', function () {
  var fw = fakeWindow();
  delete fw.win.PalmServiceBridge;
  app.start(fw.win);
  assert.ok(/Mode navigateur/.test(fw.els('status-text').textContent));
  assert.ok(/non disponible/.test(fw.els('address').textContent));
  assert.strictEqual(fw.lunaCalls.length, 0);
  fw.win.location.search = '';
  fw.key(461);
  assert.strictEqual(fw.win.closed, true);
});

test('garde ES5 et hors ligne : l\'app n\'emploie ni flèches, let/const, classes, ... ni ressource externe', function () {
  var raw = read('app.js');
  assert.ok(raw.indexOf('`') < 0, 'pas de gabarit de chaîne');
  var code = run.strip(raw);
  assert.ok(!/=>/.test(code), 'fonction fléchée');
  assert.ok(!/\b(let|const|class|async|await|yield)\b/.test(code), 'mot-clé ES2015+');
  assert.ok(!/\.\.\./.test(code), 'étalement');
  ['index.html', 'app.css'].forEach(function (f) {
    var text = read(f);
    assert.ok(!/(src|href)="https?:/i.test(text), f + ' : aucune ressource externe');
    assert.ok(!/@import|url\(\s*['"]?https?:/i.test(text), f + ' : aucun import externe');
  });
  assert.ok(!/var\(--/.test(read('app.css')), 'pas de variables CSS (webOS 3 / Chromium 38)');
});
