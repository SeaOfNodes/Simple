# Shared graph viewer

The browser application lives in `web/` and is shared by chapters 1–25.
`web/index.html` contains the page and `web/viewer.js` manages playback.
`web/groups.js` projects folded regions into display nodes; `web/layout.js`
runs ELK layout in a browser worker; `web/render.js` draws SVG
and handles selection, pan and zoom. Libraries live in `web/vendor/`, with one checked-in
copy of each; chapters do not copy them during builds.

From a chapter directory, run `make view`. The shared `GraphViewer` finds
`graph/web/index.html` by walking up from the working directory, serves its
directory through the JDK's HTTP server on a local ephemeral port, and opens
that web address in the browser. This works from a chapter directory, the
repository root, or the root of a linearized checkout. Java launches from elsewhere can specify
`-Dsimple.graph.url=<viewer URL>` before the main class.

The viewer uses a WebSocket connection. Every chapter sends structured
JSON snapshots in each frame.
No JavaScript package installation, frontend build, or separate server process
is required.
For development over HTTP, run this from the repository root:

```sh
python -m http.server 8000 --bind 127.0.0.1 --directory graph/web
```

Then launch Java with `-Dsimple.graph.url=http://127.0.0.1:8000/index.html`.
The compiler WebSocket still uses port 12345; run one viewer session at a time.

The initial program compiles on connection. For another program, click **Compile**
or press **Ctrl+Enter** (**Cmd+Enter** on macOS). Use the arrows to step through
frames, the first/last buttons to jump to either end, or enter a frame number
and press **Enter**. The slider scrubs through available frames; its arrow keys
step and **Home/End** jump to either end. Keep `make view` running while using the tab.
Frame numbers start at 1. Jumps lay out only their destination. When the slider
moves during layout, the viewer finishes that calculation and then handles the
latest request, skipping intermediate requests. Already visited layouts stay cached.

Functions, loops and closed If/Region diamonds have enclosing boxes. Click **−** in a box's upper-left
corner to fold it into a thick-bordered node; click **+** to reopen it.
**Unfold all** restores the complete graph. Folds persist while stepping forward
or backward, including child folds inside a folded parent. Compiling a new
program resets them. Edges between the same visible use/def pair bundle when
either endpoint is folded. Bundles use thick lines: red if any member is control,
otherwise blue if any is memory, otherwise the data color. Pure associations
retain their dash pattern and visibility toggle. Hover for the original node IDs and input slots.
Selection and peephole highlights
follow hidden nodes onto their enclosing folded box.

Scroll to zoom, drag to pan, and use **Fit** to show the whole
graph again. Clicking a node shows its full label, type and input slots, and
highlights its edges. Selection follows that node's ID across frames; Escape
clears it. **Associations** shows scope bindings and lifetime edges, which are
hidden by default. Input slots retain their indices, including null slots.
**Peep neighborhood** outlines nearby nodes in amber, with a darker outline
on the current node/replacement. The event label identifies the phase, rewrite,
and enclosing rewrite when calls nest. This marks context; it does not zoom or
animate the rewrite yet.

For documentation figures, step to the desired frame and click **Save SVG**.
It saves the whole graph, regardless of pan/zoom, with its current association
visibility, neighborhood highlighting and selection. Clear selection with
Escape and uncheck **Peep neighborhood** for an unmarked diagram. The SVG
includes its styles and arrow markers and needs no JavaScript or viewer assets;
check it into the chapter's docs and embed it like the existing static figures.
Existing `.gv` sources and their generated SVGs remain another way to maintain
documentation figures.

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

All chapters share the Java viewer plumbing as well as the browser:

- `GraphViewer` owns asset discovery, the source/compile loop and session shutdown.
  Each chapter launches it from `SimpleGraphObserver.main`; no separate launcher
  class is needed.
- `GraphSocket` serves local assets, opens the browser, and handles WebSocket
  messages. One buffered stream handles both the upgrade and frames. Payloads
  use exact reads and their advertised length, including 64-bit lengths;
  continuation frames assemble UTF-8 before decoding, and PING data is echoed.
- `GraphCapture<N>` owns nested attempts, buffered snapshots, event numbering,
  neighborhood accumulation, serialization and disconnect handling. It extends
  `GraphObserver<N>`. Chapter subclasses compile source, attach/detach the observer,
  and supply roots, phase and parser position. Each compilation gets fresh capture
  state and a new compilation key, even when the previous compilation failed.
- `GraphAdapter<N>` supplies node facts and indexed graph/dependency access.
  Dependency access defaults to an empty set in chapters without those lists.

Shared Java has no dependency on chapter classes.
The WebSocket listener and built-in asset server bind to 127.0.0.1.

The connection messages are:

| Direction | Message | Meaning |
| --- | --- | --- |
| Compiler to browser | `!` | Request the source program |
| Browser to compiler | Source text | Compile and capture a new sequence |
| Compiler to browser | JSON `{snap, pos, evt}` | Complete graph, parser position and event |
| Compiler to browser | JSON `{error}` | Compilation failed; the session accepts another program |
| Browser to compiler | `+` | Acknowledge/request another frame |
| Compiler to browser | `#` | End of sequence |
| Browser to compiler | `null` | End the session |

The JSON frame has this shape:

```json
{
  "snap": {
    "ver": 1, "comp": "compilation UUID", "step": 0, "roots": [1], "scope": 0,
    "nodes": [
      {"id": 1, "label": "Start", "type": null, "kind": "CTRL", "edges": [], "proj": null}
    ],
    "groups": []
  },
  "pos": -1,
  "evt": {
    "kind": "PHASE", "peep": 0, "up": 0, "phase": "Opto",
    "node": 0, "repl": 0, "near": []
  }
}
```

`GraphJson.write(snap)` serializes the graph alone. `GraphJson.frame(snap, pos, evt)`
adds the playback envelope. `comp` changes for each submitted
program, and `step` starts at zero. `nodes` carries the complete graph, including
each node's `id`, `label`, `type`, `kind`, `edges`, and `proj`. Enum values use
their names; absent types, labels and projections use JSON null. Missing node
references use ID zero. `pos` is the parser position, or -1 outside parsing.
`scope` identifies the parser's active scope (zero outside parsing); scope nodes
in `roots` include the parser's saved scopes. These are context references,
not IR edges.

The browser caches the parsed objects in `frames`. It computes geometry on the
first visit to a frame and keeps it in `frames[i].layout`; revisiting a frame
uses exactly that geometry. Viewport changes do not rerun layout. Nodes and
edges have stable SVG IDs within a compilation. There is no animation yet.
Each frame also caches its most recent folded layout, keyed by the folded
header IDs; changing folds recomputes that view without changing the snapshot.
One `+` acknowledges the whole frame. The shared writer sends UTF-8 bytes
and supports WebSocket's 64-bit length header for frames larger than 65535 bytes.

The browser owns frame history and backward/forward playback. It
uses ELK's layered layout with fixed input ports and orthogonal routing.
Layout follows def-to-use flow downward, while the displayed arrows point
from use to def, matching Simple's edges. Control edges get a higher layout
priority; known Loop/Phi backedges get a lower priority. Network-simplex node
placement gives forward control edges a straightness priority of 100; other
edges have zero straightness priority. Associations are drawn
as a separate overlay and do not constrain layout. Each MultiNode and its
projections occupy one box: the parent above, projection cells below in index
order. Each cell keeps its node ID, selection, highlights and edge connections;
the internal parent/projection edge is represented by the shared box. A
projection whose parent is absent is drawn on its own. Node kinds use color
and shape rather than a KIND label. IDs share the name line; projections use
`#id/idx`. Ordinary nodes have two lines (name and type) in a 44-pixel box.

Phi slot 0 uses a short left-facing arrow at the middle of the box's left side,
instead of a full edge to its Region. The tooltip and node details retain the
definition ID. Value inputs stay along the top.
Each Region (including a loop header) and its Phis form one layout row, with
the Region on the left. Their boxes, ports, selections and node IDs stay separate.
This also keeps a newly created Phi on its Region's row while the parser holds it.
Stops occupy the bottom of the graph, beside the Parser box when it is present,
even before return edges attach. StopCUs share a row above their outer Stop;
StartCUs sit below Start, connected by their real control inputs. Chapter adapters
identify Stops with `Kind.STOP`.
Stops and chapter 25's Start/StartCU nodes use compact single-line boxes; their
full types remain in the tooltip and selection details. Start-to-Stop SCCP
feedback edges remain in the snapshot/details but have no drawn edge or port.
Chapter 25 omits its cached zero and XCtrl nodes when only keep references hold
them alive. Actual graph uses, including Scope bindings, make them visible.
Type printers use short names for common extrema, including `MemBot`/`MemTop`,
`StructBot`/`StructTop`, `PtrBot`/`PtrTop` and `FunBot`/`FunTop`. These aliases
apply only to the exact types; more precise aliases, fields and escape sets
still print normally. Integer and float types retain their existing short names.

Scopes have named slots across the top, including control, memory and variable
bindings. Edges leave these slots upward toward their definitions. They stay visible
even when **Associations** is unchecked, and do not constrain CFG layout.
While parsing, saved scopes line up beside the graph, slightly below their
`$ctrl` definitions, with space to avoid overlapping other scopes. The active
scope stays at the bottom. The **Parser** box points to both active and saved
scopes using display-only arrows.

During parsing, values with no graph users yet appear in a separate bottom
row, with dashed display-only arrows from a **Parser**
box. Scope bindings count as users. Pending projections keep their parent box
and get an arrow to their own cell. These marks disappear when real uses attach
or parsing finishes; they do not add nodes or edges to the compiler graph.
This identifies unconsumed values from the snapshot, not Java stack references.

Function, loop and diamond membership comes from the compiler process; ELK
lays out the supplied hierarchy. RPO is not a pending layout requirement.
Placement can still shift substantially between rewrites.
Each visited frame currently retains a full snapshot and layout; bounded
history, checkpoints and deltas are also follow-up work.

Pending graph work:

- Refine CFG placement with compilation-unit containers and fuller membership
  for unfinished loops and paths leaving loops.
- Preserve positions across peepholes, then animate edits and zoom into the
  peep neighborhood before returning to the whole graph.
- For large compilations, add checkpoints and forward deltas with a bounded
  cache of recent backward steps.

### Function, loop and diamond grouping

Shared Java `GraphGroups` computes display ownership from detached snapshots.
The snapshot's `groups` array contains `{id, par, nodes}`: the header ID, parent
group ID (zero outside), and directly owned node IDs. A node has one display
home. Functions contain their CFG; natural loops nest inside functions and
other loops. Closed If/Region diamonds form nested SESE groups. Pinned values
follow their control; Phis stay with their Region. A floating node can join a
group if all semantic inputs come from that group, or all semantic outputs go
to it. Matching nominations keep it inside; sibling nominations leave it
outside both, in a common enclosing group. Nested nominations keep it in the
enclosing group: a function and its loop both nominate the function. Null slots and parser/lifetime
associations do not nominate a group, and constants can remain outside.
Ownership propagates through floating chains and is checked against the final
neighbors. Phi uses refer to the Phi itself, not its incoming CFG path: a loop
backedge value does not thereby belong to the diamond producing the backedge.
Scopes and Stops remain outside containers for their existing placement rules.

Loop membership walks backward from the backedge to the header. Before that
backedge is attached, only the header and values anchored there belong to the
loop. Paths that leave via break/return can remain in the enclosing group.
Diamond discovery checks both arms for a common Region, rejects extra CFG
entries/exits and paths that cannot reach the join, and accepts only nested or
disjoint groups. Unfinished diamonds have no box until their join closes.
This is conservative display grouping, not the compiler's loop tree.
Capture never invokes the compiler loop-tree pass, which can mutate
the graph by adding exits for infinite loops. No chapter-specific grouping
hooks or compiler edits are needed.

Expanded groups are ELK compound layout containers with a visible border and
a fold button. Sibling groups occupy disjoint boxes; nested groups remain inside
their parent. Group padding is 12 pixels at the sides/bottom and 34 at the top
for the heading and fold button. The hierarchy constrains layout and routing.

Browser-side folding replaces the group's visible contents with one thick-bordered box,
its label, a hidden-node count and an unfold button. It hides internal edges and
redirects each crossing edge to the folded box, retaining its original node IDs
and use-slot index for details and selection. Bundles share a port and route;
the tooltip retains every member, and selecting any member highlights the bundle.
Expanded node slot labels list the merged input indices. Folded boxes omit slot
labels. Unfolding restores separate edges and ports from the original snapshot.
This is a browser view of the snapshot, not a compiler graph rewrite.

Fold state persists by header ID across frames and backward playback,
including remembered child folds when a parent is unfolded. A folded group
containing a selected node or an active peephole carries that highlight.
Recompiling resets fold state along with frame history.

ELK runs locally from the pinned elkjs 0.12.0 worker; see
[`web/vendor/elk-README.md`](web/vendor/elk-README.md) for provenance and licensing.
No npm or Maven step is needed to use it.

The linearization workflow includes this directory as a shared root resource.
All chapters compile the shared Java sources from this directory; the
interactive viewer also loads the web assets from this checkout.

## Chapter adapters

The shared Java code is in `src/main/java/com/seaofnodes/graph/`:

- `GraphAdapter<N>` is an abstract base with hooks for `id`, `desc`, and indexed
  edge access (`nIns`/`in`, `nOuts`/`out`). Its final `snap` method walks definitions and
  uses iteratively, handles cycles, and sorts the copied records by ID.
- `GraphSnapshot` is detached data: compilation key, step, roots, active scope, nodes and groups.
  Each node has an ID, plain-text label/type, kind, edges to its defs, and optional
  projection metadata. It contains no chapter classes or layout coordinates.
  Node and edge lists are concrete `ArrayList`s, treated as read-only after capture.

Root IDs use `int[]`. Capture uses a node table indexed by ID for visitation and
root deduplication; no boxed integer lists, sets or map keys are needed.
Indexed edge access reads each chapter's native storage without copying it into
a different collection type.

Every chapter implements `com.seaofnodes.simple.print.SimpleGraphAdapter`,
extending `GraphAdapter<Node>`. The chapter owns classification and extraction; the base owns traversal
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
CallEnd links to known callees additionally carry `jump`, the function entry ID.
The real `def` remains the callee's Return. Chapters 18–25 supply this optional
field; zero is omitted from JSON. The browser draws a small local shortcut
beside CallEnd instead of routing that link across functions or giving it to
ELK. Clicking the shortcut (or pressing Enter/Space with it focused) centers
and selects the function at the current zoom, including a folded function.
Folding the caller retains shortcuts for external callees. Node details still
show the original input slots and Return IDs.
Projection nodes retain their own IDs and identify their parent and tuple
index, allowing the renderer to fold them into ports later. A missing parent
has `par == 0`; real node IDs are never zero.

Chapter 4 adds control/data roles and scope binding names. Chapter 25 adds
memory roles and node kinds for Phis, Regions, Loops,
Functions and compilation-unit boundaries. Shared grouping uses these existing
IR facts to compute display membership. Scope bindings and constant
lifetime edges are associations, so they need not constrain CFG layout.
Types may be absent on newly constructed nodes. Node source locations
and delta encoding remain follow-up work. All chapters use the observer events
described below.

Make compiles these shared sources directly into each participating chapter's
classes using `javac`. From the repository root:

```sh
make -C chapter04 build
make -C chapter25 build
```

`build` compiles the compiler and adapter without running tests or opening a
browser. Shared source changes trigger recompilation. No Maven executable,
Maven-built artifacts, generated source copies or separately installed graph
JARs are needed. Every `make view` depends on `build` through `graph/graph.mk`. Existing
`make tests` and `make release` also include the shared sources through their
normal prerequisites.

For the existing Maven/CI build path, `build-helper:add-source` describes the
same source directory. Chapter POMs select `../graph`, while the root
POM defaults to `graph` for linearized checkouts. These entries are independent
of the Make build.

## Peephole observers

`GraphObserver<N>` is a shared abstract base for `before`, `after`, `phase`, and
`dep` callbacks. `Parser._obs` (chapters 2–17) or `CodeGen._obs` (chapters 18–25) owns the
optional observer. Parser-only chapters use `Parser.PARSER` as their current
compilation context, alongside the existing global `START`. Normal compilation
leaves it null, so it does no snapshot capture or serialization. Assertions
that probe peepholes with `_midAssert` set do not generate observer calls.
Compiler hooks refer only to the shared observer base.

`GraphCapture` supplies capture and neighborhood logic. Each chapter's small
`SimpleGraphObserver` supplies its compilation context. The shared capture base
resets its state before each compilation and detaches the observer afterward.
An I/O failure disables the observer and allows optimization to continue.
The observer is detached in a `finally` block when compilation finishes or fails.

The event kinds distinguish the actual capture boundaries:

| Kind | Meaning |
| --- | --- |
| `BEFORE` | Graph before an attempt that made progress, or contained a nested rewrite |
| `RETURN` | Recursive `Node.peephole()` finished, including its local DCE; the caller has not yet attached the returned node |
| `APPLY` | A worklist attempt finished after substitution, dependency draining, and unused-node cleanup |
| `PHASE` | Parse, Iter, or Opto completed, including work outside individual peepholes |

`peep` identifies an attempt; `up` identifies its enclosing attempt, or zero.
`node` and `repl` identify the original and result nodes. A zero `repl` on
`APPLY` means removal without a surviving result. Phase events use zero IDs.
An in-place rewrite has equal `node` and `repl` IDs. Type changes also count as
progress; these events are not restricted to structural rewrites.

Before snapshots are buffered until the outcome is known. Attempts without
progress are discarded unless they contain a visible child rewrite. Ancestor
before frames are emitted before their children; after frames close the nested
attempts. Frame steps are assigned consecutively on emission, while attempt IDs
may have gaps. A failed compilation can leave an unfinished attempt; the next
compilation starts with empty capture state and a new compilation key.

Capture includes the active attempts and returned node as extra roots, so nodes
not yet attached by the parser remain visible. Dead originals are not added as
after-frame roots. The observer never adds keep edges, changes use lists, or
modifies the compiler's dependency tracking.

`near` is a primitive array of node IDs, assembled with a `BitSet`. It combines:

- The current node and replacement, with their immediate defs and uses before
  and after the rewrite.
- Existing wake-up dependents, read before those lists are drained.
- Nodes named by `addDep`/`addDepForwards` during the attempt, including calls
  whose dependency already exists or is an immediate neighbor.
- Neighborhoods of nested attempts.

This is initial display context, not an exact record of every node read or
changed by a rule. Dependency lists contain wake-up dependents; they do not
describe an entire successful pattern match. Some rules inspect distant nodes
without registering a dependency. Before events include context known when
emitted; their matching after events can add more. IDs of deleted nodes remain
in `near`, and the browser highlights only those present in the current frame.
Actual graph diffs and explicit rule annotations can refine this later.

The browser keeps the event alongside its snapshot and layout. It labels and
highlights the current step, but does not yet collapse nested rewrites or move
the camera to their neighborhoods. Chapter 4 uses the same event model, with
defs/uses for its neighborhoods and no worklist or dependency lists. It retains the final Return as a root after scope cleanup.

The compiler DOT dump flag, parser graph directive, Java DOT generators and old
viewer/transport classes have been removed from all chapters. Use **Save SVG**
for new documentation figures. Existing `.gv` sources, their checked-in SVGs
and the documentation Make rules for regenerating those SVGs remain.

Chapter 1 has no peepholes and supplies one completed parse snapshot. Chapter 2
shows constant folding; chapters 3 and 4 add variables and algebraic rewrites.
Chapters 9–17 also capture worklist application and dependency neighborhoods;
chapters 18–25 use their CodeGen phase/context. Each chapter retains only its
`SimpleGraphAdapter`, `SimpleGraphObserver`, an optional observer field and the
compiler callbacks/accessors needed by those two classes.
