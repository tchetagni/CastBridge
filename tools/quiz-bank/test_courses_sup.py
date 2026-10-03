#!/usr/bin/env python3
"""Tests des 26 parcours « Supérieur » (courses_sup.py) : python3 -m unittest discover -s tools/quiz-bank"""
import json
import re
import sys
import tempfile
import unittest
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import cells_report  # noqa: E402
import quizbank  # noqa: E402
from qb import core, importer, lots  # noqa: E402
from qb.courses_sup import SUP_COURSES, SUP_SCOPES  # noqa: E402

KT = ROOT / "android/core/src/main/kotlin/castbridge/core/quiz"
EMPTY = {"L1": 7, "L2": 9, "L3": 10}


def fixture(course):
    return {"course": course, "category": "Test", "difficulty": 2, "question": "Combien font deux plus deux ?",
            "choices": ["3", "4", "5", "6"], "answer": "B", "explanation": "Deux plus deux font quatre.",
            "source": "Fixture de test", "verif": "import"}


class Registration(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        quizbank.load_generators()      # tous les parcours dynamiques (lycée) sont alors enregistrés

    def test_26_courses_in_the_right_cells(self):
        self.assertEqual(26, len(SUP_COURSES))
        self.assertEqual(EMPTY, dict(Counter(c["level"] for c in SUP_COURSES.values())))
        self.assertEqual(26, len({(c["level"], c["field"]) for c in SUP_COURSES.values()}))
        fields = re.findall(r'"(\w+)" to "[^"]+"', re.search(r"val fields: List<Field> = listOf\((.*?)\)\.map", (KT / "QuizBank.kt").read_text("utf-8"), re.S).group(1))
        for k, c in SUP_COURSES.items():
            self.assertEqual("higher", c["track"])
            self.assertIn(c["field"], fields, k)
            self.assertTrue(k.startswith(c["level"].lower() + "-"), k)
            self.assertIn(k, core.COURSES)

    def test_prefixes_are_unique_among_all_courses(self):
        count = Counter(c["prefix"] for c in core.COURSES.values())
        self.assertEqual([], [p for p, n in count.items() if n > 1])
        prefixes = [c["prefix"] + "-" for c in core.COURSES.values()]
        for p in (c["prefix"] + "-" for c in SUP_COURSES.values()):    # aucun préfixe n'est le début d'un autre
            self.assertEqual([p], [q for q in prefixes if q.startswith(p)], p)

    def test_all_39_cells_have_a_course(self):
        self.assertEqual([], cells_report.registered())

    def test_scopes_follow_the_naming_rule_and_point_to_the_courses(self):
        self.assertEqual(26, len(SUP_SCOPES))
        for scope, (course, region, title) in SUP_SCOPES.items():
            self.assertIs(lots.SCOPES[scope][0], course)
            self.assertIsNone(region)
            self.assertEqual(title, SUP_COURSES[course]["label"])
        self.assertIn("maths-l3", SUP_SCOPES)
        self.assertEqual(len(lots.SCOPES), len(set(lots.SCOPES)))

    def test_kotlin_specs_mirror_python_scopes(self):
        kt = (KT / "QuizLots.kt").read_text("utf-8")
        specs = {m[0]: m for m in re.findall(r'Spec\("([^"]+)", "([^"]+)", Track\.(\w+), (null|"[^"]+"), (null|"[^"]+")', kt)}
        self.assertEqual(set(lots.SCOPES), set(specs) | {"culture-cm", "culture-afrique", "culture-monde"} & set(lots.SCOPES))
        for scope, (course, _, title) in lots.SCOPES.items():
            _, ktitle, track, level, field = specs[scope]
            c = core.COURSES[course]
            self.assertEqual(title, ktitle, scope)
            self.assertEqual(c["track"], track.lower(), scope)
            self.assertEqual(c["level"], None if level == "null" else level.strip('"'), scope)
            self.assertEqual(c["field"], None if field == "null" else field.strip('"'), scope)


class BuildSafety(unittest.TestCase):
    def test_import_accepts_a_registered_cell_and_refuses_an_unknown_one(self):
        with tempfile.TemporaryDirectory() as d:
            f = Path(d) / "higher-l1-geographie-01.json"
            f.write_text(json.dumps({"batch": "higher-l1-geographie-01", "questions": [fixture("l1-geo"), fixture("l1-geographie")]}), encoding="utf-8")
            ok, problems = importer.import_file(f, Path(d) / "out")
            self.assertEqual(1, ok)
            self.assertEqual(1, len(problems))
            self.assertIn("parcours inconnu", problems[0])
            q = importer.load_batches(Path(d) / "out")[0]
            self.assertEqual(("higher", "L1", "geographie"), (q["track"], q["level"], q["field"]))
            self.assertTrue(q["id"].startswith("hge1-"))

    def test_empty_courses_produce_no_lot_no_pack_and_a_coverage_note(self):
        from qb import pack, qc
        quizbank.load_generators()
        with tempfile.TemporaryDirectory() as d:
            cat, lines = lots.build_lots([], Path(d))
            self.assertEqual([], cat["lots"])
            self.assertEqual([], list(Path(d).glob("*.zip")))
            cat = pack.build_packs([], Path(d) / "dist", 1)
            self.assertEqual([], cat["packs"])
        cov = qc.coverage([])
        for k in SUP_COURSES:
            self.assertEqual(0, cov[k]["total"])
            self.assertEqual("sans question", cov[k]["note"])

    def test_embedded_index_builder_ignores_cells_without_lot(self):
        import build_embedded
        files = build_embedded.build()
        index = json.loads(files["embedded/index.json"].decode("utf-8")) if "embedded/index.json" in files else {}
        text = json.dumps(index)
        for scope in SUP_SCOPES:
            if scope not in {l["scope"] for l in json.loads((ROOT / "content/quiz/lots/catalog-lots.json").read_text("utf-8"))["lots"]}:
                self.assertNotIn('"%s"' % scope, text)


if __name__ == "__main__":
    unittest.main()
