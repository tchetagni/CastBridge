#!/usr/bin/env python3
"""Assembles the rsync-ready tree and CONTENT-RELEASE.json (docs/CONTENT-PUBLISH.md).

  make_release.py --lots DIR --out TREE [--version V] [--content content/] [--bust-cache]

TREE/
  CONTENT-RELEASE.json   version, date, per-feature and per-lot sizes, sha256 of the tree, number of skills, signed or not
  SHA256SUMS             sha256 of every file of the tree (except itself and CONTENT-RELEASE.json), `sha256sum -c` compatible
  lots/                  castbridge-lot-*.lot + catalog.json (signed)
  graph/                 scopes.json, paths.json, <domain>.json (the skill graph, for the server and the admin tools)
  MEDIA-MANIFEST.json
The tree hash is the sha256 of the sorted lines "<path> <sha256>" of the SHA256SUMS content, so two builds of the same content give the same hash.
"""
import argparse, datetime, hashlib, json, os, shutil, subprocess, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "content-lib"))
import lotlib as L  # noqa: E402


def sha_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for c in iter(lambda: f.read(1 << 20), b""):
            h.update(c)
    return h.hexdigest()


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lots", required=True); ap.add_argument("--out", required=True); ap.add_argument("--content", default=L.CONTENT)
    ap.add_argument("--version", default=datetime.date.today().strftime("%Y.%m.%d"))
    ap.add_argument("--generated-at", default=None)
    a = ap.parse_args(argv)
    if os.path.exists(a.out):
        shutil.rmtree(a.out)
    os.makedirs(os.path.join(a.out, "lots")); os.makedirs(os.path.join(a.out, "graph"))
    for fn in sorted(os.listdir(a.lots)):
        if fn.endswith(".lot") or fn == "catalog.json":
            shutil.copy2(os.path.join(a.lots, fn), os.path.join(a.out, "lots", fn))
    gd = os.path.join(a.content, "graph")
    for fn in sorted(os.listdir(gd)):
        if fn.endswith(".json"):
            shutil.copy2(os.path.join(gd, fn), os.path.join(a.out, "graph", fn))
    shutil.copy2(os.path.join(a.content, "MEDIA-MANIFEST.json"), os.path.join(a.out, "MEDIA-MANIFEST.json"))
    cat = L.read_json(os.path.join(a.out, "lots", "catalog.json"))
    sums = []
    for root, _, files in os.walk(a.out):
        for fn in files:
            p = os.path.join(root, fn)
            sums.append((os.path.relpath(p, a.out).replace(os.sep, "/"), sha_file(p)))
    sums.sort()
    with open(os.path.join(a.out, "SHA256SUMS"), "w", encoding="utf-8") as f:
        f.writelines("%s  %s\n" % (h, p) for p, h in sums)
    tree = hashlib.sha256("".join("%s %s\n" % (p, h) for p, h in sums).encode()).hexdigest()
    feats = {}
    for e in cat["lots"]:
        f = feats.setdefault(e["feature"], {"lots": 0, "bytes": 0})
        f["lots"] += 1; f["bytes"] += e["bytes"]
    skills = domains = 0
    for fn in os.listdir(gd):
        if fn.endswith(".json") and fn not in ("scopes.json", "paths.json"):
            domains += 1; skills += len(L.read_json(os.path.join(gd, fn))["skills"])
    commit = None
    try:
        commit = subprocess.run(["git", "-C", L.REPO, "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        pass
    total = sum(os.path.getsize(os.path.join(r, f)) for r, _, fs in os.walk(a.out) for f in fs)
    rel = {"format": 1, "version": a.version, "generatedAt": a.generated_at or datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
           "sourceCommit": commit, "channel": cat["channel"], "signed": cat["signature"] != "UNSIGNED", "keyId": cat.get("keyId"),
           "features": feats, "lots": [{"id": "%s:%s" % (e["feature"], e["scope"]), "version": e["version"], "bytes": e["bytes"], "sha256": e["sha256"]} for e in cat["lots"]],
           "graph": {"domains": domains, "skills": skills}, "files": len(sums), "totalBytes": total, "treeSha256": tree,
           "limits": {"tvLotMaxBytes": L.TV_LOT_MAX, "phonePathMaxBytes": L.PHONE_PATH_MAX, "totalMaxBytes": L.TOTAL_MAX}}
    with open(os.path.join(a.out, "CONTENT-RELEASE.json"), "w", encoding="utf-8") as f:
        json.dump(rel, f, ensure_ascii=False, indent=1); f.write("\n")
    print("Release %s : %d lots, %d fichiers, %.2f Mo, arbre %s…, %s" % (rel["version"], len(cat["lots"]), len(sums) + 2, total / 1048576, tree[:12], "signé" if rel["signed"] else "NON SIGNÉ"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
