import json
import shutil
import tempfile
import unittest
from pathlib import Path

import cbvalidate as c

Q = {"question": "Capitale du Cameroun ?", "choices": ["Douala", "Yaoundé", "Garoua", "Bafoussam"], "answer": 1,
     "explanation": "Yaoundé est la capitale politique."}
MCQ = {"kind": "mcq", "prompt": "2 + 2 = ?", "choices": ["3", "4", "5", "22"], "answer": 1, "explanation": "On additionne."}

LESSONS = """{
  "chapter": "ch1",
  "lessons": [
    {
      "id": "l1",
      "title": "Une leçon",
      "status": "draft",
      "blocks": []
    }
  ],
  "exercises": [
    { "id": "e1", "kind": "mcq", "prompt": "2 + 2 = ?", "choices": ["3", "4", "5", "22"], "answer": 1, "explanation": "On additionne.", "review": true },
    {
      "id": "e2",
      "kind": "truefalse",
      "prompt": "Vrai ?",
      "answer": true,
      "explanation": ""
    }
  ]
}
"""


class HashVectors(unittest.TestCase):
    """The same vectors are asserted in android/core ContentValidationTest (ContentHash.kt)."""
    def test_vectors(self):
        self.assertEqual(c.question_hash(Q), "4aba34d897c8b24d")
        self.assertEqual(c.question_hash(dict(Q, choices=["Yaoundé", "Douala", "Bafoussam", "Garoua"], answer=0)), "4aba34d897c8b24d")
        self.assertEqual(c.exercise_hash(MCQ), "a3ab444d79169a75")
        self.assertEqual(c.exercise_hash({"kind": "numeric", "prompt": "Aire d'un carré de côté 1,5 m", "answer": 2.25, "tolerance": 0.01,
                                          "explanation": "c×c"}), "023f4fbad5e10aab")
        self.assertEqual(c.exercise_hash({"kind": "truefalse", "prompt": "Vrai ?", "answer": True, "explanation": ""}), "4cfc1f36d4769884")
        self.assertEqual(c.exercise_hash({"kind": "matching", "prompt": "Associe", "pairs": [["a", "1"], ["b", "2"]], "explanation": ""}), "eefcb410223673c8")
        self.assertEqual(c.exercise_hash({"kind": "problem", "prompt": "Problème", "explanation": "",
                                          "parts": [MCQ, {"kind": "open", "prompt": "Explique", "model": "Parce que", "explanation": ""}]}), "13f6506e1935c47c")

    def test_a_change_changes_the_hash(self):
        self.assertNotEqual(c.question_hash(dict(Q, answer=0)), c.question_hash(Q))
        self.assertNotEqual(c.question_hash(dict(Q, explanation="autre")), c.question_hash(Q))
        self.assertEqual(c.question_hash(dict(Q, question="  Capitale   du Cameroun ? ")), c.question_hash(Q))

    def test_lesson_hash_ignores_validation_keys(self):
        a = {"id": "l", "title": "t", "status": "draft", "blocks": [{"type": "text", "md": "x", "review": True}]}
        b = {"id": "l", "title": "t", "status": "validated", "state": "validated", "blocks": [{"type": "text", "md": "x"}]}
        self.assertEqual(c.lesson_hash(a), c.lesson_hash(b))
        b["blocks"][0]["md"] = "y"
        self.assertNotEqual(c.lesson_hash(a), c.lesson_hash(b))


class Records(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, True)
        (self.tmp / "validation").mkdir()
        c.VALIDATION = self.tmp / "validation"
        c.APPROVALS = self.tmp / "approvals.json"
        c.LEARN = self.tmp / "learn"
        c.QUIZ_DIST = self.tmp / "dist"
        (c.LEARN / "p1" / "lessons").mkdir(parents=True)
        (c.LEARN / "p1" / "pack.json").write_text(json.dumps({"id": "p1", "level": "CM2", "subject": "maths"}), encoding="utf-8")
        (c.LEARN / "p1" / "lessons" / "a.json").write_text(LESSONS, encoding="utf-8")
        c.QUIZ_DIST.mkdir()

    def rec(self, **kw):
        r = {"id": "e1", "kind": "exercise", "state": "validated", "reviewer": "M. Dupont", "date": "2026-10-01", "note": "", "hash": None}
        r.update(kw)
        return r

    def write(self, *recs):
        (c.VALIDATION / "t.jsonl").write_text("".join(json.dumps(r) + "\n" for r in recs), encoding="utf-8")

    def items(self):
        return c.learn_items()

    def test_validation_rules(self):
        self.assertTrue(c.problems(self.rec()))                                   # validated without hash
        self.assertFalse(c.problems(self.rec(hash="a3ab444d79169a75")))
        self.assertTrue(c.problems(self.rec(state="rejected", hash="a3ab444d79169a75")))  # rejected needs a note
        self.assertFalse(c.problems(self.rec(state="rejected", note="réponse fausse")))
        self.assertTrue(c.problems(self.rec(date="01/10/2026", state="needs-fix", note="x")))

    def test_transitions(self):
        errs = []
        h = "a3ab444d79169a75"
        self.write(self.rec(state="rejected", note="faux"), self.rec(state="validated", hash=h))   # rejected -> validated is not allowed
        eff, _ = c.load_records(errs)
        self.assertEqual(eff["e1"]["state"], "rejected")
        self.assertEqual(len(errs), 1)
        errs = []
        self.write(self.rec(state="needs-fix", note="à corriger"), self.rec(state="review"), self.rec(state="validated", hash=h))
        eff, _ = c.load_records(errs)
        self.assertEqual((eff["e1"]["state"], errs), ("validated", []))

    def test_stale_decision_goes_back_to_review(self):
        rec = {"state": "validated", "hash": "0000000000000000"}
        self.assertEqual(c.effective_state(rec, "a3ab444d79169a75"), ("review", True))
        self.assertEqual(c.effective_state(dict(rec, hash="a3ab444d79169a75"), "a3ab444d79169a75"), ("validated", False))

    def test_apply_learn_patches_in_place_and_is_idempotent(self):
        its = self.items()
        self.write(self.rec(hash=its["e1"]["hash"]), self.rec(id="l1", kind="lesson", hash=its["l1"]["hash"]),
                   self.rec(id="e2", state="rejected", note="énoncé faux"))
        self.assertEqual(c.cmd_apply(type("A", (), {"dry_run": False})()), 0)
        f = c.LEARN / "p1" / "lessons" / "a.json"
        t = f.read_text(encoding="utf-8")
        d = json.loads(t)
        les, ex = d["lessons"][0], {x["id"]: x for x in d["exercises"]}
        self.assertEqual((les["status"], les["state"]), ("validated", "validated"))
        self.assertEqual((ex["e1"]["review"], ex["e1"]["state"]), (False, "validated"))
        self.assertEqual(ex["e2"]["state"], "rejected")
        self.assertIn('"chapter": "ch1"', t)                                      # the rest of the file is untouched
        self.assertEqual(self.items()["e1"]["hash"], its["e1"]["hash"])         # validation keys do not change the hash
        self.assertEqual(self.items()["l1"]["hash"], its["l1"]["hash"])
        c.cmd_apply(type("A", (), {"dry_run": False})())
        self.assertEqual(f.read_text(encoding="utf-8"), t)

    def test_apply_learn_stale_record_reopens_the_item(self):
        self.write(self.rec(hash="ffffffffffffffff"))
        c.cmd_apply(type("A", (), {"dry_run": False})())
        d = json.loads((c.LEARN / "p1" / "lessons" / "a.json").read_text(encoding="utf-8"))
        e1 = d["exercises"][0]
        self.assertEqual((e1["state"], e1["review"]), ("review", True))

    def test_apply_quiz_writes_approvals(self):
        import zipfile
        q = dict(Q, id="q1", track="secondary", level="3e", field=None, region="CM", category="Géo", difficulty=2, source="s", status="review")
        with zipfile.ZipFile(c.QUIZ_DIST / "quiz-3e-p1-v1.quiz.zip", "w") as z:
            z.writestr("manifest.json", json.dumps({"course": "3e", "track": "secondary", "level": "3e", "field": None}))
            z.writestr("questions.json", json.dumps({"version": 2, "questions": [q, dict(q, id="q2")]}))
        h = c.question_hash(q)
        self.write(self.rec(id="q1", kind="question", hash=h), self.rec(id="q2", kind="question", state="needs-fix", note="ambiguë"))
        self.assertEqual(c.quiz_items()["q1"]["lot"], "quiz/secondary/3e")             # same ids as ContentFeedback.lotOf in the apps
        c.cmd_apply(type("A", (), {"dry_run": False})())
        a = json.loads(c.APPROVALS.read_text(encoding="utf-8"))
        self.assertEqual(a["q1"]["status"], "approved")
        self.assertEqual(a["q2"]["status"], "needs-fix")

    def test_csv_import(self):
        its = self.items()
        csvf = self.tmp / "r.csv"
        csvf.write_text("id;state;reviewer;date;note\ne1;validated;A. Prof;2026-10-02;ok\ne2;rejected;A. Prof;2026-10-02;\nzz;validated;A;2026-10-02;\n", encoding="utf-8")
        rc = c.cmd_import_csv(type("A", (), {"file": str(csvf), "into": "imports.jsonl", "dry_run": False})())
        self.assertEqual(rc, 1)                                                   # e2 without note, zz unknown
        lines = (c.VALIDATION / "imports.jsonl").read_text(encoding="utf-8").splitlines()
        self.assertEqual(len(lines), 1)
        self.assertEqual(json.loads(lines[0])["hash"], its["e1"]["hash"])


if __name__ == "__main__":
    unittest.main()
