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


def check(chapter):
    root = Path(__file__).resolve().parent.parent
    number = int(chapter.removeprefix("chapter"))
    assert 18 <= number <= 25
    chapter_dir = root / chapter
    package = "com.seaofnodes.simple." + ("print." if number >= 20 else "")
    log = root / "build" / "graph-transport" / (chapter + ".log")
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open("w") as output:
        process = subprocess.Popen(
            ["java", "-ea", "-Dsimple.graph.open=false", "-cp",
             "build/classes/main", package + "JSViewer"],
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
            for asset in ("index.html", "viewer.js", "vendor/d3.v7.min.js",
                          "vendor/d3-graphviz.js", "vendor/graphviz.umd.js"):
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
                client.sendall(b"\r\n")
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
    print(chapter + ": HTTP assets, fragmented upgrade, greeting, and shutdown passed")


if __name__ == "__main__":
    check(sys.argv[1] if len(sys.argv) > 1 else "chapter25")
