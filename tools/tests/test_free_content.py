"""Tests de l'archive des contenus libres (python3 -m unittest discover -s tools/tests -p 'test_free_content.py')."""
import io, json, os, shutil, sys, tempfile, unittest, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.normpath(os.path.join(HERE, ".."))
REPO = os.path.normpath(os.path.join(TOOLS, ".."))
sys.path.insert(0, os.path.join(TOOLS, "free-content"))
import build_free_archive as B  # noqa: E402

NOW = "2026-10-02T12:00:00Z"


def write(p, data):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "wb" if isinstance(data, bytes) else "w", **({} if isinstance(data, bytes) else {"encoding": "utf-8"})) as f:
        f.write(data)


def learn_pack(root, pid, lic=None, **extra):
    d = {"format": 1, "id": pid, "version": 2, "title": "Titre " + pid, "authors": ["CastBridge"]}
    if lic is not None:
        d["license"] = lic
    d.update(extra)
    write(os.path.join(root, "content", "learn", pid, "pack.json"), json.dumps(d))
    write(os.path.join(root, "content", "learn", pid, "lessons", "a.json"), json.dumps({"lessons": [{"id": pid + "-l1", "media": []}]}))


def langue_pack(root, pid, lic=None, media_license="CASTBRIDGE-ORIGINAL"):
    d = {"format": 1, "type": "langue", "id": pid, "version": 1, "target": "zh", "level": "a0", "theme": "salut", "source": "fr", "title": "Chinois " + pid}
    if lic is not None:
        d["license"] = lic
    write(os.path.join(root, "content", "langues", pid, "langue.json"), json.dumps(d))
    m = {"id": "zh-nihao", "file": "audio/zh-nihao.opus", "kind": "audio", "license": media_license, "synthetic": True, "voiceLicense": "GPL-3.0"}
    write(os.path.join(root, "content", "langues", pid, "media.json"), json.dumps({"format": 1, "media": [m]}))
    write(os.path.join(root, "content", "langues-media", "zh-a0-salut", "audio", "zh-nihao.opus"), b"OPUS-FAKE" * 50)
    write(os.path.join(root, "content", "langues", pid, "figures", "f1.json"), json.dumps({"type": "illustration"}))


def registry(root, free=(), reserved=()):
    write(os.path.join(root, "content", "langues", "lots.json"), json.dumps({"format": 1, "free": list(free), "reserved": list(reserved), "lots": {}}))


def run(args):
    out, err = io.StringIO(), io.StringIO()
    code = B.main(args, out, err)
    return code, out.getvalue(), err.getvalue()


class FreeContentTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp, True)
        self.repo = os.path.join(self.tmp, "repo")
        self.out = os.path.join(self.tmp, "out")
        os.makedirs(os.path.join(self.repo, "content"))
        self.no_tags = os.path.join(self.tmp, "no-tags.json")
        write(self.no_tags, json.dumps({"format": 1, "tags": {}}))

    def build(self, *extra, out=None):
        return run(["--repo", self.repo, "--tags", self.no_tags, "--out", out or self.out, "--generated-at", NOW] + list(extra))

    def test_tagged_included_untagged_excluded_with_warning(self):
        # Create a temporary free-content directory WITHOUT the license file (absent → licenseTextVerbatim False)
        fake_free_content = os.path.join(self.tmp, "fake-free-content")
        os.makedirs(os.path.join(fake_free_content, "licenses"))
        original_here = B.HERE
        B.HERE = fake_free_content
        self.addCleanup(lambda: setattr(B, "HERE", original_here))

        learn_pack(self.repo, "p-libre", "CC BY-SA 4.0")
        learn_pack(self.repo, "p-sans")
        learn_pack(self.repo, "p-autre", "CC BY 4.0")
        code, out, err = self.build()
        self.assertEqual(code, 0, err)
        self.assertIn("sans étiquette de licence : exclu", out)
        z = zipfile.ZipFile(os.path.join(self.out, "castbridge-contenus-libres-20261002-v1.zip"))
        names = z.namelist()
        self.assertEqual(names, sorted(names))
        for n in ("LISEZ-MOI.txt", "ATTRIBUTION.md", "MANIFEST.json", "LICENSE-CC-BY-SA-4.0.txt", "contenus/learn/p-libre/pack.json", "contenus/learn/p-libre/lessons/a.json"):
            self.assertIn(n, names)
        self.assertFalse([n for n in names if "p-sans" in n or "p-autre" in n])
        man = json.loads(z.read("MANIFEST.json"))
        self.assertFalse(man["licenseTextVerbatim"])
        self.assertEqual(man["packs"][0]["licenseVersion"], "4.0")
        self.assertIn("modifié par CastBridge", z.read("ATTRIBUTION.md").decode())
        self.assertIn("legalcode", z.read("LICENSE-CC-BY-SA-4.0.txt").decode())

    def test_langues_pack_with_media(self):
        langue_pack(self.repo, "zh-a0-salut-fr", "CC BY-SA 4.0")
        registry(self.repo, free=["langues:zh-a0-salut-fr"])
        code, out, err = self.build()
        self.assertEqual(code, 0, err)
        z = zipfile.ZipFile(os.path.join(self.out, "castbridge-contenus-libres-20261002-v1.zip"))
        self.assertIn("contenus/langues-media/zh-a0-salut/audio/zh-nihao.opus", z.namelist())
        self.assertIn("contenus/langues/zh-a0-salut-fr/figures/f1.json", z.namelist())
        self.assertEqual(B.check_archive(os.path.join(self.out, "castbridge-contenus-libres-20261002-v1.zip")), [])

    def test_reserved_and_tagged_refused(self):
        langue_pack(self.repo, "zh-a0-salut-fr", "CC BY-SA 4.0")
        registry(self.repo, reserved=["langues:zh-a0-salut-fr"])
        code, out, err = self.build()
        self.assertEqual(code, 2)
        self.assertIn("RÉSERVÉ", err)
        self.assertFalse(os.path.exists(self.out))

    def test_ambiguous_versionless_and_unknown_media_refused(self):
        learn_pack(self.repo, "p-mixte", ["CC BY-SA 4.0", "CC BY 4.0"])
        code, _, err = self.build()
        self.assertEqual(code, 2)
        self.assertIn("ambiguë", err)
        shutil.rmtree(os.path.join(self.repo, "content"))
        os.makedirs(os.path.join(self.repo, "content"))
        learn_pack(self.repo, "p-sans-version", "CC BY-SA")
        code, _, err = self.build()
        self.assertEqual(code, 2)
        self.assertIn("version", err)
        shutil.rmtree(os.path.join(self.repo, "content"))
        os.makedirs(os.path.join(self.repo, "content"))
        langue_pack(self.repo, "zh-a0-salut-fr", "CC BY-SA 4.0", media_license="Inconnue-X")
        code, _, err = self.build()
        self.assertEqual(code, 2)
        self.assertIn("Inconnue-X", err)

    def test_media_without_license_refused(self):
        langue_pack(self.repo, "zh-a0-salut-fr", "CC BY-SA 4.0")
        p = os.path.join(self.repo, "content", "langues", "zh-a0-salut-fr", "media.json")
        d = json.load(open(p))
        del d["media"][0]["license"]
        write(p, json.dumps(d))
        code, _, err = self.build()
        self.assertEqual(code, 2)
        self.assertIn("sans licence", err)

    def test_empty_refused_without_flag(self):
        learn_pack(self.repo, "p-sans")
        code, _, err = self.build()
        self.assertEqual(code, 2)
        self.assertEqual(self.build("--dry-run")[0], 0)

    def test_owner_tags_file_and_conflict(self):
        learn_pack(self.repo, "p-decl")
        tags = os.path.join(self.tmp, "tags.json")
        write(tags, json.dumps({"format": 1, "tags": {"p-decl": "CC BY-SA 4.0"}}))
        code, _, err = run(["--repo", self.repo, "--tags", tags, "--out", self.out, "--generated-at", NOW])
        self.assertEqual(code, 0, err)
        learn_pack(self.repo, "p-decl", "CC BY-SA 3.0")
        code, _, err = run(["--repo", self.repo, "--tags", tags, "--out", self.out, "--generated-at", NOW])
        self.assertEqual(code, 2)

    def test_reproducible_bytes(self):
        learn_pack(self.repo, "p-libre", "CC BY-SA 4.0")
        langue_pack(self.repo, "zh-a0-salut-fr", "CC BY-SA 4.0")
        o1, o2 = os.path.join(self.tmp, "o1"), os.path.join(self.tmp, "o2")
        self.assertEqual(self.build(out=o1)[0], 0)
        self.assertEqual(self.build(out=o2)[0], 0)
        n = "castbridge-contenus-libres-20261002-v1.zip"
        self.assertEqual(B.read_bytes(os.path.join(o1, n)), B.read_bytes(os.path.join(o2, n)))

    def test_check_detects_tampering(self):
        learn_pack(self.repo, "p-libre", "CC BY-SA 4.0")
        self.assertEqual(self.build()[0], 0)
        good = os.path.join(self.out, "castbridge-contenus-libres-20261002-v1.zip")
        self.assertEqual(run(["--check", good])[0], 0)
        # contenu modifié
        bad = os.path.join(self.tmp, "bad.zip")
        with zipfile.ZipFile(good) as zi, zipfile.ZipFile(bad, "w") as zo:
            for n in zi.namelist():
                data = zi.read(n)
                if n.endswith("lessons/a.json"):
                    data = data.replace(b"p-libre", b"p-LIBRE")
                zo.writestr(n, data)
        code, _, err = run(["--check", bad])
        self.assertEqual(code, 4)
        self.assertIn("modifié", err)
        # fichier ajouté
        extra = os.path.join(self.tmp, "extra.zip")
        with zipfile.ZipFile(good) as zi, zipfile.ZipFile(extra, "w") as zo:
            for n in zi.namelist():
                zo.writestr(n, zi.read(n))
            zo.writestr("contenus/learn/intrus.json", b"{}")
        self.assertEqual(run(["--check", extra])[0], 4)
        self.assertEqual(run(["--check", os.path.join(self.tmp, "absent.zip")])[0], 4)

    def test_verbatim_license_text_used_when_provided(self):
        # Create a temporary free-content directory WITH a dummy license file (provided → licenseTextVerbatim True)
        fake_free_content = os.path.join(self.tmp, "fake-free-content")
        licenses_dir = os.path.join(fake_free_content, "licenses")
        os.makedirs(licenses_dir)
        text = "Attribution-ShareAlike 4.0 International\n" + ("texte légal. " * 2000)
        write(os.path.join(licenses_dir, "CC-BY-SA-4.0.txt"), text)
        original_here = B.HERE
        B.HERE = fake_free_content
        self.addCleanup(lambda: setattr(B, "HERE", original_here))

        learn_pack(self.repo, "p-libre", "CC BY-SA 4.0")
        self.assertEqual(self.build()[0], 0)
        z = zipfile.ZipFile(os.path.join(self.out, "castbridge-contenus-libres-20261002-v1.zip"))
        self.assertTrue(json.loads(z.read("MANIFEST.json"))["licenseTextVerbatim"])

    def test_tag_parsing(self):
        P = B.parse_tag
        self.assertEqual((P("CC BY-SA 4.0")["kind"], P("CC BY-SA 4.0")["version"]), ("by-sa", "4.0"))
        self.assertEqual(P("cc-by-sa-3.0")["version"], "3.0")
        self.assertEqual(P("CC BY-SA 4.0 International")["kind"], "by-sa")
        self.assertEqual(P("CC BY-SA 4.0 / CC BY 3.0")["kind"], "ambiguous")
        self.assertEqual(P("CC BY 4.0")["kind"], "other")
        self.assertEqual(P(None)["kind"], "none")

    def test_real_repo_dry_run(self):
        out, err = io.StringIO(), io.StringIO()
        code = B.main(["--repo", REPO, "--dry-run"], out, err)
        self.assertEqual(code, 0, err.getvalue())
        self.assertIn("Résumé :", out.getvalue())
        self.assertIn("zh-a0-salut-fr", out.getvalue())

    def test_real_repo_license_text_verbatim(self):
        """Verify that the real repository reports licenseTextVerbatim=True now that the license file is present."""
        out, err = io.StringIO(), io.StringIO()
        tmp_out = os.path.join(self.tmp, "real-repo-out")
        code = B.main(["--repo", REPO, "--out", tmp_out, "--generated-at", NOW], out, err)
        self.assertEqual(code, 0, err.getvalue())
        # Check that an archive was created
        zips = [f for f in os.listdir(tmp_out) if f.endswith(".zip")]
        self.assertEqual(len(zips), 1)
        # Verify the manifest shows licenseTextVerbatim=True
        z = zipfile.ZipFile(os.path.join(tmp_out, zips[0]))
        manifest = json.loads(z.read("MANIFEST.json"))
        self.assertTrue(manifest["licenseTextVerbatim"], "Real repo should report licenseTextVerbatim=True now that CC-BY-SA-4.0.txt is present")


if __name__ == "__main__":
    unittest.main()
