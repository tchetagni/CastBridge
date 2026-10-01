#!/usr/bin/env python3
"""Builds ONLY the packs of the scope « primaire et premier cycle » (CP-4e, CM2, 3e, Class 1-6, Form 1-3) into content/quiz/dist,
leaving the packs of the other scopes untouched, and merges their entries into catalog.json / coverage.json / sources.json /
rejected.json (by course key / fiche id / question id). Same packs as `quizbank.py build` would produce for these courses.
    python3.12 tools/quiz-bank/build_scope.py [--version N]
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import quizbank  # noqa: E402
from qb import core, facts_engine, pack, qc  # noqa: E402

SCOPE = [k for k in core.COURSES if k in core.PC_COURSES or k in ("cm2", "3e")]
DOC = Path(__file__).resolve().parents[2] / "docs" / "coverage" / "quiz-primaire-college.md"
MATHS = {"Calcul", "Problèmes", "Géométrie", "Mesures", "Fractions", "Nombres décimaux", "Algèbre", "Nombres relatifs", "Proportionnalité", "Statistiques", "Arithmétique", "Argent", "Temps",
         "Pourcentages", "Numération", "Grandeurs", "Mathématiques", "Mathematics", "Number work", "Problem solving", "Geometry", "Measures", "Decimals", "Integers", "Ratio and proportion", "Number theory",
         "Money", "Time", "Place value", "Percentages", "Algebra", "Statistics"}
FRENCH = {"Conjugaison", "Orthographe", "Grammaire", "Vocabulaire"}
ENGLISH_LANG = {"Verbs", "Spelling", "Grammar", "Vocabulary"}


def subject(q, lang):
    c = q["category"]
    if c in MATHS and c not in ("Grammar", "Vocabulary"):
        return "Mathématiques" if lang == "fr" else "Mathematics"
    if lang == "fr" and c in FRENCH:
        return "Français"
    if lang == "en" and c in ENGLISH_LANG:
        return "English language"
    if c.startswith("Histoire"):
        return "Histoire-géographie"
    return c


def write_doc(good):
    """Per course and subject: questions, distinct models, variants (written between the AUTO markers of the coverage doc)."""
    from collections import Counter, defaultdict
    rows = []
    for c in SCOPE:
        cc = core.COURSES[c]
        cq = [q for q in good if (q["track"], q["level"], q["field"]) == (cc["track"], cc["level"], cc["field"])]
        by = defaultdict(list)
        for q in cq:
            by[subject(q, cc.get("lang", "fr"))].append(q)
        rows.append("### %s (`%s`) — %d questions, %d parties sans répétition (objectif 300)\n" % (cc["label"], c, len(cq), len(cq) // 15))
        rows.append("| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |\n|---|---:|---:|---:|---:|---|")
        for sub in sorted(by, key=lambda x: -len(by[x])):
            l = by[sub]
            comp = [q for q in l if q["verif"] == "computed"]
            dif = Counter(q["difficulty"] for q in l)
            rows.append("| %s | %d | %d | %d | %d | %s |" % (sub, len(l), len({q["tpl"] for q in comp}), len(comp), len(l) - len(comp), "-".join(str(dif.get(d, 0)) for d in range(1, 6))))
        rows.append("")
    block = "<!-- BEGIN AUTO -->\n" + "\n".join(rows) + "\n<!-- END AUTO -->"
    DOC.parent.mkdir(parents=True, exist_ok=True)
    text = DOC.read_text(encoding="utf-8") if DOC.is_file() else "# Couverture du quiz — primaire et premier cycle\n\n<!-- BEGIN AUTO -->\n<!-- END AUTO -->\n"
    a, b = text.index("<!-- BEGIN AUTO -->"), text.index("<!-- END AUTO -->") + len("<!-- END AUTO -->")
    DOC.write_text(text[:a] + block + text[b:], encoding="utf-8")


def load(p, default):
    return json.loads(p.read_text(encoding="utf-8")) if p.is_file() else default


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", type=int)
    args = ap.parse_args()
    qs, good, res, fails = quizbank.prepare()
    mine = {(core.COURSES[c]["track"], core.COURSES[c]["level"], core.COURSES[c]["field"]): c for c in SCOPE}
    in_scope = lambda q: (q["track"], q["level"], q["field"]) in mine
    bad = [i for i in res["errors"] if i in {q["id"] for q in qs if in_scope(q)}]
    problems = [p for p in res["bank"] if p.split(" :")[0] in SCOPE]
    if bad or problems or fails:
        print("ERREURS dans le périmètre :", bad[:10], problems, fails[:3])
        return 1
    version = args.version or int(quizbank.VERSION_FILE.read_text().strip())
    dist = quizbank.DIST
    dist.mkdir(parents=True, exist_ok=True)
    cat_path = dist / "catalog.json"
    catalog = load(cat_path, {"format": 1, "version": version, "packs": []})
    for c in SCOPE:
        for old in dist.glob("quiz-%s-p*-v*%s" % (c, pack.PACK_SUFFIX)):
            if old.name.startswith("quiz-%s-p" % c) and old.name[len("quiz-%s-p" % c)].isdigit():
                old.unlink()
    entries = {e["id"]: e for e in catalog["packs"] if e["course"] not in SCOPE}
    import hashlib
    total = 0
    for c in SCOPE:
        cc = core.COURSES[c]
        cq = [q for q in good if (q["track"], q["level"], q["field"]) == (cc["track"], cc["level"], cc["field"])]
        if not cq:
            continue
        parts = pack.split_parts(cq)
        for i, part in enumerate(parts, 1):
            manifest, data = pack.make_pack(c, i, len(parts), part, version)
            name = pack.pack_name(c, i, version)
            (dist / name).write_bytes(data)
            total += len(data)
            entries[manifest["id"]] = {"id": manifest["id"], "course": c, "track": cc["track"], "level": cc["level"], "field": cc["field"],
                                       "part": i, "parts": len(parts), "version": version, "file": name, "size": len(data),
                                       "sha256": hashlib.sha256(data).hexdigest(), "questions": len(part), "byRegion": manifest["byRegion"],
                                       "byDifficulty": manifest["byDifficulty"]}
    order = {c: i for i, c in enumerate(core.COURSES)}
    catalog["packs"] = sorted(entries.values(), key=lambda e: (order.get(e["course"], 99), e["part"]))
    catalog["version"] = version
    cat_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    cov_all = qc.coverage(good)
    cov = load(dist / "coverage.json", {})
    for c in SCOPE:
        cov[c] = cov_all[c]
    cov = {c: cov[c] for c in sorted(cov, key=lambda c: order.get(c, 99))}
    (dist / "coverage.json").write_text(json.dumps(cov, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    src = load(dist / "sources.json", [])
    mine_src = [s for s in facts_engine.sources_report(good) if s["id"].startswith("pc") or s["id"] in ("p-sci", "p-hg", "s3-svt", "s3-hg", "s3-fr")]
    ids = {s["id"] for s in mine_src}
    src = sorted([s for s in src if s["id"] not in ids] + mine_src, key=lambda s: s["id"])
    (dist / "sources.json").write_text(json.dumps(src, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    rej = load(dist / "rejected.json", {})
    (dist / "rejected.json").write_text(json.dumps(dict(sorted(rej.items())), ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    write_doc(good)
    n = len([q for q in good if in_scope(q)])
    print("Périmètre : %d parcours, %d questions, %d packs (%.2f Mo) — version %d" % (len(SCOPE), n, sum(1 for e in entries.values() if e["course"] in SCOPE), total / 1e6, version))
    for c in SCOPE:
        v = cov_all[c]
        print("  %-7s %5d questions (calculées %d, factuelles %d) | parties sans répétition : %d / difficulté : %d" % (c, v["total"], v["computed"], v["fact"], v["games"], v["games_by_difficulty"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
