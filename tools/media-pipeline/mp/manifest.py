"""Manifeste de provenance par média (docs/MEDIA-PIPELINE.md § 5) : champs ADDITIFS à `MEDIA-MANIFEST.json` (docs/MEDIA-POLICY.md). Aucun secret."""
import datetime

from .common import STATUT, scan_secrets, sha256_file  # noqa: F401
from . import registries as reg

FAMILIES = {"libre": "CC-BY-SA-4.0", "reserve": "CastBridge-original"}
REQUIRED = ("id", "path", "kind", "origin", "generator", "licence", "author", "sha256", "bytes", "lot", "synthetic", "statut", "engine", "requestFingerprint",
            "producedAt", "family", "licenceStatus")


def entry(request, file_path, producer, measures, family, lot_feature="langues-media", now=None):
    """Entrée de manifeste d'un média produit. [producer] = {agent, engineId, voiceId (audio)} ; le moteur et la voix sont résolus par les registres (plus tard)."""
    if family not in FAMILIES:
        raise ValueError("famille de lot inconnue : %s (libre | reserve)" % family)
    e = {
        "id": request["id"], "path": request["outputPath"], "kind": request["kind"], "origin": "generated", "generator": "google-media-pipeline",
        "licence": FAMILIES[family], "licenceStatus": "à valider par un juriste (docs/MEDIA-PIPELINE.md § 4)", "family": family,
        "author": producer.get("agent", "agent-media"), "sha256": sha256_file(file_path), "bytes": measures.get("bytes"),
        "lot": "%s:%s" % (lot_feature, request["scope"]), "synthetic": True, "statut": STATUT,
        "engine": {"id": producer.get("engineId")}, "requestFingerprint": request["fingerprint"],
        "producedAt": (now or datetime.date.today()).isoformat(), "lessons": [request["source"]["unit"]] if request.get("source") else [],
    }
    if request["kind"] == "audio":
        e["voiceClass"] = request["voiceClass"]
        e["voiceId"] = producer.get("voiceId")
        e["durationS"] = measures.get("durationS")
        e["codec"] = measures.get("codec")
        e["transcript"] = request["payload"].get("text") or ""
        e["caption"] = e["transcript"]
        e["clonage_vocal"] = False
        e["imitation_personne_reelle"] = False
    else:
        e["alt"] = {"fr": request["payload"].get("description", "")}
    if request["kind"] == "video":
        e["durationS"] = measures.get("durationS")
        e["height"] = measures.get("height")
        e["flashHz"] = 0
    return e


def validate(e, request, engines, voices):
    """Problèmes d'une entrée de manifeste vis-à-vis de sa demande et des registres (liste ; vide = conforme)."""
    p = []
    for k in REQUIRED:
        if e.get(k) in (None, ""):
            p.append("champ obligatoire absent : %s" % k)
    if e.get("synthetic") is not True:
        p.append("synthetic doit valoir true")
    if e.get("statut") != STATUT:
        p.append("statut doit valoir « %s »" % STATUT)
    if e.get("requestFingerprint") != request["fingerprint"]:
        p.append("empreinte de demande différente de la demande listée")
    if e.get("family") not in FAMILIES or e.get("licence") != FAMILIES.get(e.get("family")):
        p.append("licence incohérente avec la famille de lot (une famille ne se mélange pas à l'autre)")
    if request["kind"] == "audio":
        v = next((x for x in voices.get("voices", []) if x.get("id") == e.get("voiceId")), None)
        want, why = reg.voice_for(request["voiceClass"], engines, voices)
        if want is None:
            p.append("voix : " + why)
        elif v is None or v["id"] != want["id"]:
            p.append("voix utilisée (%s) différente de la voix choisie par le propriétaire pour cette classe" % e.get("voiceId"))
        elif (e.get("engine") or {}).get("id") != v.get("engineId"):
            p.append("moteur déclaré différent du moteur de la voix dans le registre")
        if e.get("clonage_vocal") is not False or e.get("imitation_personne_reelle") is not False:
            p.append("clonage_vocal et imitation_personne_reelle doivent valoir false")
    return p
