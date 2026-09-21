# Shared graph viewer

The browser application lives in `web/` and is shared by chapters 18–25.
`web/index.html` contains the page, `web/viewer.js` manages playback and rendering,
and `web/vendor/` contains the existing D3 and Graphviz bundles. There is one
checked-in copy of each; chapters do not copy them during builds.

From a chapter directory, run `make view`. The chapter's `JSViewer` finds
`graph/web/index.html` by walking up from the working directory, serves its
directory through the JDK's HTTP server on a local ephemeral port, and opens
that web address in the browser. This works from a chapter directory, the
repository root, or the root of a linearized checkout. Java launches from elsewhere can specify
`-Dsimple.graph.url=<viewer URL>` before the main class.

The viewer retains the existing DOT/WebSocket protocol. No JavaScript package
installation, frontend build, or separate server process is required.
For development over HTTP, run this from the repository root:

```sh
python -m http.server 8000 --bind 127.0.0.1 --directory graph/web
```

Then launch Java with `-Dsimple.graph.url=http://127.0.0.1:8000/index.html`.
The compiler WebSocket still uses port 12345; run one viewer session at a time.

The initial program compiles on connection. For another program, click **Compile**
or press **Ctrl+Enter** (**Cmd+Enter** on macOS), then use the arrows to step
through the captured frames. Keep `make view` running while using the tab.

The launcher prints the viewer URL before opening the browser. If the desktop
only raises an existing browser window, paste that URL into a new tab. Use
`-Dsimple.graph.open=false` to suppress automatic browser opening when testing
or connecting a browser manually. A disconnected viewer stays open and shows
its connection status instead of silently closing the tab.

After building a chapter, `python graph/test_transport.py chapter25` from the
repository root checks HTTP asset delivery, a delayed and fragmented WebSocket
upgrade, and session shutdown without opening a browser.

`python graph/test_browser.py chapter25 --browser msedge` additionally checks
actual rendering, stepping backward/forward, compiling multiple programs, and
disconnected startup in a headless browser. This optional test requires the
Python `playwright` package and the selected browser; it is not a viewer runtime
dependency. Use `--browser firefox` after `python -m playwright install firefox`
for Firefox testing. Test logs/screenshots are written under `build/graph-browser/`.

## Boundary between chapters and the viewer

Each chapter currently retains its Java graph exporter, compiler hooks, and
WebSocket transport. The browser code has no dependency on a chapter's Java
classes. Moving the transport into a shared Java library is a separate step;
the frontend extraction does not add dependencies to the compiler builds.

The legacy protocol is:

| Direction | Message | Meaning |
| --- | --- | --- |
| Compiler to browser | `!` | Request the source program |
| Browser to compiler | Source text | Compile and capture a new sequence |
| Compiler to browser | `digraph ...` | One complete DOT snapshot |
| Browser to compiler | `+` | Acknowledge/request another frame |
| Compiler to browser | `#` | End of sequence |
| Browser to compiler | `null` | End the session |

DOT frames may contain a `// POS:` comment identifying the parser position.
The browser owns frame history and backward/forward playback.

The next layout implementation belongs here. Its chapter interface should use
versioned snapshots with stable IDs, node labels/roles, indexed inputs, edge
roles, and optional source/grouping metadata. Keep compiler-specific traversal
in chapter adapters, and layout, routing, animation, and interaction here.
Full snapshots are a useful first interface; deltas can follow if needed.

The linearization workflow includes this directory as a shared root resource.
The compiler remains self-contained in each chapter; the optional interactive
viewer additionally needs this directory from the same checkout.

## Peephole hook investigation

The current chapter25 capture sites are:

- `Node.peephole()`: before a new node's first optimization, and after
  `peepholeOpt()` reports progress. The latter happens before recursive
  optimization and `deadCodeElim`, and before the caller attaches the result.
- `IterPeeps.iteratePeeps()`: after a successful worklist optimization and
  substitution, but before the subsequent unused-node cleanup.
- `CodeGen.parse()`: after parsing, at the phase boundary.

These sites capture different meanings of “after.” Recursive peepholes can
also produce intermediate frames. Replacing `JSViewer.show()` with a generic
callback alone would preserve that ambiguity.

The chapter25 interactive exporter now sorts a copy of `_outputs` for display,
uses numeric node IDs independent of labels, and escapes HTML label text.
The older exporters still need the same review before extending their adapters.

A follow-up should introduce an optional compilation observer, owned by the
compilation/session, with explicit events for an attempted rewrite, an applied
replacement, and a phase boundary. Events should identify the node/replacement
and let the chapter adapter capture a snapshot without modifying the IR. The
viewer can group nested rewrites into one visible step. Capture completed
rewrites after the caller has installed the replacement and performed cleanup;
keep intermediate snapshots available when teaching the rewrite itself.
For parse-time nodes not yet reachable from the normal roots, the adapter must
also include the event's current node/replacement as snapshot roots.

Serialization and transport should be inactive when no observer is installed.
Graph drawing should not appear in `Node`'s API, and browser connection failures
should not change compilation behavior. These are design notes, not changes to
the current compiler callbacks.

Constant-folding peepholes already exist in chapter02. Chapters 03 and 04 add
variables and algebraic rewrites, respectively, and are useful next adapters.
They should not need the later chapters' `CodeGen`, worklist, or type hierarchy
to use the shared display.
