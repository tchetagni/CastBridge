#!/usr/bin/env python3
"""Génère de courtes vidéos MP4 (H.264 480p, ≤ 30 s) pour la catégorie « Langues » :
une « carte vidéo » de salutation par langue cible (7 vidéos), avec le mot, sa lecture et sa traduction,
accompagnée de la piste audio synthétique déjà produite (docs/LANGUES.md § 4.3, 7.2, 8).

Rendu : images PIL (fade-in) + ffmpeg (libx264 + AAC). Sans personne réelle, sous-titres incrustés.
Les fichiers vont dans content/langues-media/<lang>-a0-salut/video/ et sont ajoutés au media.json du lot texte.
Exécution : python3 tools/langues/gen_videos.py
"""
import json, os, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
MEDIA_DIR = os.path.join(ROOT, "content", "langues-media")

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("Pillow manquant (pip install pillow)")

W, H = 854, 480
FPS = 25
DUR = 5  # secondes

FONTS = [
    "/System/Library/Fonts/STHeiti Medium.ttc",
    "/System/Library/Fonts/Hiragino Sans GB.ttc",
    "/System/Library/Fonts/AppleSDGothicNeo.ttc",
    "/System/Library/Fonts/Helvetica.ttc",
]

# (langue, thème, mot, lecture, traduction fr, traduction en, slug audio)
VIDEOS = [
    ("zh", "salut", "你好", "nǐ hǎo", "Bonjour", "Hello", "zh-nihao"),
    ("ja", "salut", "こんにちは", "konnichiwa", "Bonjour", "Hello", "ja-konnichiwa"),
    ("en", "salut", "Hello", "", "Bonjour", "Hello", "en-hello"),
    ("de", "salut", "Hallo", "", "Bonjour", "Hello", "de-hallo"),
    ("es", "salut", "Hola", "", "Bonjour / Salut", "Hello / Hi", "es-hola"),
    ("it", "salut", "Ciao", "", "Salut", "Hi", "it-ciao"),
    ("fr", "salut", "Bonjour", "", "Bonjour", "Hello", "fr-bonjour"),
]


def font(size):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, size)
        except Exception:
            continue
    return ImageFont.load_default()


def render_frame(word, reading, tr_fr, tr_en, alpha, out):
    img = Image.new("RGB", (W, H), (24, 34, 56))
    d = ImageDraw.Draw(img)
    f_word = font(120)
    f_reading = font(44)
    f_tr = font(38)
    # word (with fade)
    col = int(255 * alpha)
    d.text((W / 2, 190), word, font=f_word, fill=(col, col, col), anchor="mm")
    if reading:
        d.text((W / 2, 290), reading, font=f_reading, fill=(col, col, 140), anchor="mm")
    d.text((W / 2, 360), f"{tr_fr}  ·  {tr_en}", font=f_tr, fill=(200, 200, 200), anchor="mm")
    d.text((W / 2, 440), "CastBridge — Langues (voix de synthèse)", font=ImageFont.truetype(FONTS[-1], 20) or ImageFont.load_default(),
           fill=(120, 130, 150), anchor="mm")
    img.save(out)


def build_video(lang, theme, word, reading, tr_fr, tr_en, audio_slug):
    scope = f"{lang}-a0-{theme}"
    src = os.path.join(MEDIA_DIR, scope, "audio", f"{audio_slug}.opus")
    if not os.path.isfile(src):
        print(f"  (audio {audio_slug} introuvable, vidéo ignorée)")
        return None
    outdir = os.path.join(MEDIA_DIR, scope, "video")
    os.makedirs(outdir, exist_ok=True)
    out = os.path.join(outdir, f"{lang}-{theme}-salut.mp4")
    with tempfile.TemporaryDirectory() as t:
        n = FPS * DUR
        for i in range(n):
            # alpha: montée rapide puis maintien
            a = min(1.0, (i / (FPS * 1.2)))
            render_frame(word, reading, tr_fr, tr_en, a, os.path.join(t, f"{i:04d}.png"))
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-framerate", str(FPS), "-i", os.path.join(t, "%04d.png"),
                        "-i", src, "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "28", "-preset", "fast",
                        "-c:a", "aac", "-b:a", "32k", "-shortest", out], check=True)
    return {"id": f"{lang}-{theme}-salut", "file": f"video/{lang}-{theme}-salut.mp4", "kind": "video",
            "bytes": os.path.getsize(out), "durationMs": DUR * 1000, "license": "CASTBRIDGE-ORIGINAL",
            "synthetic": False, "lang": lang,
            "source": "CastBridge, animation vectorielle rendue (sans personne réelle), audio de synthèse inclus"}


def main():
    if not subprocess.run(["ffmpeg", "-version"], capture_output=True).returncode == 0:
        sys.exit("ffmpeg manquant")
    for lang, theme, word, reading, tr_fr, tr_en, audio in VIDEOS:
        v = build_video(lang, theme, word, reading, tr_fr, tr_en, audio)
        if v is None:
            continue
        # ajoute l'entrée au media.json du lot texte (partagé fr/en : un seul media.json suffit, ils sont identiques)
        for src in ("fr", "en"):
            p = os.path.join(ROOT, "content", "langues", f"{lang}-a0-{theme}-{src}", "media.json")
            if not os.path.isfile(p):
                continue
            m = json.load(open(p, encoding="utf-8"))
            m.setdefault("media", [])
            m["media"] = [x for x in m["media"] if x.get("id") != v["id"]] + [v]
            open(p, "w", encoding="utf-8").write(json.dumps(m, ensure_ascii=False, indent=1))
        print(f"✓ {v['file']}  {v['bytes']/1024:.0f} Ko")


if __name__ == "__main__":
    main()
