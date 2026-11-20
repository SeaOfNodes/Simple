# Chapter 33: Automatic Memory Management

English | [日本語](README.ja.md)

[Previous: Chapter 32 — Compiler Internal Speedups](../chapter32/README.md)

This chapter starts automatic memory management with an allocation-lifetime
analysis. It runs after Opto and field-layout finalization, before TypeCheck.
It reports where storage must live; allocation and machine code still use the
existing runtime. The [design survey](../docs/automatic-memory-management-survey.md)
records the language decisions. Chapter 32's
[speedup measurements](../chapter32/README.md) remain there.

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

Within one summary, a worklist propagates requirements through object fields.
For example, returning a Box retains its Thing too. This local closure does
not repeat the call-graph traversal. Stores are conservative weak updates:
overwriting a field does not yet release the earlier value's lifetime constraint.

The ASCII dump shows callees first, then their callers:

```text
+-- make_noInline
|   [result] new#352:Test.Thing (8 bytes)
|   [result] new#381:Test.Box (8 bytes)
|      .child --> new#352:Test.Thing
`-- return -> new#381:Test.Box

+-- forward_noInline
|   call#219 -> make_noInline
|   [result] call#219/new#352:Test.Thing (8 bytes)
|   [result] call#219/new#381:Test.Box (8 bytes)
|      .child --> call#219/new#352:Test.Thing
`-- return -> call#219/new#381:Test.Box
```

`frame` means scratch for that function; `result` means the caller's result
destination; `static` means program lifetime. `arg3` names storage associated
with argument input 3 (ordinary class functions use input 2 for their hidden
receiver). `arg3 & arg4` requires a destination satisfying both owners; it does
not arbitrarily pick one. Node numbers help correlate this output with the IR.
These are ownership constraints, not assigned physical stack offsets.

Run a poster child and print its plan:

```sh
make -j8 test CLASS=Chapter33Test METHOD=testAllocationFactoryGraph "TEST_JAVA_OPTS=-XX:ActiveProcessorCount=2 -Dsimple.alloc.dump=true"
```

The first cut rejects plans for loops, recursive calls, opaque/indirect calls,
variable-size allocations, raw address conversions, and reference origins that
it cannot trace, including pointer-valued field loads. A rejected function is
printed as `REJECT`, never as a usable partial plan. Scalar field reads and
writes do not themselves introduce retained references.

Normal compilation records those rejections and continues with the existing
allocator. `-Dsimple.alloc.check=true` rejects compilation if any function lacks
a supported plan; tests can also require an individual summary to be supported.
The pass does not yet insert hidden destination arguments, stack reservations,
or reclamation. The proposed inline-object and growing-array syntax is not
implemented by this analysis experiment.

[Allocation.java](src/main/java/com/seaofnodes/simple/codegen/Allocation.java)
contains the walk, one summary class, and one storage-site class. Comments mark
the next work: loop and SCC destinations, field-path origins, bin packing,
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
