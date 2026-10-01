"""Contrôles automatiques à la réception d'un média (docs/MEDIA-PIPELINE.md § 6). Sans réseau, sans Google : ffprobe / ffmpeg locaux (absents = « non vérifiable »)."""
import json
import re
import shutil
import subprocess
from pathlib import Path

DEFAULT_LIMITS = {
    "audio": {"codec": "opus", "channels": 1, "sampleRateHz": 16000, "bitrateToleranceRatio": 1.35, "targetLufs": -16.0, "lufsTolerance": 1.5,
              "silenceDb": -50, "leadingMaxS": 0.5, "trailingMaxS": 0.8, "minDurationS": 0.2},
    "image": {"maxBytes": 122880},
    "video": {"codec": "h264", "maxHeight": 480, "maxDurationS": 30},
    "lotMaxBytes": 104857600, "phoneQuotaBytes": 524288000, "envelopeBytes": 6442450944,
}
PERSONAL_TAGS = {"artist", "album_artist", "album", "title", "comment", "date", "creation_time", "location", "genre", "composer", "copyright", "author", "username", "gps", "make", "model", "software_author"}
NOT_VERIFIABLE = "non vérifiable dans cet environnement"


def have_tools():
    return bool(shutil.which("ffprobe") and shutil.which("ffmpeg"))


def _probe(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-print_format", "json", "-show_format", "-show_streams", str(path)], capture_output=True, text=True)
    if out.returncode != 0:
        return None
    return json.loads(out.stdout)


def opus_input_rate(path):
    """Fréquence d'entrée déclarée dans l'en-tête OpusHead (ffprobe annonce toujours 48000 Hz pour Opus : fréquence de décodage). None si absent."""
    head = Path(path).read_bytes()[:4096]
    i = head.find(b"OpusHead")
    if i < 0 or len(head) < i + 16:
        return None
    return int.from_bytes(head[i + 12:i + 16], "little")


def _tags(probe):
    tags = {}
    for scope in [probe.get("format", {})] + probe.get("streams", []):
        for k, v in (scope.get("tags") or {}).items():
            tags[k.lower()] = v
    return tags


def _personal(probe):
    bad = sorted(k for k in _tags(probe) if k in PERSONAL_TAGS)
    return ["métadonnées personnelles ou d'identification présentes : %s" % ", ".join(bad)] if bad else []


def _loudness(path):
    p = subprocess.run(["ffmpeg", "-nostats", "-hide_banner", "-i", str(path), "-af", "ebur128=framelog=quiet", "-f", "null", "-"], capture_output=True, text=True)
    m = re.findall(r"I:\s+(-?\d+(?:\.\d+)?)\s+LUFS", p.stderr)
    return float(m[-1]) if m else None


def _silence(path, db):
    p = subprocess.run(["ffmpeg", "-nostats", "-hide_banner", "-i", str(path), "-af", "silencedetect=noise=%ddB:d=0.1" % db, "-f", "null", "-"], capture_output=True, text=True)
    starts = [float(x) for x in re.findall(r"silence_start:\s*(-?\d+(?:\.\d+)?)", p.stderr)]
    ends = [(float(a), float(b)) for a, b in re.findall(r"silence_end:\s*(-?\d+(?:\.\d+)?)\s*\|\s*silence_duration:\s*(-?\d+(?:\.\d+)?)", p.stderr)]
    return starts, ends


def check_audio(path, request, limits=None):
    """Retourne (problèmes, mesures). Une mesure impossible est signalée « non vérifiable », jamais déduite conforme."""
    lim = (limits or DEFAULT_LIMITS)["audio"]
    c = request["constraints"]
    problems, m = [], {}
    if not have_tools():
        return ["contrôle audio %s (ffprobe/ffmpeg absents)" % NOT_VERIFIABLE], m
    probe = _probe(path)
    if probe is None:
        return ["fichier illisible par ffprobe"], m
    st = next((s for s in probe.get("streams", []) if s.get("codec_type") == "audio"), None)
    if st is None:
        return ["aucun flux audio"], m
    fmt = probe.get("format", {})
    m.update({"codec": st.get("codec_name"), "channels": st.get("channels"), "sampleRateHz": (opus_input_rate(path) if st.get("codec_name") == "opus" else None) or int(st.get("sample_rate", 0) or 0), "container": fmt.get("format_name"),
              "bytes": int(fmt.get("size", 0) or 0)})
    dur = float(fmt.get("duration") or st.get("duration") or 0)
    m["durationS"] = round(dur, 3)
    br = fmt.get("bit_rate") or st.get("bit_rate")
    m["bitrateKbps"] = round(int(br) / 1000, 1) if br else None
    if m["codec"] != c.get("codec", lim["codec"]):
        problems.append("codec %s au lieu de %s" % (m["codec"], c.get("codec", lim["codec"])))
    if "ogg" not in (m["container"] or ""):
        problems.append("conteneur %s au lieu de ogg" % m["container"])
    if m["channels"] != c.get("channels", lim["channels"]):
        problems.append("%s canaux au lieu de %s (mono)" % (m["channels"], c.get("channels", lim["channels"])))
    if m["sampleRateHz"] != c.get("sampleRateHz", lim["sampleRateHz"]):
        problems.append("fréquence %s Hz au lieu de %s Hz" % (m["sampleRateHz"], c.get("sampleRateHz", lim["sampleRateHz"])))
    if c.get("bitrateKbps") and m["bitrateKbps"] and m["bitrateKbps"] > c["bitrateKbps"] * lim["bitrateToleranceRatio"]:
        problems.append("débit %.1f kbit/s au-dessus de %s kbit/s (tolérance %.0f %%)" % (m["bitrateKbps"], c["bitrateKbps"], (lim["bitrateToleranceRatio"] - 1) * 100))
    if c.get("maxDurationS") and dur > c["maxDurationS"]:
        problems.append("durée %.2f s au-dessus de %s s" % (dur, c["maxDurationS"]))
    if dur < lim["minDurationS"]:
        problems.append("durée %.2f s : fichier quasi vide" % dur)
    lufs = _loudness(path)
    m["lufs"] = lufs
    target = c.get("targetLufs", lim["targetLufs"])
    if lufs is None:
        problems.append("niveau sonore (LUFS) %s" % NOT_VERIFIABLE)
    elif abs(lufs - target) > lim["lufsTolerance"]:
        problems.append("niveau %.1f LUFS, cible %s ± %s" % (lufs, target, lim["lufsTolerance"]))
    starts, ends = _silence(path, lim["silenceDb"])
    lead = ends[0][0] if starts and starts[0] <= 0.02 and ends else 0.0
    trail = 0.0
    if starts and (not ends or len(starts) > len(ends)):
        trail = max(0.0, dur - starts[-1])
    elif ends and abs(ends[-1][0] - dur) < 0.05:
        trail = ends[-1][1]
    m["leadingSilenceS"], m["trailingSilenceS"] = round(lead, 3), round(trail, 3)
    if lead > lim["leadingMaxS"]:
        problems.append("silence en tête de %.2f s (max %s s)" % (lead, lim["leadingMaxS"]))
    if trail > lim["trailingMaxS"]:
        problems.append("silence en queue de %.2f s (max %s s)" % (trail, lim["trailingMaxS"]))
    problems += _personal(probe)
    return problems, m


def check_image(path, request, limits=None):
    lim = (limits or DEFAULT_LIMITS)["image"]
    data = Path(path).read_bytes()
    problems, m = [], {"bytes": len(data)}
    if data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        problems.append("ce n'est pas un fichier WebP")
        return problems, m
    maxb = request["constraints"].get("maxBytes", lim["maxBytes"])
    if len(data) > maxb:
        problems.append("%d octets au-dessus de %d (WebP)" % (len(data), maxb))
    for chunk in (b"EXIF", b"XMP ", b"ICCP"):
        if chunk in data[12:]:
            if chunk != b"ICCP":
                problems.append("métadonnées %s présentes dans le WebP : à retirer" % chunk.decode().strip())
    return problems, m


def check_video(path, request, limits=None):
    lim = (limits or DEFAULT_LIMITS)["video"]
    c = request["constraints"]
    if not have_tools():
        return ["contrôle vidéo %s (ffprobe absent)" % NOT_VERIFIABLE], {}
    probe = _probe(path)
    if probe is None:
        return ["fichier illisible par ffprobe"], {}
    st = next((s for s in probe.get("streams", []) if s.get("codec_type") == "video"), None)
    if st is None:
        return ["aucun flux vidéo"], {}
    dur = float(probe.get("format", {}).get("duration") or 0)
    m = {"codec": st.get("codec_name"), "height": st.get("height"), "durationS": round(dur, 3), "bytes": int(probe.get("format", {}).get("size", 0) or 0)}
    problems = []
    if m["codec"] != c.get("codec", lim["codec"]):
        problems.append("codec vidéo %s au lieu de %s" % (m["codec"], c.get("codec", lim["codec"])))
    if (m["height"] or 0) > c.get("maxHeight", lim["maxHeight"]):
        problems.append("hauteur %s px au-dessus de %s" % (m["height"], c.get("maxHeight", lim["maxHeight"])))
    if dur > c.get("maxDurationS", lim["maxDurationS"]):
        problems.append("durée %.1f s au-dessus de %s s" % (dur, c.get("maxDurationS", lim["maxDurationS"])))
    problems += _personal(probe)
    return problems, m


def check_file(path, request, limits=None):
    return {"audio": check_audio, "image": check_image, "video": check_video}[request["kind"]](path, request, limits)


def check_totals(sizes, lot_bytes_before=0, phone_bytes_before=0, limits=None):
    """Plafonds cumulés : lot ≤ 100 Mo, quota téléphone ≤ 500 Mo, enveloppe ≤ 6 Go. [sizes] = octets des fichiers acceptés dans ce lot."""
    lim = limits or DEFAULT_LIMITS
    lot = lot_bytes_before + sum(sizes)
    problems = []
    if lot > lim["lotMaxBytes"]:
        problems.append("lot de %d octets au-dessus de %d (100 Mo)" % (lot, lim["lotMaxBytes"]))
    if phone_bytes_before + sum(sizes) > lim["phoneQuotaBytes"]:
        problems.append("quota téléphone dépassé (500 Mo)")
    return problems
