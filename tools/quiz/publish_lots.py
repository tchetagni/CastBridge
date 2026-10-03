#!/usr/bin/env python3
"""Publie les lots « Quiz » LIBRES (content/quiz/lots) sur le serveur CastBridge (docs/QUIZ-PUBLISH.md). Même protocole que tools/langues/publish_lots.py.

  publish_lots.py [--lots-dir DIR] [--families FILE] [--server https://bridge.sti-cm.com] [--only SCOPE] [--limit N] [--apply]
  publish_lots.py --write-families      (régénère content/quiz/families.json depuis catalog-lots.json)

Règle du propriétaire (2026-10-03) : « publie sur le serveur, 30 % réservé ». RÉSERVÉ = tous les lots des niveaux L1, L2, L3 et Tle ; LIBRE = le reste.
Un lot réservé est chiffré par TV et livré par la location scellée : il n'est JAMAIS envoyé. Tout lot absent de families.json (ou présent dans les deux
listes) est refusé : inconnu = non publié. Par défaut : SIMULATION. --apply : POST /api/v1/admin/lots (publish=false, sha256 attendu) puis
POST /api/v1/admin/lots/{id}/publish. Jeton : variable CASTBRIDGE_ADMIN_TOKEN uniquement (jamais en argument, jamais affiché).
Idempotent : même empreinte déjà publiée = sautée ; envoyée non publiée = seulement publiée ; même version, autre empreinte = erreur.
"""
import argparse, hashlib, json, os, sys, urllib.error, urllib.parse, urllib.request, uuid

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
DEFAULT_LOTS_DIR = os.path.join(REPO, "content", "quiz", "lots")
DEFAULT_FAMILIES = os.path.join(REPO, "content", "quiz", "families.json")
MAX_LOT_BYTES = 3 << 20          # limite d'un lot texte côté TV
DEFAULT_SERVER = "https://bridge.sti-cm.com"
TOKEN_ENV = "CASTBRIDGE_ADMIN_TOKEN"
FEATURE = "quiz"
RESERVED_LEVELS = ("L1", "L2", "L3", "Tle")


class Fail(Exception):
    pass


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def read_catalog(lots_dir):
    p = os.path.join(lots_dir, "catalog-lots.json")
    if not os.path.isfile(p):
        raise Fail("catalog-lots.json introuvable dans %s" % lots_dir)
    with open(p, encoding="utf-8") as f:
        return json.load(f).get("lots", [])


def derive_families(catalog):
    """Règle déterministe : niveau dans RESERVED_LEVELS -> réservé, sinon libre."""
    free, reserved = [], []
    for e in catalog:
        (reserved if e.get("level") in RESERVED_LEVELS else free).append("quiz:" + e["scope"])
    return {"free": sorted(free), "reserved": sorted(reserved)}


def write_families(lots_dir, path, out=print):
    fam = derive_families(read_catalog(lots_dir))
    with open(path, "w", encoding="utf-8") as f:
        json.dump(fam, f, ensure_ascii=False, indent=1)
        f.write("\n")
    out("families.json écrit : %d libres, %d réservés" % (len(fam["free"]), len(fam["reserved"])))


def load_families(path):
    if not os.path.isfile(path):
        raise Fail("families.json introuvable (%s) : rien n'est publié" % path)
    with open(path, encoding="utf-8") as f:
        d = json.load(f)
    free, res = set(d.get("free", [])), set(d.get("reserved", []))
    both = sorted(free & res)
    if both:
        raise Fail("lots à la fois libres et réservés dans families.json : " + ", ".join(both))
    return free, res


def load_lots(lots_dir, families_path, only=None, limit=None):
    """Lots libres vérifiés : (lots à publier, nombre de réservés). Refuse tout lot réservé ou inconnu (rien n'est envoyé)."""
    free, res = load_families(families_path)
    cat = [e for e in read_catalog(lots_dir) if e.get("feature") == FEATURE]
    if only:
        cat = [e for e in cat if e["scope"] == only]
        if not cat:
            raise Fail("lot « %s » absent du catalogue" % only)
    problems, out = [], []
    for e in cat:
        scope, key = e["scope"], "quiz:" + e["scope"]
        if key in res:
            if only:
                problems.append("%s : lot RÉSERVÉ : livré par la location scellée, jamais publié" % scope)
            continue                      # lancement complet : les réservés ne sont jamais sélectionnés
        if key in free and e.get("level") in RESERVED_LEVELS:
            problems.append("%s : niveau %s réservé mais classé libre dans families.json (incohérent)" % (scope, e.get("level"))); continue
        if key not in free:
            problems.append("%s : absent de families.json (inconnu = non publié)" % scope); continue
        path = os.path.join(lots_dir, e.get("file", ""))
        if os.path.basename(path) != e.get("file") or not os.path.isfile(path):
            problems.append("%s : fichier %s absent" % (scope, e.get("file"))); continue
        size = os.path.getsize(path)
        if size > MAX_LOT_BYTES or size != e["bytes"]:
            problems.append("%s : taille %d octets (catalogue %s, maximum %d)" % (scope, size, e["bytes"], MAX_LOT_BYTES)); continue
        if sha256_file(path) != e["sha256"]:
            problems.append("%s : empreinte SHA-256 différente du catalogue" % scope); continue
        out.append({"scope": scope, "version": e["version"], "title": e["title"], "bytes": size, "sha256": e["sha256"], "path": path,
                    "minAppVersion": e.get("minAppVersion", 0)})
    if problems:
        raise Fail("lots refusés avant tout envoi :\n  " + "\n  ".join(problems))
    out.sort(key=lambda l: l["scope"])
    if limit is not None:
        out = out[:limit]
    if not out:
        raise Fail("aucun lot à publier")
    return out, len(res)


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
    req = urllib.request.Request(url, data=body, method=method, headers=headers)
    try:
        with urllib.request.build_opener(NoRedirect).open(req, timeout=120) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


class Admin:
    def __init__(self, server, token, transport=default_transport):
        try:
            u = urllib.parse.urlparse(server)
            host, user = u.hostname, u.username
        except ValueError:
            raise Fail("adresse du serveur invalide")
        loopback = u.scheme == "http" and host in ("localhost", "127.0.0.1", "::1")
        if user is not None or not host or not (u.scheme == "https" or loopback):
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
    by = {(e["feature"], e["scope"], e["version"]): e for e in existing}
    out = []
    for l in lots:
        e = by.get((FEATURE, l["scope"], l["version"]))
        if e is None:
            out.append((l, "upload", None))
        elif e["sha256"] != l["sha256"]:
            raise Fail("%s v%d existe déjà sur le serveur avec une autre empreinte : une version ne change jamais" % (l["scope"], l["version"]))
        elif e.get("revoked"):
            raise Fail("%s v%d a été retiré du serveur : publier une nouvelle version" % (l["scope"], l["version"]))
        elif e.get("published"):
            out.append((l, "skip", e))
        else:
            out.append((l, "publish", e))
    return out


def mo(n):
    return "%.2f Mo" % (n / 1048576.0) if n >= 1 << 20 else "%d Ko" % ((n + 1023) // 1024)


def summary(rows, nreserved, apply_, out=print):
    total = sum(l["bytes"] for l, _, _ in rows)
    todo = sum(1 for _, a, _ in rows if a != "skip")
    out("%d lot(s) libre(s), %s ; %d à envoyer/publier, %d déjà à jour ; %d lot(s) réservé(s) jamais publiés (location scellée)"
        % (len(rows), mo(total), todo, len(rows) - todo, nreserved))
    for l, action, _ in rows:
        out("  %-28s v%-2d %9s  %s" % (l["scope"], l["version"], mo(l["bytes"]), action if apply_ else "(simulation) " + action))


def run(args, env=None, transport=default_transport, out=print):
    env = os.environ if env is None else env
    if args.write_families:
        write_families(args.lots_dir, args.families, out)
        return 0
    lots, nres = load_lots(args.lots_dir, args.families, args.only, args.limit)
    if not args.apply:
        out("SIMULATION (aucun envoi) : ajouter --apply pour publier sur %s" % args.server)
        summary([(l, "à envoyer", None) for l in lots], nres, False, out)
        return 0
    token = env.get(TOKEN_ENV, "").strip()
    if not token:
        raise Fail("variable d'environnement %s absente : le jeton n'est jamais passé en argument" % TOKEN_ENV)
    admin = Admin(args.server, token, transport)
    rows = plan(lots, admin.list_lots())
    for l, action, e in rows:
        if action == "upload":
            e = admin.upload(l, "stable")
        if action in ("upload", "publish"):
            admin.publish(e["id"])
    summary(rows, nres, True, out)
    out("Vérification : curl -s '%s/api/v1/lots/catalog?feature=quiz'" % args.server)
    return 0


def parse(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lots-dir", default=DEFAULT_LOTS_DIR)
    ap.add_argument("--families", default=DEFAULT_FAMILIES)
    ap.add_argument("--server", default=DEFAULT_SERVER)
    ap.add_argument("--only", help="ne publier que ce scope (canari)")
    ap.add_argument("--limit", type=int, help="au plus N lots (canari)")
    ap.add_argument("--write-families", action="store_true", help="régénère families.json depuis le catalogue")
    ap.add_argument("--apply", action="store_true", help="envoie et publie vraiment (sinon simulation)")
    return ap.parse_args(argv)


def main(argv=None):
    try:
        return run(parse(argv))
    except Fail as e:
        print("ERREUR : %s" % e, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
