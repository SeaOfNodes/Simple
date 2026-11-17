# Chapter 32: Compiler Internal Speedups

English | [日本語](README.ja.md)

[Previous: Chapter 31 — Software Drawing, Time, and Graphical Games](../chapter31/README.md)

The games and libraries from chapter 31 made both compiling and testing slow
enough to interrupt development. This chapter makes room for future work:
faster feedback for Simple's developers, and faster compilation for its users.

| What improved | Result |
| --- | --- |
| Repeated compilation | 29–79% less compile time for the large workloads below |
| Development testing | Roughly 30 seconds for a full run → 8.8 seconds for sanity checks; the full suite now takes 22.8 seconds |
| IR stored in ELF files | 21.9% smaller across 26 objects |

The compiler figures isolate the allocator-scratch fix in repeated compiles.
The testing gain includes a deliberate coverage tradeoff. The sections below
explain what changed and what each measurement covers.

## Faster development of Simple

### Measure the work and the wait

A test run starts JVMs, compiles Java methods, runs the compiler, checks
assertions, links native programs, and executes them. In one serial profiling
run, compiler phases took 29.7 seconds and 153 GCC calls took another 29.8,
mostly linking. Compiler optimization could address only part of the cost.

Parallel testing has the "tallest pole" problem: we wait for the last worker.
The [Makefile](Makefile) now splits the former catch-all JVM into balanced
groups, with private native outputs. Each JVM sizes its JIT and GC pools for
two processors. Eight Make jobs beat both four and sixteen on this host.

| Command | Coverage | Warm elapsed time |
| --- | --- | ---: |
| `make -j8 sanity` | Full lattice, chapter/backend tests, saved fuzzer regressions, focused compiler tests | 8.8 s |
| `make -j8 tests` | All of the above, plus remaining library, application, and CLI integration | 22.8 s |

These runs use Windows, Java 21, and a 10-core/20-thread i9-10900K, with builds
already up to date. They execute 680 and 819 tests respectively, including the
lattice partitions. Assertions stay enabled. Use the full suite before finishing
a change, especially when working on libraries or interfaces omitted by `sanity`.

Routine savings came from cheaper fixtures: loop over the division/remainder
matrix instead of generating 200 conditional checks; share native programs
between formatting cases; compile two small modules for an assembly-dump test
instead of all of `sys`. The full suite's GCC calls fell from 153 to 141.

### Keep the full lattice, with fewer repetitions

The [lattice test](src/test/java/com/seaofnodes/simple/TypeTest.java) stays in
the sanity run. Compiler transformations depend on its laws, so we reduced
repetition rather than sampling fewer types.

First, format assertion messages only on failure. Doing that inside the cubic
loop reduced the standalone run from 10.6 to 4.5 seconds. Next, cache pairwise
meets and joins, computing both operand orders before checking commutativity.

Associativity formerly visited all `n^3` ordered triples. Now it visits
`i <= j <= k`, including repeated operands: `n(n+1)(n+2)/6` iterations. Each
visit compares all six results:

```
(a & b) & c     c & (a & b)
(a & c) & b     b & (a & c)
(b & c) & a     a & (b & c)
```

Once the original pairs commute, the six original associativity equalities
connect these results in a cycle. Comparing them to one common result checks
the same property. We must retain both operand orders involving intermediate
types: `a & b` may be outside the gathered set, so its commutativity has not
yet been established.

All comparable-pair monotonicity checks remain. The complete unpartitioned
TypeTest now takes about 0.7 seconds. Make still partitions it across four
JVMs, each with private type-interning state.

## Faster compilation for Simple users

### Wake nodes when something relevant changes

`listener.addDep(source)` asks to wake a node when the source changes.
Registering extra dependencies is easy; paying for them happens later.

Tracing enqueues exposed two broad notifications. Calls and summaries read
`Start.outside` even when their arguments did not need it, subscribing them to
unrelated changes. They now read it only for opaque arguments. Removing a
constant Phi input also woke every user of that constant throughout the
program. It now queues the changed Phi.

These filters cut worklist visits from 4.46 million to 3.70 million, or 17.2%.
Time inside `iterate()` fell from 14.24 to 11.97 seconds. Further MemPhi and
IfNode filters reduced visits slightly without a measurable timing gain.
Fewer visits alone are not proof of a faster compiler.

Broad notifications can hide missing dependencies. Stronger verification found
nodes that had relied on accidental wakeups, including a Phi whose guard-merge
rewrite became possible after an If predicate changed. Checking every progress
step isolated the peephole that exposed the missing dependency.

Normal runs sample expensive checks every 1,024 eligible steps, retaining entry
and completed-phase checks. Use `-Dsimple.assert.expensive=1` to check every
progress step while diagnosing a failure. Ordinary assertions remain enabled.

### Give scratch data the lifetime of the work

[RegAlloc](src/main/java/com/seaofnodes/simple/codegen/RegAlloc.java) already
allocated one function at a time, but its interference-graph helper retained
basic-block keys from every previous allocation. Each `sys` compile added
another 2,558 keys to clear on later runs.

Each function now owns its [IFG](src/main/java/com/seaofnodes/simple/codegen/IFG.java)
scratch maps. They survive spill retries, then become unreachable when allocation
finishes. Spill scoring also computes the function's area once per coloring
attempt instead of walking the blocks for each candidate.

| Program | Before | After |
| --- | ---: | ---: |
| `sys` library | 1,290 ms | 913 ms |
| Clock | 537 ms | 279 ms |
| Breakout | 1,007 ms | 609 ms |
| Graphical tic-tac-toe | 1,362 ms | 292 ms |

These medians isolate that change: six measured compiles after three warmups,
alternating baseline and changed compilers, with the same library and assertions
enabled. They include object export but exclude linking. The old compiler
accumulated keys during warmup too; this measures repeated compilation, not
fresh command-line startup. Machine-code bytes matched in all 26 objects checked.

### Remove empty space and unnecessary allocation

[MemMergeNode](src/main/java/com/seaofnodes/simple/node/MemMergeNode.java)
used alias numbers as input indices, leaving mostly null slots supplied by
the default memory value. A dense list plus an alias-to-input map cut `sys`'s
live MemMerge input slots from 54,388 to 2,462. That change alone reduced its
compile time by 5.8%, while preserving folding behavior and generated code.

Other savings were straightforward: remove recursive scratch-state setup from
the shallow `isConstant` and `isFinal` queries; use node-ID BitSets for load-fold
visits; stop collecting a packed-value histogram left over from profiling.
The latter two changes reduced `sys` compiler-thread allocation from 119.6 to
69.4 MiB and median compile time from 1,017 to 942 ms. These are separate
comparisons, not cumulative chapter-wide timings.

### Make saved compiler state smaller

ELF's `.simple` section stores reloadable IR, declarations, and import metadata.
It occupied 79% of the measured `sys` objects. Three encoding changes removed
avoidable overhead: gradual growth for packed integers, explicit MemMerge
alias/input pairs instead of default holes, and a wider compact encoding for
singleton function IDs. Strings retain raw UTF-8 bytes.

| `.simple` payload | Before | After | Reduction |
| --- | ---: | ---: | ---: |
| `sys`, all 22 modules | 469,881 bytes | 368,694 bytes | 21.5% |
| Clock | 43,854 bytes | 31,906 bytes | 27.2% |
| Breakout | 80,950 bytes | 62,139 bytes | 23.2% |
| Graphical tic-tac-toe | 44,353 bytes | 35,961 bytes | 18.9% |
| HelloWorld | 14,635 bytes | 11,523 bytes | 21.3% |

Across these 26 objects, the payload shrank by 143,450 bytes, or 21.9%, with
identical machine code. Compile timings showed no consistent improvement;
the demonstrated gain is smaller saved compiler state.

## Keep the generated code good

Changing worklist order can change rewrites and register allocation. Some
broader notification filters increased register copies, so we did not keep
them. Judge a compiler speedup by compile time, memory use, and generated code
together.

Correctness fixes uncovered here belong in their teaching chapters:
[Phi factoring after strength reduction in chapter 5](../chapter05/README.md#factoring-after-strength-reduction),
and shared class receivers in [chapter 28a](../chapter28a/README.md#globals-use-the-same-receiver-as-fields)
and [chapter 28b](../chapter28b/README.md).

## Build and use this chapter

From `chapter32`, populate the dependency cache once with `make libs` if needed.
Ordinary builds use that cache and do not download dependencies.

```sh
make -j8 release
make -j8 sanity
make -j8 tests
```

For a fully headless full run, use `make -j8 tests_nographics`. It disables the
optional MiniFB and miniaudio backends. The packed IR header changes from `C0DE`
to `C0D2`; the reader rejects the older format. Run `make clean` before
`make -j8 release` to rebuild old objects, and rebuild any separately maintained
Simple libraries too.

Run one class, or enable every-step verification for a focused investigation:

```sh
make test CLASS=TypeTest
make test CLASS=WorklistTest "TEST_JAVA_OPTS=-XX:ActiveProcessorCount=2 -Dsimple.assert.expensive=1"
```

The [grammar and parser map](docs/32-grammar.md) describes this snapshot.
[Chapter 31](../chapter31/README.md) teaches its language and library additions;
the [examples](docs/examples/README.md) include launch commands for the games.
Library references live with [drawing](src/main/smp/sys/draw/README.md),
[window/input](src/main/smp/sys/gfx.md), [audio](src/main/smp/sys/audio.md), and
[time](src/main/smp/sys/time/README.md). Further projects belong in the
[roadmap](../docs/roadmap.md).

Use the fast checks while editing. Run the full suite before finishing, and
turn up verification when a failure needs a closer look.
