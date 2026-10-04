#!/usr/bin/env python3
"""Hors ligne : vérifie un export du suivi des activations (docs/ACTIVATION-TRACKING.md).

Ce que l'outil contrôle, sans le serveur ni sa base :
  * la CHAÎNE de l'historique (export JSONL « what=events ») : chaque ligne porte hash(ligne précédente | ses champs) ; une ligne modifiée, retirée, ajoutée ou
    déplacée est désignée par son numéro ; les identifiants se suivent sans trou ;
  * la chaîne du journal d'audit des lectures (export « what=reads »), si elle est donnée ;
  * les POINTS DE CONTRÔLE signés (GET /checkpoints) : la signature (Ed25519 avec la clé publique, ou HMAC avec le fichier act-audit.key) et la tête de chaîne qu'ils
    annoncent, comparée à la ligne correspondante de l'export.

Le hash est HMAC-SHA-256 avec la clé SHA-256(contenu du fichier act-audit.key) quand ce fichier existe sur le serveur (donner --audit-key-file), sinon SHA-256 simple.
Une chaîne recalculée par quelqu'un qui n'a pas la clé ne se vérifie donc pas ici.

Sortie : « chaîne intacte jusqu'au <id> » et code 0, ou la première anomalie (« ligne n … ») et code 1. Un export partiel (après une archive froide) se vérifie avec
--anchor-id et --anchor-hash (la dernière ligne archivée, dans act_archive.last_hash).

Dépendance facultative : « cryptography » pour la signature Ed25519 des points de contrôle (sinon cette vérification est annoncée comme non faite, code 2).
"""
import argparse
import base64
import hashlib
import hmac
import json
import sys

GENESIS = "0" * 64
FORMAT = "castbridge-act-checkpoint-v1"


def nz(v):
    return "" if v is None else str(v)


def digest(key, parts):
    data = "\x1f".join(parts).encode("utf-8")
    return (hmac.new(key, data, hashlib.sha256) if key else hashlib.sha256(data)).hexdigest()


def event_parts(prev, e):
    return [prev, str(e["atMs"]), str(e["recordedMs"]), e["type"], nz(e["fp"]), nz(e["tvRef"]), nz(e["licenseId"]), nz(e["kid"]), e["actorType"], nz(e["actor"]), e["source"],
            nz(e["before"]), nz(e["after"]), e["idemKey"]]


def read_parts(prev, e):
    return [prev, str(e["atMs"]), nz(e["actor"]), e["role"], e["channel"], e["route"], nz(e["params"]), nz(e["target"]), str(e["rows"]), "1" if e["export"] else "0"]


def verify_chain(path, parts, key, anchor_id, anchor_hash, partial):
    """Returns (last id, count, {id: hash}) or raises ValueError with the first anomaly."""
    prev, last, count, hashes = anchor_hash, anchor_id, 0, {}
    with open(path, encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            if not line.strip():
                continue
            e = json.loads(line)
            if count == 0 and not partial and anchor_id == 0 and e["prevHash"] != GENESIS:
                raise ValueError("ligne %d (id %s) : la chaîne ne commence pas au début (export partiel ? voir --anchor-id)" % (n, e["id"]))
            if e["prevHash"] != prev:
                raise ValueError("ligne %d (id %s) : chaîne rompue (ligne retirée, ajoutée ou déplacée)" % (n, e["id"]))
            if e["id"] != last + 1 and not (count == 0 and partial):
                raise ValueError("ligne %d (id %s) : les identifiants ne se suivent pas (après %d)" % (n, e["id"], last))
            if digest(key, parts(prev, e)) != e["hash"]:
                raise ValueError("ligne %d (id %s) : ligne modifiée (ou clé de chaîne différente : --audit-key-file)" % (n, e["id"]))
            prev, last, count = e["hash"], e["id"], count + 1
            hashes[e["id"]] = e["hash"]
    return last, count, hashes


def kid_of(public_key_raw):
    return hashlib.sha256(public_key_raw).hexdigest()[:16]


def verify_checkpoints(path, key, public_key_b64, event_hashes, first_event_id):
    """Returns (problems, not_done)."""
    problems, not_done = [], []
    with open(path, encoding="utf-8") as f:
        doc = json.load(f)
    items = doc["items"] if isinstance(doc, dict) else doc
    for c in items:
        payload = "|".join([FORMAT, c["day"], str(c["eventLastId"]), c["eventHead"], str(c["readLastId"]), c["readHead"], c["countsJson"]])
        kid, sig = c["sigKid"], c["signature"]
        if kid == "sha256":
            ok = hashlib.sha256(payload.encode()).hexdigest() == sig
        elif kid == "hmac":
            if key is None:
                not_done.append("point de contrôle %s : signature HMAC, il faut --audit-key-file" % c["day"])
                ok = True
            else:
                ok = hmac.new(key, payload.encode(), hashlib.sha256).hexdigest() == sig
        else:
            if public_key_b64 is None:
                not_done.append("point de contrôle %s : signature Ed25519, il faut --public-key" % c["day"])
                ok = True
            else:
                raw = base64.b64decode(public_key_b64)
                if kid_of(raw) != kid:
                    problems.append("point de contrôle %s : signé par la clé %s, pas par celle donnée (%s)" % (c["day"], kid, kid_of(raw)))
                    continue
                try:
                    from cryptography.exceptions import InvalidSignature
                    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
                except ImportError:
                    not_done.append("point de contrôle %s : module « cryptography » absent, signature non vérifiée" % c["day"])
                    ok = True
                else:
                    try:
                        Ed25519PublicKey.from_public_bytes(raw).verify(base64.b64decode(sig), payload.encode())
                        ok = True
                    except InvalidSignature:
                        ok = False
        if not ok:
            problems.append("point de contrôle %s : signature fausse" % c["day"])
            continue
        n = c["eventLastId"]
        if n and event_hashes is not None and n >= first_event_id:
            if n not in event_hashes:
                not_done.append("point de contrôle %s : la ligne %d n'est pas dans l'export (export plus court)" % (c["day"], n))
            elif event_hashes[n] != c["eventHead"]:
                problems.append("point de contrôle %s : la tête annoncée (ligne %d) ne correspond pas à l'historique exporté : réécriture" % (c["day"], n))
    return problems, not_done


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--events", required=True, help="export JSONL de l'historique (what=events)")
    ap.add_argument("--reads", help="export JSONL des lectures (what=reads)")
    ap.add_argument("--checkpoints", help="réponse de GET /checkpoints")
    ap.add_argument("--audit-key-file", help="le fichier act-audit.key du serveur (clé HMAC des chaînes)")
    ap.add_argument("--public-key", help="clé publique Ed25519 des points de contrôle (base64, 32 octets)")
    ap.add_argument("--anchor-id", type=int, default=0, help="export partiel : id de la dernière ligne archivée")
    ap.add_argument("--anchor-hash", default=GENESIS, help="export partiel : son hash (act_archive.last_hash)")
    a = ap.parse_args(argv)
    key = None
    if a.audit_key_file:
        with open(a.audit_key_file, "rb") as f:
            key = hashlib.sha256(f.read()).digest()
    partial = a.anchor_id > 0
    try:
        last, count, hashes = verify_chain(a.events, event_parts, key, a.anchor_id, a.anchor_hash, partial)
        print("chaîne intacte jusqu'au %d (%d lignes)" % (last, count))
        if a.reads:
            rl, rc, _ = verify_chain(a.reads, read_parts, key, 0, GENESIS, False)
            print("lectures : chaîne intacte jusqu'au %d (%d lignes)" % (rl, rc))
    except (ValueError, KeyError) as e:
        print("ANOMALIE : %s" % (e if isinstance(e, ValueError) else "champ manquant " + str(e)))
        return 1
    not_done = []
    if a.checkpoints:
        first = min(hashes) if hashes else 0
        problems, not_done = verify_checkpoints(a.checkpoints, key, a.public_key, hashes, first)
        for p in problems:
            print("ANOMALIE : " + p)
        if problems:
            return 1
        print("points de contrôle vérifiés")
    for n in not_done:
        print("NON VÉRIFIÉ : " + n)
    return 2 if not_done else 0


if __name__ == "__main__":
    sys.exit(main())
