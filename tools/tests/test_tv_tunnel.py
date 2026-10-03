"""tools/remote/tv-tunnel.sh : arguments, choix du téléphone, idempotence, jamais de code affiché. Aucun appareil requis (faux adb)."""
import os
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "remote" / "tv-tunnel.sh"

FAKE_ADB = r"""#!/usr/bin/env bash
echo "$*" >> "$FAKE_LOG"
if [ "$1" = "devices" ]; then printf 'List of devices attached\n%b' "$FAKE_DEVICES"; exit 0; fi
if [ "$1" = "forward" ] && [ "$2" = "--list" ]; then printf '%b' "${FAKE_FORWARDS:-}"; exit 0; fi
if [ "$1" = "-s" ] && [ "$3" = "shell" ]; then printf '%b' "${FAKE_TCP:-}"; exit 0; fi
if [ "$1" = "-s" ] && [ "$3" = "forward" ]; then exit 0; fi
exit 0
"""

LOOPBACK_BOTH = (
    "  sl  local_address remote_address st\\n"
    "   5: 0000000000000000FFFF00000100007F:494D 00000000000000000000000000000000:0000 0A 0\\n"
    "   7: 0000000000000000FFFF00000100007F:08AE 00000000000000000000000000000000:0000 0A 0\\n"
)
ONE_PHONE = "RFCR313ABNF\\tdevice\\nemulator-5580\\tdevice\\n"


class TvTunnelTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        d = Path(self.tmp.name)
        self.adb = d / "adb"
        self.adb.write_text(FAKE_ADB)
        self.adb.chmod(self.adb.stat().st_mode | stat.S_IEXEC)
        self.log = d / "log"
        self.log.write_text("")

    def tearDown(self):
        self.tmp.cleanup()

    def run_script(self, *args, devices=ONE_PHONE, tcp=LOOPBACK_BOTH, forwards=""):
        env = dict(os.environ, ADB=str(self.adb), FAKE_LOG=str(self.log), FAKE_DEVICES=devices, FAKE_TCP=tcp, FAKE_FORWARDS=forwards,
                   CB_PIN="482913", PATH="/usr/bin:/bin")
        return subprocess.run(["bash", str(SCRIPT), *args], capture_output=True, text=True, env=env, timeout=30)

    def calls(self):
        return self.log.read_text().splitlines()

    def test_help_is_french_and_exits_zero(self):
        r = self.run_script("--help")
        self.assertEqual(r.returncode, 0)
        self.assertIn("Usage : tv-tunnel.sh up|status|down", r.stdout)
        self.assertIn("Passerelle Bluetooth", r.stdout)
        self.assertEqual(self.calls(), [], "l'aide n'appelle pas adb")

    def test_missing_or_unknown_command_is_refused(self):
        self.assertEqual(self.run_script().returncode, 2)
        r = self.run_script("ouvrir")
        self.assertEqual(r.returncode, 2)
        self.assertIn("commande inconnue", r.stderr)
        self.assertEqual(self.run_script("up", "A", "B").returncode, 2)

    def test_two_phones_without_serial_is_refused(self):
        r = self.run_script("up", devices="AAA\\tdevice\\nBBB\\tdevice\\n")
        self.assertEqual(r.returncode, 2)
        self.assertIn("plusieurs téléphones", r.stderr)
        self.assertFalse(any("forward tcp" in c for c in self.calls()))

    def test_no_phone_says_so(self):
        r = self.run_script("up", devices="emulator-5580\\tdevice\\n")
        self.assertEqual(r.returncode, 3)
        self.assertIn("aucun téléphone", r.stderr)

    def test_explicit_serial_must_be_connected(self):
        self.assertEqual(self.run_script("up", "ZZZ").returncode, 3)
        r = self.run_script("up", "BBB", devices="AAA\\tdevice\\nBBB\\tdevice\\n")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("-s BBB forward tcp:2222 tcp:2222", self.calls())

    def test_up_forwards_both_ports_and_prints_the_commands_with_a_placeholder(self):
        r = self.run_script("up")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("-s RFCR313ABNF forward tcp:2222 tcp:2222", self.calls())
        self.assertIn("-s RFCR313ABNF forward tcp:18765 tcp:18765", self.calls())
        self.assertIn("ssh -p 2222 tv@127.0.0.1", r.stdout)
        self.assertIn("curl -H 'X-CB-Pin: <code>' http://127.0.0.1:18765/api/hello", r.stdout)
        self.assertIn("boucle locale", r.stdout)
        self.assertNotIn("482913", r.stdout + r.stderr, "jamais le code, même présent dans l'environnement")

    def test_up_is_idempotent(self):
        self.assertEqual(self.run_script("up").returncode, 0)
        self.assertEqual(self.run_script("up").returncode, 0)
        self.assertEqual(sum(1 for c in self.calls() if c == "-s RFCR313ABNF forward tcp:2222 tcp:2222"), 2)

    def test_up_refuses_when_the_gateway_does_not_listen(self):
        r = self.run_script("up", tcp="  sl  local_address\\n")
        self.assertEqual(r.returncode, 5)
        self.assertIn("Passerelle Bluetooth", r.stderr)
        self.assertFalse(any("forward tcp" in c for c in self.calls()))

    def test_up_says_when_ssh_is_exposed_on_the_phone_networks(self):
        tcp = "   7: 00000000000000000000000000000000:08AE 00000000000000000000000000000000:0000 0A 0\\n"
        r = self.run_script("up", tcp=tcp)
        self.assertEqual(r.returncode, 0)
        self.assertIn("EXPOSÉ", r.stdout)
        self.assertIn("Port 18765 : la passerelle n'écoute pas", r.stdout)
        self.assertNotIn("-s RFCR313ABNF forward tcp:18765 tcp:18765", self.calls())

    def test_down_removes_both_and_is_harmless_twice(self):
        for _ in range(2):
            r = self.run_script("down")
            self.assertEqual(r.returncode, 0)
        self.assertEqual(sum(1 for c in self.calls() if c == "-s RFCR313ABNF forward --remove tcp:2222"), 2)
        self.assertIn("-s RFCR313ABNF forward --remove tcp:18765", self.calls())

    def test_status_lists_forwards_and_listeners(self):
        r = self.run_script("status", forwards="RFCR313ABNF tcp:2222 tcp:2222\\nOTHER tcp:2222 tcp:2222\\n")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("Redirection : tcp:2222 -> tcp:2222", r.stdout)
        self.assertEqual(r.stdout.count("Redirection :"), 1)
        self.assertIn("Port 2222 sur le téléphone : loopback", r.stdout)


if __name__ == "__main__":
    unittest.main()
