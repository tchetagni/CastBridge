#!/usr/bin/env python3
"""Vérificateur de référence INDÉPENDANT de Kotlin : rejoue tools/activation/test-vectors.json avec la seule spécification docs/ACTIVATION-FORMAT.md.

    python3 tools/activation/verify_vectors.py            # exit 0 si tous les vecteurs passent

Sert de preuve que la spécification suffit à écrire un émetteur ou un vérificateur (bureau, serveur, téléphone) sans lire le code Kotlin, et de modèle de départ
pour ces outils. Dépendance : le paquet `cryptography` (Ed25519).
"""
import base64
import hashlib
import json
import os
import sys

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey

ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
KINDS = {"FLASH": (0, True), "ETHERNET": (1, True), "WIFI": (2, False), "SYSTEM_SERIAL": (3, False), "BLUETOOTH": (4, False)}
SALT = "castbridge-device-v1"
DAY = 86400000
EPOCH_MS = 1767225600000
MAX_WINDOW = 366 * DAY
MAX_OPEN_ALL = 30 * DAY
CHALLENGE_TTL_MS = 120000
PLACEHOLDER = {"", "unknown", "null", "none", "n/a", "default string", "not specified", "123456789abcdef0", "123456789abcdef", "0123456789abcdef", "020000000000"}


def b32value(c):
    c = c.upper()
    c = {"O": "0", "I": "1", "L": "1"}.get(c, c)
    return ALPHABET.find(c)


def b32encode(data):
    out, buf, bits = [], 0, 0
    for b in data:
        buf = (buf << 8) | b
        bits += 8
        while bits >= 5:
            bits -= 5
            out.append(ALPHABET[(buf >> bits) & 31])
        buf &= (1 << bits) - 1
    if bits:
        out.append(ALPHABET[(buf << (5 - bits)) & 31])
    return "".join(out)


def b32decode(text, count):
    out, buf, bits = bytearray(), 0, 0
    for c in text:
        v = b32value(c)
        if v < 0:
            return None
        buf = (buf << 5) | v
        bits += 5
        if bits >= 8:
            bits -= 8
            if len(out) < count:
                out.append((buf >> bits) & 0xFF)
            buf &= (1 << bits) - 1
    return bytes(out) if len(out) == count else None


def check_char(chars, salt=0):
    return ALPHABET[(sum(b32value(c) * (2 * i + 1) for i, c in enumerate(chars)) + salt * 7) & 31]


def clean(v):
    if v is None:
        return None
    s = v.strip().lower().replace(":", "").replace("-", "")
    if s in PLACEHOLDER or (s and set(s) <= {"0"}) or (s and set(s) <= {"f"}) or (s and set(s) <= {"x"}):
        return None
    return s


def wifi_soldered(path):
    if not path:
        return False
    p = path.lower()
    if "/usb" in p:
        return False
    return any(x in p for x in ("/sdio", "/mmc", "/pci", "/platform"))


def fp_hash(kind, value):
    return hashlib.sha256(("%s|%s|%s" % (SALT, kind, value)).encode()).digest()[:16].hex()


def fingerprints(raw):
    m = {}
    flash = "+".join(x for x in (clean(raw.get("flashSerial")), clean(raw.get("flashCid"))) if x)
    if flash:
        m["FLASH"] = fp_hash("FLASH", flash)
    if clean(raw.get("ethernetMac")):
        m["ETHERNET"] = fp_hash("ETHERNET", clean(raw["ethernetMac"]))
    if wifi_soldered(raw.get("wifiSysfsPath")) and clean(raw.get("wifiMac")):
        m["WIFI"] = fp_hash("WIFI", clean(raw["wifiMac"]))
    if clean(raw.get("systemSerial")):
        m["SYSTEM_SERIAL"] = fp_hash("SYSTEM_SERIAL", clean(raw["systemSerial"]))
    if clean(raw.get("bluetoothAddress")):
        m["BLUETOOTH"] = fp_hash("BLUETOOTH", clean(raw["bluetoothAddress"]))
    return m


def order(fp):
    return sorted(fp, key=lambda k: KINDS[k][0])      # the order of the FactorKind enum (bit order)


def set_hash(fp):
    return hashlib.sha256("\n".join("%s=%s" % (k, fp[k]) for k in order(fp)).encode()).digest()


def device_code(fp):
    mask = sum(1 << KINDS[k][0] for k in fp)
    body = ALPHABET[mask & 31] + b32encode(set_hash(fp))[:14]
    s = body + check_char(body)
    return "-".join(s[i:i + 4] for i in range(0, 16, 4))


def parse_code(text):
    s = "".join(ch for ch in text if ch != "-" and not ch.isspace())
    if any(b32value(c) < 0 for c in s):
        return None
    s = "".join(ALPHABET[b32value(c)] for c in s)
    if len(s) != 16 or check_char(s[:15]) != s[15]:
        return None
    return "-".join(s[i:i + 4] for i in range(0, 16, 4))


def k_for(n):
    return n - 1 if n >= 3 else max(n, 1)


def matches(factors, k, device):
    if not factors or k < 1:
        return False
    hit = [f for f in factors if device.get(f) == factors[f]]
    if len(hit) < k:
        return False
    return not any(KINDS[f][1] for f in factors) or any(KINDS[f][1] for f in hit)


def grouped_encode(data):
    raw = b32encode(bytes([len(data) >> 8, len(data) & 255]) + data)
    raw = raw + "0" * (-len(raw) % 4)
    return "-".join(raw[i:i + 4] + check_char(raw[i:i + 4], i // 4 + 1) for i in range(0, len(raw), 4))


def grouped_decode(text):
    chars = "".join(c for c in text if c != "-" and not c.isspace())
    if not chars or len(chars) % 5:
        return ("malformed",)
    data = ""
    for i in range(0, len(chars), 5):
        g = chars[i:i + 5]
        if any(b32value(c) < 0 for c in g):
            return ("bad-group", i // 5 + 1)
        g = "".join(ALPHABET[b32value(c)] for c in g)
        if check_char(g[:4], i // 5 + 1) != g[4]:
            return ("bad-group", i // 5 + 1)
        data += g[:4]
    head = b32decode(data, 2)
    n = (head[0] << 8) | head[1]
    full = b32decode(data, 2 + n)
    return ("ok", full[2:]) if full else ("malformed",)


# ------------------------------------------------------------------ activation (cba1)

def right_line_ok(line):
    f = line.split("|")
    return (f[0] == "purchase" and len(f) == 4) or (f[0] == "subscription" and len(f) == 7) or (f[0] == "openall" and len(f) == 4)


def parse_activation(token):
    """Returns (dict, payload_text, signature_bytes) or None. The payload must be EXACTLY the canonical text (rebuilt and compared)."""
    try:
        parts = token.strip().split(".")
        if len(parts) != 3 or parts[0] != "cba1":
            return None
        pad = "=" * (-len(parts[1]) % 4)
        text = base64.urlsafe_b64decode(parts[1] + pad).decode("utf-8")
        sig = base64.b64decode(parts[2], validate=True)
        lines = text.split("\n")
        if lines[0] != "castbridge-activation-v1":
            return None

        def f(i, key):
            assert lines[i].startswith(key + "=")
            return lines[i].split("=", 1)[1]

        a = {"kind": f(1, "kind"), "subject": f(2, "subject"), "kid": f(3, "kid"), "nonce": f(4, "nonce"), "issuedAt": int(f(5, "issuedAt")),
             "notBefore": int(f(6, "notBefore")), "notAfter": int(f(7, "notAfter")), "license": f(8, "license"), "seat": f(9, "seat"), "k": int(f(10, "k")),
             "factors": {}, "rights": []}
        for l in lines[11:]:
            if l.startswith("factor="):
                kind, h = l[7:].split("|")
                assert kind in KINDS and len(h) == 32
                a["factors"][kind] = h
            elif l.startswith("right="):
                assert right_line_ok(l[6:])
                a["rights"].append(l[6:])
            else:
                return None
        if build_activation_text(a) != text:
            return None
        return a, text, sig
    except Exception:
        return None


def build_activation_text(a):
    lines = ["castbridge-activation-v1", "kind=" + a["kind"], "subject=" + a["subject"], "kid=" + a["kid"], "nonce=" + a["nonce"], "issuedAt=%d" % a["issuedAt"],
             "notBefore=%d" % a["notBefore"], "notAfter=%d" % a["notAfter"], "license=" + a["license"], "seat=" + a["seat"], "k=%d" % a["k"]]
    lines += ["factor=%s|%s" % (k, a["factors"][k]) for k in order(a["factors"])]
    lines += ["right=" + r for r in sorted(a["rights"])]
    return "\n".join(lines)


def verify_sig(pub_b64, message, sig):
    try:
        Ed25519PublicKey.from_public_bytes(base64.b64decode(pub_b64)).verify(sig, message)
        return True
    except (InvalidSignature, ValueError):
        return False


def verify_activation(c, keys, devices):
    tk = {keys[n]["kid"]: keys[n] for n in c["trustedKeys"]}
    revoked = {keys[n]["kid"] for n in c["revokedKeys"]}
    seats = {"%s|%s" % (s["license"], s["seat"]): s["at"] for s in c["revokedSeats"]}
    p = parse_activation(c["token"])
    if p is None:
        return ("rejected", "MALFORMED")
    a, text, sig = p
    key = tk.get(a["kid"])
    if key is None:
        return ("rejected", "UNKNOWN_KEY")
    if a["kid"] in revoked:
        return ("rejected", "REVOKED_KEY")
    if not verify_sig(key["publicKey"], text.encode(), sig):
        return ("rejected", "BAD_SIGNATURE")
    scopes = set(key["scopes"])
    if ("ISSUE_TRIAL" if a["kind"] == "trial" else "ISSUE_PRODUCTION") not in scopes:
        return ("rejected", "KEY_NOT_ALLOWED")
    opens = [r.split("|") for r in a["rights"] if r.startswith("openall|")]
    if opens and "COMMAND_OPEN_ALL" not in scopes:
        return ("rejected", "KEY_NOT_ALLOWED")
    if a["kind"] == "trial" and a["rights"]:
        return ("rejected", "BAD_RIGHTS")
    if any(int(o[3]) - int(o[2]) > MAX_OPEN_ALL or int(o[3]) <= int(o[2]) for o in opens):
        return ("rejected", "BAD_RIGHTS")
    if a["subject"] != c["expectSubject"]:
        return ("rejected", "WRONG_SUBJECT")
    if a["notAfter"] - a["notBefore"] > MAX_WINDOW:
        return ("rejected", "WINDOW_TOO_LONG")
    dev = devices[c["device"]]["fingerprints"]
    if not matches(a["factors"], a["k"], dev):
        return ("rejected", "WRONG_DEVICE")
    at = seats.get("%s|%s" % (a["license"], a["seat"]))
    if at is not None and a["issuedAt"] <= at:
        return ("rejected", "REVOKED_SEAT")
    now = max(c["nowMs"], a["issuedAt"])
    if now + DAY < a["notBefore"]:
        return ("rejected", "NOT_YET_VALID")
    if now > a["notAfter"]:
        return ("rejected", "WINDOW_CLOSED")
    return ("accepted", a)


def sign(seed_hex, message):
    return Ed25519PrivateKey.from_private_bytes(bytes.fromhex(seed_hex)).sign(message)


def issue_activation(c, keys, devices):
    r = c["request"]
    key = keys[c["signer"]]
    dev = devices[r["device"]]
    scopes = set(key["scopes"])
    code = parse_code(r.get("deviceCodeOverride") or dev["code"])
    if code is None or code != device_code(dev["fingerprints"]):
        return None
    if not 1 <= r["windowDays"] <= 366:
        return None
    if ("ISSUE_TRIAL" if r["kind"] == "trial" else "ISSUE_PRODUCTION") not in scopes:
        return None
    if r["kind"] == "trial" and (r["rights"] or r["license"] != "trial"):
        return None
    if r["kind"] != "trial" and not r["rights"]:
        return None
    for line in r["rights"]:
        f = line.split("|")
        if f[0] == "openall" and ("COMMAND_OPEN_ALL" not in scopes or int(f[3]) - int(f[2]) > MAX_OPEN_ALL or int(f[3]) <= int(f[2])):
            return None
    fp = dev["fingerprints"]
    seat = r["seat"] or hashlib.sha256(("castbridge-seat|%s|%s" % (r["license"], set_hash(fp).hex())).encode()).digest()[:8].hex()
    a = {"kind": r["kind"], "subject": r["subject"], "kid": key["kid"], "nonce": r["nonce"], "issuedAt": r["issuedAt"], "notBefore": r["notBefore"],
         "notAfter": r["notBefore"] + r["windowDays"] * DAY, "license": r["license"], "seat": seat, "k": k_for(len(fp)), "factors": fp, "rights": r["rights"]}
    text = build_activation_text(a)
    sig = sign(key["seed"], text.encode())
    return "cba1." + base64.urlsafe_b64encode(text.encode()).decode().rstrip("=") + "." + base64.b64encode(sig).decode()


# ------------------------------------------------------------------ compact

def bind_of(code):
    return hashlib.sha256(("castbridge-bind|" + parse_code(code)).encode()).digest()[:8]


def key_tag(kid):
    d = hashlib.sha256(kid.encode()).digest()
    return (d[0] << 8) | d[1]


def compact_header(kind, tag, nb_day, window, set_id, bind):
    return bytes([1, 0 if kind == "trial" else 1, tag >> 8, tag & 255, nb_day >> 8, nb_day & 255, window >> 8, window & 255, set_id >> 8, set_id & 255]) + bind


def compact_signed_text(header):
    return "castbridge-activation-compact-v1\n" + header.hex()


def compact_encode(header, sig):
    raw = b32encode(header + sig)
    raw += "0" * (-len(raw) % 4)
    return "-".join(raw[i:i + 4] + check_char(raw[i:i + 4], i // 4 + 1) for i in range(0, len(raw), 4))


def verify_compact(c, keys):
    chars = "".join(ch for ch in c["text"] if ch != "-" and not ch.isspace())
    if not chars or len(chars) % 5:
        return ("rejected", "MALFORMED")
    data = ""
    for i in range(0, len(chars), 5):
        g = chars[i:i + 5]
        if any(b32value(x) < 0 for x in g):
            return ("rejected", "MALFORMED")
        g = "".join(ALPHABET[b32value(x)] for x in g)
        if check_char(g[:4], i // 5 + 1) != g[4]:
            return ("rejected", "MALFORMED")
        data += g[:4]
    raw = b32decode(data, 82)
    if raw is None or raw[0] != 1 or raw[1] not in (0, 1):
        return ("rejected", "MALFORMED")
    header, sig = raw[:18], raw[18:]
    tag = (header[2] << 8) | header[3]
    tk = [keys[n] for n in c["trustedKeys"]]
    key = next((k for k in tk if key_tag(k["kid"]) == tag), None)
    if key is None:
        return ("rejected", "UNKNOWN_KEY")
    revoked = {keys[n]["kid"] for n in c["revokedKeys"]}
    if key["kid"] in revoked:
        return ("rejected", "REVOKED_KEY")
    if not verify_sig(key["publicKey"], compact_signed_text(header).encode(), sig):
        return ("rejected", "BAD_SIGNATURE")
    if ("ISSUE_TRIAL" if header[1] == 0 else "ISSUE_PRODUCTION") not in key["scopes"]:
        return ("rejected", "KEY_NOT_ALLOWED")
    if header[10:18] != bind_of(c["deviceCode"]):
        return ("rejected", "WRONG_DEVICE")
    start = EPOCH_MS + ((header[4] << 8) | header[5]) * DAY
    window = (header[6] << 8) | header[7]
    if window > 366:
        return ("rejected", "WINDOW_TOO_LONG")
    if c["nowMs"] + DAY < start:
        return ("rejected", "NOT_YET_VALID")
    if c["nowMs"] > start + window * DAY:
        return ("rejected", "WINDOW_CLOSED")
    return ("accepted", "trial" if header[1] == 0 else "production")


# ------------------------------------------------------------------ commands (cbo1)

def parse_command(token):
    try:
        parts = token.strip().split(".")
        if len(parts) != 3 or parts[0] != "cbo1":
            return None
        text = base64.urlsafe_b64decode(parts[1] + "=" * (-len(parts[1]) % 4)).decode()
        sig = base64.b64decode(parts[2], validate=True)
        lines = text.split("\n")
        if lines[0] != "castbridge-owner-command-v1":
            return None
        g = lambda i, k: lines[i].split("=", 1)[1] if lines[i].startswith(k + "=") else (_ for _ in ()).throw(ValueError())
        c = {"kid": g(1, "kid"), "power": g(2, "power"), "action": g(3, "action"), "challenge": g(4, "challenge"), "k": int(g(5, "k")), "factors": {}}
        i = 6
        while lines[i].startswith("factor="):
            kind, h = lines[i][7:].split("|")
            c["factors"][kind] = h
            i += 1
        c["bundles"] = [x for x in g(i, "bundles").split(",") if x]
        c["lots"] = [x for x in g(i + 1, "lots").split(",") if x]
        c["days"] = int(g(i + 2, "days"))
        if build_command_text(c) != text:
            return None
        return c, text, sig
    except Exception:
        return None


def build_command_text(c):
    lines = ["castbridge-owner-command-v1", "kid=" + c["kid"], "power=" + c["power"], "action=" + c["action"], "challenge=" + c["challenge"], "k=%d" % c["k"]]
    lines += ["factor=%s|%s" % (k, c["factors"][k]) for k in order(c["factors"])]
    lines += ["bundles=" + ",".join(sorted(c["bundles"])), "lots=" + ",".join(sorted(c["lots"])), "days=%d" % c["days"]]
    return "\n".join(lines)


POWER_SCOPE = {"support": "COMMAND_SUPPORT", "unlock": "COMMAND_UNLOCK", "open_all": "COMMAND_OPEN_ALL"}
POWER_DAYS = {"support": 0, "unlock": 30, "open_all": 30}


def run_command(c, keys, devices):
    openc = set(c["openChallenges"])
    spent = set()
    tk = {keys[n]["kid"]: keys[n] for n in c["trustedKeys"]}
    revoked = {keys[n]["kid"] for n in c["revokedKeys"]}
    out = []
    for step in c["steps"]:
        p = parse_command(step["token"])
        if p is None:
            out.append(("rejected", "MALFORMED")); continue
        cmd, text, sig = p
        key = tk.get(cmd["kid"])
        if key is None:
            out.append(("rejected", "UNKNOWN_KEY")); continue
        if cmd["kid"] in revoked:
            out.append(("rejected", "REVOKED_KEY")); continue
        if not verify_sig(key["publicKey"], text.encode(), sig):
            out.append(("rejected", "BAD_SIGNATURE")); continue
        if POWER_SCOPE[cmd["power"]] not in key["scopes"]:
            out.append(("rejected", "KEY_NOT_ALLOWED")); continue
        if not matches(cmd["factors"], cmd["k"], devices[c["device"]]["fingerprints"]):
            out.append(("rejected", "WRONG_DEVICE")); continue
        if cmd["power"] == "support" and cmd["action"] not in ("diagnostic", "reset-trial"):
            out.append(("rejected", "BAD_COMMAND")); continue
        if cmd["power"] == "unlock" and not cmd["bundles"] and not cmd["lots"]:
            out.append(("rejected", "BAD_COMMAND")); continue
        if cmd["power"] != "support" and cmd["days"] < 1:
            out.append(("rejected", "BAD_COMMAND")); continue
        if cmd["challenge"] in spent or cmd["challenge"] not in openc:
            out.append(("rejected", "REPLAY")); continue
        openc.discard(cmd["challenge"]); spent.add(cmd["challenge"])
        days = min(cmd["days"], POWER_DAYS[cmd["power"]])
        until = 0 if cmd["power"] == "support" else step["wallMs"] + days * DAY
        out.append(("accepted", until))
    return out


def issue_command(c, keys, devices):
    r = c["request"]
    key = keys[c["signer"]]
    fp = devices[r["device"]]["fingerprints"]
    cmd = {"kid": key["kid"], "power": r["power"], "action": r["action"], "challenge": r["challenge"], "k": k_for(len(fp)), "factors": fp, "bundles": r["bundles"], "lots": r["lots"], "days": r["days"]}
    text = build_command_text(cmd)
    return "cbo1." + base64.urlsafe_b64encode(text.encode()).decode().rstrip("=") + "." + base64.b64encode(sign(key["seed"], text.encode())).decode()


def issue_compact(c, keys):
    r = c["request"]
    key = keys[c["signer"]]
    header = compact_header(r["kind"], key_tag(key["kid"]), r["notBeforeDay"], r["windowDays"], r["setId"], bind_of(r["deviceCode"]))
    return compact_encode(header, sign(key["seed"], compact_signed_text(header).encode()))


def main():
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "test-vectors.json")
    v = json.load(open(path, encoding="utf-8"))
    keys = {k["name"]: k for k in v["keys"]}
    for k in keys.values():       # the seeds are TEST keys: check that the published key is the one of the seed
        pub = Ed25519PrivateKey.from_private_bytes(bytes.fromhex(k["seed"])).public_key().public_bytes_raw()
        assert base64.b64encode(pub).decode() == k["publicKey"], "clé publique incohérente : " + k["name"]
        assert hashlib.sha256(pub).digest()[:8].hex() == k["kid"], "kid incohérent : " + k["name"]
    devices = {d["name"]: d for d in v["devices"]}
    failures, n = [], 0

    def check(cid, ok, detail=""):
        nonlocal n
        n += 1
        if not ok:
            failures.append("%s %s" % (cid, detail))

    for d in devices.values():
        check("device-" + d["name"], fingerprints(d["raw"]) == d["fingerprints"] and device_code(d["fingerprints"]) == d["code"])
    for c in v["cases"]:
        t, cid, e = c["type"], c["id"], c.get("expect")
        if t == "fingerprints":
            fp = fingerprints(c["raw"])
            check(cid, fp == e["fingerprints"] and (not fp or (device_code(fp) == e["code"] and set_hash(fp).hex() == e["setHash"])) and (not fp or k_for(len(fp)) == e["k"]))
        elif t == "base32":
            check(cid, b32encode(bytes.fromhex(c["bytesHex"])) == c["expectText"])
        elif t == "grouped":
            check(cid, grouped_encode(bytes.fromhex(c["bytesHex"])) == c["expectText"])
        elif t == "grouped-decode":
            r = grouped_decode(c["text"])
            check(cid, (r[0] == "ok" and e["result"] == "ok" and r[1].hex() == e["bytesHex"]) or (r[0] == "bad-group" and e["result"] == "bad-group" and r[1] == e["group"]), str(r))
        elif t == "device-code-parse":
            p = parse_code(c["text"])
            check(cid, (e["result"] == "ok" and p == e["code"]) or (e["result"] == "invalid" and p is None))
        elif t == "activation":
            r = verify_activation(c, keys, devices)
            check(cid, (r[0] == "accepted" and e["result"] == "accepted" and r[1]["license"] == e["license"] and r[1]["seat"] == e["seat"]) or (r[0] == "rejected" and e["result"] == "rejected" and r[1] == e["reason"]), str(r[:2]) + " attendu " + str(e))
        elif t == "compact":
            r = verify_compact(c, keys)
            check(cid, (r[0] == "accepted" and e["result"] == "accepted") or (r[0] == "rejected" and e["result"] == "rejected" and r[1] == e["reason"]), str(r) + " attendu " + str(e))
        elif t == "command":
            res = run_command(c, keys, devices)
            ok = len(res) == len(c["steps"])
            for r, step in zip(res, c["steps"]):
                se = step["expect"]
                ok = ok and ((r[0] == "accepted" and se["result"] == "accepted" and r[1] == se["untilMs"]) or (r[0] == "rejected" and se["result"] == "rejected" and r[1] == se["reason"]))
            check(cid, ok, str(res))
        elif t == "build-activation":
            tok = issue_activation(c, keys, devices)
            check(cid, (e.get("refused") and tok is None) or (tok == e.get("token")), "jeton différent" if tok else "refusé")
        elif t == "build-compact":
            check(cid, issue_compact(c, keys) == e["text"])
        elif t == "build-command":
            check(cid, issue_command(c, keys, devices) == e["token"])
        else:
            check(cid, False, "type inconnu " + t)
    print("%d contrôles, %d échecs" % (n, len(failures)))
    for f in failures:
        print("ÉCHEC :", f)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
