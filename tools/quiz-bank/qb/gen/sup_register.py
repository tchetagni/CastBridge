"""Parcours de l'enseignement supérieur et professionnel ajoutés (L2, L3, informatique, santé publique, méthodologie).

Enregistrés ici plutôt que dans core.py pour ne pas toucher aux parcours des autres périmètres. Les champs (`field`) sont
ceux du catalogue de l'appli (QuizCatalog.fields) : aucun nouveau champ n'est inventé.
"""
from ..core import CAP_SCALE, COURSES


def _c(prefix, level, field, label):
    return dict(track="higher", level=level, field=field, prefix=prefix, label=label)


COURSES.update({
    "l2-droit":  _c("hd2", "L2", "droit",         "Supérieur · L2 Droit"),
    "l3-droit":  _c("hd3", "L3", "droit",         "Supérieur · L3 Droit"),
    "l2-eco":    _c("he2", "L2", "economie",      "Supérieur · L2 Économie"),
    "l3-eco":    _c("he3", "L3", "economie",      "Supérieur · L3 Économie"),
    "l2-maths":  _c("hm2", "L2", "mathematiques", "Supérieur · L2 Mathématiques"),
    "l1-info":   _c("hi1", "L1", "informatique",  "Supérieur · L1 Informatique"),
    "l2-info":   _c("hi2", "L2", "informatique",  "Supérieur · L2 Informatique"),
    "l3-info":   _c("hi3", "L3", "informatique",  "Supérieur · L3 Informatique"),
    "l1-bio":    _c("hb1", "L1", "biologie",      "Supérieur · L1 Biologie (santé publique de base)"),
    "l1-socio":  _c("hs1", "L1", "sociologie",    "Supérieur · L1 Sociologie (méthodologie)"),
})
CAP_SCALE.update({"l2-maths": 1.6, "l1-info": 1.6, "l2-info": 1.6, "l2-eco": 1.6})

# Parcours de ce périmètre (utilisé par `quizbank.py build --superieur` : seuls ces packs sont réécrits)
SUPERIEUR = ("l1-droit", "l2-droit", "l3-droit", "l1-eco", "l2-eco", "l3-eco", "l1-maths", "l2-maths",
             "l1-info", "l2-info", "l3-info", "l1-bio", "l1-socio")
