#!/usr/bin/env python3
"""Génère content/langues/graph/langue-<code>.json : graphe de compétences (docs/LANGUES.md § 3.6). Déterministe.
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
NAMES = {"zh": "Chinois (mandarin)", "ja": "Japonais", "en": "Anglais", "de": "Allemand", "fr": "Français", "it": "Italien", "es": "Espagnol"}

def graph(code):
    nodes = []
    for i, lv in enumerate(LEVELS):
        for sk, label in SKILLS:
            req = [f"{code}-{LEVELS[i-1]}-{sk}"] if i else []
            nodes.append({"id": f"{code}-{lv}-{sk}", "level": lv, "skill": sk, "title": f"{label} ({lv.upper()})", "requires": req})
    for lv, sid, title, req in SPECIFIC[code]:
        nodes.append({"id": f"{code}-{lv}-{sid}", "level": lv, "skill": None, "title": title, "requires": [f"{code}-{lv0}-{r}" for r in req for lv0 in [next(l for l, i2, *_ in SPECIFIC[code] if i2 == r)]]})
    # chaque compétence de niveau n exige aussi le fondement propre à la langue du même niveau, s'il existe
    for n in nodes:
        if n["skill"] == "ce":
            n["requires"] += [m["id"] for m in nodes if m["skill"] is None and m["level"] == n["level"] and m["requires"] == [] and m["id"] not in n["requires"]]
    return {"format": 1, "lang": code, "title": NAMES[code], "nodes": nodes}

def main(out):
    os.makedirs(out, exist_ok=True)
    for code in SPECIFIC:
        with open(os.path.join(out, f"langue-{code}.json"), "w", encoding="utf-8") as f:
            json.dump(graph(code), f, ensure_ascii=False, indent=1); f.write("\n")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "..", "content", "langues", "graph"))
