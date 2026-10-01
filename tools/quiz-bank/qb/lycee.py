"""Courses of the lycée / GCE scope (francophone second cycle: 2nde, 1re, Tle; anglophone GCE Ordinary and Advanced Level).

A course is (track, level, field). The app validates `field` against QuizCatalog (droit, economie, mathematiques, physique, psychologie,
geographie, litterature, histoire, informatique, chimie, biologie, philosophie, sociologie), so the SUBJECT is mapped onto that list:

    maths -> mathematiques      phys -> physique        chim -> chimie           svt / bio -> biologie
    philo -> philosophie        fran + angl -> litterature (French literature AND English language/literature in one course)
    hist -> histoire            geo -> geographie       eco / econ -> economie   info / cs -> informatique
    ecm -> droit (citizenship, institutions and rights: the nearest field)

Two subjects that share a field (French + English, English Language + Literature) are ONE course, because two courses with the same
(track, level, field) would put the same questions in two packs; the language of each question is carried by its own `lang`.
Series (A, C, D, E, TI) are named in the question category. Courses are registered when asked for; empty courses give no pack.
"""
from . import core

FR_FIELDS = {"maths": "mathematiques", "phys": "physique", "chim": "chimie", "svt": "biologie", "philo": "philosophie", "fran": "litterature", "angl": "litterature",
             "hist": "histoire", "geo": "geographie", "ecm": "droit", "eco": "economie", "info": "informatique"}
FR_LABELS = {"maths": "Mathématiques", "phys": "Physique", "chim": "Chimie", "svt": "SVT", "philo": "Philosophie", "fran": "Français / Littérature / Anglais",
             "hist": "Histoire", "geo": "Géographie", "ecm": "ECM", "eco": "Économie", "info": "Informatique"}
FR_LEVELS = {"2nde": "2nde", "1re": "1re", "tle": "Tle"}
EN_FIELDS = {"lang": "litterature", "lit": "litterature", "math": "mathematiques", "phys": "physique", "chem": "chimie", "bio": "biologie",
             "geo": "geographie", "hist": "histoire", "econ": "economie", "cs": "informatique"}
EN_LABELS = {"lit": "English Language and Literature", "math": "Mathematics", "phys": "Physics", "chem": "Chemistry", "bio": "Biology", "geo": "Geography",
             "hist": "History", "econ": "Economics", "cs": "Computer Science"}
EN_LEVELS = {"f5": ("Form 5", "GCE Ordinary Level"), "l6": ("Lower Sixth", "GCE Advanced Level, Lower Sixth"), "u6": ("Upper Sixth", "GCE Advanced Level, Upper Sixth")}
ALIAS_FR = {"angl": "fran"}
ALIAS_EN = {"lang": "lit"}


def fr(level, subject):
    subject = ALIAS_FR.get(subject, subject)
    key = "%s-%s" % (level, subject)
    if key not in core.COURSES:
        core.COURSES[key] = dict(track="secondary", level=FR_LEVELS[level], field=FR_FIELDS[subject], prefix="ly-" + key,
                                 label="Lycée · %s · %s" % (FR_LEVELS[level], FR_LABELS[subject]))
    return key


def en(level, subject):
    subject = ALIAS_EN.get(subject, subject)
    key = "%s-%s" % (level, subject)
    if key not in core.COURSES:
        lv, name = EN_LEVELS[level]
        core.COURSES[key] = dict(track="secondary", level=lv, field=EN_FIELDS[subject], prefix="ly-" + key, lang="en",
                                 label="%s · %s" % (name, EN_LABELS[subject]))
    return key
