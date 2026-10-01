import json
import unittest
from pathlib import Path

from helpers import HAVE_FFMPEG, pack, registries, tmpdir, tone, write_pack

from mp import asr, checks, estimate, manifest, receive, registries as reg, requests_gen
from mp.common import canonical, read_jsonl, scan_secrets, write_jsonl, write_json


class Requests(unittest.TestCase):
    def build(self, packs=None):
        d, root = tmpdir()
        self.addCleanup(d.cleanup)
        for p in packs or [pack()]:
            write_pack(root, p)
        return requests_gen.build(root)

    def test_same_packs_give_the_same_requests_and_fingerprints(self):
        a, _ = self.build()
        b, _ = self.build()
        self.assertEqual([canonical(r) for r in a], [canonical(r) for r in b])
        self.assertEqual(len({r["fingerprint"] for r in a}), len(a), "une empreinte par demande")

    def test_order_of_files_and_of_keys_does_not_matter(self):
        p1, p2 = pack("zh-a0-aaa-fr"), pack("zh-a0-bbb-fr")
        for p in (p1, p2):   # same ids in two packs would conflict: make them distinct
            for u in p["units"]:
                for v in u["vocab"]:
                    v["audio"] = "m:" + p["id"] + "-" + v["audio"][2:]
                    if "image" in v:
                        v["image"] = "m:" + p["id"] + "-" + v["image"][2:]
                for d in u["dialogues"]:
                    d["audio"] = "m:" + p["id"] + "-" + d["audio"][2:]
                    for l in d["lines"]:
                        l["audio"] = "m:" + p["id"] + "-" + l["audio"][2:]
                for x in u["exercises"]:
                    x["audio"] = "m:" + p["id"] + "-" + x["audio"][2:]
        a, ca = self.build([p1, p2])
        b, cb = self.build([p2, p1])
        self.assertEqual(ca, []); self.assertEqual(cb, [])
        self.assertEqual([canonical(r) for r in a], [canonical(r) for r in b])

    def test_fingerprint_ignores_path_and_priority_but_not_text(self):
        a, _ = self.build()
        r = a[0]
        other = dict(r, outputPath="ailleurs.opus", priority=999)
        body = {k: other[k] for k in ("kind", "lang")}
        self.assertEqual(r["fingerprint"], a[0]["fingerprint"])
        p = pack(); p["units"][0]["vocab"][0]["term"] = "您好"
        b, _ = self.build([p])
        fp = {x["id"]: x["fingerprint"] for x in a}; fp2 = {x["id"]: x["fingerprint"] for x in b}
        self.assertNotEqual(fp["t-nihao"], fp2["t-nihao"], "texte modifié = autre demande")
        self.assertEqual(fp["t-xiexie"], fp2["t-xiexie"])

    def test_text_is_copied_exactly_and_no_voice_is_named(self):
        rows, _ = self.build()
        by = {r["id"]: r for r in rows}
        self.assertEqual("你好", by["t-nihao"]["payload"]["text"])
        self.assertEqual("你好！", by["t-d1-a"]["payload"]["text"], "ponctuation conservée telle quelle")
        for r in rows:
            blob = canonical(r).lower()
            self.assertNotIn("voiceid", blob); self.assertNotIn("api_voice", blob)
        self.assertEqual("zh-CN", by["t-nihao"]["voiceClass"]["variety"])
        self.assertNotEqual(by["t-d1-a"]["voiceClass"]["gender"], by["t-d1-b"]["voiceClass"]["gender"], "une voix par locuteur")

    def test_what_cannot_be_determined_is_blocked_not_invented(self):
        rows, _ = self.build()
        by = {r["id"]: r for r in rows}
        self.assertIn("blocked", by["t-mcq"], "audio d'un QCM : texte à dire inconnu")
        self.assertEqual("谢谢", by["t-dict"]["payload"]["text"], "dictée : le texte dicté est la réponse")
        en, _ = self.build([pack("en-a0-test-fr", "en")])
        self.assertTrue(all("blocked" in r for r in en if r["kind"] == "audio"), "variété de l'anglais à choisir par le propriétaire")

    def test_priority_puts_a0_a2_audio_words_before_dialogues_and_video(self):
        rows, _ = self.build([pack(level="A0")])
        order = [r["id"] for r in rows]
        self.assertLess(order.index("t-nihao"), order.index("t-d1"))
        self.assertLess(order.index("t-d1-a"), order.index("t-d1"))
        self.assertLess(order.index("t-d1"), order.index("t-img-nihao"), "audio avant image")
        late = pack("zh-b2-test-fr", "zh", "B2")
        for u in late["units"]:
            u["vocab"][0]["audio"] = "m:b2-nihao"; u["vocab"][0]["image"] = "m:b2-img"
            u["vocab"][1]["audio"] = "m:b2-xiexie"
            for d in u["dialogues"]:
                d["audio"] = "m:b2-d1"; d["lines"][0]["audio"] = "m:b2-a"; d["lines"][1]["audio"] = "m:b2-b"
            for x in u["exercises"]:
                x["audio"] = "m:b2-" + x["id"][-2:]
        rows, c = self.build([pack(level="A0"), late])
        ids = [r["id"] for r in rows]
        self.assertEqual([], c)
        self.assertLess(max(ids.index(i) for i in ("t-nihao", "t-d1", "t-img-nihao")), ids.index("b2-nihao"), "A0-A2 avant B2, même l'image d'A0")

    def test_same_id_with_different_texts_is_a_conflict(self):
        p = pack(); p["units"][0]["exercises"][0]["audio"] = "m:t-nihao"
        _, conflicts = self.build([p])
        self.assertEqual("t-nihao", conflicts[0]["id"])


class Estimate(unittest.TestCase):
    def rows(self):
        d, root = tmpdir(); self.addCleanup(d.cleanup)
        write_pack(root, pack()); return requests_gen.build(root)[0]

    def test_empty_pricing_means_unknown_cost_never_zero(self):
        s = estimate.summarize(self.rows(), dict(estimate.EMPTY_PRICING))
        self.assertIsNone(s["cost"]); self.assertGreater(s["chars"], 0); self.assertIn("inconnu", s["costNote"])
        self.assertIsNone(estimate.EMPTY_PRICING["audio"]["perMillionChars"], "aucun prix en dur")

    def test_cost_from_a_fixture_pricing_file(self):
        pricing = {"currency": "TEST", "gridDate": "1999-01-01", "audio": {"perMillionChars": 1_000_000.0}, "image": {"perImage": 0.5}, "video": {"perSecond": 2.0}}   # valeurs de TEST, pas des tarifs
        s = estimate.summarize(self.rows(), pricing)
        self.assertEqual(round(s["chars"] * 1.0 + s["images"] * 0.5, 6), s["cost"])

    def test_caps_stop_before_overrun_and_defer_the_rest(self):
        rows = self.rows()
        kept, later, stop = estimate.plan(rows, dict(estimate.EMPTY_PRICING), {"maxChars": 4})
        self.assertEqual("maxChars", stop)
        self.assertLessEqual(sum(estimate.measure(r)["chars"] for r in kept), 4)
        self.assertTrue(later)

    def test_a_money_cap_without_a_known_price_refuses(self):
        with self.assertRaises(estimate.PricingError):
            estimate.plan(self.rows(), dict(estimate.EMPTY_PRICING), {"maxCost": 10.0})

    def test_money_cap_with_prices(self):
        pricing = {"currency": "TEST", "audio": {"perMillionChars": 1_000_000.0}, "image": {"perImage": 1.0}, "video": {"perSecond": 1.0}}
        kept, later, stop = estimate.plan(self.rows(), pricing, {"maxCost": 5.0})
        self.assertEqual("maxCost", stop)
        self.assertLessEqual(estimate.summarize(kept, pricing)["cost"], 5.0)

    def test_resume_skips_what_was_produced_and_never_counts_twice(self):
        d, root = tmpdir(); self.addCleanup(d.cleanup)
        rows = self.rows()
        st = estimate.State(root / "state.json")
        first = next(r for r in rows if not r.get("blocked"))
        self.assertTrue(st.record(first))
        self.assertFalse(st.record(first), "même empreinte : jamais comptée deux fois")
        again = estimate.State(root / "state.json")           # après interruption : l'état est relu du fichier
        self.assertEqual(1, again.data["spent"]["requests"])
        kept, _, _ = estimate.plan(rows, dict(estimate.EMPTY_PRICING), {}, again)
        self.assertNotIn(first["id"], [r["id"] for r in kept])
        kept, _, stop = estimate.plan(rows, dict(estimate.EMPTY_PRICING), {"maxRequests": 1}, again)
        self.assertEqual("maxRequests", stop, "la dépense déjà faite compte dans le plafond")


class Registries(unittest.TestCase):
    def voices(self, status="approved", **extra):
        v = {"id": "v1", "apiVoiceId": "REEL", "engineId": "eng1", "lang": "zh", "variety": "zh-CN", "voiceClass": {"gender": "f", "ageBand": "adulte", "register": "lent"},
             "type": "voix_de_catalogue_preconstruite", "status": status}
        v.update(extra)
        return {"format": 1, "voices": [v], "mapping": {"zh-CN|f|adulte|lent": "v1"}}

    def test_empty_templates_are_valid_and_nothing_is_ready(self):
        base = Path(__file__).resolve().parents[1]
        reg_dir = base / "templates/registry" if (base / "templates/registry").is_dir() else base.parents[1] / "registry"    # dépôt de code : templates/ ; castbridge-content : registry/
        eng = json.loads((reg_dir / "engines-registry.json").read_text())
        voi = json.loads((reg_dir / "voices-registry.json").read_text())
        self.assertEqual([], reg.validate_registries(eng, voi))
        self.assertEqual([], eng["engines"]); self.assertEqual([], voi["voices"]); self.assertEqual({}, voi["mapping"])

    def test_only_approved_voice_of_an_approved_engine_is_usable(self):
        cls = {"variety": "zh-CN", "gender": "f", "ageBand": "adulte", "register": "lent"}
        v, why = reg.voice_for(cls, registries(), self.voices())
        self.assertEqual("v1", v["id"])
        self.assertIsNone(reg.voice_for(cls, registries(), self.voices(status="à choisir"))[0])
        self.assertIsNone(reg.voice_for(cls, registries(approved=False), self.voices())[0])
        self.assertIsNone(reg.voice_for({**cls, "gender": "m"}, registries(), self.voices())[0], "aucune voix choisie pour cette classe")

    def test_cloned_or_custom_or_wrong_type_is_refused(self):
        for extra in ({"cloned": True}, {"customVoice": True}, {"referenceAudio": "x.wav"}, {"imitatesRealPerson": True}, {"type": "voix_personnalisee"}):
            problems = reg.validate_registries(registries(), self.voices(**extra))
            self.assertTrue(problems, extra)

    def test_approved_voice_needs_every_field(self):
        v = self.voices(apiVoiceId=None)
        self.assertTrue(any("champs manquants" in p for p in reg.validate_registries(registries(), v)))


@unittest.skipUnless(HAVE_FFMPEG, "ffmpeg/ffprobe absents : contrôles média non exécutés")
class Media(unittest.TestCase):
    REQ = {"kind": "audio", "constraints": {"codec": "opus", "container": "ogg", "channels": 1, "sampleRateHz": 16000, "bitrateKbps": 16, "maxDurationS": 4, "targetLufs": -16}}

    def setUp(self):
        d, self.root = tmpdir(); self.addCleanup(d.cleanup)

    def test_conforming_audio_passes(self):
        problems, m = checks.check_audio(tone(self.root / "a.opus"), self.REQ)
        self.assertEqual([], problems)
        self.assertEqual((16000, 1, "opus"), (m["sampleRateHz"], m["channels"], m["codec"]))

    def test_wrong_rate_channels_duration_level_silence_and_tags_are_each_reported(self):
        cases = [
            (tone(self.root / "r.opus", rate=48000), "fréquence"),
            (tone(self.root / "s.opus", channels=2), "canaux"),
            (tone(self.root / "l.opus", seconds=6), "durée"),
            (tone(self.root / "q.opus", lufs=-30), "LUFS"),
            (tone(self.root / "h.opus", lead=1.5), "silence en tête"),
            (tone(self.root / "t.opus", tags={"artist": "Quelqu'un"}), "personnelles"),
            (tone(self.root / "b.opus", bitrate="64k"), "débit"),
        ]
        for f, word in cases:
            problems, _ = checks.check_audio(f, self.REQ)
            self.assertTrue(any(word in p for p in problems), "%s : %s" % (word, problems))

    def test_image_webp_limits_and_metadata(self):
        req = {"kind": "image", "constraints": {"maxBytes": 120 * 1024}}
        ok = self.root / "i.webp"; ok.write_bytes(b"RIFF" + (100).to_bytes(4, "little") + b"WEBPVP8 " + b"\0" * 100)
        self.assertEqual([], checks.check_image(ok, req)[0])
        big = self.root / "b.webp"; big.write_bytes(b"RIFF\0\0\0\0WEBP" + b"\0" * (130 * 1024))
        self.assertTrue(any("au-dessus" in p for p in checks.check_image(big, req)[0]))
        exif = self.root / "e.webp"; exif.write_bytes(b"RIFF\0\0\0\0WEBPEXIF" + b"\0" * 50)
        self.assertTrue(any("EXIF" in p for p in checks.check_image(exif, req)[0]))
        png = self.root / "n.webp"; png.write_bytes(b"\x89PNG....")
        self.assertTrue(any("WebP" in p for p in checks.check_image(png, req)[0]))

    def test_lot_and_quota_ceilings(self):
        self.assertEqual([], checks.check_totals([50 << 20]))
        self.assertTrue(checks.check_totals([60 << 20, 50 << 20]))
        self.assertTrue(checks.check_totals([10 << 20], phone_bytes_before=495 << 20))


class AsrAndReceive(unittest.TestCase):
    def setUp(self):
        d, self.root = tmpdir(); self.addCleanup(d.cleanup)
        write_pack(self.root / "packs", pack())
        rows, _ = requests_gen.build(self.root / "packs")
        self.rows = [r for r in rows if r["id"] in ("t-nihao", "t-xiexie")]
        self.req_path = self.root / "req.jsonl"; write_jsonl(self.req_path, self.rows)
        self.engines = registries()
        self.voices = {"format": 1, "voices": [{"id": "v1", "apiVoiceId": "REEL", "engineId": "eng1", "lang": "zh", "variety": "zh-CN",
                                                 "voiceClass": {"gender": "neutre", "ageBand": "adulte", "register": "lent"}, "type": "voix_de_catalogue_preconstruite", "status": "approved"}],
                       "mapping": {reg.class_key(self.rows[0]["voiceClass"]): "v1"}}

    def test_asr_gap_is_a_rejection_and_missing_result_is_not_an_acceptance(self):
        r = self.rows[0]
        self.assertEqual("accepte", asr.compare(r, {"fingerprint": r["fingerprint"], "transcript": "你好"})[0])
        self.assertEqual("accepte", asr.compare(r, {"transcript": "你 好。"})[0], "ponctuation et espaces ignorés pour le chinois")
        self.assertEqual("rejete", asr.compare(r, {"transcript": "您好"})[0])
        self.assertEqual("non_verifiable", asr.compare(r, None)[0])
        self.assertIn("non vérifiable dans cet environnement", asr.compare(r, None)[1])
        self.assertEqual("rejete", asr.compare(r, {"fingerprint": "autre", "transcript": "你好"})[0])

    @unittest.skipUnless(HAVE_FFMPEG, "ffmpeg absent")
    def test_reception_accepts_rejects_and_waits(self):
        work = self.root / "work"
        for r in self.rows:
            tone(work / r["outputPath"])
        tone(work / "media/zh-a0-test-fr/audio/intrus.opus")              # fichier non listé
        produced = [{"id": r["id"], "fingerprint": r["fingerprint"], "producer": {"agent": "agy", "engineId": "eng1", "voiceId": "v1"}} for r in self.rows]
        write_json(self.root / "produced.json", produced)
        write_jsonl(self.root / "asr.jsonl", [{"id": "t-nihao", "fingerprint": self.rows[0]["fingerprint"], "engine": "asr", "transcript": "你好", "checkedOn": "2000-01-01"},
                                               {"id": "t-xiexie", "fingerprint": self.rows[1]["fingerprint"], "engine": "asr", "transcript": "再见", "checkedOn": "2000-01-01"}])
        res, acc, rej, wait = receive.run(self.req_path, work, self.root / "produced.json", self.engines, self.voices, self.root / "asr.jsonl", "reserve", self.root / "out")
        self.assertEqual(["t-nihao"], [e["id"] for e in acc])
        reasons = {r["id"]: r["motif"] for r in rej}
        self.assertIn("écart ASR", reasons["t-xiexie"])
        self.assertTrue(any(r["id"] is None and "non listé" in r["motif"] for r in rej), "fichier hors demandes rejeté")
        media = json.loads((self.root / "out/media.json").read_text())
        self.assertEqual(["t-nihao"], [m["id"] for m in media["media"]], "les pistes rejetées ne sont pas dans media.json")
        e = media["media"][0]
        self.assertTrue(e["synthetic"] and e["statut"] == "bêta : non validé" and e["clonage_vocal"] is False)
        self.assertNotIn("key", json.dumps(e).lower().replace("keyword", ""))
        # no ASR at all: nothing accepted, everything waits
        res, acc, rej, wait = receive.run(self.req_path, work, self.root / "produced.json", self.engines, self.voices, None, "reserve", self.root / "out2")
        self.assertEqual([], acc); self.assertEqual(2, len(wait))
        self.assertIn("non vérifiable dans cet environnement", wait[0]["motif"])

    @unittest.skipUnless(HAVE_FFMPEG, "ffmpeg absent")
    def test_unapproved_voice_and_secrets_block(self):
        work = self.root / "work"
        tone(work / self.rows[0]["outputPath"])
        (work / ".env").write_text("TOKEN=abcdefghijklmnopqrstuvwxyz0123456789\n")
        write_json(self.root / "produced.json", [{"id": "t-nihao", "fingerprint": self.rows[0]["fingerprint"], "producer": {"agent": "agy", "engineId": "eng1", "voiceId": "v1"}}])
        write_jsonl(self.root / "asr.jsonl", [{"id": "t-nihao", "transcript": "你好"}])
        voices = json.loads(json.dumps(self.voices)); voices["voices"][0]["status"] = "à choisir"
        res, acc, rej, wait = receive.run(self.req_path, work, self.root / "produced.json", self.engines, voices, self.root / "asr.jsonl", "reserve", self.root / "out")
        self.assertEqual([], acc)
        self.assertTrue(any("voix" in r["motif"] for r in rej))
        self.assertTrue(any("secret" in b for b in res["bloquants"]))
        blob = (self.root / "out/rapport-reception.md").read_text()
        self.assertNotIn("abcdefghijklmnop", blob, "la valeur du secret n'est jamais recopiée")


class Manifest(unittest.TestCase):
    def test_family_and_status_rules(self):
        d, root = tmpdir(); self.addCleanup(d.cleanup)
        write_pack(root / "p", pack())
        r = requests_gen.build(root / "p")[0][0]
        f = root / "x.opus"; f.write_bytes(b"x")
        e = manifest.entry(r, f, {"agent": "agy", "engineId": "eng1", "voiceId": "v1"}, {"bytes": 1, "durationS": 1.0, "codec": "opus"}, "libre")
        self.assertEqual("CC-BY-SA-4.0", e["licence"])
        e2 = manifest.entry(r, f, {"agent": "agy", "engineId": "eng1", "voiceId": "v1"}, {"bytes": 1}, "reserve")
        self.assertEqual("CastBridge-original", e2["licence"])
        with self.assertRaises(ValueError):
            manifest.entry(r, f, {}, {}, "melange")
        bad = dict(e, licence="CastBridge-original")
        voices = {"voices": [], "mapping": {}}
        self.assertTrue(any("famille" in p for p in manifest.validate(bad, r, registries(), voices)))
        self.assertTrue(any("synthetic" in p for p in manifest.validate(dict(e, synthetic=False), r, registries(), voices)))

    def test_secret_scan_reports_path_and_kind_only(self):
        d, root = tmpdir(); self.addCleanup(d.cleanup)
        (root / "a.json").write_text('{"type": "service_' + 'account", "k": 1}')   # coupé : ce fichier de test ne doit pas se signaler lui-même
        (root / "b.txt").write_text("rien")
        (root / "c.cfg").write_text("api_key = ABCDEFGHIJKLMNOPQRSTUV123456\n")
        found = scan_secrets(root)
        self.assertEqual({"a.json", "c.cfg"}, {p for p, _ in found})
        self.assertTrue(all("ABCDEF" not in k for _, k in found))



class Cli(unittest.TestCase):
    def test_end_to_end_commands(self):
        import contextlib, io, sys
        sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
        import mp as _mp  # noqa: F401
        import importlib.util
        spec = importlib.util.spec_from_file_location("mpcli", str(Path(__file__).resolve().parents[1] / "mp.py"))
        d, root = tmpdir(); self.addCleanup(d.cleanup)
        # `mp.py` and the package `mp/` share a name: load the script under another name
        cli = importlib.util.module_from_spec(spec); spec.loader.exec_module(cli)
        write_pack(root / "packs", pack())
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(0, cli.main(["requests", str(root / "packs"), str(root / "req.jsonl")]))
            self.assertEqual(0, cli.main(["estimate", str(root / "req.jsonl")]))
            self.assertEqual(3, cli.main(["plan", str(root / "req.jsonl"), str(root / "plan.json"), "--max-chars", "3", "--state", str(root / "st.json")]), "plafond atteint : code 3")
            ids = json.loads((root / "plan.json").read_text())["retenues"]
            self.assertEqual(0, cli.main(["record", str(root / "req.jsonl"), str(root / "st.json")] + ids))
            self.assertEqual(0, cli.main(["secrets", str(root)]))
        self.assertIn("coût inconnu", out.getvalue())
        err = io.StringIO()
        with contextlib.redirect_stderr(err), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(2, cli.main(["plan", str(root / "req.jsonl"), str(root / "p2.json"), "--max-cost", "5"]), "plafond en monnaie sans tarif : refusé")
        self.assertIn("REFUSÉ", err.getvalue())


if __name__ == "__main__":
    unittest.main()
