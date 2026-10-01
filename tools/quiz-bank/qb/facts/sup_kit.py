"""Outils pour écrire des tables de faits du supérieur (droit, économie, gestion, sciences politiques, informatique…).

Chaque ligne est une connaissance ; elle donne plusieurs questions différentes (sens direct, sens inverse, classement).
Les mauvaises réponses viennent du MÊME groupe (même sujet), pour rester plausibles.
Toutes les questions sortent en statut `review` (voir facts_engine) : une relecture humaine reste nécessaire.
"""
from .. import gen as _gen  # noqa: F401
from ..gen import sup_register  # noqa: F401  (enregistre les parcours)
from ..facts_engine import fq, pairs, pick, source  # noqa: F401
from ..core import norm
from collections import Counter


def cap(s):
    return s[:1].upper() + s[1:]


def table(course, tpl, rows, *, cat, src, fwd=None, rev=None, region="WORLD", diff=2, extra_a=(), extra_b=(), expl="{A} : {B}.", min_rows=5):
    """rows = (a, b[, difficulté]) en minuscules initiales (a = notion, b = définition / rôle / effet).
    fwd : texte avec {a}  -> réponse b ; rev : texte avec {b} -> réponse a. Les réponses sont mises en majuscule initiale.
    Un groupe doit avoir au moins `min_rows` lignes (sinon pas assez de mauvaises réponses plausibles)."""
    rows = [(r[0], r[1], r[2] if len(r) > 2 else diff) for r in rows]
    assert len(rows) + len(extra_b) >= min_rows, "groupe trop petit : " + tpl
    # {a}/{b} dans la question gardent la forme d'origine (minuscule) ; les réponses sont en majuscule initiale
    bs = Counter(norm(r[1]) for r in rows)
    as_ = Counter(norm(r[0]) for r in rows)
    col_a = [cap(r[0]) for r in rows] + [cap(x) for x in extra_a]
    col_b = [cap(r[1]) for r in rows] + [cap(x) for x in extra_b]
    for a, b, d in rows:
        A, B = cap(a), cap(b)
        if fwd and as_[norm(a)] == 1:
            same = {cap(r[1]) for r in rows if norm(r[0]) == norm(a)}
            fq(course, tpl + "-fwd", fwd.format(a=a, A=A, b=b, B=B), B, pick(tpl + a, col_b, B, 9, same), expl.format(a=a, A=A, b=b, B=B), src, region, cat, d)
        if rev and bs[norm(b)] == 1:
            same = {cap(r[0]) for r in rows if norm(r[1]) == norm(b)}
            fq(course, tpl + "-rev", rev.format(a=a, A=A, b=b, B=B), A, pick(tpl + b, col_a, A, 9, same), expl.format(a=a, A=A, b=b, B=B), src, region, cat, d)


def classify(course, tpl, groups, *, cat, src, fwd, rev, region="WORLD", diff=2, expl="{item} relève de {group}.", diffs=None):
    """groups = {nom_du_groupe: [éléments]} ; les groupes sont DISJOINTS (un élément n'appartient qu'à un groupe).
    fwd : « À quelle catégorie appartient {item} ? » -> nom du groupe ; rev : « Lequel de ces éléments relève de {group} ? » -> élément."""
    names = list(groups)
    assert len(names) >= 4, "il faut au moins 4 groupes pour 4 choix : " + tpl
    seen = {}
    for g, items in groups.items():
        for it in items:
            assert norm(it) not in seen, "élément dans deux groupes : %s (%s / %s)" % (it, seen[norm(it)], g)
            seen[norm(it)] = g
    for g, items in groups.items():
        others = [x for h, its in groups.items() if h != g for x in its]
        for it in items:
            d = (diffs or {}).get(it, diff)
            fq(course, tpl + "-fwd", fwd.format(item=it, group=g), cap(g), [cap(h) for h in pick(tpl + it, names, g, 9)], expl.format(item=it, group=g), src, region, cat, d)
            fq(course, tpl + "-rev", rev.format(item=it, group=g), cap(it), [cap(x) for x in pick(tpl + g + it, others, it, 9)], expl.format(item=it, group=g), src, region, cat, d)


def mcq(course, tpl, rows, *, cat, src, region="WORLD"):
    """Questions rédigées à la main : rows = (question, bonne réponse, [3 à 5 mauvaises], explication, difficulté)."""
    for q, right, wrongs, expl, d in rows:
        fq(course, tpl, q, right, wrongs, expl, src, region, cat, d)
