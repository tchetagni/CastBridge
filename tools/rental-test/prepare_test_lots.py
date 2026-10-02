#!/usr/bin/env python3
"""Prepares the TEST lots of « Apprendre » for a rental test: copies the built learn lots under the names the TV expects
(castbridge-lot-learn-<scope>-v<N>.lot) and writes ONE signed catalog (catalog.json) covering them.

  gradle :core:buildLearnLots                      # builds android/core/build/learn-lots (one zip per class + lots-catalog.json)
  python3 tools/rental-test/prepare_test_lots.py --out DIR --key test-lots.pem [--scopes cp,ce1] [--max-bytes 7000000]

TEST ONLY: sign with a TEST key (the TV test build trusts its public key through -Pcastbridge.extraUpdateKey). The production catalog is signed with the owner's key
(docs/CONTENT-PUBLISH.md). Needs the `cryptography` module (pip install cryptography)."""
import argparse, base64, datetime, hashlib, importlib.util, json, os, shutil, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
spec = importlib.util.spec_from_file_location("build_lots", os.path.join(ROOT, "tools", "content-lots", "build_lots.py"))
build_lots = importlib.util.module_from_spec(spec); spec.loader.exec_module(build_lots)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--learn", default=os.path.join(ROOT, "android", "core", "build", "learn-lots"))
    ap.add_argument("--out", required=True)
    ap.add_argument("--key", required=True, help="PEM private key of the TEST catalog signer")
    ap.add_argument("--scopes", default="", help="comma-separated scopes to keep (default: all)")
    ap.add_argument("--max-bytes", type=int, default=0, help="stop adding lots once this total is reached (the TV holds 10 Mo with the starter data)")
    a = ap.parse_args(argv)
    cat = json.load(open(os.path.join(a.learn, "lots-catalog.json"), encoding="utf-8"))
    want = {s for s in a.scopes.split(",") if s}
    os.makedirs(a.out, exist_ok=True)
    entries, total = [], 0
    for e in sorted(cat["lots"], key=lambda e: e["bytes"]):                       # smallest first: more classes fit
        if want and e["scope"] not in want: continue
        if a.max_bytes and total + e["bytes"] > a.max_bytes: continue
        src = os.path.join(a.learn, e["file"])
        name = "castbridge-lot-%s-%s-v%d.lot" % (e["feature"], e["scope"], e["version"])
        shutil.copyfile(src, os.path.join(a.out, name))
        if hashlib.sha256(open(os.path.join(a.out, name), "rb").read()).hexdigest() != e["sha256"]:
            sys.exit("empreinte différente pour " + name)
        entries.append({k: v for k, v in e.items() if k != "file"}); total += e["bytes"]
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    catalog = {"channel": "stable", "feature": None, "generatedAt": now, "keyId": None, "signature": "UNSIGNED", "lots": entries}
    build_lots.sign(catalog, a.key)
    json.dump(catalog, open(os.path.join(a.out, "catalog.json"), "w", encoding="utf-8"), ensure_ascii=False)
    print("%d lot(s), %.2f Mo, catalogue signé (clé %s) dans %s" % (len(entries), total / 1048576, catalog["keyId"], a.out))


if __name__ == "__main__":
    main()
