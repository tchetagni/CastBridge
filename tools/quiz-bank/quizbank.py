#!/usr/bin/env python3
"""CastBridge question-bank pipeline (docs/QUIZ.md, section Volume de contenu).

    quizbank.py build [--version N]   generate + check + write packs to content/quiz/dist (deterministic)
    quizbank.py lots [--out DIR]      build the Quiz lots (one per scope, content/quiz/lots) + catalog-lots.json, fail above 3 MB per lot
    quizbank.py check                 generate + check, print the report, write nothing (exit 1 on any error)
    quizbank.py report                coverage table per course (questions, status, games without repeat, what is missing)
    quizbank.py import FILE...        validate expert/AI-written batches and store them in content/quiz/batches/
    quizbank.py approve FILE          mark question ids (one per line, or a JSON list) as approved, with --by NAME

Only the Python standard library is needed. Sources: qb/gen/*.py (computed questions), facts/*.py (sourced facts),
content/quiz/batches/*.json (imported batches), content/quiz/approvals.json (human review decisions).
"""
import argparse
import importlib
import json
import pkgutil
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))

from qb import balance, core, lots, pack, qc  # noqa: E402

QUIZ = ROOT / "content" / "quiz"
DIST = QUIZ / "dist"
LOTS = QUIZ / "lots"
BATCHES = QUIZ / "batches"
APPROVALS = QUIZ / "approvals.json"
VERSION_FILE = QUIZ / "pack-version.txt"


def load_generators():
    import qb.gen
    for m in pkgutil.iter_modules(qb.gen.__path__):
        importlib.import_module("qb.gen." + m.name)
    import qb.facts
    for m in pkgutil.iter_modules(qb.facts.__path__):
        importlib.import_module("qb.facts." + m.name)


def collect(strict=True):
    """All questions of the pipeline: computed + facts + batches, approvals applied. Returns (questions, notes)."""
    load_generators()
    from qb import facts_engine, importer
    qs, fails = core.run_generators(strict=strict)
    qs += facts_engine.run()
    qs += importer.load_batches(BATCHES)
    uniq, seen = [], set()
    for q in qs:                                   # the same question from two generators: keep the first
        k = (q["track"], q["level"], q["field"], core.norm(q["question"]))
        if k not in seen:
            seen.add(k); uniq.append(q)
    qs = balance.balance(uniq)
    approvals = json.loads(APPROVALS.read_text(encoding="utf-8")) if APPROVALS.is_file() else {}
    for q in qs:
        a = approvals.get(q["id"])
        if a and a.get("status") == "approved":
            q["status"] = "approved"
        elif a and a.get("status") in ("rejected", "needs-fix"):   # decisions of tools/content-validation (docs/CONTENT-VALIDATION.md)
            q["status"] = a["status"]
    return qs, fails


def prepare(strict=True):
    qs, fails = collect(strict)
    res = qc.check_bank(qs)
    good = [q for q in qs if q["id"] not in res["dropped"]]
    return qs, good, res, fails


def print_report(good, res, raw_count):
    cov = qc.coverage(good)
    print("Questions générées : %d, gardées : %d, rejetées par le contrôle qualité : %d, avertissements : %d"
          % (raw_count, len(good), len(res["dropped"]), len(res["warnings"])))
    hdr = "%-10s %7s %8s %7s %8s %6s %6s %6s %6s"
    print(hdr % ("parcours", "total", "approved", "review", "calculées", "factuel", "import", "parties", "manque"))
    for c, v in cov.items():
        miss = sum(v["missing"].values()) if "missing" in v else v["missing_total"]
        print(hdr % (c, v["total"], v["approved"], v["review"], v["computed"], v["fact"], v["imported"], v["games"], miss))
    for c, v in cov.items():
        if v["total"]:
            print("  %-9s région %s | difficulté %s | réponses %s | %d modèles" % (
                c, dict(sorted(v["by_region"].items())), list(v["by_difficulty"].values()), list(v["answer_positions"].values()), v["templates"]))
    for p in res["bank"]:
        print("ATTENTION", p)
    return cov


def cmd_build(args):
    qs, good, res, fails = prepare()
    cov = print_report(good, res, len(qs))
    if res["dropped"]:
        print("Rejetées (10 premières) :")
        for i in list(res["dropped"])[:10]:
            print("  ", i, res["errors"][i])
    version = args.version or (int(VERSION_FILE.read_text().strip()) if VERSION_FILE.is_file() else 1)
    if args.courses:
        return build_scope(args, good, res, cov, version)
    catalog = pack.build_packs(good, DIST, version)
    from qb import facts_engine
    (DIST / "sources.json").write_text(json.dumps(facts_engine.sources_report(good), ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    (DIST / "coverage.json").write_text(json.dumps(cov, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    (DIST / "rejected.json").write_text(json.dumps({i: res["errors"][i] for i in sorted(res["dropped"])}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    total = sum(p["size"] for p in catalog["packs"])
    print("Packs : %d fichiers, %.2f Mo au total (%s) dans %s" % (len(catalog["packs"]), total / 1e6, version, DIST))
    per_mb = sum(p["questions"] for p in catalog["packs"]) / (total / 1e6)
    print("Densité : %d questions par Mo de pack compressé" % per_mb)
    return 0


def cmd_lots(args):
    """Builds the Quiz lots (one per scope) into content/quiz/lots + catalog-lots.json; exit 2 if a lot exceeds the size cap."""
    qs, good, res, fails = prepare()
    out = LOTS if not args.out else Path(args.out)
    try:
        catalog, lines = lots.build_lots(good, out)
    except lots.LotTooBig as e:
        print("ERREUR", e)
        return 2
    print("\n".join(lines))
    return 0


def build_scope(args, good, res, cov, version):
    """`build --courses a,b` : réécrit SEULEMENT les packs de ces parcours (les packs des autres périmètres ne sont jamais touchés),
    fusionne catalog.json, coverage.json, sources.json et rejected.json."""
    wanted = [c.strip() for c in args.courses.split(",") if c.strip()]
    if wanted == ["superieur"]:
        from qb.gen.sup_register import SUPERIEUR
        wanted = list(SUPERIEUR)
    unknown = [c for c in wanted if c not in core.COURSES]
    if unknown:
        print("parcours inconnus :", unknown)
        return 2
    catalog = pack.build_scope_packs(good, DIST, version, wanted)
    from qb import facts_engine
    def merge(name, fn):
        f = DIST / name
        cur = json.loads(f.read_text(encoding="utf-8")) if f.is_file() else None
        f.write_text(json.dumps(fn(cur), ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    merge("coverage.json", lambda cur: {**(cur or {}), **{c: cov[c] for c in wanted}})
    prefixes = tuple(core.COURSES[c]["prefix"] + "-" for c in wanted)
    merge("rejected.json", lambda cur: {**{k: v for k, v in (cur or {}).items() if not k.startswith(prefixes)},
                                        **{i: res["errors"][i] for i in sorted(res["dropped"]) if i.startswith(prefixes)}})
    def src(cur):
        new = {s["id"]: s for s in facts_engine.sources_report([q for q in good if (q["id"].startswith(prefixes))])}
        old = {s["id"]: s for s in (cur or [])}
        for i, s in new.items():
            # fiche utilisée seulement par ce périmètre -> compte à jour ; fiche partagée -> on garde le plus grand compte
            old[i] = s if i not in old or old[i]["questions"] <= s["questions"] else old[i]
        return [old[i] for i in sorted(old) if old[i]["questions"] or i in new]
    merge("sources.json", src)
    mine = [p for p in catalog["packs"] if p["course"] in wanted]
    total = sum(p["size"] for p in mine)
    print("Packs (périmètre) : %d fichiers, %.2f Mo, version %s ; %d fichiers au catalogue" % (len(mine), total / 1e6, version, len(catalog["packs"])))
    big = [p["file"] for p in mine if p["size"] > 3_000_000]
    print("ATTENTION lot > 3 Mo :", big) if big else print("Tous les lots < 3 Mo (le plus gros : %d octets)" % max(p["size"] for p in mine))
    return 0


def cmd_check(args):
    qs, good, res, fails = prepare()
    print_report(good, res, len(qs))
    for i in sorted(res["dropped"])[:50]:
        print("ERREUR", i, res["errors"][i])
    return 1 if res["dropped"] or res["bank"] or fails else 0


def cmd_report(args):
    qs, good, res, fails = prepare()
    cov = print_report(good, res, len(qs))
    print()
    for c, v in cov.items():
        print("%s : %s" % (v["label"], "; ".join("%s %d" % (k, n) for k, n in list(v["by_category"].items())[:12])))
    return 0


def cmd_import(args):
    from qb import importer
    load_generators()
    bad = 0
    for f in args.files:
        ok, problems = importer.import_file(Path(f), BATCHES)
        print("%s : %d questions valides importées (statut review)" % (f, ok))
        for p in problems:
            print("   ", p)
        bad += len(problems)
    return 1 if bad else 0


def cmd_approve(args):
    text = Path(args.file).read_text(encoding="utf-8")
    try:
        ids = json.loads(text)
    except ValueError:
        ids = [l.strip() for l in text.splitlines() if l.strip() and not l.startswith("#")]
    cur = json.loads(APPROVALS.read_text(encoding="utf-8")) if APPROVALS.is_file() else {}
    known = {q["id"] for q in collect()[0]}
    unknown = [i for i in ids if i not in known]
    for i in ids:
        if i in known:
            cur[i] = {"status": "approved", "by": args.by, "on": args.date}
    APPROVALS.parent.mkdir(parents=True, exist_ok=True)
    APPROVALS.write_text(json.dumps(dict(sorted(cur.items())), ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print("%d approuvées, %d ids inconnus %s" % (len(ids) - len(unknown), len(unknown), unknown[:5]))
    return 1 if unknown else 0


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("build"); b.add_argument("--version", type=int); b.add_argument("--courses", help="parcours à (ré)écrire seulement, ex. superieur ou l2-droit,l3-droit"); b.set_defaults(fn=cmd_build)
    l = sub.add_parser("lots"); l.add_argument("--out"); l.set_defaults(fn=cmd_lots)
    sub.add_parser("check").set_defaults(fn=cmd_check)
    sub.add_parser("report").set_defaults(fn=cmd_report)
    i = sub.add_parser("import"); i.add_argument("files", nargs="+"); i.set_defaults(fn=cmd_import)
    a = sub.add_parser("approve"); a.add_argument("file"); a.add_argument("--by", required=True); a.add_argument("--date", default="")
    a.set_defaults(fn=cmd_approve)
    args = ap.parse_args(argv)
    return args.fn(args)


if __name__ == "__main__":
    sys.exit(main())
