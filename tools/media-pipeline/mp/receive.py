"""Réception d'un lot de médias produit par l'exécutant (docs/MEDIA-PIPELINE.md § 6) : tout est contrôlé sans Google.

Entrées : demandes (`media-requests.jsonl`), dossier de travail (les fichiers à leur `outputPath`), `produced.json` (liste d'entrées {id, fingerprint, producer}),
registres, résultats ASR (optionnels). Sorties : `media.json` (acceptés SEULEMENT), `rejets.json`, `attente.json` (ASR manquant : ni accepté ni rejeté), `controle.json`, rapport lisible.
Règles : un fichier non listé dans les demandes est rejeté ; un écart ASR est un rejet ; pas de contrôle ASR = pas d'acceptation ; secrets trouvés = lot bloqué.
"""
from pathlib import Path

from . import asr, checks, manifest
from .common import read_json, read_jsonl, scan_secrets, write_json


def run(requests_path, workdir, produced_path, engines, voices, asr_path=None, family="reserve", out_dir=None, limits=None, lot_before=0, phone_before=0):
    requests = {r["id"]: r for r in read_jsonl(requests_path)}
    workdir = Path(workdir)
    produced = read_json(produced_path) if Path(produced_path).is_file() else []
    asr_results = {r["id"]: r for r in read_jsonl(asr_path)} if asr_path and Path(asr_path).is_file() else {}
    accepted, rejected, waiting, control = [], [], [], {}
    secrets = scan_secrets(workdir)
    sizes = []
    seen = set()
    for p in produced:
        rid = p.get("id")
        req = requests.get(rid)
        if req is None:
            rejected.append({"id": rid, "fichier": p.get("file"), "motif": "média non listé dans les demandes", "action": "ne pas produire ce qui n'est pas demandé"})
            continue
        seen.add(rid)
        f = workdir / req["outputPath"]
        reasons = []
        if req.get("blocked"):
            reasons.append("demande bloquée : " + "; ".join(req["blocked"]))
        if p.get("fingerprint") != req["fingerprint"]:
            reasons.append("empreinte de la demande différente (demande modifiée depuis ?)")
        if not f.is_file():
            reasons.append("fichier absent : %s" % req["outputPath"])
            rejected.append({"id": rid, "fichier": req["outputPath"], "motif": "; ".join(reasons), "action": "reproduire"})
            continue
        problems, measures = checks.check_file(f, req, limits)
        reasons += problems
        entry = manifest.entry(req, f, p.get("producer", {}), measures, family)
        reasons += manifest.validate(entry, req, engines, voices)
        control[rid] = {"mesures": measures, "problemes": reasons[:]}
        status = "accepte"
        if req["kind"] == "audio":
            status, why = asr.compare(req, asr_results.get(rid))
            control[rid]["asr"] = status
            if status == "rejete":
                reasons.append(why)
            elif status == "non_verifiable" and not reasons:
                waiting.append({"id": rid, "fichier": req["outputPath"], "motif": asr.NOT_VERIFIABLE, "bloquant": True})
                continue
        if reasons:
            rejected.append({"id": rid, "fichier": req["outputPath"], "motif": "; ".join(reasons), "action": "voir le motif ; reproduire ou faire arbitrer par le propriétaire"})
        else:
            accepted.append(entry)
            sizes.append(measures.get("bytes") or f.stat().st_size)
    for rid, req in requests.items():
        if rid not in seen and not any(r["id"] == rid for r in rejected):
            pass
    extra = []
    listed = {r["outputPath"] for r in requests.values()}
    for f in sorted(workdir.rglob("*")):
        if f.is_file() and f.suffix in (".opus", ".webp", ".mp4") and f.relative_to(workdir).as_posix() not in listed:
            extra.append(f.relative_to(workdir).as_posix())
    for rel in extra:
        rejected.append({"id": None, "fichier": rel, "motif": "fichier média non listé dans les demandes", "action": "le supprimer du lot de travail"})
    totals = checks.check_totals(sizes, lot_before, phone_before, limits)
    blocking = []
    if secrets:
        blocking.append("secret potentiel dans le lot : %s" % "; ".join("%s (%s)" % s for s in secrets))
    blocking += totals
    result = {"acceptes": len(accepted), "rejetes": len(rejected), "en_attente_asr": len(waiting), "bloquants": blocking, "octets_acceptes": sum(sizes)}
    if out_dir:
        out = Path(out_dir)
        write_json(out / "media.json", {"format": 1, "statut": "bêta : non validé", "media": accepted})
        write_json(out / "rejets.json", {"format": 1, "rejets": rejected})
        write_json(out / "attente.json", {"format": 1, "attente": waiting})
        write_json(out / "controle.json", {"format": 1, "resume": result, "controles": control})
        (out / "rapport-reception.md").write_text(report(result, rejected, waiting, blocking), encoding="utf-8")
    return result, accepted, rejected, waiting


def report(result, rejected, waiting, blocking):
    lines = ["# Rapport de réception", "", "Statut : bêta : non validé", "",
             "- Acceptés : %d" % result["acceptes"], "- Rejetés : %d" % result["rejetes"], "- En attente du contrôle ASR : %d" % result["en_attente_asr"],
             "- Octets acceptés : %d" % result["octets_acceptes"], ""]
    if blocking:
        lines += ["## BLOQUANT", ""] + ["- " + b for b in blocking] + [""]
    if rejected:
        lines += ["## Rejets", "", "| ID | Fichier | Motif |", "|---|---|---|"] + ["| %s | %s | %s |" % (r["id"], r["fichier"], r["motif"].replace("|", "/")) for r in rejected] + [""]
    if waiting:
        lines += ["## En attente (ni acceptés ni rejetés)", ""] + ["- %s : %s" % (w["id"], w["motif"]) for w in waiting] + [""]
    lines += ["Rappel : aucune validation humaine n'est simulée ; tons du mandarin, accent japonais et prosodie restent à faire vérifier par un locuteur natif.", ""]
    return "\n".join(lines)
