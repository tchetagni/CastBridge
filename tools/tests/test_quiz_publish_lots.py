"""Tests de tools/quiz/publish_lots.py (faux serveur, aucun réseau) :
python3 -m unittest discover -s tools/tests -p 'test_quiz_publish_lots.py'"""
import argparse, hashlib, importlib.util, json, os, re, tempfile, unittest

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
spec = importlib.util.spec_from_file_location("quiz_publish_lots", os.path.join(REPO, "tools", "quiz", "publish_lots.py"))
P = importlib.util.module_from_spec(spec)
spec.loader.exec_module(P)

TOKEN = "jeton-secret-de-test-0123456789"


def make_dir(lots, corrupt=None, families=None):
    """lots: [(scope, level, version, data)]. families=None -> dérivées de la règle."""
    d = tempfile.mkdtemp()
    entries = []
    for scope, level, version, data in lots:
        name = "quiz-%s-p1-v%d.quiz.zip" % (scope, version)
        with open(os.path.join(d, name), "wb") as f:
            f.write(data)
        sha = hashlib.sha256(data).hexdigest() if scope != corrupt else "0" * 64
        entries.append({"feature": "quiz", "scope": scope, "level": level, "version": version, "bytes": len(data), "sha256": sha,
                        "title": "T " + scope, "minAppVersion": 0, "file": name})
    with open(os.path.join(d, "catalog-lots.json"), "w") as f:
        json.dump({"format": 1, "feature": "quiz", "lots": entries}, f)
    fam = P.derive_families(entries) if families is None else families
    fp = os.path.join(d, "families.json")
    with open(fp, "w") as f:
        json.dump(fam, f)
    return d, fp


class FakeServer:
    def __init__(self, existing=None):
        self.lots = list(existing or [])
        self.calls = []
        self.uploaded = []

    def __call__(self, method, url, headers, body):
        path = re.sub(r"^https?://[^/]+", "", url)
        self.calls.append((method, path))
        if headers.get("Authorization") != "Bearer " + TOKEN:
            return 401, b"{}"
        if method == "GET" and path == "/api/v1/admin/lots":
            return 200, json.dumps(self.lots).encode()
        if method == "POST" and path == "/api/v1/admin/lots":
            f = dict(re.findall(r'name="(\w+)"\r\n\r\n([^\r]*)\r\n', body.decode("latin-1")))
            assert f["publish"] == "false" and f["feature"] == "quiz" and len(f["sha256"]) == 64
            e = {"id": len(self.lots) + 1, "feature": "quiz", "scope": f["scope"], "version": int(f["version"]), "sha256": f["sha256"],
                 "published": False, "revoked": False}
            self.lots.append(e)
            self.uploaded.append(f["scope"])
            return 201, json.dumps(e).encode()
        m = re.match(r"/api/v1/admin/lots/(\d+)/publish", path)
        if method == "POST" and m:
            for e in self.lots:
                if e["id"] == int(m.group(1)):
                    e["published"] = True
                    return 200, json.dumps(e).encode()
        return 404, b"{}"


def args(d, fp, apply_=False, only=None, limit=None, server="https://bridge.sti-cm.com"):
    return argparse.Namespace(lots_dir=d, families=fp, server=server, only=only, limit=limit, apply=apply_, write_families=False)


class QuizPublishTest(unittest.TestCase):
    def setUp(self):
        self.out = []
        self.lots = [("2nde-droit", "2nde", 1, b"A" * 1500), ("tle-droit", "Tle", 1, b"B" * 2000), ("droit-l1", "L1", 1, b"C" * 900),
                     ("culture-cm", None, 3, b"D" * 700)]

    def run_(self, a, srv=None, env=None):
        return P.run(a, env={P.TOKEN_ENV: TOKEN} if env is None else env, transport=srv or FakeServer(), out=self.out.append)

    def test_rule_splits_levels(self):
        d, fp = make_dir(self.lots)
        free, res = P.load_families(fp)
        self.assertEqual({"quiz:2nde-droit", "quiz:culture-cm"}, free)
        self.assertEqual({"quiz:tle-droit", "quiz:droit-l1"}, res)

    def test_dry_run_sends_nothing_and_needs_no_token(self):
        d, fp = make_dir(self.lots)
        srv = FakeServer()
        self.assertEqual(0, self.run_(args(d, fp), srv, env={}))
        self.assertEqual([], srv.calls)
        text = "\n".join(self.out)
        self.assertIn("SIMULATION", text); self.assertIn("2 lot(s) libre(s)", text); self.assertIn("2 lot(s) réservé(s)", text)
        self.assertNotIn("tle-droit  ", text)

    def test_apply_publishes_only_free_lots_without_leaking_token(self):
        d, fp = make_dir(self.lots)
        srv = FakeServer()
        self.assertEqual(0, self.run_(args(d, fp, True), srv))
        self.assertEqual(["2nde-droit", "culture-cm"], sorted(srv.uploaded))
        self.assertTrue(all(e["published"] for e in srv.lots))
        self.assertNotIn(TOKEN, "\n".join(self.out))

    def test_reserved_lot_refused_nothing_sent(self):
        d, fp = make_dir(self.lots)
        srv = FakeServer()
        with self.assertRaises(P.Fail) as c:
            self.run_(args(d, fp, True, only="tle-droit"), srv)
        self.assertIn("RÉSERVÉ", str(c.exception)); self.assertEqual([], srv.calls)

    def test_reserved_level_listed_free_refused(self):
        d, fp = make_dir(self.lots, families={"free": ["quiz:2nde-droit", "quiz:culture-cm", "quiz:tle-droit"], "reserved": ["quiz:droit-l1"]})
        srv = FakeServer()
        with self.assertRaises(P.Fail):
            self.run_(args(d, fp, True), srv)
        self.assertEqual([], srv.calls)

    def test_unknown_lot_refused(self):
        d, fp = make_dir(self.lots, families={"free": ["quiz:2nde-droit"], "reserved": ["quiz:tle-droit", "quiz:droit-l1"]})
        srv = FakeServer()
        with self.assertRaises(P.Fail) as c:
            self.run_(args(d, fp, True), srv)
        self.assertIn("culture-cm", str(c.exception)); self.assertEqual([], srv.calls)

    def test_lot_in_both_lists_refused(self):
        d, fp = make_dir(self.lots, families={"free": ["quiz:2nde-droit", "quiz:culture-cm"], "reserved": ["quiz:tle-droit", "quiz:droit-l1", "quiz:2nde-droit"]})
        with self.assertRaises(P.Fail):
            self.run_(args(d, fp))

    def test_missing_families_file_refused(self):
        d, fp = make_dir(self.lots)
        os.remove(fp)
        with self.assertRaises(P.Fail):
            self.run_(args(d, fp))

    def test_hash_mismatch_refused(self):
        d, fp = make_dir(self.lots, corrupt="2nde-droit")
        srv = FakeServer()
        with self.assertRaises(P.Fail) as c:
            self.run_(args(d, fp, True), srv)
        self.assertIn("SHA-256", str(c.exception)); self.assertEqual([], srv.calls)

    def test_oversize_refused(self):
        d, fp = make_dir([("2nde-droit", "2nde", 1, b"A" * ((3 << 20) + 1))])
        with self.assertRaises(P.Fail):
            self.run_(args(d, fp))

    def test_idempotent_second_run_sends_nothing(self):
        d, fp = make_dir(self.lots)
        srv = FakeServer()
        self.run_(args(d, fp, True), srv)
        n = len(srv.calls)
        self.run_(args(d, fp, True), srv)
        self.assertEqual(["GET"], [m for m, _ in srv.calls[n:]])
        self.assertIn("2 déjà à jour", "\n".join(self.out))

    def test_uploaded_not_published_is_only_published_and_hash_clash_is_error(self):
        d, fp = make_dir(self.lots)
        sha = hashlib.sha256(b"A" * 1500).hexdigest()
        srv = FakeServer([{"id": 7, "feature": "quiz", "scope": "2nde-droit", "version": 1, "sha256": sha, "published": False, "revoked": False}])
        self.run_(args(d, fp, True, only="2nde-droit"), srv)
        self.assertEqual([], srv.uploaded); self.assertIn(("POST", "/api/v1/admin/lots/7/publish"), srv.calls)
        bad = FakeServer([{"id": 7, "feature": "quiz", "scope": "2nde-droit", "version": 1, "sha256": "f" * 64, "published": True, "revoked": False}])
        with self.assertRaises(P.Fail):
            self.run_(args(d, fp, True, only="2nde-droit"), bad)
        self.assertEqual([("GET", "/api/v1/admin/lots")], bad.calls)

    def test_limit_and_only_canary(self):
        d, fp = make_dir(self.lots)
        srv = FakeServer()
        self.run_(args(d, fp, True, limit=1), srv)
        self.assertEqual(1, len(srv.uploaded))

    def test_token_required_and_never_cleartext(self):
        d, fp = make_dir(self.lots)
        with self.assertRaises(P.Fail):
            self.run_(args(d, fp, True), env={})
        with self.assertRaises(P.Fail):
            self.run_(args(d, fp, True, server="http://example.com"))

    def test_token_not_an_argument(self):
        with self.assertRaises(SystemExit):
            P.parse(["--token", "x"])


class RealFamiliesTest(unittest.TestCase):
    """families.json commité = règle appliquée au vrai catalogue ; chaque lot dans exactement une liste (échec fermé)."""

    def test_every_lot_in_exactly_one_list(self):
        lots_dir = os.path.join(REPO, "content", "quiz", "lots")
        cat = P.read_catalog(lots_dir)
        free, res = P.load_families(os.path.join(REPO, "content", "quiz", "families.json"))
        self.assertEqual(93, len(cat))
        for e in cat:
            k = "quiz:" + e["scope"]
            self.assertEqual(1, (k in free) + (k in res), k)
            self.assertEqual(e["level"] in P.RESERVED_LEVELS, k in res, k)
        self.assertEqual({"quiz:" + e["scope"] for e in cat}, free | res)
        self.assertEqual((68, 25), (len(free), len(res)))
        self.assertEqual(P.derive_families(cat), json.load(open(os.path.join(REPO, "content", "quiz", "families.json"))))


if __name__ == "__main__":
    unittest.main()
