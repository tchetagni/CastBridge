#!/usr/bin/env python3
"""Test de verify_export.py sans le serveur : une chaîne construite ici (même formule que le serveur) est acceptée ; chaque falsification est désignée."""
import hashlib
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(HERE, "verify_export.py")
sys.path.insert(0, HERE)
import verify_export as v  # noqa: E402

KEY = hashlib.sha256(b"cle-de-test-publique").digest()


def build(n, key=KEY, start=1, prev=v.GENESIS):
    out = []
    for i in range(start, start + n):
        e = {"id": i, "atMs": 1759600000000 + i, "recordedMs": 1759600000000 + i + 5, "type": "SEEN", "fp": None, "tvRef": "0123456789abcdef", "licenseId": None, "kid": None,
             "actorType": "TV", "actor": "tv", "source": "REPORT", "before": None, "after": "{\"i\":%d}" % i, "idemKey": "k%d" % i, "prevHash": prev}
        e["hash"] = v.digest(key, v.event_parts(prev, e))
        prev = e["hash"]
        out.append(e)
    return out


def write(rows):
    f = tempfile.NamedTemporaryFile("w", suffix=".jsonl", delete=False, encoding="utf-8")
    for r in rows:
        f.write(json.dumps(r, ensure_ascii=False) + "\n")
    f.close()
    return f.name


def keyfile():
    f = tempfile.NamedTemporaryFile("wb", suffix=".key", delete=False)
    f.write(b"cle-de-test-publique")
    f.close()
    return f.name


def run(*args):
    p = subprocess.run([sys.executable, SCRIPT, *args], capture_output=True, text=True)
    return p.returncode, p.stdout


class VerifyExport(unittest.TestCase):
    def test_a_good_chain_is_accepted(self):
        code, out = run("--events", write(build(30)), "--audit-key-file", keyfile())
        self.assertEqual(code, 0, out)
        self.assertIn("chaîne intacte jusqu'au 30", out)

    def test_a_modified_line_is_pointed_at(self):
        rows = build(30)
        rows[11]["after"] = "{\"i\":999}"
        code, out = run("--events", write(rows), "--audit-key-file", keyfile())
        self.assertEqual(code, 1)
        self.assertIn("ligne 12", out)
        self.assertIn("modifiée", out)

    def test_a_removed_line_is_pointed_at(self):
        rows = build(30)
        del rows[4]
        code, out = run("--events", write(rows), "--audit-key-file", keyfile())
        self.assertEqual(code, 1)
        self.assertIn("ligne 5", out)

    def test_a_chain_recomputed_without_the_key_does_not_verify(self):
        rows = build(30, key=None)
        code, out = run("--events", write(rows), "--audit-key-file", keyfile())
        self.assertEqual(code, 1)
        self.assertIn("ligne 1", out)

    def test_without_a_key_file_the_chain_is_plain_sha256(self):
        code, out = run("--events", write(build(10, key=None)))
        self.assertEqual(code, 0, out)

    def test_a_partial_export_needs_its_anchor(self):
        full = build(30)
        part = full[10:]
        code, out = run("--events", write(part), "--audit-key-file", keyfile())
        self.assertEqual(code, 1)
        code, out = run("--events", write(part), "--audit-key-file", keyfile(), "--anchor-id", "10", "--anchor-hash", full[9]["hash"])
        self.assertEqual(code, 0, out)
        self.assertIn("jusqu'au 30", out)

    def test_hmac_checkpoints(self):
        rows = build(8)
        payload = "|".join([v.FORMAT, "2026-10-10", "8", rows[7]["hash"], "0", v.GENESIS, "{}"])
        import hmac as h
        cp = [{"day": "2026-10-10", "eventLastId": 8, "eventHead": rows[7]["hash"], "readLastId": 0, "readHead": v.GENESIS, "countsJson": "{}", "sigKid": "hmac",
               "signature": h.new(KEY, payload.encode(), hashlib.sha256).hexdigest()}]
        path = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False)
        json.dump({"items": cp}, path)
        path.close()
        code, out = run("--events", write(rows), "--audit-key-file", keyfile(), "--checkpoints", path.name)
        self.assertEqual(code, 0, out)
        cp[0]["eventHead"] = "ff" + cp[0]["eventHead"][2:]
        path2 = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False)
        json.dump(cp, path2)
        path2.close()
        code, out = run("--events", write(rows), "--audit-key-file", keyfile(), "--checkpoints", path2.name)
        self.assertEqual(code, 1, out)


if __name__ == "__main__":
    unittest.main()
