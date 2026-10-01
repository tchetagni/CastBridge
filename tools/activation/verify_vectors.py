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
HOUR = 3600000
MAX_WINDOW = 48 * HOUR              # an activation can be installed during 48 h from its creation
UNLIMITED_NOT_AFTER = 253402300799000   # 9999-12-31T23:59:59Z: needs the ISSUE_UNLIMITED scope
UNLIMITED_UNITS = 0xFFFF
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


# ------------------------------------------------------------------ enveloppe (cbx1), activation

def envelope_text(e):
    t = e["target"]
    lines = ["castbridge-envelope-v1", "type=" + e["type"], "kid=" + e["kid"], "seq=%d" % e["seq"], "nonce=" + e["nonce"], "issuedAt=%d" % e["issuedAt"],
             "notBefore=%d" % e["notBefore"], "expiresAt=%d" % e["expiresAt"]]
    if t["kind"] == "any":
        lines.append("target=any")
    elif t["kind"] == "device":
        lines += ["target=device", "k=%d" % t["k"]] + ["factor=%s|%s" % (k, t["factors"][k]) for k in order(t["factors"])]
    else:
        lines.append("target=%s:%s" % (t["kind"], t["id"]))
    lines.append("--")
    return "\n".join(lines + e["body"])


def parse_envelope(token):
    """(env, texte, signature) ou None. Le texte est reconstruit à partir des champs et doit être identique à celui reçu."""
    try:
        parts = token.strip().split(".")
        if len(parts) != 3 or parts[0] != "cbx1":
            return None
        text = base64.urlsafe_b64decode(parts[1] + "=" * (-len(parts[1]) % 4)).decode("utf-8")
        sig = base64.b64decode(parts[2], validate=True)
        lines = text.split("\n")
        assert lines[0] == "castbridge-envelope-v1"

        def f(i, key):
            assert lines[i].startswith(key + "=")
            return lines[i].split("=", 1)[1]

        e = {"type": f(1, "type"), "kid": f(2, "kid"), "seq": int(f(3, "seq")), "nonce": f(4, "nonce"), "issuedAt": int(f(5, "issuedAt")),
             "notBefore": int(f(6, "notBefore")), "expiresAt": int(f(7, "expiresAt"))}
        t = f(8, "target")
        i = 9
        if t == "any":
            e["target"] = {"kind": "any"}
        elif t == "device":
            k = int(f(i, "k"))
            i += 1
            factors = {}
            while lines[i].startswith("factor="):
                kind, h = lines[i][7:].split("|")
                assert kind in KINDS and len(h) == 32
                factors[kind] = h
                i += 1
            e["target"] = {"kind": "device", "k": k, "factors": factors}
        elif t.startswith("license:") or t.startswith("group:"):
            kind, ident = t.split(":", 1)
            e["target"] = {"kind": kind, "id": ident}
        else:
            return None
        assert lines[i] == "--"
        e["body"] = lines[i + 1:]
        if envelope_text(e) != text:
            return None
        return e, text, sig
    except Exception:
        return None


def right_line_ok(line):
    f = line.split("|")
    return (f[0] == "purchase" and len(f) == 4) or (f[0] == "subscription" and len(f) == 7) or (f[0] == "openall" and len(f) == 4)


def activation_view(e):
    try:
        if e["type"] != "activation" or e["target"]["kind"] != "device":
            return None
        b = e["body"]
        g = lambda i, k: b[i].split("=", 1)[1] if b[i].startswith(k + "=") else (_ for _ in ()).throw(ValueError())
        a = {"kind": g(0, "kind"), "subject": g(1, "subject"), "license": g(2, "license"), "seat": g(3, "seat"), "rights": []}
        for l in b[4:]:
            assert l.startswith("right=") and right_line_ok(l[6:])
            a["rights"].append(l[6:])
        if a["rights"] != sorted(a["rights"]):
            return None
        return a
    except Exception:
        return None


def activation_body(kind, subject, license, seat, rights):
    return ["kind=" + kind, "subject=" + subject, "license=" + license, "seat=" + seat] + ["right=" + r for r in sorted(rights)]


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
    last = {keys[n]["kid"]: v for n, v in c["lastSeq"].items()}
    p = parse_envelope(c["token"])
    if p is None:
        return ("rejected", "MALFORMED")
    e, text, sig = p
    if e["type"] != "activation":
        return ("rejected", "UNKNOWN_TYPE")
    a = activation_view(e)
    if a is None:
        return ("rejected", "MALFORMED")
    key = tk.get(e["kid"])
    if key is None:
        return ("rejected", "UNKNOWN_KEY")
    if e["kid"] in revoked:
        return ("rejected", "REVOKED_KEY")
    if not verify_sig(key["publicKey"], text.encode(), sig):
        return ("rejected", "BAD_SIGNATURE")
    scopes = set(key["scopes"])
    if a["kind"] == "trial":
        allowed = "ISSUE_TRIAL" in scopes
    else:
        allowed = "ISSUE_PRODUCTION" in scopes or "REACTIVATE" in scopes
    if not allowed:
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
    if e["expiresAt"] == UNLIMITED_NOT_AFTER:
        if "ISSUE_UNLIMITED" not in scopes:
            return ("rejected", "KEY_NOT_ALLOWED")
    elif e["expiresAt"] - e["notBefore"] > MAX_WINDOW:
        return ("rejected", "WINDOW_TOO_LONG")
    dev = devices[c["device"]]["fingerprints"]
    t = e["target"]
    if not matches(t["factors"], t["k"], dev):
        return ("rejected", "WRONG_DEVICE")
    at = seats.get("%s|%s" % (a["license"], a["seat"]))
    if at is not None and e["issuedAt"] <= at:
        return ("rejected", "REVOKED_SEAT")
    if e["seq"] < last.get(e["kid"], 0):
        return ("rejected", "STALE_SEQUENCE")
    now = max(c["nowMs"], e["issuedAt"])
    if now + DAY < e["notBefore"]:
        return ("rejected", "NOT_YET_VALID")
    if now > e["expiresAt"]:
        return ("rejected", "WINDOW_CLOSED")
    return ("accepted", {"license": a["license"], "seat": a["seat"]})


def sign(seed_hex, message):
    return Ed25519PrivateKey.from_private_bytes(bytes.fromhex(seed_hex)).sign(message)


def finish(env, key):
    text = envelope_text(env)
    return "cbx1." + base64.urlsafe_b64encode(text.encode()).decode().rstrip("=") + "." + base64.b64encode(sign(key["seed"], text.encode())).decode()


def issue_activation(c, keys, devices):
    r = c["request"]
    key = keys[c["signer"]]
    dev = devices[r["device"]]
    scopes = set(key["scopes"])
    code = parse_code(r.get("deviceCodeOverride") or dev["code"])
    if code is None or code != device_code(dev["fingerprints"]):
        return None
    unlimited = bool(r.get("unlimited"))
    if unlimited:
        if "ISSUE_UNLIMITED" not in scopes:
            return None
    elif not 1 <= r["windowHours"] <= 48:
        return None
    if r["kind"] == "trial":
        if "ISSUE_TRIAL" not in scopes or r["rights"] or r["license"] != "trial":
            return None
    else:
        if not ({"ISSUE_PRODUCTION", "REACTIVATE"} & scopes) or not r["rights"]:
            return None
    for line in r["rights"]:
        f = line.split("|")
        if f[0] == "openall" and ("COMMAND_OPEN_ALL" not in scopes or int(f[3]) - int(f[2]) > MAX_OPEN_ALL or int(f[3]) <= int(f[2])):
            return None
    fp = dev["fingerprints"]
    seat = r["seat"] or hashlib.sha256(("castbridge-seat|%s|%s" % (r["license"], set_hash(fp).hex())).encode()).digest()[:8].hex()
    env = {"type": "activation", "kid": key["kid"], "seq": r["seq"] if r.get("seq") is not None else r["issuedAt"], "nonce": r["nonce"], "issuedAt": r["issuedAt"], "notBefore": r["notBefore"],
           "expiresAt": UNLIMITED_NOT_AFTER if unlimited else r["notBefore"] + r["windowHours"] * HOUR, "target": {"kind": "device", "k": k_for(len(fp)), "factors": fp},
           "body": activation_body(r["kind"], r["subject"], r["license"], seat, r["rights"])}
    return finish(env, key)


# ------------------------------------------------------------------ compact

def bind_of(code):
    return hashlib.sha256(("castbridge-bind|" + parse_code(code)).encode()).digest()[:8]


def key_tag(kid):
    d = hashlib.sha256(kid.encode()).digest()
    return (d[0] << 8) | d[1]


def compact_header(kind, tag, nb_day, window, set_id, bind, version=2):
    return bytes([version, 0 if kind == "trial" else 1, tag >> 8, tag & 255, nb_day >> 8, nb_day & 255, window >> 8, window & 255, set_id >> 8, set_id & 255]) + bind


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
    if raw is None or raw[0] not in (1, 2) or raw[1] not in (0, 1):
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
    unit = DAY if header[0] == 1 else HOUR           # version 1 counted days (old keys), version 2 counts hours (exact 48 h)
    start = EPOCH_MS + ((header[4] << 8) | header[5]) * unit
    window = (header[6] << 8) | header[7]
    unlimited = header[0] == 2 and window == UNLIMITED_UNITS
    if unlimited and "ISSUE_UNLIMITED" not in key["scopes"]:
        return ("rejected", "KEY_NOT_ALLOWED")
    end = UNLIMITED_NOT_AFTER if unlimited else start + window * unit
    if not unlimited and end - start > MAX_WINDOW:
        return ("rejected", "WINDOW_TOO_LONG")
    if c["nowMs"] + HOUR < start:
        return ("rejected", "NOT_YET_VALID")
    if c["nowMs"] > end:
        return ("rejected", "WINDOW_CLOSED")
    return ("accepted", "trial" if header[1] == 0 else "production")


# ------------------------------------------------------------------ commandes du propriétaire (type command)

POWER_SCOPE = {"support": "COMMAND_SUPPORT", "unlock": "COMMAND_UNLOCK", "open_all": "COMMAND_OPEN_ALL"}
POWER_DAYS = {"support": 0, "unlock": 30, "open_all": 30}


def command_view(e):
    try:
        if e["type"] != "command" or e["target"]["kind"] != "device" or len(e["body"]) != 5:
            return None
        if e["notBefore"] != e["issuedAt"] or e["expiresAt"] != e["issuedAt"] + DAY:
            return None
        g = lambda i, k: e["body"][i].split("=", 1)[1] if e["body"][i].startswith(k + "=") else (_ for _ in ()).throw(ValueError())
        return {"power": g(0, "power"), "action": g(1, "action"), "bundles": [x for x in g(2, "bundles").split(",") if x], "lots": [x for x in g(3, "lots").split(",") if x], "days": int(g(4, "days"))}
    except Exception:
        return None


def run_command(c, keys, devices):
    openc = set(c["openChallenges"])
    spent = set()
    tk = {keys[n]["kid"]: keys[n] for n in c["trustedKeys"]}
    revoked = {keys[n]["kid"] for n in c["revokedKeys"]}
    out = []
    for step in c["steps"]:
        p = parse_envelope(step["token"])
        if p is None:
            out.append(("rejected", "MALFORMED")); continue
        e, text, sig = p
        if e["type"] != "command":
            out.append(("rejected", "UNKNOWN_TYPE")); continue
        cmd = command_view(e)
        if cmd is None:
            out.append(("rejected", "MALFORMED")); continue
        key = tk.get(e["kid"])
        if key is None:
            out.append(("rejected", "UNKNOWN_KEY")); continue
        if e["kid"] in revoked:
            out.append(("rejected", "REVOKED_KEY")); continue
        if not verify_sig(key["publicKey"], text.encode(), sig):
            out.append(("rejected", "BAD_SIGNATURE")); continue
        if POWER_SCOPE[cmd["power"]] not in key["scopes"]:
            out.append(("rejected", "KEY_NOT_ALLOWED")); continue
        t = e["target"]
        if not matches(t["factors"], t["k"], devices[c["device"]]["fingerprints"]):
            out.append(("rejected", "WRONG_DEVICE")); continue
        if cmd["power"] == "support" and cmd["action"] not in ("diagnostic", "reset-trial"):
            out.append(("rejected", "BAD_COMMAND")); continue
        if cmd["power"] == "unlock" and not cmd["bundles"] and not cmd["lots"]:
            out.append(("rejected", "BAD_COMMAND")); continue
        if cmd["power"] != "support" and cmd["days"] < 1:
            out.append(("rejected", "BAD_COMMAND")); continue
        ch = e["nonce"]
        if ch in spent or ch not in openc:
            out.append(("rejected", "REPLAY")); continue
        openc.discard(ch); spent.add(ch)
        days = min(cmd["days"], POWER_DAYS[cmd["power"]])
        until = 0 if cmd["power"] == "support" else step["wallMs"] + days * DAY
        out.append(("accepted", until))
    return out


def issue_command(c, keys, devices):
    r = c["request"]
    key = keys[c["signer"]]
    fp = devices[r["device"]]["fingerprints"]
    env = {"type": "command", "kid": key["kid"], "seq": r["issuedAt"], "nonce": r["challenge"], "issuedAt": r["issuedAt"], "notBefore": r["issuedAt"], "expiresAt": r["issuedAt"] + DAY,
           "target": {"kind": "device", "k": k_for(len(fp)), "factors": fp},
           "body": ["power=" + r["power"], "action=" + r["action"], "bundles=" + ",".join(sorted(r["bundles"])), "lots=" + ",".join(sorted(r["lots"])), "days=%d" % r["days"]]}
    return finish(env, key)


def issue_compact(c, keys):
    r = c["request"]
    key = keys[c["signer"]]
    window = UNLIMITED_UNITS if r.get("unlimited") else r["windowHours"]
    header = compact_header(r["kind"], key_tag(key["kid"]), r["notBeforeHour"], window, r["setId"], bind_of(r["deviceCode"]))
    return compact_encode(header, sign(key["seed"], compact_signed_text(header).encode()))


# ------------------------------------------------------------------ ordres différés (type order) et listes de révocation (type revocation)

import re
ACTION_RE = re.compile(r"^[a-z][a-z0-9_.-]{0,47}$")
PARAM_RE = re.compile(r"^[a-z][a-z0-9_.-]{0,31}$")


def order_view(e):
    try:
        if e["type"] != "order":
            return None
        b = e["body"]
        assert b[0].startswith("action=")
        action = b[0][7:]
        params = {}
        for l in b[1:]:
            assert l.startswith("param=")
            name, _, value = l[6:].partition("|")
            params[name] = value
        assert ACTION_RE.match(action) and len(params) <= 16 and all(PARAM_RE.match(k) and len(v) <= 512 and "\n" not in v for k, v in params.items())
        assert b[1:] == ["param=%s|%s" % (k, params[k]) for k in sorted(params)]
        return {"action": action, "params": params}
    except Exception:
        return None


def verify_order(c, keys, devices):
    tk = {keys[n]["kid"]: keys[n] for n in c["trustedKeys"]}
    revoked = {keys[n]["kid"] for n in c["revokedKeys"]}
    last = {keys[n]["kid"]: v for n, v in c["lastSeq"].items()}
    p = parse_envelope(c["token"])
    if p is None:
        return ("rejected", "MALFORMED")
    e, text, sig = p
    if e["type"] != "order":
        return ("rejected", "UNKNOWN_TYPE")
    key = tk.get(e["kid"])
    if key is None:
        return ("rejected", "UNKNOWN_KEY")
    if e["kid"] in revoked:
        return ("rejected", "REVOKED_KEY")
    if not verify_sig(key["publicKey"], text.encode(), sig):
        return ("rejected", "BAD_SIGNATURE")
    if "POLICY" not in key["scopes"]:
        return ("rejected", "KEY_NOT_ALLOWED")
    o = order_view(e)
    if o is None:
        return ("rejected", "BAD_ORDER")
    t = e["target"]
    ok = (t["kind"] == "any" or (t["kind"] == "device" and matches(t["factors"], t["k"], devices[c["device"]]["fingerprints"]))
          or (t["kind"] == "license" and t["id"] in c["deviceLicenses"]) or (t["kind"] == "group" and t["id"] in c["deviceGroups"]))
    if not ok:
        return ("rejected", "WRONG_TARGET")
    if e["seq"] <= last.get(e["kid"], 0):
        return ("rejected", "STALE_SEQUENCE")
    if c["nowMs"] + DAY < e["notBefore"]:
        return ("rejected", "NOT_YET_VALID")
    if c["nowMs"] > e["expiresAt"]:
        return ("rejected", "WINDOW_CLOSED")
    return ("accepted", o, e["seq"])


def issue_order(c, keys):
    r = c["request"]
    key = keys[c["signer"]]
    if not ACTION_RE.match(r["action"]) or len(r["params"]) > 16 or r["expiresAt"] <= r["notBefore"]:
        return None
    env = {"type": "order", "kid": key["kid"], "seq": r["seq"], "nonce": r["nonce"], "issuedAt": r["issuedAt"], "notBefore": r["notBefore"], "expiresAt": r["expiresAt"], "target": {"kind": "any"},
           "body": ["action=" + r["action"]] + ["param=%s|%s" % (k, r["params"][k]) for k in sorted(r["params"])]}
    return finish(env, key)


def revocation_body(keys_list, seats):
    return ["key=" + k for k in sorted(keys_list)] + ["seat=%s|%d" % (k, seats[k]) for k in sorted(seats)]


def verify_revocation(c, keys):
    tk = {keys[n]["kid"]: keys[n] for n in c["trustedKeys"]}
    p = parse_envelope(c["token"])
    if p is None:
        return None
    e, text, sig = p
    if e["type"] != "revocation" or e["target"]["kind"] != "any":
        return None
    key = tk.get(e["kid"])
    if key is None or "REVOKE" not in key["scopes"] or not verify_sig(key["publicKey"], text.encode(), sig):
        return None
    ks = [l[4:] for l in e["body"] if l.startswith("key=")]
    seats = {}
    for l in e["body"]:
        if l.startswith("seat="):
            lic, seat, at = l[5:].split("|")
            seats["%s|%s" % (lic, seat)] = int(at)
    if len(e["body"]) != len(ks) + len(seats) or e["body"] != revocation_body(ks, seats):
        return None
    return {"keys": sorted(ks), "seats": seats}


def issue_revocation(c, keys):
    r = c["request"]
    key = keys[c["signer"]]
    seats = {k: int(v) for k, v in r["seats"].items()}
    env = {"type": "revocation", "kid": key["kid"], "seq": r["at"], "nonce": "00000000" + format(r["at"], "x").rjust(8, "0"), "issuedAt": r["at"], "notBefore": r["at"], "expiresAt": r["at"] + 366 * DAY,
           "target": {"kind": "any"}, "body": revocation_body(r["keys"], seats)}
    return finish(env, key)


# ------------------------------------------------------------------ licences (registre d'événements signés)

YEAR = 365 * DAY


def parse_event(e):
    lines = e["text"].split("\n")
    if lines[0] != "castbridge-licence-event-v1":
        return None
    f, factors = {}, {}
    for l in lines[1:]:
        if l.startswith("factor="):
            kind, h = l[7:].split("|")
            factors[kind] = h
        else:
            f[l.split("=", 1)[0]] = l.split("=", 1)[1]
    eid = hashlib.sha256((e["kid"] + "|" + e["text"]).encode()).digest()[:8].hex()
    if e.get("id") not in (None, eid):
        return None
    return {"id": eid, "kid": e["kid"], "text": e["text"], "signature": e["signature"], "f": f, "factors": factors, "at": int(f.get("at", "0"))}


def replay_licences(raw_events, keys_by_kid):
    licenses, seats, alias, transfers, duplicates, warnings, rejected = {}, {}, {}, [], [], [], []
    rev_seats = {}
    seen = set()
    events = [x for x in (parse_event(e) for e in raw_events) if x]
    for e in sorted(events, key=lambda x: (x["at"], x["id"])):
        if e["id"] in seen:
            continue
        seen.add(e["id"])
        key = keys_by_kid.get(e["kid"])
        t = e["f"].get("type")
        scope = {"license": None, "issue": None, "transfer": "TRANSFER", "revoke": "REVOKE"}.get(t, "?")
        if t in ("license", "issue"):
            scope = "ISSUE_TRIAL" if e["f"].get("kind") == "trial" else "ISSUE_PRODUCTION"
        if key is None:
            rejected.append("UNKNOWN_KEY"); continue
        if not verify_sig(key["publicKey"], e["text"].encode(), base64.b64decode(e["signature"])):
            rejected.append("BAD_SIGNATURE"); continue
        if scope == "?":
            rejected.append("MALFORMED"); continue
        react_only = t == "issue" and "ISSUE_PRODUCTION" not in key["scopes"] and "REACTIVATE" in key["scopes"] and e["f"].get("kind") != "trial"
        if scope not in key["scopes"] and not react_only:
            rejected.append("KEY_NOT_ALLOWED"); continue
        f = e["f"]
        if t == "license":
            licenses.setdefault(f["license"], {"seats": int(f.get("seats", 0)), "cap": int(f.get("maxTransfersPerYear", 2))})
        elif t == "issue":
            lic, seat = f["license"], f["seat"]
            if lic == "trial":
                continue
            if lic not in licenses:
                rejected.append("UNKNOWN_LICENSE"); continue
            subject, k = f.get("subject", "tv"), int(f.get("k", 1))
            ko = "%s|%s" % (lic, alias.get("%s|%s" % (lic, seat), seat))
            if ko in seats:
                seats[ko]["last"] = max(seats[ko]["last"], e["at"]); continue
            same = next((s for s in seats.values() if s["license"] == lic and s["subject"] == subject and matches(s["factors"], s["k"], e["factors"])), None)
            if same:
                duplicates.append(("%s|%s" % (lic, same["seat"]), "%s|%s" % (lic, seat)))
                alias["%s|%s" % (lic, seat)] = same["seat"]
                continue
            if react_only:
                rejected.append("KEY_NOT_ALLOWED"); continue
            if sum(1 for s in seats.values() if s["license"] == lic) >= licenses[lic]["seats"]:
                warnings.append(lic)
            seats[ko] = {"license": lic, "seat": seat, "subject": subject, "factors": e["factors"], "k": k, "last": e["at"]}
        elif t == "transfer":
            lic = f["license"]
            seat = alias.get("%s|%s" % (lic, f["seat"]), f["seat"])
            cur = seats.get("%s|%s" % (lic, seat))
            if lic not in licenses or cur is None:
                rejected.append("UNKNOWN_LICENSE"); continue
            if sum(1 for x in transfers if x[0] == lic and e["at"] - YEAR < x[2] <= e["at"]) >= licenses[lic]["cap"]:
                rejected.append("TRANSFER_LIMIT"); continue
            cur["factors"], cur["k"] = e["factors"], int(f.get("k", cur["k"]))
            transfers.append((lic, seat, e["at"]))
            rev_seats["%s|%s" % (lic, seat)] = max(rev_seats.get("%s|%s" % (lic, seat), 0), e["at"])
        elif t == "revoke" and f.get("target") == "seat":
            rev_seats[f["value"]] = max(rev_seats.get(f["value"], 0), e["at"])
    return {"licenses": licenses, "seats": seats, "transfers": transfers, "duplicates": duplicates, "warnings": warnings, "rejected": rejected, "revokedSeats": rev_seats}


def plan_licence(st, lic, subject, fp):
    if lic not in st["licenses"]:
        return ("refused", "UNKNOWN_LICENSE")
    for s in st["seats"].values():
        if s["license"] == lic and s["subject"] == subject and matches(s["factors"], s["k"], fp):
            return ("reuse", s["seat"])
    used = sum(1 for s in st["seats"].values() if s["license"] == lic)
    left = st["licenses"][lic]["seats"] - used
    if left <= 0:
        return ("refused", "NO_SEAT_LEFT")
    return ("new", hashlib.sha256(("castbridge-seat|%s|%s" % (lic, set_hash(fp).hex())).encode()).digest()[:8].hex(), left - 1)


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
        elif t == "licence":
            tk = {keys[n]["kid"]: keys[n] for n in c["trustedKeys"]}
            st = replay_licences(c["events"], tk)
            ok = sorted(st["seats"]) == e["seats"] and len(st["transfers"]) == e["transfers"] and len(st["duplicates"]) == e["duplicates"] and sorted(st["rejected"]) == e["rejected"]
            ok = ok and len(st["warnings"]) == e["warnings"] and {l: sum(1 for s in st["seats"].values() if s["license"] == l) for l in sorted(st["licenses"])} == e["used"]
            ok = ok and {k: v for k, v in sorted(st["revokedSeats"].items())} == e["revokedSeats"]
            for p in c["plans"]:
                r = plan_licence(st, p["license"], p["subject"], devices[p["device"]]["fingerprints"])
                pe = p["expect"]
                ok = ok and ((pe["plan"] == "reuse" and r[0] == "reuse" and r[1] == pe["seat"]) or (pe["plan"] == "new" and r[0] == "new" and r[1] == pe["seat"] and r[2] == pe["left"])
                             or (pe["plan"] == "refused" and r[0] == "refused" and r[1] == pe["reason"]))
            check(cid, ok, str(st["rejected"]))
        elif t == "order":
            r = verify_order(c, keys, devices)
            check(cid, (r[0] == "accepted" and e["result"] == "accepted" and r[1]["action"] == e["action"] and r[1]["params"] == e["params"] and r[2] == e["seq"]) or (r[0] == "rejected" and e["result"] == "rejected" and r[1] == e["reason"]), str(r) + " attendu " + str(e))
        elif t == "revocation":
            r = verify_revocation(c, keys)
            check(cid, (r is None and e["result"] == "rejected") or (r is not None and e["result"] == "accepted" and r["keys"] == e["keys"] and r["seats"] == e["seats"]), str(r))
        elif t == "build-order":
            check(cid, (e.get("refused") and issue_order(c, keys) is None) or issue_order(c, keys) == e.get("token"))
        elif t == "build-revocation":
            check(cid, issue_revocation(c, keys) == e["token"])
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
