"use strict";
// Page de jeu Internet de CastBridge : WebSocket, puis SSE + POST, puis long-poll. Même protocole play-v1 (docs/PLAY-PROTOCOL.md) sur les trois.
// Le serveur décide de tout : le bandeau « Partie sûre » est AFFICHÉ tel que reçu (jamais calculé ici), les réponses ne sont jamais connues avant la clôture.
const $ = (t, a = {}, ...kids) => { const e = document.createElement(t);
  for (const [k, v] of Object.entries(a)) { if (k === "class") e.className = v; else if (k.startsWith("on")) e.addEventListener(k.slice(2), v); else if (v !== null && v !== undefined && v !== false) e.setAttribute(k, v === true ? "" : v); }
  for (const k of kids.flat()) if (k !== null && k !== undefined && k !== false) e.append(k.nodeType ? k : document.createTextNode(String(k)));
  return e; };
const app = document.getElementById("app"), who = document.getElementById("who"), safetyEl = document.getElementById("safety"), netEl = document.getElementById("net");
const show = (...kids) => app.replaceChildren(...kids.flat().filter(k => k !== null && k !== undefined && k !== false));
const L = ["A", "B", "C", "D"], SHAPES = ["▲", "◆", "●", "■"], GLYPH = { GREEN: "●", ORANGE: "▲", RED: "■", BLACK: "◯" };
const TRANSPORTS = ["ws", "sse", "poll"], TRANSPORT_NAME = { ws: "connexion directe", sse: "mode de repli SSE", poll: "mode de repli par requêtes" };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const store = { get(k) { try { return sessionStorage.getItem(k); } catch (e) { return null; } }, set(k, v) { try { v === null ? sessionStorage.removeItem(k) : sessionStorage.setItem(k, v); } catch (e) {} } };
const keep = { get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }, set(k, v) { try { localStorage.setItem(k, v); } catch (e) {} } };
let dev = keep.get("playDev");
if (!dev) { dev = Array.from(crypto.getRandomValues(new Uint8Array(8)), b => b.toString(16).padStart(2, "0")).join(""); keep.set("playDev", dev); }

// Un cookie de session de repli PAR ONGLET : le nonce (non secret) nomme le cookie `__Host-cbp-<nonce>` ; sessionStorage est propre à l'onglet
let tab = store.get("playTab");
if (!tab) { tab = Array.from(crypto.getRandomValues(new Uint8Array(8)), b => b.toString(16).padStart(2, "0")).join(""); store.set("playTab", tab); }
let session = null; try { session = JSON.parse(store.get("playSession") || "null"); } catch (e) {}   // { roomId, token, code, name } : le jeton reste en sessionStorage, jamais dans une adresse
let S = { view: null, role: null, opensAtLocal: 0, deadline: 0, timerTotal: 0, err: "", gone: null, myTooEarly: false };
let kind = 0, gen = 0, seq = 0, lastSeq = 0, conn = false, since = 0, link = null, pendingJoin = null, leaving = false, shortLives = 0, retries = 0;

function note(text) { netEl.textContent = text || ""; netEl.classList.toggle("hidden", !text); }
function save() { store.set("playSession", session ? JSON.stringify(session) : null); }
function fr(s) { return String(s || "").replace(/« /g, "« ").replace(/ »/g, " »").replace(/ ([?!:])/g, " $1"); }
function normCode(s) { return String(s || "").toUpperCase().replace(/[^0-9A-Z]/g, "").replace(/[IL]/g, "1").replace(/O/g, "0"); }
function prettyCode(c) { return c.length === 8 ? c.slice(0, 4) + "-" + c.slice(4) : c; }

// ---------- transports ----------
function stop() { gen++; if (link) { try { link.close(); } catch (e) {} } link = null; conn = false; since = 0; }
function start(idx) {
  stop(); kind = idx; const g = gen;
  const k = TRANSPORTS[kind];
  if (k === "ws" && !window.WebSocket) return fallback("Ce navigateur n'a pas de connexion directe");
  if (k === "sse" && !window.EventSource) return fallback("Ce navigateur n'a pas de flux SSE");
  if (k === "ws") openWs(g); else openFallback(g, k);
}
function fallback(why) {
  if (kind + 1 >= TRANSPORTS.length) { note(why + " : plus aucun mode de repli."); S.err = "Connexion impossible pour le moment. Réessayez plus tard."; render(); return; }
  note(why + " : passage en " + TRANSPORT_NAME[TRANSPORTS[kind + 1]] + ".");
  start(kind + 1);
}
function openWs(g) {
  const ws = new WebSocket((location.protocol === "https:" ? "wss://" : "ws://") + location.host + "/play/ws");
  let opened = false, at = 0;
  const timer = setTimeout(() => { if (!opened && g === gen) { ws.close(); fallback("La connexion directe n'a pas abouti"); } }, 5000);
  link = { send: m => { if (ws.readyState === 1) ws.send(JSON.stringify(m)); }, close: () => ws.close() };
  ws.onopen = () => { opened = true; at = performance.now(); clearTimeout(timer); if (g === gen) ready(); };
  ws.onmessage = e => { if (g === gen) handle(JSON.parse(e.data)); };
  ws.onclose = () => {
    clearTimeout(timer);
    if (g !== gen || leaving) return;
    if (!opened) return fallback("La connexion directe n'a pas abouti");
    shortLives = performance.now() - at < 60000 ? shortLives + 1 : 0;
    if (shortLives >= 2 || ++retries > 3) { retries = 0; return fallback("La connexion directe est coupée (un relais l'interrompt peut-être)"); }
    note("Connexion coupée, reprise…"); setTimeout(() => { if (g === gen && !leaving) start(0); }, 800 * retries);
  };
}
let chain = Promise.resolve();
const post = (g, m) => { chain = chain.then(() => doPost(g, m)); return chain; };   // un message à la fois : le premier crée la session de repli
async function doPost(g, m) {
  try {
    const r = await fetch("/play/act?tab=" + tab, { method: "POST", cache: "no-store", headers: { "Content-Type": "application/json" }, credentials: "same-origin", body: JSON.stringify(m) });
    if (g !== gen) return;
    if (r.status === 410) { conn = false; since = 0; if (session) { await sleep(300); if (g === gen) { ready(); } } return; }   // session de repli terminée : reprise avec le jeton
    if (r.status === 429) { S.err = "Trop de messages ou de connexions : patientez un instant."; render(); return; }
    const b = await r.json();
    if (!conn && b.ok) { conn = true; if (TRANSPORTS[kind] === "sse") openStream(g); else pollLoop(g); }   // le secret de session est un cookie HttpOnly posé par le service : la page ne le voit jamais
  } catch (e) { note("Réseau indisponible, nouvel essai…"); }
}
function openFallback(g, k) {
  link = { send: m => post(g, m), close: () => { if (link && link.es) link.es.close(); } };
  ready();
}
function openStream(g) {
  const es = new EventSource("/play/events?tab=" + tab);
  link.es = es;
  for (const t of ["welcome", "state", "question", "reveal", "safety", "ping", "error", "roomGone", "replay", "ack"]) es.addEventListener(t, ev => { if (g === gen) handle(JSON.parse(ev.data)); });
  es.onerror = () => { if (g !== gen) return; es.close(); conn = false; note("Flux interrompu, reprise…"); setTimeout(() => { if (g === gen && !leaving) ready(); }, 1000); };
}
async function pollLoop(g) {
  let fails = 0;
  while (g === gen && conn) {
    try {
      const r = await fetch("/play/state?tab=" + tab + "&since=" + since, { cache: "no-store", credentials: "same-origin" });
      if (g !== gen) return;
      if (r.status === 410) { conn = false; since = 0; await sleep(300); if (g === gen) ready(); return; }
      if (r.status !== 200) throw new Error("http " + r.status);
      const b = await r.json(); fails = 0; since = b.next; for (const m of b.msgs) handle(m);
    } catch (e) { if (++fails > 5) { note("Réseau indisponible."); fails = 0; } await sleep(2000); }
  }
}
const sendMsg = m => { if (link) link.send(m); };

// ---------- protocole ----------
function ready() {
  sendMsg({ t: "hello", proto: 1, caps: ["play1", "sse", "longpoll"], deviceHash: dev });
  if (session && session.token) sendMsg({ t: "resume", roomId: session.roomId, token: session.token, lastSeq });
  else if (pendingJoin) sendMsg({ t: "join", code: pendingJoin.code, name: pendingJoin.name, deviceHash: dev, spectate: false });
}
function handle(m) {
  switch (m.t) {
    case "welcome": retries = 0; note(kind > 0 ? "Vous jouez en " + TRANSPORT_NAME[TRANSPORTS[kind]] + "." : "");
      S.role = m.role; session = { roomId: m.roomId, token: m.token, code: m.code, name: (session && session.name) || (pendingJoin && pendingJoin.name) || "" }; pendingJoin = null; S.err = ""; save(); break;
    case "state": lastSeq = m.seq; onState(m.view); break;
    case "question": S.opensAtLocal = performance.now() + Math.max(0, m.opensAtServerMs - m.serverNowMs); S.qIndex = m.index; render(); break;
    case "reveal": break;
    case "safety": showSafety(m); break;
    case "ping": sendMsg({ t: "pong", id: m.id }); break;
    case "ack": if (m.result === "TOO_EARLY") { S.err = "Trop tôt : attendez le départ de la question."; render(); } else if (m.result === "OK" || m.result === "SAME") S.err = ""; break;
    case "error": onError(m); break;
    case "roomGone": S.gone = m.reason; session = null; save(); stop(); render(); break;
    case "replay": break;
  }
}
function showSafety(m) {
  safetyEl.className = "lv-" + m.level; safetyEl.textContent = "";
  safetyEl.append($("span", { "aria-hidden": "true" }, GLYPH[m.level] || "●"), $("span", {}, m.word + " — " + fr(m.text)));
}
function onError(m) {
  if (session && (m.reason === "PLAY_BAD_CODE" || m.reason === "PLAY_ROOM_GONE")) { session = null; save(); S.view = null; }
  if (m.reason === "PLAY_BANNED") { session = null; save(); stop(); }
  S.err = m.message || "Une erreur est survenue."; render();
}
function onState(v) {
  S.view = v; S.err = S.err && /Trop tôt/.test(S.err) ? S.err : "";
  const d = v.duel, wait = v.timing && v.timing.waitMs ? v.timing.waitMs : 0, now = performance.now();
  if (d && d.phase === "QUESTION") { S.opensAtLocal = now + wait; S.deadline = now + wait + (wait > 0 ? d.questionMs : d.remainingMs); S.timerTotal = d.questionMs; }
  else { S.opensAtLocal = 0; S.deadline = d && d.remainingMs > 0 ? now + d.remainingMs : 0; S.timerTotal = d ? d.remainingMs : 0; }
  render();
}
function answer(i) {
  const d = S.view && S.view.duel; if (!d) return;
  sendMsg({ t: "act", action: "answer", questionId: d.question.id, choice: i, seq: ++seq });
}
function leave() {
  if (!confirm("Quitter la salle ?")) return;
  leaving = true; session = null; save(); stop(); S.view = null; S.gone = null; leaving = false; safetyEl.classList.add("hidden"); note(""); render();
}

// ---------- écrans ----------
function renderJoin() {
  who.textContent = "";
  const fromUrl = (location.pathname.match(/\/play\/j\/([^/]+)/) || [])[1] || "";
  const code = $("input", { id: "code", type: "text", maxlength: 12, autocomplete: "off", autocapitalize: "characters", spellcheck: "false", placeholder: "XXXX-XXXX", value: prettyCode(normCode(fromUrl)) });
  const name = $("input", { id: "name", type: "text", maxlength: 16, autocomplete: "nickname", placeholder: "Votre pseudonyme", value: keep.get("playName") || "" });
  const age = $("input", { id: "age", type: "checkbox" });
  const msg = $("p", { class: "err", role: "alert" }, S.err);
  const go = $("button", { class: "primary", onclick: join }, "Rejoindre la partie");
  code.addEventListener("input", () => { code.value = prettyCode(normCode(code.value).slice(0, 8)); });
  function join() {
    const c = normCode(code.value), n = name.value.trim();
    if (c.length !== 8) { msg.textContent = "Le code de salle a 8 caractères (XXXX-XXXX), affiché sur la TV."; return; }
    if (!n) { msg.textContent = "Choisissez un pseudonyme (pas votre vrai nom)."; return; }
    if (!age.checked) { msg.textContent = "Cochez la case : il faut avoir 13 ans ou plus, ou être accompagné d'un parent."; return; }
    S.err = ""; msg.textContent = ""; keep.set("playName", n); pendingJoin = { code: c, name: n }; leaving = false; S.gone = null;
    note("Connexion…"); start(0);
  }
  show($("h1", {}, "Rejoindre une partie en ligne"),
    $("p", { class: "muted" }, "Entrez le code de la salle affiché sur CastBridge-TV, puis un pseudonyme. Rien d'autre n'est demandé."),
    $("div", {}, $("label", { for: "code" }, "Code de la salle"), code),
    $("div", {}, $("label", { for: "name" }, "Pseudonyme"), name),
    $("label", { class: "age", for: "age" }, age, "J'ai 13 ans ou plus, ou un parent m'accompagne."),
    msg, go);
  (code.value ? name : code).focus();
}
function timerBar() { return $("div", { class: "bar" }, $("i", { id: "tbar" })); }
function tick() {
  const now = performance.now(), b = document.getElementById("tbar"), t = document.getElementById("tsec"), g = document.getElementById("gap");
  const left = S.deadline ? Math.max(0, S.deadline - now) : 0, wait = S.opensAtLocal ? Math.max(0, S.opensAtLocal - now) : 0;
  if (b) b.style.width = (S.timerTotal > 0 && !wait ? Math.min(100, left / S.timerTotal * 100) : 100) + "%";
  if (t) t.textContent = S.deadline && !wait ? Math.ceil(left / 1000) + " s" : "";
  if (g) { if (wait > 0) g.textContent = "Question suivante dans " + (Math.ceil(wait / 100) / 10).toFixed(1).replace(".", ",") + " s"; else { g.textContent = ""; if (g.dataset.on === "1") { g.dataset.on = "0"; render(); } } if (wait > 0) g.dataset.on = "1"; }
}
function answers(q, o) {
  return $("div", { class: "grid" }, q.choices.map((c, i) => {
    const cls = ["ans", L[i]];
    if (o.selected === i) cls.push("sel");
    const known = o.answer !== null && o.answer !== undefined;
    if (known && i !== o.answer && o.selected !== i) cls.push("dim");
    const mark = known ? (i === o.answer ? "✓" : (o.selected === i ? "✗" : "")) : "";
    return $("button", { class: cls.join(" "), disabled: o.disabled, "aria-label": L[i] + " : " + c, onclick: () => o.onPick && o.onPick(i) },
      $("span", { class: "l" }, SHAPES[i] + " " + L[i]), $("span", { class: "t" }, fr(c)), mark ? $("span", { class: "mark" }, mark) : null);
  }));
}
function ranking(d, me) {
  return $("ul", { class: "pl" }, d.ranking.map(r => $("li", { class: me && r.id === me.id ? "me" : "" }, $("span", {}, r.rank + ". " + r.name), $("span", {}, r.score + " pts" + (r.gained ? "  (+" + r.gained + ")" : "")))));
}
function render() {
  const v = S.view;
  if (S.gone) {
    who.textContent = "";
    const why = { EXPIRED: "La salle a expiré.", HOST_CLOSED_INTERNET: "L'hôte a fermé la partie aux joueurs à distance.", HOST_LOST: "CastBridge-TV ne répond plus : la partie est interrompue.", IDLE: "La salle est restée vide trop longtemps." }[S.gone] || "La salle est fermée.";
    return show($("div", { class: "card center" }, $("h1", {}, "Partie terminée"), $("p", { class: "muted" }, why)), $("button", { class: "ghost", onclick: () => { S.gone = null; render(); } }, "Rejoindre une autre partie"));
  }
  if (!session && !v) return renderJoin();
  if (!v) return show($("p", { class: "muted center" }, "Connexion à la salle " + (session ? prettyCode(session.code) : "") + "…"), S.err ? $("p", { class: "err" }, S.err) : null);
  const me = v.me, room = v.room || {}, spectator = S.role === "SPECTATOR" || !me;
  who.textContent = (me ? me.name + (v.duel ? " · " + me.score + " pts" : "") : "Spectateur") + (room.code ? " · " + room.code : "");
  const tail = [S.err ? $("p", { class: "err", role: "alert" }, S.err) : null, $("button", { class: "ghost", onclick: leave }, "Quitter la salle")];
  if (v.stage === "LOBBY" || !v.duel) return show($("div", { class: "card center" }, $("h2", {}, spectator ? "Vous regardez la partie" : "Vous êtes dans la salle ✓"),
    $("p", { class: "muted" }, v.game ? "Cette partie (Millionnaire) se joue sur CastBridge-TV." : "En attente du lancement sur CastBridge-TV…")),
    $("h2", {}, "Joueurs (" + v.players.length + "/" + v.maxPlayers + ")"),
    $("ul", { class: "pl" }, v.players.map(p => $("li", { class: me && p.id === me.id ? "me" : "" }, $("span", {}, (p.connected ? "● " : "○ ") + p.name), $("span", { class: "muted" }, p.connected ? "" : "hors ligne")))), ...tail);
  renderDuel(v, spectator, tail);
}
function renderDuel(v, spectator, tail) {
  const d = v.duel, q = d.question, me = v.me;
  const head = $("div", { class: "row" }, $("span", { class: "pill" }, "Question " + (d.index + 1) + "/" + d.count), $("span", { class: "pill center" }, q.category || ""), $("span", { class: "pill", id: "tsec", style: "text-align:right" }));
  if (d.phase === "QUESTION") {
    const waiting = S.opensAtLocal && performance.now() < S.opensAtLocal, mine = d.myAnswer, done = mine !== null && mine !== undefined;
    return show(head, $("p", { class: "gap", id: "gap", "aria-live": "polite", "data-on": waiting ? "1" : "0" }), timerBar(), $("div", { class: "card q" }, fr(q.text)),
      answers(q, { selected: mine, disabled: spectator || done || waiting, onPick: answer }),
      $("p", { class: "muted center" }, spectator ? "Vous regardez : seuls les joueurs répondent." : done ? "Réponse " + L[mine] + " envoyée ✓ — attendez les autres (" + d.answeredCount + "/" + v.players.length + ")" : waiting ? "Tout le monde démarre ensemble : lisez la question." : "Touchez votre réponse : plus vous êtes rapide, plus vous gagnez de points."), ...tail);
  }
  const mine = me ? d.ranking.find(r => r.id === me.id) : null, o = d.outcome;
  if (d.phase === "REVEAL") return show(head, $("div", { class: "card center" }, o ? $("div", { class: "big " + (o.correct ? "ok" : "bad") }, o.correct ? "✓ +" + o.points : (d.myAnswer === null || d.myAnswer === undefined ? "⏱ Trop tard" : "✗ Raté")) : null,
    $("p", {}, "Bonne réponse : " + L[q.answer] + " — " + q.choices[q.answer]), $("p", { class: "muted" }, q.explanation || "")), answers(q, { selected: d.myAnswer, answer: q.answer, disabled: true }), ...tail);
  if (d.phase === "BOARD") return show(head, $("h2", {}, "Classement"), mine ? $("p", { class: "big" }, mine.rank + (mine.rank === 1 ? "er" : "e")) : null, ranking(d, me), $("p", { class: "muted center" }, "Question suivante dans un instant…"), ...tail);
  show($("div", { class: "card center" }, $("h1", {}, "🏆 Fin du duel"), mine ? $("p", { class: "big" }, mine.rank === 1 ? "Victoire !" : mine.rank + "e place") : null, mine ? $("p", {}, mine.score + " points") : null),
    ranking(d, me), $("p", { class: "muted center" }, "L'hôte peut relancer une partie : restez connecté."), ...tail);
}

setInterval(tick, 200);
if (session && session.token) { who.textContent = ""; start(0); render(); } else render();
