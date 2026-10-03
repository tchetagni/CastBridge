#!/usr/bin/env python3
"""Construit le Quiz embarqué par niveau (CastBridge-TV) à partir des lots de content/quiz/lots.

Sortie : android/core/src/main/resources/castbridge/quiz/embedded/<niveau>.json + index.json.
Déterministe (graine fixe, tris explicites), stratifié (lot/matière, puis difficulté), identifiants des lots conservés,
questions NON modifiées (statut « review » conservé ; rien n'est marqué approuvé).
Fail closed : un lot absent de content/quiz/families.json, ou RÉSERVÉ, ne peut jamais être embarqué (sortie en erreur
si un niveau demandé en contient un ; les niveaux entièrement réservés sont simplement ignorés et listés).

Usage : python3 tools/quiz-bank/build_embedded.py [--out DIR] [--check]
"""
import argparse, hashlib, json, random, re, sys, unicodedata, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOTS = ROOT / "content/quiz/lots"
FAMILIES = ROOT / "content/quiz/families.json"
RES = ROOT / "android/core/src/main/resources/castbridge/quiz"
OUT = RES / "embedded"
SEED = "castbridge-embedded-quiz-v1"
TARGET = 2000          # minimum par niveau libre
FORMAT = 1
CULTURE = "culture-generale"
SOURCE_FALLBACK = "Banque embarquée CastBridge (en relecture)"


class RefusedLot(Exception):
    pass


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def norm(s):
    s = unicodedata.normalize("NFD", s.lower())
    return re.sub(r"[^a-z0-9]+", " ", "".join(c for c in s if not unicodedata.combining(c))).strip()


def level_key(level):
    return CULTURE if level is None else slug(level)


def load_families():
    f = json.loads(FAMILIES.read_text("utf-8"))
    return set(f["free"]), set(f["reserved"])


def classify(lot, free, reserved):
    """'free' | 'reserved' ; lève RefusedLot si le lot est inconnu de families.json (fail closed)."""
    s = "quiz:" + lot["id"]
    if s in reserved and s in free:
        raise RefusedLot(f"{s} est à la fois libre et réservé dans families.json")
    if s in reserved:
        return "reserved"
    if s in free:
        return "free"
    raise RefusedLot(f"{s} absent de families.json : refusé (fail closed)")


def allocate(total, caps):
    """Répartit `total` proportionnellement aux capacités (plus fort reste, départage par clé), sans dépasser chaque cap."""
    keys = sorted(caps)
    alloc = {k: 0 for k in keys}
    live = [k for k in keys if caps[k] > 0]
    left = min(total, sum(caps.values()))
    while left > 0 and live:
        w = sum(caps[k] for k in live)
        raw = {k: left * caps[k] / w for k in live}
        got = {k: min(int(raw[k]), caps[k] - alloc[k]) for k in live}
        rest = left - sum(got.values())
        for k in sorted(live, key=lambda k: (-(raw[k] - int(raw[k])), k)):
            if rest <= 0:
                break
            if got[k] < caps[k] - alloc[k]:
                got[k] += 1
                rest -= 1
        for k in live:
            alloc[k] += got[k]
        left = total - sum(alloc.values())
        live = [k for k in live if alloc[k] < caps[k]]
    return alloc


def read_lot(lot):
    p = LOTS / lot["file"]
    data = p.read_bytes()
    if hashlib.sha256(data).hexdigest() != lot["sha256"]:
        raise RefusedLot(f"{lot['file']} : empreinte différente du catalogue")
    with zipfile.ZipFile(p) as z:
        qs = json.loads(z.read("questions.json"))["questions"]
    return qs


def existing_texts(level):
    """Textes (normalisés) des questions déjà embarquées dans l'ancienne banque, pour ne pas les doubler."""
    out = set()
    for n in ("questions.json", "questions-school.json"):
        for q in json.loads((RES / n).read_text("utf-8"))["questions"]:
            if (q.get("level") or None) == level:
                out.add(norm(q["question"]))
    return out


def select(level, lots_qs, target):
    """lots_qs : {lot_id: [questions]} ; renvoie la liste choisie (triée par id)."""
    seen = existing_texts(level)
    pool = {}                                  # strate -> difficulté -> [q] (triées par id, sans doublon d'identifiant ni de texte)
    ids = set()
    for lot_id in sorted(lots_qs):
        for q in sorted(lots_qs[lot_id], key=lambda q: q["id"]):
            if q["id"] in ids:
                continue
            ids.add(q["id"])
            pool.setdefault(lot_id, {}).setdefault(q["difficulty"], []).append(q)
    chosen, taken = [], set()

    def take(q):
        t = norm(q["question"]) + "|" + str(q.get("field"))
        if q["id"] in taken or (norm(q["question"]) in seen) or t in taken:
            return False
        taken.add(q["id"]); taken.add(t)
        chosen.append(q)
        return True

    per_lot = allocate(target, {l: sum(len(v) for v in d.values()) for l, d in pool.items()})
    for lot_id in sorted(pool):
        d = pool[lot_id]
        per_diff = allocate(per_lot[lot_id], {k: len(v) for k, v in d.items()})
        for diff in sorted(d):
            rng = random.Random(f"{SEED}|{level_key(level)}|{lot_id}|{diff}")
            cand = list(d[diff]); rng.shuffle(cand)
            n = 0
            for q in cand:
                if n >= per_diff[diff]:
                    break
                n += take(q)
    if len(chosen) < target:                   # complément (textes en double écartés) : tirage gelé sur le reste
        rest = [q for d in pool.values() for v in d.values() for q in v if q["id"] not in taken]
        random.Random(f"{SEED}|{level_key(level)}|fill").shuffle(rest)
        for q in rest:
            if len(chosen) >= target:
                break
            take(q)
    return sorted(chosen, key=lambda q: q["id"])


def compact(level, qs):
    """Format compact (docs/QUIZ.md « Quiz embarqué ») : une ligne par question, table des sources en tête."""
    sources = sorted({q.get("source") or SOURCE_FALLBACK for q in qs})
    sidx = {s: i for i, s in enumerate(sources)}
    track = qs[0]["track"]
    assert all(q["track"] == track and q.get("level") == level for q in qs)
    head = {"v": FORMAT, "level": level, "track": track, "sources": sources}
    rows = []
    for q in qs:
        rows.append([q["id"], q["region"], q["category"], q["difficulty"], q["question"], q["choices"], q["answer"],
                     q.get("explanation", ""), sidx[q.get("source") or SOURCE_FALLBACK], q.get("field"), q.get("status", "review"), q.get("verif")])
    dump = lambda o: json.dumps(o, ensure_ascii=False, separators=(",", ":"))
    return (dump(head)[:-1] + ',"q":[\n' + ",\n".join(dump(r) for r in rows) + "\n]}\n").encode("utf-8")


def validate_rows(level, qs):
    ids = set()
    for q in qs:
        assert q["id"] not in ids, f"id en double {q['id']}"
        ids.add(q["id"])
        assert len(q["choices"]) == 4 and 0 <= q["answer"] < 4, q["id"]
        assert q["status"] == "review", f"{q['id']} : statut {q['status']} (le statut doit rester tel quel)"


def build(out_dir=OUT):
    free, reserved = load_families()
    cat = json.loads((LOTS / "catalog-lots.json").read_text("utf-8"))["lots"]
    by_level, skipped = {}, {}
    for lot in cat:
        kind = classify(lot, free, reserved)
        if kind == "reserved":
            skipped.setdefault(lot["level"], []).append(lot["id"])
            continue
        by_level.setdefault(lot["level"], {}).setdefault(lot["id"], []).append(lot)
    mixed = set(by_level) & set(skipped)
    if mixed:
        raise RefusedLot(f"niveau mêlant lots libres et réservés : {sorted(mixed, key=str)}")
    files, index = {}, []
    for level in sorted(by_level, key=lambda l: level_key(l)):
        lots_qs = {}
        for lot_id, parts in by_level[level].items():
            lots_qs[lot_id] = [q for lot in parts for q in read_lot(lot)]
        qs = select(level, lots_qs, TARGET)
        if len(qs) < TARGET:
            raise RefusedLot(f"niveau {level_key(level)} : {len(qs)} questions < {TARGET}")
        validate_rows(level, qs)
        key = level_key(level)
        files[key + ".json"] = compact(level, qs)
        index.append({"key": key, "level": level, "track": qs[0]["track"], "count": len(qs), "file": key + ".json",
                      "lots": sorted(lots_qs)})
    idx = {"v": FORMAT, "seed": SEED, "levels": index,
           "reservedNotEmbedded": sorted(k for k in (level_key(l) for l in skipped))}
    files["index.json"] = (json.dumps(idx, ensure_ascii=False, indent=1) + "\n").encode("utf-8")
    return files


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(OUT))
    ap.add_argument("--check", action="store_true", help="échoue si le contenu commité diffère")
    a = ap.parse_args()
    out = Path(a.out)
    try:
        files = build(out)
    except RefusedLot as e:
        print("REFUSÉ :", e, file=sys.stderr)
        return 2
    if a.check:
        bad = [n for n, b in files.items() if not (out / n).is_file() or (out / n).read_bytes() != b]
        extra = [p.name for p in out.glob("*.json") if p.name not in files]
        if bad or extra:
            print("DIFFÉRENT :", bad + extra, file=sys.stderr)
            return 1
        print("identique")
        return 0
    out.mkdir(parents=True, exist_ok=True)
    for p in out.glob("*.json"):
        p.unlink()
    for n, b in files.items():
        (out / n).write_bytes(b)
    tot = sum(len(b) for b in files.values())
    print(f"{len(files) - 1} niveaux, {tot} octets")
    return 0


if __name__ == "__main__":
    sys.exit(main())
