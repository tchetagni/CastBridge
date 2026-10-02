#!/usr/bin/env python3
"""Packs « Français (Québec) » — préparation TCF Canada, niveaux B1 et B2, langue de départ anglais.

Variété canadienne : vocabulaire (dépanneur, cégep, magasiner, stationnement…), contextes québécois et
exercices calqués sur les sections du TCF (compréhension orale/écrite, structures de la langue, expression).
4 thèmes x 1 source (en) = 4 packs texte, plus audio de synthèse (espeak-ng fr, marqué `synthetic` : accent
standard, PAS l'accent québécois — celui-ci exigera des enregistrements humains, docs/LANGUES.md § 8).

Tout est « free » (contenu original, CC BY-SA 4.0). Exécution : python3 tools/langues/gen_fr_ca_tcf.py
"""
import json, os, re, shutil, subprocess, sys, tempfile, wave

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "anim"))
import animlib  # noqa: E402

CONTENT_DIR = os.path.join(ROOT, "content", "langues")
MEDIA_DIR = os.path.join(ROOT, "content", "langues-media")
LANG = "fr"
SRC = "en"
ENGINE = "espeak-ng 1.52 (voix fr)"
VOICE = "fr"


def slug(t):
    return re.sub(r"[^a-z0-9]+", "", t.lower()) or "w"


# vocab : (term, gloss_en, pos)   — la langue de départ est l'anglais
# dialogue : lines [(who, text, en)]
# grammar : [(title, md, [(text, en)])]
# story : paragraphs [(text, en)]
CONTENT = {
    "b1": {
        "quotidien": {
            "title": "Français (Québec) B1 — La vie quotidienne",
            "unit": "Une journée à Montréal",
            "vocab": [("dépanneur", "convenience store", "n"), ("magasiner", "to shop", "v"), ("stationnement", "parking lot", "n"),
                      ("fin de semaine", "weekend", "n"), ("courriel", "email", "n"), ("cellulaire", "cellphone", "n"),
                      ("tuque", "winter hat", "n"), ("autobus", "bus", "n"), ("déjeuner", "breakfast", "n"),
                      ("dîner", "lunch", "n"), ("souper", "dinner", "n"), ("char", "car (informal)", "n")],
            "dialogue": {"title": "Au dépanneur", "lines": [
                ("A", "Bonjour ! Je cherche des timbres.", "Hi! I'm looking for stamps."),
                ("B", "Bonjour ! Oui, ils sont ici, à la caisse.", "Hi! Yes, they're here, at the cash register."),
                ("A", "Merci. Est-ce que vous vendez des billets d'autobus ?", "Thanks. Do you sell bus tickets?"),
                ("B", "Oui, la carte à puce se recharge ici.", "Yes, you can top up the smart card here."),
                ("A", "Parfait. Je vais aussi magasiner pour le souper.", "Great. I'll also shop for dinner."),
                ("B", "Pas de problème. Bonne fin de semaine !", "No problem. Have a good weekend!"),
            ]},
            "grammar": [
                {"title": "« tu » et « vous » au Québec",
                 "md": "Le **tu** est plus répandu au Québec qu'en France, même entre inconnus. En situation officielle ou avec une personne plus âgée, on garde le **vous**. Au TCF, la compréhension des deux registres est attendue.",
                 "ex": [("Tu veux du café ?", "Do you want some coffee?"), ("Voulez-vous un reçu ?", "Would you like a receipt?")]},
                {"title": "Quelques québécismes du quotidien",
                 "md": "Des mots diffèrent du français de France : **dépanneur** (épicerie de quartier), **magasiner** (faire les courses), **stationnement** (parking), **fin de semaine** (week-end).",
                 "ex": [("Je vais au dépanneur.", "I'm going to the convenience store.")]},
                {"title": "Les repas : déjeuner, dîner, souper",
                 "md": "Au Québec, le repas de midi s'appelle le **dîner** et celui du soir le **souper** (en France : déjeuner / dîner). Le **déjeuner** est le repas du matin.",
                 "ex": [("Le dîner est à midi.", "Lunch is at noon.")]},
            ],
            "story": {"title": "Une journée à Montréal", "paragraphs": [
                ("Léa habite à Montréal. Le matin, elle déjeune chez elle, puis elle prend l'autobus pour aller travailler.", "Léa lives in Montreal. In the morning, she has breakfast at home, then takes the bus to go to work."),
                ("À midi, elle dîne avec ses collègues. Elle achète un sandwich au dépanneur du coin.", "At noon, she has lunch with her colleagues. She buys a sandwich at the local convenience store."),
                ("Le soir, elle magasine pour le souper. En hiver, elle met sa tuque et ses mitaines.", "In the evening, she shops for dinner. In winter, she wears her tuque and mittens."),
                ("La fin de semaine, elle sort avec sa blonde. Elles visitent le Vieux-Montréal.", "On the weekend, she goes out with her girlfriend. They visit Old Montreal."),
            ]},
            "order": (["Je", "vais", "au", "dépanneur"], "Je vais au dépanneur."),
            "cloze": ("Complète : Je vais ____ dépanneur.", "au"),
        },
        "travail": {
            "title": "Français (Québec) B1 — Le travail et l'emploi",
            "unit": "Chercher un emploi",
            "vocab": [("emploi", "job", "n"), ("entrevue", "interview", "n"), ("CV", "résumé (CV)", "n"), ("poste", "position", "n"),
                      ("salaire", "salary", "n"), ("congé", "leave / day off", "n"), ("embauche", "hiring", "n"),
                      ("patron", "boss", "n"), ("collègue", "colleague", "n"), ("quart de travail", "work shift", "n"),
                      ("formation", "training", "n"), ("stage", "internship", "n")],
            "dialogue": {"title": "Une entrevue d'embauche", "lines": [
                ("A", "Bonjour, merci de me recevoir pour cette entrevue.", "Hello, thank you for meeting me for this interview."),
                ("B", "Bonjour ! Parlez-moi de votre expérience.", "Hello! Tell me about your experience."),
                ("A", "J'ai fait un stage de six mois dans une compagnie de Montréal.", "I did a six-month internship at a Montreal company."),
                ("B", "Très bien. Le poste exige un quart de soir.", "Very good. The position requires an evening shift."),
                ("A", "Ça me convient. Quel est le salaire offert ?", "That suits me. What salary is offered?"),
                ("B", "Nous en discuterons à l'embauche.", "We'll discuss that upon hiring."),
            ]},
            "grammar": [
                {"title": "Le vouvoiement en contexte professionnel",
                 "md": "En entrevue et au travail, on utilise le **vous**. Le **tu** vient seulement si le patron ou le collègue le propose.",
                 "ex": [("Pouvez-vous décrire votre expérience ?", "Can you describe your experience?")]},
                {"title": "Vocabulaire de l'emploi au Québec",
                 "md": "Le **CV** (curriculum vitae) est le même terme ; on parle d'**entrevue**, de **quart de travail**, de **congé** et de **poste**. Le **stage** est très valorisé à la sortie du cégep ou de l'université.",
                 "ex": [("J'ai un quart de nuit.", "I have a night shift.")]},
            ],
            "story": {"title": "Une première entrevue", "paragraphs": [
                ("Marc a terminé son stage. Aujourd'hui, il a sa première entrevue pour un poste de commis.", "Marc finished his internship. Today he has his first interview for a clerk position."),
                ("Il a préparé son CV et il arrive quinze minutes à l'avance. La patronne le reçoit avec le sourire.", "He prepared his résumé and arrives fifteen minutes early. The boss welcomes him with a smile."),
                ("Elle lui pose des questions sur sa formation et sur ses disponibilités pour les quarts de travail.", "She asks him about his training and his availability for work shifts."),
                ("À la fin, elle lui dit : « Nous vous rappellerons pour l'embauche. » Marc est content.", "At the end, she says: 'We'll call you back about hiring.' Marc is happy."),
            ]},
            "order": (["J'ai", "un", "quart", "de", "soir"], "J'ai un quart de soir."),
            "cloze": ("Complète : J'ai une ____ d'embauche demain.", "entrevue"),
        },
    },
    "b2": {
        "societe": {
            "title": "Français (Québec) B2 — La société québécoise",
            "unit": "Langue et identité",
            "vocab": [("cégep", "CEGEP (college)", "n"), ("traversier", "ferry", "n"), ("poutine", "poutine (dish)", "n"),
                      ("cabane à sucre", "sugar shack", "n"), ("fleuve", "river", "n"), ("province", "province", "n"),
                      ("bilingue", "bilingual", "adj"), ("francophone", "French-speaking", "n/adj"), ("anglicisme", "anglicism", "n"),
                      ("identité", "identity", "n"), ("minorité", "minority", "n"), ("cohabitation", "coexistence", "n")],
            "dialogue": {"title": "Une discussion sur la langue", "lines": [
                ("A", "Pourquoi dit-on que le français québécois est différent ?", "Why do people say Quebec French is different?"),
                ("B", "Il a gardé des mots anciens et en a créé de nouveaux, souvent pour éviter les anglicismes.", "It kept old words and created new ones, often to avoid anglicisms."),
                ("A", "Par exemple ?", "For example?"),
                ("B", "On dit « courriel » et « magasiner » plutôt que « e-mail » et « faire du shopping ».", "We say 'courriel' and 'magasiner' instead of 'e-mail' and 'go shopping'."),
                ("A", "Est-ce que le Québec est bilingue ?", "Is Quebec bilingual?"),
                ("B", "Le Québec est une province francophone, mais Montréal est très cosmopolite.", "Quebec is a French-speaking province, but Montreal is very cosmopolitan."),
            ]},
            "grammar": [
                {"title": "Anglicismes et français québécois",
                 "md": "Le Québec lutte contre les **anglicismes** tout en en gardant quelques-uns (ex. *la job*). Les expressions locales (cégep, traversier, cabane à sucre) enrichissent le français standard.",
                 "ex": [("On prend le traversier pour traverser le fleuve.", "We take the ferry to cross the river.")]},
                {"title": "Registres : soutenu, courant, familier",
                 "md": "Au TCF, on évalue la capacité à comprendre les registres. Familier : *« t'sais »*, *« la job »*. Courant : *« C'est correct. »*. Soutenu : *« Cela convient parfaitement. »*",
                 "ex": [("La job est finie. (familier)", "The job is done. (informal)"), ("Le travail est terminé. (soutenu)", "The work is finished. (formal)")]},
            ],
            "story": {"title": "Le Québec en bref", "paragraphs": [
                ("Le Québec est une province de l'est du Canada, la seule dont la langue officielle est le français.", "Quebec is a province in eastern Canada, the only one whose official language is French."),
                ("On y trouve le cégep, un collège qui prépare à l'université ou à un métier.", "There you find the cégep, a college that prepares for university or a trade."),
                ("Au printemps, les familles vont à la cabane à sucre manger du sirop d'érable et de la poutine.", "In spring, families go to the sugar shack to eat maple syrup and poutine."),
                ("Le fleuve Saint-Laurent, que l'on traverse en traversier, est au cœur de la vie québécoise.", "The St. Lawrence River, which you cross by ferry, is at the heart of Quebec life."),
            ]},
            "order": (["Le", "Québec", "est", "une", "province", "francophone"], "Le Québec est une province francophone."),
            "cloze": ("Complète : Le ____ Saint-Laurent traverse le Québec.", "fleuve"),
        },
        "sante": {
            "title": "Français (Québec) B2 — La santé et les services",
            "unit": "Prendre rendez-vous",
            "vocab": [("rendez-vous", "appointment", "n"), ("clinique", "clinic", "n"), ("urgence", "emergency", "n"),
                      ("assurance maladie", "health insurance", "n"), ("carte d'assurance maladie", "health insurance card", "n"),
                      ("pharmacie", "pharmacy", "n"), ("médecin", "doctor", "n"), ("infirmier", "nurse", "n"),
                      ("ordonnance", "prescription", "n"), ("symptôme", "symptom", "n"), ("dossier", "file / record", "n"),
                      ("consultation", "consultation", "n")],
            "dialogue": {"title": "À la clinique", "lines": [
                ("A", "Bonjour, je voudrais prendre un rendez-vous avec un médecin.", "Hello, I'd like to make an appointment with a doctor."),
                ("B", "Bonjour ! Avez-vous votre carte d'assurance maladie ?", "Hello! Do you have your health insurance card?"),
                ("A", "Oui, la voici. Quels sont mes symptômes à décrire ?", "Yes, here it is. What symptoms should I describe?"),
                ("B", "Décrivez simplement ce qui ne va pas : fièvre, douleur, fatigue.", "Just describe what's wrong: fever, pain, fatigue."),
                ("A", "J'ai de la fièvre depuis deux jours et mal à la gorge.", "I've had a fever for two days and a sore throat."),
                ("B", "Le médecin vous verra demain à dix heures. Apportez votre dossier.", "The doctor will see you tomorrow at ten. Bring your file."),
            ]},
            "grammar": [
                {"title": "Prendre rendez-vous : les formules",
                 "md": "**Je voudrais prendre un rendez-vous**, **Est-ce que le médecin est disponible ?**, **J'ai un symptôme à signaler**. La politesse et le conditionnel sont attendus.",
                 "ex": [("Je voudrais un rendez-vous, s'il vous plaît.", "I would like an appointment, please.")]},
                {"title": "Le système de santé au Québec",
                 "md": "L'**assurance maladie** est gérée par la Régie de l'assurance maladie du Québec (RAMQ) ; la **carte d'assurance maladie** (la « carte soleil ») est obligatoire. Les **CLSC** offrent des soins courants, les **cliniques** reçoivent sans rendez-vous.",
                 "ex": [("Présentez votre carte d'assurance maladie.", "Show your health insurance card.")]},
            ],
            "story": {"title": "Une visite à la clinique", "paragraphs": [
                ("Nadia ne se sent pas bien depuis deux jours. Elle appelle la clinique du quartier.", "Nadia hasn't felt well for two days. She calls the local clinic."),
                ("La réceptionniste lui demande sa carte d'assurance maladie et lui donne un rendez-vous le lendemain.", "The receptionist asks for her health insurance card and gives her an appointment for the next day."),
                ("Le médecin l'examine, note ses symptômes dans son dossier et lui remet une ordonnance.", "The doctor examines her, records her symptoms in her file and gives her a prescription."),
                ("Elle va ensuite à la pharmacie pour acheter ses médicaments.", "She then goes to the pharmacy to buy her medication."),
            ]},
            "order": (["Je", "voudrais", "prendre", "un", "rendez-vous"], "Je voudrais prendre un rendez-vous."),
            "cloze": ("Complète : J'ai besoin d'une ____ pour mes médicaments.", "ordonnance"),
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
    for term, _en, _pos in th["vocab"]:
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
    for i, (term, en, pos) in enumerate(th["vocab"], 1):
        vocab.append({"id": f"{scope}-v{i}", "term": term, "gloss": en, "pos": pos, "audio": f"m:{mid(level, theme, slug(term))}"})
    d = th["dialogue"]
    lines = [{"who": w, "text": t, "tr": en} for w, t, en in d["lines"]]
    dialogue = [{"id": f"{scope}-d1", "title": d["title"], "lines": lines, "audio": f"m:{mid(level, theme, 'd1')}"}]
    grammar = [{"id": f"{scope}-g{i}", "title": g["title"], "md": g["md"], "examples": [{"text": t, "tr": en} for t, en in g["ex"]]}
               for i, g in enumerate(th["grammar"], 1)]
    story = {"id": f"{scope}-s1", "title": th["story"]["title"],
             "paragraphs": [{"who": "", "text": t, "tr": en} for t, en in th["story"]["paragraphs"]]}
    order_words, order_ans = th["order"]
    cloze_prompt, cloze_ans = th["cloze"]
    exercises = [
        {"id": f"{scope}-x1", "kind": "mcq", "prompt": "What does « dépanneur » mean? (Compréhension / lexique)",
         "choices": ["convenience store", "parking lot", "bakery"], "correct": 0},
        {"id": f"{scope}-x2", "kind": "match", "prompt": "Match each Québécois word to its English meaning.",
         "pairs": [{"a": vocab[k]["term"], "b": vocab[k]["gloss"]} for k in range(5)]},
        {"id": f"{scope}-x3", "kind": "cloze", "prompt": cloze_prompt, "answers": [cloze_ans]},
        {"id": f"{scope}-x4", "kind": "order", "prompt": "Put the words in the correct order (Structures).",
         "words": order_words, "answers": [order_ans]},
        {"id": f"{scope}-x5", "kind": "truefalse", "prompt": "In Quebec, the midday meal is called « le dîner ».", "correct": 0},
        {"id": f"{scope}-x6", "kind": "dictation", "prompt": "Listen and write the sentence (Compréhension orale).",
         "audio": f"m:{mid(level, theme, 'd1')}", "answers": [lines[0]["text"]]},
        {"id": f"{scope}-x7", "kind": "translate", "prompt": f"Translate into English: {vocab[0]['term']}.", "answers": [vocab[0]["gloss"]]},
        {"id": f"{scope}-x8", "kind": "write", "prompt": "Write two or three sentences about your own daily routine (Expression écrite).",
         "model": "Je me lève, je déjeune, puis je prends l'autobus."},
        {"id": f"{scope}-x9", "kind": "speak", "prompt": "Describe your last appointment in French, then compare (Expression orale).",
         "model": "Je voudrais prendre un rendez-vous, s'il vous plaît.", "audio": f"m:{mid(level, theme, slug(th['vocab'][0][0]))}"},
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
    for level in ("b1", "b2"):
        for theme in ("quotidien", "travail") if level == "b1" else ("societe", "sante"):
            media = build_media(level, theme)
            write_pack(level, theme, media)
    print("Packs Français (Québec) B1/B2 générés : 4")


if __name__ == "__main__":
    main()
