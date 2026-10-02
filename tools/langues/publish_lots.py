#!/usr/bin/env python3
"""Publie les lots « Langues » (content/langues) sur le serveur CastBridge (docs/LANGUES.md § « Publier les lots Langues »).

  publish_lots.py [--lots-dir DIR] [--server https://bridge.sti-cm.com] [--apply] [--channel stable]

Par défaut : SIMULATION (dry-run). Rien n'est envoyé ; le script construit les lots (gradle :core:buildLangLots, hors réseau, ou relit
--lots-dir déjà construit), vérifie chaque fichier (taille <= 3 Mo, SHA-256 du catalogue, famille libre) et affiche le résumé.

Avec --apply : pour chaque lot, POST /api/v1/admin/lots (multipart, publish=false, sha256 attendu) puis POST /api/v1/admin/lots/{id}/publish.
Le jeton d'administration est lu dans la variable d'environnement CASTBRIDGE_ADMIN_TOKEN (jamais en argument, jamais affiché).
Idempotent : un lot déjà publié avec la même empreinte est sauté ; déjà envoyé mais non publié -> seulement publié ; même version avec une
empreinte différente -> erreur (une version ne change jamais : relancer la construction avec -Pupdate pour passer à la suivante).
Seuls les lots de famille « free » (CC BY-SA, jamais scellés ni loués) sont publiés ; un lot « reserved » est refusé.
"""
import argparse, hashlib, json, os, subprocess, sys, urllib.error, urllib.request, uuid

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
MAX_LOT_BYTES = 3 << 20          # castbridge.core.langues.LangLotConsumer.MAX_TEXT_LOT_BYTES (= server LangLotValidator.MAX_BYTES)
DEFAULT_SERVER = "https://bridge.sti-cm.com"
TOKEN_ENV = "CASTBRIDGE_ADMIN_TOKEN"
FEATURE = "langues"


class Fail(Exception):
    pass


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def build_lots(out_dir=None, gradle=None):
    """Runs the repo's builder (no network: --offline) and returns the output directory."""
    out = out_dir or os.path.join(REPO, "android", "core", "build", "langues-lots")
    cmd = (gradle or ["bash", os.path.join(REPO, "tools", "agents", "gradle-lock.sh"), "gradle"]) + ["--offline", ":core:buildLangLots"]
    r = subprocess.run(cmd, cwd=os.path.join(REPO, "android"))
    if r.returncode != 0:
        raise Fail("la construction des lots a échoué (code %d) : voir la sortie de gradle ci-dessus" % r.returncode)
    return out


def load_lots(lots_dir):
    """The lots announced by lots-catalog.json, each checked against its file: [{scope, version, title, bytes, sha256, family, path, minAppVersion}]."""
    cat = os.path.join(lots_dir, "lots-catalog.json")
    if not os.path.isfile(cat):
        raise Fail("lots-catalog.json introuvable dans %s (lancer gradle :core:buildLangLots)" % lots_dir)
    with open(cat, encoding="utf-8") as f:
        data = json.load(f)
    out, problems = [], []
    for e in data.get("lots", []):
        scope = e.get("scope", "?")
        if e.get("feature") != FEATURE:
            problems.append("%s : feature « %s » (seul « %s » est publié ici)" % (scope, e.get("feature"), FEATURE)); continue
        if e.get("family") != "free":
            problems.append("%s : famille « %s » : seuls les lots libres sont publiés (jamais un lot réservé)" % (scope, e.get("family"))); continue
        path = os.path.join(lots_dir, e.get("file", ""))
        if os.path.basename(path) != e.get("file") or not os.path.isfile(path):
            problems.append("%s : fichier %s absent" % (scope, e.get("file"))); continue
        size = os.path.getsize(path)
        if size != e["bytes"] or size > MAX_LOT_BYTES:
            problems.append("%s : taille %d octets (catalogue %s, maximum %d)" % (scope, size, e["bytes"], MAX_LOT_BYTES)); continue
        if sha256_file(path) != e["sha256"]:
            problems.append("%s : empreinte SHA-256 différente du catalogue (reconstruire)" % scope); continue
        out.append({"scope": scope, "version": e["version"], "title": e["title"], "bytes": size, "sha256": e["sha256"], "family": "free",
                    "path": path, "minAppVersion": e.get("minAppVersion", 0)})
    if problems:
        raise Fail("lots refusés avant tout envoi :\n  " + "\n  ".join(problems))
    if not out:
        raise Fail("aucun lot à publier")
    return sorted(out, key=lambda l: l["scope"])


def multipart(fields, file_field, file_name, data):
    boundary = "----cb" + uuid.uuid4().hex
    parts = []
    for k, v in fields.items():
        parts.append(('--%s\r\nContent-Disposition: form-data; name="%s"\r\n\r\n' % (boundary, k)).encode() + str(v).encode("utf-8") + b"\r\n")
    parts.append(('--%s\r\nContent-Disposition: form-data; name="%s"; filename="%s"\r\nContent-Type: application/octet-stream\r\n\r\n'
                  % (boundary, file_field, file_name)).encode() + data + b"\r\n")
    parts.append(("--%s--\r\n" % boundary).encode())
    return b"".join(parts), "multipart/form-data; boundary=" + boundary


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


def default_transport(method, url, headers, body):
    """(code, bytes). Never follows a redirect (the token must not leave the server host)."""
    req = urllib.request.Request(url, data=body, method=method, headers=headers)
    opener = urllib.request.build_opener(NoRedirect)
    try:
        with opener.open(req, timeout=120) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


class Admin:
    def __init__(self, server, token, transport=default_transport):
        if not server.startswith("https://") and not (server.startswith("http://127.0.0.1") or server.startswith("http://localhost")):
            raise Fail("le serveur doit être en HTTPS (le jeton d'administration ne circule jamais en clair)")
        self.base, self.token, self.transport = server.rstrip("/"), token, transport

    def call(self, method, path, body=None, content_type=None):
        h = {"Authorization": "Bearer " + self.token, "Accept": "application/json"}
        if content_type:
            h["Content-Type"] = content_type
        code, raw = self.transport(method, self.base + path, h, body)
        if code in (401, 403):
            raise Fail("jeton d'administration refusé par le serveur (HTTP %d)" % code)
        try:
            data = json.loads(raw.decode("utf-8")) if raw else None
        except ValueError:
            data = None
        return code, data

    def list_lots(self):
        code, data = self.call("GET", "/api/v1/admin/lots")
        if code != 200 or not isinstance(data, list):
            raise Fail("liste des lots impossible (HTTP %d)" % code)
        return data

    def upload(self, lot, channel):
        with open(lot["path"], "rb") as f:
            payload = f.read()
        fields = {"feature": FEATURE, "scope": lot["scope"], "version": lot["version"], "title": lot["title"], "channel": channel,
                  "minAppVersion": lot["minAppVersion"], "publish": "false", "sha256": lot["sha256"]}
        body, ctype = multipart(fields, "file", os.path.basename(lot["path"]), payload)
        code, data = self.call("POST", "/api/v1/admin/lots", body, ctype)
        if code != 201 or not data:
            msg = (data or {}).get("message", "") if isinstance(data, dict) else ""
            detail = "; ".join((data or {}).get("errors", [])) if isinstance(data, dict) else ""
            raise Fail("envoi refusé pour %s (HTTP %d) %s %s" % (lot["scope"], code, msg, detail))
        return data

    def publish(self, lot_id):
        code, data = self.call("POST", "/api/v1/admin/lots/%d/publish" % lot_id)
        if code != 200:
            raise Fail("publication refusée (HTTP %d)" % code)
        return data


def plan(lots, existing):
    """For each lot: 'skip' (published, same hash), 'publish' (uploaded, not published), 'upload' (new). Raises on a same-version hash clash."""
    by = {(e["feature"], e["scope"], e["version"]): e for e in existing}
    out = []
    for l in lots:
        e = by.get((FEATURE, l["scope"], l["version"]))
        if e is None:
            out.append((l, "upload", None))
        elif e["sha256"] != l["sha256"]:
            raise Fail("%s v%d existe déjà sur le serveur avec une autre empreinte : une version ne change jamais (reconstruire avec -Pupdate pour la suivante)" % (l["scope"], l["version"]))
        elif e.get("revoked"):
            raise Fail("%s v%d a été retiré du serveur : publier une nouvelle version" % (l["scope"], l["version"]))
        elif e.get("published"):
            out.append((l, "skip", e))
        else:
            out.append((l, "publish", e))
    return out


def mo(n):
    return "%.2f Mo" % (n / 1048576.0) if n >= 1 << 20 else "%d Ko" % ((n + 1023) // 1024)


def summary(rows, apply_, out=print):
    out("%-28s %4s %9s  %s" % ("lot", "ver", "taille", "action"))
    total = 0
    for l, action, _ in rows:
        total += l["bytes"]
        out("%-28s %4d %9s  %s" % (l["scope"], l["version"], mo(l["bytes"]), action if apply_ else "(simulation) " + action))
    todo = sum(1 for _, a, _ in rows if a != "skip")
    out("%d lot(s), %s ; %d à envoyer/publier, %d déjà à jour" % (len(rows), mo(total), todo, len(rows) - todo))


def run(args, env=None, transport=default_transport, out=print):
    env = os.environ if env is None else env
    lots_dir = args.lots_dir or build_lots()
    lots = load_lots(lots_dir)
    if not args.apply:
        out("SIMULATION (aucun envoi) : ajouter --apply pour publier sur %s" % args.server)
        summary([(l, "à envoyer", None) for l in lots], False, out)
        return 0
    token = env.get(TOKEN_ENV, "").strip()
    if not token:
        raise Fail("variable d'environnement %s absente : le jeton d'administration n'est jamais passé en argument" % TOKEN_ENV)
    admin = Admin(args.server, token, transport)
    rows = plan(lots, admin.list_lots())
    for l, action, e in rows:
        if action == "upload":
            e = admin.upload(l, args.channel)
        if action in ("upload", "publish"):
            admin.publish(e["id"])
    summary(rows, True, out)
    out("Vérification : curl -s '%s/api/v1/lots/catalog?feature=langues' (catalogue signé)" % args.server)
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lots-dir", help="dossier déjà construit (lots-catalog.json + *.lot) : saute la construction gradle")
    ap.add_argument("--server", default=DEFAULT_SERVER)
    ap.add_argument("--channel", default="stable", choices=["stable", "beta"])
    ap.add_argument("--apply", action="store_true", help="envoie et publie vraiment (sinon simulation)")
    try:
        return run(ap.parse_args(argv))
    except Fail as e:
        print("ERREUR : %s" % e, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
