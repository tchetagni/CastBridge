"""Petit outil pour les modules de culture générale rédigés à la main (cult_*.py).

Un module déclare sa fiche source avec `theme(...)`, puis ses questions avec `rows(...)` (questions à 4 réponses rédigées)
ou `table(...)` (tableaux de faits : une question dans chaque sens). Tout sort en statut `review` : rien n'est « approuvé »
tant qu'une personne n'a pas relu (content/quiz/approvals.json).
"""
from ..facts_engine import fq, pairs, source

COURSE = "general"
REGIONS = ("CM", "AF", "WORLD")


def theme(id_, title, check):
    """Fiche source du thème : dit où comparer les faits, sans prétendre que la vérification a été faite."""
    source("cult-" + id_, title, "rédaction assistant (à relire)", check)


def rows(id_, region, cat, items):
    """items : (difficulté 1-5, question, bonne réponse, [mauvaises réponses (3 à 6)], explication[, catégorie])."""
    assert region in REGIONS, region
    for it in items:
        diff, text, right, wrongs, expl = it[:5]
        assert 1 <= diff <= 5, text
        assert isinstance(wrongs, (list, tuple)) and len(wrongs) >= 3, text
        fq(COURSE, "cult-" + id_, text, right, list(wrongs), expl, "cult-" + id_, region, it[5] if len(it) > 5 else cat, diff)


def table(id_, region, cat, items, fwd=None, rev=None, *, diff=3, expl="{a} : {b}.", rev_expl=None, extra_a=(), extra_b=(), k=3):
    """items : (a, b[, difficulté]). `fwd` demande b d'après a (utilise {a}), `rev` demande a d'après b (utilise {b}).
    La question inverse n'est posée que si b est unique dans le tableau. Les mauvaises réponses viennent du même tableau."""
    assert region in REGIONS, region
    pairs(COURSE, "cult-" + id_, items, fwd, rev, region=region, cat=cat, src="cult-" + id_, diff=diff, expl=expl, rev_expl=rev_expl,
          extra_a=extra_a, extra_b=extra_b, k=k)
