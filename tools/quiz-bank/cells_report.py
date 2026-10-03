#!/usr/bin/env python3
"""Matrice des 39 cellules « Supérieur » (niveau L1/L2/L3 × 13 filières) : nombre de questions par cellule.

Compte les questions de content/quiz/batches/*.json ET celles des parcours générés (générateurs, fiches de faits), après
le contrôle qualité, comme le fait la construction des lots. Affiche la matrice puis la liste des cellules vides
(« bientôt » sur la TV). Ne modifie RIEN (ni dist, ni lots, ni batches).

Usage : python3 tools/quiz-bank/cells_report.py [--batches-only] [--strict]
  --batches-only  ne lit que les lots d'écriture (rapide, sans les générateurs ni le contrôle qualité)
  --strict        code de sortie 1 s'il reste au moins une cellule vide (vérification « plus aucun bientôt »)
"""
import argparse
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import quizbank  # noqa: E402
from qb import core, importer  # noqa: E402

LEVELS = ("L1", "L2", "L3")
FIELDS = ("droit", "economie", "mathematiques", "physique", "psychologie", "geographie", "litterature", "histoire",
          "informatique", "chimie", "biologie", "philosophie", "sociologie")     # ordre de QuizCatalog.fields (Kotlin)


def count_cells(batches_only=False):
    """Counter {(niveau, filière): nombre de questions} pour la voie supérieure."""
    if batches_only:
        qs = importer.load_batches(quizbank.BATCHES)
    else:
        _, qs, _, _ = quizbank.prepare(strict=False)
    return Counter((q["level"], q["field"]) for q in qs if q["track"] == "higher")


def render(counts):
    w = 14
    lines = ["%-*s" % (w, "filière") + "".join("%8s" % lv for lv in LEVELS)]
    for f in FIELDS:
        lines.append("%-*s" % (w, f) + "".join("%8s" % (counts[(lv, f)] or "-") for lv in LEVELS))
    lines.append("%-*s" % (w, "TOTAL") + "".join("%8d" % sum(counts[(lv, f)] for f in FIELDS) for lv in LEVELS))
    empty = [(lv, f) for lv in LEVELS for f in FIELDS if not counts[(lv, f)]]
    lines.append("")
    lines.append("Cellules vides (%d sur %d) : %s" % (len(empty), len(LEVELS) * len(FIELDS),
                 ", ".join("%s/%s" % (lv, f) for lv, f in empty) or "aucune : plus aucun « bientôt »"))
    return lines, empty


def registered():
    """Cellules sans parcours enregistré (ne devrait jamais arriver : le rédacteur ne pourrait pas importer)."""
    have = {(c["level"], c["field"]) for c in core.COURSES.values() if c["track"] == "higher"}
    return [(lv, f) for lv in LEVELS for f in FIELDS if (lv, f) not in have]


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--batches-only", action="store_true")
    ap.add_argument("--strict", action="store_true")
    a = ap.parse_args(argv)
    quizbank.load_generators()
    lines, empty = render(count_cells(a.batches_only))
    print("\n".join(lines))
    missing = registered()
    if missing:
        print("ERREUR : cellules sans parcours enregistré : %s" % missing)
        return 2
    return 1 if (a.strict and empty) else 0


if __name__ == "__main__":
    sys.exit(main())
