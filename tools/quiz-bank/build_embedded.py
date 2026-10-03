#!/usr/bin/env python3
"""Construit le Quiz embarqué de CastBridge-TV : TOUTES les questions des lots de content/quiz/lots (phase d'essai).

Décisions du propriétaire (2026-10-03) :
- toutes les questions du dépôt sont embarquées (plus de plafond à 2000) ;
- aucun niveau n'est réservé en bloc : la réservation est PAR QUESTION. Dans chaque lot, 3 questions sur 10 sont
  « réservables » (famille RESERVED), 7 sur 10 sont libres (FREE).

Règle de marquage (déterministe, documentée, testée) :
  pour chaque lot, strates = (difficulté) ou (région, difficulté) pour la culture générale ; dans chaque strate les
  questions sont triées par SHA-256("castbridge-quiz-reserved-v1|" + id) ; les K premières sont réservables, avec
  K = round(0,30 × effectif cumulé) − round(0,30 × effectif cumulé précédent) (le reste est reporté d'une strate à
  l'autre : le total du lot est exactement round(0,30 × N), chaque strate est à ±1 question de 30 %).
  Stabilité : le marquage ne dépend que de la graine et du contenu du lot ; si un lot grossit, seules les questions
  voisines du seuil (rang de hachage proche de 30 %) peuvent changer (mesuré dans les tests : ≤ 3 %). Pour une
  garantie stricte après publication, geler la liste des identifiants réservables (voir le rapport).

Sortie (android/core/src/main/resources/castbridge/quiz/) :
  embedded/<niveau>/<lot>[.N].json            questions LIBRES
  embedded-reserved/<niveau>/<lot>[.N].json   questions RÉSERVABLES
  embedded/index.json                         niveaux, comptes (total / libres / réservables), fichiers (nombre, taille)
Un fichier contient au plus CHUNK questions (≈ 0,15 Mo) : une partie ne charge que quelques fichiers.
Questions NON modifiées (statut « review » conservé, rien n'est marqué approuvé). Identifiants des lots conservés.

Usage : python3 tools/quiz-bank/build_embedded.py [--out DIR] [--check]
"""
import argparse, hashlib, json, re, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOTS = ROOT / "content/quiz/lots"
RES = ROOT / "android/core/src/main/resources/castbridge/quiz"
SEED = "castbridge-embedded-quiz-v2"            # ordre de rotation des fichiers côté TV (index.json « seed »)
RESERVED_SEED = "castbridge-quiz-reserved-v1"   # marquage des questions réservables
RESERVED_RATIO = 0.30
CHUNK = 500                                     # questions par fichier au plus (≈ 0,1 à 0,25 Mo de JSON) : une partie mélange plusieurs lots
FORMAT = 2
CULTURE = "culture-generale"
SOURCE_FALLBACK = "Banque embarquée CastBridge (en relecture)"


class RefusedLot(Exception):
    pass


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def level_key(level):
    return CULTURE if level is None else slug(level)


def read_lot(lot):
    p = LOTS / lot["file"]
    if hashlib.sha256(p.read_bytes()).hexdigest() != lot["sha256"]:
        raise RefusedLot(f"{lot['file']} : empreinte différente du catalogue")
    with zipfile.ZipFile(p) as z:
        return json.loads(z.read("questions.json"))["questions"]


def _h(qid):
    return hashlib.sha256((RESERVED_SEED + "|" + qid).encode("utf-8")).hexdigest()


def quota(sizes, ratio=RESERVED_RATIO):
    """Nombre de réservables par strate, reste reporté (somme = round(ratio × somme des effectifs))."""
    out, cum, prev = [], 0, 0
    for n in sizes:
        cum += n
        cur = int(ratio * cum + 0.5)
        out.append(cur - prev)
        prev = cur
    return out


def reserved_ids(qs, by_region=False):
    """Ensemble des identifiants réservables d'un lot (règle du module)."""
    strata = {}
    for q in qs:
        strata.setdefault((q["region"] if by_region else "", q["difficulty"]), []).append(q["id"])
    keys = sorted(strata)
    out = set()
    for k, n in zip(keys, quota([len(strata[k]) for k in keys])):
        out.update(sorted(strata[k], key=_h)[:n])
    return out


def compact(head, qs):
    """Format compact (docs/QUIZ.md) : en-tête + une ligne par question, table des sources en tête."""
    sources = sorted({q.get("source") or SOURCE_FALLBACK for q in qs})
    sidx = {s: i for i, s in enumerate(sources)}
    head = dict(head, sources=sources)
    rows = [[q["id"], q["region"], q["category"], q["difficulty"], q["question"], q["choices"], q["answer"],
             q.get("explanation", ""), sidx[q.get("source") or SOURCE_FALLBACK], q.get("field"), q.get("status", "review"), q.get("verif")]
            for q in qs]
    dump = lambda o: json.dumps(o, ensure_ascii=False, separators=(",", ":"))
    return (dump(head)[:-1] + ',"q":[\n' + ",\n".join(dump(r) for r in rows) + "\n]}\n").encode("utf-8")


def validate(qs):
    for q in qs:
        assert len(q["choices"]) == 4 and 0 <= q["answer"] < 4, q["id"]
        assert q.get("status", "review") == "review", f"{q['id']} : statut {q.get('status')} (le statut doit rester tel quel)"


def chunks(qs):
    """Parts de taille égale, au plus CHUNK questions, triées par identifiant."""
    qs = sorted(qs, key=lambda q: q["id"])
    n = max(1, -(-len(qs) // CHUNK))
    size = -(-len(qs) // n)
    return [qs[i:i + size] for i in range(0, len(qs), size)] if qs else []


def build():
    """{chemin relatif à RES : octets} (fichiers de questions + embedded/index.json)."""
    cat = json.loads((LOTS / "catalog-lots.json").read_text("utf-8"))["lots"]
    files, levels = {}, {}
    for lot in sorted(cat, key=lambda l: (level_key(l["level"]), l["id"])):
        qs = read_lot(lot)
        validate(qs)
        key = level_key(lot["level"])
        track = lot["track"]
        assert all(q["track"] == track and q.get("level") == lot["level"] for q in qs), lot["id"]
        ids = [q["id"] for q in qs]
        if len(set(ids)) != len(ids):
            raise RefusedLot(f"{lot['id']} : identifiants en double")
        marked = reserved_ids(qs, lot["level"] is None)
        e = levels.setdefault(key, {"key": key, "level": lot["level"], "track": track, "count": 0, "freeCount": 0, "reservedCount": 0,
                                    "files": [], "reservedFiles": [], "lots": []})
        e["lots"].append(lot["id"])
        for fam, folder, listing in (("free", "embedded", "files"), ("reserved", "embedded-reserved", "reservedFiles")):
            part = [q for q in qs if (q["id"] in marked) == (fam == "reserved")]
            parts = chunks(part)
            for i, c in enumerate(parts):
                name = f"{folder}/{key}/{lot['id']}" + ("" if i == 0 else f".{i + 1}") + ".json"
                head = {"v": FORMAT, "level": lot["level"], "track": track, "lot": lot["id"], "family": fam, "part": i + 1, "parts": len(parts)}
                data = compact(head, c)
                files[name] = data
                e[listing].append({"name": name, "lot": lot["id"], "field": lot["field"], "count": len(c), "bytes": len(data)})
            e["freeCount" if fam == "free" else "reservedCount"] += len(part)
        e["count"] += len(qs)
    index = {"v": FORMAT, "seed": SEED, "reservedSeed": RESERVED_SEED, "reservedRatio": RESERVED_RATIO,
             "levels": [levels[k] for k in sorted(levels)]}
    files["embedded/index.json"] = (json.dumps(index, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
    return files


def existing(out):
    return {str(p.relative_to(out)) for fam in ("embedded", "embedded-reserved") for p in (out / fam).rglob("*.json")}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(RES))
    ap.add_argument("--check", action="store_true", help="échoue si le contenu commité diffère")
    a = ap.parse_args()
    out = Path(a.out)
    try:
        files = build()
    except RefusedLot as e:
        print("REFUSÉ :", e, file=sys.stderr)
        return 2
    if a.check:
        bad = [n for n, b in files.items() if not (out / n).is_file() or (out / n).read_bytes() != b]
        extra = sorted(existing(out) - set(files))
        if bad or extra:
            print("DIFFÉRENT :", bad[:10], extra[:10], file=sys.stderr)
            return 1
        print("identique")
        return 0
    for fam in ("embedded", "embedded-reserved"):
        d = out / fam
        if d.is_dir():
            for p in d.rglob("*.json"):
                p.unlink()
    for n, b in files.items():
        p = out / n
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(b)
    tot = sum(len(b) for b in files.values())
    print(f"{len(files) - 1} fichiers de questions, {tot} octets")
    return 0


if __name__ == "__main__":
    sys.exit(main())
