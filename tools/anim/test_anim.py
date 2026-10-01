import json, os, sys, unittest
sys.path.insert(0, os.path.dirname(__file__))
import animlib, templates


class AnimLibTest(unittest.TestCase):
    def test_every_template_builds_a_complete_block_within_budget(self):
        self.assertGreaterEqual(len(templates.EXAMPLES), 15)
        for name, fn in templates.EXAMPLES.items():
            b = fn()
            self.assertEqual(b["type"], "illustration", name)
            self.assertGreaterEqual(len(b["alt"]), 12, name)
            self.assertIn("figure", b); self.assertIn("animation", b)
            self.assertLess(animlib.size_bytes(b), 40 * 1024, name)
            a = b["animation"]
            ids = [i["id"] for i in a["items"]]
            self.assertEqual(len(ids), len(set(ids)), name)
            for act in a["do"]:
                self.assertTrue(act["d"] == 0 or act["d"] >= 0.2, name)
            if a["mode"] == "steps":
                self.assertTrue(a["steps"], name)
                self.assertAlmostEqual(a["steps"][-1]["at"], max(x["at"] + x["d"] for x in a["do"]), 1, msg=name)

    def test_deterministic(self):
        for name, fn in templates.EXAMPLES.items():
            self.assertEqual(animlib.dumps(fn()), animlib.dumps(fn()), name)

    def test_examples_on_disk_are_up_to_date(self):
        d = os.path.join(os.path.dirname(__file__), "examples")
        for name, fn in templates.EXAMPLES.items():
            with open(os.path.join(d, name + ".json"), encoding="utf-8") as f:
                self.assertEqual(f.read().strip(), animlib.dumps(fn()), name + ": relancer tools/anim/build.py")

    def test_step_builder_and_fallback(self):
        a = animlib.Anim(100, 100)
        d = a.add(animlib.circle(10, 10, 5, "red"))
        h = a.add(animlib.text(50, 50, "caché", 12), alpha=0)
        with a.step("Le point avance.") as s:
            s.move(d, dx=50, d=1.0)
        self.assertEqual(a.stops, [{"at": 1.0, "say": "Le point avance."}])
        fig = a.figure()
        self.assertEqual(len(fig["items"]), 1)          # the hidden text is never revealed
        self.assertEqual(fig["items"][0]["cx"], 60)     # moved by dx
        with self.assertRaises(AssertionError):
            a.act("fade", d, 0, 0.1)                     # between 0 and 0.2 s is refused


if __name__ == "__main__":
    unittest.main()
