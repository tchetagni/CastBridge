#!/usr/bin/env python3
"""SSH to CastBridge TV over Bluetooth, without any shared network (Linux, Python standard library only).

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

macOS / Windows: Python's standard library has no RFCOMM sockets there. Use the phone instead: CastBridge app >
CastBridge TV > Bluetooth > "Passerelle SSH Bluetooth", tick "exposer sur le réseau local", join the phone's hotspot
and run `ssh -p 2222 tv@<phone address>`.

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


def save_cache(addr, channel):
    c = load_cache()
    c[addr.upper()] = channel
    try:
        os.makedirs(os.path.dirname(CACHE), exist_ok=True)
        with open(CACHE, "w") as f:
            json.dump(c, f)
    except OSError:
        pass


def sdp_channel(addr):
    """RFCOMM channel of the CastBridge SSH service from BlueZ's sdptool, or None."""
    if not shutil.which("sdptool"):
        return None
    try:
        out = subprocess.run(["sdptool", "search", "--bdaddr", addr, SSH_UUID], capture_output=True, text=True, timeout=20).stdout
    except (OSError, subprocess.SubprocessError):
        return None
    m = re.search(r"Channel:\s*(\d+)", out)
    return int(m.group(1)) if m else None


def connect(addr, channel, greet_timeout):
    """Connected RFCOMM socket and the first bytes the TV sent ("SSH-..."), or (None, b"")."""
    s = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    try:
        s.settimeout(10)
        s.connect((addr, channel))
        s.settimeout(greet_timeout)
        first = s.recv(256)
        if first.startswith(b"SSH-"):
            s.settimeout(None)
            return s, first
    except OSError:
        pass
    s.close()
    return None, b""


def open_link(addr, channel=None, greet_timeout=4.0):
    tried = []
    for ch in [channel, load_cache().get(addr.upper()), sdp_channel(addr)]:
        if ch and ch not in tried:
            tried.append(ch)
            s, first = connect(addr, ch, greet_timeout)
            if s:
                save_cache(addr, ch)
                return s, first
    if channel:
        return None, b""
    log("searching the CastBridge SSH service (channels 1-30)...")
    for ch in range(1, 31):
        if ch in tried:
            continue
        s, first = connect(addr, ch, greet_timeout)
        if s:
            save_cache(addr, ch)
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


def main():
    ap = argparse.ArgumentParser(description="SSH ProxyCommand to CastBridge TV over Bluetooth RFCOMM (Linux).")
    ap.add_argument("address", help="Bluetooth address of the TV, e.g. AA:BB:CC:DD:EE:FF")
    ap.add_argument("--channel", type=int, help="RFCOMM channel of the 'CastBridge SSH' service (skips discovery)")
    ap.add_argument("--timeout", type=float, default=4.0, help="seconds to wait for the SSH greeting on each channel")
    a = ap.parse_args()
    if not re.fullmatch(r"(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}", a.address):
        log("invalid Bluetooth address: " + a.address)
        return 2
    if not hasattr(socket, "AF_BLUETOOTH") or not hasattr(socket, "BTPROTO_RFCOMM"):
        log("this Python has no Bluetooth RFCOMM sockets (macOS/Windows): use the phone's 'Passerelle SSH Bluetooth' instead")
        return 2
    s, first = open_link(a.address, a.channel, a.timeout)
    if not s:
        log("no CastBridge SSH service found: is SSH enabled on the TV (MENU > SSH), and the computer paired with it?")
        return 1
    relay(s, first)
    return 0


if __name__ == "__main__":
    sys.exit(main())
