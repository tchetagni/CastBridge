#!/usr/bin/env python3
"""Synthèse vocale par lots pour les lots média « Langues » (docs/LANGUES.md § 4.3, 7.2, 8).
  tts_synth.py --lines lignes.json --out DOSSIER [--engine kokoro|melo|piper|espeak] [--bitrate 24k]
lignes.json : [{"id":"zh-nihao","lang":"zh","text":"你好","slow":false}, …]
Écrit DOSSIER/audio/<id>.opus (Opus mono 16 kHz) et DOSSIER/media.json (licence, moteur, voix, synthetic=true, taille, durée).
ÉTAT : `espeak` testé dans le cloud (voix robotique, secours). `kokoro`, `melo`, `piper` sont écrits mais NON TESTÉS : leurs modèles sont sur Hugging Face,
injoignable depuis le cloud ; à lancer sur un poste avec accès réseau, après `pip install kokoro soundfile` (ou `melotts`, `piper-tts`).
Vérifier la licence de CHAQUE modèle/voix au téléchargement et la reporter dans VOICES (une voix NC est refusée par le script)."""
import argparse, json, os, shutil, subprocess, sys, tempfile, wave

FREE = {"CC0", "PD", "CC-BY-4.0", "CC-BY-SA-4.0", "CC-BY-3.0", "MIT", "Apache-2.0", "BSD", "OFL-1.1", "GPL-3.0"}
# langue -> moteur -> (voix, licence de la voix). Valeurs de départ À VÉRIFIER au téléchargement.
VOICES = {
  "kokoro": {"en": ("af_heart", "Apache-2.0"), "fr": ("ff_siwis", "Apache-2.0"), "it": ("if_sara", "Apache-2.0"), "es": ("ef_dora", "Apache-2.0"),
             "ja": ("jf_alpha", "Apache-2.0"), "zh": ("zf_xiaobei", "Apache-2.0")},
  "melo": {"en": ("EN-BR", "MIT"), "es": ("ES", "MIT"), "fr": ("FR", "MIT"), "zh": ("ZH", "MIT"), "ja": ("JP", "MIT")},
  "piper": {"de": ("de_DE-thorsten-high", "CC0"), "fr": ("fr_FR-siwis-medium", "CC-BY-4.0"), "es": ("es_ES-davefx-medium", "CC0"),
            "it": ("it_IT-paola-medium", "CC0"), "en": ("en_GB-alba-medium", "CC-BY-4.0"), "zh": ("zh_CN-huayan-medium", "CC0")},
  "espeak": {"zh": ("cmn", "GPL-3.0"), "ja": ("ja", "GPL-3.0"), "en": ("en", "GPL-3.0"), "de": ("de", "GPL-3.0"), "fr": ("fr", "GPL-3.0"), "it": ("it", "GPL-3.0"), "es": ("es", "GPL-3.0")},
}
KOKORO_LANG = {"en": "a", "fr": "f", "it": "i", "es": "e", "ja": "j", "zh": "z"}

def wav_ms(path):
    with wave.open(path) as w: return int(1000 * w.getnframes() / w.getframerate())

def synth(engine, lang, text, voice, wav, slow):
    if engine == "espeak":
        subprocess.run(["espeak-ng", "-v", voice, "-s", "110" if slow else "150", text, "-w", wav], check=True)
    elif engine == "piper":
        subprocess.run([sys.executable, "-m", "piper", "-m", voice, "-f", wav], input=text.encode(), check=True)
    elif engine == "kokoro":
        import numpy as np, soundfile as sf  # noqa
        from kokoro import KPipeline
        pipe = KPipeline(lang_code=KOKORO_LANG[lang])
        audio = np.concatenate([a for _, _, a in pipe(text, voice=voice, speed=0.8 if slow else 1.0)])
        sf.write(wav, audio, 24000)
    elif engine == "melo":
        from melo.api import TTS
        m = TTS(language=voice if voice in ("ZH", "JP") else lang.upper(), device="cpu")
        m.tts_to_file(text, m.hps.data.spk2id[voice], wav, speed=0.8 if slow else 1.0)
    else:
        raise SystemExit("moteur inconnu : " + engine)

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lines", required=True); ap.add_argument("--out", required=True)
    ap.add_argument("--engine", default="espeak", choices=sorted(VOICES)); ap.add_argument("--bitrate", default="24k")
    a = ap.parse_args()
    if not shutil.which("ffmpeg"): sys.exit("ffmpeg manquant")
    os.makedirs(os.path.join(a.out, "audio"), exist_ok=True)
    media = []
    for ln in json.load(open(a.lines, encoding="utf-8")):
        voice, lic = VOICES[a.engine].get(ln["lang"], (None, None))
        if voice is None: sys.exit(f"pas de voix {a.engine} pour {ln['lang']}")
        if lic not in FREE: sys.exit(f"voix {voice} : licence {lic} non libre")
        with tempfile.TemporaryDirectory() as t:
            wav = os.path.join(t, "x.wav"); synth(a.engine, ln["lang"], ln["text"], voice, wav, ln.get("slow", False))
            ms = wav_ms(wav); rel = f"audio/{ln['id']}.opus"; dst = os.path.join(a.out, rel)
            subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-ac", "1", "-ar", "16000", "-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-c:a", "libopus", "-b:a", a.bitrate, dst], check=True)
        media.append({"id": ln["id"], "file": rel, "kind": "audio", "bytes": os.path.getsize(dst), "durationMs": ms, "license": "CASTBRIDGE-ORIGINAL",
                      "engine": f"{a.engine} ({voice})", "synthetic": True, "voiceLicense": lic, "lang": ln["lang"], "source": "CastBridge, voix de synthèse libre"})
    json.dump({"format": 1, "media": media}, open(os.path.join(a.out, "media.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(f"{len(media)} pistes, {sum(m['bytes'] for m in media)} octets, {sum(m['durationMs'] for m in media) / 1000:.1f} s")

if __name__ == "__main__": main()
