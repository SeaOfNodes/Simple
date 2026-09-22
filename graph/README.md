# Shared graph viewer

The browser application lives in `web/` and is shared by chapters 18–25.
`web/index.html` contains the page and `web/viewer.js` manages playback.
`web/layout.js` runs ELK layout in a browser worker; `web/render.js` draws SVG
and handles selection, pan and zoom. `web/legacy.js` loads Graphviz on demand
for earlier chapters. Libraries live in `web/vendor/`, with one checked-in
copy of each; chapters do not copy them during builds.

From a chapter directory, run `make view`. The chapter's `JSViewer` finds
`graph/web/index.html` by walking up from the working directory, serves its
directory through the JDK's HTTP server on a local ephemeral port, and opens
that web address in the browser. This works from a chapter directory, the
repository root, or the root of a linearized checkout. Java launches from elsewhere can specify
`-Dsimple.graph.url=<viewer URL>` before the main class.

The viewer uses the existing WebSocket connection. Chapter 25 sends structured
JSON snapshots in each frame; earlier chapters send bare DOT.
No JavaScript package installation, frontend build, or separate server process
is required.
For development over HTTP, run this from the repository root:

```sh
python -m http.server 8000 --bind 127.0.0.1 --directory graph/web
```

Then launch Java with `-Dsimple.graph.url=http://127.0.0.1:8000/index.html`.
The compiler WebSocket still uses port 12345; run one viewer session at a time.

The initial program compiles on connection. For another program, click **Compile**
or press **Ctrl+Enter** (**Cmd+Enter** on macOS), then use the arrows to step
through the captured frames. Keep `make view` running while using the tab.

In chapter 25, scroll to zoom, drag to pan, and use **Fit** to show the whole
graph again. Clicking a node shows its full label, type and input slots, and
highlights its edges. Selection follows that node's ID across frames; Escape
clears it. **Associations** shows scope bindings and lifetime edges, which are
hidden by default. Input slots retain their indices, including null slots.

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
classes. The adapters below share snapshot capture; moving the transport into
the shared Java code is a separate step.

The connection messages are:

| Direction | Message | Meaning |
| --- | --- | --- |
| Compiler to browser | `!` | Request the source program |
| Browser to compiler | Source text | Compile and capture a new sequence |
| Compiler to browser | `digraph ...` | One complete DOT snapshot |
| Compiler to browser | JSON `{snap, pos}` | Chapter 25's complete graph and parser position |
| Browser to compiler | `+` | Acknowledge/request another frame |
| Compiler to browser | `#` | End of sequence |
| Browser to compiler | `null` | End the session |

Chapter 25's JSON frame has this shape:

```json
{
  "snap": {
    "ver": 1, "comp": "compilation UUID", "step": 0, "roots": [1],
    "nodes": [
      {"id": 1, "label": "Start", "type": null, "kind": "CTRL", "edges": [], "proj": null}
    ]
  },
  "pos": -1
}
```

`GraphJson.write(snap)` serializes the graph alone. `GraphJson.frame(snap, pos)`
adds the playback envelope. `comp` changes for each submitted
program, and `step` starts at zero. `nodes` carries the complete graph, including
each node's `id`, `label`, `type`, `kind`, `edges`, and `proj`. Enum values use
their names; absent types, labels and projections use JSON null. Missing node
references use ID zero. `pos` is the parser position, or -1 outside parsing.

The browser caches the parsed objects in `frames`. It computes geometry on the
first visit to a frame and keeps it in `frames[i].layout`; revisiting a frame
uses exactly that geometry. Viewport changes do not rerun layout. Nodes and
edges have stable SVG IDs within a compilation. There is no animation yet.
One `+` acknowledges the whole frame. The chapter 25 writer sends UTF-8 bytes
and supports WebSocket's 64-bit length header for frames larger than 65535 bytes.

Earlier chapters' DOT frames may contain a `// POS:` comment identifying the
parser position. The browser owns frame history and backward/forward playback.

Chapter 25 no longer generates DOT for the interactive viewer. The browser
uses ELK's layered layout with fixed input ports and orthogonal routing.
Layout follows def-to-use flow downward, while the displayed arrows point
from use to def, matching Simple's edges. Control edges get a higher layout
priority; known Loop/Phi backedges get a lower priority. Associations are drawn
as a separate overlay and do not constrain layout. Projections remain real
nodes for now.

This first layout is flat. It does not discover compiler loops, compute RPO,
or build a SESE hierarchy. ELK breaks cycles for drawing, which can still put
whole-program cycles in an awkward order. Compiler-provided grouping and CFG
ordering are follow-up work, along with preserving positions across rewrites.
Each visited frame currently retains a full snapshot and layout; bounded
history, checkpoints and deltas are also follow-up work.

ELK runs locally from the pinned elkjs 0.12.0 worker; see
[`web/vendor/elk-README.md`](web/vendor/elk-README.md) for provenance and licensing.
No npm or Maven step is needed to use it.

The linearization workflow includes this directory as a shared root resource.
Chapters 4 and 25 compile the shared Java sources from this directory; the
interactive viewer also loads the web assets from this checkout.

## Chapter adapters

The shared Java code is in `src/main/java/com/seaofnodes/graph/`:

- `GraphAdapter<N>` is an abstract base with hooks for `id`, `desc`, and indexed
  edge access (`nIns`/`in`, `nOuts`/`out`). Its final `snap` method walks definitions and
  uses iteratively, handles cycles, and sorts the copied records by ID.
- `GraphSnapshot` is detached data: compilation key, step, roots, and nodes.
  Each node has an ID, plain-text label/type, kind, edges to its defs, and optional
  projection metadata. It contains no chapter classes or layout coordinates.
  Node and edge lists are concrete `ArrayList`s, treated as read-only after capture.

Root IDs use `int[]`. Capture uses a node table indexed by ID for visitation and
root deduplication; no boxed integer lists, sets or map keys are needed.
Indexed edge access reads each chapter's native storage without copying it into
a different collection type.

Both chapters implement `com.seaofnodes.simple.print.SimpleGraphAdapter`,
extending `GraphAdapter<Node>`. No changes to `Node` or its subclasses are
needed. The chapter owns classification and extraction; the base owns traversal
and snapshot assembly. No new Java interface is involved.

For chapter 4:

```java
var parser = new Parser("return 1+arg+2;");
var ret = parser.parse();
var adapter = new SimpleGraphAdapter();
var frame = adapter.snap("compile-1", 0, ret, parser._scope);
```

For chapter 25:

```java
var code = new CodeGen("while(arg < 10) arg = arg + 1; return arg;").parse();
var adapter = new SimpleGraphAdapter();
var frame = adapter.snap("compile-1", 0, code._stop);
```

During a peephole, pass the current node and any unattached replacement as
additional roots. Null roots are ignored. Capture runs on the compiler thread
at a point where the graph is not changing; consumers can retain the returned
records after compilation resumes. The adapter does not compute types, invoke
peepholes, schedule nodes, or rearrange use lists.

Node IDs are the chapter's `_nid`, scoped by the caller's compilation key. Use
a new key whenever a parser/compiler resets IDs. A snapshot rejects two distinct
nodes sharing one ID. Each edge belongs to its use node and names the referenced
`def` ID and the input slot `idx` on that use: `use.in(idx) == def`. The adapter
derives these edges from the compiler's `_inputs`; `_outputs` only speeds up
traversal. Repeated definitions produce distinct edges with different slot
indices. Each input slot has an `Edge` record, including holes with `def == 0`.
Edges remain in input-slot order under their use node.
Projection nodes retain their own IDs and identify their parent and tuple
index, allowing the renderer to fold them into ports later. A missing parent
has `par == 0`; real node IDs are never zero.

Chapter 4 adds control/data roles and scope binding names. Chapter 25 adds
memory roles and node kinds for Phis, Regions, Loops,
Functions and compilation-unit boundaries. These are existing IR facts, not
computed region membership or a SESE hierarchy. Scope bindings and constant
lifetime edges are associations, so they need not constrain CFG layout.
Types may be absent on newly constructed nodes. Source locations, grouping,
observer events and delta encoding remain follow-up work.

Make compiles these shared sources directly into each participating chapter's
classes using `javac`. From the repository root:

```sh
make -C chapter04 build
make -C chapter25 build
```

`build` compiles the compiler and adapter without running tests or opening a
browser. Shared source changes trigger recompilation. No Maven executable,
Maven-built artifacts, generated source copies or separately installed graph
JARs are needed. Existing `make tests`, `make release` and chapter 25's
`make view` also include the shared sources through their normal prerequisites.

For the existing Maven/CI build path, `build-helper:add-source` describes the
same source directory. The two chapter POMs select `../graph`, while the root
POM defaults to `graph` for linearized checkouts. These entries are independent
of the Make build.

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

Chapter 25's interactive viewer now uses the adapter's numeric IDs and plain-text
labels; browser rendering does not interpret labels as HTML. Capture traverses
`_outputs` without changing their order. The older DOT exporters still need
review before extending their adapters.

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
