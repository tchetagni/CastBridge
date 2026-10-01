#!/usr/bin/env python3
"""Content budget (docs/CONTENT-ARCHITECTURE.md § 8): scans content/ and prints sizes per lot, feature, media type, domain
and class; FAILS (exit 1) above the owner ceilings:

  - any TV-eligible lot            > 3 MB (figures and animations included; heavy media never in a TV lot)
  - the phone total of a learner path (content/graph/paths.json)  > 100 MB
  - any single file                > 50 MB
  - the whole content tree         > 3 GB (warning from 2.5 GB)

Lot sizes are what the lot file will weigh: the zipped size of the sources (quiz packs and media are already compressed).
Usage: content_budget.py [--content DIR] [--json OUT.json] [--quiet]
"""
import argparse, json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "content-lib"))
import lotlib as L  # noqa: E402

FIG_MAX = 8 << 10; ANIM_MAX = 40 << 10


def mb(n):
    return "%.2f Mo" % (n / (1 << 20)) if n >= 1 << 16 else "%.1f Ko" % (n / 1024)


def inline_media(node, out, path=""):
    """Collects inline vector figures / animations of a lesson file: [(kind, bytes, where)]."""
    if isinstance(node, dict):
        for k, v in node.items():
            if k in ("figure", "animation") and isinstance(v, dict):
                out.append((k, len(json.dumps(v, ensure_ascii=False, separators=(",", ":")).encode("utf-8")), path + "/" + k))
            else:
                inline_media(v, out, path + "/" + k)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            inline_media(v, out, "%s[%d]" % (path, i))


def analyse(content):
    recs = L.scan(content)
    scopes = {s["id"]: s for s in L.read_json(os.path.join(content, "graph", "scopes.json"))["scopes"]} if os.path.isfile(os.path.join(content, "graph", "scopes.json")) else {}
    errors, warnings = [], []
    lots = {}
    for r in recs:
        if r["feature"] is None:
            continue
        if r["scope"] is None:
            errors.append("%s: aucune portée de lot (niveau inconnu)" % r["path"]); continue
        if scopes and r["scope"] not in scopes:
            errors.append("%s: portée de lot « %s » absente de content/graph/scopes.json" % (r["path"], r["scope"]))
        key = "%s:%s" % (r["feature"], r["scope"])
        lot = lots.setdefault(key, {"lot": key, "bytes": 0, "raw": 0, "files": 0, "by_kind": {}, "tv": scopes.get(r["scope"], {}).get("tv", not r["scope"].endswith("-media"))})
        lot["bytes"] += r["zipped"]; lot["raw"] += r["bytes"]; lot["files"] += 1
        lot["by_kind"][r["kind"]] = lot["by_kind"].get(r["kind"], 0) + r["zipped"]
        if lot["tv"] and r["kind"] in ("clip", "audio", "image"):
            errors.append("%s: %s dans un lot compatible TV (%s) : réservé aux lots média du téléphone" % (r["path"], r["kind"], key))
    for key, lot in sorted(lots.items()):
        if lot["tv"] and lot["bytes"] > L.TV_LOT_MAX:
            errors.append("lot %s: %s > %s (plafond d'un lot TV)" % (key, mb(lot["bytes"]), mb(L.TV_LOT_MAX)))
        elif lot["tv"] and lot["bytes"] > 0.8 * L.TV_LOT_MAX:
            warnings.append("lot %s: %s, plus de 80 %% du plafond TV" % (key, mb(lot["bytes"])))
    for r in recs:
        if r["bytes"] > L.FILE_MAX:
            errors.append("%s: %s > 50 Mo par fichier" % (r["path"], mb(r["bytes"])))
    total = sum(r["bytes"] for r in recs)
    if total > L.TOTAL_MAX:
        errors.append("total %s > 3 Go (plafond du propriétaire)" % mb(total))
    elif total > L.TOTAL_WARN:
        warnings.append("total %s > 2,5 Go (alerte avant le plafond de 3 Go)" % mb(total))
    # inline figures / animations
    figs = {"figure": [0, 0], "animation": [0, 0]}
    for r in recs:
        if r["feature"] == "learn" and r["kind"] == "text" and r["path"].endswith(".json") and "/lessons/" in r["path"]:
            found = []
            try:
                with open(os.path.join(content, r["path"]), encoding="utf-8") as f:
                    inline_media(json.load(f), found)
            except ValueError as ex:
                errors.append("%s: JSON invalide (%s)" % (r["path"], ex)); continue
            for k, b, where in found:
                figs[k][0] += 1; figs[k][1] += b
                if b > (FIG_MAX if k == "figure" else ANIM_MAX):
                    errors.append("%s %s: %s > %s (%s)" % (r["path"], where, mb(b), mb(FIG_MAX if k == "figure" else ANIM_MAX), k))
    # learner paths
    paths = []
    pj = os.path.join(content, "graph", "paths.json")
    if os.path.isfile(pj):
        for p in L.read_json(pj)["paths"]:
            size = sum(lots[k]["bytes"] for k in p["lots"] if k in lots)
            paths.append({"id": p["id"], "bytes": size, "lots": sum(1 for k in p["lots"] if k in lots), "declared": len(p["lots"])})
            if size > L.PHONE_PATH_MAX:
                errors.append("parcours %s: %s > %s sur le téléphone" % (p["id"], mb(size), mb(L.PHONE_PATH_MAX)))
    # aggregates
    feat, kind, domain, cls = {}, {}, {}, {}
    for r in recs:
        feat[r["feature"] or "(hors lot)"] = feat.get(r["feature"] or "(hors lot)", 0) + r["zipped"]
        kind[r["kind"]] = kind.get(r["kind"], 0) + r["zipped"]
        if r["domain"]:
            domain[r["domain"]] = domain.get(r["domain"], 0) + r["zipped"]
        if r["scope"]:
            cls[r["scope"]] = cls.get(r["scope"], 0) + r["zipped"]
    return {"lots": sorted(lots.values(), key=lambda x: x["lot"]), "feature": feat, "kind": kind, "domain": domain, "class": cls, "paths": paths,
            "inline": {k: {"count": v[0], "bytes": v[1]} for k, v in figs.items()}, "total_bytes": total, "files": len(recs),
            "errors": errors, "warnings": warnings}


def report(res):
    out = []
    out.append("== Lots (taille du fichier lot, compressé) ==")
    for l in res["lots"]:
        out.append("  %-24s %10s  (%d fichiers, brut %s)%s" % (l["lot"], mb(l["bytes"]), l["files"], mb(l["raw"]), "" if l["tv"] else "  [média, téléphone seulement]"))
    for title, key in (("par fonction", "feature"), ("par type de média", "kind"), ("par domaine", "domain"), ("par classe / portée", "class")):
        out.append("== %s ==" % title.capitalize())
        for k, v in sorted(res[key].items(), key=lambda kv: -kv[1]):
            out.append("  %-24s %10s" % (k, mb(v)))
    out.append("== Figures et animations intégrées aux leçons ==")
    for k, v in res["inline"].items():
        out.append("  %-10s %4d, %s" % (k, v["count"], mb(v["bytes"])))
    out.append("== Parcours (téléphone, plafond %s) ==" % mb(L.PHONE_PATH_MAX))
    for p in res["paths"]:
        out.append("  %-18s %10s  (%d/%d lots produits)" % (p["id"], mb(p["bytes"]), p["lots"], p["declared"]))
    out.append("== Total : %s dans %d fichiers (plafond %s, alerte %s) ==" % (mb(res["total_bytes"]), res["files"], mb(L.TOTAL_MAX), mb(L.TOTAL_WARN)))
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--content", default=L.CONTENT); ap.add_argument("--json"); ap.add_argument("--quiet", action="store_true")
    a = ap.parse_args(argv)
    res = analyse(a.content)
    if not a.quiet:
        print("\n".join(report(res)))
    for w in res["warnings"]:
        print("AVERTISSEMENT: " + w)
    for e in res["errors"]:
        print("ERREUR: " + e, file=sys.stderr)
    if a.json:
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump(res, f, ensure_ascii=False, indent=1)
    print("Budget OK." if not res["errors"] else "%d erreur(s) de budget." % len(res["errors"]))
    return 1 if res["errors"] else 0


if __name__ == "__main__":
    sys.exit(main())
