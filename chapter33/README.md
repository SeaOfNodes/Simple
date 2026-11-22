# Chapter 33: Automatic Memory Management

English | [日本語](README.ja.md)

[Previous: Chapter 32 — Compiler Internal Speedups](../chapter32/README.md)

This chapter starts automatic memory management with an allocation-lifetime
analysis. It runs after Opto, field-layout finalization, and LoopTree, before TypeCheck.
It reports where storage must live; allocation and machine code still use the
existing runtime. The [design survey](../docs/automatic-memory-management-survey.md)
records the language decisions. Chapter 32's
[speedup measurements](../chapter32/README.md) remain there.

The [existing-program survey](docs/allocation-survey.md) records what this first
analysis accepts and rejects across `sys` and the games, with small reductions
that separate analysis limitations from storage decisions.
The [growing-array survey](docs/growing-arrays-survey.md) identifies builders that
can finish in one scope, persistent collections, and library API changes needed
for grow-in-place storage.

## First allocation plans

Four small examples in [Chapter33Test](src/test/java/com/seaofnodes/simple/Chapter33Test.java)
exercise the first cut:

| Example | Allocation destination |
|---|---|
| Make a temporary and pass it to a reader | Current function frame |
| Return a Box containing a Thing through two factories | Caller-supplied result storage for both objects |
| Assign a newly allocated Thing to an argument's field | Storage that lives long enough for that argument |
| Call one factory twice and store into different arguments | Separate destinations for the two calls |

The analysis walks the call DAG once. It discovers callees on entry and builds
each function's summary on exit. Within a body it follows control, memory, and
references, skipping arithmetic and offset expressions. Control anchors expose
allocations even when only scalar uses remain; reference uses expose raw-address
conversions without walking the arithmetic that consumes them. Sorting is confined
to the debug dump. Parameters name symbolic storage owners;
returning or storing a fresh reference adds destination requirements. Composing
a call substitutes its actual arguments and keeps its fresh allocations distinct
from other calls to the same factory. Private callee scratch stays in the
callee's summary.

Within one summary, loads, stores, and call effects converge before a worklist
propagates destination requirements. For example, returning a Box retains its
Thing too. This local closure does not repeat the call-graph traversal. Stores
are conservative weak updates: overwriting a field does not yet release the
earlier value's lifetime constraint. Array elements share one field, regardless
of index.

Pointer-valued loads keep the child's origin separate from the holder:

```text
get(h) { return h.c; }             // returns borrowed origin arg3.c
fill(h) { h.c.d = new Thing; }     // new Thing must outlive arg3.c
```

At a call, `arg3.c` resolves to the actual holder's possible field values. A
function can build a temporary holder and return its child; only the child
needs result storage. Copying a child between holders likewise retains the
child without retaining its source holder. Call substitution also handles two
arguments referring to the same object.

The ASCII dump shows function summaries in call-graph reverse postorder (RPO):
callers before callees, starting from the program entry. Other retained functions
(such as constructor bodies whose calls were inlined) follow in a separate RPO
section. Reachability here follows direct calls in the optimized graph. Nested
loop frames stay under their parent, with siblings ordered by node ID. Analysis
still builds summaries in postorder.

```text
+-- forward_noInline
|   call#219 -> make_noInline
|   [result] call#219/new#352:Test.Thing (8 bytes)
|   [result] call#219/new#381:Test.Box (8 bytes)
|      .child --> call#219/new#352:Test.Thing
`-- return -> {call#219/new#381:Test.Box}

+-- make_noInline
|   [result] new#352:Test.Thing (8 bytes)
|   [result] new#381:Test.Box (8 bytes)
|      .child --> new#352:Test.Thing
`-- return -> {new#381:Test.Box}
```

`frame` means scratch for that function; `result` means the caller's result
destination; `static` means program lifetime. `arg3` names storage associated
with argument input 3 (ordinary class functions use input 2 for their hidden
receiver). `arg3.c` names the storage referenced by that argument's `c` field,
not storage inside the holder. `arg3 & arg4` requires a destination satisfying both owners; it does
not arbitrarily pick one. Node numbers help correlate this output with the IR.
These are ownership constraints, not assigned physical stack offsets.
Return origins are a set of alternatives: `{arg3:a | arg4:b}` means the returned
reference may point to either argument. `{}` means no tracked storage is returned.

Run a poster child and print its plan:

```sh
make -j8 test CLASS=Chapter33Test METHOD=testAllocationFactoryGraph "TEST_JAVA_OPTS=-XX:ActiveProcessorCount=2 -Dsimple.alloc.dump=true"
```

## Iteration storage and winners

Functions and loop iterations use the same `Summary` and cleanup rule. Each
summary has incoming values, normal exit values, retention effects, and scratch
storage. A loop also has continuation values: its backedge returns these as
arguments to the next iteration. Both loop exits clean up the iteration frame.
The backedge's conceptual pop/push can reuse the same fixed storage without code.

A loop can have both a surviving result and unrelated scratch. One reference-flow
calculation finds both; placement follows the summary interfaces. For example,
`testAllocationLoopWinnerAndScratch` constructs a Scratch and a candidate Thing
each iteration, tests the candidate, and breaks with the winner:

```text
|   +-- loop#221 frame; parent find_noInline
|   |   continue refs -> {}
|   |   return refs -> {call#276/new#424:Test.Thing}
|   |   [loop#221 iteration] new#484:Test.Scratch (8 bytes)
|   `-- cleanup on continue and return -> {new#484:Test.Scratch}
|   [result] call#276/new#424:Test.Thing (8 bytes); loop#221 winner
`-- return -> {call#276/new#424:Test.Thing}
```

The Thing has fixed result space reserved before entering the loop. Failed
candidates may reuse it because no earlier candidate remains reachable on the
backedge. Scratch belongs to the iteration and can be reclaimed each turn.
Storage prints inside its owning frame, including an inner loop's winner when
the outer iteration owns it. Iteration storage and function `[frame]` storage
are the same logical scratch-storage choice, with different cleanup boundaries.
A winner's referenced objects survive with it. An inner loop's winner can still
belong to the outer loop's iteration storage. Storage exported by a frame is
promoted through enclosing frames until one can own it. Calls substitute
arguments and fresh sites; nested loops already share their function's SSA
vocabulary, so their composition preserves site identities. Private callee
scratch stays in the callee.

The abbreviated dump above omits the value ports. The full dump names entry,
continuation, and exit values, including scalar and memory values. A scalar
loaded from scratch can cross an exit without retaining the scratch object.
Returning from nested loops exports through each intervening summary; each
frame retains its own cleanup set. These logical storage frames are separate
from machine return-address and spill slots.

The continuation summary follows reference Phis and memory reaching each backedge,
including helper-call retention. Borrowed references can continue to the next
iteration. Keeping an earlier candidate in a variable, linking
candidates together, or storing them into a pre-existing array rejects the plan.
Publishing through an argument's field only on an exit arm is allowed. For now,
even bounded retention and overwritten memory slots reject: weak updates do not
prove that the old reference died. Collection destinations and more precise
memory liveness remain future work. Floating scalar uses may finish before an
iteration reset; uses constrained outside the loop retain their referenced storage.

These are still plans: no stack reservations, slot reuse, or reclamation are
emitted yet. Allocation uses each CFG node's tightest containing LoopTree and
the tree's parent links. Simple produces reducible control flow; that is an
asserted invariant, not an allocation rejection. The early LoopTree walk leaves
the IR unchanged. Before scheduling, the tree is rebuilt to account for Lower's
CFG changes, and loops with zero exits receive one synthetic exit. Delaying those
exits preserves nonreturning-function summaries during type checking and serialization.

That ordering is temporary. Before this chapter closes, the original LoopTree
build must insert NeverExits, Allocation's zero-exit support must be removed,
and Lower must maintain the tree with local edits instead of a full rebuild.
The [roadmap](../docs/roadmap.md#phase-ordering-and-incremental-analysis) tracks
this deferred work, including preserving nonreturning-function semantics.

Native calls borrow their arguments until the call returns: they must not retain,
hide, or publish those references. Converting an array pointer to an integer now
adds a `KeepAlive(control, value)` at the converting scope's exits. Allocation
traces that value through arithmetic and Phis to its storage. The integer can
travel through memory and Simple wrappers without losing the original endpoint.
The native ABI and pointer representation are unchanged.

The marker keeps every conversion, including earlier values of reassigned locals.
Returns, breaks, and continues end the scopes they leave; unbraced if/else arms
and loop bodies also have their own borrowing scopes. Conditional expressions
share their enclosing scope. Returning or storing the integer does not extend
that scope: using the address afterwards can access reclaimed storage.

Markers survive serialization and inlining, then disappear before scheduling;
they require no registers or machine instructions. Zero/null, duplicate adjacent
markers, and unused address expressions fold away. Placement still chooses whole
function/iteration frames, so a lexical endpoint can conservatively retain storage
in a larger frame. Actual stack lowering is still pending. Serialized IR is now
format 3; older objects must be rebuilt to supply these lifetime endpoints.

Native results that are Simple pointers also need an explicit origin/allocation
contract; they are not assumed to be borrowed or freshly allocated.

The first cut also rejects plans for recursive calls, unknown/opaque Simple calls,
variable-size allocations, and reference origins that it cannot trace.
Weak updates can also invent endlessly longer field paths in
code such as `p.next = p.next.next`; the pass rejects these plans when paths
exceed the body's reads plus the path depths supplied by its callees. Tracking
memory order is future work. A rejected function is printed as `REJECT`, never
as a usable partial plan. Scalar field reads and writes do not themselves
introduce retained references.

Normal compilation records those rejections and continues with the existing
allocator. `-Dsimple.alloc.check=true` rejects compilation if any function lacks
a supported plan; tests can also require an individual summary to be supported.
The pass does not yet insert hidden destination arguments, stack reservations,
or reclamation. The proposed inline-object and growing-array syntax is not
implemented by this analysis experiment.

[Allocation.java](src/main/java/com/seaofnodes/simple/codegen/Allocation.java)
contains the walk, one summary class, and one storage-site class. Comments mark
the next work: collection and SCC destinations, memory-ordered field updates, bin packing,
free lists, and parallel growth areas. No new IR nodes or serialized contracts
are introduced yet.

## Build and use this chapter

From `chapter33`:

```sh
make -j8 release
make -j8 sanity
make -j8 tests
```

`sanity` keeps the full lattice checks and focused compiler regressions.
Run the full suite before finishing a change. For a fully headless full run,
use `make -j8 tests_nographics` to disable MiniFB and miniaudio.

Run one test class, or enable every-step verification for an investigation:

```sh
make test CLASS=TypeTest
make test CLASS=WorklistTest "TEST_JAVA_OPTS=-XX:ActiveProcessorCount=2 -Dsimple.assert.expensive=1"
```

The [grammar and parser map](docs/33-grammar.md) describes this snapshot.
The [examples](docs/examples/README.md) include launch commands for the games.
Library references live with [drawing](src/main/smp/sys/draw/README.md),
[window/input](src/main/smp/sys/gfx.md), [audio](src/main/smp/sys/audio.md), and
[time](src/main/smp/sys/time/README.md). Future projects are in the
[roadmap](../docs/roadmap.md).
