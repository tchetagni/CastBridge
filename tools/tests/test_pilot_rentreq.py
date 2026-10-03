"""Tests de tools/pilot/rentreq.py : lecture et vérification d'une demande de location, impression de la commande W16."""

import contextlib
import io
import json
import os
import sys
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "pilot"))

import rentreq  # noqa: E402

TV = "0123456789abcdef"
CANON_NEW = ("castbridge-rent-request-v1\nat=1800000000000\nbundle=classe-cm2\nchoice=12h\nkind=new\n"
             "nonce=a1b2c3d4\norigin=tv\nperiod=0\ntv=" + TV)
CANON_EXT = ("castbridge-rent-request-v1\nat=1800000000000\nbundle=classe-cm2\nchoice=7j\nkind=extend\n"
             "nonce=a1b2c3d4\norigin=phone\nperiod=1799740800000\ntv=" + TV)
CMD_NEW = "louer.py location --tv %s --bouquet classe-cm2 --choix 12h --pilote tools/pilot/pilot.json" % TV


def run(*argv):
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        try:
            code = rentreq.main(list(argv))
        except SystemExit as e:
            code = e.code
    return code, out.getvalue(), err.getvalue()


class TestRentReq(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)

    def write(self, text, name="demande.txt"):
        path = os.path.join(self.dir.name, name)
        with open(path, "wb") as f:
            f.write(text.encode("utf-8"))
        return path

    def test_fichier_canonique_nouvelle_location(self):
        code, out, _ = run("lire", self.write(CANON_NEW))
        self.assertEqual(code, 0)
        self.assertIn(CMD_NEW, out.splitlines())
        self.assertIn("Demande : CM2 · 12 heures d'utilisation · née sur la TV le 15/01", out)

    def test_fichier_prolongation(self):
        code, out, _ = run("lire", self.write(CANON_EXT))
        self.assertEqual(code, 0)
        self.assertIn("louer.py location --tv %s --bouquet classe-cm2 --choix 7j --pilote tools/pilot/pilot.json "
                      "--prolonger --periode 1799740800000" % TV, out.splitlines())
        self.assertIn("née sur le téléphone", out)

    def test_choix_defaut(self):
        text = CANON_NEW.replace("choice=12h", "choice=defaut")
        code, out, _ = run("lire", self.write(text))
        self.assertEqual(code, 0)
        self.assertIn("--choix defaut", out)
        self.assertIn("Sans durée précise : 30 jours", out)

    def test_crlf_normalise(self):
        code, out, _ = run("lire", self.write(CANON_NEW.replace("\n", "\r\n")))
        self.assertEqual(code, 0)
        self.assertIn(CMD_NEW, out.splitlines())

    def test_fichiers_alteres_refuses(self):
        cas = {
            "ligne en trop": CANON_NEW + "\nextra=1",
            "champ manquant": CANON_NEW.replace("nonce=a1b2c3d4\n", ""),
            "choix hors grammaire": CANON_NEW.replace("choice=12h", "choice=12x"),
            "choix zéro": CANON_NEW.replace("choice=12h", "choice=0h"),
            "mauvais en-tête": CANON_NEW.replace("-v1", "-v2"),
            "ordre": "castbridge-rent-request-v1\ntv=" + TV + "\n" + CANON_NEW.split("\n", 1)[1].rsplit("\n", 1)[0],
            "nouvelle avec période": CANON_NEW.replace("period=0", "period=5"),
        }
        for nom, text in cas.items():
            with self.subTest(nom):
                code, out, err = run("lire", self.write(text))
                self.assertEqual(code, 2)
                self.assertNotIn("louer.py", out)
                self.assertIn("refusée", err.lower())

    def test_fichier_absent(self):
        code, out, err = run("lire", os.path.join(self.dir.name, "nexiste-pas.txt"))
        self.assertEqual(code, 2)
        self.assertEqual(out, "")
        self.assertIn("illisible", err)

    def test_code_court_non_verifiable(self):
        code, out, _ = run("code", "CM2-12H-0PF8", "--tv", TV, "--bouquet", "classe-cm2")
        self.assertEqual(code, 0)
        self.assertIn(CMD_NEW, out.splitlines())
        self.assertIn("code court : non vérifiable (nonce inconnu) : confirmez avec le foyer", out)

    def test_code_court_avec_nonce_exact(self):
        code, out, _ = run("code", "CM2-12H-0PF8", "--tv", TV, "--bouquet", "classe-cm2", "--nonce", "a1b2c3d4")
        self.assertEqual(code, 0)
        self.assertIn(CMD_NEW, out.splitlines())
        self.assertNotIn("non vérifiable", out)
        self.assertIn("vérifié", out)

    def test_code_court_nonce_different_refuse(self):
        code, out, err = run("code", "CM2-12H-0PF8", "--tv", TV, "--bouquet", "classe-cm2", "--nonce", "a1b2c3d5")
        self.assertEqual(code, 2)
        self.assertNotIn("louer.py", out)
        self.assertIn("ne correspondent pas", err)

    def test_code_court_defaut_et_alias_connu(self):
        code, out, _ = run("code", "CM2-DEF-1TK8", "--tv", TV, "--nonce", "0000000a")
        self.assertEqual(code, 0)
        self.assertIn("--bouquet classe-cm2 --choix defaut", out)

    def test_code_court_alias_inconnu_exige_bouquet(self):
        code, out, err = run("code", "ZZZ-12H-0PF8", "--tv", TV)
        self.assertEqual(code, 2)
        self.assertIn("--bouquet", err)
        self.assertNotIn("louer.py", out)

    def test_code_court_mal_forme(self):
        for mauvais in ("CM2-12H", "CM2-12H-0PF", "CM2-12H-0PFU", "cm2-12h-0pf8x", "CM2-0H-0PF8", "CM2-12X-0PF8"):
            with self.subTest(mauvais):
                code, out, _ = run("code", mauvais, "--tv", TV, "--bouquet", "classe-cm2")
                self.assertEqual(code, 2)
                self.assertNotIn("louer.py", out)

    def test_code_court_tv_invalide(self):
        code, _, _ = run("code", "CM2-12H-0PF8", "--tv", "xyz", "--bouquet", "classe-cm2")
        self.assertEqual(code, 2)

    def test_code_court_prolongation_explicite(self):
        code, out, _ = run("code", "CM2-12H-0PF8", "--tv", TV, "--bouquet", "classe-cm2", "--prolonger", "--periode", "5")
        self.assertEqual(code, 0)
        self.assertIn("--prolonger --periode 5", out)

    def test_bouquet_gratuit_refuse_fichier(self):
        text = CANON_NEW.replace("classe-cm2", "langues-anglais")
        code, out, err = run("lire", self.write(text))
        self.assertEqual(code, 2)
        self.assertNotIn("louer.py", out)
        self.assertIn("gratuit", err)

    def test_bouquet_gratuit_refuse_code(self):
        code, out, err = run("code", "ANG-12H-0PF8", "--tv", TV, "--bouquet", "langues")
        self.assertEqual(code, 2)
        self.assertNotIn("louer.py", out)
        self.assertIn("gratuit", err)

    def test_bouquet_gratuit_declare_dans_pilot_json(self):
        pilote = self.write(json.dumps({"freeBundles": ["classe-cm2"]}), "pilot.json")
        code, out, _ = run("lire", self.write(CANON_NEW), "--pilote", pilote)
        self.assertEqual(code, 2)
        self.assertNotIn("louer.py", out)

    def test_pilote_personnalise_dans_commande(self):
        code, out, _ = run("lire", self.write(CANON_NEW), "--pilote", "autre/pilot.json")
        self.assertEqual(code, 0)
        self.assertIn("--pilote autre/pilot.json", out)

    def test_json(self):
        code, out, _ = run("lire", self.write(CANON_EXT), "--json")
        self.assertEqual(code, 0)
        d = json.loads(out)
        self.assertEqual(d["bundle"], "classe-cm2")
        self.assertEqual(d["kind"], "extend")
        self.assertTrue(d["commande"].endswith("--prolonger --periode 1799740800000"))
        self.assertIn("avertissements", d)

    def test_aide_en_francais(self):
        code, out, _ = run("--help")
        self.assertEqual(code, 0)
        self.assertIn("demande de location", out)
        self.assertIn("ne vaut pas droit", out)

    def test_aucune_execution_ni_reseau(self):
        with open(os.path.join(ROOT, "tools", "pilot", "rentreq.py"), encoding="utf-8") as f:
            src = f.read()
        for mot in ("subprocess", "requests", "urllib", "socket", "os.system"):
            self.assertNotIn(mot, src)

    def test_vecteurs_store_rejoues(self):
        with open(os.path.join(ROOT, "tools", "activation", "store-vectors.json"), encoding="utf-8") as f:
            doc = json.load(f)
        vus = 0
        for c in doc["cases"]:
            t = c["type"]
            if t == "canonical":
                self.assertEqual(rentreq.canonique(c["fields"]), c["expect"], c["id"])
                self.assertIsNotNone(rentreq.analyser(c["expect"]), c["id"])
            elif t == "shortcode":
                self.assertEqual(rentreq.code_court(c["fields"], c["alias"]), c["expect"], c["id"])
            elif t == "parse-ok":
                self.assertIsNotNone(rentreq.analyser(c["text"]), c["id"])
            elif t == "parse-refused":
                self.assertIsNone(rentreq.analyser(c["text"]), c["id"])
            elif t == "labels":
                self.assertEqual([rentreq.libelle(x) for x in c["choices"]], c["expect"], c["id"])
            else:
                continue
            vus += 1
        self.assertGreaterEqual(vus, 10)


if __name__ == "__main__":
    unittest.main()
