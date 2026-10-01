#!/usr/bin/env python3
"""Media manifest checker (docs/MEDIA-POLICY.md): every asset of content/ is declared in content/MEDIA-MANIFEST.json, exists,
matches its sha256 and size, respects the budget of its kind, has its accessibility texts and belongs to exactly one lot.

Budgets: figure <= 8 KB, animation <= 40 KB, image <= 120 KB and <= 1280 px (WebP), audio <= 200 KB per 30 s (Opus),
clip <= 15 MB and <= 30 s (480p H.264/WebM, with a poster). Provenance: original, generated or CC0 only.
Usage: check_media.py [--content DIR]      exit 1 on any error
"""
import argparse, hashlib, json, math, os, re, struct, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "content-lib"))
import lotlib as L  # noqa: E402

KINDS = ("figure", "animation", "image", "audio", "clip")
ORIGINS = ("original", "generated", "cc0")
LICENCES = {"original": "CastBridge-original", "generated": "CastBridge-original", "cc0": "CC0-1.0"}
EXT = {"figure": (".json", ".svg"), "animation": (".json",), "image": (".webp",), "audio": (".opus", ".ogg"), "clip": (".webm", ".mp4")}
MAX_FLASH_HZ = 3  # WCAG 2.3.1: no more than three flashes per second
MAX_BYTES = {"figure": 8 << 10, "animation": 40 << 10, "image": 120 << 10, "clip": 15 << 20}
ASSET_ID = re.compile(r"^[a-z0-9][a-z0-9._-]{2,80}$")


def webp_size(path):
    """(width, height) of a WebP file read from its header (VP8, VP8L, VP8X), or None."""
    with open(path, "rb") as f:
        h = f.read(40)
    if len(h) < 30 or h[:4] != b"RIFF" or h[8:12] != b"WEBP":
        return None
    t = h[12:16]
    if t == b"VP8X":
        return 1 + int.from_bytes(h[24:27], "little"), 1 + int.from_bytes(h[27:30], "little")
    if t == b"VP8L":
        b = int.from_bytes(h[21:25], "little")
        return 1 + (b & 0x3FFF), 1 + ((b >> 14) & 0x3FFF)
    if t == b"VP8 ":
        return struct.unpack("<HH", h[26:30])[0] & 0x3FFF, struct.unpack("<HH", h[26:30])[1] & 0x3FFF
    return None


def sha256(path):
    d = hashlib.sha256()
    with open(path, "rb") as f:
        for c in iter(lambda: f.read(1 << 20), b""):
            d.update(c)
    return d.hexdigest()


def lesson_ids_and_media(content):
    ids, refs = set(), []
    learn = os.path.join(content, "learn")
    for root, _, files in os.walk(learn) if os.path.isdir(learn) else []:
        for fn in files:
            if root.endswith("lessons") and fn.endswith(".json"):
                d = L.read_json(os.path.join(root, fn))
                for l in d.get("lessons", []):
                    ids.add(l["id"])
                    for m in l.get("media", []):
                        refs.append((l["id"], m))
                for x in d.get("exercises", []):
                    for m in x.get("media", []):
                        refs.append((x["id"], m))
    return ids, refs


def check(content):
    errors = []
    mm = L.media_manifest(content)
    scopes = {}
    sp = os.path.join(content, "graph", "scopes.json")
    if os.path.isfile(sp):
        scopes = {s["id"]: s for s in L.read_json(sp)["scopes"]}
    lessons, refs = lesson_ids_and_media(content)
    seen_ids, seen_paths = set(), set()
    for a in mm.get("assets", []):
        aid = a.get("id", "?")
        w = "média %s" % aid
        if not ASSET_ID.match(aid or ""):
            errors.append("%s: identifiant invalide" % w)
        if aid in seen_ids:
            errors.append("%s: id en double" % w)
        seen_ids.add(aid)
        kind = a.get("kind")
        if kind not in KINDS:
            errors.append("%s: type « %s » inconnu (%s)" % (w, kind, ", ".join(KINDS))); continue
        path = a.get("path", "")
        if os.path.isabs(path) or ".." in path.split("/"):
            errors.append("%s: chemin non relatif" % w); continue
        if path in seen_paths:
            errors.append("%s: chemin déjà déclaré" % w)
        seen_paths.add(path)
        full = os.path.join(content, path)
        if not os.path.isfile(full):
            errors.append("%s: fichier absent (%s)" % (w, path)); continue
        size = os.path.getsize(full)
        if not path.lower().endswith(EXT[kind]):
            errors.append("%s: extension de %s attendue parmi %s" % (w, kind, "/".join(EXT[kind])))
        if size != a.get("bytes"):
            errors.append("%s: taille %d ≠ manifeste %s" % (w, size, a.get("bytes")))
        if sha256(full) != a.get("sha256"):
            errors.append("%s: sha256 différent du manifeste" % w)
        if size > L.FILE_MAX:
            errors.append("%s: fichier > 50 Mo" % w)
        # budgets
        if kind in MAX_BYTES and size > MAX_BYTES[kind]:
            errors.append("%s: %d o > budget %s de %d o" % (w, size, kind, MAX_BYTES[kind]))
        if kind == "image":
            dim = webp_size(full)
            if dim is None:
                errors.append("%s: WebP illisible" % w)
            elif max(dim) > 1280:
                errors.append("%s: %dx%d > 1280 px" % (w, dim[0], dim[1]))
        if kind in ("audio", "clip"):
            dur = a.get("durationS")
            if not isinstance(dur, (int, float)) or dur <= 0:
                errors.append("%s: durationS manquante" % w)
            elif kind == "audio" and size > 200 * 1024 * max(1.0, dur / 30.0):
                errors.append("%s: audio %d o pour %.0f s > 200 Ko par 30 s" % (w, size, dur))
            elif kind == "clip" and dur > 30:
                errors.append("%s: clip de %.0f s > 30 s" % (w, dur))
        if kind == "clip":
            poster = a.get("poster")
            if not poster or not os.path.isfile(os.path.join(content, poster)):
                errors.append("%s: affiche (poster) manquante" % w)
            if (a.get("height") or 0) > 480:
                errors.append("%s: hauteur %s > 480 px" % (w, a.get("height")))
        # accessibility
        alt = a.get("alt") or {}
        if kind in ("figure", "animation", "image", "clip") and not (alt.get("fr") and alt.get("en")):
            errors.append("%s: texte alternatif alt.fr et alt.en obligatoires" % w)
        if kind in ("audio", "clip") and not a.get("caption"):
            errors.append("%s: légende / sous-titres (caption) obligatoires" % w)
        if kind == "audio" and not a.get("transcript"):
            errors.append("%s: transcription obligatoire" % w)
        if kind == "animation":
            if not a.get("reducedMotion"):
                errors.append("%s: repli statique « reducedMotion » obligatoire (mouvement réduit)" % w)
            elif a["reducedMotion"] != "first-frame" and not os.path.isfile(os.path.join(content, a["reducedMotion"])):
                errors.append("%s: reducedMotion pointe vers un fichier absent" % w)
        if kind in ("animation", "clip"):
            hz = a.get("flashHz")
            if not isinstance(hz, (int, float)):
                errors.append("%s: flashHz (clignotements par seconde) à déclarer" % w)
            elif hz > MAX_FLASH_HZ:
                errors.append("%s: %s clignotements/s > %d (seuil de sécurité)" % (w, hz, MAX_FLASH_HZ))
        # provenance
        origin = a.get("origin")
        if origin not in ORIGINS:
            errors.append("%s: origine « %s » refusée (original, generated ou cc0 seulement)" % (w, origin))
        elif a.get("licence") != LICENCES[origin]:
            errors.append("%s: licence %s attendue pour l'origine %s" % (w, LICENCES[origin], origin))
        if origin == "cc0" and not a.get("sourceUrl"):
            errors.append("%s: sourceUrl obligatoire pour un média cc0" % w)
        if origin == "generated" and not a.get("generator"):
            errors.append("%s: generator (script) obligatoire pour un média généré" % w)
        if not a.get("author"):
            errors.append("%s: agent auteur manquant" % w)
        if a.get("depictsPerson"):
            errors.append("%s: photo de personne identifiable interdite" % w)
        # lot
        feat, _, sc = (a.get("lot") or "").partition(":")
        if feat not in ("learn", "quiz") or not sc:
            errors.append("%s: lot « %s » invalide (feature:scope)" % (w, a.get("lot")))
        elif scopes:
            s = scopes.get(sc)
            if s is None:
                errors.append("%s: scope de lot %s inconnu" % (w, sc))
            elif kind in ("image", "audio", "clip") and s.get("kind") != "media":
                errors.append("%s: %s seulement dans un lot média (…-media, téléphone), pas dans %s" % (w, kind, sc))
            elif s.get("kind") == "media" and kind in ("figure", "animation"):
                pass
        for lid in a.get("lessons", []):
            if lessons and lid not in lessons:
                errors.append("%s: leçon %s inconnue" % (w, lid))
    declared = {a.get("path") for a in mm.get("assets", [])}
    ids = {a.get("id") for a in mm.get("assets", [])}
    for owner, m in refs:
        if m not in ids:
            errors.append("%s référence le média %s absent du manifeste" % (owner, m))
    # media files that nobody declared
    for root, _, files in os.walk(content):
        for fn in files:
            p = os.path.join(root, fn)
            rel = os.path.relpath(p, content).replace(os.sep, "/")
            k = L.media_kind(p)
            if k in ("image", "audio", "clip") and rel not in declared:
                errors.append("%s: média non déclaré dans MEDIA-MANIFEST.json" % rel)
    return errors


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--content", default=L.CONTENT)
    a = ap.parse_args(argv)
    errs = check(a.content)
    for e in errs:
        print("ERREUR: " + e, file=sys.stderr)
    n = len(L.media_manifest(a.content).get("assets", []))
    print("Manifeste de médias OK (%d média(s))." % n if not errs else "%d erreur(s) de média." % len(errs))
    return 1 if errs else 0


if __name__ == "__main__":
    sys.exit(main())
