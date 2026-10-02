#!/usr/bin/env python3
"""Sélection automatique de l'édition d'essai CastBridge (docs/TRIAL-EDITION.md).

    trial_edition.py select [--repo .] [--out content/TRIAL-MANIFEST.json] [--only learn,quiz,langues] [--previous FILE] [--draft]
    trial_edition.py check  [--repo .] [--manifest content/TRIAL-MANIFEST.json] [--only ...]   # le manifeste correspond-il au contenu ?
    trial_edition.py build  [--repo .] --out DIR [--only ...]                                   # fabrique les lots <lot>-trial (zip)

Python 3.8+, bibliothèque standard seulement. Déterministe : aucune horloge, aucun hasard (l'ordre « aléatoire » est un hachage
SHA-256 de l'identifiant), même contenu + même configuration + même manifeste précédent = même manifeste, octet pour octet.

Un échec (code 1) : plafond de 100 Mo dépassé, sous-catégorie vide, plancher qui ne tient pas, lot d'essai trop gros.
"""
import argparse
import glob
import hashlib
import io
import json
import os
import sys
import zipfile

TOOL_VERSION = 1
HERE = os.path.dirname(os.path.abspath(__file__))
TIER_RANK = {"application": 1, "approfondissement": 2, "examen": 3, "autoeval": 0}


def jb(o):
    return json.dumps(o, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def sha(s):
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


class Fail(Exception):
    """Une règle dure n'est pas respectée (le manifeste n'est pas écrit comme valide)."""


class Item:
    """Une unité sélectionnable : leçon, exercice, question de quiz, unité de langue, média."""
    __slots__ = ("cat", "sub", "kind", "id", "lot", "cost", "opens", "a", "obj")

    def __init__(self, cat, sub, kind, id_, lot, cost, opens=(), obj=None, **a):
        self.cat, self.sub, self.kind, self.id, self.lot, self.cost = cat, sub, kind, id_, lot, cost
        self.opens, self.obj, self.a = tuple(opens), obj, a

    @property
    def uid(self):
        return "%s/%s/%s" % (self.cat, self.kind, self.id)

    @property
    def order_hash(self):
        return sha(self.uid)


class Inventory:
    def __init__(self):
        self.items = {}      # uid -> Item
        self.subs = {}       # sub key -> {"cat","tier","label","scope"}
        self.lots = {}       # (feature, scope) -> {"title", "cat"}
        self.scopes = {}     # classe/niveau -> titre (la « classe » du cahier)
        self.packs = {}      # pack id -> pack.json
        self.extra = {}      # données de projection par lot

    def add_sub(self, key, cat, tier, label, scope=None):
        self.subs.setdefault(key, {"cat": cat, "tier": tier, "label": label, "scope": scope})

    def add(self, it):
        if it.uid in self.items:
            raise Fail("identifiant en double : " + it.uid)
        self.items[it.uid] = it

    def of_sub(self, key):
        return sorted((i for i in self.items.values() if i.sub == key), key=lambda i: i.uid)

    def fingerprint(self):
        h = hashlib.sha256()
        for uid in sorted(self.items):
            i = self.items[uid]
            h.update(("%s|%d|%s\n" % (uid, i.cost, i.sub)).encode("utf-8"))
        for k in sorted(self.subs):
            h.update(("sub|%s|%s\n" % (k, self.subs[k]["tier"])).encode("utf-8"))
        return h.hexdigest()


# ------------------------------------------------------------------------------------------------ inventaire

def is_exam_scope(cfg, scope):
    return scope in cfg["examScopes"] or any(scope.startswith(p) for p in cfg["examScopePrefixes"])


def scan_learn(inv, repo, cfg):
    base = os.path.join(repo, "content", "learn")
    scopes_file = os.path.join(base, "scopes.txt")
    if not os.path.isfile(scopes_file):
        return
    pack_scope = {}
    with open(scopes_file, encoding="utf-8") as sf:
        scope_lines = sf.read().splitlines()
    for line in scope_lines:
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        scope, title, packs = [p.strip() for p in line.split("|", 2)]
        inv.scopes[scope] = title
        for p in packs.split():
            pack_scope[p] = scope
        inv.lots[("learn", scope)] = {"title": title, "cat": "learn"}
    for pdir in sorted(glob.glob(os.path.join(base, "*", "pack.json"))):
        pack = load(pdir)
        pid = pack["id"]
        scope = pack_scope.get(pid)
        if scope is None:
            continue   # un pack sans lot : signalé par build-learn-lots, pas par cet outil
        subject = pack.get("subject") or "divers"
        sub = "learn/%s/%s" % (scope, subject)
        inv.add_sub(sub, "learn", 0 if is_exam_scope(cfg, scope) else 1, "%s · %s" % (inv.scopes[scope], subject), scope)
        inv.packs[pid] = pack
        chap_order = {c["id"]: c.get("order", n) for n, c in enumerate(pack.get("chapters", []))}
        pack_cost = len(jb(pack))
        for lf in sorted(glob.glob(os.path.join(os.path.dirname(pdir), "lessons", "*.json"))):
            rel = "%s/lessons/%s" % (pid, os.path.basename(lf))
            data = load(lf)
            chapter = data.get("chapter")
            opens = (("pack:" + pid, pack_cost), ("file:" + rel, 120))
            exs = {e["id"]: e for e in data.get("exercises", [])}
            for n, les in enumerate(data.get("lessons", [])):
                linked = [exs[x] for x in les.get("exercises", []) if x in exs]
                hard = max([int(e.get("difficulty", 1)) * 10 + TIER_RANK.get(e.get("tier"), 0) for e in linked] or [0])
                blocks = les.get("blocks", [])
                fig = any(b.get("type") == "illustration" and b.get("figure") for b in blocks)
                anim = any(b.get("type") == "illustration" and b.get("animation") for b in blocks)
                inv.add(Item("learn", sub, "lesson", les["id"], ("learn", scope), len(jb(les)), opens, obj=les,
                             order=(chap_order.get(chapter, 999), n), hard=hard, fig=fig, anim=anim, pack=pid, file=rel,
                             chapter=chapter, linked=[e["id"] for e in linked]))
            for e in data.get("exercises", []):
                inv.add(Item("learn", sub, "exercise", e["id"], ("learn", scope), len(jb(e)), opens, obj=e,
                             tier=e.get("tier", "application"), diff=int(e.get("difficulty", 1)), expl=bool(e.get("explanation")),
                             lesson=e.get("lesson"), pack=pid, file=rel, chapter=chapter))
            inv.extra.setdefault(("file", rel), {"chapter": chapter, "pack": pid})


def scan_quiz(inv, repo, cfg):
    cat = os.path.join(repo, "content", "quiz", "lots", "catalog-lots.json")
    if not os.path.isfile(cat):
        return
    catalog = load(cat)
    for lot in catalog["lots"]:
        scope = lot["scope"]
        zpath = os.path.join(repo, "content", "quiz", "lots", lot["file"])
        if not os.path.isfile(zpath):
            continue
        sub = "quiz/" + scope
        inv.add_sub(sub, "quiz", 0 if is_exam_scope(cfg, scope) else 1, lot["title"], scope)
        inv.lots[("quiz", scope)] = {"title": lot["title"], "cat": "quiz"}
        with zipfile.ZipFile(zpath) as z:
            qs = json.loads(z.read("questions.json").decode("utf-8"))["questions"]
            index = json.loads(z.read("index.json").decode("utf-8"))
            manifest = json.loads(z.read("manifest.json").decode("utf-8"))
        inv.extra[("quiz", scope)] = {"index": index, "manifest": manifest, "lot": lot}
        fixed = len(jb(manifest)) + 400     # manifest.json + en-tête d'index.json + marge
        for n, q in enumerate(qs):
            # + l'entrée de la question dans index.json ("id":"hash8",)
            inv.add(Item("quiz", sub, "question", q["id"], ("quiz", scope), len(jb(q)) + 1 + len(q["id"]) + 14, (("lot", fixed),), obj=q,
                         diff=int(q.get("difficulty", 1)), pos=n))


def scan_langues(inv, repo, cfg):
    base = os.path.join(repo, "content", "langues")
    budget = os.path.join(base, "budget.json")
    languages = list(cfg["expectedLanguages"])
    levels = list(cfg["levels"]["languageLevels"])
    if os.path.isfile(budget):
        b = load(budget)
        levels = b.get("levels", levels)
        languages = sorted(b.get("languageWeightsPercent", {}).keys()) or languages
    for lang in sorted(languages):
        for lv in levels:
            sub = "langues/%s/%s" % (lang, lv.upper())
            inv.add_sub(sub, "langues", 1, "%s %s" % (lang, lv.upper()), lang)
            inv.lots[("langues", "%s-%s" % (lang, lv.lower()))] = {"title": "Langues %s %s" % (lang, lv.upper()), "cat": "langues"}
            inv.lots[("langmedia", "%s-%s" % (lang, lv.lower()))] = {"title": "Médias %s %s" % (lang, lv.upper()), "cat": "langues"}
    for ldir in sorted(glob.glob(os.path.join(base, "*", "langue.json"))):
        pk = load(ldir)
        lang, lv = pk["target"], pk["level"].upper()
        sub = "langues/%s/%s" % (lang, lv)
        if sub not in inv.subs:
            continue
        cell = "%s-%s" % (lang, lv.lower())
        pack_dir = os.path.dirname(ldir)
        pack_cost = len(jb({k: v for k, v in pk.items() if k != "units"}))
        opens = (("pack:" + pk["id"], pack_cost),)
        media = {}
        mpath = os.path.join(pack_dir, "media.json")
        if os.path.isfile(mpath):
            for m in load(mpath).get("media", []):
                media[m["id"]] = m
        for n, u in enumerate(pk.get("units", [])):
            exs = u.get("exercises", [])
            inv.add(Item("langues", sub, "unit", u["id"], ("langues", cell), len(jb(u)), opens, obj=u, order=n, pack=pk["id"],
                         nex=len(exs), anim=bool(u.get("animations")), refs=sorted(_media_refs(u))))
        for mid, m in sorted(media.items()):
            if m.get("license") not in cfg["allowedLicenses"]:
                inv.extra.setdefault("unlicensed", []).append(mid)
                continue
            inv.add(Item("langues", sub, "media", pk["id"] + ":" + mid, ("langmedia", cell), int(m["bytes"]),
                         (("lot", 400),), obj=m, mkind=m.get("kind"), ms=int(m.get("durationMs", 0)), mid=mid, pack=pk["id"]))
        inv.extra[("pack", pk["id"])] = {"pack": {k: v for k, v in pk.items() if k != "units"}, "dir": pack_dir}


def _media_refs(o):
    out = set()
    if isinstance(o, dict):
        for k, v in o.items():
            if k == "audio" and isinstance(v, str) and v.startswith("m:"):
                out.add(v[2:])
            else:
                out |= _media_refs(v)
    elif isinstance(o, list):
        for v in o:
            out |= _media_refs(v)
    return out


def scan(repo, cfg, only=None):
    inv = Inventory()
    for cat, fn in (("learn", scan_learn), ("quiz", scan_quiz), ("langues", scan_langues)):
        if only is None or cat in only:
            fn(inv, repo, cfg)
    return inv


# ------------------------------------------------------------------------------------------------ sélection

class Selection:
    def __init__(self, inv, cfg):
        self.inv, self.cfg = inv, cfg
        self.chosen = {}          # uid -> raison ("plancher:intro", "remplissage", "précédent")
        self.opened = set()       # (lot, clé fixe)
        self.total = 0
        self.lot_bytes = {}
        self.sub_bytes = {}
        self.cat_fill = {}
        self.roles = {}
        self.warnings = []

    def lot_limit(self, lot):
        return self.cfg["mediaLotMaxBytes"] if lot[0] == "langmedia" else self.cfg["textLotMaxBytes"]

    def price(self, it):
        return it.cost + sum(c for k, c in it.opens if (it.lot, k) not in self.opened)

    def add(self, it, why, limit):
        if it.uid in self.chosen:
            return True
        p = self.price(it)
        if self.total + p > limit or self.lot_bytes.get(it.lot, 0) + p > self.lot_limit(it.lot):
            return False
        self.chosen[it.uid] = why
        self.total += p
        self.lot_bytes[it.lot] = self.lot_bytes.get(it.lot, 0) + p
        self.sub_bytes[it.sub] = self.sub_bytes.get(it.sub, 0) + p
        for k, _ in it.opens:
            self.opened.add((it.lot, k))
        return True

    def role(self, sub, role, it, floor_limit):
        if it is None:
            return
        if not self.add(it, "plancher:" + role, floor_limit):
            raise Fail("le plancher « %s » de %s ne tient pas (plafond %d octets ou lot %s trop gros)" % (role, sub, floor_limit, it.lot))
        self.roles.setdefault(sub, {})[role] = it.id


def by_hash(items):
    return sorted(items, key=lambda i: (i.order_hash, i.uid))


def floors(sel, sub, items, cfg, limit):
    f = cfg["floors"]
    cat = sel.inv.subs[sub]["cat"]
    if cat == "learn":
        lessons = sorted([i for i in items if i.kind == "lesson"], key=lambda i: (i.a["order"], i.id))
        exs = [i for i in items if i.kind == "exercise"]
        intro = lessons[0] if lessons else None
        rest = [l for l in lessons if l is not intro]
        hard = max(rest, key=lambda l: (l.a["hard"], -l.a["order"][0], l.id), default=None) if rest else None
        sel.role(sub, "introduction", intro, limit)
        sel.role(sub, "difficile", hard, limit)
        has_fig = any(l.a["fig"] for l in (intro, hard) if l)
        if not has_fig:
            figs = [l for l in lessons if l.a["fig"]]
            sel.role(sub, "figure", min(figs, key=lambda l: (not l.a["anim"], l.cost, l.id), default=None), limit)
        picked = [l for l in (intro, hard) if l]
        linked = [x for l in picked for x in l.a["linked"]]
        cand = [e for e in exs if e.a["expl"]]
        cand.sort(key=lambda e: (e.id not in linked, -TIER_RANK.get(e.a["tier"], 0), e.order_hash))
        chosen, tiers = [], set()
        for e in cand:                       # d'abord un exercice de chaque niveau, puis on complète
            if len(chosen) >= f["learnExercises"]:
                break
            if e.a["tier"] not in tiers:
                chosen.append(e)
                tiers.add(e.a["tier"])
        for e in cand:
            if len(chosen) >= f["learnExercises"]:
                break
            if e not in chosen:
                chosen.append(e)
        if lessons and not chosen:
            sel.warnings.append("%s : aucun exercice corrigé disponible" % sub)
        for n, e in enumerate(chosen):
            sel.role(sub, "exercice%d" % (n + 1), e, limit)
    elif cat == "quiz":
        buckets = {}
        for q in items:
            buckets.setdefault(q.a["diff"], []).append(q)
        for d in sorted(buckets):
            for n, q in enumerate(by_hash(buckets[d])[:f["quizPerDifficulty"]]):
                sel.role(sub, "N%d-%d" % (d - 1, n + 1), q, limit)
    else:
        units = sorted([i for i in items if i.kind == "unit"], key=lambda i: (i.a["order"], i.id))
        media = [i for i in items if i.kind == "media"]
        intro = units[0] if units else None
        rest = [u for u in units if u is not intro]
        hard = max(rest, key=lambda u: (u.a["nex"], -u.a["order"], u.id), default=None)
        sel.role(sub, "introduction", intro, limit)
        sel.role(sub, "difficile", hard, limit)
        if not any(u.a["anim"] for u in (intro, hard) if u):
            anim = [u for u in units if u.a["anim"]]
            sel.role(sub, "figure", min(anim, key=lambda u: (u.cost, u.id), default=None), limit)
        audio = [m for m in media if m.a["mkind"] == "audio" and m.a["ms"] <= f["audioMaxMs"] and m.cost <= f["audioMaxBytes"]]
        used = {x for u in units if u.uid in sel.chosen for x in u.a["refs"]}
        audio.sort(key=lambda m: (m.a["mid"] not in used, m.cost, m.id))
        sel.role(sub, "audio", audio[0] if audio else None, limit)
        if not audio:
            sel.warnings.append("%s : aucun audio court sous licence admise (lot média à produire)" % sub)


def videos(sel, limit):
    """Au moins une vidéo courte par langue ET par niveau (couverture croisée, la plus petite d'abord)."""
    inv, f = sel.inv, sel.cfg["floors"]
    vids = [i for i in inv.items.values() if i.cat == "langues" and i.kind == "media" and i.a["mkind"] == "video"
            and i.a["ms"] <= f["videoMaxMs"] and i.cost <= f["videoMaxBytes"]]
    if not vids:
        if any(i.cat == "langues" for i in inv.items.values()):
            sel.warnings.append("langues : aucune vidéo courte disponible (vague 4 de production)")
        return
    langs = sorted({i.sub.split("/")[1] for i in vids})
    levels = sorted({i.sub.split("/")[2] for i in vids})
    have = lambda pos, v: any(sel.chosen.get(i.uid) and i.sub.split("/")[pos] == v for i in vids)
    for pos, values in ((2, levels), (1, langs)):
        for v in values:
            if have(pos, v):
                continue
            cands = sorted([i for i in vids if i.sub.split("/")[pos] == v],
                           key=lambda i: (i.cost, i.uid))
            if cands:
                sel.role(cands[0].sub, "video", cands[0], limit)


def fill_queue(sel, sub, items):
    """Ordre de remplissage d'une sous-catégorie (déterministe, équilibré)."""
    cat = sel.inv.subs[sub]["cat"]
    rest = [i for i in items if i.uid not in sel.chosen]
    if cat == "learn":
        les = sorted([i for i in rest if i.kind == "lesson"], key=lambda i: (i.a["order"], i.id))
        exs = sorted([i for i in rest if i.kind == "exercise" and i.a["expl"]], key=lambda e: (-TIER_RANK.get(e.a["tier"], 0), e.order_hash))
        out, a, b = [], 0, 0
        while a < len(les) or b < len(exs):
            if a < len(les):
                out.append(les[a]); a += 1
            for _ in range(3):
                if b < len(exs):
                    out.append(exs[b]); b += 1
        return out
    if cat == "quiz":
        buckets = {}
        for q in by_hash(rest):
            buckets.setdefault(q.a["diff"], []).append(q)
        out, n = [], 0
        while any(len(v) > n for v in buckets.values()):
            for d in sorted(buckets):
                if len(buckets[d]) > n:
                    out.append(buckets[d][n])
            n += 1
        return out
    units = sorted([i for i in rest if i.kind == "unit"], key=lambda i: (i.a["order"], i.id))
    audio = sorted([i for i in rest if i.kind == "media" and i.a["mkind"] == "audio"], key=lambda i: (i.cost, i.id))
    out, a, b = [], 0, 0
    while a < len(units) or b < len(audio):
        if a < len(units):
            out.append(units[a]); a += 1
        for _ in range(2):
            if b < len(audio):
                out.append(audio[b]); b += 1
    return out


def select(inv, cfg, previous=frozenset()):
    sel = Selection(inv, cfg)
    cap = cfg["capBytes"]
    limit = cap - cfg["reserveBytes"]
    by_sub = {k: inv.of_sub(k) for k in inv.subs}
    for sub in sorted(inv.subs):
        if by_sub[sub]:
            floors(sel, sub, by_sub[sub], cfg, cap)
    videos(sel, cap)
    floor_total = sel.total
    remaining = max(0, limit - floor_total)
    cat_cap = {c: int(cfg["categoryFillShare"].get(c, 0.3) * remaining) for c in {s["cat"] for s in inv.subs.values()}}
    full = {k: sum(i.cost for i in v) for k, v in by_sub.items()}
    share_cap = {k: int(cfg["maxShareOfFull"] * full[k]) for k in by_sub}

    def fill_one(it, why):
        cat = it.cat
        p = sel.price(it)
        if sel.sub_bytes.get(it.sub, 0) + p > max(share_cap[it.sub], sel.sub_bytes.get(it.sub, 0)):
            return "share"
        if sel.cat_fill.get(cat, 0) + p > cat_cap[cat]:
            return "cat"
        if sel.add(it, why, limit):
            sel.cat_fill[cat] = sel.cat_fill.get(cat, 0) + p
            return "ok"
        return "cap"

    pinned = sorted(u for u in previous if u in inv.items and u not in sel.chosen)   # stabilité : on garde ce qui était déjà dans l'essai
    for uid in pinned:
        fill_one(inv.items[uid], "précédent")
    queues = {k: fill_queue(sel, k, by_sub[k]) for k in by_sub}
    for tier in (0, 1):
        subs = sorted(k for k in by_sub if inv.subs[k]["tier"] == tier and queues[k])
        done = set()
        while len(done) < len(subs):
            for k in subs:
                if k in done:
                    continue
                q = queues[k]
                while q and q[0].uid in sel.chosen:
                    q.pop(0)
                if not q:
                    done.add(k)
                    continue
                r = fill_one(q[0], "remplissage")
                if r == "ok":
                    q.pop(0)
                elif r == "cap" and (len(q) > 1):
                    q.pop(0)        # cet élément ne tient pas (lot ou plafond) : on essaie le suivant, plus petit peut-être
                else:
                    done.add(k)
    return sel


# ------------------------------------------------------------------------------------------------ projection (fichiers des lots d'essai)

def trial_scope(cfg, scope):
    return scope + cfg["trialSuffix"]


def project(inv, sel, cfg):
    """{lot: {chemin: octets}} : le contenu exact des lots d'essai ; sert à la taille exacte et à `build`."""
    chosen = set(sel.chosen)
    files = {}
    for lot in sorted({i.lot for i in inv.items.values() if i.uid in chosen}):
        files[lot] = _project_lot(inv, chosen, lot, cfg)
    return files


def _project_lot(inv, chosen, lot, cfg):
    feature, scope = lot
    items = [i for i in inv.items.values() if i.lot == lot and i.uid in chosen]
    out = {}
    if feature == "learn":
        sel_les = {i.id for i in items if i.kind == "lesson"}
        sel_ex = {i.id for i in items if i.kind == "exercise"}
        for pid in sorted({i.a["pack"] for i in items}):
            pack = dict(inv.packs[pid])
            used = {i.a["chapter"] for i in items if i.a["pack"] == pid}
            pack["chapters"] = [c for c in pack.get("chapters", []) if c["id"] in used]
            pack["edition"] = "trial"
            out[pid + "/pack.json"] = jb(pack)
        for rel in sorted({i.a["file"] for i in items}):
            mine = [i for i in items if i.a["file"] == rel]
            ls = []
            for i in sorted((x for x in mine if x.kind == "lesson"), key=lambda x: x.a["order"]):
                l = dict(i.obj)
                l["prerequisites"] = [p for p in l.get("prerequisites", []) if p in sel_les]
                for key in ("exercises", "selfCheck"):
                    if key in l:
                        l[key] = [x for x in l[key] if x in sel_ex]
                l["blocks"] = [b for b in l.get("blocks", []) if not (b.get("type") == "exercise" and b.get("ref") not in sel_ex)]
                ls.append(l)
            es = [i.obj for i in mine if i.kind == "exercise"]
            out[rel] = jb({"chapter": inv.extra[("file", rel)]["chapter"], "lessons": ls, "exercises": es})
    elif feature == "quiz":
        ex = inv.extra[("quiz", scope)]
        qs = [i for i in sorted(items, key=lambda x: x.a["pos"])]
        hashes = {i.id: ex["index"]["q"][i.id] for i in qs}
        chash = hashlib.sha256("".join("%s:%s\n" % (k, hashes[k]) for k in sorted(hashes)).encode("utf-8")).hexdigest()
        tscope = trial_scope(cfg, scope)
        man = dict(ex["manifest"])
        man.update({"questions": len(qs), "edition": "trial", "baseScope": scope})
        man["lot"] = dict(man.get("lot", {}), scope=tscope, contentHash=chash, title=ex["lot"]["title"] + " (essai)")
        diff = {}
        for q in qs:
            diff[q.a["diff"]] = diff.get(q.a["diff"], 0) + 1
        man["byDifficulty"] = {str(k): diff[k] for k in sorted(diff)}
        man.pop("files", None)
        out["questions.json"] = jb({"version": ex["manifest"]["version"], "questions": [q.obj for q in qs]})
        out["index.json"] = jb({"v": 1, "scope": tscope, "version": ex["manifest"]["version"], "count": len(qs), "contentHash": chash, "q": hashes})
        out["manifest.json"] = jb(man)
    elif feature == "langues":
        for pid in sorted({i.a["pack"] for i in items}):
            ex = inv.extra[("pack", pid)]
            mine = [i for i in items if i.a["pack"] == pid]
            keep = {m.a["mid"] for m in inv_media(inv, chosen, lot, pid)}
            units = [_strip_audio(i.obj, keep) for i in sorted(mine, key=lambda x: x.a["order"])]
            pk = dict(ex["pack"], units=units, edition="trial")
            out[pid + "/langue.json"] = jb(pk)
    elif feature == "langmedia":
        for pid in sorted({i.a["pack"] for i in items}):
            ms = sorted((i for i in items if i.a["pack"] == pid), key=lambda x: x.a["mid"])
            out[pid + "/media.json"] = jb({"format": 1, "media": [m.obj for m in ms]})
            for m in ms:
                out["%s/%s" % (pid, m.obj["file"])] = int(m.obj["bytes"])      # taille déclarée (le fichier audio/vidéo vit dans le lot média)
    return out


def inv_media(inv, chosen, lot, pid):
    media_lot = ("langmedia", lot[1])
    return [i for i in inv.items.values() if i.lot == media_lot and i.a["pack"] == pid and i.uid in chosen]


def _strip_audio(o, keep):
    if isinstance(o, dict):
        return {k: _strip_audio(v, keep) for k, v in o.items()
                if not (k == "audio" and isinstance(v, str) and v.startswith("m:") and v[2:] not in keep)}
    if isinstance(o, list):
        return [_strip_audio(v, keep) for v in o]
    return o


def fsize(v):
    return v if isinstance(v, int) else len(v)


# ------------------------------------------------------------------------------------------------ manifeste

def bundles(inv, aliases=None):
    """Bouquets payants (docs/TRIAL-EDITION.md § Bouquets), calculés depuis le registre : aucun prix."""
    full = {}
    for i in inv.items.values():
        full[i.lot] = full.get(i.lot, 0) + i.cost
    key = lambda l: "%s:%s" % l
    out = []
    for scope in sorted(inv.scopes):
        lots = [l for l in (("learn", scope), ("quiz", scope), ("quiz", (aliases or {}).get(scope, ""))) if l in full]
        out.append({"id": "classe-" + scope, "type": "classe", "title": inv.scopes[scope], "lots": [key(l) for l in lots], "rawBytes": sum(full[l] for l in lots)})
    for l in sorted(l for l in full if l[0] == "quiz"):
        out.append({"id": "quiz-" + l[1], "type": "quiz", "title": inv.lots[l]["title"], "lots": [key(l)], "rawBytes": full[l]})
    langs = sorted({l[1].split("-")[0] for l in full if l[0].startswith("lang")})
    for lang in langs:
        lots = sorted(l for l in full if l[0].startswith("lang") and l[1].split("-")[0] == lang)
        out.append({"id": "langue-" + lang, "type": "langue", "title": "Langue " + lang, "lots": [key(l) for l in lots], "rawBytes": sum(full[l] for l in lots)})
    allots = sorted(full)
    out.append({"id": "tout", "type": "tout", "title": "Tout le catalogue", "lots": [key(l) for l in allots], "rawBytes": sum(full.values())})
    return out


RENTAL_DAYS_MIN, RENTAL_DAYS_MAX = 1, 366
DEFAULT_RENTAL_FILE = os.path.join(HERE, "..", "..", "content", "bundles-rental.json")


def load_rental(path=None):
    """Durées de location FIXÉES PAR LE SERVEUR (content/bundles-rental.json) : {"default": jours, "bundles": {id: jours}}."""
    p = path or DEFAULT_RENTAL_FILE
    if not os.path.isfile(p):
        raise Fail("fichier des durées de location introuvable : %s (créez-le : {\"default\": 30, \"bundles\": {}} ; c'est le propriétaire qui fixe les durées)" % p)
    try:
        r = load(p)
    except ValueError as e:
        raise Fail("fichier des durées de location illisible (%s) : %s" % (p, e))
    if not isinstance(r, dict) or not isinstance(r.get("bundles", {}), dict):
        raise Fail("fichier des durées de location : un objet {\"default\": jours, \"bundles\": {...}} est attendu (%s)" % p)
    return r


def rental_days(rental, bund):
    """Durée de location exacte de chaque bouquet (par bouquet, sinon « default ») ; échoue si l'une n'est pas un entier de 1 à 366."""
    def ok(v):
        return isinstance(v, int) and not isinstance(v, bool) and RENTAL_DAYS_MIN <= v <= RENTAL_DAYS_MAX
    per = rental.get("bundles", {})
    ids = {b["id"] for b in bund}
    for k in sorted(per):
        if k not in ids:
            raise Fail("durées de location : le bouquet « %s » n'existe pas dans le catalogue (faute de frappe ?)" % k)
    out = {}
    for b in bund:
        v = per.get(b["id"], rental.get("default"))
        if v is None:
            raise Fail("durées de location : aucune durée pour le bouquet « %s » (ni durée propre, ni « default »)" % b["id"])
        if not ok(v):
            raise Fail("durées de location : la durée du bouquet « %s » doit être un nombre entier de jours de %d à %d (reçu : %r)" % (b["id"], RENTAL_DAYS_MIN, RENTAL_DAYS_MAX, v))
        out[b["id"]] = v
    return out


def build_manifest(inv, sel, cfg, files, rental=None):
    lots, total = [], 0
    by_lot = {}
    for i in inv.items.values():
        if i.uid in sel.chosen:
            by_lot.setdefault(i.lot, []).append(i)
    bund = bundles(inv, cfg.get("bundleQuizAliases"))
    days = rental_days(load_rental() if rental is None else rental, bund)
    for x in bund:
        x["rentalDays"] = days[x["id"]]       # la durée d'une location est fixée par le serveur, exacte : les outils du propriétaire n'en acceptent pas d'autre
    for lot in sorted(by_lot):
        f = files[lot]
        b = sum(fsize(v) for v in f.values())
        total += b
        lots.append({
            "feature": lot[0], "scope": trial_scope(cfg, lot[1]), "fullLot": {"feature": lot[0], "scope": lot[1]}, "edition": "trial",
            "title": inv.lots.get(lot, {}).get("title", lot[1]) + " (essai)", "bytes": b,
            "files": [{"path": p, "bytes": fsize(f[p])} for p in sorted(f)],
            "items": sorted("%s/%s" % (i.kind, i.id) for i in by_lot[lot]),
            "bundles": [x["id"] for x in bund if "%s:%s" % lot in x["lots"]],
        })
    fullsub = {k: sum(i.cost for i in inv.of_sub(k)) for k in inv.subs}
    subs = []
    for k in sorted(inv.subs):
        sb = sum(i.cost for i in inv.of_sub(k) if i.uid in sel.chosen)
        subs.append({"key": k, "category": inv.subs[k]["cat"], "tier": inv.subs[k]["tier"], "label": inv.subs[k]["label"],
                     "selectedItems": sum(1 for i in inv.of_sub(k) if i.uid in sel.chosen), "totalItems": len(inv.of_sub(k)),
                     "selectedBytes": sb, "fullBytes": fullsub[k], "roles": sel.roles.get(k, {})})
    cats = {}
    for s in subs:
        c = cats.setdefault(s["category"], {"bytes": 0, "selectedItems": 0, "fullBytes": 0})
        c["bytes"] += s["selectedBytes"]; c["selectedItems"] += s["selectedItems"]; c["fullBytes"] += s["fullBytes"]
    return {
        "format": 1, "tool": TOOL_VERSION, "valid": True,
        "capBytes": cfg["capBytes"], "totalBytes": total, "inventory": inv.fingerprint(),
        "configHash": hashlib.sha256(jb(cfg)).hexdigest(),
        "categories": cats, "lots": lots, "subcategories": subs, "bundles": bund,
        "warnings": sorted(set(sel.warnings)),
    }


def violations(inv, sel, cfg, files):
    v = []
    total = sum(fsize(x) for f in files.values() for x in f.values())
    if total > cfg["capBytes"]:
        v.append("plafond dépassé : %d octets > %d" % (total, cfg["capBytes"]))
    for k in sorted(inv.subs):
        if not any(i.uid in sel.chosen for i in inv.of_sub(k)):
            v.append("sous-catégorie vide : %s (%s)" % (k, "aucun contenu" if not inv.of_sub(k) else "rien de sélectionné"))
    for scope in sorted(inv.scopes):
        if not any(i.uid in sel.chosen and i.lot == ("learn", scope) for i in inv.items.values()):
            v.append("classe sans échantillon : " + scope)
    for lot, f in sorted(files.items()):
        lim = cfg["mediaLotMaxBytes"] if lot[0] == "langmedia" else cfg["textLotMaxBytes"]
        b = sum(fsize(x) for x in f.values())
        if b > lim:
            v.append("lot d'essai trop gros : %s:%s = %d > %d" % (lot[0], lot[1], b, lim))
    for lot in sorted(inv.lots):
        if lot[0] in ("quiz",) and lot not in files and any(i.lot == lot for i in inv.items.values()):
            v.append("lot Quiz sans échantillon : " + lot[1])
    # niveaux : chaque difficulté (N0–N4) présente dans un lot Quiz doit y être représentée
    for k in sorted(inv.subs):
        if inv.subs[k]["cat"] == "quiz":
            have = {i.a["diff"] for i in inv.of_sub(k)}
            got = {i.a["diff"] for i in inv.of_sub(k) if i.uid in sel.chosen}
            for d in sorted(have - got):
                v.append("%s : niveau %s sans question" % (k, cfg["levels"]["difficultyLabels"].get(str(d), d)))
    return v


def previous_uids(man):
    """Les éléments déjà dans l'essai (pour ne pas les faire sortir sans raison quand le contenu grandit)."""
    out = set()
    for lot in man.get("lots", []):
        cat = "langues" if lot["feature"].startswith("lang") else lot["feature"]
        for it in lot["items"]:
            out.add("%s/%s" % (cat, it))
    return out


def run_select(repo, cfg, only, previous_ids):
    inv = scan(repo, cfg, only)
    if not inv.subs:
        raise Fail("aucune sous-catégorie trouvée dans " + repo)
    sel = select(inv, cfg, previous_ids)
    files = project(inv, sel, cfg)
    return inv, sel, files


def report(man):
    lines = ["Édition d'essai : %.2f Mo sur %.0f Mo (plafond dur), %d lots, %d éléments"
             % (man["totalBytes"] / 1048576, man["capBytes"] / 1048576, len(man["lots"]), sum(len(l["items"]) for l in man["lots"]))]
    for c, v in sorted(man["categories"].items()):
        lines.append("  %-8s %7.2f Mo d'essai / %8.2f Mo complets (%d éléments)" % (c, v["bytes"] / 1048576, v["fullBytes"] / 1048576, v["selectedItems"]))
    lines.append("Sous-catégories (octets d'essai / octets complets) :")
    for s in man["subcategories"]:
        lines.append("  %-34s %8d / %9d  %3d/%-5d éléments" % (s["key"], s["selectedBytes"], s["fullBytes"], s["selectedItems"], s["totalItems"]))
    for w in man["warnings"]:
        lines.append("  AVERTISSEMENT : " + w)
    return "\n".join(lines)


def load_cfg(path=None):
    return load(path or os.path.join(HERE, "config.json"))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cmd", choices=["select", "check", "build"])
    ap.add_argument("--repo", default=os.path.abspath(os.path.join(HERE, "..", "..")))
    ap.add_argument("--config")
    ap.add_argument("--out")
    ap.add_argument("--manifest")
    ap.add_argument("--only")
    ap.add_argument("--rental", help="durées de location par bouquet (défaut : <repo>/content/bundles-rental.json)")
    ap.add_argument("--previous")
    ap.add_argument("--draft", action="store_true", help="écrit <out>.draft.json même si une règle échoue (diagnostic ; exit 1 quand même)")
    a = ap.parse_args(argv)
    cfg = load_cfg(a.config)
    only = set(a.only.split(",")) if a.only else None
    out = a.out or os.path.join(a.repo, "content", "TRIAL-MANIFEST.json")
    prev_path = a.previous or (out if os.path.isfile(out) and a.cmd == "select" else None)
    prev = previous_uids(load(prev_path)) if prev_path and os.path.isfile(prev_path) else set()
    try:
        inv, sel, files = run_select(a.repo, cfg, only, prev)
        v = violations(inv, sel, cfg, files)
        man = build_manifest(inv, sel, cfg, files, load_rental(a.rental or os.path.join(a.repo, "content", "bundles-rental.json")))
    except Fail as e:
        print("ÉCHEC : %s" % e, file=sys.stderr)
        return 1
    print(report(man))
    if v:
        man["valid"] = False
        man["violations"] = v
        for x in v:
            print("ÉCHEC : " + x, file=sys.stderr)
        if a.draft and a.cmd == "select":
            with open(out + ".draft.json", "w", encoding="utf-8") as f:
                json.dump(man, f, ensure_ascii=False, indent=1, sort_keys=True)
        return 1
    if a.cmd == "select":
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "w", encoding="utf-8") as f:
            json.dump(man, f, ensure_ascii=False, indent=1, sort_keys=True)
            f.write("\n")
        print("Manifeste écrit : " + out)
    elif a.cmd == "check":
        mp = a.manifest or out
        old = load(mp)
        if old != json.loads(json.dumps(man, sort_keys=True)):
            print("ÉCHEC : %s n'est plus à jour (le contenu ou les règles ont changé) : relancer « select »" % mp, file=sys.stderr)
            return 1
        print("Manifeste à jour.")
    else:
        os.makedirs(out, exist_ok=True)
        report_lines = []
        for lot, f in sorted(files.items()):
            name = "castbridge-trial-%s-%s.lot.zip" % (lot[0], trial_scope(cfg, lot[1]))
            buf = io.BytesIO()
            with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
                for p in sorted(f):
                    if isinstance(f[p], int):
                        src = _media_source(inv, p)
                        if src is None:
                            report_lines.append("média absent du dépôt (non inclus) : " + p)
                            continue
                        data = open(src, "rb").read()
                    else:
                        data = f[p]
                    zi = zipfile.ZipInfo(p, (2026, 1, 1, 0, 0, 0))
                    zi.compress_type = zipfile.ZIP_DEFLATED
                    z.writestr(zi, data)
            open(os.path.join(out, name), "wb").write(buf.getvalue())
            report_lines.append("%s : %d octets (zip)" % (name, len(buf.getvalue())))
        print("\n".join(report_lines))
    return 0


def _media_source(inv, path):
    pid, rel = path.split("/", 1)
    ex = inv.extra.get(("pack", pid))
    if not ex:
        return None
    p = os.path.join(ex["dir"], rel)
    return p if os.path.isfile(p) else None


if __name__ == "__main__":
    sys.exit(main())
