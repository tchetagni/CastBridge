#!/usr/bin/env python3
"""Plays a whole rental against a TV (the emulator tonight, the real TV tomorrow): device request -> rental activation (the owner's TEST desk key) -> install on the TV ->
every lot sealed for that TV, uploaded and installed -> state of the rentals.

  python3 tools/rental-test/rental_test.py --tv http://192.168.0.121:8765 --pin 123456 --desk DIR --pass-file F --lots DIR [--days 2] [--super]
  python3 tools/rental-test/rental_test.py ... --status     # only prints GET /api/rental
  python3 tools/rental-test/rental_test.py ... --sweep      # POST /api/rental/sweep (the TV also sweeps by itself every 15 minutes)

TEST ONLY: the desk key must be trusted by the TV build (the test build trusts a TEST key). The licence is created in the TEST desk registry, never on the server."""
import argparse, json, os, subprocess, sys, tempfile, time, urllib.error, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
CHUNK = 512 * 1024


def call(tv, pin, method, path, body=None, ctype="application/octet-stream"):
    req = urllib.request.Request(tv + path, data=body, method=method, headers={"X-CB-Pin": pin, "Content-Type": ctype})
    try:
        with urllib.request.urlopen(req, timeout=60) as r: return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e: return e.code, e.read().decode("utf-8", "replace")


def desk(a, *args, capture=True):
    cmd = ["java", "-jar", a.jar, *args, "--dossier", a.desk, "--code-fichier", a.pass_file]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0: sys.exit("outil de bureau : %s\n%s" % (" ".join(args[:1]), r.stdout + r.stderr))
    return r.stdout


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--tv", required=True); ap.add_argument("--pin", required=True)
    ap.add_argument("--desk", required=True); ap.add_argument("--pass-file", required=True)
    ap.add_argument("--jar", default=os.path.join(ROOT, "tools", "activation-desktop", "build", "libs", "castbridge-activation-desktop.jar"))
    ap.add_argument("--lots", help="folder made by prepare_test_lots.py")
    ap.add_argument("--days", type=int, default=2); ap.add_argument("--product", default="loc-apprendre-test"); ap.add_argument("--bundle", default="apprendre-test")
    ap.add_argument("--license", default="lic-test-location"); ap.add_argument("--super", action="store_true", help="also give the SUPER_UNLIMITED right (rentals become permanent)")
    ap.add_argument("--status", action="store_true"); ap.add_argument("--sweep", action="store_true")
    a = ap.parse_args(argv)
    if a.status:
        s, b = call(a.tv, a.pin, "GET", "/api/rental"); print(s, json.dumps(json.loads(b), ensure_ascii=False, indent=1)); return 0 if s == 200 else 1
    if a.sweep:
        s, b = call(a.tv, a.pin, "POST", "/api/rental/sweep", b""); print(s, b); return 0 if s == 200 else 1
    if not a.lots: sys.exit("--lots est obligatoire (dossier produit par prepare_test_lots.py)")
    tmp = tempfile.mkdtemp(prefix="rental-test-")
    s, b = call(a.tv, a.pin, "GET", "/api/activation/request")
    if s != 200: sys.exit("la TV ne donne pas sa demande d'appareil (%s) : %s" % (s, b))
    req = os.path.join(tmp, "device-request.txt"); open(req, "w").write(json.loads(b)["request"] + "\n")
    print("1. demande d'appareil de la TV :", open(req).readline().strip())
    subprocess.run(["java", "-jar", a.jar, "licence", a.license, "--postes", "1", "--dossier", a.desk, "--code-fichier", a.pass_file], capture_output=True, text=True)   # idempotent: already exists is fine
    extra = ["--super"] if a.super else []
    out = desk(a, "emettre", "--appareil", req, "--production", "--licence", a.license, "--location", "%s=%s:%d" % (a.product, a.bundle, a.days), "--sortie", tmp, *extra)
    token = out.split("Jeton :")[1].strip().splitlines()[0]
    period = [l for l in out.splitlines() if l.startswith("Location ")][0].split("période")[1].strip()
    contract = "%s@%s" % (a.product, period)
    print("2. activation émise : location de %d jour(s)%s, contrat %s" % (a.days, " + SUPER_UNLIMITED" if a.super else "", contract))
    s, b = call(a.tv, a.pin, "POST", "/api/activation/install", token.encode(), "text/plain"); print("3. installation sur la TV :", s, b)
    if s != 200: return 1
    actfile = os.path.join(tmp, "activation"); open(actfile, "w").write(token + "\n")
    catalog = open(os.path.join(a.lots, "catalog.json"), "rb").read()
    lots = sorted(f for f in os.listdir(a.lots) if f.endswith(".lot"))
    ok = bad = 0
    for f in lots:
        sealed_dir = os.path.join(tmp, "sealed"); os.makedirs(sealed_dir, exist_ok=True)
        desk(a, "lot-chiffrer", "--activation", actfile, "--produit", a.product, "--lot", os.path.join(a.lots, f), "--sortie", sealed_dir)
        data = open(os.path.join(sealed_dir, f), "rb").read(); off = 0
        while off < len(data):
            part = data[off:off + CHUNK]
            s, b = call(a.tv, a.pin, "POST", "/api/lots/upload?name=%s&offset=%d&total=%d" % (f, off, len(data)), part)
            if s != 200: print("   ENVOI REFUSÉ", f, s, b[:160]); break
            off += len(part)
        else:
            s, b = call(a.tv, a.pin, "POST", "/api/rental/install?name=%s&contract=%s" % (f, contract.replace("@", "%40")), catalog, "application/json")
            if s == 200: ok += 1; print("   installé  ", f)
            else: bad += 1; print("   REFUSÉ    ", f, s, b[:200])
    print("4. lots : %d installé(s), %d refusé(s) sur %d" % (ok, bad, len(lots)))
    s, b = call(a.tv, a.pin, "GET", "/api/rental"); print("5. état des locations :", s, b[:600])
    return 0 if bad == 0 else 2


if __name__ == "__main__":
    sys.exit(main())
