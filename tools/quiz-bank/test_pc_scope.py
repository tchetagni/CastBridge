#!/usr/bin/env python3
"""Tests of the scope « primaire et premier cycle » (CP-4e, CM2, 3e, Class 1-6, Form 1-3):
python3.12 -m unittest discover -s tools/quiz-bank -p 'test_pc*.py'"""
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import quizbank  # noqa: E402
from qb import core, courses_pc, facts_engine, qc  # noqa: E402

KNOWN_LEVELS = {"SIL", "CP", "CE1", "CE2", "CM1", "CM2", "Class 1", "Class 2", "Class 3", "Class 4", "Class 5", "Class 6", "6e", "5e", "4e", "3e", "2nde", "1re", "Tle",
                "Form 1", "Form 2", "Form 3", "Form 4", "Form 5", "Lower Sixth", "Upper Sixth"}      # QuizCatalog.levels
SAMPLE = {"cp", "ce2", "6e", "4e", "class3", "class6", "form2"}


def ints(s, lg):
    s = s.replace(" ", "").replace(" ", "")
    return int(s.replace(",", "") if lg == "en" else s)


class Scope(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        quizbank.load_generators()
        cls.qs, cls.fails = core.run_generators(only=SAMPLE, strict=False)

    def test_courses_use_levels_known_by_the_app(self):
        self.assertEqual(16, len(courses_pc.PC_COURSES))
        for k, c in courses_pc.PC_COURSES.items():
            self.assertIn(c["level"], KNOWN_LEVELS, k)
            self.assertIn(c["track"], ("primary", "secondary"))
            self.assertIsNone(c["field"])
            self.assertEqual(c["lang"], "en" if c["level"].startswith(("Class", "Form")) else "fr", k)
        prefixes = [c["prefix"] for c in core.COURSES.values()]
        self.assertEqual(len(prefixes), len(set(prefixes)), "id prefixes must be unique")

    def test_generators_pass_every_self_check_and_the_quality_control(self):
        self.assertEqual([], self.fails)
        seen = {}
        uniq = [q for q in self.qs if (q["level"], core.norm(q["question"])) not in seen and not seen.setdefault((q["level"], core.norm(q["question"])), 1)]
        res = qc.check_bank(uniq)
        self.assertEqual({}, dict(list(res["errors"].items())[:5]))
        for q in uniq:
            self.assertEqual(4, len({core.norm(c) for c in q["choices"]}), q["question"])
            self.assertEqual(q["lang"], courses_pc.PC_COURSES[next(k for k, c in courses_pc.PC_COURSES.items() if c["level"] == q["level"])]["lang"])
            self.assertEqual("review", q["status"])
            if q["lang"] == "en":
                self.assertNotIn(" ?", q["question"])

    def test_arithmetic_answers_are_right_independently_of_the_generators(self):
        pat = re.compile(r"(?:Combien font|What is) ([\d  ,]+) ([+−×÷]) ([\d  ,]+) ? ?\?")
        n = 0
        for q in self.qs:
            m = pat.fullmatch(q["question"])
            if not m:
                continue
            if q["lang"] == "fr" and "," in m.group(1) + m.group(3):
                continue                      # decimals are checked by their own model
            a, b = ints(m.group(1), q["lang"]), ints(m.group(3), q["lang"])
            op = m.group(2)
            r = {"+": a + b, "−": a - b, "×": a * b, "÷": a // b if b and a % b == 0 else None}[op]
            if r is None:
                continue                      # decimal quotient (× 10 / ÷ 100 model)
            self.assertEqual(r, ints(q["choices"][q["answer"]], q["lang"]), q["question"])
            n += 1
        self.assertGreater(n, 500)

    def test_fact_tables_of_the_scope_pass_the_quality_control(self):
        wanted = {(c["track"], c["level"], c["field"]) for k, c in core.COURSES.items() if k in courses_pc.PC_COURSES or k in ("cm2", "3e")}
        facts = [q for q in facts_engine.run() if (q["track"], q["level"], q["field"]) in wanted]
        res = qc.check_bank(facts)
        self.assertEqual({}, dict(list(res["errors"].items())[:5]))
        for q in facts:
            self.assertEqual(q["status"], "review")
            self.assertTrue(q["explanation"].strip())
        by_level = {}
        for q in facts:
            by_level.setdefault(q["level"], []).append(q)
        for lvl in ("CP", "CE1", "CE2", "CM1", "6e", "5e", "4e", "Class 1", "Class 4", "Form 1", "Form 3"):
            self.assertGreaterEqual(len(by_level.get(lvl, [])), 60, lvl)


if __name__ == "__main__":
    unittest.main()
