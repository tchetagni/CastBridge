#!/usr/bin/env python3
"""Writes docs/coverage/quiz-lycee.md : coverage of the lycée / GCE scope per class, subject, model and topic.

    python3 tools/quiz-bank/lycee_report.py        (about 90 s: it runs the whole pipeline)

Everything in the tables comes from the pipeline (same generation and quality control as `quizbank.py build`)."""
import sys
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import quizbank  # noqa: E402
from qb import core  # noqa: E402

OUT = HERE.parents[1] / "docs" / "coverage" / "quiz-lycee.md"
FR_LEVELS = {"2nde": 0, "1re": 1, "Tle": 2}
EN_LEVELS = {"Form 5": 0, "Lower Sixth": 1, "Upper Sixth": 2}
SUBJECTS_FR = ["Mathématiques", "Physique", "Chimie", "SVT", "Philosophie", "Français / littérature", "Anglais", "Histoire", "Géographie", "ECM", "Économie", "Informatique"]
SUBJECTS_EN = ["English Language", "Literature", "Mathematics", "Physics", "Chemistry", "Biology", "Geography", "History", "Economics", "Computer Science"]
FIELD_FR = {"mathematiques": "Mathématiques", "physique": "Physique", "chimie": "Chimie", "biologie": "SVT", "philosophie": "Philosophie", "histoire": "Histoire", "geographie": "Géographie",
            "droit": "ECM", "economie": "Économie", "informatique": "Informatique"}
FIELD_EN = {"mathematiques": "Mathematics", "physique": "Physics", "chimie": "Chemistry", "biologie": "Biology", "histoire": "History", "geographie": "Geography", "economie": "Economics", "informatique": "Computer Science"}
ENGLISH_LANGUAGE_TPL = ("en-irregular", "en-present-perfect", "en-past-simple", "en-comparative", "en-plural", "en-prepositions", "en-conditionals", "en-reported", "en-articles", "lang-notions", "angl-notions")


def subject_of(q):
    """(group, subject) of a question: the francophone and GCE courses are told apart by level; English and French share the `litterature` field."""
    english_level = q["level"] in EN_LEVELS
    tpl = q.get("tpl") or ""
    if q["field"] == "litterature":
        if english_level:
            return "gce", "English Language" if tpl.startswith(ENGLISH_LANGUAGE_TPL) else "Literature"
        return "fr", "Anglais" if q["lang"] == "en" else "Français / littérature"
    return ("gce", FIELD_EN[q["field"]]) if english_level else ("fr", FIELD_FR[q["field"]])


def main():
    qs, good, res, fails = quizbank.prepare()
    mine = [q for q in good if q["id"].startswith("ly-")]
    groups = defaultdict(list)
    for q in mine:
        g, s = subject_of(q)
        groups[(g, s, q["level"])].append(q)
    courses = {(q["level"], q["field"]) for q in mine}
    total = len(mine)
    lines = []
    w = lines.append
    w("# Quiz — second cycle francophone et GCE (Ordinary / Advanced Level) : couverture")
    w("")
    w(f"> Généré le {date.today().isoformat()} par `tools/quiz-bank/lycee_report.py` (mêmes générateurs et mêmes contrôles que `quizbank.py build`). "
      "**Toutes les questions sont `review`, aucune n'est `approved`** : la relecture humaine est prévue environ 3 mois après la diffusion aux bêta-testeurs ; "
      "l'application jouera ce contenu avec la marque « bêta » visible. Les questions de calcul ont leur réponse prouvée par le calcul ; les questions factuelles "
      "restent exclues des parties tant qu'un humain ne les a pas approuvées (règle du pipeline, `docs/QUIZ.md` § 6 ter).")
    w("")
    total_txt = f"{total:,}".replace(",", " ")
    w(f"**Total : {total_txt} questions dans {len(courses)} parcours** (niveau + filière). Le format de pack ne porte ni série ni matière libre : la **filière** de l'application "
      "(`QuizCatalog.fields`, validée à la lecture d'un pack) sert de matière, et la série A / C / D / E / TI est indiquée dans la catégorie de chaque question.")
    w("")
    w("| Matière | Filière du pack (`field`) | Remarque |")
    w("|---|---|---|")
    for a, b, c in (("Mathématiques, Physique, Chimie, Histoire, Géographie, Économie, Informatique, Philosophie", "`mathematiques`, `physique`, `chimie`, `histoire`, `geographie`, `economie`, `informatique`, `philosophie`", ""),
                    ("SVT / Biology", "`biologie`", ""),
                    ("Français, Anglais (lycée), Literature, English Language", "`litterature`", "un seul parcours par niveau ; la langue de chaque question est dans `lang` (`fr` ou `en`)"),
                    ("ECM", "`droit`", "aucune filière « ECM » dans l'application : `droit` (institutions, droits, citoyenneté) est la plus proche")):
        w(f"| {a} | {b} | {c} |")
    w("")
    w("## 1. Comment c'est fabriqué")
    w("- **Modèles de calcul** (`qb/gen/lycee_*.py`) : chaque modèle tire des nombres au hasard (graine fixe, résultat reproductible), calcule la bonne réponse **et la vérifie** (substitution, "
      "fractions exactes, dérivée ou intégrale numérique, énumération complète pour la génétique et les tables de vérité, **exécution Python** pour les algorithmes, équations chimiques "
      "équilibrées atome par atome). Les mauvaises réponses sont des erreurs typiques (signe, oubli d'un terme, mauvaise unité). Une vérification qui échoue arrête la génération.")
    w("- **Faits** (`qb/facts/lycee_*.py`) : tables écrites par l'assistant avec une fiche source par thème (`dist/sources.json`) ; la fiche dit où comparer, jamais que c'est vérifié.")
    w("- **Plafonds** (`qb/gen/lycee_caps.json`, calculés par `lycee_caps.py`) : aucun modèle ne dépasse 8 % d'un parcours ; un parcours qui a trop peu de modèles est limité à environ 500 "
      "questions (voir § 4). Les positions A/B/C/D de la bonne réponse sont rééquilibrées (sel par parcours).")
    w("- **Difficulté** 1 à 5 relative à la classe : fixée par modèle (`diffs=`), pas tirée au hasard.")
    w("")
    w("## 2. Questions par classe et par matière")
    w("")
    w("Parties sans répétition = nombre de parties de 15 questions que la banque seule permet sans reposer une question (règle de la TV : 300 parties demandent 4 500 questions).")
    w("")
    for title, g, levels, subjects in (("Second cycle francophone", "fr", FR_LEVELS, SUBJECTS_FR), ("GCE Ordinary Level (Form 5) et Advanced Level (Lower / Upper Sixth), en anglais", "gce", EN_LEVELS, SUBJECTS_EN)):
        w(f"### {title}")
        w("")
        w("| Matière | Classe | Questions | calculées | factuelles | modèles | difficulté 1·2·3·4·5 | parties sans répétition |")
        w("|---|---|---:|---:|---:|---:|---|---:|")
        keys = sorted((k for k in groups if k[0] == g), key=lambda k: (subjects.index(k[1]) if k[1] in subjects else 99, levels[k[2]]))
        for k in keys:
            v = groups[k]
            d = Counter(q["difficulty"] for q in v)
            comp = [q for q in v if q["verif"] == "computed"]
            w(f"| {k[1]} | {k[2]} | {len(v)} | {len(comp)} | {len(v) - len(comp)} | {len({q['tpl'] for q in comp})} | {'·'.join(str(d.get(i, 0)) for i in range(1, 6))} | {len(v) // core.PER_GAME} |")
        w("")
    w("## 3. Thèmes couverts (catégories) et modèles, par matière et par classe")
    w("")
    for title, g, levels, subjects in (("Francophone", "fr", FR_LEVELS, SUBJECTS_FR), ("GCE (anglais)", "gce", EN_LEVELS, SUBJECTS_EN)):
        w(f"### {title}")
        w("")
        keys = sorted((k for k in groups if k[0] == g), key=lambda k: (subjects.index(k[1]) if k[1] in subjects else 99, levels[k[2]]))
        for k in keys:
            v = groups[k]
            cats = Counter(q["category"] for q in v)
            tpls = Counter(q["tpl"] for q in v if q["verif"] == "computed")
            w(f"**{k[1]} — {k[2]}** : " + " ; ".join(f"{n} ({m})" for n, m in sorted(cats.items(), key=lambda x: -x[1])[:14]) + ".")
            if tpls:
                w("  - modèles : " + ", ".join(f"`{t}` {m}" for t, m in sorted(tpls.items())))
            w("")
    calc = sorted((len(v), k) for k, v in groups.items() if sum(1 for q in v if q["verif"] == "computed") > 100)
    lo, hi = calc[0], calc[-1]
    w("## 4. Lacunes connues (à lire avant de se fier aux chiffres)")
    w("")
    w(f"- **Volume** : le tirage « 300 parties sans répétition » (4 500 questions par parcours) n'est atteint par **aucune** matière de ce périmètre (maximum : {hi[0]} questions pour {hi[1][1]} {hi[1][2]}, soit {hi[0] // 15} parties). "
      f"Les matières de calcul comptent de {lo[0]} à {hi[0]} questions par classe. La limite n'est pas la place (quelques dizaines de Ko par paquet) mais la qualité : une règle du pipeline interdit qu'un modèle dépasse 8 % d'un parcours, "
      "donc un parcours n'est grand que s'il a au moins une quinzaine de modèles différents ; sinon il est limité à environ 500 questions. **Pour grossir un parcours il faut ajouter des modèles** (pas des variantes) "
      "puis relancer `lycee_caps.py`.")
    w("- **Séries** : A, C, D, E, TI ne sont pas des champs du format ; elles figurent dans la catégorie. Les thèmes de maths et de physique de Tle séries C / D / E sont couverts, "
      "pas les spécialités de la série TI (électronique, génie).")
    w("- **Matières fortement factuelles** (histoire, philosophie, littérature, ECM, géographie de Tle) : de 10 à 100 questions par classe, **toutes à relire** ; elles ne sont **pas jouables** "
      "tant qu'elles ne sont pas approuvées (règle `review` + `fact`). Les œuvres au programme changent chaque année : seuls des auteurs et ouvrages très enseignés sont utilisés.")
    w("- **Répartition par classe indicative** : la correspondance thème → classe (par exemple l'histoire du Cameroun en Tle) a été faite sans le texte officiel sous les yeux ; "
      "à confronter aux programmes MINESEC et au syllabus du GCE Board par un enseignant.")
    w("- **Filières de l'application** : sans filière « ECM » ni « Anglais » dans `QuizCatalog`, l'ECM est classée sous `droit` et l'anglais avec la littérature ; une partie « Littérature » d'une classe "
      "francophone mêle donc le français et l'anglais. Si l'application ajoute ces filières, il suffira de changer `qb/lycee.py` (une table) et de reconstruire.")
    w("- **Tirage sans filière** : au niveau secondaire, une partie lancée sans choisir de filière puise dans toutes les matières de la classe qui sont jouables (questions de calcul `computed`). "
      "C'est le comportement de `QuestionFilter` ; à régler côté application si on veut forcer le choix d'une matière.")
    w("- **GCE** : pas de couverture des épreuves pratiques ni des cartes (Geography Paper 2), ni des textes imposés de Literature d'une année donnée ; pas de questions à réponse longue.")
    w("- **Anglais / English Language** : grammaire et vocabulaire (formes verbales, comparatifs, pluriels, prépositions, conditionnels, discours rapporté, articles, notions de rédaction) ; "
      "pas de compréhension de texte (pas de passage fourni, donc pas de question).")
    w("- **ECM, informatique, SVT, économie** : peu de faits par classe ; la partie calculée (génétique, écologie, comptabilité nationale, représentation des données, algorithmes) est plus riche que la partie « cours ».")
    w("- **Sources** : `dist/sources.json` donne une fiche par thème de faits ; aucune n'a été vérifiée par un humain.")
    w("")
    w("## 5. Où sont les paquets, et comment les reconstruire")
    w("")
    w("```sh")
    w("python3.12 tools/quiz-bank/quizbank.py check     # génère + contrôle (code 0 = aucune erreur, aucun plafond dépassé)")
    w("python3.12 tools/quiz-bank/lycee_caps.py         # recalcule les plafonds et les sels (après avoir ajouté ou modifié un modèle)")
    w("python3.12 tools/quiz-bank/quizbank.py build     # écrit content/quiz/dist (un paquet par parcours et par tranche de 1 500 questions)")
    w("python3.12 tools/quiz-bank/lycee_report.py       # régénère ce document")
    w("```")
    w("")
    w("Python 3.12 est nécessaire (les générateurs plus anciens du dépôt utilisent des f-strings de 3.12). Un paquet pèse de 10 à 80 Ko : bien en dessous des 3 Mo d'un lot.")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("écrit", OUT, "(%d lignes)" % len(lines))


if __name__ == "__main__":
    main()
