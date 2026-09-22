"""Check HTTP assets and a delayed, fragmented WebSocket upgrade.

Build the chapter first, then run: python graph/test_transport.py chapter25
Uses only the Python standard library; does not launch a browser.
"""

from pathlib import Path
import socket
import subprocess
import sys
import time
import urllib.request
import json
import struct


def packet(data, op=1, fin=True):
    n = len(data)
    head = bytes([op | (128 if fin else 0)])
    head += bytes([128 | n]) if n < 126 else (b'\xfe' + struct.pack('>H', n) if n <= 65535
                                           else b'\xff' + struct.pack('>Q', n))
    mask = b'abcd'
    return head + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(data))


def frame(stream):
    head = stream.read(2)
    assert len(head) == 2, 'Incomplete server frame'
    n = head[1]
    if n == 126: n = struct.unpack('>H', stream.read(2))[0]
    elif n == 127: n = struct.unpack('>Q', stream.read(8))[0]
    data = stream.read(n)
    assert len(data) == n
    return head[0] & 15, data


def compile_frames(stream):
    frames = []
    while True:
        op, data = frame(stream)
        assert op == 1
        if data == b'#': return frames
        frames.append(json.loads(data))


def check(chapter):
    root = Path(__file__).resolve().parent.parent
    number = int(chapter.removeprefix("chapter"))
    assert 1 <= number <= 25
    chapter_dir = root / chapter
    package = "com.seaofnodes.simple.print."
    log = root / "build" / "graph-transport" / (chapter + ".log")
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open("w") as output:
        process = subprocess.Popen(
            ["java", "-ea", "-Dsimple.graph.open=false", "-cp",
             "build/classes/main", package + "SimpleGraphObserver"],
            cwd=chapter_dir, stdout=output, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 10
            url = None
            while time.monotonic() < deadline and process.poll() is None:
                lines = log.read_text().splitlines()
                addresses = [line.removeprefix("Graph viewer: ") for line in lines
                             if line.startswith("Graph viewer: ")]
                if addresses:
                    url = addresses[0]
                    break
                time.sleep(.05)
            assert url and url.startswith("http://127.0.0.1:"), log.read_text()
            # The browser must be able to load all assets before connecting.
            for asset in ("index.html", "viewer.js", "layout.js", "render.js",
                          "vendor/elk-api.js", "vendor/elk-worker.min.js", "vendor/d3.v7.min.js"):
                with urllib.request.urlopen(url.rsplit("/", 1)[0] + "/" + asset,
                                            timeout=3) as response:
                    assert response.status == 200
                    assert response.read() == (root / "graph/web" / asset).read_bytes()
            with socket.create_connection(("127.0.0.1", 12345), timeout=3) as client:
                # Reproduces the old ready() race: accept precedes any headers.
                time.sleep(.3)
                client.sendall(b"GET / HTTP/1.1\r\nHost: localhost:12345\r\n")
                time.sleep(.1)
                client.sendall(b"Upgrade: websocket\r\nConnection: Upgrade\r\n"
                               b"Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                               b"Sec-WebSocket-Version: 13\r\n")
                # Even with the key present, the header block is not complete.
                client.settimeout(.2)
                try:
                    early = client.recv(1)
                except socket.timeout:
                    pass
                else:
                    raise AssertionError("Responded before headers ended: " + repr(early))
                client.settimeout(3)
                # A frame in the same write as the upgrade must not get lost in a reader buffer.
                client.sendall(b"\r\n" + packet(b'hello', op=9))
                with client.makefile("rb") as stream:
                    assert stream.readline() == b"HTTP/1.1 101 Switching Protocols\r\n"
                    headers = []
                    while True:
                        line = stream.readline()
                        assert line, "Connection closed during upgrade"
                        if line == b"\r\n":
                            break
                        headers.append(line)
                    assert b"Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n" in headers
                    assert stream.read(3) == b"\x81\x01!", "Missing source request"
                    assert frame(stream) == (10, b'hello')
                    # Larger than both the old 1 KB buffer and the 16-bit length field.
                    data = packet(b' ' * 70000 + b'return 3;')
                    client.sendall(data[:3])
                    time.sleep(.05)
                    for start in range(3, len(data), 509):
                        client.sendall(data[start:start + 509])
                    frames = compile_frames(stream)
                    assert frames and all('snap' in f for f in frames)
                    # Split a UTF-8 code point across fragments, with an interleaved PING.
                    client.sendall(packet(b'return 3; \xc3', fin=False) + packet(b'ping', op=9) + packet(b'\xa9', op=0))
                    assert frame(stream) == (10, b'ping')
                    errors = compile_frames(stream)
                    assert any('error' in f for f in errors)
                    assert '\u00e9' in log.read_text()
                    client.sendall(packet(b'return 4;'))
                    frames = compile_frames(stream)
                    assert frames and all('snap' in f for f in frames)
                    # Masked 'null' asks the compiler to exit normally.
                    mask = b"abcd"
                    payload = bytes(byte ^ mask[i % 4] for i, byte in enumerate(b"null"))
                    client.sendall(b"\x81\x84" + mask + payload)
                    assert stream.read(2) == b"\x88\x00", "Invalid close frame"
            assert process.wait(timeout=5) == 0, log.read_text()
            # The HTTP server must not outlive the compiler session.
            try:
                urllib.request.urlopen(url, timeout=.5)
            except OSError:
                pass
            else:
                raise AssertionError("HTTP server survived viewer shutdown")
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=5)
    print(chapter + ": HTTP assets, fragmented upgrade, greeting, and shutdown passed" +
          "; large frames, UTF-8 fragments, ping, and compile-error recovery passed")


if __name__ == "__main__":
    check(sys.argv[1] if len(sys.argv) > 1 else "chapter25")
