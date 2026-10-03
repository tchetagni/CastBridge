"""Tests de tools/pilot/store_usb_pack.py : préparation du dossier CastBridge/store/ d'une clé USB (aucune signature, aucune clé)."""

import contextlib
import hashlib
import io
import json
import os
import sys
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "pilot"))

import store_usb_pack as sup  # noqa: E402

LOT = "castbridge-lot-learn-cm2-v1.lot"
DATA = b"contenu du lot" * 50


def lots_doc(sig="c2ln", data=DATA, extra=None):
    d = {"generatedAt": "2026-10-01T08:00:00Z", "signature": sig, "lots": [
        {"feature": "learn", "scope": "cm2", "version": 1, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}]}
    d.update(extra or {})
    return json.dumps(d)


def bundles_doc(sig="c2ln"):
    return json.dumps({"generatedAt": "2026-09-28T10:00:00Z", "signature": sig, "bundles": []})


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.d = self.tmp.name

    def ecrire(self, nom, contenu, mode="w"):
        p = os.path.join(self.d, nom)
        with open(p, mode) as f:
            f.write(contenu)
        return p

    def sortie(self):
        return os.path.join(self.d, "cle")


class PackTest(Base):
    def test_prepare_les_deux_catalogues_et_un_lot(self):
        lots = self.ecrire("l.json", lots_doc())
        bun = self.ecrire("b.json", bundles_doc())
        lot = self.ecrire(LOT, DATA, "wb")
        ecrits = sup.preparer(self.sortie(), lots, bun, [lot])
        dossier = os.path.join(self.sortie(), "CastBridge", "store")
        self.assertEqual(sorted(os.listdir(dossier)), sorted(["lots-catalog.json", "bundles-catalog.json", LOT]))
        self.assertEqual(len(ecrits), 3)
        with open(os.path.join(dossier, LOT), "rb") as f:
            self.assertEqual(f.read(), DATA)

    def test_preuve_a_cote_du_lot_est_copiee(self):
        lot = self.ecrire(LOT, DATA, "wb")
        self.ecrire(LOT + ".json", lots_doc())
        sup.preparer(self.sortie(), lots=[lot])
        self.assertIn(LOT + ".json", os.listdir(os.path.join(self.sortie(), "CastBridge", "store")))

    def test_document_non_signe_refuse_et_rien_ecrit(self):
        lots = self.ecrire("l.json", lots_doc(sig="UNSIGNED"))
        with self.assertRaises(sup.Refus) as e:
            sup.preparer(self.sortie(), lots)
        self.assertIn("non signé", str(e.exception))
        self.assertFalse(os.path.exists(self.sortie()))

    def test_document_trop_gros_refuse(self):
        lots = self.ecrire("l.json", lots_doc(extra={"pad": "x" * (256 * 1024)}))
        with self.assertRaises(sup.Refus) as e:
            sup.preparer(self.sortie(), lots)
        self.assertIn("trop volumineux", str(e.exception))
        bun = self.ecrire("b.json", bundles_doc().replace("[]", "[]") + " " * (64 * 1024))
        with self.assertRaises(sup.Refus):
            sup.preparer(self.sortie(), bundles_catalog=bun)

    def test_octets_de_remplissage_refuses(self):
        lots = self.ecrire("l.json", lots_doc().encode() + b"\x00" * 8, "wb")
        with self.assertRaises(sup.Refus) as e:
            sup.preparer(self.sortie(), lots)
        self.assertIn("zéros", str(e.exception))

    def test_cle_privee_refusee(self):
        lots = self.ecrire("l.json", lots_doc(extra={"k": "-----BEGIN PRIVATE KEY-----"}))
        with self.assertRaises(sup.Refus) as e:
            sup.preparer(self.sortie(), lots)
        self.assertIn("clé privée", str(e.exception))
        self.assertFalse(os.path.exists(self.sortie()))

    def test_lot_corrompu_ou_nom_pirate_refuse(self):
        lot = self.ecrire(LOT, DATA + b"!", "wb")
        self.ecrire(LOT + ".json", lots_doc())
        with self.assertRaises(sup.Refus):
            sup.preparer(self.sortie(), lots=[lot])
        mauvais = self.ecrire("..evil.lot", DATA, "wb")
        with self.assertRaises(sup.Refus):
            sup.preparer(self.sortie(), lots=[mauvais])

    def test_lot_sans_preuve_refuse(self):
        lot = self.ecrire(LOT, DATA, "wb")
        with self.assertRaises(sup.Refus) as e:
            sup.preparer(self.sortie(), lots=[lot])
        self.assertIn("preuve", str(e.exception))

    def test_lien_symbolique_refuse(self):
        reel = self.ecrire("reel.json", lots_doc())
        lien = os.path.join(self.d, "lien.json")
        os.symlink(reel, lien)
        with self.assertRaises(sup.Refus):
            sup.preparer(self.sortie(), lien)

    def test_dossier_existant_exige_remplacer(self):
        lots = self.ecrire("l.json", lots_doc())
        sup.preparer(self.sortie(), lots)
        with self.assertRaises(sup.Refus):
            sup.preparer(self.sortie(), lots)
        sup.preparer(self.sortie(), lots, remplacer=True)

    def test_rien_a_copier_refuse(self):
        with self.assertRaises(sup.Refus):
            sup.preparer(self.sortie())

    def test_main_code_de_sortie_et_aucun_secret_dans_le_source(self):
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            self.assertEqual(sup.main(["--sortie", self.sortie()]), 1)
        self.assertIn("REFUSÉ", err.getvalue())
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(sup.main(["--sortie", self.sortie(), "--catalogue-lots", self.ecrire("l.json", lots_doc())]), 0)
        self.assertIn("Prêt", out.getvalue())
        with open(sup.__file__, encoding="utf-8") as f:
            src = f.read()
        self.assertNotIn("sender", src.lower())
        self.assertNotIn("receiver", src.lower())


if __name__ == "__main__":
    unittest.main()
