"""Tests de tools/langues/publish_lots.py avec un faux serveur (aucun réseau) :
python3 -m unittest discover -s tools/tests -p 'test_publish_lots.py'"""
import argparse, hashlib, json, os, re, sys, tempfile, unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "langues"))
import publish_lots as P  # noqa: E402

TOKEN = "jeton-secret-de-test-0123456789"


def make_dir(lots, family="free", feature="langues", corrupt=None):
    d = tempfile.mkdtemp()
    entries = []
    for scope, version, data in lots:
        name = "castbridge-lot-%s-%s-v%d.lot" % (feature, scope, version)
        with open(os.path.join(d, name), "wb") as f:
            f.write(data)
        sha = hashlib.sha256(data).hexdigest() if scope != corrupt else "0" * 64
        entries.append({"feature": feature, "scope": scope, "version": version, "bytes": len(data), "sha256": sha, "title": "T " + scope,
                        "minAppVersion": 0, "file": name, "family": family})
    with open(os.path.join(d, "lots-catalog.json"), "w") as f:
        json.dump({"format": 1, "feature": "langues", "signature": "UNSIGNED", "lots": entries}, f)
    return d


class FakeServer:
    def __init__(self, existing=None, status_override=None):
        self.lots = list(existing or [])
        self.calls = []
        self.headers_seen = []
        self.status_override = status_override or {}

    def __call__(self, method, url, headers, body):
        path = url.split("sti-cm.com")[-1] if "sti-cm.com" in url else re.sub(r"^https?://[^/]+", "", url)
        self.calls.append((method, path))
        self.headers_seen.append(dict(headers))
        if headers.get("Authorization") != "Bearer " + TOKEN:
            return 401, b"{}"
        if (method, path) in self.status_override:
            return self.status_override[(method, path)], b'{"message":"refus","errors":["detail"]}'
        if method == "GET" and path == "/api/v1/admin/lots":
            return 200, json.dumps(self.lots).encode()
        if method == "POST" and path == "/api/v1/admin/lots":
            text = body.decode("latin-1")
            f = dict(re.findall(r'name="(\w+)"\r\n\r\n([^\r]*)\r\n', text))
            assert f["publish"] == "false" and f["feature"] == "langues" and len(f["sha256"]) == 64
            e = {"id": len(self.lots) + 1, "feature": "langues", "scope": f["scope"], "version": int(f["version"]), "sha256": f["sha256"], "published": False, "revoked": False}
            self.lots.append(e)
            return 201, json.dumps(e).encode()
        m = re.match(r"/api/v1/admin/lots/(\d+)/publish", path)
        if method == "POST" and m:
            for e in self.lots:
                if e["id"] == int(m.group(1)):
                    e["published"] = True
                    return 200, json.dumps(e).encode()
        return 404, b"{}"


def args(lots_dir, apply_=False, server="https://bridge.sti-cm.com"):
    return argparse.Namespace(lots_dir=lots_dir, server=server, channel="stable", apply=apply_)


class PublishLotsTest(unittest.TestCase):
    def setUp(self):
        self.out = []
        self.lots = [("de-a0-famille-en", 1, b"A" * 1500), ("fr-a0-salut-en", 1, b"B" * 2500)]

    def run_(self, a, srv=None, env=None):
        return P.run(a, env=env if env is not None else {P.TOKEN_ENV: TOKEN}, transport=srv or FakeServer(), out=self.out.append)

    def test_dry_run_sends_nothing_and_needs_no_token(self):
        srv = FakeServer()
        self.assertEqual(0, self.run_(args(make_dir(self.lots)), srv, env={}))
        self.assertEqual([], srv.calls)
        text = "\n".join(self.out)
        self.assertIn("SIMULATION", text); self.assertIn("de-a0-famille-en", text); self.assertIn("2 lot(s)", text)

    def test_apply_uploads_then_publishes_without_leaking_the_token(self):
        srv = FakeServer()
        self.assertEqual(0, self.run_(args(make_dir(self.lots), True), srv))
        self.assertEqual(2, sum(1 for c in srv.calls if c == ("POST", "/api/v1/admin/lots")))
        self.assertEqual(2, sum(1 for c in srv.calls if c[1].endswith("/publish")))
        self.assertTrue(all(e["published"] for e in srv.lots))
        self.assertNotIn(TOKEN, "\n".join(self.out))
        self.assertTrue(all(c[1].startswith("/api/") for c in srv.calls))

    def test_second_run_is_idempotent(self):
        d = make_dir(self.lots); srv = FakeServer()
        self.run_(args(d, True), srv)
        n = len(srv.calls); self.out.clear()
        self.run_(args(d, True), srv)
        self.assertEqual(["GET"], [c[0] for c in srv.calls[n:]])        # only the listing
        self.assertIn("0 à envoyer/publier, 2 déjà à jour", "\n".join(self.out))

    def test_uploaded_but_unpublished_lot_is_only_published(self):
        d = make_dir(self.lots)
        sha = hashlib.sha256(self.lots[0][2]).hexdigest()
        srv = FakeServer([{"id": 7, "feature": "langues", "scope": "de-a0-famille-en", "version": 1, "sha256": sha, "published": False, "revoked": False}])
        self.run_(args(d, True), srv)
        self.assertIn(("POST", "/api/v1/admin/lots/7/publish"), srv.calls)
        self.assertEqual(1, sum(1 for c in srv.calls if c == ("POST", "/api/v1/admin/lots")))

    def test_same_version_other_hash_is_an_error(self):
        srv = FakeServer([{"id": 1, "feature": "langues", "scope": "de-a0-famille-en", "version": 1, "sha256": "f" * 64, "published": True, "revoked": False}])
        with self.assertRaises(P.Fail) as c:
            self.run_(args(make_dir(self.lots), True), srv)
        self.assertIn("ne change jamais", str(c.exception))
        self.assertEqual([("GET", "/api/v1/admin/lots")], srv.calls)

    def test_reserved_lot_is_refused_before_any_call(self):
        srv = FakeServer()
        with self.assertRaises(P.Fail) as c:
            self.run_(args(make_dir(self.lots, family="reserved"), True), srv)
        self.assertIn("réservé", str(c.exception)); self.assertEqual([], srv.calls)

    def test_other_feature_and_corrupt_file_and_oversize_are_refused(self):
        with self.assertRaises(P.Fail):
            self.run_(args(make_dir(self.lots, feature="learn")))
        with self.assertRaises(P.Fail) as c:
            self.run_(args(make_dir(self.lots, corrupt="fr-a0-salut-en")))
        self.assertIn("SHA-256", str(c.exception))
        with self.assertRaises(P.Fail) as c:
            self.run_(args(make_dir([("de-a0-grand-en", 1, b"x" * ((3 << 20) + 1))])))
        self.assertIn("maximum", str(c.exception))

    def test_token_is_required_and_a_wrong_one_is_reported_without_printing_it(self):
        d = make_dir(self.lots)
        with self.assertRaises(P.Fail) as c:
            self.run_(args(d, True), env={})
        self.assertIn(P.TOKEN_ENV, str(c.exception))
        with self.assertRaises(P.Fail) as c:
            self.run_(args(d, True), env={P.TOKEN_ENV: "mauvais-jeton"})
        self.assertNotIn("mauvais-jeton", str(c.exception)); self.assertIn("refusé", str(c.exception))

    def test_server_refusal_is_reported(self):
        srv = FakeServer(status_override={("POST", "/api/v1/admin/lots"): 400})
        with self.assertRaises(P.Fail) as c:
            self.run_(args(make_dir(self.lots), True), srv)
        self.assertIn("HTTP 400", str(c.exception))

    def test_https_only(self):
        with self.assertRaises(P.Fail):
            self.run_(args(make_dir(self.lots), True, server="http://bridge.example.com"))

    def test_plain_http_only_to_a_real_loopback_host(self):
        for bad in ("http://localhost.example.com", "http://127.0.0.1.evil.com", "http://localhost@evil.com", "http://evil.com/localhost",
                    "http://localhost:8080@evil.com", "ftp://localhost", "http://0.0.0.0"):
            with self.assertRaises(P.Fail, msg=bad):
                P.Admin(bad, "t")
        for good in ("http://localhost:8080", "http://127.0.0.1:8080/x", "http://[::1]:8080", "https://bridge.sti-cm.com"):
            P.Admin(good, "t")

    def test_no_redirect_handler_and_multipart_shape(self):
        self.assertIsNone(P.NoRedirect().redirect_request(None, None, 302, "", {}, "http://evil"))
        body, ctype = P.multipart({"a": "é"}, "file", "x.lot", b"\x00\x01")
        self.assertIn("boundary=", ctype); self.assertIn(b'name="file"; filename="x.lot"', body); self.assertTrue(body.endswith(b"--\r\n"))


if __name__ == "__main__":
    unittest.main()
