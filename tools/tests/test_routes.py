import pathlib, subprocess, sys, unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools" / "routes" / "list_routes.py"
ROUTES = ROOT / "tools" / "routes" / "routes.txt"


class RoutesTest(unittest.TestCase):
    def test_check_passes(self):
        r = subprocess.run([sys.executable, str(SCRIPT), "--check"], capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stderr)

    def test_known_routes_present(self):
        lines = {l.strip() for l in ROUTES.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")}
        for r in ("/api/hello", "/api/library", "/stream/", "/api/learn/packs/import", "/api/transfer/chunk"):
            self.assertIn(r, lines)
        self.assertGreaterEqual(len(lines), 150)

    def test_game_rooms_mounted_with_roomhttp_are_listed(self):
        """`RoomHttp("/chess", …)` construit ses sous-routes (`"$prefix/api/hello"`…) : l'extraction doit les lister quand même, la TV les sert toujours (régression de la migration
        des échecs sur la plateforme de jeux, où `/chess` et `/chess/api/*` avaient disparu de routes.txt)."""
        lines = {l.strip() for l in ROUTES.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")}
        for r in ("/chess", "/chess/api/hello", "/chess/api/join", "/chess/api/events", "/chess/api/state", "/chess/api/act", "/chess/api/leave"):
            self.assertIn(r, lines)


if __name__ == "__main__":
    unittest.main()
