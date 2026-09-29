#!/usr/bin/env python3
"""CastBridge PC transcoding server.

GET/HEAD /stream/<profile>.ts?src=<url|path>&start=<sec>  -> MPEG-TS transcode
GET      /api/probe?src=<url|path>                          -> ffprobe JSON summary
GET      /api/profiles                                      -> profile list
GET      /health                                            -> engine info
"""
import argparse
import asyncio
import json
import logging
import os
import shutil
import socket
from dataclasses import dataclass
from urllib.parse import urlparse

from aiohttp import web

log = logging.getLogger("castbridge")
PORT = 8788

DLNA_FEATURES = ("DLNA.ORG_PN=MPEG_TS_SD_EU;DLNA.ORG_OP=00;DLNA.ORG_CI=1;"
                 "DLNA.ORG_FLAGS=01700000000000000000000000000000")


@dataclass(frozen=True)
class Profile:
    name: str
    video: str          # "copy" | "h264"
    audio: str          # "aac" | "mp3"
    height: int = 0     # 0 = keep
    vbitrate: int = 0   # kb/s
    abitrate: int = 128
    mime: str = "video/mpeg"


PROFILES = {p.name: p for p in [
    Profile("remux-aac", "copy", "aac", abitrate=192),
    Profile("dlna-480", "h264", "aac", 480, 1200, 128),
    Profile("dlna-720", "h264", "aac", 720, 3500, 160),
    Profile("dlna-1080", "h264", "aac", 1080, 8000, 192),
    Profile("audio-mp3", "none", "mp3", abitrate=192, mime="audio/mpeg"),
]}


def ffmpeg_args(p: Profile, src: str, start: float, ffmpeg="ffmpeg") -> list[str]:
    a = [ffmpeg, "-hide_banner", "-loglevel", "error"]
    if start > 0:
        a += ["-ss", f"{start:.3f}"]
    a += ["-i", src, "-map", "0:v:0?", "-map", "0:a:0?"] if p.video != "none" else ["-i", src, "-vn", "-map", "0:a:0?"]
    if p.video == "copy":
        a += ["-c:v", "copy"]
    elif p.video == "h264":
        a += ["-c:v", "libx264", "-preset", "veryfast", "-profile:v", "high", "-pix_fmt", "yuv420p",
              "-b:v", f"{p.vbitrate}k", "-maxrate", f"{p.vbitrate}k", "-bufsize", f"{p.vbitrate * 2}k",
              "-vf", f"scale=-2:'min({p.height},ih)'", "-g", "50"]
    a += ["-c:a", "libmp3lame" if p.audio == "mp3" else "aac", "-b:a", f"{p.abitrate}k", "-ac", "2"]
    a += ["-f", "mp3" if p.audio == "mp3" and p.video == "none" else "mpegts", "pipe:1"]
    return a


def vlc_args(p: Profile, src: str, start: float, vlc="vlc") -> list[str]:
    vc = "vcodec=h264,venc=x264{preset=veryfast,profile=high}" + (
        f",vb={p.vbitrate},height={p.height},scale=0" if p.height else "")
    parts = []
    if p.video == "h264":
        parts.append(vc)
    elif p.video == "copy":
        parts.append("vcodec=h264")  # VLC has no true copy across muxers without transcode flags
    acodec = "mp3" if p.audio == "mp3" else "mp4a"
    parts.append(f"acodec={acodec},ab={p.abitrate},channels=2,samplerate=48000")
    mux = "raw" if p.video == "none" else "ts"
    a = [vlc, "-I", "dummy", "--no-sout-all", "--sout-keep", src]
    if start > 0:
        a += [f"--start-time={start:.3f}"]
    a += ["--sout", f"#transcode{{{','.join(parts)}}}:std{{access=file,mux={mux},dst=-}}", "vlc://quit"]
    return a


def detect_engine(prefer: str | None = None) -> tuple[str, str] | None:
    order = [prefer] if prefer in ("vlc", "ffmpeg") else ["vlc", "ffmpeg"]
    order += [e for e in ("vlc", "ffmpeg") if e not in order]
    for e in order:
        path = shutil.which(e)
        if path:
            return e, path
    return None


def build_cmd(engine: tuple[str, str], profile: Profile, src: str, start: float) -> list[str]:
    name, path = engine
    return (vlc_args if name == "vlc" else ffmpeg_args)(profile, src, start, path)


def valid_src(src: str, allow_local: bool) -> bool:
    if not src:
        return False
    u = urlparse(src)
    if u.scheme in ("http", "https", "rtsp", "rtmp", "udp", "rtp"):
        return True
    return allow_local and u.scheme == "" and os.path.isfile(src)


def parse_start(v: str | None) -> float:
    try:
        return max(0.0, float(v or 0))
    except ValueError:
        return 0.0


def stream_headers(p: Profile) -> dict:
    return {"Content-Type": p.mime, "transferMode.dlna.org": "Streaming",
            "contentFeatures.dlna.org": DLNA_FEATURES, "Accept-Ranges": "none",
            "Cache-Control": "no-cache"}


async def handle_stream(req: web.Request) -> web.StreamResponse:
    name = req.match_info["profile"]
    p = PROFILES.get(name)
    if not p:
        return web.json_response({"error": "unknown profile"}, status=404)
    src = req.query.get("src", "")
    if not valid_src(src, req.app["allow_local"]):
        return web.json_response({"error": "invalid src"}, status=400)
    headers = stream_headers(p)
    if req.method == "HEAD":  # never start a transcode for a probe
        return web.Response(headers=headers)
    engine = req.app["engine"]
    if not engine:
        return web.json_response({"error": "no VLC/FFmpeg found"}, status=503)
    sem: asyncio.Semaphore = req.app["sem"]
    if sem.locked():
        return web.json_response({"error": "too many transcodes"}, status=429)
    cmd = build_cmd(engine, p, src, parse_start(req.query.get("start")))
    async with sem:
        proc = await asyncio.create_subprocess_exec(
            *cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
        resp = web.StreamResponse(headers=headers)
        await resp.prepare(req)
        try:
            while chunk := await proc.stdout.read(64 * 1024):
                await resp.write(chunk)
        except (ConnectionResetError, asyncio.CancelledError):
            pass
        finally:
            if proc.returncode is None:
                proc.kill()
            await proc.wait()
        try:
            await resp.write_eof()
        except Exception:
            pass
        return resp


async def handle_probe(req: web.Request) -> web.Response:
    src = req.query.get("src", "")
    if not valid_src(src, req.app["allow_local"]):
        return web.json_response({"error": "invalid src"}, status=400)
    ffprobe = shutil.which("ffprobe")
    if not ffprobe:
        return web.json_response({"error": "ffprobe not found"}, status=503)
    proc = await asyncio.create_subprocess_exec(
        ffprobe, "-v", "error", "-show_format", "-show_streams", "-of", "json", src,
        stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
    try:
        out, _ = await asyncio.wait_for(proc.communicate(), 20)
    except asyncio.TimeoutError:
        proc.kill()
        return web.json_response({"error": "probe timeout"}, status=504)
    if proc.returncode != 0:
        return web.json_response({"error": "probe failed"}, status=422)
    return web.json_response(summarize_probe(json.loads(out)))


def summarize_probe(d: dict) -> dict:
    v = next((s for s in d.get("streams", []) if s.get("codec_type") == "video"), {})
    a = next((s for s in d.get("streams", []) if s.get("codec_type") == "audio"), {})
    fmt = d.get("format", {})
    return {"container": fmt.get("format_name"), "duration": float(fmt.get("duration") or 0),
            "video": {"codec": v.get("codec_name"), "width": v.get("width"), "height": v.get("height"),
                      "pix_fmt": v.get("pix_fmt"), "profile": v.get("profile")} if v else None,
            "audio": {"codec": a.get("codec_name"), "channels": a.get("channels")} if a else None}


async def handle_profiles(req):
    return web.json_response([p.__dict__ for p in PROFILES.values()])


async def handle_health(req):
    e = req.app["engine"]
    return web.json_response({"ok": True, "engine": e[0] if e else None, "version": "0.2"})


def make_app(engine, allow_local=False, max_streams=2) -> web.Application:
    app = web.Application()
    app["engine"], app["allow_local"] = engine, allow_local
    app["sem"] = asyncio.Semaphore(max_streams)
    app.router.add_route("*", "/stream/{profile}.ts", handle_stream)
    app.router.add_get("/api/probe", handle_probe)
    app.router.add_get("/api/profiles", handle_profiles)
    app.router.add_get("/health", handle_health)
    return app


def lan_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


async def announce_mdns(port: int):
    try:
        from zeroconf import ServiceInfo
        from zeroconf.asyncio import AsyncZeroconf
    except ImportError:
        log.warning("zeroconf missing: mDNS disabled")
        return None
    ip = lan_ip()
    info = ServiceInfo("_castbridge._tcp.local.", f"CastBridge-{socket.gethostname()}._castbridge._tcp.local.",
                       addresses=[socket.inet_aton(ip)], port=port, properties={"v": "0.2", "role": "server"})
    azc = AsyncZeroconf()
    await azc.async_register_service(info)
    log.info("mDNS announced on %s:%d", ip, port)
    return azc


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=PORT)
    ap.add_argument("--engine", choices=["vlc", "ffmpeg"])
    ap.add_argument("--allow-local", action="store_true", help="allow local file paths as src")
    ap.add_argument("--max-streams", type=int, default=2)
    ap.add_argument("--no-mdns", action="store_true")
    a = ap.parse_args()
    logging.basicConfig(level=logging.INFO)
    if hasattr(os, "geteuid") and os.geteuid() == 0:
        log.warning("running as root: VLC refuses to run as root")
    engine = detect_engine(a.engine)
    log.info("engine: %s", engine)

    async def run():
        runner = web.AppRunner(make_app(engine, a.allow_local, a.max_streams))
        await runner.setup()
        await web.TCPSite(runner, "0.0.0.0", a.port).start()
        zc = None if a.no_mdns else await announce_mdns(a.port)
        try:
            await asyncio.Event().wait()
        finally:
            if zc:
                await zc.async_close()
            await runner.cleanup()
    try:
        asyncio.run(run())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
