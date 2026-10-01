"""Tests of the content tools (python3 -m unittest discover -s tools/tests): budget, media check, lots, release, mapping, QA report."""
import copy, hashlib, json, os, shutil, struct, subprocess, sys, tempfile, unittest, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.normpath(os.path.join(HERE, ".."))
for sub in ("content-lib", "content-budget", "content-media", "content-lots", "content-map", "pedagogy-report", "content-graph"):
    sys.path.insert(0, os.path.join(TOOLS, sub))
import lotlib as L  # noqa: E402
import content_budget, check_media, build_lots, make_release, map_existing, validate_report, gen_graph  # noqa: E402


def write(p, data):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "wb" if isinstance(data, bytes) else "w") as f:
        f.write(data)


def webp(w, h, pad=0):
    body = b"VP8X" + struct.pack("<I", 10) + b"\x00\x00\x00\x00" + (w - 1).to_bytes(3, "little") + (h - 1).to_bytes(3, "little")
    body += b"\x00" * pad
    return b"RIFF" + struct.pack("<I", 4 + len(body)) + b"WEBP" + body


def mini_content(root):
    """A tiny content tree: one learn pack (CM2), one quiz pack (CM2), graph scopes, empty media manifest."""
    c = os.path.join(root, "content")
    write(os.path.join(c, "learn", "p1", "pack.json"), json.dumps({"id": "p1", "version": 1, "level": "CM2", "subject": "maths", "title": "t"}))
    write(os.path.join(c, "learn", "p1", "lessons", "a.json"), json.dumps({"chapter": "c", "lessons": [{"id": "p1-l1", "title": "T", "media": []}],
                                                                           "exercises": []}))
    qz = os.path.join(c, "quiz", "dist", "quiz-cm2-p1-v1.quiz.zip")
    os.makedirs(os.path.dirname(qz))
    with zipfile.ZipFile(qz, "w") as z:
        z.writestr("questions.json", json.dumps({"version": 2, "questions": []}))
    write(os.path.join(c, "quiz", "dist", "catalog.json"), json.dumps({"packs": [{"id": "cm2-p1", "course": "cm2", "track": "primary", "level": "CM2", "field": None, "file": "quiz-cm2-p1-v1.quiz.zip"}]}))
    scopes = [{"id": "cm2", "kind": "class", "tv": True, "ord": 6}, {"id": "cm2-media", "kind": "media", "tv": False, "ord": 6}]
    write(os.path.join(c, "graph", "scopes.json"), json.dumps({"scopes": scopes}))
    write(os.path.join(c, "MEDIA-MANIFEST.json"), json.dumps({"format": 1, "assets": []}))
    return c


def asset(content, aid="img-demo-1", kind="image", ext=".webp", data=None, **kw):
    data = data if data is not None else webp(640, 480, 100)
    path = "media/%s%s" % (aid, ext)
    write(os.path.join(content, path), data)
    a = {"id": aid, "path": path, "kind": kind, "origin": "original", "licence": "CastBridge-original", "author": "agent-x", "sha256": hashlib.sha256(data).hexdigest(),
         "bytes": len(data), "lot": "learn:cm2-media", "lessons": ["p1-l1"], "alt": {"fr": "texte", "en": "text"}}
    a.update(kw)
    m = json.load(open(os.path.join(content, "MEDIA-MANIFEST.json")))
    m["assets"].append(a)
    write(os.path.join(content, "MEDIA-MANIFEST.json"), json.dumps(m))
    return a


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(); self.content = mini_content(self.tmp)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)


class BudgetTest(Base):
    def test_ok_and_per_lot_sizes(self):
        r = content_budget.analyse(self.content)
        self.assertEqual([], r["errors"])
        self.assertEqual({"learn:cm2", "quiz:cm2"}, {l["lot"] for l in r["lots"]})
        self.assertGreater(r["feature"]["learn"], 0)

    def test_tv_lot_over_3mb_fails(self):
        import os as _os
        write(os.path.join(self.content, "learn", "p1", "lessons", "big.json"), json.dumps({"chapter": "c", "x": _os.urandom(3 << 20).hex()}))  # 6 MB of hex: ~3 MB zipped
        r = content_budget.analyse(self.content)
        self.assertTrue(any("plafond d'un lot TV" in e for e in r["errors"]), r["errors"])

    def test_clip_in_tv_lot_fails_and_media_lot_is_ok(self):
        write(os.path.join(self.content, "learn", "p1", "media", "c.webm"), b"x" * 100)
        self.assertTrue(any("lot compatible TV" in e for e in content_budget.analyse(self.content)["errors"]))

    def test_unknown_level_and_path_budget(self):
        write(os.path.join(self.content, "learn", "p1", "pack.json"), json.dumps({"id": "p1", "version": 1, "level": "Galaxy", "subject": "maths", "title": "t"}))
        self.assertTrue(any("aucune portée" in e for e in content_budget.analyse(self.content)["errors"]))

    def test_oversized_figure_fails(self):
        fig = {"type": "bars", "bars": [{"label": "x" * 20, "value": i} for i in range(600)]}
        write(os.path.join(self.content, "learn", "p1", "lessons", "f.json"), json.dumps({"chapter": "c", "lessons": [{"id": "p1-l2", "title": "T", "blocks": [{"type": "illustration", "figure": fig}]}]}))
        self.assertTrue(any("> 8.0 Ko" in e or "8.0 Ko" in e for e in content_budget.analyse(self.content)["errors"]))

    def test_path_over_100mb(self):
        write(os.path.join(self.content, "graph", "paths.json"), json.dumps({"paths": [{"id": "x", "lots": ["learn:cm2"]}]}))
        old = L.PHONE_PATH_MAX
        try:
            L.PHONE_PATH_MAX = 10
            self.assertTrue(any("parcours x" in e for e in content_budget.analyse(self.content)["errors"]))
        finally:
            L.PHONE_PATH_MAX = old

    def test_total_ceiling(self):
        old = (L.TOTAL_MAX, L.TOTAL_WARN)
        try:
            L.TOTAL_MAX, L.TOTAL_WARN = 100, 50
            self.assertTrue(any("3 Go" in e for e in content_budget.analyse(self.content)["errors"]))
            L.TOTAL_MAX = 10 ** 9
            self.assertTrue(any("2,5 Go" in w for w in content_budget.analyse(self.content)["warnings"]))
        finally:
            L.TOTAL_MAX, L.TOTAL_WARN = old


class MediaTest(Base):
    def errs(self):
        return check_media.check(self.content)

    def test_valid_image(self):
        asset(self.content)
        self.assertEqual([], self.errs())

    def test_hash_and_size_mismatch_and_missing(self):
        a = asset(self.content)
        write(os.path.join(self.content, a["path"]), webp(640, 480, 5))
        e = self.errs(); self.assertTrue(any("sha256" in x for x in e)); self.assertTrue(any("taille" in x for x in e))
        os.remove(os.path.join(self.content, a["path"])); self.assertTrue(any("absent" in x for x in self.errs()))

    def test_budgets(self):
        asset(self.content, "img-big-1", data=webp(1600, 900, 10)); asset(self.content, "img-fat-1", data=webp(800, 600, 130 * 1024))
        e = self.errs(); self.assertTrue(any("1280" in x for x in e)); self.assertTrue(any("budget image" in x for x in e))

    def test_alt_caption_transcript(self):
        asset(self.content, alt={})
        self.assertTrue(any("alt" in x for x in self.errs()))
        asset(self.content, "aud-demo-1", "audio", ".opus", b"o" * 1000, durationS=10, alt={"fr": "a", "en": "a"})
        e = self.errs(); self.assertTrue(any("caption" in x for x in e)); self.assertTrue(any("transcription" in x for x in e))

    def test_audio_and_clip_budgets(self):
        asset(self.content, "aud-fat-1", "audio", ".opus", b"o" * (250 * 1024), durationS=30, caption="c", transcript="t")
        asset(self.content, "clp-long-1", "clip", ".webm", b"v" * 1000, durationS=45, caption="c", flashHz=1, poster="media/none.webp", height=480)
        e = self.errs(); self.assertTrue(any("200 Ko par 30 s" in x for x in e)); self.assertTrue(any("> 30 s" in x for x in e)); self.assertTrue(any("poster" in x for x in e))

    def test_animation_needs_reduced_motion_and_safe_flashing(self):
        asset(self.content, "ani-demo-1", "animation", ".json", b'{"frames":[]}', flashHz=5)
        e = self.errs(); self.assertTrue(any("reducedMotion" in x for x in e)); self.assertTrue(any("clignotements" in x for x in e))

    def test_provenance(self):
        asset(self.content, origin="scraped"); asset(self.content, "img-cc0-1", origin="cc0", licence="CC0-1.0"); asset(self.content, "img-gen-1", origin="generated"); asset(self.content, "img-face-1", depictsPerson=True)
        e = self.errs()
        self.assertTrue(any("scraped" in x for x in e)); self.assertTrue(any("sourceUrl" in x for x in e)); self.assertTrue(any("generator" in x for x in e)); self.assertTrue(any("identifiable" in x for x in e))

    def test_heavy_media_only_in_media_lots_and_lot_must_exist(self):
        asset(self.content, "img-tv-1", lot="learn:cm2"); asset(self.content, "img-nolot-1", lot="learn:nope")
        e = self.errs(); self.assertTrue(any("lot média" in x for x in e)); self.assertTrue(any("inconnu" in x for x in e))

    def test_undeclared_media_and_dangling_reference(self):
        write(os.path.join(self.content, "learn", "p1", "media", "stray.webp"), webp(10, 10))
        write(os.path.join(self.content, "learn", "p1", "lessons", "m.json"), json.dumps({"chapter": "c", "lessons": [{"id": "p1-l9", "title": "T", "media": ["ghost-1"]}]}))
        e = self.errs(); self.assertTrue(any("non déclaré" in x for x in e)); self.assertTrue(any("ghost-1" in x for x in e))


class LotsTest(Base):
    def packs(self):
        d = os.path.join(self.tmp, "packs"); os.makedirs(d, exist_ok=True)
        with zipfile.ZipFile(os.path.join(d, "p1-v1.learn.zip"), "w") as z:
            z.writestr("manifest.json", "{}")
        return d

    def build(self, out, **kw):
        # scopes.json of the mini tree has no `title`
        sp = os.path.join(self.content, "graph", "scopes.json"); s = json.load(open(sp))
        for x in s["scopes"]:
            x.setdefault("title", {"fr": "Classe — " + x["id"], "en": x["id"]})
        write(sp, json.dumps(s))
        argv = ["--content", self.content, "--out", out, "--learn-packs", self.packs(), "--registry", os.path.join(self.tmp, "reg.json"), "--generated-at", "2026-01-01T00:00:00Z"]
        return build_lots.main(argv + kw.get("extra", []))

    def test_build_is_deterministic_and_versions_follow_content(self):
        o1, o2 = os.path.join(self.tmp, "o1"), os.path.join(self.tmp, "o2")
        self.assertEqual(0, self.build(o1)); self.assertEqual(0, self.build(o2))
        c1, c2 = json.load(open(o1 + "/catalog.json")), json.load(open(o2 + "/catalog.json"))
        self.assertEqual(c1, c2)
        self.assertEqual({"learn:cm2", "quiz:cm2"}, {"%s:%s" % (e["feature"], e["scope"]) for e in c1["lots"]})
        self.assertEqual("UNSIGNED", c1["signature"])
        for e in c1["lots"]:
            f = "%s/castbridge-lot-%s-%s-v%d.lot" % (o1, e["feature"], e["scope"], e["version"])
            self.assertEqual(e["sha256"], hashlib.sha256(open(f, "rb").read()).hexdigest()); self.assertEqual(e["bytes"], os.path.getsize(f))
            with zipfile.ZipFile(f) as z:
                self.assertIn("lot.json", z.namelist()); m = json.loads(z.read("lot.json")); self.assertEqual(e["version"], m["version"])
        # a change without --bump is refused, with --bump the version of ONLY that lot moves
        with zipfile.ZipFile(os.path.join(self.tmp, "packs", "p1-v1.learn.zip"), "w") as z:
            z.writestr("manifest.json", '{"changed":1}')
        self.assertEqual(1, build_lots.main(["--content", self.content, "--out", os.path.join(self.tmp, "o3"), "--learn-packs", os.path.join(self.tmp, "packs"), "--registry", os.path.join(self.tmp, "reg.json")]))
        with zipfile.ZipFile(os.path.join(self.tmp, "packs", "p1-v1.learn.zip"), "w") as z:
            z.writestr("manifest.json", '{"changed":1}')
        o4 = os.path.join(self.tmp, "o4")
        self.assertEqual(0, build_lots.main(["--content", self.content, "--out", o4, "--learn-packs", os.path.join(self.tmp, "packs"), "--registry", os.path.join(self.tmp, "reg.json"), "--bump"]))
        v = {"%s:%s" % (e["feature"], e["scope"]): e["version"] for e in json.load(open(o4 + "/catalog.json"))["lots"]}
        self.assertEqual({"learn:cm2": 2, "quiz:cm2": 1}, v)

    def test_signature_verifies(self):
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
        from cryptography.hazmat.primitives import serialization
        import base64
        key = Ed25519PrivateKey.generate(); kp = os.path.join(self.tmp, "k.pem")
        open(kp, "wb").write(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        out = os.path.join(self.tmp, "o"); self.assertEqual(0, self.build(out, extra=["--sign-key", kp]))
        cat = json.load(open(out + "/catalog.json")); self.assertNotEqual("UNSIGNED", cat["signature"])
        key.public_key().verify(base64.b64decode(cat["signature"]), build_lots.canonical(cat).encode())
        cat["lots"][0]["bytes"] += 1
        with self.assertRaises(Exception):
            key.public_key().verify(base64.b64decode(cat["signature"]), build_lots.canonical(cat).encode())

    def test_release_tree(self):
        out = os.path.join(self.tmp, "o"); self.build(out)
        write(os.path.join(self.content, "graph", "mathematiques.json"), json.dumps({"domain": "mathematiques", "skills": [{"id": "math.a-b"}]}))
        tree = os.path.join(self.tmp, "tree")
        self.assertEqual(0, make_release.main(["--lots", out, "--out", tree, "--content", self.content, "--version", "9.9.9"]))
        rel = json.load(open(tree + "/CONTENT-RELEASE.json"))
        self.assertEqual("9.9.9", rel["version"]); self.assertFalse(rel["signed"]); self.assertEqual(1, rel["graph"]["skills"]); self.assertEqual(2, len(rel["lots"]))
        sums = open(tree + "/SHA256SUMS").read().splitlines()
        self.assertTrue(all(len(s.split("  ")[0]) == 64 for s in sums)); self.assertFalse(any("CONTENT-RELEASE" in s for s in sums))
        tree2 = os.path.join(self.tmp, "tree2"); make_release.main(["--lots", out, "--out", tree2, "--content", self.content, "--version", "9.9.9"])
        self.assertEqual(rel["treeSha256"], json.load(open(tree2 + "/CONTENT-RELEASE.json"))["treeSha256"])


class RepoContentTest(unittest.TestCase):
    """The real repository content."""

    def test_graph_files_are_in_sync_with_seeds(self):
        self.assertEqual(0, subprocess.run([sys.executable, os.path.join(TOOLS, "content-graph", "gen_graph.py"), "--check"], capture_output=True).returncode)

    def test_real_content_fits_the_budget_and_the_media_manifest(self):
        self.assertEqual([], content_budget.analyse(L.CONTENT)["errors"])
        self.assertEqual([], check_media.check(L.CONTENT))

    def test_mapping_proposes_a_skill_for_most_existing_lessons(self):
        r = map_existing.run(L.CONTENT, 0.12)
        self.assertGreaterEqual(sum(1 for x in r["learn"] if x["proposed"]), 0.7 * len(r["learn"]))
        ids = {s["id"] for fn in os.listdir(os.path.join(L.CONTENT, "graph")) if fn.endswith(".json") and fn not in ("scopes.json", "paths.json")
               for s in json.load(open(os.path.join(L.CONTENT, "graph", fn)))["skills"]}
        for x in r["learn"]:
            if x["proposed"]:
                self.assertIn(x["proposed"]["skill"], ids)

    def test_seed_graph_semantics(self):
        # every skill's lot appears in scopes, prerequisites exist, N2+ lots are excellence lots
        scopes = {s["id"]: s for s in json.load(open(os.path.join(L.CONTENT, "graph", "scopes.json")))["scopes"]}
        skills = {}
        for fn in os.listdir(os.path.join(L.CONTENT, "graph")):
            if fn.endswith(".json") and fn not in ("scopes.json", "paths.json"):
                for s in json.load(open(os.path.join(L.CONTENT, "graph", fn)))["skills"]:
                    skills[s["id"]] = s
        self.assertGreaterEqual(len(skills), 300)
        for s in skills.values():
            for p in s["prereq"]:
                self.assertIn(p, skills)
            for l in s["lots"]:
                self.assertIn(s["level"], scopes[l]["levels"])
        paths = json.load(open(os.path.join(L.CONTENT, "graph", "paths.json")))["paths"]
        self.assertTrue(all(len(set(p["lots"])) == len(p["lots"]) for p in paths))


class SizeGateTest(Base):
    def test_code_mode_refuses_heavy_media_and_big_binaries_content_mode_allows_lfs(self):
        sys.path.insert(0, os.path.join(TOOLS, "content-split-repo"))
        import check_sizes
        write(os.path.join(self.tmp, "content", "learn", "p1", "media", "v.webm"), b"x" * 10)
        write(os.path.join(self.tmp, "tool.bin"), b"y" * (6 << 20))
        e = check_sizes.check(self.tmp, "code")
        self.assertTrue(any("média lourd" in x for x in e)); self.assertTrue(any("tool.bin" in x for x in e))
        write(os.path.join(self.tmp, ".gitattributes"), "*.bin filter=lfs diff=lfs merge=lfs -text\n")
        e = check_sizes.check(self.tmp, "content")
        self.assertFalse(any("tool.bin" in x for x in e))

    def test_the_code_repository_has_no_heavy_media_or_big_binary(self):
        sys.path.insert(0, os.path.join(TOOLS, "content-split-repo"))
        import check_sizes
        self.assertEqual([], check_sizes.check(L.REPO, "code"))


class ReportTest(unittest.TestCase):
    sample = json.load(open(os.path.join(TOOLS, "pedagogy-report", "sample-report.json")))

    def test_sample_is_valid_and_passes(self):
        self.assertEqual([], validate_report.validate(self.sample)); self.assertEqual("pass", validate_report.decision(self.sample))

    def test_cannot_claim_pass_below_thresholds(self):
        r = copy.deepcopy(self.sample); r["scores"]["accessibility"]["score"] = 2.5
        r["scores"]["accessibility"]["issues"] = [{"severity": "major", "ref": "x", "note": "contraste"}]
        self.assertEqual("revise", validate_report.decision(r)); self.assertTrue(any("pass" in e for e in validate_report.validate(r)))
        r["decision"] = "revise"; self.assertEqual([], validate_report.validate(r))

    def test_level_fidelity_gate_for_excellence(self):
        r = copy.deepcopy(self.sample); r["levelFidelity"]["verdict"] = "partial"
        self.assertEqual("revise", validate_report.decision(r))
        r = copy.deepcopy(self.sample); r["levelFidelity"]["challengeForTopLearner"] = 1.5
        self.assertEqual("revise", validate_report.decision(r))
        r = copy.deepcopy(self.sample); r["levelFidelity"]["standard"] = "programme"
        self.assertTrue(any("standard" in e for e in validate_report.validate(r)))

    def test_blocker_rejects_and_structure_is_enforced(self):
        r = copy.deepcopy(self.sample); r["scores"]["clarity"]["issues"] = [{"severity": "blocker", "ref": "l1", "note": "faux énoncé"}]
        self.assertEqual("reject", validate_report.decision(r))
        r = copy.deepcopy(self.sample); del r["scores"]["misconceptions"]; r["humanValidation"]["dueAfterBetaMonths"] = 1; r["lot"] = "autre"
        e = validate_report.validate(r); self.assertTrue(any("misconceptions" in x for x in e)); self.assertTrue(any("humanValidation" in x for x in e)); self.assertTrue(any("lot" in x for x in e))


if __name__ == "__main__":
    unittest.main()
