#!/usr/bin/env python3
"""Builds ALL lots (Apprendre and Quiz) of content/ and the signed catalog (docs/CONTENT-PUBLISH.md). Nothing here talks to a server (rule A).

  build_lots.py --out DIR [--learn-packs DIR] [--registry content/LOT-VERSIONS.json] [--channel stable] [--sign-key FILE] [--bump]

A lot = castbridge-lot-<feature>-<scope>-v<version>.lot (a deterministic ZIP, name as in castbridge.core.lots.LotNames):
  lot.json            manifest (feature, scope, version, title, skills, files with sha256)
  packs/<file>        the packs of the lot (Apprendre: <id>-v<n>.learn.zip built by gradle :core:buildLearnPacks; Quiz: *.quiz.zip)
  media/<path>        media assets declared in MEDIA-MANIFEST.json for this lot
The VERSION of a lot changes only when its content changes: the registry keeps the content hash of every lot; with --bump a changed
hash raises the version by one, without it a change is an error (CI never bumps by surprise). The catalog follows the signing text of
castbridge.core.lots.LotManifest (castbridge-lot-catalog-v1); without --sign-key it is written UNSIGNED and marked so.
"""
import argparse, base64, datetime, hashlib, json, os, sys, zipfile
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "content-lib"))
import lotlib as L  # noqa: E402

FIXED_TIME = (2026, 1, 1, 0, 0, 0)
MIN_APP_VERSION = 0


def sha(b):
    return hashlib.sha256(b).hexdigest()


def collect(content, learn_packs):
    """lot key -> {"packs": [(name, bytes)], "media": [(rel, bytes)], "title": str}"""
    lots = {}
    scopes = {s["id"]: s for s in L.read_json(os.path.join(content, "graph", "scopes.json"))["scopes"]}

    def lot(feature, scope):
        k = "%s:%s" % (feature, scope)
        if k not in lots:
            title = scopes.get(scope, {}).get("title", {}).get("fr", scope)
            lots[k] = {"feature": feature, "scope": scope, "packs": [], "media": [], "title": ("Apprendre — " if feature == "learn" else "Quiz — ") + title.split("— ")[-1]}
        return lots[k]
    learn = os.path.join(content, "learn")
    for d in sorted(os.listdir(learn)):
        pj = os.path.join(learn, d, "pack.json")
        if not os.path.isfile(pj):
            continue
        pack = L.read_json(pj); scope = L.learn_scope(pack)
        if scope is None:
            raise SystemExit("pack %s: niveau « %s » sans portée de lot" % (d, pack.get("level")))
        z = os.path.join(learn_packs, "%s-v%d.learn.zip" % (pack["id"], pack["version"])) if learn_packs else None
        if not z or not os.path.isfile(z):
            raise SystemExit("paquet %s introuvable dans %s (lancer gradle :core:buildLearnPacks)" % (z, learn_packs))
        lot("learn", scope)["packs"].append((os.path.basename(z), open(z, "rb").read()))
    dist = os.path.join(content, "quiz", "dist")
    for e in L.read_json(os.path.join(dist, "catalog.json"))["packs"]:
        scope = L.quiz_scope(e)
        if scope is None:
            raise SystemExit("paquet de quiz %s: portée de lot inconnue" % e["id"])
        lot("quiz", scope)["packs"].append((e["file"], open(os.path.join(dist, e["file"]), "rb").read()))
    for a in L.media_manifest(content).get("assets", []):
        feat, _, sc = a["lot"].partition(":")
        lot(feat, sc)["media"].append((a["path"], open(os.path.join(content, a["path"]), "rb").read()))
    return lots


def skills_of(content, scope):
    out = []
    gd = os.path.join(content, "graph")
    for fn in sorted(os.listdir(gd)):
        if fn.endswith(".json") and fn not in ("scopes.json", "paths.json"):
            for s in L.read_json(os.path.join(gd, fn))["skills"]:
                if scope in s["lots"]:
                    out.append(s["id"])
    return out


def content_hash(lot):
    h = hashlib.sha256()
    for name, data in sorted(lot["packs"]) + [("media/" + n, d) for n, d in sorted(lot["media"])]:
        h.update(("%s|%s\n" % (name, sha(data))).encode())
    return h.hexdigest()


def write_lot(path, lot, version, skills):
    files = [("packs/" + n, d) for n, d in sorted(lot["packs"])] + [("media/" + n, d) for n, d in sorted(lot["media"])]
    manifest = {"format": 1, "feature": lot["feature"], "scope": lot["scope"], "version": version, "title": lot["title"], "skills": skills,
                "files": [{"path": n, "size": len(d), "sha256": sha(d)} for n, d in files]}
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for n, d in [("lot.json", (json.dumps(manifest, ensure_ascii=False, indent=1) + "\n").encode("utf-8"))] + files:
            zi = zipfile.ZipInfo(n, FIXED_TIME); zi.compress_type = zipfile.ZIP_STORED if n.endswith(".zip") else zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, d)
    return open(path, "rb").read()


def canonical(catalog):
    lines = ["castbridge-lot-catalog-v1", "channel=" + catalog["channel"], "feature=" + (catalog["feature"] or "*"), "generatedAt=" + catalog["generatedAt"]]
    for e in sorted(catalog["lots"], key=lambda e: (e["feature"], e["scope"], e["version"])):
        lines.append("lot=%s|%s|%d|%d|%s|%d|%s" % (e["feature"], e["scope"], e["version"], e["bytes"], e["sha256"], e["minAppVersion"], sha(e["title"].encode("utf-8"))))
    return "\n".join(lines)


def sign(catalog, key_file):
    """Ed25519 signature (base64) of the canonical text, as castbridge.core.update.Ed25519 verifies. Key: PEM private key file."""
    from cryptography.hazmat.primitives import serialization
    key = serialization.load_pem_private_key(open(key_file, "rb").read(), password=None)
    pub = key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
    catalog["keyId"] = hashlib.sha256(pub).hexdigest()[:16]
    catalog["signature"] = base64.b64encode(key.sign(canonical(catalog).encode("utf-8"))).decode()


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--content", default=L.CONTENT); ap.add_argument("--out", required=True); ap.add_argument("--learn-packs")
    ap.add_argument("--registry", default=os.path.join(L.CONTENT, "LOT-VERSIONS.json")); ap.add_argument("--channel", default="stable")
    ap.add_argument("--sign-key", default=os.environ.get("CASTBRIDGE_LOT_SIGN_KEY")); ap.add_argument("--bump", action="store_true")
    ap.add_argument("--generated-at", default=None)
    a = ap.parse_args(argv)
    os.makedirs(a.out, exist_ok=True)
    reg = L.read_json(a.registry) if os.path.isfile(a.registry) else {"format": 1, "lots": {}}
    lots = collect(a.content, a.learn_packs)
    entries, errors, changed = [], [], False
    for key in sorted(lots):
        lot = lots[key]; h = content_hash(lot); r = reg["lots"].get(key)
        if r is None:
            version = 1; reg["lots"][key] = {"version": 1, "contentHash": h}; changed = True
        elif r["contentHash"] != h:
            if not a.bump:
                errors.append("lot %s: contenu modifié sans nouvelle version (relancer avec --bump)" % key); continue
            version = r["version"] + 1; reg["lots"][key] = {"version": version, "contentHash": h}; changed = True
        else:
            version = r["version"]
        name = "castbridge-lot-%s-%s-v%d.lot" % (lot["feature"], lot["scope"], version)
        data = write_lot(os.path.join(a.out, name), lot, version, skills_of(a.content, lot["scope"]))
        entries.append({"feature": lot["feature"], "scope": lot["scope"], "version": version, "bytes": len(data), "sha256": sha(data), "title": lot["title"], "minAppVersion": MIN_APP_VERSION, "file": name})
    if errors:
        for e in errors:
            print("ERREUR: " + e, file=sys.stderr)
        return 1
    # a lot of the registry that no longer has content is reported, never silently dropped
    for k in sorted(set(reg["lots"]) - set(lots)):
        print("AVERTISSEMENT: le lot %s est dans le registre mais n'a plus de contenu" % k)
    now = a.generated_at or datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    catalog = {"channel": a.channel, "feature": None, "generatedAt": now, "keyId": None, "signature": "UNSIGNED",
               "lots": [{k: v for k, v in e.items() if k != "file"} for e in entries]}
    if a.sign_key:
        sign(catalog, a.sign_key)
    else:
        print("AVERTISSEMENT: catalogue NON SIGNÉ (pas de --sign-key / CASTBRIDGE_LOT_SIGN_KEY) : bon pour tester, refusé par les téléphones")
    with open(os.path.join(a.out, "catalog.json"), "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=1); f.write("\n")
    if changed:
        os.makedirs(os.path.dirname(a.registry), exist_ok=True)
        with open(a.registry, "w", encoding="utf-8") as f:
            json.dump(reg, f, ensure_ascii=False, indent=1, sort_keys=True); f.write("\n")
    print("%d lot(s) dans %s, catalogue %s" % (len(entries), a.out, "signé (clé %s)" % catalog["keyId"] if catalog["keyId"] else "NON SIGNÉ"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
