"""Courses of the scope « primaire et premier cycle » : francophone CP..4e (CM2 and 3e already exist in core.COURSES)
and anglophone Class 1-6 and Form 1-3 (questions in English, lang "en"). One course = one line of the TV choice screen."""

PC_COURSES = {}


def _add(key, track, level, prefix, label, lang):
    PC_COURSES[key] = dict(track=track, level=level, field=None, prefix=prefix, label=label, lang=lang)


for _k, _l in (("cp", "CP"), ("ce1", "CE1"), ("ce2", "CE2"), ("cm1", "CM1")):
    _add(_k, "primary", _l, "p" + _k, "Primaire · " + _l, "fr")
for _k, _l in (("6e", "6e"), ("5e", "5e"), ("4e", "4e")):
    _add(_k, "secondary", _l, "s" + _k[0], "Secondaire · " + _l, "fr")
for _n in range(1, 7):
    _add("class%d" % _n, "primary", "Class %d" % _n, "ec%d" % _n, "Primary (EN) · Class %d" % _n, "en")
for _n in range(1, 4):
    _add("form%d" % _n, "secondary", "Form %d" % _n, "ef%d" % _n, "Secondary (EN) · Form %d" % _n, "en")

# grade index used by the shared generators: 1 CP/Class 1 ... 6 Class 6, 7 = 6e/Form 1, 8 = 5e/Form 2, 9 = 4e/Form 3
GRADE = {"cp": 1, "ce1": 2, "ce2": 3, "cm1": 4, "6e": 7, "5e": 8, "4e": 9,
         "class1": 1, "class2": 2, "class3": 3, "class4": 4, "class5": 5, "class6": 6, "form1": 7, "form2": 8, "form3": 9}
EXISTING_FR = {"cm2": 5, "3e": 10}
