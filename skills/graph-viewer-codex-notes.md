# Shared graph viewer: restart notes

Updated 2026-09-25. Read graph/README.md for the current design and protocol.
These are AI-facing continuity notes, not a new task list or authority to change
compiler semantics. Cliff reports the peephole animations, inlining display, and
TypeCheck diagnostics committed and working well as of 2026-09-25.

## Working tree and latest change

Preserve existing dirty files. This checkout still reports earlier viewer changes
and untracked peep.js despite Cliff's report of commits; do not reconcile or
commit those files merely on the basis of these notes.

Latest request: Left/Right arrow keys perform the same actions as the Previous/Next
buttons, including animation stages. Added outside inputs/editable content with
no modifiers; slider and source-editing keys keep their existing behavior.
Source cursor updates preserve focus so repeated playback keys keep working.
Disposable headless check build/check-viewer-keys.py passed forward/back mouse
equivalence, input/modifier exclusions, slider navigation, and the first-frame
boundary. These changes need only a browser refresh.

Previous request: run TypeCheck after optimization and end playback on a visible
diagnostic. Implemented in chapters 10-25 (the chapters with a TypeCheck pass):

- Chapters 10-17: SimpleGraphObserver calls STOP.typeCheck() after Iter; the
  previously package-private method is now public. Chapters 18-25 call
  CodeGen.parse().opto().typeCheck(). Earlier chapters remain unchanged.
- GraphObserver.error(node,msg) is an optional callback. Existing compiler type
  checking reports the node it diagnoses, rather than the viewer repeating the
  check or trying to infer the node from text. Chapter 25 retains its originating
  error selection. Compiler behavior without an observer still throws normally.
- GraphEvent has ERROR and optional msg. Node ID 0 handles a diagnostic without
  a node, such as an unresolved struct name. GraphCapture emits a final snapshot
  before detach and catches an already-reported exception in its viewer run,
  avoiding a duplicate global error response. Success emits TypeCheck PHASE.
- The browser treats ERROR as a stable terminal frame. Next/Last/frame jumps
  reveal folded ancestors locally, center the bad node at readable scale, and
  show a red outline plus the message on the event line and in node details.
  Persistent fold choices and earlier history remain available. A new compile
  clears the diagnostic. Node-free errors show the message without centering.
- Restart make view for Java changes; refreshing is enough for JS-only changes.

Validation on 2026-09-25: all 25 chapters built with Make. Headless browser checks
passed for real TypeCheck errors in chapters 10, 17, 18, 25, including final Next,
folded error nodes, backward playback, direct jump, and a subsequent valid compile.
Dead erroneous code was removed before TypeCheck. Existing JUnit checks passed:
chapter10 Chapter10Test (27), chapter18 Chapter13Test + Chapter18Test (39),
chapter25 Chapter13Test (16). This was NOT an all-chapter full test-suite run.

## Durable design and owner preferences

- Shared Java capture/model and browser code live in graph/. Each chapter keeps
  a small SimpleGraphAdapter and SimpleGraphObserver, plus compiler callbacks.
  No Maven requirement. DOT compiler/viewer support is gone; existing docs GV/SVG
  assets and documentation regeneration rules remain. Keep the backport docs.
- Node inputs describe USE -> DEF: containing node is the use, edge idx is its
  input slot, def is the target ID. 0 means no node. Output lists are reverse
  caches, not semantic edge descriptors. Phi control comes from input 0's Region.
- Renderer uses kind through color/shape, not a printed KIND row. IDs follow names
  in small font; projections use #id/index. Multi and projections share a box.
- Parser-held temporaries are low with Parser links. Active Scope is low; pending
  scopes sit near their control, and Scope edges leave the top. Parser retention
  and nested peep temporaries must not be confused.
- Functions/loops/SESE diamonds can fold. Folded edge bundles are thick, with
  control color winning over memory, then data. Floating-node ownership can be
  nominated by all inputs or all outputs in one region; exclude only when both
  sides nominate DIFFERENT regions. RPO is off the layout task list.
- CallEnd cross-function links use clickable shortcut nodes. Loop/Phi backedges
  route around the right of the loop region. Edge clicks hop use/def under the
  stationary mouse with eased pan; preserve this behavior during display edits.
- Prefer concrete types and short names, avoid boxed high-count IDs and redundant
  requireNonNull. Do not add permanent tests for each volatile UI experiment.
- Do not open visible test tabs or use the user's live WebSocket port 12345.
  NoScript once blocked 127.0.0.1; a connecting tab is not always a compiler bug.

## Peephole animation invariants

Stable -> gather -> rewrite -> release (stable). Gather/rewrite are intermediate
views; slider/frame jumps/first/last skip them. Backward clicks reverse the stages.
Type-only and genuinely unchanged results take one click without gathering.

- Pair enclosing BEFORE with matching RETURN/APPLY, using peep/up IDs. Nested
  captures are available in the stream but not stable stops. A recursively built
  constant must not appear before its enclosing rewrite. Raw nested frame jumps
  map to the enclosing BEFORE. Capture numbers remain unchanged and can skip.
- Neighborhood: all changed nodes, original root, replacement (even an unchanged
  existing GVN result), plus their one-hop defs from both snapshots. Exclude Start
  anchors for constants. Do NOT use scheduling dependency lists as membership.
- Keep Multi/projection boxes intact. A gathered Phi brings its Region; gathering
  the Region must NOT recursively bring all sibling Phis.
- Gathered pre/post geometry shares positions and reserves room for new nodes.
  Exactly-one-hop outside neighbors stay more visible than the distant graph.
  Folds temporarily open for the edit, but persistent fold choices survive release.
- Java capture temps IDs distinguish nested peep-created nodes from Parser-held
  expression values. An unattached new replacement must not acquire a premature
  Parser arrow.
- Chapters 18-25 mark Call/CallEnd/Fun/Return mid-inlining with folding:true.
  The browser adds a small FOLDING tag and pink fill; this is unrelated to UI folds.
  Read flags without calling transiently unsafe/mutating accessors (e.g. cend()).

RETURN can precede caller attachment. GraphPeep.attached and viewer.resultEnd
conservatively extend to the next BEFORE/PHASE only if its changes are exactly
root->replacement rewires and deletion of the root/unused input nodes. No new
nodes or unrelated changes may slip in. The next BEFORE serves as both the
previous result and the next starting point. Preserve the original peep event
using editEvent; don't label it as the next peep. Streaming waits for that next
capture. Identical results without an attachment stay one-click, not empty zooms.

Reproducer (chapter18):

```simple
val _add1 = { int x -> return x+1; };
val _mul2 = { int y -> return y*2; };
return _mul2(1+_add1(2))+3;
```

Frame 111 BEFORE Phi_$mem#12 and 112 RETURN#67 had identical nodes; the caller's
ret.setDef happened in frame113. Playback now animates 111->113, showing Return#14
memory input changing 12->67 and Phi12 disappearing. Likewise 113->115 for Phi13
and 115->117 for Region11. No compiler peephole fix was needed.

## Local verification tools (disposable, ignored)

Windows workspace: C:/Users/cliffc/Desktop/Simple. Python is py -3.13. Playwright
is installed under build/graph-tools; headless Chromium channel='msedge' works.
Use an ephemeral HTTP port and intercepted browser WebSocket, or an isolated Java
capture server on an ephemeral port. Scratch helpers may disappear after cleanup.

- build/build-graph-chapters.py: Make builds all chapters through Cygwin bash.
- build/check-viewer-errors.py: captures and checks latest TypeCheck feature;
  build/error-chapterXX.json and build/viewer-error.png are outputs.
- build/check-peep-attach.py and check-peep-nav.py: attachment, streaming, reverse
  navigation; build/peep111.json is the 174-frame capture above before TypeCheck.
- build/check-peep-gvn.py, check-peep-phis.py, check-peep-order.py: GVN endpoints,
  sibling-Phi exclusion, nested replacement ordering. Some older scratch scripts
  assumed adjacent capture frames and are stale after atomic peep playback.
- build/check-folding-state.py: inlining FOLDING labels; folding-frames.json is a
  349-frame chapter25 capture predating the added final TypeCheck frame.
- build/check-graph-finish.py defines capture(ch) before its chapter05 invocation;
  helpers reuse that prefix. build/FinishFrames.java (build/graph-finish classes)
  calls GraphCapture.run on an ephemeral socket without launching a browser.
- graph/test_browser.py and graph/test_transport.py are existing smoke tools.

## Deferred work, not authorization to start

Cliff considers incomplete/exit-loop grouping adequate as is. Bounded history can
wait longer: mid-sized compiles slow the viewer, but it remains fine for demos.
Full graph snapshots currently remain cached. Stable whole-graph placement has no
agreed approach; compilation-unit containers remain deferred.
Dead-field cleanup/interprocedural reverse
liveness is explicitly deferred; do not revive the rejected whole-graph store
sweep. Private-field syntax decisions belong in the parser, not optimization.
