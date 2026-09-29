import sys, pathlib, stat
sys.path.insert(0, str(pathlib.Path(__file__).parent.parent))
import pytest
from aiohttp.test_utils import TestClient, TestServer
import castbridge_server as cs


@pytest.fixture
def fake_engine(tmp_path):
    # ffmpeg-style engine replacement: emits 200 KB and exits
    f = tmp_path / "ffmpeg"
    f.write_text("#!/usr/bin/env python3\nimport sys\nsys.stdout.buffer.write(b'x'*200000)\n")
    f.chmod(f.stat().st_mode | stat.S_IEXEC)
    return ("ffmpeg", str(f))


async def client(engine, **kw):
    c = TestClient(TestServer(cs.make_app(engine, **kw)))
    await c.start_server()
    return c


async def test_stream_and_headers(fake_engine):
    c = await client(fake_engine)
    r = await c.get("/stream/dlna-720.ts", params={"src": "http://x/a.mkv"})
    assert r.status == 200 and len(await r.read()) == 200000
    assert "DLNA.ORG_PN" in r.headers["contentFeatures.dlna.org"]
    await c.close()


async def test_head_does_not_transcode():
    c = await client(None)  # no engine: GET would be 503
    r = await c.head("/stream/dlna-720.ts", params={"src": "http://x/a.mkv"})
    assert r.status == 200 and "contentFeatures.dlna.org" in r.headers
    assert (await c.get("/stream/dlna-720.ts", params={"src": "http://x/a"})).status == 503
    await c.close()


async def test_validation(fake_engine):
    c = await client(fake_engine)
    assert (await c.get("/stream/nope.ts", params={"src": "http://x"})).status == 404
    assert (await c.get("/stream/dlna-720.ts", params={"src": "/etc/passwd"})).status == 400
    assert (await c.get("/stream/dlna-720.ts")).status == 400
    await c.close()


def test_profiles_and_cmds():
    assert len(cs.PROFILES) == 5
    for p in cs.PROFILES.values():
        for eng in (("ffmpeg", "ffmpeg"), ("vlc", "vlc")):
            cmd = cs.build_cmd(eng, p, "http://x/a.mkv", 10)
            assert "http://x/a.mkv" in cmd
    assert "-ss" in cs.build_cmd(("ffmpeg", "f"), cs.PROFILES["dlna-720"], "u", 5)
    assert "-ss" not in cs.build_cmd(("ffmpeg", "f"), cs.PROFILES["dlna-720"], "u", 0)
    assert cs.parse_start("abc") == 0 and cs.parse_start("-3") == 0 and cs.parse_start("2.5") == 2.5


def test_summarize_probe():
    s = cs.summarize_probe({"format": {"format_name": "matroska", "duration": "12.5"},
                            "streams": [{"codec_type": "video", "codec_name": "hevc", "width": 3840, "height": 2160},
                                        {"codec_type": "audio", "codec_name": "ac3", "channels": 6}]})
    assert s["video"]["codec"] == "hevc" and s["audio"]["channels"] == 6 and s["duration"] == 12.5
