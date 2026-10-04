#!/usr/bin/env python3
"""Vérificateur de référence INDÉPENDANT de Kotlin : rejoue tools/activation/test-vectors.json, rental-vectors.json et server-issued.json avec la seule spécification docs/ACTIVATION-FORMAT.md
(et docs/RENTAL-LOTS.md § 3 et 10 pour la clé de location, l'enveloppe par TV et le lot chiffré).

    python3 tools/activation/verify_vectors.py            # exit 0 si tous les vecteurs passent

Sert de preuve que la spécification suffit à écrire un émetteur ou un vérificateur (bureau, serveur, téléphone) sans lire le code Kotlin, et de modèle de départ
pour ces outils. Dépendance : le paquet `cryptography` (Ed25519).
"""
import base64
import hashlib
import hmac
import itertools
import json
import os
import re
import sys

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
KINDS = {"FLASH": (0, True), "ETHERNET": (1, True), "WIFI": (2, False), "SYSTEM_SERIAL": (3, False), "BLUETOOTH": (4, False)}
SALT = "castbridge-device-v1"
DAY = 86400000
EPOCH_MS = 1767225600000
HOUR = 3600000
MAX_WINDOW = 48 * HOUR              # an activation can be installed during 48 h from its creation
def is_super(line):                 # the `super` right (SUPER_UNLIMITED): only the super administrator's key may sign it
    return line.split("|")[0] == "super"
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


ID_RE = re.compile(r"^[a-z0-9][a-z0-9-]{0,63}$")
BOX_RE = re.compile(r"^[A-Za-z0-9_;:+-]{0,4096}$")
TRIAL_PRODUCT = "essai"


def rental_ok(f):
    try:
        if len(f) != 10 or not ID_RE.match(f[1]) or not BOX_RE.match(f[9]):
            return False
        if f[2] and not all(ID_RE.match(b) for b in f[2].split(",")):
            return False
        for i in (3, 4, 5, 6, 7, 8):
            int(f[i])
        return True
    except ValueError:
        return False


def rental_bounds_ok(line):
    """Mirror of RentalLines.bounds: True when the (well-formed) rental line is within bounds."""
    f = line.split("|")
    starts, period, days, grace, usage, conc = int(f[3]), int(f[4]), int(f[5]), int(f[6]), int(f[7]), int(f[8])
    if f[1] == TRIAL_PRODUCT and (not 1 <= days <= 3 or not 1 <= usage <= 720 or grace != 0):
        return False
    return (1 <= days <= 366 and 0 <= grace <= 30 * DAY and 0 <= usage <= 366 * 24 * 60 and 0 <= conc <= 20
            and starts > 0 and 0 < period <= starts and f[2] != "")


TRIAL_DEFAULT_DAYS = 30


def implicit_usage_end(kind, rights, issued_at, not_before):
    """Mirror of Activation.implicitUsageEnd: a TRIAL activation without a `usage` right ends 30 days after its issue (a compact key has no issue time: its notBefore).
    A production activation without usage stays unlimited (None); an explicit usage right always wins (None here: its own `to` applies)."""
    if kind != "trial" or any(r.startswith("usage|") for r in rights):
        return None
    return (issued_at if issued_at > 0 else not_before) + TRIAL_DEFAULT_DAYS * DAY


def trial_right_ok(line):
    return line.startswith("usage|") or (line.startswith("rental|") and line.split("|")[1] == TRIAL_PRODUCT)


KIND_NAME_RE = re.compile(r"^[a-z][a-z0-9-]{0,31}$")
KNOWN_KINDS = ("rental", "purchase", "subscription", "openall", "super", "usage")


def right_line_ok(line):
    f = line.split("|")
    if f[0] not in KNOWN_KINDS:      # a right of another kind is kept verbatim and grants nothing (docs/RENTAL-LOTS.md 1.2 and 10.1; RentalLines.unknown)
        return bool(KIND_NAME_RE.match(f[0])) and "\n" not in line
    if f[0] == "rental":
        return rental_ok(f)
    return (f[0] == "purchase" and len(f) == 4) or (f[0] == "subscription" and len(f) == 7) or (f[0] == "openall" and len(f) == 4) or (f[0] == "super" and len(f) == 3) or (f[0] == "usage" and len(f) == 4 and f[1] == "duree")


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
    if any(is_super(r) for r in a["rights"]) and "SUPER_UNLIMITED" not in scopes:
        return ("rejected", "KEY_NOT_ALLOWED")
    if a["kind"] == "trial" and any(not trial_right_ok(r) for r in a["rights"]):
        return ("rejected", "BAD_RIGHTS")
    if any(r.startswith("rental|") and not rental_bounds_ok(r) for r in a["rights"]):
        return ("rejected", "BAD_RIGHTS")
    if any(int(o[3]) - int(o[2]) > MAX_OPEN_ALL or int(o[3]) <= int(o[2]) for o in opens):
        return ("rejected", "BAD_RIGHTS")
    if a["subject"] != c["expectSubject"]:
        return ("rejected", "WRONG_SUBJECT")
    if e["expiresAt"] - e["notBefore"] > MAX_WINDOW:
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
    return ("accepted", {"license": a["license"], "seat": a["seat"], "usageEnd": implicit_usage_end(a["kind"], a["rights"], e["issuedAt"], e["notBefore"])})


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
    if not 1 <= r["windowHours"] <= 48:
        return None
    if any(is_super(line) for line in r["rights"]) and "SUPER_UNLIMITED" not in scopes:
        return None
    if r["kind"] == "trial":
        if "ISSUE_TRIAL" not in scopes or not all(trial_right_ok(x) for x in r["rights"]) or r["license"] != "trial":
            return None
    else:
        if not ({"ISSUE_PRODUCTION", "REACTIVATE"} & scopes) or r["license"] == "trial":
            return None
    for line in r["rights"]:
        f = line.split("|")
        if f[0] == "rental" and not (rental_ok(f) and rental_bounds_ok(line)):
            return None
        if f[0] == "openall" and ("COMMAND_OPEN_ALL" not in scopes or int(f[3]) - int(f[2]) > MAX_OPEN_ALL or int(f[3]) <= int(f[2])):
            return None
    rights = list(r["rights"])
    if r.get("installKey"):            # the TV's signing key (W23-05 audit HIGH-1): signed as the right `ik|<64 hex>`, production for a TV only
        if r["kind"] != "production" or r["subject"] != "tv" or not re.fullmatch("[0-9a-f]{64}", r["installKey"]) or any(x.split("|")[0] == "ik" for x in rights):
            return None
        rights.append("ik|" + r["installKey"])
    fp = dev["fingerprints"]
    seat = r["seat"] or hashlib.sha256(("castbridge-seat|%s|%s" % (r["license"], set_hash(fp).hex())).encode()).digest()[:8].hex()
    env = {"type": "activation", "kid": key["kid"], "seq": r["seq"] if r.get("seq") is not None else r["issuedAt"], "nonce": r["nonce"], "issuedAt": r["issuedAt"], "notBefore": r["notBefore"],
           "expiresAt": r["notBefore"] + r["windowHours"] * HOUR, "target": {"kind": "device", "k": k_for(len(fp)), "factors": fp},
           "body": activation_body(r["kind"], r["subject"], r["license"], seat, rights)}
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
    end = start + window * unit
    if end - start > MAX_WINDOW:
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
    header = compact_header(r["kind"], key_tag(key["kid"]), r["notBeforeHour"], r["windowHours"], r["setId"], bind_of(r["deviceCode"]))
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


HERE = os.path.dirname(os.path.abspath(__file__))


def load(name):
    return json.load(open(os.path.join(HERE, name), encoding="utf-8"))


def check_keys(keys):
    for k in keys.values():       # the seeds are TEST keys: check that the published key is the one of the seed
        pub = Ed25519PrivateKey.from_private_bytes(bytes.fromhex(k["seed"])).public_key().public_bytes_raw()
        assert base64.b64encode(pub).decode() == k["publicKey"], "clé publique incohérente : " + k["name"]
        assert hashlib.sha256(pub).digest()[:8].hex() == k["kid"], "kid incohérent : " + k["name"]


def run_test_vectors():
    v = load("test-vectors.json")
    keys = {k["name"]: k for k in v["keys"]}
    check_keys(keys)
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
    return n, failures


# ------------------------------------------------------------------ rental vectors (rental-vectors.json): keys, box and sealed lot (docs/RENTAL-LOTS.md 3 and 10.4)

def hkdf_extract(salt, ikm):
    return hmac.new(salt if salt else bytes(32), ikm, hashlib.sha256).digest()


def hkdf_expand(prk, info, n):
    out, t, i = b"", b"", 1
    while len(out) < n:
        t = hmac.new(prk, t + info + bytes([i]), hashlib.sha256).digest()
        out += t
        i += 1
    return out[:n]


def hkdf(ikm, salt, info, n):
    return hkdf_expand(hkdf_extract(salt.encode(), ikm), info.encode(), n)


def kek_of(factors):
    """Key-encryption key of a set of factors: the lines KIND=fingerprint in the ORDER OF THE FactorKind ENUM (not alphabetical)."""
    return hkdf_expand(hkdf_extract(b"castbridge-kek-v1", "\n".join("%s=%s" % (k, factors[k]) for k in order(factors)).encode()), b"kek", 32)


def rental_key(master, license, seat, product, period):
    return hkdf(master, "castbridge-rental-v1", "key|%s|%s|%s|%d" % (license, seat, product, period), 32)


def lot_key(rkey, lot, version):
    return hkdf(rkey, "castbridge-rental-lot-v1", "lot|%s|%d" % (lot, version), 32)


def seal_lot(rkey, lot, version, plain):
    key = lot_key(rkey, lot, version)
    nonce = hkdf(key, "castbridge-rental-nonce-v1", "nonce", 12)
    return nonce + AESGCM(key).encrypt(nonce, plain, ("castbridge-rental-lot-v1|%s|%d" % (lot, version)).encode())


def open_lot(rkey, lot, version, blob):
    if rkey is None or len(rkey) != 32 or len(blob) < 28:
        return None
    try:
        return AESGCM(lot_key(rkey, lot, version)).decrypt(blob[:12], blob[12:], ("castbridge-rental-lot-v1|%s|%d" % (lot, version)).encode())
    except Exception:
        return None


def b64u(data):
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


def make_box(fp, k, rkey, product, period):
    wraps = []
    for subset in itertools.combinations(list(fp), k):
        names = "+".join(sorted(subset))
        kek = kek_of({f: fp[f] for f in subset})
        nonce = hkdf(kek, "castbridge-rentalbox-nonce-v1", "%s|%d|%s" % (product, period, names), 12)
        wraps.append((names, names + ":" + b64u(nonce + AESGCM(kek).encrypt(nonce, rkey, ("castbridge-rentalbox-v1|%s|%d|%s" % (product, period, names)).encode()))))
    return ";".join(w for _, w in sorted(wraps))


def open_box(box, current, product, period):
    for part in [p for p in box.split(";") if p]:
        names, _, blob = part.partition(":")
        kinds = names.split("+")
        try:
            raw = base64.urlsafe_b64decode(blob + "=" * (-len(blob) % 4))
        except Exception:
            continue
        if any(x not in KINDS or x not in current for x in kinds) or len(raw) < 28:
            continue
        try:
            key = AESGCM(kek_of({x: current[x] for x in kinds})).decrypt(raw[:12], raw[12:], ("castbridge-rentalbox-v1|%s|%d|%s" % (product, period, names)).encode())
        except Exception:
            continue
        if len(key) == 32:
            return key
    return None


def rental_line(r, box):
    return "rental|%s|%s|%d|%d|%d|%d|%d|%d|%s" % (r["product"], ",".join(sorted(r["bundles"])), r["startsAt"], r.get("period", r["startsAt"]), r["days"], r["graceMs"], r["usage"], r["concurrent"], box)


def granted_bundles(rights, now):
    """What an OLD device (no rental evaluation) grants: purchases and running subscriptions; unknown kinds and rental lines grant nothing."""
    out = set()
    for line in rights:
        f = line.split("|")
        if f[0] == "purchase":
            out |= {b for b in f[2].split(",") if b}
        elif f[0] == "subscription" and int(f[3]) <= now < int(f[4]) + int(f[5]):
            out |= {b for b in f[2].split(",") if b}
    return sorted(out)


def run_rental_vectors():
    v = load("rental-vectors.json")
    assert v["format"] == "castbridge-rental-vectors-v1"
    keys = {k["name"]: k for k in v["keys"]}
    for k in keys.values():       # the published test keys carry a seed and the scopes only: derive the public part, as the Kotlin test does
        pub = Ed25519PrivateKey.from_private_bytes(bytes.fromhex(k["seed"])).public_key().public_bytes_raw()
        k["publicKey"] = base64.b64encode(pub).decode()
        k["kid"] = hashlib.sha256(pub).digest()[:8].hex()
    devices = {}
    for d in v["devices"]:
        fp = fingerprints(d["raw"])
        devices[d["name"]] = {"name": d["name"], "fingerprints": fp, "code": device_code(fp)}
    master = bytes.fromhex(v["master"])
    failures, n = [], 0

    def check(cid, ok, detail=""):
        nonlocal n
        n += 1
        if not ok:
            failures.append("%s %s" % (cid, detail))

    def seat_of(license, fp):
        return hashlib.sha256(("castbridge-seat|%s|%s" % (license, set_hash(fp).hex())).encode()).digest()[:8].hex()

    def case_for(token, device, now, expect_subject="tv"):
        return {"token": token, "trustedKeys": list(keys), "revokedKeys": [], "revokedSeats": [], "lastSeq": {}, "expectSubject": expect_subject, "device": device, "nowMs": now}

    for c in v["cases"]:
        t, cid, e = c["type"], c["id"], c["expect"] if "expect" in c else None
        if t == "build-activation":
            r = c["request"]
            fp = devices[r["device"]]["fingerprints"]
            lines = []
            for rr in r["rentals"]:
                period = rr.get("period", rr["startsAt"])
                box = make_box(fp, k_for(len(fp)), rental_key(master, r["license"], seat_of(r["license"], fp), rr["product"], period), rr["product"], period)
                lines.append(rental_line(rr, box))
            req = {"device": r["device"], "kind": "production", "subject": "tv", "license": r["license"], "seat": None, "windowHours": r["windowHours"], "rights": lines + r["rights"],
                   "issuedAt": r["issuedAt"], "notBefore": r["issuedAt"], "nonce": r["nonce"]}
            tok = issue_activation({"request": req, "signer": c["signer"]}, keys, devices)
            check(cid, (e.get("refused") and tok is None) or (tok == e.get("token")), "jeton différent" if tok else "refusé")
            if tok and not e.get("refused"):      # and the independent verifier accepts what it just built
                res = verify_activation(case_for(tok, r["device"], r["issuedAt"]), keys, devices)
                check(cid + "/accepted", res[0] == "accepted", str(res))
        elif t == "box":
            r = c["request"]
            fp, other = devices[r["device"]]["fingerprints"], devices[r["otherDevice"]]["fingerprints"]
            period = r.get("period", r["startsAt"])
            key = rental_key(master, r["license"], seat_of(r["license"], fp), r["product"], period)
            box = make_box(fp, k_for(len(fp)), key, r["product"], period)
            check(cid, box == e["box"] and hashlib.sha256(key).hexdigest()[:16] == e["keyFingerprint"], "octets différents")
            check(cid + "/own-tv", open_box(box, fp, r["product"], period) == key)
            check(cid + "/other-tv", open_box(box, other, r["product"], period) is None)
        elif t == "seal":
            r = c["request"]
            fp = devices[r["device"]]["fingerprints"]
            key = rental_key(master, r["license"], seat_of(r["license"], fp), r["product"], r["period"])
            lot = "%s:%s" % (r["feature"], r["scope"])
            plain = bytes.fromhex(r["plainHex"])
            sealed = seal_lot(key, lot, r["version"], plain)
            check(cid, sealed.hex() == e["sealedHex"] and hashlib.sha256(key).hexdigest()[:16] == e["keyFingerprint"], "octets différents")
            check(cid + "/reopen", open_lot(key, lot, r["version"], sealed) == plain)
            check(cid + "/other-version", open_lot(key, lot, r["version"] + 1, sealed) is None)
            check(cid + "/no-key", open_lot(None, lot, r["version"], sealed) is None)
            check(cid + "/tampered", open_lot(key, lot, r["version"], sealed[:-1] + bytes([sealed[-1] ^ 1])) is None)
        elif t == "old-device":
            res = verify_activation(case_for(c["token"], c["device"], c["nowMs"]), keys, devices)
            p = parse_envelope(c["token"])
            ok = res[0] == "accepted" and p is not None   # accepted although a right is unknown; the canonical form is rebuilt byte for byte by parse_envelope
            check(cid, ok and granted_bundles(activation_view(p[0])["rights"], c["nowMs"]) == e["granted"], str(res))
        elif t == "lot-policy":
            lot = c["lot"]
            key = "%s:%s" % (lot["feature"], lot["scope"])
            refused = lot["edition"] == "trial" or key in c["free"] or key not in c["reserved"]     # a free lot, a trial lot or a lot of unknown family is never rented
            check(cid, refused == e["refused"])
        elif t == "rental-state":
            # the clock rules (RentalEngine.judge/evaluate, French messages, monotonic time) stay with the Kotlin implementations; here: every token installs on its TV
            # and the contracts named by the expectation (product@period) are exactly the rental lines of the tokens
            names = set()
            ok = True
            for tok in c["tokens"]:
                res = verify_activation(case_for(tok, c["device"], parse_envelope(tok)[0]["issuedAt"]), keys, devices)
                ok = ok and res[0] == "accepted"
                for line in activation_view(parse_envelope(tok)[0])["rights"]:
                    f = line.split("|")
                    if f[0] == "rental":
                        names.add("%s@%s" % (f[1], f[4]))
            check(cid, ok and {x["key"] for x in e} <= names, "jetons ou contrats incohérents")
        else:
            check(cid, False, "type inconnu " + t)
    return n, failures


# ------------------------------------------------------------------ server-issued.json: what the SERVER signs, verified by this independent code

def run_server_issued():
    v = load("server-issued.json")
    assert v["format"] == "castbridge-server-issued-v1"
    s = v["server"]
    pub = base64.b64decode(s["publicKey"])
    assert hashlib.sha256(pub).digest()[:8].hex() == s["kid"], "kid incohérent"
    keys = {"server": {"name": "server", "kid": s["kid"], "publicKey": s["publicKey"], "scopes": s["scopes"]}}
    failures, n = [], 0

    def check(cid, ok, detail=""):
        nonlocal n
        n += 1
        if not ok:
            failures.append("%s %s" % (cid, detail))

    for c in v["cases"]:
        t, cid, e = c["type"], c["id"], c["expect"]
        if t == "activation":
            fp = c["fingerprints"]
            devices = {c["device"]: {"fingerprints": fp}}
            case = {"token": c["token"], "trustedKeys": ["server"], "revokedKeys": [], "revokedSeats": [], "lastSeq": {}, "expectSubject": c["subject"], "device": c["device"], "nowMs": v["nowMs"]}
            r = verify_activation(case, keys, devices)
            ok = r[0] == "accepted" and r[1]["license"] == e["license"] and r[1]["seat"] == e["seat"]
            p = parse_envelope(c["token"])
            ok = ok and p is not None and p[0]["body"][0] == "kind=" + e["kind"] and sorted(activation_view(p[0])["rights"]) == e["rights"]
            if ok and e["kind"] == "trial":     # the implicit ceiling of a trial key without `usage`: 30 days after the issue
                ok = r[1]["usageEnd"] == p[0]["issuedAt"] + 30 * DAY
            elif ok:
                ok = r[1]["usageEnd"] is None
            check(cid, ok, str(r))
        elif t == "revocation":
            r = verify_revocation({"token": c["token"], "trustedKeys": ["server"]}, keys)
            check(cid, r is not None and r["keys"] == e["keys"] and r["seats"] == e["seats"], str(r))
        elif t == "order":
            case = {"token": c["token"], "trustedKeys": ["server"], "revokedKeys": [], "lastSeq": {}, "device": c["device"], "deviceLicenses": [], "deviceGroups": [], "nowMs": v["nowMs"]}
            r = verify_order(case, keys, {c["device"]: {"fingerprints": c["fingerprints"]}})
            check(cid, r[0] == "accepted" and r[1]["action"] == e["action"] and r[1]["params"] == e["params"] and r[2] == e["seq"], str(r))
        else:
            check(cid, False, "type inconnu " + t)
    return n, failures


def run_implicit_usage_end():
    """Hand-written expectations of the implicit usage ceiling (Activation.implicitUsageEnd), independent of any file."""
    t0, failures, n = 1_800_000_000_000, [], 0
    cases = [("trial-no-usage", ("trial", [], t0, t0 - HOUR), t0 + 30 * DAY),
             ("trial-no-usage-issuedAt-zero-uses-notBefore", ("trial", [], 0, t0), t0 + 30 * DAY),
             ("trial-with-usage-explicit-wins", ("trial", ["usage|duree|%d|%d" % (t0, t0 + DAY)], t0, t0), None),
             ("trial-essai-rental-only-still-capped", ("trial", ["rental|essai|a|1|1|3|0|720|0|"], t0, t0), t0 + 30 * DAY),
             ("production-no-usage-unlimited", ("production", [], t0, t0), None),
             ("production-no-rights-unlimited", ("production", ["purchase|p|a|1"], t0, t0), None)]
    for cid, args, want in cases:
        n += 1
        got = implicit_usage_end(*args)
        if got != want:
            failures.append("%s : %s au lieu de %s" % (cid, got, want))
    return n, failures


def store_canonical(f):
    """castbridge-rent-request-v1 (DESIGN-W17 § 4.1) : en-tête puis les huit champs triés par clé, séparés par \n, sans retour final."""
    keys = ["at", "bundle", "choice", "kind", "nonce", "origin", "period", "tv"]
    return "castbridge-rent-request-v1\n" + "\n".join("%s=%s" % (k, f[k]) for k in keys)


def store_short_code(f, alias):
    """<ALIAS>-<CHOIX>-<4 Crockford> : 20 premiers bits de SHA-256 de castbridge-rent-request-v1|tv|bundle|choice|nonce."""
    assert re.fullmatch(r"[A-Z0-9]{1,6}", alias)
    d = hashlib.sha256(("castbridge-rent-request-v1|%s|%s|%s|%s" % (f["tv"], f["bundle"], f["choice"], f["nonce"])).encode()).digest()
    bits = (d[0] << 12) | (d[1] << 4) | (d[2] >> 4)
    tail = "".join(ALPHABET[(bits >> s) & 31] for s in (15, 10, 5, 0))
    return "%s-%s-%s" % (alias, "DEF" if f["choice"] == "defaut" else f["choice"].upper(), tail)


def store_parse_ok(text):
    """Analyse stricte : les huit champs et eux seuls, chacun à sa grammaire, period = 0 ssi kind = new, puis relecture canonique."""
    if len(text) > 1024:
        return False
    lines = text.split("\n")
    if lines[0] != "castbridge-rent-request-v1":
        return False
    kv = {}
    for line in lines[1:]:
        k, eq, v = line.partition("=")
        if not eq or not k or k in kv:
            return False
        kv[k] = v
    if set(kv) != {"at", "bundle", "choice", "kind", "nonce", "origin", "period", "tv"}:
        return False
    ok = (re.fullmatch(r"[0-9a-f]{16}", kv["tv"]) and re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", kv["bundle"]) and re.fullmatch(r"defaut|[1-9][0-9]{0,2}[jh]", kv["choice"])
          and re.fullmatch(r"[0-9a-f]{8}", kv["nonce"]) and kv["kind"] in ("new", "extend") and kv["origin"] in ("tv", "phone")
          and re.fullmatch(r"[0-9]{1,15}", kv["at"]) and re.fullmatch(r"[0-9]{1,15}", kv["period"]))
    if not ok or (kv["kind"] == "new") != (int(kv["period"]) == 0):
        return False
    return store_canonical({**kv, "at": int(kv["at"]), "period": int(kv["period"])}) == text


def store_label(choice):
    """W16 § 1.3 : libellés exacts, jamais de conversion jours / heures."""
    if choice == "defaut":
        return "Sans durée précise : 30 jours"
    n = int(choice[:-1])
    if choice.endswith("h"):
        return "1 heure d'utilisation" if n == 1 else "%d heures d'utilisation" % n
    return "1 jour" if n == 1 else "%d jours" % n


def run_store_vectors():
    """tools/activation/store-vectors.json : octets (forme canonique, code court, refus d'analyse, libellés) rejoués sans Kotlin ; les scénarios d'états sont rejoués par StoreVectorsTest."""
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "store-vectors.json"), encoding="utf-8") as fh:
        doc = json.load(fh)
    failures, n = [], 0
    if doc.get("format") != "castbridge-store-vectors-v1":
        return 1, ["format inattendu : %s" % doc.get("format")]
    for c in doc["cases"]:
        t, cid = c["type"], c["id"]
        if t == "scenario":
            continue
        n += 1
        if t == "canonical":
            got = store_canonical(c["fields"]); want = c["expect"]
        elif t == "shortcode":
            got = store_short_code(c["fields"], c["alias"]); want = c["expect"]
        elif t == "parse-ok":
            got = "accepté" if store_parse_ok(c["text"]) else "refusé"; want = "accepté"
        elif t == "parse-refused":
            got = "MALFORMED" if not store_parse_ok(c["text"]) else "accepté"; want = c["expect"]
        elif t == "labels":
            got = [store_label(x) for x in c["choices"]]; want = c["expect"]
        else:
            got, want = "type inconnu " + t, None
        if got != want:
            failures.append("%s : %s au lieu de %s" % (cid, got, want))
        if t == "canonical" and not store_parse_ok(c["expect"]):
            failures.append("%s : le texte canonique n'est pas accepté par l'analyse stricte" % cid)
    return n, failures


# ------------------------------------------------------------------ rental-pilot-vectors.json: the pilot's rules re-derived by this independent code (docs/coordination/DESIGN-W16-...-PILOTE § 2.5)
# Everything below is a SECOND implementation written from the design, not a port of the Kotlin code: dates are Africa/Douala (UTC+1, no daylight saving).

import datetime as _dt

DOUALA_MS = HOUR
EPOCH_ORDINAL = 719163                      # date(1970, 1, 1).toordinal()
HARD_CAP_MIN = 96 * 60                      # the engine's ceiling of one hourly rental
PILOT_USAGE_FIELDS = ("days", "usage", "concurrent", "line")


def douala_label(ms):
    t = _dt.datetime.fromtimestamp(ms // 1000, _dt.timezone(_dt.timedelta(hours=1)))
    return t.strftime("%Y-%m-%dT%H:%M:%S.") + "%03d+01:00" % (ms % 1000)


def douala_date(ms):
    return _dt.date.fromordinal((ms + DOUALA_MS) // DAY + EPOCH_ORDINAL)


def douala_start(d):
    return (d.toordinal() - EPOCH_ORDINAL) * DAY - DOUALA_MS


def douala_end(d):
    return douala_start(d + _dt.timedelta(days=1)) - 1


def parse_pilot_date(text, end):
    d = _dt.date.fromisoformat(text)
    return douala_end(d) if end else douala_start(d)


class Refused(Exception):
    pass


def refuse_if(cond, why="refus"):
    if cond:
        raise Refused(why)


def contract_active(c, now):
    return (c["endedAt"] is None or c["endedAt"] > now) and c["endsAt"] > now


def pilot_bounds(v):
    start, end = parse_pilot_date(v["params"]["pilot.start"], False), parse_pilot_date(v["params"]["pilot.end"], True)
    hours_limit = douala_end(douala_date(end) + _dt.timedelta(days=14))      # 15/11: hours are used before it
    days_limit = douala_end(douala_date(end) + _dt.timedelta(days=30))       # 01/12: days and the default are honoured in full
    return start, end, hours_limit, days_limit


def pilot_bundle(v, bundle_id):
    """The bundle's product, or a refusal: unknown, Langues (by type or prefix), no lot, a lot that is not RESERVED (free or of unknown family)."""
    b = next((x for x in v["catalog"]["bundles"] if x["id"] == bundle_id), None)
    refuse_if(b is None, "bouquet inconnu")
    refuse_if(b["type"].strip().lower() == "langues" or bundle_id.lower().startswith("langues"), "Langues")
    refuse_if(not b["lots"] or any(lot not in v["families"]["reserved"] for lot in b["lots"]), "bouquet libre ou famille inconnue")
    product = "loc-" + bundle_id
    refuse_if(not ID_RE.match(product), "identifiant")
    return b, product


def pilot_choice(text):
    if text == "defaut":
        return ("default", 0)
    m = re.match(r"^(\d{1,4})([jh])$", text)
    refuse_if(not m or int(m.group(1)) < 1, "choix illisible")
    return ("days" if m.group(2) == "j" else "hours", int(m.group(1)))


def pilot_line(product, bundle, issued, period, days, usage):
    return "rental|%s|%s|%s|T|%d|0|%d|3|" % (product, bundle, "T" if issued == period else "T2", days, usage)


def pilot_new(v, c):
    start, end, hours_limit, days_limit = pilot_bounds(v)
    b, product = pilot_bundle(v, c["bundle"])
    issued, st = c["issuedAt"], c["state"]
    refuse_if(not start <= issued <= end, "fenêtre du pilote")
    refuse_if(any(a["product"] == product and contract_active(a, issued) for a in st["active"]), "déjà loué")
    refuse_if(sum(1 for a in st["active"] if contract_active(a, issued)) >= 3, "3 contrats actifs")
    kind, n = pilot_choice(c["choice"])
    cap_days = min(30, b.get("rentalDays", 0) or 30)
    if kind == "default":
        days, usage = cap_days, 0
        refuse_if(issued + days * DAY > days_limit, "fin au-delà du 01/12")
    elif kind == "days":
        refuse_if(not 1 <= n <= cap_days, "jours hors bornes")
        days, usage = n, 0
        refuse_if(issued + days * DAY > days_limit, "fin au-delà du 01/12")
    else:
        refuse_if(not 1 <= n <= 96, "heures hors bornes")
        refuse_if(st["hoursLast168"] + n > 192, "quota 192 h / 168 h")
        left = (douala_date(hours_limit) - douala_date(issued)).days
        days, usage = max(1, min(30, left)), n * 60
        refuse_if(issued + days * DAY > hours_limit, "fin au-delà du 15/11")
    return {"ok": True, "days": days, "usage": usage, "concurrent": 3, "line": pilot_line(product, c["bundle"], issued, issued, days, usage)}


def live_contract(ex, issued):
    refuse_if((ex["unit"] == "hours") != (ex["usage"] > 0), "unité incohérente")
    refuse_if(not contract_active(ex, issued), "contrat terminé")
    refuse_if(ex["period"] > issued, "période future")


def pilot_extend(v, c):
    start, end, hours_limit, days_limit = pilot_bounds(v)
    b, product = pilot_bundle(v, c["bundle"])
    ex, issued, st = c["existing"], c["issuedAt"], c["state"]
    refuse_if(ex["product"] != product, "autre bouquet")
    refuse_if(not start <= issued <= end, "fenêtre du pilote")
    live_contract(ex, issued)
    kind, n = pilot_choice(c["choice"])
    refuse_if((kind == "hours") != (ex["unit"] == "hours"), "on ne mélange pas les unités")
    new_start = max(ex["endsAt"], issued)
    if kind == "hours":
        refuse_if(ex["usage"] + n * 60 > HARD_CAP_MIN, "plafond de 96 h")
        refuse_if(st["hoursLast168"] + n > 192, "quota 192 h / 168 h")
        refuse_if(new_start + DAY > hours_limit + DAY, "fin fusionnée au-delà du 16/11")      # one day of tolerance, once
        days, usage = 1, n * 60
    else:
        cap_days = min(30, b.get("rentalDays", 0) or 30)
        days = n if kind == "days" else cap_days
        refuse_if(not 1 <= days <= cap_days, "jours hors bornes")
        refuse_if(max(0, ex["endsAt"] - issued) + days * DAY > 30 * DAY, "plus de 30 jours cumulés")
        refuse_if(new_start + days * DAY > days_limit, "fin au-delà du 01/12")
        usage = 0
    return {"ok": True, "days": days, "usage": usage, "concurrent": 3, "line": pilot_line(product, c["bundle"], issued, ex["period"], days, usage)}


def pilot_reissue(v, c):
    start, end, hours_limit, days_limit = pilot_bounds(v)
    ex, issued, st = c["existing"], c["issuedAt"], c["state"]
    refuse_if(not ex["product"].startswith("loc-") or len(ex["product"]) == 4, "contrat illisible")
    bundle = ex["product"][4:]
    pilot_bundle(v, bundle)
    refuse_if(ex["reissues"] >= 3, "3 réémissions au plus")
    live_contract(ex, issued)
    refuse_if(c["usedMinutes"] < 0, "relevé invalide")
    old, new = (ex["installPub"] or "").strip(), (c["newInstallPub"] or "").strip()
    refuse_if(not old or not new or old == new, "clé d'installation absente ou identique")      # the same key would merge with the old line
    # Pilot slice 1 (audit B1 2026-10-03): every reissue is refused; the TV engine merges a reissue with the original contract (same product and period).
    refuse_if(True, "Réémission indisponible pendant le pilote : la preuve d'installation n'est pas encore en place ; le contrat d'origine reste valable ; contactez le propriétaire")
    whole_days = (ex["endsAt"] - issued) // DAY      # rounded DOWN
    refuse_if(whole_days < 1, "moins d'un jour restant")
    if ex["unit"] == "hours":
        rest = min(ex["usage"], HARD_CAP_MIN) - c["usedMinutes"]
        refuse_if(rest <= 0, "heures épuisées")
        refuse_if(st["hoursLast168"] + (rest + 59) // 60 > 192, "quota 192 h / 168 h")
        refuse_if(issued + whole_days * DAY > hours_limit + DAY, "fin au-delà du 16/11")
        usage = rest
    else:
        refuse_if(issued + whole_days * DAY > days_limit, "fin au-delà du 01/12")
        usage = 0
    return {"ok": True, "days": whole_days, "usage": usage, "concurrent": 3, "line": pilot_line(ex["product"], bundle, issued, ex["period"], whole_days, usage)}


def expand_line(template, issued_at, period):
    f = template.split("|")
    f[3] = str(issued_at if f[3] == "T2" else period)
    f[4] = str(period)
    return "|".join(f)


def pilot_engine(c):
    """Second implementation of the engine's contract merge (unit, 96 h ceiling, renewals, mixed units) and of the state order."""
    lines = {}
    for m in c["lines"]:
        lines[json.dumps(m, sort_keys=True)] = m
    rows = sorted(lines.values(), key=lambda m: (m["startsAt"], rental_line(m, "box")))
    first = rows[0]
    end = first["startsAt"] + first["days"] * DAY
    used_rows, notes = [first], []
    for r in rows[1:]:
        grace = max(u["graceMs"] for u in used_rows)
        if (r["usage"] > 0) != (first["usage"] > 0):
            notes.append("mixte")
        elif r["startsAt"] < end + grace:
            end = max(end, r["startsAt"]) + r["days"] * DAY
            used_rows.append(r)
    total = sum(u["usage"] for u in used_rows) if first["usage"] > 0 else 0
    usage = min(total, HARD_CAP_MIN)
    if usage < total:
        notes.append("plafond")
    grace = max(u["graceMs"] for u in used_rows)
    left = max(0, usage - c["used"]) if usage > 0 else None
    now, begin = c["now"], min(first["period"], first["startsAt"])
    if c.get("expired"):
        state, reason = "EXPIRED", c["expired"]
    elif left == 0:
        state, reason = "EXPIRED", "USAGE"
    elif now >= end + grace:
        state, reason = "EXPIRED", "DATE"
    elif now + DAY < begin:
        state, reason = "NOT_STARTED", None
    elif now >= end:
        state, reason = "GRACE", None
    else:
        state, reason = "ACTIVE", None
    return {"unit": "hours" if usage > 0 else "days", "maxUsageMinutes": usage, "endsAt": end, "notes": len(notes), "state": state, "reason": reason, "remainingUsageMinutes": left}


USAGE_LINE = re.compile(r"^contract=([a-z0-9-]+)@(\d+)\|unit=(hours|days|unknown)\|used=(\d+)\|max=(\d+)\|state=([A-Z_]+)\|reason=(-|[A-Z]+)\|endsAt=(\d+)\|at=(\d+)$")


def run_pilot_vectors():
    v = load("rental-pilot-vectors.json")
    assert v["format"] == "castbridge-rental-pilot-vectors-v1"
    keys = {k["name"]: k for k in v["keys"]}
    for k in keys.values():
        pub = Ed25519PrivateKey.from_private_bytes(bytes.fromhex(k["seed"])).public_key().public_bytes_raw()
        k["publicKey"] = base64.b64encode(pub).decode()
        k["kid"] = hashlib.sha256(pub).digest()[:8].hex()
    devices = {}
    for d in v["devices"]:
        fp = fingerprints(d["raw"])
        devices[d["name"]] = {"name": d["name"], "fingerprints": fp, "code": device_code(fp)}
    failures, n = [], 0

    def check(cid, ok, detail=""):
        nonlocal n
        n += 1
        if not ok:
            failures.append("%s %s" % (cid, detail))

    check("file/cases", len(v["cases"]) >= 20 and len({c["id"] for c in v["cases"]}) == len(v["cases"]), "au moins 20 cas aux identifiants distincts")
    for c in v["cases"]:
        cid, t, e = c["id"], c["type"], c["expect"]
        for key, lab in (("issuedAt", "issuedAtLabel"), ("now", "nowLabel")):
            if lab in c:
                check(cid + "/" + lab, douala_label(c[key]) == c[lab], "%s ≠ %s" % (douala_label(c[key]), c[lab]))
        if t in ("pilot-new", "pilot-extend", "pilot-reissue"):
            try:
                got = {"pilot-new": pilot_new, "pilot-extend": pilot_extend, "pilot-reissue": pilot_reissue}[t](v, c)
            except Refused as r:
                got = {"refused": True, "why": str(r)}
            if e.get("refused"):
                check(cid, got.get("refused") is True, "devait être refusé (réponse Python : %s)" % got.get("line"))
            else:
                check(cid, got.get("ok") is True and all(got[k] == e[k] for k in PILOT_USAGE_FIELDS), "attendu %s, obtenu %s" % ({k: e[k] for k in PILOT_USAGE_FIELDS}, got))
                period = c["existing"]["period"] if t != "pilot-new" else c["issuedAt"]
                f = expand_line(e["line"], c["issuedAt"], period).split("|")
                check(cid + "/ten-fields", len(f) == 10 and rental_ok(f) and rental_bounds_ok("|".join(f)), "ligne illisible ou hors bornes : " + "|".join(f))
                if f[7] != "0":      # an hourly line never promises more than the engine will give
                    check(cid + "/cap", int(f[7]) <= HARD_CAP_MIN)
                limit = pilot_bounds(v)[2] + (DAY if t != "pilot-new" else 0) if int(f[7]) > 0 else pilot_bounds(v)[3]
                check(cid + "/ends-before-limit", max(int(f[3]), c["issuedAt"]) + int(f[5]) * DAY <= limit + (DAY if t == "pilot-extend" and int(f[7]) > 0 else 0), "fin au-delà de la borne")
        elif t == "engine-contract":
            got = pilot_engine(c)
            check(cid, got["unit"] == e["unit"] and got["maxUsageMinutes"] == e["maxUsageMinutes"] and got["endsAt"] == e["endsAt"] and got["notes"] == len(e["notes"])
                  and got["state"] == e["state"] and got["reason"] == e["reason"] and got["remainingUsageMinutes"] == e["remainingUsageMinutes"], "Python %s ≠ Kotlin %s" % (got, e))
            check(cid + "/ceiling", e["maxUsageMinutes"] <= HARD_CAP_MIN and (e["unit"] == "hours") == (e["maxUsageMinutes"] > 0))
        elif t == "line-parse":
            f = c["line"].split("|")
            ok = len(f) == 10 and rental_ok(f)
            if e.get("refused"):
                check(cid, not ok, "devait être illisible")
            else:
                check(cid, ok and f[1] == e["product"] and int(f[5]) == e["days"] and int(f[7]) == e["usage"] and int(f[8]) == e["concurrent"] and (e["bounds"] is None) == rental_bounds_ok(c["line"]) and "|".join(f) == e["line"], "ligne différente")
        elif t == "usage-report":
            rows = e["text"].split("\n")
            check(cid + "/header", rows[0] == "castbridge-rental-usage-v1" and re.match(r"^install=[0-9a-f]{16}$", rows[1]) and rows[1] == "install=" + c["installId"] and rows[-1] == "", "en-tête")
            body = rows[2:-1]
            parsed = [USAGE_LINE.match(x) for x in body]
            check(cid + "/lines", all(parsed) and len(body) == len(c["contracts"]) and body == sorted(body), "lignes de contrat")
            if all(parsed):
                for m, ct in zip(parsed, sorted(c["contracts"], key=lambda x: x["product"])):
                    hours = ct["usage"] > 0
                    check(cid + "/" + ct["product"], m.group(1) == ct["product"] and int(m.group(2)) == c["at"] and m.group(3) == ("hours" if hours else "days") and int(m.group(4)) == (min(ct["used"], ct["usage"]) if hours else ct["used"])
                          and int(m.group(5)) == ct["usage"] and m.group(6) == "ACTIVE" and m.group(7) == "-" and int(m.group(8)) == c["at"] + ct["days"] * DAY and int(m.group(9)) == c["at"] + 3 * 60_000, "ligne " + m.group(0))
            check(cid + "/no-personal-data", not any(w in e["text"].lower() for w in ("lic-", "seat", "box", "flashserial", "aa:bb", "profil", "enfant")))
        elif t == "build-activation":
            res_parse = parse_envelope(e["token"])
            check(cid + "/envelope", res_parse is not None)
            if res_parse is None:
                continue
            case = {"token": e["token"], "trustedKeys": list(keys), "revokedKeys": [], "revokedSeats": [], "lastSeq": {}, "expectSubject": "tv", "device": c["device"], "nowMs": c["issuedAt"]}
            res = verify_activation(case, keys, devices)
            check(cid + "/signature", res[0] == "accepted", str(res))
            view = activation_view(res_parse[0])
            rentals = [x for x in view["rights"] if x.startswith("rental|")]
            check(cid + "/one-rental-line", len(rentals) == 1 and len(view["rights"]) == 1)
            if len(rentals) == 1:
                f = rentals[0].split("|")
                want = expand_line(e["line"], c["issuedAt"], c["issuedAt"]).split("|")
                check(cid + "/line", len(f) == 10 and f[:9] == want[:9] and f[9].startswith("v2:") and rental_ok(f) and rental_bounds_ok(rentals[0]), "ligne signée %s" % rentals[0][:80])
            check(cid + "/window", res_parse[0]["expiresAt"] - res_parse[0]["notBefore"] == c["windowHours"] * HOUR and res_parse[0]["issuedAt"] == c["issuedAt"])
        elif t == "meter":
            check(cid, all(isinstance(x, int) and 0 <= x <= 1440 for x in e["minutes"]), "minutes hors bornes")      # the counter itself is replayed by Kotlin only
            check(cid + "/never-above-elapsed", sum(e["minutes"]) * 60_000 <= max([op[-1] for op in c["ops"] if len(op) > 1 and isinstance(op[-1], int)] or [0]) + 1, "plus de minutes que de temps écoulé")
        else:
            check(cid, False, "type inconnu " + t)
    return n, failures



# ------------------------------------------------------------------ empreinte de la clé d'installation (install-key-fingerprint-vectors.json), second audit w23-05

def install_key_fingerprint(raw):
    """8 groupes de 4 hexadécimaux minuscules : les 16 premiers octets de SHA-256 de la clé brute (32 octets) ; None si ce n'est pas une clé de 32 octets."""
    if len(raw) != 32:
        return None
    h = hashlib.sha256(raw).digest()[:16].hex()
    return "-".join(h[i:i + 4] for i in range(0, 32, 4))


def normalize_fingerprint(typed):
    s = "".join(ch for ch in typed if not ch.isspace() and ch != "-").lower()
    return s if re.fullmatch("[0-9a-f]{32}", s) else None


def run_install_key_fingerprint_vectors():
    doc = load("install-key-fingerprint-vectors.json")
    n, failures = 0, []

    def check(cid, ok, why=""):
        nonlocal n
        n += 1
        if not ok:
            failures.append("%s %s" % (cid, why))

    for c in doc["cases"]:
        check(c["id"], install_key_fingerprint(bytes.fromhex(c["publicKeyHex"])) == c["fingerprint"], "empreinte différente")
    check("short-key", install_key_fingerprint(bytes(31)) is None)
    for x in doc["normalize"]:
        check("normalize " + x["typed"], normalize_fingerprint(x["typed"]) == x["canonical"])
    return n, failures


def main():
    if "--only" in sys.argv and sys.argv[sys.argv.index("--only") + 1:][:1] == ["store"]:
        n, failures = run_store_vectors()
        print("%-36s %4d contrôles, %d échecs" % ("store-vectors.json", n, len(failures)))
        for f in failures:
            print("ÉCHEC :", f)
        return 1 if failures else 0
    if "--pilot" in sys.argv:
        n, failures = run_pilot_vectors()
        print("%-36s %4d contrôles, %d échecs" % ("rental-pilot-vectors.json", n, len(failures)))
        for f in failures:
            print("ÉCHEC :", f)
        return 1 if failures else 0
    total, bad = 0, 0
    for name, run in (("test-vectors.json", run_test_vectors), ("rental-vectors.json", run_rental_vectors), ("server-issued.json", run_server_issued), ("implicit usage end (sans fichier)", run_implicit_usage_end), ("rental-pilot-vectors.json", run_pilot_vectors), ("install-key-fingerprint-vectors.json", run_install_key_fingerprint_vectors)):
        n, failures = run()
        print("%-36s %4d contrôles, %d échecs" % (name, n, len(failures)))
        for f in failures:
            print("ÉCHEC :", name, f)
        total, bad = total + n, bad + len(failures)
    print("TOTAL %d contrôles, %d échecs" % (total, bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
