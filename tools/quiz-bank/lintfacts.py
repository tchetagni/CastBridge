#!/usr/bin/env python3
"""Contrôle rapide d'UN module de faits : python3.12 lintfacts.py qb/facts/cult_xxx.py [...]
Construit les questions du module, applique le contrôle qualité question par question et signale les doublons internes."""
import importlib
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from qb import facts_engine, qc  # noqa: E402
from qb.core import norm  # noqa: E402


def main(files):
    for f in files:
        importlib.import_module("qb.facts." + Path(f).stem)
    qs = facts_engine.run()
    bad = 0
    for q in qs:
        e, w = qc.check_question(q)
        for m in e + w:
            print("%s %s | %s" % ("ERREUR" if m in e else "avert.", m, q["question"][:90])); bad += m in e
    res = qc.check_bank(qs)
    for i, errs in res["errors"].items():
        print("ERREUR banque", i, errs)
    for i, ws in res["warnings"].items():
        print("avert. banque", i, ws)
    d = Counter(q["difficulty"] for q in qs)
    longest = sum(1 for q in qs if len(q["choices"][q["answer"]]) > max(len(x) for i, x in enumerate(q["choices"]) if i != q["answer"]))
    print("%d questions | régions %s | difficultés %s | bonne réponse la plus longue : %d %%" % (
        len(qs), dict(Counter(q["region"] for q in qs)), [d[i] for i in range(1, 6)], 100 * longest // max(1, len(qs))))
    return 1 if bad or res["errors"] else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
