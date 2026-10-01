#!/usr/bin/env python3
"""SSH or HTTP API of CastBridge TV over Bluetooth, without any shared network (Linux, Python standard library only).

Use it as an OpenSSH ProxyCommand; SSH itself is unchanged (end-to-end encryption, your key):

    ssh -o ProxyCommand="python3 tools/bt-ssh-bridge.py AA:BB:CC:DD:EE:FF" tv@castbridge
    # or in ~/.ssh/config:
    #   Host tv-bt
    #     User tv
    #     ProxyCommand python3 /path/to/tools/bt-ssh-bridge.py AA:BB:CC:DD:EE:FF

Prerequisites: the computer is paired with the TV (bluetoothctl: pair / trust), SSH is enabled on the TV
(MENU > SSH), and your public key is authorised on the TV. The TV exposes an RFCOMM service
"CastBridge SSH" (UUID 7c5e3b9a-4d2f-4c61-9b0e-cb0000000002) that relays to its SSH server.

Finding the RFCOMM channel: --channel N if you know it; otherwise `sdptool` (BlueZ) if installed; otherwise the
script probes channels 1..30 and keeps the one that greets with "SSH-" (the SSH server speaks first; the TV's file
service stays silent and is skipped). The channel found is cached in ~/.cache/castbridge-bt-ssh.json.

The TV's HTTP API (everything the phone does over Wi-Fi: library, upload, install, ssh key registration...) is the
third service "CastBridge API" (UUID ...0003). It answers one status byte first (0 = ok, 1 busy, 2 refused, 3 server down,
4 internal), then carries plain HTTP. Serve it on a local port and use curl as usual (the TV still checks the PIN):

    python3 tools/bt-ssh-bridge.py AA:BB:CC:DD:EE:FF --service api --listen 18765
    curl -H 'X-CB-Pin: <code de la TV>' http://127.0.0.1:18765/api/hello

macOS / Windows: Python's standard library has no RFCOMM sockets there. On a Mac use tools/cbt-rfcomm (`proxy`), or the
phone: CastBridge app > CastBridge TV > Bluetooth > "Passerelle Bluetooth" (SSH on 127.0.0.1:2222, API on 127.0.0.1:18765;
tick "exposer sur le réseau local" for SSH only, join the phone's hotspot and run `ssh -p 2222 tv@<phone address>`).

Throughput is that of Bluetooth Classic (roughly 100-300 kB/s): fine for a shell, slow for SFTP/scp.
"""
import argparse
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import threading

SSH_UUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000002"
API_UUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000003"
STATUS = {
    1: "the TV has too many Bluetooth links open, retry in a few seconds",
    2: "the TV does not allow this computer on that service (not paired, or 'API par Bluetooth' is off on the TV: MENU > Administration)",
    3: "the TV's service does not answer (SSH enabled on the TV? CastBridge-TV just restarted?)",
    4: "internal error on the TV",
}
CACHE = os.path.join(os.path.expanduser("~"), ".cache", "castbridge-bt-ssh.json")


def log(msg):
    # stderr only: stdout belongs to the SSH protocol
    print("bt-ssh-bridge: " + msg, file=sys.stderr, flush=True)


def load_cache():
    try:
        with open(CACHE) as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def cache_key(addr, service="ssh"):
    return addr.upper() if service == "ssh" else addr.upper() + "/" + service     # the SSH key format is unchanged


def save_cache(addr, channel, service="ssh"):
    c = load_cache()
    c[cache_key(addr, service)] = channel
    try:
        os.makedirs(os.path.dirname(CACHE), exist_ok=True)
        with open(CACHE, "w") as f:
            json.dump(c, f)
    except OSError:
        pass


def sdp_channel(addr, uuid=SSH_UUID):
    """RFCOMM channel of a CastBridge service from BlueZ's sdptool, or None."""
    if not shutil.which("sdptool"):
        return None
    try:
        out = subprocess.run(["sdptool", "search", "--bdaddr", addr, uuid], capture_output=True, text=True, timeout=20).stdout
    except (OSError, subprocess.SubprocessError):
        return None
    m = re.search(r"Channel:\s*(\d+)", out)
    return int(m.group(1)) if m else None


def connect(addr, channel, greet_timeout, service="ssh"):
    """Connected RFCOMM socket and the first bytes the TV sent, or (None, b"").

    ssh: the SSH server speaks first ("SSH-..."), those bytes are returned. api: the TV answers one status byte (0 = ok, which is
    consumed: the stream that follows is plain HTTP); a refusal (1..4) is logged with its reason and ends the attempt.
    """
    s = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    try:
        s.settimeout(10)
        s.connect((addr, channel))
        s.settimeout(greet_timeout)
        if service == "api":
            first = s.recv(1)
            if first == b"\x00":
                s.settimeout(None)
                return s, b""
            if first and first[0] in STATUS:
                log("the TV refuses: " + STATUS[first[0]])
        else:
            first = s.recv(256)
            if first.startswith(b"SSH-"):
                s.settimeout(None)
                return s, first
    except OSError as e:
        log("channel %d: %s" % (channel, e))
    s.close()
    return None, b""


def open_link(addr, channel=None, greet_timeout=4.0, service="ssh"):
    uuid = API_UUID if service == "api" else SSH_UUID
    tried = []
    for ch in [channel, load_cache().get(cache_key(addr, service)), sdp_channel(addr, uuid)]:
        if ch and ch not in tried:
            tried.append(ch)
            s, first = connect(addr, ch, greet_timeout, service)
            if s:
                save_cache(addr, ch, service)
                return s, first
    if channel:
        return None, b""
    log("searching the CastBridge %s service (channels 1-30)..." % service.upper())
    for ch in range(1, 31):
        if ch in tried:
            continue
        s, first = connect(addr, ch, greet_timeout, service)
        if s:
            save_cache(addr, ch, service)
            log("found on channel %d" % ch)
            return s, first
    return None, b""


def relay(sock, first):
    out = sys.stdout.buffer
    out.write(first)
    out.flush()

    def up():
        try:
            while True:
                data = os.read(0, 32 * 1024)
                if not data:
                    break
                sock.sendall(data)
        except OSError:
            pass
        finally:
            try:
                sock.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass

    threading.Thread(target=up, daemon=True).start()
    try:
        while True:
            data = sock.recv(32 * 1024)
            if not data:
                break
            out.write(data)
            out.flush()
    except OSError:
        pass
    finally:
        sock.close()


def pump(a, b):
    """Copies a -> b until either ends (one direction; the caller runs two)."""
    try:
        while True:
            data = a.recv(32 * 1024)
            if not data:
                break
            b.sendall(data)
    except OSError:
        pass
    finally:
        for x in (a, b):
            try:
                x.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def listen_api(addr, port, channel, timeout):
    """Local TCP port (127.0.0.1 only) bridged to the TV's API service: one RFCOMM link per TCP connection."""
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        srv.bind(("127.0.0.1", port))
    except OSError as e:
        log("port %d unavailable on 127.0.0.1: %s" % (port, e))
        return 1
    srv.listen(8)
    log("API bridge: http://127.0.0.1:%d -> %s (Ctrl-C to stop); curl -H 'X-CB-Pin: <TV code>' http://127.0.0.1:%d/api/hello" % (port, addr, port))

    def serve(c):
        bt, _ = open_link(addr, channel, timeout, "api")
        if not bt:
            msg = "Bluetooth link to the TV's API service failed: TV on and in range? computer paired? CastBridge-TV up to date?"
            log(msg)
            try:
                c.sendall(("HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\n\r\n" + msg + "\n").encode())
            except OSError:
                pass
            c.close()
            return
        threading.Thread(target=pump, args=(bt, c), daemon=True).start()
        pump(c, bt)
        c.close()
        bt.close()

    try:
        while True:
            c, _ = srv.accept()
            threading.Thread(target=serve, args=(c,), daemon=True).start()
    except KeyboardInterrupt:
        return 0


def main():
    ap = argparse.ArgumentParser(description="SSH ProxyCommand / HTTP API bridge to CastBridge TV over Bluetooth RFCOMM (Linux).")
    ap.add_argument("address", help="Bluetooth address of the TV, e.g. AA:BB:CC:DD:EE:FF")
    ap.add_argument("--service", choices=["ssh", "api"], default="ssh", help="ssh (default, ProxyCommand on stdin/stdout) or api (needs --listen)")
    ap.add_argument("--listen", type=int, metavar="PORT", help="api: local TCP port on 127.0.0.1 (e.g. 18765)")
    ap.add_argument("--channel", type=int, help="RFCOMM channel of the service (skips discovery)")
    ap.add_argument("--timeout", type=float, default=4.0, help="seconds to wait for the TV's first bytes on each channel")
    a = ap.parse_args()
    if not re.fullmatch(r"(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}", a.address):
        log("invalid Bluetooth address: " + a.address)
        return 2
    if not hasattr(socket, "AF_BLUETOOTH") or not hasattr(socket, "BTPROTO_RFCOMM"):
        log("this Python has no Bluetooth RFCOMM sockets (macOS/Windows): use tools/cbt-rfcomm (Mac) or the phone's 'Passerelle Bluetooth' instead")
        return 2
    if a.service == "api":
        if not a.listen:
            log("--service api needs --listen PORT")
            return 2
        return listen_api(a.address, a.listen, a.channel, a.timeout)
    s, first = open_link(a.address, a.channel, a.timeout)
    if not s:
        log("no CastBridge SSH service found: is SSH enabled on the TV (MENU > SSH), and the computer paired with it?")
        return 1
    relay(s, first)
    return 0


if __name__ == "__main__":
    sys.exit(main())
