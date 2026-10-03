"""Test du kit du pilote : vérifications de format et de contenu."""

import csv
import os
import re
import unittest


class TestPilotKit(unittest.TestCase):
    """Vérifications pour docs/pilot/ et content/pilote/."""

    def setUp(self):
        """Chemins des fichiers à tester."""
        self.base = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
        self.csv_file = os.path.join(self.base, "content/pilote/journal-demandes.csv")
        self.doc_files = [
            os.path.join(self.base, "docs/pilot/LOCATION-DUREE-PILOTE.md"),
            os.path.join(self.base, "docs/pilot/fiche-foyer.md"),
            os.path.join(self.base, "docs/pilot/questionnaire-S3.md"),
            os.path.join(self.base, "docs/pilot/affiche.md"),
        ]
        self.lisezmoi = os.path.join(self.base, "content/pilote/LISEZMOI-livraison.txt")

    def test_csv_header(self):
        """En-tête du CSV conforme."""
        self.assertTrue(os.path.exists(self.csv_file), f"{self.csv_file} n'existe pas")
        with open(self.csv_file, "r", encoding="utf-8") as f:
            reader = csv.reader(f)
            headers = next(reader)
            expected = ["date", "code_tv_masque", "bouquet", "unite",
                       "quantite", "jours_validite", "type"]
            self.assertEqual(
                headers, expected,
                f"En-tête attendu {expected}, reçu {headers}"
            )

    def test_no_forbidden_words(self):
        """Pas de données personnelles ou montants réels."""
        # Chercher les données réelles, pas juste les mots
        for doc in self.doc_files + [self.lisezmoi]:
            self.assertTrue(os.path.exists(doc), f"{doc} n'existe pas")
            with open(doc, "r", encoding="utf-8") as f:
                content = f.read()
                # Chercher des montants réels en XAF (1000 XAF ou plus, pas « XAF » seul)
                xaf_matches = re.findall(r'\d+\s*(?:XAF|xaf|Xaf)', content)
                self.assertEqual(
                    len(xaf_matches), 0,
                    f"Montant réel trouvé dans {os.path.basename(doc)}: {xaf_matches}"
                )
                # Chercher des numéros de téléphone réels (format Cameroun : +237 ou 0 suivi de 8-9 chiffres)
                phone_matches = re.findall(r'(?:\+237|0)\s*[1-9]\d{7,8}', content)
                self.assertEqual(
                    len(phone_matches), 0,
                    f"Numéro de téléphone trouvé dans {os.path.basename(doc)}: {phone_matches}"
                )

    def test_no_hour_day_conversions(self):
        """Pas de conversion heures/jours."""
        conversions = [
            r"96\s*h\s*=\s*4\s*j",
            r"4\s*j\s*=\s*96\s*h",
            r"1\s*j\s*=\s*24\s*h",
            r"24\s*h\s*=\s*1\s*j",
            r"12\s*h\s*=\s*1/2\s*j",
        ]
        for doc in self.doc_files + [self.lisezmoi]:
            with open(doc, "r", encoding="utf-8") as f:
                content = f.read()
                for pattern in conversions:
                    matches = re.findall(pattern, content, re.IGNORECASE)
                    self.assertEqual(
                        len(matches), 0,
                        f"Conversion heures/jours trouvée dans {os.path.basename(doc)}: {pattern}"
                    )

    def test_docs_exist(self):
        """Tous les fichiers existent."""
        for doc in self.doc_files + [self.csv_file, self.lisezmoi]:
            self.assertTrue(
                os.path.exists(doc),
                f"Fichier manquant: {doc}"
            )

    def test_csv_non_empty_content(self):
        """Le CSV a un en-tête (mais peut être vide sinon)."""
        with open(self.csv_file, "r", encoding="utf-8") as f:
            lines = f.readlines()
            self.assertGreaterEqual(
                len(lines), 1,
                "Le CSV doit avoir au moins l'en-tête"
            )

    def test_files_are_readable(self):
        """Tous les fichiers sont lisibles en UTF-8."""
        for doc in self.doc_files + [self.csv_file, self.lisezmoi]:
            try:
                with open(doc, "r", encoding="utf-8") as f:
                    f.read()
            except UnicodeDecodeError as e:
                self.fail(f"Fichier {doc} n'est pas lisible en UTF-8: {e}")


if __name__ == "__main__":
    unittest.main()
