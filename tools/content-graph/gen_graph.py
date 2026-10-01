#!/usr/bin/env python3
"""Generates content/graph/<domain>.json and content/graph/scopes.json from the compact seeds in seed/<domain>.txt.

Seed line (fields separated by « | », '#' starts a comment):
    slug | level | years | titre fr | title en | prereqs (comma; slug of the domain, or a full id of another domain) | minutes | tags

  level  N0..N4
  years  y6 or y3-5 (year of schooling, docs/CONTENT-ARCHITECTURE.md § 3; y0 = maternelle ... y13 = terminale, y14-15 = L1-L2/BTS)
  The skill is introduced in the FIRST year: its lots are the scopes of that year (both subsystems unless the domain is
  fr-only / en-only) at its level (N0-N1 -> <class>, N2-N4 -> <class>-exc). Exams are derived from the years (N1 only).

Usage: gen_graph.py [--check]   (--check: fail if the committed JSON differs from the generated one)
"""
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "content", "graph"))

# year -> (francophone scope, anglophone scope, fr title, en title)
YEARS = {
    0: ("mat", "nursery", "Maternelle", "Nursery"), 1: ("sil", "class1", "SIL", "Class 1"), 2: ("cp", "class2", "CP", "Class 2"),
    3: ("ce1", "class3", "CE1", "Class 3"), 4: ("ce2", "class4", "CE2", "Class 4"), 5: ("cm1", "class5", "CM1", "Class 5"),
    6: ("cm2", "class6", "CM2", "Class 6"), 7: ("6e", "form1", "6e", "Form 1"), 8: ("5e", "form2", "5e", "Form 2"),
    9: ("4e", "form3", "4e", "Form 3"), 10: ("3e", "form4", "3e", "Form 4"), 11: ("2nde", "form5", "2nde", "Form 5"),
    12: ("1ere", "lower-sixth", "Première", "Lower Sixth"), 13: ("tle", "upper-sixth", "Terminale", "Upper Sixth"),
    14: ("l1", "l1-en", "Licence 1 / BTS 1", "Level 1 (L1) / HND 1"), 15: ("l2", "l2-en", "Licence 2 / BTS 2", "Level 2 (L2) / HND 2"),
}
EXAMS = {6: ["cep", "fslc"], 10: ["bepc"], 11: ["gce-ol"], 12: ["probatoire"], 13: ["bac", "gce-al"], 14: [], 15: ["bts", "licence"]}
QUIZ_FIELDS = ["droit", "economie", "mathematiques", "physique", "psychologie", "geographie", "litterature", "histoire",
               "informatique", "chimie", "biologie", "philosophie", "sociologie"]
DOMAINS = {  # file -> (prefix, fr, en, subsystems)
    "mathematiques": ("math", "Mathématiques", "Mathematics", "both"),
    "francais": ("fra", "Langue française", "French language", "fr"),
    "english": ("eng", "Langue anglaise", "English language", "both"),
    "physique-chimie": ("phc", "Physique-chimie", "Physics and chemistry", "both"),
    "svt": ("svt", "Sciences de la vie et de la Terre", "Biology and Earth science", "both"),
    "histoire-geo-ecm": ("hge", "Histoire-géographie-ECM", "History, geography and civic education", "both"),
    "informatique": ("inf", "Informatique et numérique", "Computing and digital skills", "both"),
}


def scopes():
    out = []
    for y, (fr, en, tfr, ten) in YEARS.items():
        for sc, lang, t in ((fr, "fr", tfr), (en, "en", ten)):
            out.append({"id": sc, "title": {"fr": "Apprendre / Quiz — " + t, "en": "Learn / Quiz — " + t}, "lang": lang, "ord": y,
                        "kind": "class", "levels": ["N0", "N1"], "tv": True, "feature": "learn|quiz"})
            out.append({"id": sc + "-exc", "title": {"fr": "Excellence — " + t, "en": "Excellence — " + t}, "lang": lang, "ord": y,
                        "kind": "class", "levels": ["N2", "N3", "N4"], "tv": True, "feature": "learn|quiz"})
            out.append({"id": sc + "-media", "title": {"fr": "Médias lourds — " + t, "en": "Heavy media — " + t}, "lang": lang, "ord": y,
                        "kind": "media", "levels": ["N0", "N1", "N2", "N3", "N4"], "tv": False, "feature": "learn"})
    for sid, tfr, ten, y in (("alpha-adultes", "Adultes : lecture, écriture, calcul", "Adults: reading, writing, arithmetic", 1),
                             ("vie-pratique", "Vie pratique", "Practical life", 6)):
        out.append({"id": sid, "title": {"fr": tfr, "en": ten}, "lang": "fr", "ord": y, "kind": "theme", "levels": ["N0", "N1"], "tv": True, "feature": "learn|quiz"})
        out.append({"id": sid + "-exc", "title": {"fr": tfr + " (excellence)", "en": ten + " (excellence)"}, "lang": "fr", "ord": y, "kind": "theme",
                    "levels": ["N2", "N3", "N4"], "tv": True, "feature": "learn|quiz"})
    # quiz-only themes: general knowledge (70 % Cameroon / 20 % Africa / 10 % World) and university fields
    for sid, tfr, ten in (("culture-cm", "Culture générale : Cameroun", "General knowledge: Cameroon"),
                          ("culture-af", "Culture générale : Afrique", "General knowledge: Africa"),
                          ("culture-monde", "Culture générale : Monde", "General knowledge: World")):
        out.append({"id": sid, "title": {"fr": tfr, "en": ten}, "lang": "fr", "ord": 6, "kind": "theme", "levels": ["N1"], "tv": True, "feature": "quiz"})
    for fld in QUIZ_FIELDS:
        for lv, y in (("l1", 14), ("l2", 15), ("l3", 15)):
            out.append({"id": fld + "-" + lv, "title": {"fr": "Quiz %s %s" % (fld, lv.upper()), "en": "Quiz %s %s" % (fld, lv.upper())}, "lang": "fr", "ord": y,
                        "kind": "theme", "levels": ["N1"], "tv": True, "feature": "quiz"})
    return out


def paths():
    """Learner paths = ORDERED SETS OF LOTS (rule B): class by class, base lots first, then excellence, then phone-only media."""
    def lots_of(years, langs):
        out = []
        for lang in langs:
            for y in years:
                sc = YEARS[y][0 if lang == "fr" else 1]
                out += ["learn:" + sc, "quiz:" + sc, "learn:" + sc + "-exc", "quiz:" + sc + "-exc", "learn:" + sc + "-media"]
        return out
    defs = [("primaire-fr", "Parcours primaire francophone (maternelle à CM2)", "Francophone primary path (nursery to CM2)", range(0, 7), ["fr"]),
            ("college-fr", "Parcours collège francophone (6e à 3e)", "Francophone lower-secondary path (6e to 3e)", range(7, 11), ["fr"]),
            ("lycee-fr", "Parcours lycée francophone (2nde à Terminale)", "Francophone upper-secondary path (2nde to Tle)", range(11, 14), ["fr"]),
            ("primaire-en", "Parcours primaire anglophone (Nursery à Class 6)", "Anglophone primary path (Nursery to Class 6)", range(0, 7), ["en"]),
            ("secondaire-en", "Parcours secondaire anglophone (Form 1 à Upper Sixth)", "Anglophone secondary path (Form 1 to Upper Sixth)", range(7, 14), ["en"]),
            ("superieur", "Parcours supérieur (L1-L2 / BTS)", "Higher-education path (L1-L2 / HND)", range(14, 16), ["fr", "en"]),
            ("scolarite-fr", "Toute la scolarité francophone, maternelle à Terminale", "Whole francophone schooling, nursery to Tle", range(0, 14), ["fr"]),
            ("scolarite-en", "Toute la scolarité anglophone, Nursery à Upper Sixth", "Whole anglophone schooling, Nursery to Upper Sixth", range(0, 14), ["en"])]
    res = [{"id": i, "title": {"fr": f, "en": e}, "lots": lots_of(ys, ls)} for i, f, e, ys, ls in defs]
    res.append({"id": "adulte-debutant", "title": {"fr": "Adulte débutant : alphabétisation, vie pratique, puis primaire", "en": "Adult beginner: literacy, practical life, then primary"},
                "lots": ["learn:alpha-adultes", "quiz:alpha-adultes", "learn:vie-pratique", "quiz:vie-pratique"] + lots_of(range(1, 7), ["fr"])})
    return res


def parse_years(t):
    t = t.strip().lstrip("y")
    if "-" in t:
        a, b = t.split("-"); return list(range(int(a), int(b) + 1))
    return [int(t)]


def gen_domain(name):
    prefix, tfr, ten, sub = DOMAINS[name]
    skills, slugs = [], set()
    for ln, line in enumerate(open(os.path.join(HERE, "seed", name + ".txt"), encoding="utf-8"), 1):
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        f = [x.strip() for x in line.split("|")]
        if len(f) != 8:
            sys.exit("%s.txt:%d: 8 champs attendus, %d trouvés" % (name, ln, len(f)))
        slug, level, years, fr, en, pre, minutes, tags = f
        if slug in slugs:
            sys.exit("%s.txt:%d: slug en double %s" % (name, ln, slug))
        slugs.add(slug)
        ys = parse_years(years)
        first = ys[0]
        suffix = "-exc" if level in ("N2", "N3", "N4") else ""
        classes, lots = [], []
        for y in ys:
            for sc, lang in ((YEARS[y][0], "fr"), (YEARS[y][1], "en")):
                if sub == "both" or sub == lang:
                    classes.append(sc)
        for sc, lang in ((YEARS[first][0], "fr"), (YEARS[first][1], "en")):
            if sub == "both" or sub == lang:
                lots.append(sc + suffix)
        exams = []
        if level == "N1":
            for y in ys:
                for x in EXAMS.get(y, []):
                    if x not in exams and (sub == "both" or (x in ("cep", "bepc", "probatoire", "bac", "bts", "licence") if sub == "fr" else x not in ("cep", "bepc", "probatoire", "bac"))):
                        exams.append(x)
        prereq = [p if "." in p else prefix + "." + p for p in pre.split(",") if p.strip()] if pre else []
        skills.append({"id": prefix + "." + slug, "title": {"fr": fr, "en": en}, "level": level, "prereq": [p.strip() for p in prereq],
                       "years": ys, "classes": classes, "exams": exams, "lots": lots, "minutes": int(minutes),
                       "tags": [t.strip() for t in tags.split(",") if t.strip()]})
    return {"format": 1, "domain": name, "prefix": prefix, "title": {"fr": tfr, "en": ten}, "skills": skills}


def main():
    check = "--check" in sys.argv
    files = {"scopes.json": {"format": 1, "scopes": scopes()}, "paths.json": {"format": 1, "paths": paths()}}
    for d in DOMAINS:
        if os.path.exists(os.path.join(HERE, "seed", d + ".txt")):
            files[d + ".json"] = gen_domain(d)
    os.makedirs(OUT, exist_ok=True)
    bad = 0
    for fn, obj in files.items():
        text = json.dumps(obj, ensure_ascii=False, indent=1) + "\n"
        path = os.path.join(OUT, fn)
        if check:
            if not os.path.exists(path) or open(path, encoding="utf-8").read() != text:
                print("DIFFÉRENT: " + fn); bad += 1
        else:
            open(path, "w", encoding="utf-8").write(text)
    print("%d fichier(s) %s" % (len(files), "vérifiés" if check else "écrits dans " + OUT))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
