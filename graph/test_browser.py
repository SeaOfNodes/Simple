"""End-to-end viewer regression: requires Playwright and a browser.

Build the chapter, then run python graph/test_browser.py chapter25 --browser msedge.
Use --browser firefox for Playwright's Firefox build. See README.md for setup.
"""
from pathlib import Path
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import os
import subprocess
import sys
import time
from threading import Thread

ROOT = Path(__file__).resolve().parent.parent
# Optional workspace-local test dependencies; no runtime dependency for the viewer.
sys.path.insert(0, str(ROOT / "build/graph-tools"))
if (ROOT / "build/graph-browsers").is_dir():
    os.environ.setdefault("PLAYWRIGHT_BROWSERS_PATH", str(ROOT / "build/graph-browsers"))
from playwright.sync_api import sync_playwright


def check(chapter, browser_name):
    number = int(chapter.removeprefix("chapter"))
    package = "com.seaofnodes.simple.print."
    artifacts = ROOT / "build/graph-browser" / (chapter + "-" + browser_name)
    artifacts.mkdir(parents=True, exist_ok=True)
    log = artifacts / "compiler.log"
    with log.open("w") as output:
        process = subprocess.Popen([
            "java", "-ea", "-Dsimple.graph.open=false", "-cp", "build/classes/main",
            package + "SimpleGraphObserver"], cwd=ROOT/chapter, stdout=output, stderr=subprocess.STDOUT)
        try:
            for _ in range(200):
                text = log.read_text()
                if "Graph viewer: " in text:
                    break
                assert process.poll() is None, text
                time.sleep(.05)
            url = text.split("Graph viewer: ")[1].splitlines()[0]
            with sync_playwright() as playwright:
                browser = (playwright.firefox.launch(headless=True) if browser_name == "firefox"
                           else playwright.chromium.launch(channel=browser_name, headless=True))
                page = browser.new_page(viewport={"width": 1400, "height": 1000})
                errors = []
                page.on("pageerror", lambda error: errors.append(str(error)))
                page.goto(url)
                page.wait_for_function("done && current === 0 && !rendering", timeout=15000)
                assert page.locator("#graph svg g.node").count() > 0
                assert page.locator("#doPrev").is_disabled()
                if number > 1:
                    page.locator("#doNext").click()
                    page.wait_for_function("current === 1 && !rendering")
                    page.locator("#doPrev").click()
                    page.wait_for_function("current === 0 && !rendering")
                sources = (("return 1;", "return 2;") if number == 1 else
                           ("return 1 + 2;", "return 3 * 4;") if number == 2 else
                           ("return 1 + 2;", "int x=arg+1; return x+x+2;") if number <= 5 else
                           ("return 1 + 2;", "int x=0; while(x<arg) { x=x+1; } return x;"))
                for source in sources:
                    previous = page.evaluate("generation")
                    page.locator("#program").fill(source)
                    page.locator("#compile").click()
                    page.wait_for_function(
                        "previous => generation > previous && done && current === 0 && !rendering",
                        arg=previous, timeout=15000)
                    total = int(page.locator("#len").inner_text())
                    assert total >= 1
                    # Later chapters also parse library code. Sample long histories.
                    steps = (range(1, total) if total <= 100 else
                             sorted({1, total // 4, total // 2, 3 * total // 4, total - 2, total - 1}))
                    print(chapter, total, "frames captured; rendering", len(steps) + 1, flush=True)
                    prev = 0
                    for index in steps:
                        if index == prev + 1:
                            page.locator("#doNext").click()
                        else:
                            page.evaluate("index => render(index)", index)
                        page.wait_for_function("index => current === index && !rendering", arg=index)
                        prev = index
                    assert page.locator("#doNext").is_disabled()
                    assert "error" not in page.locator("#status").inner_text().lower()
                    if total > 1:
                        assert page.locator("#elk g.node").count() == page.evaluate("frames[current].snap.nodes.length")
                        # Revisit cached geometry and keep the selected node across steps.
                        page.evaluate("""() => {
                            window.lastLayout = frames[current].layout;
                            window.pickId = frames[current].snap.roots[0];
                            renderer.select(pickId);
                            layout.run = () => { throw new Error('Cached frame was laid out again'); };
                        }""")
                        page.locator("#doPrev").click()
                        page.wait_for_function("index => current === index && !rendering", arg=total - 2)
                        page.locator("#doNext").click()
                        page.wait_for_function("index => current === index && !rendering", arg=total - 1)
                        assert page.evaluate("frames[current].layout === lastLayout && renderer.pick === pickId")
                        page.evaluate("delete layout.run")
                        assert page.locator("#detail").is_visible()
                        page.keyboard.press("Escape")
                    print(chapter, browser_name, "playback passed:", source, flush=True)
                assert not errors, errors
                page.screenshot(path=str(artifacts / "viewer.png"))
                page.locator("#doExit").click()
                page.wait_for_function("document.getElementById('status').textContent.includes('disconnected')")
                assert not page.is_closed()
                assert process.wait(timeout=5) == 0, log.read_text()[-2000:]
                # A page opened without any compiler must not remain "Connecting...".
                class QuietHandler(SimpleHTTPRequestHandler):
                    def log_message(self, *args):
                        pass
                http = ThreadingHTTPServer(("127.0.0.1", 0),
                    partial(QuietHandler, directory=str(ROOT / "graph/web")))
                thread = Thread(target=http.serve_forever, daemon=True)
                thread.start()
                try:
                    offline = browser.new_page()
                    offline.goto("http://127.0.0.1:" + str(http.server_port) + "/index.html")
                    offline.wait_for_function(
                        "document.getElementById('status').textContent.includes('disconnected')")
                    assert offline.locator("#compile").is_disabled()
                    assert not offline.is_closed()
                finally:
                    http.shutdown()
                    http.server_close()
                    thread.join()
                browser.close()
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=5)
    print(chapter, browser_name, "startup, forward/back, recompile, playback, disconnect, and offline startup passed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("chapter", nargs="?", default="chapter25")
    parser.add_argument("--browser", default="msedge", choices=("msedge", "chrome", "firefox"))
    args = parser.parse_args()
    check(args.chapter, args.browser)
