"""Tests de trial_edition.py sur un dépôt synthétique : python3 -m unittest discover -s tools/trial-edition"""
import copy
import hashlib
import json
import os
import shutil
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import trial_edition as te  # noqa: E402


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False)


def make_repo(root, lessons=8, exercises=24, quiz=60, with_langues=True, quiz_scopes=("cp", "culture-cm")):
    scopes = ["cp | CP | cp-maths cp-francais", "tle-cd | Terminale | bac-maths"]
    os.makedirs(os.path.join(root, "content", "learn"), exist_ok=True)
    with open(os.path.join(root, "content", "learn", "scopes.txt"), "w") as f:
        f.write("\n".join(scopes) + "\n")
    for pid, subject in (("cp-maths", "maths"), ("cp-francais", "francais"), ("bac-maths", "maths")):
        d = os.path.join(root, "content", "learn", pid)
        w(os.path.join(d, "pack.json"), {"id": pid, "subject": subject, "chapters": [{"id": pid + "-c1", "order": 1}, {"id": pid + "-c2", "order": 2}]})
        for ch in (1, 2):
            les, exs = [], []
            for n in range(lessons):
                lid = "%s-c%d-l%d" % (pid, ch, n)
                ex_ids = ["%s-e%d" % (lid, k) for k in range(exercises // lessons)]
                blocks = [{"type": "text", "md": "x" * 200}] + [{"type": "exercise", "ref": e} for e in ex_ids]
                if n == 3:
                    blocks.append({"type": "illustration", "figure": {"kind": "shapes"}, "animation": {"a": 1}})
                les.append({"id": lid, "title": "L%d" % n, "prerequisites": ["%s-c%d-l%d" % (pid, ch, n - 1)] if n else [],
                            "exercises": ex_ids, "selfCheck": ex_ids[:1], "blocks": blocks})
                for k, e in enumerate(ex_ids):
                    exs.append({"id": e, "kind": "mcq", "prompt": "q", "explanation": "corrigé", "difficulty": 1 + (n % 3),
                                "tier": ["application", "approfondissement", "examen"][k % 3], "lesson": lid})
            w(os.path.join(d, "lessons", "ch%d.json" % ch), {"chapter": "%s-c%d" % (pid, ch), "lessons": les, "exercises": exs})
    qdir = os.path.join(root, "content", "quiz", "lots")
    os.makedirs(qdir, exist_ok=True)
    cat = {"lots": []}
    for scope in quiz_scopes:
        qs = [{"id": "q-%s-%03d" % (scope, i), "track": "general", "level": "X", "difficulty": 1 + i % 5, "question": "Q%d ?" % i,
               "choices": ["a", "b"], "answer": 0, "explanation": "e", "status": "review"} for i in range(quiz)]
        fn = "quiz-%s-p1-v1.quiz.zip" % scope
        with zipfile.ZipFile(os.path.join(qdir, fn), "w") as z:
            z.writestr("questions.json", json.dumps({"version": 1, "questions": qs}))
            z.writestr("index.json", json.dumps({"v": 1, "scope": scope, "version": 1, "count": len(qs), "contentHash": "x",
                                                   "q": {q["id"]: "%08x" % i for i, q in enumerate(qs)}}))
            z.writestr("manifest.json", json.dumps({"format": 1, "id": scope, "version": 1, "lot": {"feature": "quiz", "scope": scope, "title": scope}}))
        cat["lots"].append({"scope": scope, "file": fn, "title": "Quiz " + scope})
    w(os.path.join(qdir, "catalog-lots.json"), cat)
    if with_langues:
        base = os.path.join(root, "content", "langues")
        w(os.path.join(base, "budget.json"), {"levels": ["A0", "A1"], "languageWeightsPercent": {"zh": 50, "fr": 50}})
        for lang in ("zh", "fr"):
            for lv in ("A0", "A1"):
                pid = "%s-%s-t-fr" % (lang, lv.lower())
                units = [{"id": "%s-u%d" % (pid, n), "title": "U", "audio": "m:%s-a1" % pid, "exercises": [{"id": "x"}] * n,
                          "animations": [{"id": "an"}] if n == 2 else []} for n in range(4)]
                w(os.path.join(base, pid, "langue.json"), {"id": pid, "target": lang, "level": lv, "units": units})
                w(os.path.join(base, pid, "media.json"), {"media": [
                    {"id": pid + "-a1", "file": "audio/a1.opus", "kind": "audio", "bytes": 3000, "durationMs": 2000, "license": "CC0"},
                    {"id": pid + "-a2", "file": "audio/a2.opus", "kind": "audio", "bytes": 4000, "durationMs": 2000, "license": "CC0"},
                    {"id": pid + "-vbad", "file": "video/v0.mp4", "kind": "video", "bytes": 10, "durationMs": 1000, "license": "PROPRIETAIRE"},
                    {"id": pid + "-v1", "file": "video/v1.mp4", "kind": "video", "bytes": 500000, "durationMs": 9000, "license": "CC-BY"}]})
    return root


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp, True)
        self.cfg = te.load_cfg()
        self.cfg["expectedLanguages"] = ["zh", "fr"]
        self.cfg["levels"]["languageLevels"] = ["A0", "A1"]

    def run_sel(self, previous=frozenset(), cfg=None):
        inv, sel, files = te.run_select(self.tmp, cfg or self.cfg, None, previous)
        return inv, sel, files, te.violations(inv, sel, cfg or self.cfg, files)


class BudgetTest(Base):
    def test_valid_selection_respects_cap_and_leaves_no_subcategory_empty(self):
        make_repo(self.tmp)
        inv, sel, files, v = self.run_sel()
        self.assertEqual([], v)
        man = te.build_manifest(inv, sel, self.cfg, files)
        self.assertLessEqual(man["totalBytes"], self.cfg["capBytes"])
        for s in man["subcategories"]:
            self.assertGreaterEqual(s["selectedItems"], 1, s["key"])
        # chaque classe, matière, langue, niveau et lot Quiz est représenté
        keys = {s["key"] for s in man["subcategories"]}
        for k in ("learn/cp/maths", "learn/cp/francais", "learn/tle-cd/maths", "quiz/cp", "quiz/culture-cm", "langues/zh/A0", "langues/fr/A1"):
            self.assertIn(k, keys)

    def test_fails_when_over_the_hard_cap(self):
        make_repo(self.tmp)
        cfg = dict(self.cfg, capBytes=20000, reserveBytes=0)
        with self.assertRaises(te.Fail):          # les planchers seuls ne tiennent pas
            self.run_sel(cfg=cfg)

    def test_fails_when_a_subcategory_is_empty(self):
        make_repo(self.tmp, with_langues=False)
        inv, sel, files, v = self.run_sel()
        self.assertTrue(any("sous-catégorie vide : langues/zh/A0" in x for x in v), v)

    def test_cli_exit_code_is_one_on_failure_and_manifest_is_not_written(self):
        make_repo(self.tmp, with_langues=False)
        out = os.path.join(self.tmp, "m.json")
        self.assertEqual(1, te.main(["select", "--repo", self.tmp, "--out", out]))
        self.assertFalse(os.path.exists(out))

    def test_cli_success_writes_manifest(self):
        make_repo(self.tmp)
        out = os.path.join(self.tmp, "m.json")
        cfgp = os.path.join(self.tmp, "cfg.json")
        w(cfgp, self.cfg)
        self.assertEqual(0, te.main(["select", "--repo", self.tmp, "--out", out, "--config", cfgp]))
        self.assertTrue(json.load(open(out))["valid"])

    def test_share_cap_keeps_a_sample_not_the_whole_content(self):
        make_repo(self.tmp)
        inv, sel, files, v = self.run_sel()
        man = te.build_manifest(inv, sel, self.cfg, files)
        for s in man["subcategories"]:
            if s["totalItems"] > 12:
                self.assertLess(s["selectedItems"], s["totalItems"], s["key"])

    def test_text_lot_never_exceeds_its_limit(self):
        make_repo(self.tmp, quiz=400)
        cfg = dict(self.cfg, textLotMaxBytes=12000)
        inv, sel, files, v = self.run_sel(cfg=cfg)
        for lot, f in files.items():
            if lot[0] != "langues-media":
                self.assertLessEqual(sum(te.fsize(x) for x in f.values()), 12000, lot)


class FloorsTest(Base):
    def test_learn_floor_has_intro_hard_figure_and_corrected_exercises(self):
        make_repo(self.tmp)
        inv, sel, files, v = self.run_sel()
        roles = sel.roles["learn/cp/maths"]
        self.assertEqual("cp-maths-c1-l0", roles["introduction"])
        self.assertIn("difficile", roles)
        self.assertTrue({"exercice1", "exercice2", "exercice3"} <= set(roles))
        figs = [i for i in inv.items.values() if i.sub == "learn/cp/maths" and i.uid in sel.chosen and i.kind == "lesson" and i.a["fig"]]
        self.assertTrue(figs)

    def test_quiz_floor_is_balanced_across_difficulty_levels(self):
        make_repo(self.tmp)
        inv, sel, files, v = self.run_sel()
        for d in range(1, 6):
            n = sum(1 for i in inv.of_sub("quiz/cp") if i.uid in sel.chosen and i.a["diff"] == d)
            self.assertGreaterEqual(n, self.cfg["floors"]["quizPerDifficulty"], "N%d" % (d - 1))

    def test_languages_floor_audio_short_and_cross_video_without_unlicensed_media(self):
        make_repo(self.tmp)
        inv, sel, files, v = self.run_sel()
        for lang in ("zh", "fr"):
            for lv in ("A0", "A1"):
                self.assertIn("audio", sel.roles["langues/%s/%s" % (lang, lv)])
        self.assertTrue(any("video" in r for r in sel.roles.values()))
        videos = {k for r in sel.roles.values() for k in [r.get("video")] if k}
        self.assertFalse(any("vbad" in x for x in videos))      # licence non admise : jamais choisie
        for lang in ("zh", "fr"):                                  # au moins une vidéo par langue et par niveau
            for pos, value in ((1, lang),):
                self.assertTrue(any(i.kind == "media" and i.a["mkind"] == "video" and i.uid in sel.chosen and i.sub.split("/")[pos] == value for i in inv.items.values()))
        for lv in ("A0", "A1"):
            self.assertTrue(any(i.kind == "media" and i.a["mkind"] == "video" and i.uid in sel.chosen and i.sub.split("/")[2] == lv for i in inv.items.values()))

    def test_exam_scope_is_filled_first(self):
        make_repo(self.tmp)
        self.assertEqual(0, te.scan(self.tmp, self.cfg, {"learn"}).subs["learn/tle-cd/maths"]["tier"])
        self.assertEqual(1, te.scan(self.tmp, self.cfg, {"learn"}).subs["learn/cp/maths"]["tier"])


class DeterminismAndStabilityTest(Base):
    def manifest(self, previous=frozenset()):
        inv, sel, files, v = self.run_sel(previous)
        self.assertEqual([], v)
        return te.build_manifest(inv, sel, self.cfg, files)

    def test_same_inputs_give_the_same_manifest_byte_for_byte(self):
        make_repo(self.tmp)
        a, b = self.manifest(), self.manifest()
        self.assertEqual(json.dumps(a, sort_keys=True), json.dumps(b, sort_keys=True))

    def test_trial_ids_are_the_full_ids(self):
        make_repo(self.tmp)
        inv, sel, files, v = self.run_sel()
        full_ids = {i.id for i in inv.items.values()}
        for uid in sel.chosen:
            self.assertIn(inv.items[uid].id, full_ids)
        # le contenu projeté garde les identifiants d'origine
        pack = json.loads(files[("learn", "cp")]["cp-maths/lessons/ch1.json"])
        self.assertTrue(all(l["id"].startswith("cp-maths-c1-") for l in pack["lessons"]))

    def test_projection_drops_references_to_lessons_and_exercises_left_out(self):
        make_repo(self.tmp)
        inv, sel, files, v = self.run_sel()
        for lot, f in files.items():
            if lot[0] != "learn":
                continue
            for path, data in f.items():
                if "/lessons/" not in path:
                    continue
                d = json.loads(data)
                lids = {l["id"] for l in d["lessons"]}
                eids = {e["id"] for e in d["exercises"]}
                for l in d["lessons"]:
                    self.assertTrue(set(l["prerequisites"]) <= lids)
                    self.assertTrue(set(l["exercises"]) <= eids)

    def test_content_growth_keeps_previous_picks_and_ids(self):
        make_repo(self.tmp, lessons=8, quiz=60)
        before = self.manifest()
        prev = te.previous_uids(before)
        shutil.rmtree(self.tmp)
        os.makedirs(self.tmp)
        make_repo(self.tmp, lessons=8, quiz=120)       # le contenu complet grandit
        after = self.manifest(prev)
        self.assertTrue(prev <= te.previous_uids(after), "un élément déjà dans l'essai en est sorti sans raison")
        self.assertGreater(len(te.previous_uids(after)), len(prev))

    def test_check_detects_a_stale_manifest(self):
        make_repo(self.tmp)
        cfgp = os.path.join(self.tmp, "cfg.json")
        w(cfgp, self.cfg)
        out = os.path.join(self.tmp, "m.json")
        base = ["--repo", self.tmp, "--out", out, "--config", cfgp]
        self.assertEqual(0, te.main(["select"] + base))
        self.assertEqual(0, te.main(["check"] + base))
        make_repo(self.tmp, quiz=200)
        self.assertEqual(1, te.main(["check"] + base))

    def test_bundles_cover_every_trial_lot(self):
        make_repo(self.tmp)
        man = self.manifest()
        ids = {b["id"] for b in man["bundles"]}
        self.assertIn("tout", ids)
        for lot in man["lots"]:
            self.assertTrue(lot["bundles"], lot["scope"])           # un lot d'essai appartient à au moins un bouquet payant
            full = "%s:%s" % (lot["fullLot"]["feature"], lot["fullLot"]["scope"])
            for b in man["bundles"]:
                if b["id"] in lot["bundles"]:
                    self.assertIn(full, b["lots"])
            self.assertTrue(lot["scope"].endswith("-trial"))


if __name__ == "__main__":
    unittest.main()
