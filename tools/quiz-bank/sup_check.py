#!/usr/bin/env python3
"""Vérifie UN module de contenu du supérieur sans charger les autres :
    python3.12 tools/quiz-bank/sup_check.py qb.facts.sup_droit_intro      (ou qb.gen.sup_xxx)
Imprime : nombre de questions par parcours, erreurs du contrôle qualité, avertissements, 8 questions tirées au sort."""
import importlib
import random
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from qb import core, facts_engine, qc  # noqa: E402
from qb.gen import sup_register  # noqa: E402,F401

mod = sys.argv[1]
importlib.import_module(mod)
qs, fails = core.run_generators(only=None if mod.startswith("qb.gen") else {"__none__"})
qs = [q for q in qs if q["id"]]
if mod.startswith("qb.facts"):
    qs = facts_engine.run()
else:
    # seuls les générateurs de CE module
    mine = {g.tpl for g in core.GENS if g.fn.__module__ == mod}
    qs = [q for q in qs if q["tpl"] in mine]
res = qc.check_bank(qs)
print(mod, "->", len(qs), "questions", dict(Counter(q["id"].split("-")[0] for q in qs)), "| par difficulté", sorted(Counter(q["difficulty"] for q in qs).items()))
print("erreurs :", len(res["errors"]), "| avertissements :", len(res["warnings"]), "| fails :", fails)
for i, e in list(res["errors"].items())[:15]:
    print("  ERREUR", i, e)
for i, e in list(res["warnings"].items())[:8]:
    print("  avert.", i, e)
byid = {q["id"]: q for q in qs}
for q in random.Random(1).sample(qs, min(8, len(qs))):
    print("-", q["question"], "->", q["choices"][q["answer"]], "| faux :", [c for k, c in enumerate(q["choices"]) if k != q["answer"]])
sys.exit(1 if res["errors"] or fails else 0)
