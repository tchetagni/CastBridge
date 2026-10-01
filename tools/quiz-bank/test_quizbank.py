#!/usr/bin/env python3
"""Tests of the question-bank pipeline: python3 -m unittest discover -s tools/quiz-bank (a few seconds)."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import quizbank  # noqa: E402
from qb import balance, core, importer, pack, qc  # noqa: E402


def good(**kw):
    q = {"id": "g-1", "track": "general", "level": None, "field": None, "region": "CM", "category": "Géographie", "difficulty": 2,
         "question": "Quelle est la capitale du Cameroun ?", "choices": ["Douala", "Yaoundé", "Garoua", "Bamenda"], "answer": 1,
         "explanation": "Yaoundé est la capitale politique.", "source": "test", "status": "review", "verif": "fact", "lang": "fr", "tpl": "t"}
    q.update(kw)
    return q


class QualityControl(unittest.TestCase):
    def test_a_good_question_passes(self):
        self.assertEqual(([], []), qc.check_question(good()))

    def test_each_defect_is_caught(self):
        cases = {
            "choices": good(choices=["a", "a", "b", "c"]),
            "answer": good(answer=7),
            "mark": good(question="Quelle est la capitale du Cameroun"),
            "four": good(choices=["a", "b", "c"]),
            "order": good(choices=["Douala", "Toutes ces réponses", "Garoua", "Bamenda"]),
            "long": good(question="Q" + "x" * 300 + " ?"),
            "difficulty": good(difficulty=9),
            "region": good(region="MARS"),
            "space": good(question="Quelle  est la capitale ?"),
            "tpl": good(verif="computed", tpl=None),
            "empty": good(explanation=""),
        }
        for name, q in cases.items():
            self.assertTrue(qc.check_question(q)[0], name)

    def test_duplicates_and_answer_balance(self):
        a, b = good(id="g-1"), good(id="g-2", question="Quelle est la capitale du Cameroun?")        # same text once normalized
        res = qc.check_bank([a, b])
        self.assertIn("g-2", res["errors"])
        same_id = qc.check_bank([good(), good(question="Autre question ?")])
        self.assertIn("g-1", same_id["errors"])
        skewed = [good(id="g-%d" % i, question="Question numéro %d ?" % i, answer=1) for i in range(400)]
        self.assertTrue(any("position" in p for p in qc.check_bank(skewed)["bank"]))

    def test_near_duplicates_of_facts_are_warned(self):
        a = good(id="g-1", question="Quelle est la capitale politique du Cameroun ?")
        b = good(id="g-2", question="Quelle est la capitale du Cameroun ?", tpl="other")
        res = qc.check_bank([a, b])
        self.assertTrue(res["warnings"].get("g-2"))


class Generators(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        quizbank.load_generators()

    def test_generators_are_deterministic_and_self_checked(self):
        a, fails = core.run_generators(only={"cm2", "tle"})
        b, _ = core.run_generators(only={"cm2", "tle"})
        self.assertEqual(fails, [])
        self.assertEqual([q["id"] for q in a], [q["id"] for q in b])
        self.assertGreater(len(a), 5000)

    def test_computed_questions_pass_the_quality_control(self):
        qs, _ = core.run_generators(only={"3e", "l1-maths", "l1-eco"})
        res = qc.check_bank(qs)
        self.assertEqual({}, {i: e for i, e in list(res["errors"].items())[:5]})

    def test_exactly_one_right_answer_among_distinct_choices(self):
        qs, _ = core.run_generators(only={"3e"})
        for q in qs:
            self.assertEqual(4, len({core.norm(c) for c in q["choices"]}), q["question"])
            self.assertIn(q["answer"], range(4))

    def test_numbers_are_french_formatted(self):
        self.assertEqual("1 234 567", core.fr(1234567))
        self.assertEqual("3,5", core.fr(3.5, 1))
        self.assertEqual("−5", core.fr(-5))
        self.assertEqual("4000 FCFA", core.fcfa(4000))              # four digits are not grouped (French usage)
        self.assertEqual("12 500 FCFA", core.fcfa(12500))


class Packs(unittest.TestCase):
    def sample(self):
        return [good(id="g-%d" % i, question="Question %d ?" % i, region=("CM", "AF", "WORLD")[i % 3], difficulty=1 + i % 5, answer=i % 4) for i in range(40)]

    def test_split_is_stratified_and_pack_build_is_reproducible(self):
        qs = self.sample()
        parts = pack.split_parts(qs, part_size=10)
        self.assertEqual(4, len(parts))
        for p in parts:
            self.assertEqual(10, len(p))
        with tempfile.TemporaryDirectory() as d1, tempfile.TemporaryDirectory() as d2:
            c1 = pack.build_packs(qs, Path(d1), 1, part_size=10)
            c2 = pack.build_packs(qs, Path(d2), 1, part_size=10)
            self.assertEqual([p["sha256"] for p in c1["packs"]], [p["sha256"] for p in c2["packs"]])
            self.assertTrue(all(p["file"].startswith("quiz-general-p") for p in c1["packs"]))

    def test_balance_thins_generated_questions_but_never_facts(self):
        facts = [good(id="f-%d" % i, question="Fait %d ?" % i) for i in range(100)]
        comp = [good(id="c-%d" % i, question="Calcul %d ?" % i, verif="computed", tpl="a" if i % 2 else "b") for i in range(1000)]
        out = balance.balance(facts + comp, max_per_course=300)
        self.assertEqual(100, sum(1 for q in out if q["verif"] != "computed"))
        self.assertLessEqual(len(out), 320)


class Import(unittest.TestCase):
    def test_batches_enter_as_review_and_bad_questions_are_listed(self):
        batch = {"batch": "essai", "author": "Relecteur", "questions": [
            {"course": "general", "region": "AF", "category": "Géographie", "difficulty": 2, "question": "Quelle est la capitale du Sénégal ?",
             "choices": ["Dakar", "Thiès", "Kaolack", "Touba"], "answer": "A", "explanation": "Dakar est la capitale.", "source": "Atlas"},
            {"course": "general", "region": "AF", "category": "Géographie", "difficulty": 2, "question": "Pas de point d'interrogation",
             "choices": ["a", "b", "c", "d"], "answer": 0, "explanation": "Parce que.", "source": "Atlas"},
            {"course": "inconnu", "question": "?"},
        ]}
        with tempfile.TemporaryDirectory() as d:
            f = Path(d) / "essai.json"
            f.write_text(json.dumps(batch), encoding="utf-8")
            ok, problems = importer.import_file(f, Path(d) / "out")
            self.assertEqual(1, ok)
            self.assertEqual(2, len(problems))
            stored = importer.load_batches(Path(d) / "out")
            self.assertEqual("review", stored[0]["status"])
            self.assertEqual("import", stored[0]["verif"])
            self.assertEqual(0, stored[0]["answer"])


class Repository(unittest.TestCase):
    def test_the_whole_bank_is_clean(self):
        qs, kept, res, fails = quizbank.prepare()
        self.assertEqual([], fails)
        self.assertEqual({}, res["errors"], list(res["errors"].items())[:3])
        ids = [q["id"] for q in kept]
        self.assertEqual(len(ids), len(set(ids)))
        # every fact question points at a source fiche
        from qb import facts_engine
        for q in kept:
            if q["verif"] == "fact":
                self.assertIn(q["source"][1:q["source"].index("]")], facts_engine.SOURCES)


if __name__ == "__main__":
    unittest.main()
