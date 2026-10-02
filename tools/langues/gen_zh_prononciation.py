#!/usr/bin/env python3
"""Pack « Chinois A0 — Prononciation » (zh-a0-phonetique-{fr,en}) : tons, sons difficiles, paires minimales.

Deux unités :
  1. Les quatre tons (妈 mā / 麻 má / 马 mǎ / 骂 mà / 吗 ma neutre) + courbes de ton.
  2. Les sons difficiles (zh ch sh r / x q / ü) + carte articulatoire + paires minimales.

Multimédia généré :
  - audio Opus 16 kHz mono (espeak-ng, voix cmn, GPL-3.0, marquée synthétique) ;
  - figures vectorielles « illustration » (courbes des 4 tons, carte des sons) ;
  - figures de traits (réutilisées de gen_a0_pilot.py si présentes, sinon ignorées).

Tout tient largement sous 10 Mo (texte + figures + audio ≈ quelques centaines de Ko).
Exécution : python3 tools/langues/gen_zh_prononciation.py
"""
import json, os, shutil, subprocess, sys, tempfile, wave

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "anim"))
import animlib  # noqa: E402

CONTENT_DIR = os.path.join(ROOT, "content", "langues")
MEDIA_DIR = os.path.join(ROOT, "content", "langues-media")

LANG = "zh"
LEVEL = "a0"
THEME = "phonetique"
ENGINE = "espeak-ng 1.52"
VOICE = "cmn"

# ---- contenu (source fr / en) -------------------------------------------------------------
# vocab : (term, reading, gloss_fr, gloss_en, pos, slug)
TONS_VOCAB = [
    ("妈", "mā", "maman (1er ton)", "mom (1st tone)", "n", "ma1"),
    ("麻", "má", "chanvre (2e ton)", "hemp (2nd tone)", "n", "ma2"),
    ("马", "mǎ", "cheval (3e ton)", "horse (3rd tone)", "n", "ma3"),
    ("骂", "mà", "gronder (4e ton)", "to scold (4th tone)", "v", "ma4"),
    ("吗", "ma", "particule (ton neutre)", "question particle (neutral tone)", "part", "ma0"),
]
SOUNDS_VOCAB = [
    ("知", "zhī", "savoir (son zh)", "to know (zh sound)", "v", "zhi"),
    ("吃", "chī", "manger (son ch)", "to eat (ch sound)", "v", "chi"),
    ("是", "shì", "être (son sh)", "to be (sh sound)", "v", "shi"),
    ("西", "xī", "ouest (son x)", "west (x sound)", "n", "xi"),
    ("七", "qī", "sept (son q)", "seven (q sound)", "num", "qi"),
    ("鱼", "yú", "poisson (voyelle ü)", "fish (ü vowel)", "n", "yu"),
    ("绿", "lǜ", "vert (voyelle ü)", "green (ü vowel)", "adj", "lv"),
    ("日", "rì", "jour, soleil (son r)", "sun, day (r sound)", "n", "ri"),
]
MINIMAL_PAIRS = [
    ("ba-pa", "八趴", "bā / pā", "bā / pā"),
    ("zha-cha", "扎差", "zhā / chā", "zhā / chā"),
    ("zu-cu", "租粗", "zū / cū", "zū / cū"),
]
FOUR_TONES = ("ma1234", "妈麻马骂")


def build_content(src):
    L = "fr" if src == "fr" else "en"
    scope = f"{LANG}-{LEVEL}-{THEME}-{src}"

    def ex(text, fr, en):
        return {"text": text, "tr": fr if L == "fr" else en}

    def vocab(items, unit_no):
        out = []
        for i, (term, reading, fr, en, pos, slug) in enumerate(items, 1):
            out.append({"id": f"{scope}-u{unit_no}-v{i}", "term": term, "reading": reading,
                        "gloss": fr if L == "fr" else en, "pos": pos, "audio": f"m:{LANG}-{slug}"})
        return out

    def exercises(specs, unit_no, scope_v):
        out = []
        for i, x in enumerate(specs, 1):
            ex = {"id": f"{scope_v}-u{unit_no}-x{i}", "kind": x["kind"], "prompt": x["prompt"][L]}
            for k in ("audio", "answers", "choices", "correct", "pairs", "words", "model"):
                if k in x:
                    v = x[k]
                    ex[k] = [c[L] for c in v] if k == "choices" else ([{"a": p["a"], "b": p[L]} for p in v] if k == "pairs" else (f"m:{LANG}-{v}" if k == "audio" else v))
            out.append(ex)
        return out

    # ---- unité 1 : les tons ----
    u1_tons_ex = [
        {"kind": "mcq", "prompt": {"fr": "« 马 (mǎ) » veut dire ?", "en": "'马 (mǎ)' means?"}, "choices": [{"fr": "maman", "en": "mom"}, {"fr": "cheval", "en": "horse"}, {"fr": "gronder", "en": "to scold"}], "correct": 1},
        {"kind": "match", "prompt": {"fr": "Associe chaque caractère à son sens.", "en": "Match each character to its meaning."}, "pairs": [{"a": "妈", "fr": "maman", "en": "mom"}, {"a": "马", "fr": "cheval", "en": "horse"}, {"a": "骂", "fr": "gronder", "en": "to scold"}, {"a": "麻", "fr": "chanvre", "en": "hemp"}]},
        {"kind": "dictation", "prompt": {"fr": "Écoute : quel caractère entends-tu ?", "en": "Listen: which character do you hear?"}, "audio": "ma3", "answers": ["马"]},
        {"kind": "speak", "prompt": {"fr": "Dis « mā » (1er ton, haut et plat), puis compare.", "en": "Say 'mā' (1st tone, high and flat), then compare."}, "model": "mā — ton 1 (55, haut plat)", "audio": "ma1"},
        {"kind": "truefalse", "prompt": {"fr": "Le 3e ton descend puis remonte.", "en": "The 3rd tone goes down then up."}, "correct": 0},
        {"kind": "mcq", "prompt": {"fr": "Quel mot porte le 4e ton (descendant) ?", "en": "Which word has the 4th tone (falling)?"}, "choices": [{"fr": "妈", "en": "妈"}, {"fr": "麻", "en": "麻"}, {"fr": "骂", "en": "骂"}], "correct": 2},
        {"kind": "translate", "prompt": {"fr": "Traduis : cheval.", "en": "Translate: horse."}, "answers": ["马"]},
        {"kind": "write", "prompt": {"fr": "Écris « maman » en caractères.", "en": "Write 'mom' in characters."}, "model": "妈"},
    ]
    u2_sounds_ex = [
        {"kind": "mcq", "prompt": {"fr": "Quel caractère contient le son « ch » ?", "en": "Which character contains the 'ch' sound?"}, "choices": [{"fr": "知", "en": "知"}, {"fr": "吃", "en": "吃"}, {"fr": "西", "en": "西"}], "correct": 1},
        {"kind": "match", "prompt": {"fr": "Associe le son au caractère.", "en": "Match the sound to the character."}, "pairs": [{"a": "知", "fr": "zh", "en": "zh"}, {"a": "吃", "fr": "ch", "en": "ch"}, {"a": "是", "fr": "sh", "en": "sh"}, {"a": "西", "fr": "x", "en": "x"}, {"a": "七", "fr": "q", "en": "q"}]},
        {"kind": "dictation", "prompt": {"fr": "Écoute : quel caractère entends-tu ?", "en": "Listen: which character do you hear?"}, "audio": "chi", "answers": ["吃"]},
        {"kind": "speak", "prompt": {"fr": "Dis « chī » (rétroflexe aspiré).", "en": "Say 'chī' (aspirated retroflex)."}, "model": "chī (rétroflexe + souffle)", "audio": "chi"},
        {"kind": "truefalse", "prompt": {"fr": "« 是 (shì) » a un son rétroflexe (langue recourbée).", "en": "'是 (shì)' has a retroflex sound (curled tongue)."}, "correct": 0},
        {"kind": "translate", "prompt": {"fr": "Traduis : poisson.", "en": "Translate: fish."}, "answers": ["鱼"]},
        {"kind": "mcq", "prompt": {"fr": "Quel son est aspiré (souffle) ?", "en": "Which sound is aspirated (breath)?"}, "choices": [{"fr": "zh", "en": "zh"}, {"fr": "ch", "en": "ch"}, {"fr": "sh", "en": "sh"}], "correct": 1},
        {"kind": "write", "prompt": {"fr": "Écris « manger » en caractères.", "en": "Write 'to eat' in characters."}, "model": "吃"},
    ]

    unit1 = {
        "id": f"{scope}-u1", "title": {"fr": "Les quatre tons", "en": "The four tones"}[L],
        "minutes": 12, "skills": ["co", "po"], "prerequisites": [],
        "vocab": vocab(TONS_VOCAB, 1),
        "dialogues": [{
            "id": f"{scope}-u1-d1", "title": {"fr": "Écouter les tons", "en": "Listening to the tones"}[L],
            "lines": [
                {"who": "A", "text": "妈。", "reading": "mā.", "tr": "Maman (1er ton)." if L == "fr" else "Mom (1st tone)."},
                {"who": "A", "text": "麻。", "reading": "má.", "tr": "Chanvre (2e ton)." if L == "fr" else "Hemp (2nd tone)."},
                {"who": "A", "text": "马。", "reading": "mǎ.", "tr": "Cheval (3e ton)." if L == "fr" else "Horse (3rd tone)."},
                {"who": "A", "text": "骂。", "reading": "mà.", "tr": "Gronder (4e ton)." if L == "fr" else "To scold (4th tone)."},
            ],
            "audio": "m:zh-ma1234",
        }],
        "grammar": [
            {"id": f"{scope}-u1-g1", "title": {"fr": "Le ton change le sens", "en": "Tone changes the meaning"}[L],
             "md": {"fr": "Le chinois est une langue à **tons** : la même syllabe **ma** change de sens selon la hauteur. Quatre tons + un ton neutre :\n- **1er** mā : haut et plat (55)\n- **2e** má : monte (35)\n- **3e** mǎ : descend puis remonte (214)\n- **4e** mà : descend (51)\n- **neutre** ma : court, sans contour.",
                    "en": "Chinese is a **tonal** language: the same syllable **ma** changes meaning with pitch. Four tones plus a neutral one:\n- **1st** mā: high and flat (55)\n- **2nd** má: rising (35)\n- **3rd** mǎ: dips then rises (214)\n- **4th** mà: falling (51)\n- **neutral** ma: short, no contour."}[L],
             "examples": [ex("妈 mā", "maman", "mom"), ex("马 mǎ", "cheval", "horse")]},
        ],
        "exercises": exercises(u1_tons_ex, 1, scope),
        "stories": [],
        "cards": [{"id": f"{scope}-u1-c1", "vocab": f"{scope}-u1-v1"}, {"id": f"{scope}-u1-c2", "vocab": f"{scope}-u1-v3"}, {"id": f"{scope}-u1-c3", "vocab": f"{scope}-u1-v4"}],
        "animations": [{"id": f"{scope}-u1-a1", "kind": "articulation", "label": {"fr": "Les courbes des quatre tons", "en": "The four tone contours"}[L]}],
    }
    unit2 = {
        "id": f"{scope}-u2", "title": {"fr": "Les sons difficiles", "en": "Difficult sounds"}[L],
        "minutes": 12, "skills": ["co", "po"], "prerequisites": [f"{scope}-u1"],
        "vocab": vocab(SOUNDS_VOCAB, 2),
        "dialogues": [{
            "id": f"{scope}-u2-d1", "title": {"fr": "Écouter les sons", "en": "Listening to the sounds"}[L],
            "lines": [
                {"who": "A", "text": "知。", "reading": "zhī.", "tr": "Savoir (zh)." if L == "fr" else "To know (zh)."},
                {"who": "A", "text": "吃。", "reading": "chī.", "tr": "Manger (ch)." if L == "fr" else "To eat (ch)."},
                {"who": "A", "text": "是。", "reading": "shì.", "tr": "Être (sh)." if L == "fr" else "To be (sh)."},
                {"who": "A", "text": "鱼。", "reading": "yú.", "tr": "Poisson (ü)." if L == "fr" else "Fish (ü)."},
            ],
            "audio": "m:zh-sounds4",
        }],
        "grammar": [
            {"id": f"{scope}-u2-g1", "title": {"fr": "zh / ch / sh : la langue se recourbe", "en": "zh / ch / sh: curl the tongue"}[L],
             "md": {"fr": "Pour **zh**, **ch**, **sh**, on **recourbe la langue** vers l'arrière (rétroflexes). **ch** ajoute un souffle (aspiré), **sh** non.",
                    "en": "For **zh**, **ch**, **sh**, **curl the tongue** back (retroflex). **ch** adds a puff of air (aspirated), **sh** does not."}[L],
             "examples": [ex("知 zhī", "savoir", "to know"), ex("吃 chī", "manger", "to eat")]},
            {"id": f"{scope}-u2-g2", "title": {"fr": "x, q et la voyelle ü", "en": "x, q and the ü vowel"}[L],
             "md": {"fr": "**x** et **q** se disent langue plate, proche du palais. La voyelle **ü** (comme dans **鱼** yú, **绿** lǜ) arrondit les lèvres : on l'écrit **u** après y, j, q, x.",
                    "en": "**x** and **q** are said with a flat tongue near the palate. The **ü** vowel (as in **鱼** yú, **绿** lǜ) rounds the lips; it is written **u** after y, j, q, x."}[L],
             "examples": [ex("鱼 yú", "poisson", "fish"), ex("绿 lǜ", "vert", "green")]},
        ],
        "exercises": exercises(u2_sounds_ex, 2, scope),
        "stories": [],
        "cards": [{"id": f"{scope}-u2-c1", "vocab": f"{scope}-u2-v1"}, {"id": f"{scope}-u2-c2", "vocab": f"{scope}-u2-v2"}, {"id": f"{scope}-u2-c3", "vocab": f"{scope}-u2-v3"}],
        "animations": [{"id": f"{scope}-u2-a1", "kind": "articulation", "label": {"fr": "La carte des sons (avant / arrière)", "en": "The sound map (front / back)"}[L]}],
    }

    return {
        "format": 1, "type": "langue", "id": scope, "version": 1,
        "target": LANG, "level": LEVEL, "theme": THEME, "source": src,
        "title": {"fr": "Chinois A0 — Prononciation (tons et sons)", "en": "Chinese A0 — Pronunciation (tones and sounds)"}[L],
        "state": "review", "units": [unit1, unit2],
    }


# ---- audio -------------------------------------------------------------------------------
def wav_ms(path):
    with wave.open(path) as w:
        return int(1000 * w.getnframes() / w.getframerate())


def synth_opus(text, dst):
    with tempfile.TemporaryDirectory() as t:
        wav = os.path.join(t, "x.wav")
        subprocess.run(["espeak-ng", "-v", VOICE, "-s", "110", text, "-w", wav], check=True)
        ms = wav_ms(wav)
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-ac", "1", "-ar", "16000",
                        "-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-c:a", "libopus", "-b:a", "24k", dst], check=True)
        return os.path.getsize(dst), ms


def audio_list():
    out = []
    for term, _reading, _fr, _en, _pos, slug in TONS_VOCAB + SOUNDS_VOCAB:
        out.append((f"{LANG}-{slug}", term))
    out.append((f"{LANG}-{FOUR_TONES[0]}", FOUR_TONES[1]))
    out.append((f"{LANG}-sounds4", "知吃是鱼"))
    for slug, text, _p, _p2 in MINIMAL_PAIRS:
        out.append((f"{LANG}-{slug}", text))
    return out


def build_media():
    scope = f"{LANG}-{LEVEL}-{THEME}"
    os.makedirs(os.path.join(MEDIA_DIR, scope, "audio"), exist_ok=True)
    media = []
    for mid, tts in audio_list():
        rel = f"audio/{mid}.opus"
        dst = os.path.join(MEDIA_DIR, scope, rel)
        b, ms = synth_opus(tts, dst)
        media.append({"id": mid, "file": rel, "kind": "audio", "bytes": b, "durationMs": ms,
                      "license": "CASTBRIDGE-ORIGINAL", "engine": f"{ENGINE} (voix {VOICE})",
                      "synthetic": True, "voiceLicense": "GPL-3.0", "lang": LANG,
                      "source": "CastBridge, voix de synthèse libre (GPL-3.0, moteur uniquement)"})
    return media


# ---- figures -----------------------------------------------------------------------------
def tone_contour_block(src):
    L = "fr" if src == "fr" else "en"
    cap = {"fr": "Les quatre tons", "en": "The four tones"}[L]
    alt = {"fr": "Quatre panneaux avec une portée de hauteur et la courbe de chaque ton : 1 haut plat, 2 montant, 3 descendant puis remontant, 4 descendant.",
           "en": "Four panels with a pitch staff and each tone's contour: 1 high flat, 2 rising, 3 dipping then rising, 4 falling."}[L]
    a = animlib.Anim(480, 300, mode="steps")
    a.add(animlib.text(240, 22, cap, 22, "blue", bold=True))
    contours = [("1", "mā", "M35 55 L85 55"), ("2", "má", "M35 80 L85 45"), ("3", "mǎ", "M35 50 L60 100 L85 60"), ("4", "mà", "M35 45 L85 100")]
    for k, (num, ex, d) in enumerate(contours):
        x = 40 + k * 108
        a.add(animlib.text(x + 45, 50, num, 18, "grey", bold=True))
        for lv, y in ((5, 60), (4, 80), (3, 100), (2, 120), (1, 140)):
            a.add(animlib.line(x + 10, y, x + 80, y, "lightgrey", 1))
        a.add(animlib.text(x + 45, 160, ex, 20, "ink", bold=True))
        pid = a.add(animlib.path(d, None, "red", 3), draw=0)
        with a.step(f"Ton {num} : {ex}." if L == "fr" else f"Tone {num}: {ex}.") as s:
            s.draw(pid, 1.0)
    return a.block(alt, cap)


def sound_map_block(src):
    L = "fr" if src == "fr" else "en"
    cap = {"fr": "La carte des sons", "en": "The sound map"}[L]
    alt = {"fr": "Un axe de l'avant vers l'arrière de la bouche avec trois groupes : z/c/s à l'avant, j/q/x au milieu, zh/ch/sh recourbés à l'arrière.",
           "en": "An axis from the front to the back of the mouth with three groups: z/c/s at the front, j/q/x in the middle, zh/ch/sh curled at the back."}[L]
    a = animlib.Anim(480, 200, mode="steps")
    a.add(animlib.line(30, 120, 450, 120, "ink", 2, arrow="end"))
    a.add(animlib.text(30, 140, {"fr": "avant", "en": "front"}[L], 14, "grey"))
    a.add(animlib.text(450, 140, {"fr": "arrière", "en": "back"}[L], 14, "grey", "end"))
    groups = [(110, "z · c · s", {"fr": "plat, à l'avant", "en": "flat, front"}[L], "blue"),
              (240, "j · q · x", {"fr": "milieu, palais", "en": "middle, palate"}[L], "green"),
              (390, "zh · ch · sh", {"fr": "langue recourbée", "en": "curled tongue"}[L], "red")]
    for x, label, sub, col in groups:
        a.add(animlib.circle(x, 120, 7, col, None), id=f"d{x}", alpha=0)
        a.add(animlib.text(x, 95, label, 16, col, bold=True), id=f"l{x}", alpha=0)
        a.add(animlib.text(x, 165, sub, 12, "grey"), id=f"s{x}", alpha=0)
    for x, label, _sub, _col in groups:
        say = f"Le groupe {label}." if L == "fr" else f"The {label} group."
        with a.step(say) as s:
            s.show(f"d{x}", 0.3); s.show(f"l{x}", 0.3); s.show(f"s{x}", 0.3)
    return a.block(alt, cap)


# ---- écriture ----------------------------------------------------------------------------
def main():
    if not shutil.which("espeak-ng"):
        sys.exit("espeak-ng manquant")
    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg manquant")
    media = build_media()
    for src in ("fr", "en"):
        scope = f"{LANG}-{LEVEL}-{THEME}-{src}"
        dstdir = os.path.join(CONTENT_DIR, scope)
        os.makedirs(dstdir, exist_ok=True)
        with open(os.path.join(dstdir, "langue.json"), "w", encoding="utf-8") as f:
            json.dump(build_content(src), f, ensure_ascii=False, indent=1)
        with open(os.path.join(dstdir, "media.json"), "w", encoding="utf-8") as f:
            json.dump({"format": 1, "comment": "Médias référencés du lot texte ; fichiers dans le lot média jumeau.",
                       "media": media}, f, ensure_ascii=False, indent=1)
        figdir = os.path.join(dstdir, "figures")
        os.makedirs(figdir, exist_ok=True)
        for figid, block in ((f"{scope}-u1-a1", tone_contour_block(src)), (f"{scope}-u2-a1", sound_map_block(src))):
            with open(os.path.join(figdir, f"{figid}.json"), "w", encoding="utf-8") as f:
                json.dump(block, f, ensure_ascii=False, indent=1)
    # rapport de taille
    total = sum(m["bytes"] for m in media)
    import glob
    text_bytes = 0
    for f in glob.glob(os.path.join(CONTENT_DIR, f"{LANG}-{LEVEL}-{THEME}-*", "**", "*"), recursive=True):
        if os.path.isfile(f):
            text_bytes += os.path.getsize(f)
    print(f"Packs générés : {LANG}-{LEVEL}-{THEME}-{{fr,en}}")
    print(f"  audio : {len(media)} pistes, {total/1024:.1f} Ko")
    print(f"  texte + figures : {text_bytes/1024:.1f} Ko")
    print(f"  TOTAL : {(total + text_bytes)/1024:.1f} Ko  (budget 10 Mo = 10 240 Ko)")


if __name__ == "__main__":
    main()
