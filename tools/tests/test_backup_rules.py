"""Les règles de sauvegarde Android excluent les chemins sensibles (licences, locations, essai, coffre propriétaire)."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "android"
ANDROID = "{http://schemas.android.com/apk/res/android}"

RECEIVER = [("file", p) for p in ("activations.txt", "activations.txt.bak", "clock.txt", "grace_prompt.txt",
                                  "rental/", "lots/", "learn/", "ssh/", "policy/", "tunnel/", "content-index.key", "content-index.key.tmp")] + \
           [("sharedpref", "castbridge_tv.xml"), ("sharedpref", "castbridge_parental_reports.xml"),
            ("file", "trusted_phones.txt")]
SENDER = [("file", "owner-vault.txt"), ("sharedpref", "owner_guard.xml"), ("sharedpref", "castbridge_trust.xml"), ("sharedpref", "castbridge_pins.xml"),
          ("file", "orders/"), ("file", "lots/")]


def excludes(node):
    return {(e.get("domain"), e.get("path")) for e in node.findall("exclude")}


class BackupRules(unittest.TestCase):
    def check_module(self, module, expected):
        res = ROOT / module / "src/main"
        app = ET.parse(res / "AndroidManifest.xml").getroot().find("application")
        self.assertEqual(app.get(ANDROID + "allowBackup"), "false", module)
        self.assertEqual(app.get(ANDROID + "fullBackupContent"), "@xml/backup_rules")
        self.assertEqual(app.get(ANDROID + "dataExtractionRules"), "@xml/data_extraction_rules")
        sets = {"backup_rules.xml": excludes(ET.parse(res / "res/xml/backup_rules.xml").getroot())}
        de = ET.parse(res / "res/xml/data_extraction_rules.xml").getroot()
        sets["cloud-backup"] = excludes(de.find("cloud-backup"))
        sets["device-transfer"] = excludes(de.find("device-transfer"))
        for name, found in sets.items():
            for item in expected:
                self.assertIn(item, found, f"{module}: {item} absent de {name}")
        self.assertEqual(sets["backup_rules.xml"], sets["cloud-backup"], f"{module}: règles divergentes")
        self.assertEqual(sets["cloud-backup"], sets["device-transfer"], f"{module}: règles divergentes")

    def test_receiver(self):
        self.check_module("receiver", RECEIVER)

    def test_sender(self):
        self.check_module("sender", SENDER)


if __name__ == "__main__":
    unittest.main()
