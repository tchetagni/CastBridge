#!/usr/bin/env python3
"""Génère content/graph/langue-<code>.json : graphe de compétences d'une langue (docs/LANGUES.md § 3.6). Déterministe.
Format = celui de content/graph/<domaine>.json de l'architecture du contenu (champs additifs : cefr, skill).
Squelette commun : 4 compétences x 8 niveaux (chaque compétence exige la même au niveau d'avant) + nœuds propres à chaque langue."""
import json, os, sys

LEVELS = ["a0", "a1", "a2", "b1", "b2", "c1", "c2", "natif"]
SKILLS = [("co", "Compréhension orale"), ("ce", "Compréhension écrite"), ("po", "Expression orale"), ("pe", "Expression écrite")]
# (niveau, id court, titre, prérequis courts)  — les prérequis sont d'un niveau <= au nœud
SPECIFIC = {
 "zh": [("a0", "pinyin", "Pinyin et initiales/finales", []), ("a0", "tons", "Les quatre tons et le ton neutre", ["pinyin"]),
        ("a0", "traits", "Traits de base et ordre des traits", []), ("a1", "chars150", "150 caractères (HSK 1)", ["traits", "pinyin"]),
        ("a1", "mesure", "Classificateurs (个, 本…) et nombres", ["tons"]), ("a2", "chars300", "300 caractères (HSK 2)", ["chars150"]),
        ("a2", "aspect", "Particules 了, 过, 着 et aspect", ["mesure"]), ("b1", "chars600", "600 caractères (HSK 3)", ["chars300"]),
        ("b1", "ba", "Constructions 把 et 被", ["aspect"]), ("b2", "chars1200", "1200 caractères (HSK 4-5)", ["chars600"]),
        ("c1", "chengyu", "Chengyu et registres (HSK 6)", ["chars1200"]), ("natif", "dialectes", "Variétés, argot, littérature classique", ["chengyu"])],
 "ja": [("a0", "hira", "Hiragana", []), ("a0", "kata", "Katakana", ["hira"]), ("a1", "kanji80", "80 kanji (N5)", ["hira"]),
        ("a1", "polit1", "Forme en です/ます", ["hira"]), ("a2", "kanji300", "300 kanji (N4)", ["kanji80"]), ("a2", "te", "Forme en て et enchaînements", ["polit1"]),
        ("b1", "kanji650", "650 kanji (N3)", ["kanji300"]), ("b1", "plain", "Style neutre et conversation", ["te"]),
        ("b2", "kanji1000", "1000 kanji (N2)", ["kanji650"]), ("b2", "keigo", "Keigo : honorifique et humble", ["plain"]),
        ("c1", "kanji2000", "2000 kanji (N1)", ["kanji1000"]), ("natif", "registres", "Registres, dialectes, littérature", ["keigo"])],
 "en": [("a0", "alpha", "Alphabet et sons de base", []), ("a1", "be", "Être, avoir, présent simple", ["alpha"]), ("a2", "past", "Prétérit et présent perfect", ["be"]),
        ("b1", "modals", "Modaux et conditionnel", ["past"]), ("b2", "phrasal", "Verbes à particule", ["modals"]), ("c1", "registre", "Registres et nuances (formel, familier)", ["phrasal"]),
        ("natif", "idiomes", "Idiomes, argot, variétés (UK, US, autres)", ["registre"])],
 "de": [("a0", "alpha", "Alphabet, Umlaut et ß", []), ("a1", "genre", "Genres (der, die, das) et pluriels", ["alpha"]), ("a1", "nom-acc", "Nominatif et accusatif", ["genre"]),
        ("a2", "dat", "Datif et prépositions", ["nom-acc"]), ("a2", "perfekt", "Parfait et ordre des mots (V2)", ["nom-acc"]), ("b1", "gen", "Génitif, subordonnées", ["dat", "perfekt"]),
        ("b2", "konj2", "Subjonctif II, passif", ["gen"]), ("c1", "registre", "Registres, composés, langue écrite", ["konj2"]), ("natif", "dialectes", "Dialectes, argot, littérature", ["registre"])],
 "fr": [("a0", "alpha", "Alphabet, accents et sons de base", []), ("a1", "genre", "Genre, articles, présent", ["alpha"]), ("a2", "passe", "Passé composé et imparfait", ["genre"]),
        ("b1", "subj", "Subjonctif, pronoms", ["passe"]), ("b2", "nuances", "Concordance, discours rapporté", ["subj"]), ("c1", "registre", "Registres et style", ["nuances"]),
        ("natif", "argot", "Argot, verlan, francophonies, littérature", ["registre"])],
 "it": [("a0", "alpha", "Alphabet et sons (gn, gl, doppie)", []), ("a1", "genre", "Genre, articoli, essere/avere", ["alpha"]), ("a2", "passato", "Passato prossimo, imperfetto", ["genre"]),
        ("b1", "cong", "Congiuntivo, pronomi combinati", ["passato"]), ("b2", "periodo", "Periodo ipotetico, passivo", ["cong"]), ("c1", "registro", "Registri e sfumature", ["periodo"]),
        ("natif", "dialetti", "Dialetti, gergo, letteratura", ["registro"])],
 "es": [("a0", "alpha", "Alfabeto y sonidos (ñ, rr, ll)", []), ("a1", "ser", "Ser/estar, presente", ["alpha"]), ("a2", "pasado", "Pretérito indefinido e imperfecto", ["ser"]),
        ("b1", "subj", "Subjuntivo, pronombres", ["pasado"]), ("b2", "condicional", "Condicional, perífrasis", ["subj"]), ("c1", "registro", "Registros y matices", ["condicional"]),
        ("natif", "variedades", "Variedades (España, América), jerga, literatura", ["registro"])],
}
EN_TITLES = {'zh': {'pinyin': 'Pinyin, initials and finals', 'tons': 'The four tones and the neutral tone', 'traits': 'Basic strokes and stroke order', 'chars150': '150 characters (HSK 1)', 'mesure': 'Measure words (个, 本…) and numbers', 'chars300': '300 characters (HSK 2)', 'aspect': 'Particles 了, 过, 着 and aspect', 'chars600': '600 characters (HSK 3)', 'ba': 'The 把 and 被 constructions', 'chars1200': '1200 characters (HSK 4-5)', 'chengyu': 'Chengyu and registers (HSK 6)', 'dialectes': 'Varieties, slang, classical literature'}, 'ja': {'hira': 'Hiragana', 'kata': 'Katakana', 'kanji80': '80 kanji (N5)', 'polit1': 'Polite です/ます forms', 'kanji300': '300 kanji (N4)', 'te': 'The て-form and chaining', 'kanji650': '650 kanji (N3)', 'plain': 'Plain style and conversation', 'kanji1000': '1000 kanji (N2)', 'keigo': 'Keigo: honorific and humble', 'kanji2000': '2000 kanji (N1)', 'registres': 'Registers, dialects, literature'}, 'en': {'alpha': 'Alphabet and basic sounds', 'be': 'To be, to have, present simple', 'past': 'Past simple and present perfect', 'modals': 'Modals and conditionals', 'phrasal': 'Phrasal verbs', 'registre': 'Registers and nuances (formal, informal)', 'idiomes': 'Idioms, slang, varieties (UK, US, others)'}, 'de': {'alpha': 'Alphabet, Umlaut and ß', 'genre': 'Genders (der, die, das) and plurals', 'nom-acc': 'Nominative and accusative', 'dat': 'Dative and prepositions', 'perfekt': 'Perfect tense and word order (V2)', 'gen': 'Genitive, subordinate clauses', 'konj2': 'Subjunctive II, passive', 'registre': 'Registers, compounds, written language', 'dialectes': 'Dialects, slang, literature'}, 'fr': {'alpha': 'Alphabet, accents and basic sounds', 'genre': 'Gender, articles, present tense', 'passe': 'Passé composé and imparfait', 'subj': 'Subjunctive, pronouns', 'nuances': 'Sequence of tenses, reported speech', 'registre': 'Registers and style', 'argot': 'Slang, verlan, francophone varieties, literature'}, 'it': {'alpha': 'Alphabet and sounds (gn, gl, double consonants)', 'genre': 'Gender, articles, essere/avere', 'passato': 'Passato prossimo, imperfetto', 'cong': 'Subjunctive, combined pronouns', 'periodo': 'Conditional sentences, passive', 'registro': 'Registers and nuances', 'dialetti': 'Dialects, slang, literature'}, 'es': {'alpha': 'Alphabet and sounds (ñ, rr, ll)', 'ser': 'Ser/estar, present tense', 'pasado': 'Preterite and imperfect', 'subj': 'Subjunctive, pronouns', 'condicional': 'Conditional, periphrases', 'registro': 'Registers and nuances', 'variedades': 'Varieties (Spain, Americas), slang, literature'}}
NAMES = {"zh": ("Chinois (mandarin)", "Chinese (Mandarin)"), "ja": ("Japonais", "Japanese"), "en": ("Anglais", "English"), "de": ("Allemand", "German"),
         "fr": ("Français", "French"), "it": ("Italien", "Italian"), "es": ("Espagnol", "Spanish")}
SKILL_EN = {"co": "Listening", "ce": "Reading", "po": "Speaking", "pe": "Writing"}
# Pont approximatif vers l'échelle N0-N4 de l'architecture du contenu (sert à trier dans ses outils ; l'échelle d'une langue reste le CECRL, champ « cefr »)
NLEVEL = {"a0": "N0", "a1": "N1", "a2": "N1", "b1": "N1", "b2": "N2", "c1": "N3", "c2": "N4", "natif": "N4"}
MINUTES = {"co": 30, "ce": 30, "po": 30, "pe": 30}

def sid(code, lv, slug): return f"{code}.{lv}-{slug}"

def graph(code):
    skills = []
    for i, lv in enumerate(LEVELS):
        for sk, label in SKILLS:
            skills.append({"id": sid(code, lv, sk), "title": {"fr": f"{label} ({lv.upper()})", "en": f"{SKILL_EN[sk]} ({lv.upper()})"}, "level": NLEVEL[lv], "cefr": lv,
                           "skill": sk, "prereq": [sid(code, LEVELS[i-1], sk)] if i else [], "years": [], "classes": [], "exams": [], "lots": [], "minutes": MINUTES[sk], "tags": ["langue", sk]})
    first = {sl: lv for lv, sl, *_ in SPECIFIC[code]}
    for lv, sl, title, req in SPECIFIC[code]:
        skills.append({"id": sid(code, lv, sl), "title": {"fr": title, "en": EN_TITLES[code][sl]}, "level": NLEVEL[lv], "cefr": lv, "skill": None,
                       "prereq": [sid(code, first[r], r) for r in req], "years": [], "classes": [], "exams": [], "lots": [], "minutes": 45, "tags": ["langue", "langue-" + code]})
    # la compréhension écrite exige les fondements propres à la langue (écriture, sons) du même niveau, s'ils n'ont pas de prérequis
    for n in skills:
        if n["skill"] == "ce":
            n["prereq"] += [m["id"] for m in skills if m["skill"] is None and m["cefr"] == n["cefr"] and m["prereq"] == []]
    return {"format": 1, "domain": "langue-" + code, "prefix": code, "title": {"fr": NAMES[code][0], "en": NAMES[code][1]}, "skills": skills}

def main(out):
    os.makedirs(out, exist_ok=True)
    for code in SPECIFIC:
        with open(os.path.join(out, f"langue-{code}.json"), "w", encoding="utf-8") as f:
            json.dump(graph(code), f, ensure_ascii=False, indent=1); f.write("\n")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "..", "content", "graph"))
