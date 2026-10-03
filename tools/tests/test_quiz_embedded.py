"""Tests de tools/quiz-bank/build_embedded.py : TOUTES les questions du dépôt embarquées, réservables par QUESTION (30 %).
Lancer : python3 -m unittest tools/tests/test_quiz_embedded.py"""
import collections, hashlib, importlib.util, json, shutil, subprocess, sys, tempfile, unittest, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("build_embedded", ROOT / "tools/quiz-bank/build_embedded.py")
be = importlib.util.module_from_spec(spec); sys.modules["build_embedded"] = be; spec.loader.exec_module(be)

TOTAL = 217494


def catalog():
    return json.loads((be.LOTS / "catalog-lots.json").read_text("utf-8"))["lots"]


def rows_of(files, family):
    """{chemin: fichier JSON} des fichiers d'une famille (« embedded/ » ou « embedded-reserved/ »), index exclu."""
    return {n: json.loads(b) for n, b in files.items() if n.startswith(family + "/") and not n.endswith("index.json")}


class EmbeddedBuildTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = be.build()
        cls.free = rows_of(cls.files, "embedded")
        cls.res = rows_of(cls.files, "embedded-reserved")
        cls.index = json.loads(cls.files["embedded/index.json"])

    def all_rows(self):
        for fam, d in (("free", self.free), ("reserved", self.res)):
            for name, f in d.items():
                for r in f["q"]:
                    yield fam, name, f, r

    def test_total_217494_chaque_id_une_seule_fois(self):
        ids = [r[0] for _, _, _, r in self.all_rows()]
        self.assertEqual(TOTAL, len(ids))
        self.assertEqual(TOTAL, len(set(ids)))
        self.assertEqual(TOTAL, sum(e["count"] for e in self.index["levels"]))

    def test_chaque_lot_du_catalogue_est_present(self):
        lots = {l["id"]: l["questions"] for l in catalog()}
        self.assertEqual(93, len(lots))
        got = collections.Counter()
        for _, _, f, r in self.all_rows():
            got[f["lot"]] += 1
        self.assertEqual(lots, dict(got))

    def test_tous_les_niveaux_du_catalogue(self):
        keys = {e["key"] for e in self.index["levels"]}
        for k in ("cp ce1 ce2 cm1 cm2 6e 5e 4e 3e 2nde 1re tle form-1 form-2 form-3 form-5 lower-sixth upper-sixth "
                  "class-1 class-2 class-3 class-4 class-5 class-6 l1 l2 l3 culture-generale").split():
            self.assertIn(k, keys)
        self.assertEqual(28, len(keys))

    def test_30_pour_cent_reservables_par_lot_et_par_niveau(self):
        by_lot, by_level = collections.defaultdict(lambda: [0, 0]), collections.defaultdict(lambda: [0, 0])
        for fam, _, f, r in self.all_rows():
            i = 1 if fam == "reserved" else 0
            by_lot[f["lot"]][i] += 1
            by_level[be.level_key(f["level"])][i] += 1
        for k, (free, res) in list(by_lot.items()) + list(by_level.items()):
            pct = 100.0 * res / (free + res)
            # ±1 point ; un très petit lot (16 questions : 5 sur 16 = 31,25 %) ne peut pas faire mieux que ±1 question
            if abs(res - 0.3 * (free + res)) > 1.0:
                self.assertAlmostEqual(30.0, pct, delta=1.0, msg=f"{k}: {res}/{free + res} = {pct:.2f} %")

    def test_stratifie_par_difficulte(self):
        d = collections.defaultdict(lambda: [0, 0])
        for fam, _, f, r in self.all_rows():
            d[(f["lot"], r[3])][1 if fam == "reserved" else 0] += 1
        for (lot, diff), (free, res) in d.items():
            self.assertLessEqual(abs(res - 0.3 * (free + res)), 1.01, f"{lot} difficulté {diff}: {res}/{free + res}")

    def test_culture_generale_stratifiee_par_region(self):
        d = collections.defaultdict(lambda: [0, 0])
        for fam, _, f, r in self.all_rows():
            if f["level"] is None:
                d[r[1]][1 if fam == "reserved" else 0] += 1
        self.assertEqual({"CM", "AF", "WORLD"}, set(d))
        for reg, (free, res) in d.items():
            self.assertAlmostEqual(30.0, 100.0 * res / (free + res), delta=1.0, msg=reg)

    def test_marquage_deterministe_et_stable(self):
        self.assertEqual(self.files, be.build())
        # simulation d'un lot qui s'agrandit : 85 % des questions d'abord, puis tout : un id marqué doit le rester
        cat = {l["id"]: l for l in catalog()}
        flips = total = 0
        for lot_id in ("cp", "culture-cm", "droit-l1", "2nde-geographie"):
            qs = be.read_lot(cat[lot_id])
            small = [q for q in qs if int(hashlib.sha256(("g" + q["id"]).encode()).hexdigest(), 16) % 100 < 85]
            before = be.reserved_ids(small, cat[lot_id]["level"] is None)
            after = be.reserved_ids(qs, cat[lot_id]["level"] is None)
            total += len(before); flips += len(before - after)
        self.assertGreater(total, 1000)
        self.assertLessEqual(flips, 0.03 * total, f"{flips}/{total} ids marqués ont changé quand les lots ont grandi")

    def test_la_graine_est_celle_du_cahier(self):
        self.assertEqual("castbridge-quiz-reserved-v1", be.RESERVED_SEED)
        self.assertEqual(0.30, be.RESERVED_RATIO)

    def test_aucun_fichier_trop_gros_et_formats(self):
        for name, b in self.files.items():
            self.assertLessEqual(len(b), 1_200_000, name)
        for fam, d in (("free", self.free), ("reserved", self.res)):
            for name, f in d.items():
                self.assertLessEqual(len(f["q"]), be.CHUNK, name)
                self.assertEqual(fam, f["family"], name)
                self.assertTrue(name.startswith(("embedded-reserved/" if fam == "reserved" else "embedded/") + be.level_key(f["level"]) + "/"), name)

    def test_index_schema(self):
        self.assertNotIn("reservedNotEmbedded", self.index)
        self.assertNotIn("reservedLevels", self.index)
        self.assertEqual(2, self.index["v"])
        for e in self.index["levels"]:
            for k in ("key", "level", "track", "count", "freeCount", "reservedCount", "files", "reservedFiles", "lots"):
                self.assertIn(k, e, e["key"])
            self.assertEqual(e["count"], e["freeCount"] + e["reservedCount"], e["key"])
            self.assertEqual(e["freeCount"], sum(f["count"] for f in e["files"]), e["key"])
            self.assertEqual(e["reservedCount"], sum(f["count"] for f in e["reservedFiles"]), e["key"])
            for f, fam in [(f, "embedded") for f in e["files"]] + [(f, "embedded-reserved") for f in e["reservedFiles"]]:
                self.assertTrue(f["name"].startswith(fam + "/"), f["name"])
                self.assertEqual(len(self.files[f["name"]]), f["bytes"], f["name"])
                self.assertEqual(f["count"], len((self.free if fam == "embedded" else self.res)[f["name"]]["q"]), f["name"])
        # les 4 niveaux autrefois réservés en bloc ont maintenant des questions libres ET réservables
        for k in ("tle", "l1", "l2", "l3"):
            e = next(e for e in self.index["levels"] if e["key"] == k)
            self.assertGreater(e["freeCount"], 0); self.assertGreater(e["reservedCount"], 0)

    def test_statut_review_conserve_et_questions_non_modifiees(self):
        cat = {l["id"]: l for l in catalog()}
        src = {}
        for lot_id in ("cp", "droit-l1", "culture-monde"):
            for q in be.read_lot(cat[lot_id]):
                src[q["id"]] = q
        n = 0
        for _, _, f, r in self.all_rows():
            self.assertEqual("review", r[10], r[0])
            self.assertEqual(4, len(r[5])); self.assertIn(r[6], range(4))
            q = src.get(r[0])
            if q:
                n += 1
                self.assertEqual((q["question"], q["choices"], q["answer"], q["explanation"], q["difficulty"], q["category"], q["region"], q.get("field")),
                                 (r[4], r[5], r[6], r[7], r[3], r[2], r[1], r[9]), r[0])
                self.assertEqual(q["source"], f["sources"][r[8]])
        self.assertGreater(n, 1000)

    def test_le_resultat_commite_est_a_jour(self):
        for name, data in self.files.items():
            p = be.RES / name
            self.assertTrue(p.is_file(), name)
            self.assertEqual(data, p.read_bytes(), f"{name} : relancer tools/quiz-bank/build_embedded.py")
        committed = {str(p.relative_to(be.RES)) for fam in ("embedded", "embedded-reserved") for p in (be.RES / fam).rglob("*.json")}
        self.assertEqual(set(self.files), committed, "fichiers en trop ou manquants")

    def test_repartition_proportionnelle(self):
        self.assertEqual(3, sum(be.quota([10])))
        # 3 sur 10, strates de 7 : le reste est reporté d'une strate à l'autre (total du lot exact)
        self.assertEqual(round(0.3 * 21), sum(be.quota([7, 7, 7])))


class FailClosedTest(unittest.TestCase):
    def test_lot_dont_l_empreinte_differe_est_refuse(self):
        lot = dict(catalog()[0]); lot["sha256"] = "0" * 64
        with self.assertRaises(be.RefusedLot):
            be.read_lot(lot)


class ProductionExcludesReservedTest(unittest.TestCase):
    """-PquizReserved=exclude retire embedded-reserved/ du build (une version de production ne contient pas les questions réservables)."""

    def test_build_gradle_declare_la_propriete(self):
        text = (ROOT / "android/core/build.gradle.kts").read_text("utf-8")
        self.assertIn("quizReserved", text)
        self.assertIn("castbridge/quiz/embedded-reserved/**", text)

    @unittest.skipUnless(shutil.which("gradle"), "gradle absent")
    def test_le_dossier_est_exclu_du_build(self):
        # processResources du module core seul (harnais JVM sans plugin Android) dans un répertoire de build jetable
        for prop, expected in (("exclude", False), ("include", True)):
            h = ROOT / ".core-harness"
            r = subprocess.run(["sh", str(ROOT / "tools/core-harness/run.sh"), ":core:processResources", f"-PquizReserved={prop}", "-q"],
                               capture_output=True, text=True, timeout=900, cwd=ROOT)
            self.assertEqual(0, r.returncode, r.stdout[-1500:] + r.stderr[-1500:])
            out = ROOT / "android/core/build/resources/main/castbridge/quiz"
            self.assertTrue((out / "embedded").is_dir())
            self.assertEqual(expected, (out / "embedded-reserved").exists(), f"quizReserved={prop}")


if __name__ == "__main__":
    unittest.main()
