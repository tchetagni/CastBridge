"""Demandes de médias déterministes et idempotentes (docs/MEDIA-PIPELINE.md § 2).

Entrée : des paquets de langue `<dossier>/<scope>/langue.json` (format de docs/LANGUES.md § 3). Sortie : `media-requests.jsonl`, une ligne par média.
Règles : texte EXACT copié du paquet (jamais corrigé, jamais deviné) ; aucune voix nommée (seulement une CLASSE de voix, la voix concrète vient du registre
choisi par le propriétaire) ; une empreinte par demande : même demande = même fichier = jamais facturée deux fois ; un texte à dire impossible à
déterminer n'est PAS inventé : la demande est marquée bloquée.
"""
from pathlib import Path

from .common import KINDS, canonical, read_json, sha256_text, write_jsonl

LEVEL_RANK = {"A0": 0, "A1": 1, "A2": 2, "B1": 3, "B2": 4, "C1": 5, "C2": 6, "NATIF": 7}
ELEMENT_RANK = {"word": 0, "line": 1, "exercise": 2, "dialogue": 3, "story": 4, "card": 5, "clip": 6}
EXT = {"audio": "opus", "image": "webp", "video": "mp4"}

DEFAULT_POLICY = {
    "format": 1,
    "comment": "Politique de demandes. Valeurs par défaut modifiables par le propriétaire ; aucune ne désigne une voix.",
    "varieties": {"zh": "zh-CN", "ja": "ja-JP", "de": "de-DE", "it": "it-IT", "fr": "fr-FR", "en": None, "es": None},
    "varietiesComment": "null = à choisir par le propriétaire (en-GB / en-US, es-ES / es-LATAM) : la demande est alors marquée « variété à choisir »",
    "register": {"A0": "lent", "A1": "lent", "A2": "intermediaire"},
    "registerDefault": "naturel",
    "speakerGender": {"A": "f", "B": "m"},
    "speakerGenderDefault": "neutre",
    "ageBand": "adulte",
    "audio": {"wordMaxDurationS": 4, "wordBitrateKbps": 16, "phraseMaxDurationS": 20, "phraseBitrateKbps": 24, "dialogueMaxDurationS": 120, "sampleRateHz": 16000, "channels": 1, "codec": "opus", "container": "ogg", "targetLufs": -16},
    "image": {"maxBytes": 122880, "format": "webp", "preferVector": True},
    "video": {"maxDurationS": 30, "maxHeight": 480, "codec": "h264"},
    "imageTemplate": "Illustration simple et neutre de : {label}. Figure vectorielle de préférence ; aucun visage de personne réelle ni personne identifiable.",
    "videoTemplate": "Courte vidéo (30 s au plus, 480p au plus, sans personne réelle) illustrant : {label}.",
}


def _lang_code(target):
    return {"zh": "zh", "ja": "ja", "en": "en", "de": "de", "fr": "fr", "it": "it", "es": "es"}.get(target, target)


def _mid(ref):
    return ref[2:] if isinstance(ref, str) and ref.startswith("m:") else None


class Builder:
    def __init__(self, policy=None):
        self.policy = policy or DEFAULT_POLICY
        self.requests = {}
        self.conflicts = []

    def _voice_class(self, pack, role, kind_of):
        p = self.policy
        level = pack["level"]
        gender = p["speakerGender"].get(role, p["speakerGenderDefault"]) if role else p["speakerGenderDefault"]
        return {"ageBand": p["ageBand"], "gender": gender, "register": p["register"].get(level, p["registerDefault"]),
                "role": role or kind_of, "variety": p["varieties"].get(pack["target"])}

    def _add(self, pack, media_id, kind, element, source, payload, bitrate=None, max_s=None):
        level = pack["level"]
        level_rank = LEVEL_RANK.get(level, 9)
        tier = 0 if level_rank <= 2 else 1
        priority = tier * 100000 + KINDS.index(kind) * 10000 + level_rank * 100 + ELEMENT_RANK[element]
        scope = pack["id"]
        body = {"kind": kind, "lang": _lang_code(pack["target"]), "payload": payload}
        if kind == "audio":
            a = self.policy["audio"]
            body["constraints"] = {"codec": a["codec"], "container": a["container"], "channels": a["channels"], "sampleRateHz": a["sampleRateHz"],
                                   "bitrateKbps": bitrate, "maxDurationS": max_s, "targetLufs": a["targetLufs"]}
        elif kind == "image":
            body["constraints"] = {"format": self.policy["image"]["format"], "maxBytes": self.policy["image"]["maxBytes"], "preferVector": self.policy["image"]["preferVector"]}
        else:
            v = self.policy["video"]
            body["constraints"] = {"codec": v["codec"], "maxHeight": v["maxHeight"], "maxDurationS": v["maxDurationS"]}
        blocked = []
        voice = None
        if kind == "audio":
            voice = payload.pop("voiceClass")
            body["voiceClass"] = voice
            if payload.get("text") is None and not payload.get("segments"):
                blocked.append("texte à dire non déterminable à partir du paquet : à préciser par l'auteur du contenu")
            if payload.get("tooManyVoices"):
                blocked.append("plus de trois voix dans un dialogue : à revoir par l'auteur du contenu")
            if voice["variety"] is None:
                blocked.append("variété de la langue à choisir par le propriétaire")
        fingerprint = sha256_text(canonical(body))
        row = {
            "id": media_id, "kind": kind, "lang": body["lang"], "scope": scope, "level": level, "priority": priority,
            "outputPath": "media/%s/%s/%s.%s" % (scope, kind, media_id, EXT[kind]),
            "source": source, "constraints": body["constraints"], "fingerprint": fingerprint,
            "payload": payload, "statut": "bêta : non validé", "synthetic": True,
        }
        if voice is not None:
            row["voiceClass"] = voice
        if blocked:
            row["blocked"] = blocked
        old = self.requests.get(media_id)
        if old is None:
            self.requests[media_id] = row
        elif old["fingerprint"] != fingerprint:
            self.conflicts.append({"id": media_id, "message": "même identifiant média avec deux demandes différentes", "a": old["source"], "b": source})

    def pack(self, pack):
        a = self.policy["audio"]
        for u in pack.get("units", []):
            unit = u.get("id")
            for v in u.get("vocab", []):
                mid = _mid(v.get("audio"))
                src = {"pack": pack["id"], "unit": unit, "element": v.get("id"), "field": "vocab.term"}
                if mid:
                    self._add(pack, mid, "audio", "word", src, {"text": v.get("term"), "reading": v.get("reading"),
                                                                 "voiceClass": self._voice_class(pack, None, "mot")}, a["wordBitrateKbps"], a["wordMaxDurationS"])
                self._visual(pack, unit, v, v.get("id"), [v.get("gloss"), v.get("term")], "vocab")
            for d in u.get("dialogues", []):
                texts = []
                for i, line in enumerate(d.get("lines", [])):
                    texts.append((line.get("who"), line.get("text")))
                    mid = _mid(line.get("audio"))
                    if mid:
                        self._add(pack, mid, "audio", "line", {"pack": pack["id"], "unit": unit, "element": d.get("id"), "field": "lines[%d].text" % i},
                                  {"text": line.get("text"), "reading": line.get("reading"), "voiceClass": self._voice_class(pack, line.get("who"), "locuteur")},
                                  a["phraseBitrateKbps"], a["phraseMaxDurationS"])
                mid = _mid(d.get("audio"))
                if mid:
                    roles = sorted({w for w, _ in texts if w})
                    segs = [{"role": w, "text": t, "voiceClass": self._voice_class(pack, w, "locuteur")} for w, t in texts]
                    row_payload = {"segments": segs, "text": "\n".join(t or "" for _, t in texts), "voiceClass": self._voice_class(pack, None, "dialogue")}
                    if len(roles) > 3:
                        row_payload["tooManyVoices"] = True
                    self._add(pack, mid, "audio", "dialogue", {"pack": pack["id"], "unit": unit, "element": d.get("id"), "field": "lines"},
                              row_payload, a["phraseBitrateKbps"], a["dialogueMaxDurationS"])
                self._visual(pack, unit, d, d.get("id"), [d.get("title")] + [t for _, t in texts[:2]], "dialogue")
            for x in u.get("exercises", []):
                mid = _mid(x.get("audio"))
                if mid:
                    answers = x.get("answers") or []
                    text = answers[0] if (x.get("kind") == "dictation" and answers) else None
                    self._add(pack, mid, "audio", "exercise", {"pack": pack["id"], "unit": unit, "element": x.get("id"), "field": "answers[0]"},
                              {"text": text, "voiceClass": self._voice_class(pack, None, "mot")}, a["phraseBitrateKbps"], a["phraseMaxDurationS"])
            for s in u.get("stories", []):
                for i, para in enumerate(s.get("paragraphs", [])):
                    mid = _mid(para.get("audio"))
                    if mid:
                        self._add(pack, mid, "audio", "story", {"pack": pack["id"], "unit": unit, "element": s.get("id"), "field": "paragraphs[%d].text" % i},
                                  {"text": para.get("text"), "reading": para.get("reading"), "voiceClass": self._voice_class(pack, para.get("who"), "narrateur")},
                                  a["phraseBitrateKbps"], a["dialogueMaxDurationS"])
            for c in u.get("cards", []):
                self._visual(pack, unit, c, c.get("id"), [c.get("label"), c.get("vocab")], "card")

    def _visual(self, pack, unit, item, element, labels, element_kind):
        label = next((l for l in labels if l), element)
        for key in ("image", "video"):
            mid = _mid(item.get(key))
            if not mid:
                continue
            kind = "image" if key == "image" else "video"
            tpl = self.policy["imageTemplate" if kind == "image" else "videoTemplate"]
            self._add(pack, mid, kind, "card" if kind == "image" else "clip", {"pack": pack["id"], "unit": unit, "element": element, "field": key},
                      {"description": tpl.format(label=label), "label": label})


def load_packs(root):
    root = Path(root)
    packs = []
    for f in sorted(root.glob("*/langue.json")):
        p = read_json(f)
        if p.get("type") == "langue":
            packs.append(p)
    return packs


def build(root, policy=None):
    b = Builder(policy)
    for p in load_packs(root):
        b.pack(p)
    rows = sorted(b.requests.values(), key=lambda r: (r["priority"], r["id"]))
    return rows, b.conflicts


def run(root, out, policy=None):
    rows, conflicts = build(root, policy)
    write_jsonl(out, rows)
    return rows, conflicts
