"""Tests de tools/quiz-bank/build_embedded.py (Quiz embarqué par niveau). Lancer : python3 -m unittest tools/tests/test_quiz_embedded.py"""
import importlib.util, json, sys, unittest, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("build_embedded", ROOT / "tools/quiz-bank/build_embedded.py")
be = importlib.util.module_from_spec(spec); sys.modules["build_embedded"] = be; spec.loader.exec_module(be)

RESERVED_LEVELS = {"tle", "l1", "l2", "l3"}


def level_rows(files):
    out = {}
    for name, data in files.items():
        if name == "index.json":
            continue
        d = json.loads(data)
        out[name[:-5]] = d
    return out


class EmbeddedBuildTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = be.build()
        cls.levels = level_rows(cls.files)
        cls.index = json.loads(cls.files["index.json"])

    def test_chaque_niveau_libre_a_au_moins_2000_questions(self):
        self.assertEqual(24, len(self.levels))
        for k, d in self.levels.items():
            self.assertGreaterEqual(len(d["q"]), 2000, k)
            self.assertLessEqual(len(d["q"]), 2100, k)
        self.assertIn("culture-generale", self.levels)

    def test_aucun_niveau_reserve(self):
        self.assertFalse(RESERVED_LEVELS & set(self.levels))
        self.assertEqual(sorted(RESERVED_LEVELS), self.index["reservedNotEmbedded"])
        free = be.load_families()[0]
        for e in self.index["levels"]:
            for lot in e["lots"]:
                self.assertIn("quiz:" + lot, free)

    def test_aucun_doublon_d_identifiant_et_statut_conserve(self):
        seen = set()
        for k, d in self.levels.items():
            for r in d["q"]:
                self.assertNotIn(r[0], seen, r[0]); seen.add(r[0])
                self.assertEqual("review", r[10], r[0])
                self.assertEqual(4, len(r[5]))

    def test_deterministe_octet_pour_octet(self):
        self.assertEqual(self.files, be.build())

    def test_le_resultat_commite_est_a_jour(self):
        for name, data in self.files.items():
            p = be.OUT / name
            self.assertTrue(p.is_file(), name)
            self.assertEqual(data, p.read_bytes(), f"{name} : relancer tools/quiz-bank/build_embedded.py")

    def test_questions_non_modifiees_et_ids_des_lots(self):
        cat = {l["id"]: l for l in json.loads((be.LOTS / "catalog-lots.json").read_text("utf-8"))["lots"]}
        src = {}
        for e in self.index["levels"]:
            for lot in e["lots"]:
                for q in be.read_lot(cat[lot]):
                    src[q["id"]] = q
        for d in self.levels.values():
            for r in d["q"]:
                q = src[r[0]]
                self.assertEqual((q["question"], q["choices"], q["answer"], q["explanation"], q["difficulty"], q["category"], q["region"]),
                                 (r[4], r[5], r[6], r[7], r[3], r[2], r[1]), r[0])
                self.assertEqual(q["source"], d["sources"][r[8]])

    def test_stratifie_par_lot_et_difficulte(self):
        cat = {l["id"]: l for l in json.loads((be.LOTS / "catalog-lots.json").read_text("utf-8"))["lots"]}
        for e in self.index["levels"]:
            if e["key"] != "2nde":
                continue
            self.assertGreater(len(e["lots"]), 5)
            n = {}
            for lot in e["lots"]:
                n[lot] = len(be.read_lot(cat[lot]))
            self.assertTrue(all(c > 0 for c in n.values()))
            diffs = {r[3] for r in self.levels["2nde"]["q"]}
            self.assertEqual({1, 2, 3, 4, 5}, diffs)
        regs = {r[1] for r in self.levels["culture-generale"]["q"]}
        self.assertEqual({"CM", "AF", "WORLD"}, regs)

    def test_repartition_proportionnelle(self):
        a = be.allocate(10, {"a": 100, "b": 50, "c": 1})
        self.assertEqual(10, sum(a.values()))
        self.assertEqual(3, be.allocate(5, {"a": 3, "b": 0})["a"])   # plafonné par la capacité


class FailClosedTest(unittest.TestCase):
    def setUp(self):
        self.orig = be.load_families

    def tearDown(self):
        be.load_families = self.orig

    def test_lot_absent_de_families_refuse(self):
        free, res = self.orig()
        be.load_families = lambda: (free - {"quiz:cp"}, res)
        with self.assertRaises(be.RefusedLot):
            be.build()

    def test_lot_reserve_jamais_embarque(self):
        free, res = self.orig()
        be.load_families = lambda: (free - {"quiz:cp"}, res | {"quiz:cp"})   # un niveau libre devient réservé : ignoré, pas embarqué
        files = be.build()
        self.assertNotIn("cp.json", files)
        self.assertIn("cp", json.loads(files["index.json"])["reservedNotEmbedded"])

    def test_lot_a_la_fois_libre_et_reserve_refuse(self):
        free, res = self.orig()
        be.load_families = lambda: (free, res | {"quiz:cp"})
        with self.assertRaises(be.RefusedLot):
            be.build()


if __name__ == "__main__":
    unittest.main()
