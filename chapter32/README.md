# Chapter 32: Compiler Internal Speedups

English | [日本語](README.ja.md)

[Previous: Chapter 31 — Software Drawing, Time, and Graphical Games](../chapter31/README.md)

Chapter 31 completes the graphics, games, and library work. This chapter keeps
that language and those programs, then investigates the cost of compiling and
testing them. Larger programs have made repeated graph walks, unnecessary
worklist visits, and uneven test parallelism visible in the test runtime.

The starting changes address four costs: test JVM scheduling, scratch-state
cleanup in type queries, expensive verification, and overly broad optimizer
notifications. Each change keeps the existing native and evaluator coverage.

## Measure the work before changing it

A test suite includes Java startup, JIT compilation, assertions, compiler
passes, native compiler/linker subprocesses, and execution of generated code.
Timing the suite alone does not identify which part is growing. Likewise,
adding the timings of parallel JVMs does not give elapsed time.

The investigation used JFR and temporary counters around compiler phases,
`_iter.iterate()`, expensive checks, and native subprocesses. Counters separate
worklist peepholes from the extra peepholes performed by verification. A
separate sample records the first accepted enqueue and follows it until the
node is popped, relating a null result to the code that scheduled the visit.
Duplicate pushes while a node is already queued are coalesced by the worklist.

Use identity maps for bookkeeping keyed by Nodes. `Node.hashCode()` participates
in GVN locking; an ordinary HashMap used by a profiler can change compiler state.
The investigation helpers stay outside the production compiler.

## Give independent tests independent JVMs

The older Makefile ran chapter cohorts in parallel, but put the remaining tests
into one JVM. That last process became the long tail of `make -j 8 tests`.
The [Makefile](Makefile) divides those tests into eight measured groups, keeping
each class and its fixtures together. New test classes join the last group
automatically, so the grouping cannot silently omit them.

Each JVM defaults to `-XX:ActiveProcessorCount=2`. This sizes its JIT and GC
thread pools for a share of the machine instead of letting every concurrent
JVM size itself for all cores. The limit does not pin the process to CPUs.

Native tests create objects and executables with common filenames.
[TestWorkspace](src/test/java/com/seaofnodes/simple/TestWorkspace.java) gives
each group a working directory under `build/tests`, refreshing its fixtures
and native libraries before the run. Classes and JARs are shared through an
absolute classpath. The groups can then run without overwriting each other's
generated programs.

The exhaustive [lattice test](src/test/java/com/seaofnodes/simple/TypeTest.java)
partitions its outer type loop across four JVMs. Together they check every
pair and triple. Separate processes also isolate the mutable type interning
and traversal state. A direct JUnit or IDE invocation still runs the complete
lattice test in one JVM; part zero of the Make run also runs the other type tests.

## Shallow type queries need no recursive scratch state

[Type.isConstant and Type.isFinal](src/main/java/com/seaofnodes/simple/type/Type.java)
used to wrap their queries in `recurOpen`/`recurClose`. That made sense when
constant detection could follow pointers into cyclic structures. The current
rules stop at the pointer: constantness tests its identity, and finality tests
its access qualifier. Struct queries inspect their fields without following
pointer referents.

These queries therefore do not need to clear the shared traversal map, mark
visited structs, or treat a repeated visit as success. Removing that bookkeeping
keeps the existing answers and avoids repeated scratch-state cleanup. Recursive
operations that actually traverse or construct cyclic types retain their own
recursion machinery. Type tests cover repeated fields, cyclic singleton
pointers, and finality qualifiers.

## Sample expensive verification

The optimizer checks a strong invariant: a live node is either on the worklist
or cannot make progress. Testing it walks the entire graph and tries peepholes
again. `schedulableUses` also walks the graph to check that inputs and uses admit
a legal schedule. Running such checks too often can dominate the transformation
being checked.

[CodeGen](src/main/java/com/seaofnodes/simple/codegen/CodeGen.java) now defaults
`simple.assert.expensive` to log 10 rather than 8, sampling one eligible step in
1,024. [IterPeeps](src/main/java/com/seaofnodes/simple/codegen/IterPeeps.java)
checks scheduling at the end of the completed fixed point instead of after
every intermediate inlining batch. Entry, periodic, and phase checks remain.
Ordinary assertions remain enabled in the test harness.

The verification-only change left the measured worklist visits identical at
4,463,457. Assertion-only peepholes fell from 5,295,983 to 1,910,132, and measured
checking time inside `iterate()` fell from 12.36 to 4.08 seconds.

## Wake nodes for changes they actually read

`listener.addDep(source)` puts the listener on the source's dependency list.
Later, `source.moveDepsToWorklist()` schedules those listeners. Notifications
can follow type changes, edge edits, or use-count changes. An unnecessary
subscription can therefore cause many visits even if the source's type never
changes.

### Outside state is needed only for opaque arguments

[SummaryNode](src/main/java/com/seaofnodes/simple/node/SummaryNode.java) and
[CallNode](src/main/java/com/seaofnodes/simple/node/CallNode.java) requested
`Start.outside` unconditionally while computing exposed escapes. That request
registers a dependency on Start. Deleting constants changes Start's users and
can wake every registered summary.

But `TypeMem.escapesFrom(argument, outside)` consults `outside` only for an
opaque argument. The callers now use that same condition before requesting
outside state. Dependencies on argument types remain: if an argument later
refines to an opaque type, it wakes the reader, which installs the needed Start
dependency. The return-state dependencies of external CallEnds remain intact.

### Removing a constant argument should not wake unrelated callers

[RegionNode.removeDeadPath](src/main/java/com/seaofnodes/simple/node/RegionNode.java)
used to schedule every user of each removed Phi input. During inlining, common
constants can be shared by parameters throughout the program. Removing one
argument caused a broadcast to many unrelated functions.

For a constant input, the code now schedules the changed Phi. Nonconstant
inputs retain the broader notification, and the Phi's outputs still receive
their notification.

Stronger verification exposed a dependency on the old broadcast:
`CallNode.unlink` removed parameter inputs without scheduling the edited Parms.
Those nodes need another visit even when their types stay unchanged, because
editing their inputs removes them from GVN. Unlink now queues the parameters it
actually edits. A focused regression checks this independently of later cloning.

### Results and remaining work

With verification held at the new setting, the two filters produced:

| Worklist peepholes | Before | After |
|---|---:|---:|
| SummaryNode | 455,186 | 50,623 |
| ParmNode | 700,827 | 373,604 |
| All nodes | 4,463,457 | 3,695,609 |
| Null returns | 4,140,886 | 3,371,682 |
| Null percentage | 92.8% | 91.2% |

That is 17.2% fewer live worklist visits. Measured `iterate()` wall time fell
from 14.24 to 11.97 seconds; the serial profiled suite fell from 98.53 to 93.37
seconds. These are individual Windows/JDK 21 runs with JFR and enqueue sampling,
not portable performance guarantees. The baseline had 792 test methods; the
final run included two additional regressions. The parallel uninstrumented
Make run took 28.8 seconds and passed 797 executions, including the additional
lattice partitions. The full suite also passed at log 8, with focused tests
at log 1. These measurements were taken before creating the chapter snapshot.

A null return does not always mean wasted work: `peepholeOpt` can reinsert an
unlocked node into GVN and still return null. Some null-returning nodes are then
killed as unused. Already-dead pops are counted separately from this table.

Dependency notifications accounted for only 28% of sampled baseline null-return
visits. Most came from direct scheduling. The largest remaining candidates are
MemPhi input requeues during Lower and IfNode dominator dependencies. The latter
also wake on use-count changes. Any narrower filter must preserve the topology
and use-count changes that actually enable a rewrite; deleting subscriptions
just because a sample showed no progress is not enough.

## Build and test

From `chapter32`, using the existing offline dependency caches:

```sh
make -j 8 release
make -j 8 tests_nographics
make test CLASS=TypeTest
```

On a fresh checkout, run `make libs` once while online to populate the caches.
The ordinary builds and tests do not download dependencies or open windows.

Override JVM sizing with `TEST_JAVA_OPTS`. For example:

```sh
make -j 4 tests_nographics TEST_JAVA_OPTS=-XX:ActiveProcessorCount=4
make test CLASS=EscapeFlowTest "TEST_JAVA_OPTS=-XX:ActiveProcessorCount=2 -Dsimple.assert.expensive=1"
```

Log 1 checks every progress step; log 8 restores the preceding sampling rate;
log 0 disables these expensive checks while retaining ordinary assertions.

## Language, libraries, and examples

The [grammar and parser map](docs/32-grammar.md) describes this snapshot.
Language and library additions are taught in [Chapter 31](../chapter31/README.md).
Their implementations and runnable examples are also present here:

- [Examples and launch commands](docs/examples/README.md), including Clock,
  Breakout, and graphical tic-tac-toe.
- [Drawing](src/main/smp/sys/draw/README.md), [window/input](src/main/smp/sys/gfx.md),
  [audio](src/main/smp/sys/audio.md), and [time](src/main/smp/sys/time/README.md).
- [Math and intrinsics](../chapter31/README.md#math-support-and-intrinsics).

Future compiler and language proposals remain in the [roadmap](../docs/roadmap.md).
