import base64
import pathlib
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools" / "play" / "trusted-keys-from-tv.py"


def key(n):
    return base64.b64encode(bytes([n]) * 32).decode()


FIXTURE = "\n".join([
    "# commentaire, ignoré",
    f"kid=desk-1 pub={key(1)} scopes=ISSUE_TRIAL,ISSUE_PRODUCTION,COMMAND_SUPPORT,REVOKE",
    f"kid=owner pub={key(2)} scopes=SUPER_UNLIMITED,COMMAND_OPEN_ALL,ISSUE_PRODUCTION",
    f"kid=sans-portee pub={key(3)}",
    f"kid=mauvaise pub=pas-du-base64 scopes=REVOKE",
    f"kid=desk-1bis pub={key(1)} scopes=TRANSFER",
    f"kid=fuite pub={key(4)} scopes=REVOKE priv=ne-doit-jamais-sortir",
    "priv=00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff",
    "",
])


def run(text, *args):
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as f:
        f.write(text)
    return subprocess.run([sys.executable, str(SCRIPT), "--file", f.name, *args], capture_output=True, text=True)


class TrustedKeysFromTvTest(unittest.TestCase):
    def test_env_value_equals_the_tv_list_with_its_scopes_plus_the_server_key(self):
        r = run(FIXTURE, "--server-pub", key(9))
        self.assertEqual(r.returncode, 0, r.stderr)
        entries = r.stdout.strip().split(",")
        self.assertEqual(entries, [
            f"desk-1:{key(1)}:ISSUE_TRIAL+ISSUE_PRODUCTION+COMMAND_SUPPORT+REVOKE+TRANSFER",   # deux lignes de même clé : union des portées
            f"owner:{key(2)}:SUPER_UNLIMITED+COMMAND_OPEN_ALL+ISSUE_PRODUCTION",                # les portées « super » et « tout ouvert » y sont
            f"server:{key(9)}:REVOKE+ISSUE_TRIAL+ISSUE_PRODUCTION",
        ])

    def test_lines_without_scopes_or_unreadable_or_secret_are_dropped_and_never_printed(self):
        r = run(FIXTURE, "--server-pub", key(9))
        out = r.stdout + r.stderr
        for leaked in ("ne-doit-jamais-sortir", "00112233445566778899", "pas-du-base64", key(3), key(4)):
            self.assertNotIn(leaked, out, "rien de ce qui est écarté ne doit sortir")
        self.assertIn("sans scopes=", r.stderr)

    def test_the_server_key_already_in_the_list_gets_its_scopes_merged_not_duplicated(self):
        r = run(FIXTURE, "--server-pub", key(2))
        self.assertEqual(r.stdout.count(key(2)), 1)
        self.assertIn(f"owner:{key(2)}:SUPER_UNLIMITED+COMMAND_OPEN_ALL+ISSUE_PRODUCTION+REVOKE+ISSUE_TRIAL", r.stdout)

    def test_an_empty_list_is_an_error_not_an_empty_value(self):
        r = run("# rien\npriv=abc\n", "--server-pub", key(9))
        self.assertNotEqual(r.returncode, 0)
        self.assertEqual(r.stdout, "")

    def test_a_missing_server_key_is_said(self):
        r = run(FIXTURE)
        self.assertEqual(r.returncode, 0)
        self.assertIn("--server-pub", r.stderr)
        self.assertNotIn("server:", r.stdout)


if __name__ == "__main__":
    unittest.main()
