"""Courses of the lycée / GCE scope (francophone second cycle: 2nde, 1re, Tle; anglophone GCE Ordinary and Advanced Level).

A course is (track, level, field): the field is the SUBJECT. Francophone fields reuse the keys of QuizCatalog when one
exists (mathematiques, physique, chimie, philosophie, litterature, histoire, geographie, economie, informatique) and a
readable label otherwise (SVT, ECM, Anglais) because the app falls back to the key as label. Anglophone fields are
English labels and the questions carry lang "en". Series (A, C, D, E, TI) are named in the question category.
Courses are registered when asked for; empty courses are simply absent from the packs.
"""
from . import core

FR_FIELDS = {"maths": "mathematiques", "phys": "physique", "chim": "chimie", "svt": "SVT", "philo": "philosophie", "fran": "litterature",
             "hist": "histoire", "geo": "geographie", "ecm": "ECM", "angl": "Anglais", "eco": "economie", "info": "informatique"}
FR_LABELS = {"maths": "Mathématiques", "phys": "Physique", "chim": "Chimie", "svt": "SVT", "philo": "Philosophie", "fran": "Français / Littérature",
             "hist": "Histoire", "geo": "Géographie", "ecm": "ECM", "angl": "Anglais", "eco": "Économie", "info": "Informatique"}
FR_LEVELS = {"2nde": "2nde", "1re": "1re", "tle": "Tle"}
EN_FIELDS = {"lang": "English Language", "lit": "Literature", "math": "Mathematics", "phys": "Physics", "chem": "Chemistry", "bio": "Biology",
             "geo": "Geography", "hist": "History", "econ": "Economics", "cs": "Computer Science"}
EN_LEVELS = {"f5": ("Form 5", "GCE Ordinary Level"), "l6": ("Lower Sixth", "GCE Advanced Level, Lower Sixth"), "u6": ("Upper Sixth", "GCE Advanced Level, Upper Sixth")}


def fr(level, subject):
    key = "%s-%s" % (level, subject)
    if key not in core.COURSES:
        core.COURSES[key] = dict(track="secondary", level=FR_LEVELS[level], field=FR_FIELDS[subject], prefix="ly-" + key,
                                 label="Lycée · %s · %s" % (FR_LEVELS[level], FR_LABELS[subject]))
    return key


def en(level, subject):
    key = "%s-%s" % (level, subject)
    if key not in core.COURSES:
        lv, name = EN_LEVELS[level]
        core.COURSES[key] = dict(track="secondary", level=lv, field=EN_FIELDS[subject], prefix="ly-" + key, lang="en",
                                 label="%s · %s" % (name, EN_FIELDS[subject]))
    return key
