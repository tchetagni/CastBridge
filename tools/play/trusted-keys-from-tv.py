#!/usr/bin/env python3
"""Valeur de CASTBRIDGE_PLAY_TRUSTED_KEYS = la liste des clés de confiance des builds TV (mêmes clés, mêmes portées) + la clé du serveur (audit Opus H-5).

Le service castbridge-play doit accepter exactement les activations que la TV accepte : les TV activées par l'outil de bureau, les TV « super » (SUPER_UNLIMITED) et « tout ouvert »
(COMMAND_OPEN_ALL) échouent sinon en PLAY_SCOPE_FORBIDDEN alors que la tuile dit « disponible ».

Entrée : le fichier des clés de confiance des builds TV (par défaut ~/.castbridge-signing/activation-trusted-keys.txt ; android/receiver/build.gradle.kts, `trustedKeysFile`).
Seules les lignes PUBLIQUES « kid=… pub=… scopes=A,B » sont lues ; toute autre ligne est ignorée sans être affichée ni conservée (aucun secret n'est jamais imprimé, une ligne qui ressemble à un
secret est écartée avec un avertissement SANS son contenu). Sortie : une seule ligne `nom:clé:PORTÉE+PORTÉE,nom:clé:…` (format de TrustedIssuers côté service), sur la sortie standard.

  tools/play/trusted-keys-from-tv.py [--file FICHIER] [--server-pub CLÉ_PUBLIQUE_DU_SERVEUR_BASE64]

Une ligne sans scopes= n'accorde AUCUNE portée à la TV : elle est écartée (avertissement). Deux lignes de même clé publique fusionnent leurs portées (union), comme l'anneau de la TV.
La clé du serveur (lue sur GET /api/v1/admin/licenses/signing, champ publicKey) reçoit REVOKE+ISSUE_TRIAL+ISSUE_PRODUCTION.
"""
import argparse
import base64
import binascii
import os
import re
import sys

SERVER_SCOPES = ("REVOKE", "ISSUE_TRIAL", "ISSUE_PRODUCTION")
DEFAULT_FILE = os.path.join(os.path.expanduser("~"), ".castbridge-signing", "activation-trusted-keys.txt")
FIELD = re.compile(r"^(kid|pub|scopes)=(\S+)$")
SCOPE_NAME = re.compile(r"^[A-Z][A-Z_]{1,40}$")
SECRETISH = re.compile(r"(priv|seed|secret|token|password)", re.IGNORECASE)


def _valid_pub(pub):
    try:
        return len(base64.b64decode(pub, validate=True)) == 32
    except (binascii.Error, ValueError):
        return False


def parse(lines, warn=lambda m: sys.stderr.write(m + "\n")):
    """Rend [(nom, clé publique, [portées])] dans l'ordre du fichier ; une clé répétée fusionne ses portées."""
    out = {}
    order = []
    for n, raw in enumerate(lines, 1):
        line = raw.strip()
        if not line.startswith("kid=") or " pub=" not in line:
            continue                                   # tout ce qui n'est pas une ligne de clé publique est ignoré, sans être lu plus loin
        tokens = line.split()
        if any(SECRETISH.search(t.split("=", 1)[0]) for t in tokens if "=" in t):
            warn(f"ligne {n} écartée : un champ ressemble à un secret (contenu non affiché)")
            continue
        f = {}
        for t in tokens:
            m = FIELD.match(t)
            if m:
                f[m.group(1)] = m.group(2)
        kid, pub, scopes = f.get("kid"), f.get("pub"), f.get("scopes")
        if not kid or not pub or not _valid_pub(pub):
            warn(f"ligne {n} écartée : kid ou clé publique illisible (32 octets en Base64 attendus)")
            continue
        names = [s for s in (scopes or "").split(",") if s]
        if not names:
            warn(f"clé « {kid[:24]} » sans scopes= : aucune portée, écartée (la TV ne l'accepterait pour rien)")
            continue
        if not all(SCOPE_NAME.match(s) for s in names):
            warn(f"clé « {kid[:24]} » écartée : portée mal formée")
            continue
        if pub not in out:
            out[pub] = [re.sub(r"[^A-Za-z0-9._-]", "_", kid)[:32], []]
            order.append(pub)
        for s in names:
            if s not in out[pub][1]:
                out[pub][1].append(s)
    return [(out[p][0], p, out[p][1]) for p in order]


def env_value(keys, server_pub=None, warn=lambda m: sys.stderr.write(m + "\n")):
    entries = {pub: (name, list(scopes)) for name, pub, scopes in keys}
    ordered = [pub for _, pub, _ in keys]
    if server_pub:
        if not _valid_pub(server_pub):
            raise SystemExit("--server-pub : 32 octets en Base64 attendus (champ publicKey de GET /api/v1/admin/licenses/signing)")
        if server_pub not in entries:
            entries[server_pub] = ("server", [])
            ordered.append(server_pub)
        for s in SERVER_SCOPES:
            if s not in entries[server_pub][1]:
                entries[server_pub][1].append(s)
    else:
        warn("ATTENTION : pas de --server-pub : sans la clé du serveur (portée REVOKE), la liste de révocations signée par le serveur serait refusée")
    return ",".join(f"{entries[p][0]}:{p}:{'+'.join(entries[p][1])}" for p in ordered)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--file", default=DEFAULT_FILE)
    ap.add_argument("--server-pub", default=None)
    a = ap.parse_args(argv)
    try:
        with open(a.file, encoding="utf-8") as fh:
            lines = fh.read().splitlines()
    except OSError as e:
        raise SystemExit(f"fichier illisible : {a.file} ({e.__class__.__name__})")
    keys = parse(lines)
    if not keys:
        raise SystemExit("aucune clé publique de confiance trouvée : la liste du service serait vide (aucune TV ne pourrait jouer)")
    print(env_value(keys, a.server_pub))


if __name__ == "__main__":
    main()
