'use strict';
// Tests de CastBridge-TV Web, sans dépendance : « node tests/run.js » (code de sortie 0 = tout est vert).
// Couvre le service (mDNS, routes HTTP, état du lecteur, pont app/service, lecteur simulé), l'app (aides pures) et la cohérence des versions.
// Écrit pour tourner aussi en Node 8 : pas de ?? ni de ?., pas de modules ES.

var assert = require('assert');
var fs = require('fs');
var http = require('http');
var path = require('path');

var ROOT = path.join(__dirname, '..');
var LIB = path.join(ROOT, 'service', 'lib');
var C = require(path.join(LIB, 'constants.js'));
var mdns = require(path.join(LIB, 'mdns.js'));
var netutil = require(path.join(LIB, 'netutil.js'));
var player = require(path.join(LIB, 'player.js'));
var bridgeLib = require(path.join(LIB, 'bridge.js'));
var routes = require(path.join(LIB, 'routes.js'));
var simulatorLib = require(path.join(LIB, 'simulator.js'));
var httpLib = require(path.join(LIB, 'http.js'));
var argsLib = require(path.join(LIB, 'args.js'));

// ---------------------------------------------------------------------------------------------- petit lanceur de tests
var queue = [];
var failures = [];

function test(name, fn) { queue.push({ name: name, fn: fn }); }

function runFrom(i) {
  if (i >= queue.length) return finish();
  var t = queue[i];
  var finished = false;
  var timer = null;
  function done(err) {
    if (finished) return;
    finished = true;
    if (timer) clearTimeout(timer);
    if (err) {
      failures.push(t.name + '\n      ' + (err && err.stack ? String(err.stack).split('\n').slice(0, 5).join('\n      ') : String(err)));
      console.log('  KO  ' + t.name);
    } else {
      console.log('  ok  ' + t.name);
    }
    runFrom(i + 1);
  }
  try {
    if (t.fn.length > 0) {                                  // test asynchrone : function (done)
      timer = setTimeout(function () { done(new Error('délai de 5 s dépassé')); }, 5000);
      t.fn(done);
    } else {
      t.fn();
      done();
    }
  } catch (e) {
    done(e);
  }
}

function finish() {
  console.log('\n' + (queue.length - failures.length) + ' / ' + queue.length + ' tests réussis');
  if (failures.length) {
    console.log('\nÉCHECS :\n' + failures.map(function (f) { return '  - ' + f; }).join('\n'));
    process.exit(1);
  }
  process.exit(0);
}

// ---------------------------------------------------------------------------------------------- outils
function fakeClock() {
  var t = 1000000;
  return { now: function () { return t; }, advance: function (ms) { t += ms; } };
}

/** Un pont dont l'app est présente, et une horloge à avancer à la main. */
function fixture() {
  var clock = fakeClock();
  var bridge = bridgeLib.createBridge({ now: clock.now });
  bridge.touch();
  return { clock: clock, bridge: bridge };
}

function call(fx, method, rawUrl, headers) {
  var t = routes.parseTarget(rawUrl);
  return routes.handle({ method: method, path: t.path, query: t.query, headers: headers || {} }, { bridge: fx.bridge });
}

function playQuery(url, title, pos) {
  var q = '/api/playurl?url=' + encodeURIComponent(url);
  if (title !== undefined) q += '&title=' + encodeURIComponent(title);
  if (pos !== undefined) q += '&pos=' + encodeURIComponent(String(pos));
  return q;
}

// Port exact de TvInfo.parse / TvClient.num / TvClient.str (android/core/.../tv/TvInfo.kt) : ce que le téléphone lit dans /api/info.
function num(j, key) { var m = new RegExp('"' + key + '":(-?\\d+)').exec(j); return m ? Number(m[1]) : null; }
function str(j, key) {
  var m = new RegExp('"' + key + '":"((?:[^"\\\\]|\\\\.)*)"').exec(j);
  return m ? m[1].replace(/\\"/g, '"').replace(/\\\\/g, '\\') : null;
}
function parseTvInfo(j) {
  var files = [];
  var entry = /\{"name":"((?:[^"\\]|\\.)*)","size":(\d+),"received":(\d+),"complete":(true|false)[,}]/g;
  var m;
  while ((m = entry.exec(j))) files.push(m[1]);
  var at = j.indexOf('"player":');
  var pl = at >= 0 ? j.slice(at + '"player":'.length) : '{}';
  return {
    files: files, free: num(j, 'free') || 0, used: num(j, 'used') || 0, quota: num(j, 'quota') || 0,
    state: str(pl, 'state') || 'idle', playing: str(pl, 'name'), pos: num(pl, 'pos') || 0, dur: num(pl, 'dur') || 0
  };
}

var SVC = { instance: C.INSTANCE, type: C.MDNS_SERVICE, host: 'castbridge-tv-web-a1b2c3', port: C.HTTP_PORT, txt: C.TXT };

function queryFor(name, type, unicast, known) {
  return mdns.parseMessage(mdns.buildQuery([{ name: name, type: type, unicast: !!unicast }], known));
}

function ofType(list, type) { return list.filter(function (r) { return r.type === type; }); }

// ---------------------------------------------------------------------------------------------- mDNS
test('mDNS : noms DNS (aller-retour, point échappé, UTF-8, pointeur de compression)', function () {
  var labels = ['Salon été.TV', '_castbridge', '_tcp', 'local'];
  var name = mdns.joinLabels(labels);
  assert.strictEqual(name, 'Salon été\\.TV._castbridge._tcp.local');
  assert.deepStrictEqual(mdns.splitName(name), labels);
  var buf = Buffer.concat([Buffer.alloc(12), mdns.encodeName(name)]);
  var back = mdns.decodeName(buf, 12);
  assert.strictEqual(back.name, name);
  assert.strictEqual(back.next, buf.length);
  // « a.b.local » suivi d'un pointeur vers « b.local » (octet 14)
  var comp = Buffer.concat([Buffer.alloc(12), mdns.encodeName('a.b.local'), Buffer.from([0xc0, 14])]);
  var first = mdns.decodeName(comp, 12);
  assert.strictEqual(first.name, 'a.b.local');
  var ptr = mdns.decodeName(comp, first.next);
  assert.strictEqual(ptr.name, 'b.local');
  assert.strictEqual(ptr.next, comp.length);
  assert.throws(function () { mdns.encodeName('a..b'); });
  assert.throws(function () { mdns.encodeName(new Array(70).join('x')); });
});

test('mDNS : requête PTR du type de service -> PTR + SRV + TXT + A', function () {
  var q = queryFor('_castbridge._tcp.local', mdns.T.PTR);
  var out = mdns.respond(q, SVC, ['192.168.1.50']);
  assert.ok(out, 'une réponse est attendue');
  var msg = mdns.parseMessage(mdns.multicastResponse(out));
  assert.strictEqual(msg.response, true);
  assert.strictEqual(msg.id, 0);
  assert.strictEqual(msg.questions.length, 0);
  var ptr = ofType(msg.answers, mdns.T.PTR)[0];
  assert.strictEqual(ptr.name, '_castbridge._tcp.local');
  assert.strictEqual(ptr.data.target, 'CastBridge-TV Web._castbridge._tcp.local');
  assert.strictEqual(ptr.ttl, 4500);
  assert.strictEqual(ptr.flush, false, 'un PTR est partagé : pas de cache-flush');
  var srv = ofType(msg.additionals, mdns.T.SRV)[0];
  assert.strictEqual(srv.name, 'CastBridge-TV Web._castbridge._tcp.local');
  assert.strictEqual(srv.data.port, 8765);
  assert.strictEqual(srv.data.target, 'castbridge-tv-web-a1b2c3.local');
  assert.strictEqual(srv.ttl, 120);
  assert.strictEqual(srv.flush, true);
  assert.strictEqual(ofType(msg.additionals, mdns.T.TXT).length, 1);
  assert.strictEqual(ofType(msg.additionals, mdns.T.A)[0].data.address, '192.168.1.50');
});

test('mDNS : le nom d\'instance est « CastBridge-TV Web » (nom affiché par le téléphone)', function () {
  assert.strictEqual(C.INSTANCE, 'CastBridge-TV Web');
  var n = mdns.names(SVC);
  assert.strictEqual(n.instance, 'CastBridge-TV Web._castbridge._tcp.local');
  assert.strictEqual(n.service, '_castbridge._tcp.local');
  assert.strictEqual(C.MDNS_SERVICE + '.', '_castbridge._tcp.');        // ReceiverServer.SERVICE_TYPE
  // la casse de la question n'y change rien (les noms DNS sont insensibles à la casse)
  var out = mdns.respond(queryFor('_CastBridge._TCP.LOCAL', mdns.T.PTR), SVC, ['192.168.1.50']);
  assert.ok(out);
  assert.strictEqual(out.answers[0].data.target, 'CastBridge-TV Web._castbridge._tcp.local');
});

test('mDNS : TXT role=receiver et v=0.2, jamais locked (TvDiscovery lit role, locked=1 = verrouillée)', function () {
  var out = mdns.respond(queryFor('CastBridge-TV Web._castbridge._tcp.local', mdns.T.TXT), SVC, ['192.168.1.50']);
  var txt = ofType(mdns.parseMessage(mdns.multicastResponse(out)).answers, mdns.T.TXT)[0];
  assert.deepStrictEqual(txt.data.entries, ['role=receiver', 'v=0.2']);
  var map = mdns.txtMap(txt.data.entries);
  assert.strictEqual(map.role, 'receiver');
  assert.strictEqual(map.v, '0.2');
  assert.strictEqual(map.locked, undefined);
  assert.strictEqual(txt.flush, true);
  assert.deepStrictEqual(mdns.decodeTxt(mdns.encodeTxt([]), 0, 1), [], 'un TXT vide tient en un octet nul');
});

test('mDNS : questions SRV, TXT et ANY de l\'instance, A du nom d\'hôte', function () {
  var inst = 'CastBridge-TV Web._castbridge._tcp.local';
  var srv = mdns.respond(queryFor(inst, mdns.T.SRV), SVC, ['10.0.0.7']);
  assert.strictEqual(srv.answers.length, 1);
  assert.strictEqual(srv.answers[0].type, mdns.T.SRV);
  assert.strictEqual(ofType(srv.additionals, mdns.T.A)[0].data.address, '10.0.0.7');
  var any = mdns.respond(queryFor(inst, mdns.T.ANY), SVC, ['10.0.0.7']);
  assert.deepStrictEqual(any.answers.map(function (r) { return r.type; }).sort(), [mdns.T.TXT, mdns.T.SRV].sort());
  var a = mdns.respond(queryFor('castbridge-tv-web-a1b2c3.local', mdns.T.A), SVC, ['10.0.0.7', '192.168.1.50']);
  assert.deepStrictEqual(a.answers.map(function (r) { return r.data.address; }), ['10.0.0.7', '192.168.1.50']);
  assert.strictEqual(mdns.respond(queryFor('castbridge-tv-web-a1b2c3.local', mdns.T.AAAA), SVC, ['10.0.0.7']), null);
});

test('mDNS : énumération des services (_services._dns-sd._udp.local)', function () {
  var out = mdns.respond(queryFor('_services._dns-sd._udp.local', mdns.T.PTR), SVC, ['10.0.0.7']);
  assert.ok(out);
  assert.strictEqual(out.answers[0].data.target, '_castbridge._tcp.local');
});

test('mDNS : rien à répondre à une autre question, à une réponse ou à un opcode non nul', function () {
  assert.strictEqual(mdns.respond(queryFor('_airplay._tcp.local', mdns.T.PTR), SVC, ['10.0.0.7']), null);
  assert.strictEqual(mdns.respond(queryFor('autre.local', mdns.T.A), SVC, ['10.0.0.7']), null);
  var resp = mdns.parseMessage(mdns.announcement(SVC, ['10.0.0.7'], false));
  assert.strictEqual(resp.response, true);
  assert.strictEqual(mdns.respond(resp, SVC, ['10.0.0.7']), null, 'on ne répond pas à une réponse');
  var q = mdns.buildQuery([{ name: '_castbridge._tcp.local', type: mdns.T.PTR }]);
  q.writeUInt16BE(0x2800, 2);                                       // opcode 5
  assert.strictEqual(mdns.respond(mdns.parseMessage(q), SVC, ['10.0.0.7']), null);
  assert.strictEqual(mdns.respond(null, SVC, []), null);
});

test('mDNS : suppression des réponses connues (RFC 6762 § 7.1)', function () {
  var R = mdns.records(SVC, ['192.168.1.50']);
  function known(ttl) { return [{ name: R.ptr.name, type: R.ptr.type, ttl: ttl, flush: false, data: R.ptr.data }]; }
  var fresh = queryFor('_castbridge._tcp.local', mdns.T.PTR, false, known(4000));
  assert.strictEqual(mdns.respond(fresh, SVC, ['192.168.1.50']), null, 'le demandeur connaît déjà le PTR avec plus de la moitié du TTL');
  var stale = queryFor('_castbridge._tcp.local', mdns.T.PTR, false, known(100));
  assert.ok(mdns.respond(stale, SVC, ['192.168.1.50']), 'TTL restant trop bas : on répond');
});

test('mDNS : réponse « legacy » (demandeur hors du port 5353) : id et question recopiés, TTL plafonné', function () {
  var raw = mdns.buildMessage({ id: 4321, response: false, questions: [{ name: '_castbridge._tcp.local', type: mdns.T.PTR }] });
  var query = mdns.parseMessage(raw);
  var out = mdns.respond(query, SVC, ['192.168.1.50']);
  var msg = mdns.parseMessage(mdns.legacyResponse(query, out));
  assert.strictEqual(msg.id, 4321);
  assert.strictEqual(msg.questions.length, 1);
  assert.strictEqual(msg.questions[0].name, '_castbridge._tcp.local');
  msg.answers.concat(msg.additionals).forEach(function (r) {
    assert.ok(r.ttl <= 10, 'TTL plafonné à 10 s');
    assert.strictEqual(r.flush, false, 'pas de cache-flush en unicast legacy');
  });
});

test('mDNS : bit unicast-response (QU) lu et signalé', function () {
  var q = queryFor('_castbridge._tcp.local', mdns.T.PTR, true);
  assert.strictEqual(q.questions[0].unicast, true);
  assert.strictEqual(q.questions[0].cls, 1);
  assert.strictEqual(mdns.respond(q, SVC, ['192.168.1.50']).unicast, true);
  assert.strictEqual(mdns.respond(queryFor('_castbridge._tcp.local', mdns.T.PTR, false), SVC, ['192.168.1.50']).unicast, false);
});

test('mDNS : annonce spontanée et « au revoir » (TTL 0)', function () {
  var hello = mdns.parseMessage(mdns.announcement(SVC, ['192.168.1.50'], false));
  assert.deepStrictEqual(hello.answers.map(function (r) { return r.type; }), [mdns.T.PTR, mdns.T.SRV, mdns.T.TXT, mdns.T.A]);
  var bye = mdns.parseMessage(mdns.announcement(SVC, ['192.168.1.50'], true));
  assert.strictEqual(bye.answers.length, 4);
  bye.answers.forEach(function (r) { assert.strictEqual(r.ttl, 0); });
});

test('mDNS : paquets tronqués ou aléatoires -> null, jamais d\'exception', function () {
  var good = mdns.announcement(SVC, ['192.168.1.50'], false);
  for (var n = 0; n < good.length; n++) {
    var cut = mdns.parseMessage(good.slice(0, n));
    if (n < 12) assert.strictEqual(cut, null);
  }
  // pointeur qui boucle sur lui-même
  var loop = Buffer.concat([Buffer.from([0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0]), Buffer.from([0xc0, 12, 0, 12, 0, 1])]);
  assert.strictEqual(mdns.parseMessage(loop), null);
  var seed = 12345;
  for (var i = 0; i < 300; i++) {
    var len = 12 + (i % 60);
    var b = Buffer.alloc(len);
    for (var k = 0; k < len; k++) { seed = (seed * 1103515245 + 12345) & 0x7fffffff; b[k] = seed >> 16 & 0xff; }
    mdns.parseMessage(b);
  }
  assert.strictEqual(mdns.parseMessage('pas un tampon'), null);
});

test('réseau : adresses annoncées (sous-réseau du demandeur, lien local et boucle écartés, suffixe MAC)', function () {
  var ifaces = {
    lo0: [{ address: '127.0.0.1', family: 'IPv4', internal: true, netmask: '255.0.0.0' }],
    en0: [{ address: '192.168.1.50', family: 'IPv4', internal: false, netmask: '255.255.255.0', mac: 'aa:bb:cc:dd:ee:ff' },
      { address: 'fe80::1', family: 'IPv6', internal: false }],
    eth0: [{ address: '10.0.0.7', family: 4, internal: false, netmask: '255.0.0.0', mac: '00:11:22:33:44:55' }],
    zz: [{ address: '169.254.1.1', family: 'IPv4', internal: false, netmask: '255.255.0.0' }]
  };
  assert.deepStrictEqual(netutil.pickAddresses(ifaces), ['192.168.1.50', '10.0.0.7']);
  assert.deepStrictEqual(netutil.pickAddresses(ifaces, '192.168.1.99'), ['192.168.1.50']);
  assert.deepStrictEqual(netutil.pickAddresses(ifaces, '172.16.0.1'), ['192.168.1.50', '10.0.0.7']);
  assert.strictEqual(netutil.macSuffix(ifaces), 'ddeeff');
  assert.strictEqual(netutil.macSuffix({}), '');
});

// ---------------------------------------------------------------------------------------------- routes HTTP
test('routes : GET /api/hello (le test « castbridge-tv » du téléphone) et ses clés', function () {
  var fx = fixture();
  var r = call(fx, 'GET', '/api/hello');
  assert.strictEqual(r.status, 200);
  assert.ok(r.body.indexOf('castbridge-tv') >= 0, 'UploadService.helloOk cherche « castbridge-tv »');
  var h = JSON.parse(r.body);
  assert.strictEqual(h.app, 'castbridge-tv');
  assert.strictEqual(h.v, C.API_VERSION);
  assert.strictEqual(h.pinRequired, false);
  assert.strictEqual(h.platform, 'webos');
  assert.strictEqual(h.build, C.VERSION);
  assert.strictEqual(h.locked, undefined, 'jamais « locked » : pas d\'activation');
});

test('routes : GET /api/info au repos = les clés de TvInfo.parse, et le JSON exact', function () {
  var fx = fixture();
  var r = call(fx, 'GET', '/api/info');
  assert.strictEqual(r.status, 200);
  assert.strictEqual(r.body, '{"files":[],"free":0,"used":0,"quota":0,"player":{"state":"idle","name":null,"pos":0,"dur":0}}');
  assert.deepStrictEqual(parseTvInfo(r.body), { files: [], free: 0, used: 0, quota: 0, state: 'idle', playing: null, pos: 0, dur: 0 });
});

test('routes : POST /api/playurl avec paramètres encodés (lien, titre accentué, position en ms)', function () {
  var fx = fixture();
  var url = 'http://192.168.1.20:8089/media/ab12.mp4?t=1&lang=fr';
  var title = 'Été 2026 – Mon film.mp4';
  var r = call(fx, 'POST', playQuery(url, title, 12345));
  assert.strictEqual(r.status, 200);
  var info = parseTvInfo(r.body);
  assert.strictEqual(info.state, 'buffering');
  assert.strictEqual(info.playing, title, 'CastSession.poll compare player.name au titre envoyé');
  assert.strictEqual(info.pos, 12345);
  assert.deepStrictEqual(fx.bridge.since(0), [{ cmd: 'play', url: url, title: title, pos: 12345, seq: 1 }]);
  // la même requête telle que l'écrit TvClient.enc (URLEncoder, espace = %20)
  var raw = '/api/playurl?url=http%3A%2F%2F192.168.1.20%3A8089%2Fmedia%2Fab12.mp4%3Ft%3D1%26lang%3Dfr&title=%C3%89t%C3%A9%202026&pos=0';
  assert.strictEqual(call(fx, 'POST', raw).status, 200);
  assert.strictEqual(fx.bridge.since(1)[0].title, 'Été 2026');
});

test('routes : playurl refuse ce qui n\'est pas un lien http(s) (400, comme ReceiverServer)', function () {
  var fx = fixture();
  var bad = ['', 'ftp://x/y', 'file:///etc/passwd', 'javascript:alert(1)', 'noscheme', '://x', 'http://a b/c', 'http://x/\nGET', 'http://x/' + new Array(4100).join('a')];
  bad.forEach(function (url) {
    var r = call(fx, 'POST', playQuery(url, 'T'));
    assert.strictEqual(r.status, 400, 'refusé : ' + url.slice(0, 30));
    assert.strictEqual(r.body, '{"error":"http(s) url required"}');
  });
  assert.strictEqual(call(fx, 'POST', '/api/playurl').status, 400, 'sans paramètre url');
  assert.strictEqual(fx.bridge.seq(), 0, 'aucune commande n\'a été émise');
  assert.strictEqual(call(fx, 'POST', playQuery('HTTPS://Exemple.cm/film.mp4', 'T')).status, 200, 'schéma insensible à la casse');
});

test('routes : titre (safeName) et position (toLongOrNull, >= 0)', function () {
  var fx = fixture();
  function sent(title, pos) {
    call(fx, 'POST', playQuery('http://h/x.mp4', title, pos));
    return fx.bridge.since(0).pop();
  }
  assert.strictEqual(sent(undefined, undefined).title, C.DEFAULT_TITLE);
  assert.strictEqual(sent('a/b.mp4').title, C.DEFAULT_TITLE);
  assert.strictEqual(sent('a\\b.mp4').title, C.DEFAULT_TITLE);
  assert.strictEqual(sent('.cache').title, C.DEFAULT_TITLE);
  assert.strictEqual(sent(new Array(202).join('x')).title, C.DEFAULT_TITLE, '201 caractères');
  assert.strictEqual(sent(new Array(201).join('x')).title.length, 200, '200 caractères : gardé');
  assert.strictEqual(sent('  Film  ').title, 'Film');
  assert.strictEqual(sent('T', 'abc').pos, 0);
  assert.strictEqual(sent('T', '-5').pos, 0);
  assert.strictEqual(sent('T', '+7').pos, 7);
  assert.strictEqual(sent('T', '1.5').pos, 0);
  assert.strictEqual(sent('T', '').pos, 0);
  assert.strictEqual(sent('T', '90000').pos, 90000);
});

test('routes : playurl sans app au premier plan -> 409 et le message que le téléphone affiche', function () {
  var fx = fixture();
  fx.clock.advance(C.PRESENCE_MS + 1);
  var r = call(fx, 'POST', playQuery('http://h/x.mp4', 'T'));
  assert.strictEqual(r.status, 409);
  var body = JSON.parse(r.body);
  assert.strictEqual(body.error, 'foreground');
  assert.strictEqual(body.message, C.FOREGROUND_MESSAGE);
  assert.strictEqual(str(r.body, 'message'), C.FOREGROUND_MESSAGE, 'CastSession lit TvClient.str(corps, "message")');
  assert.strictEqual(fx.bridge.seq(), 0);
});

test('routes : pause, reprise, position et arrêt (état rendu aussitôt, comme ok(info()))', function () {
  var fx = fixture();
  call(fx, 'POST', playQuery('http://h/x.mp4', 'Film', 5000));
  fx.bridge.report({ state: 'playing', name: 'Film', pos: 5000, dur: 60000 }, 1);          // l'app confirme
  fx.clock.advance(1000);
  fx.bridge.touch();
  var i = parseTvInfo(call(fx, 'GET', '/api/info').body);
  assert.deepStrictEqual([i.state, i.pos, i.dur], ['playing', 6000, 60000], 'la position avance entre deux rapports');
  i = parseTvInfo(call(fx, 'POST', '/api/pause').body);
  assert.deepStrictEqual([i.state, i.pos], ['paused', 6000]);
  fx.clock.advance(4000);
  fx.bridge.touch();
  assert.strictEqual(parseTvInfo(call(fx, 'GET', '/api/info').body).pos, 6000, 'en pause, la position ne bouge pas');
  i = parseTvInfo(call(fx, 'POST', '/api/resume').body);
  assert.strictEqual(i.state, 'playing');
  fx.clock.advance(2000);
  fx.bridge.touch();
  assert.strictEqual(parseTvInfo(call(fx, 'GET', '/api/info').body).pos, 8000);
  i = parseTvInfo(call(fx, 'POST', '/api/seek?pos=30000').body);
  assert.deepStrictEqual([i.state, i.pos], ['playing', 30000]);
  assert.strictEqual(parseTvInfo(call(fx, 'POST', '/api/seek?pos=999999').body).pos, 60000, 'au plus la durée');
  i = parseTvInfo(call(fx, 'POST', '/api/stop').body);
  assert.deepStrictEqual(i, { files: [], free: 0, used: 0, quota: 0, state: 'idle', playing: null, pos: 0, dur: 0 });
  assert.deepStrictEqual(fx.bridge.since(0).map(function (c) { return c.cmd; }), ['play', 'pause', 'resume', 'seek', 'seek', 'stop']);
  assert.strictEqual(fx.bridge.since(0)[3].pos, 30000);
});

test('routes : commandes de lecture sans app = lecteur au repos, rien d\'émis', function () {
  var fx = fixture();
  fx.clock.advance(C.PRESENCE_MS + 1);
  ['/api/pause', '/api/resume', '/api/stop', '/api/seek?pos=5'].forEach(function (p) {
    var r = call(fx, 'POST', p);
    assert.strictEqual(r.status, 200);
    assert.strictEqual(parseTvInfo(r.body).state, 'idle');
  });
  assert.strictEqual(fx.bridge.seq(), 0);
});

test('routes : 404, 405, et refus des requêtes de navigateur (Origin)', function () {
  var fx = fixture();
  ['/', '/api/nope', '/api/sysinfo', '/api/library', '/api/storage', '/stream/x.mp4', '/api/play'].forEach(function (p) {
    var r = call(fx, 'GET', p);
    assert.strictEqual(r.status, 404, p);
    assert.strictEqual(r.body, '{"error":"not found"}');
  });
  assert.strictEqual(call(fx, 'GET', '/api/pause').status, 405);
  assert.strictEqual(call(fx, 'GET', '/api/playurl?url=http%3A%2F%2Fh%2Fx').status, 405, 'une lecture ne se lance pas par GET');
  assert.strictEqual(call(fx, 'POST', '/api/info').status, 405);
  assert.strictEqual(call(fx, 'POST', '/api/hello').status, 405);
  var r = call(fx, 'POST', playQuery('http://h/x.mp4', 'T'), { origin: 'http://page-malveillante.example' });
  assert.strictEqual(r.status, 403);
  assert.strictEqual(fx.bridge.seq(), 0);
  assert.strictEqual(call(fx, 'GET', '/api/hello', { origin: 'http://x' }).status, 403);
});

test('routes : parseTarget (premier paramètre, + = espace, % invalide toléré)', function () {
  var t = routes.parseTarget('/api/playurl?url=a%20b&url=c&title=x+y&pos=%zz&vide&=v');
  assert.strictEqual(t.path, '/api/playurl');
  assert.strictEqual(t.query.url, 'a b');
  assert.strictEqual(t.query.title, 'x y');
  assert.strictEqual(t.query.pos, '%zz');
  assert.strictEqual(t.query.vide, '');
  assert.strictEqual(routes.parseTarget('/api/%zzinfo').path, '/api/%zzinfo');
  assert.strictEqual(routes.parseTarget('/api/info').path, '/api/info');
  assert.strictEqual(Object.keys(routes.parseTarget('/x?__proto__=1').query).length, 1, '__proto__ n\'est qu\'une clé ordinaire');
});

// ---------------------------------------------------------------------------------------------- état du lecteur et pont
test('lecteur : normalisation de l\'état rapporté par l\'app', function () {
  assert.deepStrictEqual(player.normalize(null), player.idle());
  assert.deepStrictEqual(player.normalize({ state: 'danse', name: 'x', pos: 5 }), player.idle());
  assert.deepStrictEqual(player.normalize({ state: 'idle', name: 'x', pos: 5, dur: 9 }), player.idle(), 'au repos : ni nom ni position');
  assert.deepStrictEqual(player.normalize({ state: 'playing', name: 'Film', pos: 1234.6, dur: 60000.2 }), { state: 'playing', name: 'Film', pos: 1235, dur: 60000 });
  assert.deepStrictEqual(player.normalize({ state: 'paused', name: '', pos: -5, dur: 'x' }), { state: 'paused', name: null, pos: 0, dur: 0 });
  assert.strictEqual(player.normalize({ state: 'playing', pos: 90, dur: 50 }).pos, 50, 'pos <= dur');
  assert.strictEqual(player.normalize({ state: 'playing', name: new Array(300).join('a') }).name.length, 200);
  ['buffering', 'playing', 'paused', 'ended', 'error'].forEach(function (s) {
    assert.strictEqual(player.normalize({ state: s, name: 'n' }).state, s, 'états que CastSession.poll comprend : ' + s);
  });
});

test('pont : commandes numérotées, abonnés, abonné défaillant écarté', function () {
  var fx = fixture();
  var got = [];
  var off = fx.bridge.subscribe(function (c) { got.push(c.seq + ':' + c.cmd); });
  fx.bridge.subscribe(function () { throw new Error('abonné mort'); });
  assert.strictEqual(fx.bridge.subscribers(), 2);
  fx.bridge.send({ cmd: 'pause' });
  assert.strictEqual(fx.bridge.subscribers(), 1, 'l\'abonné qui lève une exception est retiré');
  fx.bridge.send({ cmd: 'resume' });
  off();
  fx.bridge.send({ cmd: 'stop' });
  assert.deepStrictEqual(got, ['1:pause', '2:resume']);
  assert.strictEqual(fx.bridge.seq(), 3);
});

test('pont : rapport en retard sur une commande ignoré, rapport à jour accepté, recalage après redémarrage', function () {
  var fx = fixture();
  var b = fx.bridge;
  b.send({ cmd: 'play', url: 'http://h/x.mp4', title: 'T', pos: 0 });
  var r = b.report({ state: 'idle' }, 0);
  assert.strictEqual(r.commands.length, 1, 'l\'app n\'a pas encore la commande : elle lui est rendue');
  assert.strictEqual(b.snapshot().state, 'buffering', 'un rapport antérieur à la commande ne ramène pas l\'état en arrière');
  r = b.report({ state: 'playing', name: 'T', pos: 100, dur: 5000 }, 1);
  assert.deepStrictEqual(r.commands, []);
  assert.strictEqual(b.snapshot().state, 'playing');
  r = b.report({ state: 'paused', name: 'T', pos: 200, dur: 5000 });
  assert.deepStrictEqual([r.commands, r.seq], [[], 1], 'app neuve (pas d\'ack) : rien n\'est rejoué, elle apprend seq');
  assert.strictEqual(b.snapshot().state, 'paused');
  r = b.report({ state: 'idle' }, 99);
  assert.deepStrictEqual([r.commands, r.seq], [[], 1], 'ack > seq : le service a redémarré, rien n\'est rejoué');
  assert.strictEqual(b.since(0).length, 1);
});

test('pont : une commande trop vieille n\'est plus rejouée, l\'app absente est au repos', function () {
  var fx = fixture();
  var b = fx.bridge;
  b.send({ cmd: 'play', url: 'http://h/x.mp4', title: 'T', pos: 0 });
  fx.clock.advance(14000);
  assert.strictEqual(b.since(0).length, 1);
  fx.clock.advance(2000);
  assert.strictEqual(b.since(0).length, 0, 'au-delà de 15 s');
  assert.strictEqual(b.isAlive(), false, 'plus de signe de vie depuis 16 s');
  assert.deepStrictEqual(b.snapshot(), player.idle());
  b.touch();
  assert.strictEqual(b.isAlive(), true);
  assert.deepStrictEqual(b.snapshot(), player.idle(), 'l\'app qui revient repart d\'un état propre');
});

test('lecteur simulé (--standalone) : chargement, lecture, pause, reprise, position, arrêt', function () {
  var fx = fixture();
  var sim = simulatorLib.create({ now: fx.clock.now, bufferMs: 400 });
  fx.bridge.subscribe(function (cmd) { sim.apply(cmd); fx.bridge.report(sim.current(), sim.ack()); });
  var i = parseTvInfo(call(fx, 'POST', playQuery('http://h/x.mp4', 'Film', 2000)).body);
  assert.deepStrictEqual([i.state, i.playing, i.pos], ['buffering', 'Film', 2000]);
  fx.clock.advance(400);
  fx.bridge.report(sim.current(), sim.ack());
  assert.strictEqual(parseTvInfo(call(fx, 'GET', '/api/info').body).state, 'playing');
  fx.clock.advance(1000);
  fx.bridge.report(sim.current(), sim.ack());
  assert.strictEqual(parseTvInfo(call(fx, 'GET', '/api/info').body).pos, 3000);
  i = parseTvInfo(call(fx, 'POST', '/api/pause').body);
  assert.deepStrictEqual([i.state, i.pos], ['paused', 3000]);
  fx.clock.advance(5000);
  fx.bridge.report(sim.current(), sim.ack());
  assert.strictEqual(parseTvInfo(call(fx, 'GET', '/api/info').body).pos, 3000);
  call(fx, 'POST', '/api/resume');
  fx.clock.advance(1000);
  fx.bridge.report(sim.current(), sim.ack());
  assert.strictEqual(parseTvInfo(call(fx, 'GET', '/api/info').body).pos, 4000);
  i = parseTvInfo(call(fx, 'POST', '/api/seek?pos=10000').body);
  assert.strictEqual(i.pos, 10000);
  i = parseTvInfo(call(fx, 'POST', '/api/stop').body);
  assert.deepStrictEqual([i.state, i.playing, i.pos], ['idle', null, 0]);
});

test('ligne de commande du service (--standalone, --port, --name, --ip, --no-mdns)', function () {
  var d = argsLib.parse([], {});
  assert.deepStrictEqual([d.standalone, d.port, d.name, d.mdns, d.errors.length], [false, 8765, 'CastBridge-TV Web', true, 0]);
  var a = argsLib.parse(['--standalone', '--port', '9000', '--name', 'Salon', '--ip', '10.0.0.7', '--no-mdns', '-v'], {});
  assert.deepStrictEqual([a.standalone, a.port, a.name, a.ip, a.mdns, a.verbose], [true, 9000, 'Salon', '10.0.0.7', false, true]);
  assert.strictEqual(argsLib.parse(['--port=9001', '--name=Chambre'], {}).port, 9001);
  assert.strictEqual(argsLib.parse([], { CASTBRIDGE_TV_NAME: 'Cuisine' }).name, 'Cuisine');
  assert.ok(argsLib.parse(['--port', '0'], {}).errors.length > 0);
  assert.ok(argsLib.parse(['--port', 'abc'], {}).errors.length > 0);
  assert.ok(argsLib.parse(['--ip', 'pas-une-ip'], {}).errors.length > 0);
  assert.ok(argsLib.parse(['--name', ''], {}).errors.length > 0);
});

// ---------------------------------------------------------------------------------------------- HTTP réel
function request(port, method, urlPath, headers, cb) {
  var req = http.request({ host: '127.0.0.1', port: port, method: method, path: urlPath, headers: headers || {}, agent: false }, function (res) {
    var chunks = [];
    res.on('data', function (c) { chunks.push(c); });
    res.on('end', function () { cb(null, { status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks).toString('utf8') }); });
  });
  req.on('error', cb);
  req.end();
}

test('HTTP réel : hello, info, playurl, 404 ; réponses JSON UTF-8 et « Connection: close »', function (done) {
  var bridge = bridgeLib.createBridge({});
  bridge.touch();
  var server = httpLib.create({ handle: function (req) { return routes.handle(req, { bridge: bridge }); } });
  server.on('error', function (e) { done(e.code === 'EPERM' || e.code === 'EACCES' ? null : e); });        // sockets interdits : on n'échoue pas
  server.listen(0, '127.0.0.1', function () {
    var port = server.address().port;
    function end(err) { server.close(function () { done(err); }); }
    request(port, 'GET', '/api/hello', {}, function (e1, hello) {
      if (e1) return end(e1);
      try {
        assert.strictEqual(hello.status, 200);
        assert.ok(/charset=utf-8/i.test(hello.headers['content-type']), 'Content-Type avec charset');
        assert.strictEqual(hello.headers.connection, 'close');
        assert.ok(hello.body.indexOf('castbridge-tv') >= 0);
      } catch (x) { return end(x); }
      request(port, 'GET', '/api/info', {}, function (e2, info) {
        if (e2) return end(e2);
        try { assert.strictEqual(parseTvInfo(info.body).state, 'idle'); } catch (x) { return end(x); }
        var q = playQuery('http://192.168.1.20:8089/media/a.mp4', 'Été.mp4', 1500);
        request(port, 'POST', q, { 'Content-Length': '0' }, function (e3, play) {
          if (e3) return end(e3);
          try {
            assert.strictEqual(play.status, 200);
            var p = parseTvInfo(play.body);
            assert.deepStrictEqual([p.state, p.playing, p.pos], ['buffering', 'Été.mp4', 1500]);
            assert.strictEqual(bridge.since(0)[0].url, 'http://192.168.1.20:8089/media/a.mp4');
          } catch (x) { return end(x); }
          request(port, 'GET', '/api/inconnue', {}, function (e4, nf) {
            if (e4) return end(e4);
            try { assert.strictEqual(nf.status, 404); } catch (x) { return end(x); }
            request(port, 'POST', '/api/stop', { Origin: 'http://page.example' }, function (e5, refused) {
              if (e5) return end(e5);
              try { assert.strictEqual(refused.status, 403); } catch (x) { return end(x); }
              end(null);
            });
          });
        });
      });
    });
  });
});

// ---------------------------------------------------------------------------------------------- cohérence des fichiers
function readJson(p) { return JSON.parse(fs.readFileSync(p, 'utf8')); }

test('cohérence : identifiants et versions (appinfo.json, package.json, services.json, constantes, version.properties)', function () {
  var appinfo = readJson(path.join(ROOT, 'appinfo.json'));
  var pkg = readJson(path.join(ROOT, 'service', 'package.json'));
  var svc = readJson(path.join(ROOT, 'service', 'services.json'));
  assert.strictEqual(appinfo.id, C.APP_ID);
  assert.strictEqual(svc.id, C.SERVICE_ID);
  assert.strictEqual(pkg.name, C.SERVICE_ID, 'package.json : name = id du service');
  assert.strictEqual(pkg.main, 'service.js');
  assert.ok(fs.existsSync(path.join(ROOT, 'service', pkg.main)));
  assert.strictEqual(C.SERVICE_ID.indexOf(C.APP_ID + '.'), 0, 'l\'id du service commence par celui de l\'app (exigé par ares-package)');
  assert.deepStrictEqual(svc.services.map(function (s) { return s.name; }), [C.SERVICE_ID]);
  var methods = svc.services[0].commands.map(function (c) { return c.name; }).sort();
  assert.deepStrictEqual(methods, ['events', 'state', 'status']);
  var plain = C.VERSION.replace(/-beta$/, '');
  assert.ok(/^\d+\.\d+\.\d+$/.test(appinfo.version), 'webOS exige X.Y.Z numérique');
  assert.strictEqual(appinfo.version, plain, 'appinfo.json = version sans suffixe');
  assert.strictEqual(pkg.version, plain);
  assert.strictEqual(appinfo.type, 'web');
  assert.strictEqual(appinfo.main, 'index.html');
  ['icon', 'largeIcon'].forEach(function (k) { assert.ok(fs.existsSync(path.join(ROOT, appinfo[k])), appinfo[k] + ' existe'); });
  var props = path.join(ROOT, '..', '..', 'version.properties');
  if (fs.existsSync(props)) {
    var text = fs.readFileSync(props, 'utf8');
    var name = /^tvweb\.versionName=(.+)$/m.exec(text);
    assert.ok(name, 'version.properties : tvweb.versionName');
    assert.strictEqual(name[1].trim(), C.VERSION, 'constants.VERSION = tvweb.versionName');
    assert.ok(/^tvweb\.versionCode=\d+$/m.test(text), 'version.properties : tvweb.versionCode');
  }
});

/** Retire commentaires et chaînes (approximatif, suffisant pour une garde de syntaxe). */
function strip(src) {
  var out = '';
  var i = 0;
  var n = src.length;
  while (i < n) {
    var c = src.charAt(i);
    var d = src.charAt(i + 1);
    if (c === '/' && d === '/') { while (i < n && src.charAt(i) !== '\n') i++; continue; }
    if (c === '/' && d === '*') { i += 2; while (i < n && !(src.charAt(i) === '*' && src.charAt(i + 1) === '/')) i++; i += 2; continue; }
    if (c === '"' || c === '\'' || c === '`') {
      i++;
      while (i < n && src.charAt(i) !== c) { if (src.charAt(i) === '\\') i++; i++; }
      i++;
      out += '""';
      continue;
    }
    out += c;
    i++;
  }
  return out;
}

function sourcesOf(dir) {
  var out = [];
  fs.readdirSync(dir).forEach(function (f) {
    var p = path.join(dir, f);
    if (fs.statSync(p).isDirectory()) out = out.concat(sourcesOf(p)); else if (/\.js$/.test(f)) out.push(p);
  });
  return out;
}

test('garde Node 8 : le service n\'emploie ni ??, ni ?., ni modules ES, ni « catch » sans paramètre', function () {
  var files = sourcesOf(path.join(ROOT, 'service'));
  assert.ok(files.length >= 8, 'les sources du service sont trouvées');
  files.forEach(function (f) {
    var code = strip(fs.readFileSync(f, 'utf8'));
    var rel = path.relative(ROOT, f);
    assert.ok(!/\?\?/.test(code), rel + ' : ??');
    assert.ok(!/\?\.[A-Za-z_$(\[]/.test(code), rel + ' : ?.');
    assert.ok(!/^\s*(import|export)\s/m.test(code), rel + ' : module ES');
    assert.ok(!/\bcatch\s*\{/.test(code), rel + ' : catch sans paramètre');
    assert.ok(!/\bawait\b/.test(code), rel + ' : await');
  });
});

test('garde « sans secret » : le service ne journalise jamais la requête (paramètres, liens)', function () {
  var code = fs.readFileSync(path.join(LIB, 'http.js'), 'utf8');
  assert.ok(/log\(req\.method \+ ' ' \+ target\.path \+/.test(code), 'le journal ne contient que la méthode, le chemin et le statut');
  assert.ok(!/req\.url/.test(code.replace(/parseTarget\(req\.url/, '')), 'req.url n\'est utilisé que pour parseTarget');
});

module.exports = { test: test, fakeClock: fakeClock, strip: strip, sourcesOf: sourcesOf, ROOT: ROOT };

// Les tests de l'app (aides pures de app.js) sont dans tests/app.test.js, chargé s'il existe.
var appTests = path.join(__dirname, 'app.test.js');
if (fs.existsSync(appTests)) require(appTests);

runFrom(0);
