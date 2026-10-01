#!/usr/bin/env python3
"""Tests of the question-bank pipeline: python3 -m unittest discover -s tools/quiz-bank (a few seconds)."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import quizbank  # noqa: E402
from qb import balance, core, importer, lots, pack, qc  # noqa: E402


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



class Lots(unittest.TestCase):
    def qs(self, n=30, region="WORLD"):
        out = []
        for i in range(n):
            out.append(good(id="p2-%04d" % i, track="primary", level="CM2", field=None, region=region, question="Combien font %d + %d ?" % (i, i), choices=[str(2 * i), str(2 * i + 1), str(2 * i + 2), str(2 * i + 3)], answer=0, difficulty=1 + i % 5))
        return out

    def test_a_lot_is_built_with_catalog_and_stays_stable_until_its_content_changes(self):
        with tempfile.TemporaryDirectory() as d:
            out = Path(d)
            cat, lines = lots.build_lots(self.qs(), out)
            e = cat["lots"][0]
            self.assertEqual(("quiz", "cm2", 1, 30), (e["feature"], e["scope"], e["version"], e["questions"]))
            self.assertEqual(e["bytes"], (out / e["file"]).stat().st_size)
            first = (out / e["file"]).read_bytes()
            cat2, _ = lots.build_lots(self.qs(), out)                      # same content: same version, same bytes
            self.assertEqual(1, cat2["lots"][0]["version"]); self.assertEqual(first, (out / cat2["lots"][0]["file"]).read_bytes())
            changed = self.qs(); changed[3]["explanation"] = "Autre explication."
            cat3, _ = lots.build_lots(changed, out)                        # one question edited: version bump, same ids
            self.assertEqual(2, cat3["lots"][0]["version"])
            self.assertEqual(["quiz-cm2-p1-v2.quiz.zip"], sorted(p.name for p in out.glob("*.quiz.zip")))
            self.assertIn("TOTAL", lines[-1])

    def test_a_lot_above_the_cap_fails_the_build(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(lots.LotTooBig):
                lots.build_lots(self.qs(), Path(d), max_bytes=1000)

    def test_general_knowledge_is_split_by_region(self):
        qs = [good(id="g-%d" % i, region=r, question="Question numéro %d ?" % i, choices=["a%d" % i, "b%d" % i, "c%d" % i, "d%d" % i]) for i, r in enumerate(["CM", "CM", "AF", "WORLD"])]
        with tempfile.TemporaryDirectory() as d:
            cat, _ = lots.build_lots(qs, Path(d))
            self.assertEqual({"culture-cm": 2, "culture-afrique": 1, "culture-monde": 1}, {e["scope"]: e["questions"] for e in cat["lots"]})

    def test_question_hash_is_content_sensitive(self):
        a = good(); b = good(answer=2)
        self.assertNotEqual(lots.question_hash(a), lots.question_hash(b))
        self.assertEqual(8, len(lots.question_hash(a)))
class Lycee(unittest.TestCase):
    """Lycée / GCE scope (qb/lycee.py, qb/gen/lycee_*.py, qb/facts/lycee_*.py)."""

    @classmethod
    def setUpClass(cls):
        quizbank.load_generators()
        cls.mine = sorted(k for k, c in core.COURSES.items() if c["prefix"].startswith("ly-"))

    def test_courses_are_secondary_with_a_subject_field_and_the_right_language(self):
        self.assertGreater(len(self.mine), 50)
        for k in self.mine:
            c = core.COURSES[k]
            self.assertEqual("secondary", c["track"], k)
            self.assertTrue(c["field"], k)
            self.assertIn(c["level"], ("2nde", "1re", "Tle", "Form 5", "Lower Sixth", "Upper Sixth"), k)
            english = c["level"] in ("Form 5", "Lower Sixth", "Upper Sixth")
            self.assertEqual("en" if english else "fr", c.get("lang", "fr"), k)
            self.assertIn(c["field"], ("droit", "economie", "mathematiques", "physique", "geographie", "litterature", "histoire", "informatique", "chimie", "biologie", "philosophie"), k)   # QuizCatalog fields
        triples = [(c["track"], c["level"], c["field"]) for k, c in core.COURSES.items()]
        self.assertEqual(len(triples), len(set(triples)))                           # one pack set per (track, level, field): no question in two packs

    def test_generators_self_check_are_deterministic_and_pass_the_quality_control(self):
        sample = {"2nde-maths", "tle-maths", "1re-phys", "u6-chem", "f5-cs", "2nde-svt", "l6-econ", "f5-geo", "u6-lit"}
        a, fails = core.run_generators(only=sample)
        b, _ = core.run_generators(only=sample)
        self.assertEqual([], fails)
        self.assertEqual([q["id"] for q in a], [q["id"] for q in b])
        self.assertEqual({}, {i: e for i, e in list(qc.check_bank(a)["errors"].items())[:5]})

    def test_the_lycee_bank_is_review_only_and_balanced(self):
        qs, kept, res, fails = quizbank.prepare()
        mine = [q for q in kept if q["id"].startswith("ly-")]
        self.assertGreater(len(mine), 30000)
        self.assertEqual({"review"}, {q["status"] for q in mine})                 # nothing is presented as approved
        self.assertEqual([], [p for p in res["bank"] if any(p.startswith(k + " ") for k in self.mine)])   # template shares, answer positions
        for q in mine:
            if q["verif"] == "fact":
                self.assertIn(q["source"][1:q["source"].index("]")], __import__("qb.facts_engine", fromlist=["SOURCES"]).SOURCES)
        english = {"Form 5", "Lower Sixth", "Upper Sixth"}
        self.assertTrue(all(q["lang"] == "en" for q in mine if q["level"] in english))
        self.assertTrue(any(q["lang"] == "en" and q["level"] == "2nde" for q in mine))      # the English class of the francophone lycée
        self.assertTrue(all(q["lang"] == "fr" for q in mine if q["level"] in ("2nde", "1re", "Tle") and q["field"] != "litterature"))

    def test_chemistry_helpers(self):
        from qb.gen import lycee_chem as ch
        self.assertEqual({"Ca": 1, "O": 2, "H": 2}, ch.parse("Ca(OH)2"))
        self.assertEqual(18, ch.mass("H2O"))
        self.assertEqual(100, ch.mass("CaCO3"))
        self.assertEqual({"C": 2, "H": 4, "O": 2}, ch.parse("CH3COOH"))
        for eq, _ in ch.REACTIONS:                                                  # every equation of the tables is balanced atom by atom
            left, right = ch.split_eq(eq)
            self.assertEqual(ch.count(left), ch.count(right), eq)

    def test_english_imperatives_become_questions_and_notes_move_in_front(self):
        from qb.gen.lycee_common import questionize
        self.assertEqual("What is the median of 1, 2, 3?", questionize("Find the median of 1, 2, 3."))
        self.assertEqual("What are the solutions of x² = 4?", questionize("Solve x² = 4."))
        d = core.Draft("Quelle est la pression ? (R = 8,31 J/(mol·K))", "1", ["2", "3", "4"], "expl")
        from qb.gen.lycee_common import tidy
        self.assertEqual("R = 8,31 J/(mol·K). Quelle est la pression ?", tidy(d).text)


if __name__ == "__main__":
    unittest.main()
