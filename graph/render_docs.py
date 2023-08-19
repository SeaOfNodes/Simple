"""Render documentation snapshots with the same layout and SVG export as make view.

Requires Python Playwright and an installed browser; not needed to build chapters.
Example: python graph/render_docs.py --browser msedge chapter01/docs/01-graph.json
"""
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
from threading import Thread
from playwright.sync_api import sync_playwright


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--browser', default='chromium', choices=['chromium', 'msedge', 'chrome', 'firefox'])
    parser.add_argument('snapshots', nargs='+', type=Path)
    args = parser.parse_args()
    web = Path(__file__).resolve().parent / 'web'
    server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(web)))
    Thread(target=server.serve_forever, daemon=True).start()
    try:
        with sync_playwright() as pw:
            engine = pw.firefox if args.browser == 'firefox' else pw.chromium
            options = {'channel': args.browser} if args.browser in ('msedge', 'chrome') else {}
            browser = engine.launch(headless=True, **options)
            page = browser.new_page(reduced_motion='reduce')
            # No connection to a compiler or to the user's live viewer session.
            page.route_web_socket('**', lambda ws: ws.on_message(lambda message: None))
            page.goto(f'http://127.0.0.1:{server.server_port}/index.html')
            page.wait_for_function('rendererReady')
            page.evaluate('''() => {
                layout = new GraphLayout();
                document.getElementById("assocs").checked = true;
                document.getElementById("near").checked = false;
            }''')
            for source in args.snapshots:
                snap = json.loads(source.read_text(encoding='utf-8'))
                page.evaluate('''async snap => {
                    const evt = {kind: "PHASE", phase: "Documentation", near: [], temps: []};
                    const scene = await layout.run(snap, evt);
                    renderer.show(snap, scene, evt);
                    renderer.select(0);
                }''', snap)
                # Capture the normal export's Blob without bulk browser downloads.
                svg = page.evaluate('''async () => {
                    const create = URL.createObjectURL, click = HTMLAnchorElement.prototype.click;
                    let blob;
                    URL.createObjectURL = value => { blob = value; return create(value); };
                    HTMLAnchorElement.prototype.click = () => {};
                    try { renderer.save(1); }
                    finally { URL.createObjectURL = create; HTMLAnchorElement.prototype.click = click; }
                    return await blob.text();
                }''')
                target = source.with_suffix('.svg')
                target.write_text(svg + '\n', encoding='utf-8', newline='\n')
                print(target)
            browser.close()
    finally:
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    main()
