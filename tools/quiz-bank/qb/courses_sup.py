"""Parcours « Supérieur » ajoutés pour remplir les 39 cellules niveau × filière (L1/L2/L3 × 13 filières du catalogue).

Fichier à part (comme courses_pc.py) : les rédacteurs de lots écrivent en parallèle, l'enregistrement est fait UNE FOIS.
Les champs (`field`) sont ceux de QuizCatalog.fields (Kotlin) ; la portée (scope) de chaque parcours est `<filière>-<niveau>`
dans lots.py (SCOPES) et dans QuizLotScopes.specs (Kotlin). Préfixe d'identifiant : radical de 2 ou 3 lettres + chiffre du
niveau, unique parmi tous les parcours (vérifié par test_courses_sup.py).
"""

SUP_COURSES = {}

# filière du catalogue -> (clé courte du parcours, radical du préfixe, libellé)
_FIELDS = {
    "physique": ("phys", "hph", "Physique"),
    "psychologie": ("psy", "hps", "Psychologie"),
    "geographie": ("geo", "hge", "Géographie"),
    "litterature": ("lit", "hli", "Littérature"),
    "histoire": ("hist", "hhi", "Histoire"),
    "chimie": ("chim", "hch", "Chimie"),
    "philosophie": ("philo", "hfi", "Philosophie"),
    "biologie": ("bio", "hb", "Biologie"),
    "sociologie": ("socio", "hs", "Sociologie"),
    "mathematiques": ("maths", "hm", "Mathématiques"),
}

# Les 26 cellules SANS parcours avant ce fichier, par niveau.
CELLS = {
    "L1": ("physique", "psychologie", "geographie", "litterature", "histoire", "chimie", "philosophie"),
    "L2": ("physique", "psychologie", "geographie", "litterature", "histoire", "chimie", "biologie", "philosophie", "sociologie"),
    "L3": ("mathematiques", "physique", "psychologie", "geographie", "litterature", "histoire", "chimie", "biologie", "philosophie", "sociologie"),
}

for _level, _fields in CELLS.items():
    for _f in _fields:
        _short, _pre, _label = _FIELDS[_f]
        SUP_COURSES["%s-%s" % (_level.lower(), _short)] = dict(
            track="higher", level=_level, field=_f, prefix=_pre + _level[1], label="Supérieur · %s %s" % (_level, _label))

# Portée de lot de chaque parcours : « <filière>-<niveau> » (même règle que QuizLotScopes.scopeFor : mathematiques -> maths)
_ALIAS = {"mathematiques": "maths"}
SUP_SCOPES = {"%s-%s" % (_ALIAS.get(c["field"], c["field"]), c["level"].lower()): (k, None, c["label"]) for k, c in SUP_COURSES.items()}
