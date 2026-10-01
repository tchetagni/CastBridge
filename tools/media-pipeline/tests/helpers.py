import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

HAVE_FFMPEG = bool(shutil.which("ffmpeg") and shutil.which("ffprobe"))


def tmpdir():
    d = tempfile.TemporaryDirectory()
    return d, Path(d.name)


def pack(scope="zh-a0-test-fr", target="zh", level="A0"):
    return {"format": 1, "type": "langue", "id": scope, "version": 1, "target": target, "source": "fr", "level": level, "theme": "test", "title": "t", "state": "review",
            "units": [{"id": scope + "-u1", "title": "u", "vocab": [
                {"id": scope + "-v1", "term": "你好", "reading": "nǐ hǎo", "gloss": "bonjour", "audio": "m:t-nihao", "image": "m:t-img-nihao"},
                {"id": scope + "-v2", "term": "谢谢", "reading": "xièxie", "gloss": "merci", "audio": "m:t-xiexie"}],
                "dialogues": [{"id": scope + "-d1", "title": "Rencontre", "audio": "m:t-d1", "lines": [
                    {"who": "A", "text": "你好！", "reading": "nǐ hǎo!", "tr": "Bonjour !", "audio": "m:t-d1-a"},
                    {"who": "B", "text": "你好，谢谢。", "reading": "nǐ hǎo, xièxie.", "tr": "Bonjour, merci.", "audio": "m:t-d1-b"}]}],
                "exercises": [{"id": scope + "-x1", "kind": "dictation", "prompt": "Écoute.", "audio": "m:t-dict", "answers": ["谢谢"]},
                              {"id": scope + "-x2", "kind": "mcq", "prompt": "Écoute.", "audio": "m:t-mcq"}]}]}


def write_pack(root, p):
    d = Path(root) / p["id"]
    d.mkdir(parents=True, exist_ok=True)
    (d / "langue.json").write_text(json.dumps(p, ensure_ascii=False), encoding="utf-8")


def tone(path, seconds=1.5, rate=16000, channels=1, bitrate="16k", lufs=-16, lead=0.0, tags=None):
    """Fichier Opus de test généré localement par ffmpeg (aucun réseau)."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    src = "sine=frequency=440:duration=%s" % seconds
    af = "loudnorm=I=%s:TP=-1.5:LRA=11" % lufs
    if lead:
        af = "adelay=%d:all=1,%s" % (int(lead * 1000), af)
    cmd = ["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", src, "-af", af, "-ar", str(rate), "-ac", str(channels), "-c:a", "libopus", "-b:a", bitrate]
    for k, v in (tags or {}).items():
        cmd += ["-metadata", "%s=%s" % (k, v)]
    subprocess.run(cmd + [str(path)], check=True)
    return path


def registries(approved=True):
    engines = {"format": 1, "engines": [{"id": "eng1", "provider": "P", "service": "S", "model": "M", "version": "1", "allowedUse": "TEST", "termsUrl": "http://x", "termsCheckedOn": "2000-01-01",
                                       "termsEvidence": "legal/x", "syntheticMarkingRequired": False, "status": "approved" if approved else "à renseigner"}]}
    return engines
