'use strict';
// mDNS / DNS-SD (RFC 6762 et 6763) pour CastBridge-TV Web : encodage, décodage et réponses.
// Module PUR (aucune entrée/sortie) : tout se teste sans réseau. Compatible Node 8.

var T = { A: 1, PTR: 12, TXT: 16, AAAA: 28, SRV: 33, ANY: 255 };
var CLASS_IN = 1;
var FLUSH = 0x8000;        // bit « cache-flush » (réponses) ou « unicast-response » (questions)
var TTL_HOST = 120;        // A et SRV (RFC 6762 § 10)
var TTL_OTHER = 4500;      // PTR et TXT
var LEGACY_TTL = 10;       // réponse unicast « legacy » : TTL plafonné (RFC 6762 § 6.7)
var PORT = 5353;
var ADDRESS = '224.0.0.251';
var ENUM_NAME = '_services._dns-sd._udp.local';

function lower(s) { return String(s).toLowerCase(); }

// ---------------------------------------------------------------------------------------------- noms

/** « a\.b.c » -> ['a.b', 'c'] ; « » et « . » donnent [] ; un point final est ignoré. */
function splitName(name) {
  if (name === '' || name === '.') return [];
  var labels = [];
  var cur = '';
  for (var i = 0; i < name.length; i++) {
    var c = name.charAt(i);
    if (c === '\\' && i + 1 < name.length) { cur += name.charAt(++i); }
    else if (c === '.') { labels.push(cur); cur = ''; }
    else { cur += c; }
  }
  if (cur !== '') labels.push(cur);
  return labels;
}

/** Inverse de splitName : les points et les barres obliques inverses d'une étiquette sont échappés. */
function joinLabels(labels) {
  return labels.map(function (l) { return l.replace(/\\/g, '\\\\').replace(/\./g, '\\.'); }).join('.');
}

function encodeName(name) {
  var labels = splitName(name);
  var parts = [];
  var total = 1;
  labels.forEach(function (l) {
    var b = Buffer.from(l, 'utf8');
    if (b.length === 0 || b.length > 63) throw new Error('étiquette DNS invalide : « ' + l + ' »');
    total += b.length + 1;
    parts.push(Buffer.from([b.length]), b);
  });
  if (total > 255) throw new Error('nom DNS trop long');
  parts.push(Buffer.from([0]));
  return Buffer.concat(parts);
}

/** Lit un nom (compression par pointeurs comprise). Retourne { name, next } ; next = octet suivant le nom d'origine. */
function decodeName(buf, offset) {
  var labels = [];
  var pos = offset;
  var next = -1;
  var hops = 0;
  var total = 0;
  for (;;) {
    if (pos >= buf.length) throw new Error('nom tronqué');
    var len = buf[pos];
    if (len === 0) { if (next < 0) next = pos + 1; break; }
    if ((len & 0xc0) === 0xc0) {
      if (pos + 1 >= buf.length) throw new Error('pointeur tronqué');
      if (next < 0) next = pos + 2;
      if (++hops > 32) throw new Error('trop de pointeurs');
      pos = ((len & 0x3f) << 8) | buf[pos + 1];
      continue;
    }
    if ((len & 0xc0) !== 0) throw new Error('étiquette réservée');
    pos++;
    if (pos + len > buf.length) throw new Error('étiquette tronquée');
    total += len + 1;
    if (total > 255) throw new Error('nom trop long');
    labels.push(buf.toString('utf8', pos, pos + len));
    pos += len;
  }
  return { name: joinLabels(labels), next: next };
}

// ---------------------------------------------------------------------------------------------- données

function encodeTxt(entries) {
  if (!entries || entries.length === 0) return Buffer.from([0]);     // un TXT vide = un seul octet 0 (RFC 6763 § 6.1)
  var parts = [];
  entries.forEach(function (e) {
    var b = Buffer.from(String(e), 'utf8');
    if (b.length > 255) throw new Error('entrée TXT trop longue');
    parts.push(Buffer.from([b.length]), b);
  });
  return Buffer.concat(parts);
}

function decodeTxt(buf, start, end) {
  var entries = [];
  var o = start;
  while (o < end) {
    var l = buf[o++];
    if (o + l > end) throw new Error('TXT tronqué');
    if (l > 0) entries.push(buf.toString('utf8', o, o + l));
    o += l;
  }
  return entries;
}

/** Les entrées « clé=valeur » d'un TXT sous forme d'objet (clés en minuscules). */
function txtMap(entries) {
  var m = {};
  entries.forEach(function (e) {
    var i = e.indexOf('=');
    if (i < 0) m[lower(e)] = '';
    else m[lower(e.slice(0, i))] = e.slice(i + 1);
  });
  return m;
}

function encodeIPv4(ip) {
  var m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(String(ip));
  if (!m) throw new Error('adresse IPv4 invalide : ' + ip);
  var b = Buffer.alloc(4);
  for (var i = 0; i < 4; i++) {
    var n = Number(m[i + 1]);
    if (n > 255) throw new Error('adresse IPv4 invalide : ' + ip);
    b[i] = n;
  }
  return b;
}

function encodeRdata(r) {
  var d = r.data || {};
  switch (r.type) {
    case T.PTR: return encodeName(d.target);
    case T.SRV:
      var h = Buffer.alloc(6);
      h.writeUInt16BE(d.priority || 0, 0);
      h.writeUInt16BE(d.weight || 0, 2);
      h.writeUInt16BE(d.port, 4);
      return Buffer.concat([h, encodeName(d.target)]);
    case T.TXT: return encodeTxt(d.entries);
    case T.A: return encodeIPv4(d.address);
    default:
      if (Buffer.isBuffer(d.raw)) return d.raw;
      throw new Error('type d\'enregistrement non géré : ' + r.type);
  }
}

function encodeRecord(r) {
  var rdata = encodeRdata(r);
  var head = Buffer.alloc(10);
  head.writeUInt16BE(r.type, 0);
  head.writeUInt16BE(CLASS_IN | (r.flush ? FLUSH : 0), 2);
  head.writeUInt32BE(r.ttl >>> 0, 4);
  head.writeUInt16BE(rdata.length, 8);
  return Buffer.concat([encodeName(r.name), head, rdata]);
}

function encodeQuestion(q) {
  var tail = Buffer.alloc(4);
  tail.writeUInt16BE(q.type, 0);
  tail.writeUInt16BE((q.cls || CLASS_IN) | (q.unicast ? FLUSH : 0), 2);
  return Buffer.concat([encodeName(q.name), tail]);
}

/** m : { id, response, questions, answers, authorities, additionals }. Les noms ne sont pas compressés (valide, plus simple). */
function buildMessage(m) {
  var qs = (m.questions || []).map(encodeQuestion);
  var an = (m.answers || []).map(encodeRecord);
  var ns = (m.authorities || []).map(encodeRecord);
  var ar = (m.additionals || []).map(encodeRecord);
  var head = Buffer.alloc(12);
  head.writeUInt16BE((m.id || 0) & 0xffff, 0);
  head.writeUInt16BE(m.response ? 0x8400 : 0, 2);       // QR + AA pour une réponse
  head.writeUInt16BE(qs.length, 4);
  head.writeUInt16BE(an.length, 6);
  head.writeUInt16BE(ns.length, 8);
  head.writeUInt16BE(ar.length, 10);
  return Buffer.concat([head].concat(qs, an, ns, ar));
}

function parseQuestion(buf, off) {
  var n = decodeName(buf, off);
  if (n.next + 4 > buf.length) throw new Error('question tronquée');
  var cls = buf.readUInt16BE(n.next + 2);
  return {
    q: { name: n.name, type: buf.readUInt16BE(n.next), cls: cls & 0x7fff, unicast: (cls & FLUSH) !== 0 },
    next: n.next + 4
  };
}

function parseRecord(buf, off) {
  var n = decodeName(buf, off);
  var o = n.next;
  if (o + 10 > buf.length) throw new Error('enregistrement tronqué');
  var type = buf.readUInt16BE(o);
  var cls = buf.readUInt16BE(o + 2);
  var ttl = buf.readUInt32BE(o + 4);
  var len = buf.readUInt16BE(o + 8);
  o += 10;
  var end = o + len;
  if (end > buf.length) throw new Error('données tronquées');
  var data;
  if (type === T.A && len === 4) data = { address: buf[o] + '.' + buf[o + 1] + '.' + buf[o + 2] + '.' + buf[o + 3] };
  else if (type === T.PTR) data = { target: decodeName(buf, o).name };
  else if (type === T.SRV && len >= 7) {
    data = { priority: buf.readUInt16BE(o), weight: buf.readUInt16BE(o + 2), port: buf.readUInt16BE(o + 4), target: decodeName(buf, o + 6).name };
  }
  else if (type === T.TXT) data = { entries: decodeTxt(buf, o, end) };
  else data = { raw: Buffer.from(buf.slice(o, end)) };
  return { r: { name: n.name, type: type, cls: cls & 0x7fff, flush: (cls & FLUSH) !== 0, ttl: ttl, data: data }, next: end };
}

/** Décode un datagramme. Retourne null si le paquet est malformé (jamais d'exception : c'est de l'entrée réseau). */
function parseMessage(buf) {
  try {
    if (!Buffer.isBuffer(buf) || buf.length < 12) return null;
    var flags = buf.readUInt16BE(2);
    var counts = [buf.readUInt16BE(4), buf.readUInt16BE(6), buf.readUInt16BE(8), buf.readUInt16BE(10)];
    if (counts[0] + counts[1] + counts[2] + counts[3] > 512) return null;
    var msg = {
      id: buf.readUInt16BE(0), flags: flags, response: (flags & 0x8000) !== 0, opcode: (flags >> 11) & 0xf,
      questions: [], answers: [], authorities: [], additionals: []
    };
    var off = 12;
    var i, p;
    for (i = 0; i < counts[0]; i++) { p = parseQuestion(buf, off); msg.questions.push(p.q); off = p.next; }
    var lists = [msg.answers, msg.authorities, msg.additionals];
    for (var k = 0; k < 3; k++) {
      for (i = 0; i < counts[k + 1]; i++) { p = parseRecord(buf, off); lists[k].push(p.r); off = p.next; }
    }
    return msg;
  } catch (e) {
    return null;
  }
}

// ---------------------------------------------------------------------------------------------- le service annoncé

/**
 * svc : { instance, type ('_castbridge._tcp'), domain ('local' par défaut), host (une étiquette), port, txt: { clé: valeur } }.
 * Retourne les noms DNS du service.
 */
function names(svc) {
  var domain = svc.domain || 'local';
  var typeLabels = splitName(svc.type);
  return {
    service: joinLabels(typeLabels.concat([domain])),
    instance: joinLabels([svc.instance].concat(typeLabels, [domain])),
    host: joinLabels([svc.host, domain]),
    enumeration: ENUM_NAME
  };
}

/** Tous les enregistrements du service pour ces adresses IPv4. ttl : force le TTL (0 = au revoir). */
function records(svc, addresses, ttl) {
  var n = names(svc);
  var ttlHost = ttl === undefined ? TTL_HOST : ttl;
  var ttlOther = ttl === undefined ? TTL_OTHER : ttl;
  var entries = Object.keys(svc.txt || {}).map(function (k) { return k + '=' + svc.txt[k]; });
  return {
    names: n,
    enumPtr: { name: ENUM_NAME, type: T.PTR, ttl: ttlOther, flush: false, data: { target: n.service } },
    ptr: { name: n.service, type: T.PTR, ttl: ttlOther, flush: false, data: { target: n.instance } },
    srv: { name: n.instance, type: T.SRV, ttl: ttlHost, flush: true, data: { priority: 0, weight: 0, port: svc.port, target: n.host } },
    txt: { name: n.instance, type: T.TXT, ttl: ttlOther, flush: true, data: { entries: entries } },
    a: (addresses || []).map(function (ip) { return { name: n.host, type: T.A, ttl: ttlHost, flush: true, data: { address: ip } }; })
  };
}

function rdataKey(r) {
  var d = r.data || {};
  switch (r.type) {
    case T.PTR: return lower(d.target);
    case T.SRV: return [d.priority || 0, d.weight || 0, d.port, lower(d.target)].join('|');
    case T.TXT: return (d.entries || []).join('\u0000');
    case T.A: return d.address;
    default: return d.raw ? d.raw.toString('hex') : '';
  }
}

function recordKey(r) { return lower(r.name) + '|' + r.type + '|' + rdataKey(r); }

/**
 * Que répond-on à cette requête ? Retourne { answers, additionals, unicast } ou null (rien à dire).
 * Répond aux questions PTR du type de service, SRV/TXT de l'instance, A du nom d'hôte, à l'énumération
 * _services._dns-sd._udp.local et à ANY ; applique la suppression des réponses connues (RFC 6762 § 7.1).
 */
function respond(query, svc, addresses) {
  if (!query || query.response || query.opcode !== 0) return null;
  var R = records(svc, addresses);
  var n = R.names;
  var answers = [];
  var extra = [];
  var unicast = false;
  query.questions.forEach(function (q) {
    if (q.cls !== CLASS_IN && q.cls !== 255) return;
    var name = lower(q.name);
    var any = q.type === T.ANY;
    var hit = false;
    if (name === lower(n.enumeration)) {
      if (q.type === T.PTR || any) { answers.push(R.enumPtr); hit = true; }
    } else if (name === lower(n.service)) {
      if (q.type === T.PTR || any) { answers.push(R.ptr); extra.push(R.srv, R.txt); R.a.forEach(function (r) { extra.push(r); }); hit = true; }
    } else if (name === lower(n.instance)) {
      if (q.type === T.SRV || any) { answers.push(R.srv); R.a.forEach(function (r) { extra.push(r); }); hit = true; }
      if (q.type === T.TXT || any) { answers.push(R.txt); hit = true; }
    } else if (name === lower(n.host)) {
      if (q.type === T.A || any) { R.a.forEach(function (r) { answers.push(r); }); hit = true; }
    }
    if (hit && q.unicast) unicast = true;
  });
  // suppression des réponses connues : le demandeur détient déjà l'enregistrement avec au moins la moitié de son TTL
  var known = query.answers || [];
  var kept = [];
  var seen = {};
  answers.forEach(function (r) {
    var key = recordKey(r);
    if (seen[key]) return;
    seen[key] = true;
    var suppressed = known.some(function (k) { return recordKey(k) === key && k.ttl >= r.ttl / 2; });
    if (!suppressed) kept.push(r);
  });
  if (kept.length === 0) return null;
  var extras = [];
  extra.forEach(function (r) {
    var key = recordKey(r);
    if (seen[key]) return;
    seen[key] = true;
    extras.push(r);
  });
  return { answers: kept, additionals: extras, unicast: unicast };
}

// ---------------------------------------------------------------------------------------------- paquets à envoyer

/** Réponse multicast (id 0, sans question). */
function multicastResponse(out) {
  return buildMessage({ id: 0, response: true, answers: out.answers, additionals: out.additionals });
}

/** Réponse unicast « legacy » à un demandeur qui n'écrit pas depuis le port 5353 : id et questions recopiés, TTL plafonné. */
function legacyResponse(query, out) {
  var cap = function (r) { return { name: r.name, type: r.type, ttl: Math.min(r.ttl, LEGACY_TTL), flush: false, data: r.data }; };
  var echo = query.questions.map(function (q) { return { name: q.name, type: q.type, cls: q.cls, unicast: false }; });
  return buildMessage({ id: query.id, response: true, questions: echo, answers: out.answers.map(cap), additionals: out.additionals.map(cap) });
}

/** Annonce spontanée (goodbye = vrai : TTL 0, la TV disparaît aussitôt de la liste du téléphone). */
function announcement(svc, addresses, goodbye) {
  var R = records(svc, addresses, goodbye ? 0 : undefined);
  return buildMessage({ id: 0, response: true, answers: [R.ptr, R.srv, R.txt].concat(R.a) });
}

/** Une requête (pour les tests et les diagnostics). questions : [{ name, type, unicast }], known : enregistrements déjà connus. */
function buildQuery(questions, known) {
  return buildMessage({ id: 0, response: false, questions: questions, answers: known || [] });
}

module.exports = {
  T: T, PORT: PORT, ADDRESS: ADDRESS, ENUM_NAME: ENUM_NAME, TTL_HOST: TTL_HOST, TTL_OTHER: TTL_OTHER, LEGACY_TTL: LEGACY_TTL,
  splitName: splitName, joinLabels: joinLabels, encodeName: encodeName, decodeName: decodeName,
  encodeTxt: encodeTxt, decodeTxt: decodeTxt, txtMap: txtMap, buildMessage: buildMessage, parseMessage: parseMessage,
  names: names, records: records, respond: respond,
  multicastResponse: multicastResponse, legacyResponse: legacyResponse, announcement: announcement, buildQuery: buildQuery
};
