"""Registres des moteurs et des voix (docs/MEDIA-PIPELINE.md § 4). Le propriétaire choisit ; l'outil ne choisit JAMAIS une voix à sa place.

Une voix n'est utilisable que si : elle est dans voices-registry.json avec le statut exactement « approved », son moteur est dans engines-registry.json avec
le statut « approved », c'est une voix de catalogue préconstruite (pas de clonage ni de voix personnalisée), et ses conditions ont une date de vérification et une preuve.
"""
from .common import read_json

APPROVED = "approved"
VOICE_TYPE = "voix_de_catalogue_preconstruite"
ENGINE_FIELDS = ("id", "provider", "service", "model", "version", "allowedUse", "termsUrl", "termsCheckedOn", "termsEvidence", "syntheticMarkingRequired", "status")
VOICE_FIELDS = ("id", "apiVoiceId", "engineId", "lang", "variety", "voiceClass", "type", "status")
FORBIDDEN_LICENCES = ("NC", "ND")


def class_key(voice_class):
    """Clé de classe de voix (sans le rôle de scène) : variété|genre|âge|registre."""
    return "|".join(str(voice_class.get(k)) for k in ("variety", "gender", "ageBand", "register"))


def load(engines_path, voices_path):
    return read_json(engines_path), read_json(voices_path)


def _engine(engines, engine_id):
    return next((e for e in engines.get("engines", []) if e.get("id") == engine_id), None)


def validate_registries(engines, voices):
    """Problèmes de forme et d'approbation des deux registres (liste de textes ; vide = cohérents)."""
    problems = []
    ids = set()
    for e in engines.get("engines", []):
        miss = [f for f in ENGINE_FIELDS if e.get(f) in (None, "")]
        if e.get("status") == APPROVED and miss:
            problems.append("moteur %s « approved » avec champs manquants : %s" % (e.get("id"), ", ".join(miss)))
        if e.get("id") in ids:
            problems.append("moteur en double : %s" % e.get("id"))
        ids.add(e.get("id"))
        text = " ".join(str(e.get(k, "")) for k in ("allowedUse", "licence"))
        if any(("-" + f) in text.upper() or (" " + f + " ") in (" " + text.upper() + " ") for f in FORBIDDEN_LICENCES):
            problems.append("moteur %s : licence NC/ND interdite" % e.get("id"))
    vids = set()
    for v in voices.get("voices", []):
        miss = [f for f in VOICE_FIELDS if v.get(f) in (None, "")]
        if v.get("status") == APPROVED:
            if miss:
                problems.append("voix %s « approved » avec champs manquants : %s" % (v.get("id"), ", ".join(miss)))
            if v.get("type") != VOICE_TYPE:
                problems.append("voix %s : seules les voix de catalogue préconstruites sont admises (type = %s)" % (v.get("id"), v.get("type")))
            eng = _engine(engines, v.get("engineId"))
            if eng is None or eng.get("status") != APPROVED:
                problems.append("voix %s : moteur %s absent ou non approuvé" % (v.get("id"), v.get("engineId")))
            if v.get("cloned") or v.get("customVoice") or v.get("referenceAudio") or v.get("imitatesRealPerson"):
                problems.append("voix %s : clonage, voix personnalisée, audio de référence ou imitation interdits" % v.get("id"))
        if v.get("id") in vids:
            problems.append("voix en double : %s" % v.get("id"))
        vids.add(v.get("id"))
    for k, vid in voices.get("mapping", {}).items():
        if vid is not None and vid not in vids:
            problems.append("classe %s associée à une voix inconnue : %s" % (k, vid))
    return problems


def voice_for(voice_class, engines, voices):
    """(voix, motif) : la voix approuvée choisie par le propriétaire pour cette classe, ou (None, raison)."""
    vid = voices.get("mapping", {}).get(class_key(voice_class))
    if vid is None:
        return None, "aucune voix choisie par le propriétaire pour la classe %s" % class_key(voice_class)
    v = next((x for x in voices.get("voices", []) if x.get("id") == vid), None)
    if v is None or v.get("status") != APPROVED:
        return None, "voix %s absente ou non « approved »" % vid
    eng = _engine(engines, v.get("engineId"))
    if eng is None or eng.get("status") != APPROVED:
        return None, "moteur de la voix %s absent ou non « approved »" % vid
    return v, None


def classes_needed(requests):
    """Classes de voix à faire choisir au propriétaire (liste triée) : le point de départ de voices-registry.json « mapping »."""
    return sorted({class_key(r["voiceClass"]) for r in requests if r["kind"] == "audio" and not r.get("blocked")})


def readiness(requests, engines, voices):
    """Pour chaque demande audio : prête ou non à être produite (voix choisie et approuvée). Retourne {id: raison|None}."""
    out = {}
    for r in requests:
        if r["kind"] != "audio":
            continue
        _, why = voice_for(r["voiceClass"], engines, voices)
        out[r["id"]] = why
    return out
