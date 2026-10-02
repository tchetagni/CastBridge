#!/usr/bin/env python3
"""Packs « Anglais — préparation d'excellence aux certifications » (IELTS · TOEFL · Cambridge C1 Advanced / C2 Proficiency),
niveaux C1 et C2, langue de départ français. Exercices calqués sur les épreuves (Reading & Use of English, Writing, Listening, Speaking).
4 thèmes x 1 source (fr) = 4 packs texte + audio de synthèse (espeak-ng en, marqué `synthetic`).
Tout est « free » (contenu original, CC BY-SA 4.0). Exécution : python3 tools/langues/gen_en_cert.py
"""
import json, os, re, shutil, subprocess, sys, tempfile, wave

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
CONTENT_DIR = os.path.join(ROOT, "content", "langues")
MEDIA_DIR = os.path.join(ROOT, "content", "langues-media")
LANG = "en"
SRC = "fr"
ENGINE = "espeak-ng 1.52 (voix en)"
VOICE = "en"


def slug(t):
    return re.sub(r"[^a-z0-9]+", "", t.lower()) or "w"


# vocab : (term, gloss_fr, pos)
CONTENT = {
    "c1": {
        "argumentation": {
            "title": "Anglais C1 — L'argumentation (Writing)",
            "unit": "Rédiger un essai argumenté",
            "vocab": [("moreover", "de plus", "adv"), ("however", "cependant", "adv"), ("consequently", "par conséquent", "adv"),
                      ("to assess", "évaluer", "v"), ("to convey", "transmettre, exprimer", "v"), ("coherent", "cohérent", "adj"),
                      ("compelling", "convaincant", "adj"), ("underlying", "sous-jacent", "adj"), ("perspective", "point de vue", "n"),
                      ("implication", "conséquence, implication", "n"), ("notwithstanding", "malgré", "prep"), ("to advocate", "préconiser", "v")],
            "dialogue": {"title": "Un débat", "lines": [
                ("A", "Do you think universities should be free?", "Penses-tu que les universités devraient être gratuites ?"),
                ("B", "That is a compelling argument, but I see a different perspective.", "C'est un argument convaincant, mais j'ai un autre point de vue."),
                ("A", "Moreover, education benefits the whole of society.", "De plus, l'éducation profite à toute la société."),
                ("B", "However, we must assess the cost to taxpayers.", "Cependant, nous devons évaluer le coût pour les contribuables."),
                ("A", "Consequently, a compromise may be the most coherent solution.", "Par conséquent, un compromis est peut-être la solution la plus cohérente."),
            ]},
            "grammar": [
                {"title": "Les connecteurs d'argumentation",
                 "md": "**Moreover** (ajout), **however** (opposition), **consequently** (conséquence), **notwithstanding** (concession). Ils structurent un essai et marquent la progression de la pensée.",
                 "ex": [("Moreover, it is widely accepted.", "De plus, c'est largement admis.")]},
                {"title": "Le hedging (langue prudente)",
                 "md": "À l'écrit académique, on nuance : **It may be argued that**, **It tends to suggest**. L'affirmation trop tranchée est pénalisée au niveau C1.",
                 "ex": [("It may be argued that this is beneficial.", "On peut avancer que cela est bénéfique.")]},
            ],
            "story": {"title": "Pour ou contre la gratuité", "paragraphs": [
                ("Many people advocate free higher education. They argue that it conveys a clear message: knowledge is a public good.", "Beaucoup préconisent l'enseignement supérieur gratuit. Ils soutiennent que cela transmet un message clair : le savoir est un bien public."),
                ("However, others assess the underlying cost. Consequently, the debate rests on how we value education.", "Cependant, d'autres évaluent le coût sous-jacent. Par conséquent, le débat repose sur la valeur que nous accordons à l'éducation."),
                ("A coherent policy must balance both perspectives, notwithstanding the difficulty.", "Une politique cohérente doit concilier les deux points de vue, malgré la difficulté."),
            ]},
            "order": (["This", "is", "a", "compelling", "argument"], "This is a compelling argument."),
            "cloze": ("Complète : _____, we must consider the cost. (Cependant)", "However"),
        },
        "academique": {
            "title": "Anglais C1 — Lecture et écoute académiques",
            "unit": "Comprendre un texte universitaire",
            "vocab": [("hypothesis", "hypothèse", "n"), ("evidence", "preuve", "n"), ("analysis", "analyse", "n"), ("significant", "significatif", "adj"),
                      ("methodology", "méthodologie", "n"), ("conclusion", "conclusion", "n"), ("whereas", "tandis que", "conj"),
                      ("nevertheless", "néanmoins", "adv"), ("furthermore", "en outre", "adv"), ("to investigate", "étudier, enquêter sur", "v"),
                      ("relevant", "pertinent", "adj"), ("criterion", "critère", "n")],
            "dialogue": {"title": "Séminaire", "lines": [
                ("A", "What is the main hypothesis of this paper?", "Quelle est l'hypothèse principale de cet article ?"),
                ("B", "It argues that the evidence is not significant.", "Il soutient que la preuve n'est pas significative."),
                ("A", "Nevertheless, the methodology is relevant.", "Néanmoins, la méthodologie est pertinente."),
                ("B", "Furthermore, the analysis supports the conclusion.", "En outre, l'analyse appuie la conclusion."),
            ]},
            "grammar": [
                {"title": "La nominalisation académique",
                 "md": "L'écrit universitaire transforme les verbes en noms : *to analyse* → *analysis*, *to conclude* → *conclusion*. Cela rend le style plus formel et dense.",
                 "ex": [("The analysis of the data.", "L'analyse des données.")]},
                {"title": "La voix passive",
                 "md": "La **voix passive** est fréquente en contexte scientifique : *The experiment was conducted* (l'expérience a été menée).",
                 "ex": [("The results were analysed.", "Les résultats ont été analysés.")]},
            ],
            "story": {"title": "Résumé d'un article", "paragraphs": [
                ("The study investigated whether sleep affects memory. The methodology compared two groups of students.", "L'étude a cherché à savoir si le sommeil affecte la mémoire. La méthodologie a comparé deux groupes d'étudiants."),
                ("The evidence showed a significant difference, whereas the second group performed better. Nevertheless, further research is needed.", "Les preuves ont montré une différence significative, tandis que le second groupe a mieux réussi. Néanmoins, des recherches supplémentaires sont nécessaires."),
                ("The conclusion is relevant to educators, furthermore it opens new questions.", "La conclusion est pertinente pour les enseignants ; en outre, elle ouvre de nouvelles questions."),
            ]},
            "order": (["The", "results", "were", "analysed", "carefully"], "The results were analysed carefully."),
            "cloze": ("Complète : The ____ supports the hypothesis. (preuve)", "evidence"),
        },
    },
    "c2": {
        "essai": {
            "title": "Anglais C2 — L'essai de haut niveau",
            "unit": "Nuancer et structurer",
            "vocab": [("ostensibly", "en apparence", "adv"), ("invariably", "invariablement", "adv"), ("paradoxical", "paradoxal", "adj"),
                      ("inherently", "intrinsèquement", "adv"), ("contentious", "controversé", "adj"), ("albeit", "bien que", "conj"),
                      ("to substantiate", "étayer", "v"), ("unequivocal", "sans équivoque", "adj"), ("discrepancy", "écart, divergence", "n"),
                      ("to entail", "impliquer", "v"), ("premise", "prémisse", "n"), ("to refute", "réfuter", "v")],
            "dialogue": {"title": "Échange entre experts", "lines": [
                ("A", "The proposal is ostensibly fair, yet it is inherently flawed.", "La proposition est en apparence juste, mais elle est intrinsèquement défectueuse."),
                ("B", "I would refute that premise, albeit cautiously.", "Je réfuterais cette prémisse, quoique prudemment."),
                ("A", "To substantiate your claim, you need unequivocal evidence.", "Pour étayer votre affirmation, il faut une preuve sans équivoque."),
                ("B", "There is, however, a discrepancy in the data.", "Il y a toutefois une divergence dans les données."),
            ]},
            "grammar": [
                {"title": "L'inversion stylistique",
                 "md": "**Never have I seen** (jamais je n'ai vu), **Rarely does one find**. L'inversion apporte de l'emphase et marque un registre très soutenu.",
                 "ex": [("Never have I encountered such a discrepancy.", "Jamais je n'ai rencontré une telle divergence.")]},
                {"title": "Les cleft sentences",
                 "md": "**It is X that…** / **What matters is…** permettent de mettre un élément en relief avec précision.",
                 "ex": [("What matters is the underlying premise.", "Ce qui compte, c'est la prémisse sous-jacente.")]},
            ],
            "story": {"title": "Un essai nuancé", "paragraphs": [
                ("The policy is ostensibly progressive, yet it entails contentious consequences. Invariably, such reforms are paradoxical.", "La politique est en apparence progressiste, mais elle implique des conséquences controversées. Invariablement, ces réformes sont paradoxales."),
                ("To substantiate this view, one must refute the underlying premise, albeit carefully. What matters is the discrepancy between intention and result.", "Pour étayer ce point de vue, il faut réfuter la prémisse sous-jacente, quoique prudemment. Ce qui compte, c'est l'écart entre l'intention et le résultat."),
            ]},
            "order": (["Never", "have", "I", "seen", "such", "progress"], "Never have I seen such progress."),
            "cloze": ("Complète : ____ have I seen such a result. (Jamais)", "Never"),
        },
        "nuances": {
            "title": "Anglais C2 — Idiomes, registres et ironie",
            "unit": "Comprendre l'implicite",
            "vocab": [("to beat around the bush", "tourner autour du pot", "idiom"), ("to cut corners", "faire les choses à moitié", "idiom"),
                      ("to get the gist", "saisir l'essentiel", "idiom"), ("a long shot", "un pari risqué", "idiom"),
                      ("to bite the bullet", "se jeter à l'eau", "idiom"), ("understated", "sobre, nuancé", "adj"),
                      ("sarcasm", "sarcasme", "n"), ("irony", "ironie", "n"), ("understatement", "euphémisme", "n"),
                      ("to insinuate", "insinuer", "v"), ("subtle", "subtil", "adj"), ("a euphemism", "un euphémisme", "n")],
            "dialogue": {"title": "Une conversation ironique", "lines": [
                ("A", "Don't beat around the bush. What do you really think?", "Ne tourne pas autour du pot. Qu'en penses-tu vraiment ?"),
                ("B", "To put it mildly, the plan is a long shot.", "Pour rester modéré, le plan est un pari risqué."),
                ("A", "That is quite an understatement.", "C'est un sacré euphémisme."),
                ("B", "I detect a hint of sarcasm in your voice.", "Je décèle une pointe de sarcasme dans ta voix."),
            ]},
            "grammar": [
                {"title": "L'euphémisme et l'ironie",
                 "md": "Au niveau C2, on reconnaît et produit l'implicite : **understatement** (dire moins pour signifier plus), **irony**, **sarcasm**. *« Not bad »* peut vouloir dire *« excellent »*.",
                 "ex": [("Not bad at all.", "Pas mal du tout.")]},
                {"title": "Les idiomes en contexte",
                 "md": "**To beat around the bush**, **to cut corners**, **to get the gist** : les idiomes se comprennent en contexte et marquent la maîtrise d'un natif.",
                 "ex": [("Stop cutting corners and do it properly.", "Arrête de bâcler et fais-le correctement.")]},
            ],
            "story": {"title": "Lire entre les lignes", "paragraphs": [
                ("When she said it was « a minor setback », that was an understatement. The project had, in fact, been a long shot from the start.", "Quand elle a parlé d'un « léger contretemps », c'était un euphémisme. Le projet était en réalité un pari risqué dès le départ."),
                ("Her colleagues beat around the bush, but the irony was subtle: everyone knew corners had been cut.", "Ses collègues ont tourné autour du pot, mais l'ironie était subtile : tout le monde savait qu'on avait bâclé le travail."),
            ]},
            "order": (["Don't", "beat", "around", "the", "bush"], "Don't beat around the bush."),
            "cloze": ("Complète : That's quite an ____. (euphémisme)", "understatement"),
        },
    },
}


def wav_ms(p):
    with wave.open(p) as w:
        return int(1000 * w.getnframes() / w.getframerate())


def synth_opus(text, dst):
    with tempfile.TemporaryDirectory() as t:
        wav = os.path.join(t, "x.wav")
        subprocess.run(["espeak-ng", "-v", VOICE, "-s", "120", text, "-w", wav], check=True)
        ms = wav_ms(wav)
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-ac", "1", "-ar", "16000",
                        "-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-c:a", "libopus", "-b:a", "24k", dst], check=True)
        return os.path.getsize(dst), ms


def mid(level, theme, s):
    if s in ("d1", "s1"):
        return f"{LANG}-{level}-{theme}-{s}"
    return f"{LANG}-{s}"


def audio_list(level, theme):
    th = CONTENT[level][theme]
    out = []
    for term, _fr, _pos in th["vocab"]:
        out.append((mid(level, theme, slug(term)), term))
    out.append((mid(level, theme, "d1"), " ".join(l[1].rstrip(".!?") for l in th["dialogue"]["lines"])))
    out.append((mid(level, theme, "s1"), " ".join(p[0].rstrip(".") for p in th["story"]["paragraphs"])))
    return out


def build_media(level, theme):
    scope = f"{LANG}-{level}-{theme}"
    os.makedirs(os.path.join(MEDIA_DIR, scope, "audio"), exist_ok=True)
    media = []
    for m, tts in audio_list(level, theme):
        rel = f"audio/{m}.opus"
        dst = os.path.join(MEDIA_DIR, scope, rel)
        b, ms = synth_opus(tts, dst)
        media.append({"id": m, "file": rel, "kind": "audio", "bytes": b, "durationMs": ms,
                      "license": "CASTBRIDGE-ORIGINAL", "engine": ENGINE, "synthetic": True, "voiceLicense": "GPL-3.0",
                      "lang": LANG, "source": "CastBridge, voix de synthèse libre (GPL-3.0, moteur uniquement)"})
    return media


def build_unit(level, theme):
    th = CONTENT[level][theme]
    scope = f"{LANG}-{level}-{theme}-{SRC}"
    vocab = []
    for i, (term, fr, pos) in enumerate(th["vocab"], 1):
        vocab.append({"id": f"{scope}-v{i}", "term": term, "gloss": fr, "pos": pos, "audio": f"m:{mid(level, theme, slug(term))}"})
    d = th["dialogue"]
    lines = [{"who": w, "text": t, "tr": fr} for w, t, fr in d["lines"]]
    dialogue = [{"id": f"{scope}-d1", "title": d["title"], "lines": lines, "audio": f"m:{mid(level, theme, 'd1')}"}]
    grammar = [{"id": f"{scope}-g{i}", "title": g["title"], "md": g["md"], "examples": [{"text": t, "tr": fr} for t, fr in g["ex"]]}
               for i, g in enumerate(th["grammar"], 1)]
    story = {"id": f"{scope}-s1", "title": th["story"]["title"],
             "paragraphs": [{"who": "", "text": t, "tr": fr} for t, fr in th["story"]["paragraphs"]]}
    order_words, order_ans = th["order"]
    cloze_prompt, cloze_ans = th["cloze"]
    exercises = [
        {"id": f"{scope}-x1", "kind": "mcq", "prompt": f"Que signifie « {vocab[0]['term']} » ? (Use of English)",
         "choices": [vocab[0]["gloss"], vocab[1]["gloss"], vocab[2]["gloss"]], "correct": 0},
        {"id": f"{scope}-x2", "kind": "match", "prompt": "Associe chaque mot à sa traduction française.",
         "pairs": [{"a": vocab[k]["term"], "b": vocab[k]["gloss"]} for k in range(5)]},
        {"id": f"{scope}-x3", "kind": "cloze", "prompt": cloze_prompt, "answers": [cloze_ans]},
        {"id": f"{scope}-x4", "kind": "order", "prompt": "Remets les mots dans l'ordre (Structures).",
         "words": order_words, "answers": [order_ans]},
        {"id": f"{scope}-x5", "kind": "truefalse", "prompt": "« However » exprime une opposition.", "correct": 0},
        {"id": f"{scope}-x6", "kind": "dictation", "prompt": "Écoute et écris la phrase (Listening).",
         "audio": f"m:{mid(level, theme, 'd1')}", "answers": [lines[0]["text"]]},
        {"id": f"{scope}-x7", "kind": "translate", "prompt": f"Traduis en français : {vocab[0]['term']}.", "answers": [vocab[0]["gloss"]]},
        {"id": f"{scope}-x8", "kind": "write", "prompt": "Rédige un court paragraphe argumenté sur un sujet de ton choix (Writing).",
         "model": "Moreover, it is widely accepted that education benefits society."},
        {"id": f"{scope}-x9", "kind": "speak", "prompt": "Donne ton opinion sur un sujet d'actualité en anglais, puis compare (Speaking).",
         "model": "From my perspective, this is a compelling argument.", "audio": f"m:{mid(level, theme, slug(th['vocab'][0][0]))}"},
    ]
    cards = [{"id": f"{scope}-c{i}", "vocab": f"{scope}-v{i}"} for i in range(1, 9)]
    return {"id": f"{scope}-u1", "title": th["unit"], "minutes": 25, "skills": ["co", "ce", "po", "pe"], "prerequisites": [],
            "vocab": vocab, "dialogues": dialogue, "grammar": grammar, "exercises": exercises,
            "stories": [story], "cards": cards, "animations": []}


def write_pack(level, theme, media):
    scope = f"{LANG}-{level}-{theme}-{SRC}"
    dstdir = os.path.join(CONTENT_DIR, scope)
    os.makedirs(dstdir, exist_ok=True)
    pack = {"format": 1, "type": "langue", "id": scope, "version": 1, "target": LANG, "level": level, "theme": theme,
            "source": SRC, "title": CONTENT[level][theme]["title"], "state": "review", "units": [build_unit(level, theme)]}
    with open(os.path.join(dstdir, "langue.json"), "w", encoding="utf-8") as f:
        json.dump(pack, f, ensure_ascii=False, indent=1)
    with open(os.path.join(dstdir, "media.json"), "w", encoding="utf-8") as f:
        json.dump({"format": 1, "comment": "Médias référencés du lot texte ; fichiers dans le lot média jumeau.", "media": media}, f, ensure_ascii=False, indent=1)


def main():
    if not shutil.which("espeak-ng"):
        sys.exit("espeak-ng manquant")
    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg manquant")
    for level in ("c1", "c2"):
        for theme in ("argumentation", "academique") if level == "c1" else ("essai", "nuances"):
            media = build_media(level, theme)
            write_pack(level, theme, media)
    print("Packs Anglais (certifications) C1/C2 générés : 4")


if __name__ == "__main__":
    main()
