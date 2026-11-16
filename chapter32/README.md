# Chapter 32: Compiler Internal Speedups

English | [日本語](README.ja.md)

[Previous: Chapter 31 — Software Drawing, Time, and Graphical Games](../chapter31/README.md)

Chapter 31 completes the graphics, games, and library work.  This chapter keeps
that language and those programs, then investigates the cost of compiling and
testing them.  Larger programs have made repeated graph walks, unnecessary
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

## Reduce test overhead before reducing coverage

The November 16 follow-up uses `make -j8 tests` as the normal run. On the same
Windows/JDK 21 host, warm runs took 25.4–26.0 seconds; four workers took 30.5
seconds and sixteen took 33.1. More concurrent JVMs did not help. Keep the
two-processor JVM sizing and distinguish warm testing from rebuilding the
compiler and released library.

The serial attribution run spent 29.8 seconds in 153 GCC calls, mostly linking,
and 29.7 seconds in compiler phases. These are summed serial costs, not portions
of the parallel elapsed time. Four changes reduce work while retaining the
existing test methods and their value checks:

* [TypeTest](src/test/java/com/seaofnodes/simple/TypeTest.java) constructs lattice
  assertion messages only when the identity comparison fails. Formatting types
  inside every successful triple was more expensive than the comparison. The
  standalone lattice fell from 10.6 to 4.5 seconds, with all pairs and triples
  still checked.
* [ModTest](src/test/java/com/seaofnodes/simple/ModTest.java) puts its native
  division/remainder matrix into constant arrays and loops. All 200 checks,
  including zero divisors and signed overflow, remain. This avoids compiling a
  long chain of calls and conditional returns; the isolated test fell from
  2.75 to 0.62 seconds.
* [StringFormatTest](src/test/java/com/seaofnodes/simple/StringFormatTest.java)
  shares three small native programs between compatible cases. Each original
  test checks its own output, including embedded NULs and UTF-8 bytes. Separate
  import, forward-reference, evaluator, and error tests remain. The class now
  makes seven GCC calls instead of nineteen.
* [AssemblyDumpTest](src/test/java/com/seaofnodes/simple/AssemblyDumpTest.java)
  compiles two small modules containing the real Canvas font declaration. It
  still checks unit headings, all 96 glyphs, exported data aliases, and repeated
  dumps without changing the active encoding. Drawing integration remains in
  DrawTest; inspecting assembly no longer requires recompiling all of `sys`.

The Make groups redistribute the now-cheaper formatting and assembly work and
the tests accumulated in the fallback group. Remaining chapter cohorts start
before the short lattice partitions so their native links can overlap earlier
work. Rebalanced warm runs took 22.1 and 24.3 seconds and passed the same 819
executions, including the three additional lattice partitions. Their mean is
about 10% below the earlier baseline; individual parallel runs vary. Compiler
verification remains enabled.

## Keep the lattice in the fast checks

`make -j8 sanity` retains the entire lattice, chapters 1–24's existing test
cohorts, saved fuzzer regression seeds, and the focused compiler tests. Its
three compiler groups have private native-output directories just like the
full suite. New compiler test classes enter the last sanity group automatically.
Warm runs on the same host took 8.77 and 8.85 seconds and passed 680 executions.
`make -j8 tests` still runs all 819 executions.

The fast set leaves graphics, audio, time, math-library, formatting, game, terminal, stream,
runtime-argument, CLI-diagnostic, and native-harness integration in the full
suite, along with Chapter25Test and the larger BrainFuck/MergeSort programs.
It retains native execution for arithmetic, memory encoding, static data,
receivers, separate compilation, incremental rebuilds, and the chapter backend
tests. This selects a cheaper set of checks; it does not replace native
execution with evaluator-only coverage. Full tests remain necessary when
changing the omitted libraries, interfaces, or programs.

The lattice itself now does less redundant work. It caches each pair's meet
and join, computing both operand orders before checking commutativity. For
associativity it visits `i <= j <= k`: `n(n+1)(n+2)/6` triples instead of `n^3`.
For each triple it checks all six intermediate results:

```
(a & b) & c     c & (a & b)
(a & c) & b     b & (a & c)
(b & c) & a     a & (b & c)
```

Once the original pairs commute, the six original associativity equalities
connect these results in a cycle. Comparing all six to one common result
checks the same property. Both operand orders involving an intermediate meet
remain explicit: that intermediate type need not belong to the gathered set.
Repeated operands are included, and all comparable-pair monotonicity checks
remain. No random sampling or smaller type collection is used.

The unpartitioned TypeTest now takes about 0.7 seconds, following the earlier
reduction to roughly four seconds from lazy assertion messages. The existing
four process partitions remain; in the warm sanity run each took 0.3–0.4
seconds. Reducing duplicate operations made additional JVMs unnecessary.

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

### MemPhi and IfNode follow-up

The next trace records the notifying node as well as the enqueue stack.
BulkMemPhi splitting and MemMerge cleanup repeatedly queue precise memory Phis
as inputs of successful peepholes. Removing memory Phis also changes a Region's
users, waking IfNodes which subscribed while walking the dominator chain.
Unlinking callers produces similar notifications from CallNodes.

Two small filters are retained. A type-only update of a user no longer queues
an already-used, stable MemPhi input. New or edited inputs and first-use Phis
remain eligible; replacement and edge-edit notifications retain their existing
behavior. `IfNode.idealize` searches dominators only when both branches remain
possible. Known branches already fold through CProj and need no new distant
subscriptions.

On the same 794-method profiling sweep, MemPhi visits fell from 474,510 to
469,866, IfNode visits from 254,787 to 249,609, and total visits from 3,695,609
to 3,661,560. The total reduction is 0.9%; whole-suite elapsed times of 91.88
and 91.64 seconds do not establish a meaningful timing improvement. Existing
spill expectations still pass. One graph expectation loses a redundant integer
narrowing; both branches also have execution checks.

A broader experiment separated dependency use-count notifications from type
and input-edge notifications, and filtered more MemPhi use-count revisits.
That version changed optimizer ordering enough to increase the Sieve spill
score from 216 to 343 and the forward-field example from 23 to 31. It is not
enabled: reducing compiler work is not a reason to silently raise code-quality
expectations. The broader filter also requires notifying a node's dependents
when `delDef` changes its inputs, rather than redundantly notifying the old
input's use-count listeners.

In the 302-method non-chapter group, that isolated experiment reduced MemPhi
visits from 367,387 to 205,245 and IfNode visits from 144,652 to 59,185. Total
visits fell 9.7%, but elapsed time was essentially unchanged at 57.48 versus
57.75 seconds. Fewer visits alone do not demonstrate a runtime improvement.

These measurements precede the verifier fixes below.

### Every-step verifier fixes

Checking every progress step exposed two bugs, both reproducible before the
MemPhi/IfNode filters:

- `Chapter05Test.testMerge4` fails at iteration 38: replacing `Phi#44` with
  `arg` changes `If#52`'s predicate and enables `Phi#75`'s guard-merge rewrite.
  `matchingGuardMerge` had no dependency on that predicate comparison. It now
  subscribes to the If for direct input edits and to its predicate for replacement
  through `subsume`. Tests cover both notification paths.
- `Chapter15Test.testArrayLoadDominatingControl` fails at iteration 30, when a
  bulk memory Phi splits into a MemMerge. The verifier treated the consuming
  load's earlier range-check control as its execution block. A memory operation's
  control is only a lower bound: its data inputs can force it later. The check
  now includes those inputs, matching early scheduling. Regression tests accept
  this legal placement and still reject a load used before its memory is ready.

After these fixes, all 802 suite executions pass at log 8; 444 chapter tests and
34 focused worklist, scheduling, and memory tests pass at log 1. Existing spill
expectations are unchanged.

### Dense MemMerge inputs

`MemMergeNode` now keeps precise alias slices in a dense input list, with an
`IntHashMap` from alias to input index and a reverse array for iteration.
Alias order is preserved. Missing aliases use input 1, the bulk default.
Deletion compacts the list and rebuilds the small map; graph copies own their
maps. Bulk-Phi splitting and object loading use alias lookup rather than
assuming an alias is an input index. The existing sparse object encoding remains
compatible with the baseline compiler.

This is deliberately a storage experiment. `_aliasLimit` retains the previous
sparse extent for encoding and fold eligibility. The old `allDefault` test
counts null holes as non-defaults; simply folding whenever the dense list is
empty introduces additional optimizations. That separate trial altered code
quality: graphical Tic-tac-toe's weighted spill score rose from 1085 to 1240
with its rebuilt library. The retained version preserves the old folding
behavior and matches baseline peephole counts, code sizes, and spill counts on
all measured workloads.

Against commit `8b80d94c`, the final interleaved comparison measured:

| Workload | Compile time change | Compiler-thread allocation change |
| --- | ---: | ---: |
| sys library | -5.8% | -3.7% |
| Clock | -2.1% | -10.8% |
| Breakout | -2.9% | -6.4% |
| Graphical tic-tac-toe | -0.5% | -5.3% |
| HelloWorld | -0.6% | -4.5% |

The timing improvement is modest; sub-percent differences are within noise.
The structural reduction is much larger. After Lower, sys's live MemMerge input
slots fall from 54,388 to 2,462, Clock's from 54,255 to 2,237, and Breakout's from
61,286 to 2,592. These are input-slot counts, not total heap measurements; the
maps and reverse arrays have their own cost.

Measurements use Java 21, assertions at log10, two active processors, and a
256 MiB initial / 2 GiB maximum heap. The compiler runs through object export,
without gcc, linking, or execution. Isolated compiler class loaders alternate
in one JVM; six measured rounds follow three warmups for each workload. Games
read the same baseline sys objects. Time changes are medians of paired ratios;
allocation uses ThreadMXBean. Earlier separate-process runs drifted enough that
the interleaved comparison is the basis for the table.

The full suite passes at log8; 444 chapter tests and 18 memory/import tests pass
with every-step verification. Raw measurements, saved compiler classes, and
reproduction commands are in `build/memmerge-dense/`; verification logs are in
`build/test-profile/dense-final-*.log`.

## Match interference scratch lifetime to function allocation

[RegAlloc](src/main/java/com/seaofnodes/simple/codegen/RegAlloc.java) already
allocates one function at a time, retrying after spills before moving to the
next function. But [IFG](src/main/java/com/seaofnodes/simple/codegen/IFG.java)'s
static `BBOUTS` retained basic-block keys from every previous allocation, even
across compilations. Each interference build cleared all their live-out maps.
Repeated sys compiles added 2,558 keys apiece, making later compiles slower.

Each function now creates an IFG instance. Its live-out maps, temporary live
set, and worklist survive that function's spill retries and become unreachable
when allocation returns. The shared interference bitsets still reuse capacity:
they contain live-range numbers, with no references to compiler graphs.

Risky spill scoring also repeatedly walked the function's scheduled blocks to
compute the same area. Coloring now computes that area once, after coalescing,
and passes the integer to the scoring routines. A later spill retry recomputes
it because splitting can change the schedule. An assertion checks that the
live ranges being colored belong to the current function.

Compared with `b8bcd9c0`, median compile times through object export were:

| Workload | Before | After | RegAlloc before / after |
| --- | ---: | ---: | ---: |
| sys library | 1,290 ms | 913 ms | 494 / 91 ms |
| Clock | 537 ms | 279 ms | 274 / 27 ms |
| Breakout | 1,007 ms | 609 ms | 428 / 51 ms |
| Graphical tic-tac-toe | 1,362 ms | 292 ms | 1,113 / 40 ms |

These are repeated compilations: six measured rounds after three warmups,
alternating isolated baseline and changed compiler class loaders. Each workload
starts with fresh loaders. The old implementation accumulates block keys even
during warmup, so these gains depend on compilation history; they are not
single-shot CLI startup measurements. Java 21 uses two active processors,
assertions at log10, and a 256 MiB initial / 2 GiB maximum heap. Both compilers
read the same sys objects; gcc, linking, and native execution are excluded.
HelloWorld remains about 69 ms, within measurement noise.

Peephole counts, code sizes, and spill counts match in every benchmark pair.
The final 26 objects, including sys submodules, have identical `.text` bytes.
The full non-graphical suite passes all 805 test executions at log8, including
the native allocator checks. Benchmark sources, saved baseline classes, raw
timings, and follow-up JFR recordings are in `build/ifg-profile/`; the suite log
is `build/test-profile/ifg-full.log`.

## Reduce allocation in load folding and packed writes

The x86 [load-fold legality check](src/main/java/com/seaofnodes/simple/node/cpus/x86_64_v2/x86_64_v2.java)
walks the other operand's inputs to ensure that combining it with a load cannot
move the read across a conflicting write. Its visited set now uses node IDs in
a local BitSet. The traversal, including control dependencies, is unchanged;
the check avoids growing arrays of identity-map keys and values.

[BAOS](src/main/java/com/seaofnodes/simple/util/BAOS.java)'s packed-value
histogram was exploratory instrumentation for choosing smaller encodings.
Its map and updates are commented out, ready to restore for another packing
study. Normal compilation no longer boxes histogram keys and counts. Packed
bytes and their readers are unchanged.

Against `6643e8fd`, an interleaved comparison measured sys compiler-thread
allocation falling from 119.6 to 69.4 MiB and median compile time from 1,017 to
942 ms. Breakout fell from 54.5 to 43.6 MiB and 652 to 617 ms. The smaller timing
differences were mixed: Clock measured 315 to 318 ms, graphical tic-tac-toe
292 to 291 ms, and HelloWorld 65 to 64 ms. All five allocate less. These use the
same three-warmup, six-measurement method and JVM settings as the IFG comparison.
All 26 resulting objects have byte-identical `.text` and `.simple` sections.
The Windows suite at log8 and the Linux suite at the default log10 each pass
all 805 test executions, including native scheduling and import coverage.

## Measure serialized IR size before changing its packing

The ELF `.simple` section stores reloadable ideal IR, declarations, and import
metadata. It can be much larger than the native code. For the same x86-64/win64
objects, including all 22 sys modules:

| Workload | Whole ELF bytes | `.simple` bytes | Share of ELF |
| --- | ---: | ---: | ---: |
| sys library, all modules | 597,399 | 469,881 | 78.7% |
| Clock | 64,317 | 43,854 | 68.2% |
| Breakout | 118,163 | 80,950 | 68.5% |
| Graphical tic-tac-toe | 64,605 | 44,353 | 68.7% |
| HelloWorld | 16,355 | 14,635 | 89.5% |

Temporary byte-offset counters around [Serialize](src/main/java/com/seaofnodes/simple/codegen/Serialize.java)'s
existing write stages divide sys's payload into 67,844 bytes of strings and
unit metadata, 73,564 of identity mappings, 180,456 of types, 2,447 of function
summaries, and 145,482 of node records and input references, plus 88 header
bytes. HelloWorld's strings, mappings, and types alone take 14,161 of its 14,635
bytes; its graph records and inputs take only 447 bytes.

The broad metadata footprint warrants a separate investigation. The current
writer emits the full compilation's alias, function, and
return-PC mappings into each object, along with their names. Restricting them
to identities needed by that object's declarations, summaries, and graph may
save space, but requires coordinated remapping in the writer and reader.
Those measurements made no packing or format changes. Profiling helpers,
saved baseline classes, per-object sizes, byte breakdowns, and timings are in
`build/packing-profile/`.

### Which packed fields have avoidable overhead?

A deeper byte-count pass on `c21026c5` found three localized candidates:

| Candidate | Modeled saving across sys | Clock | Breakout |
| --- | ---: | ---: | ---: |
| Unsigned varints for type-child IDs | 40,137 bytes | 2,757 | 3,561 |
| Sparse serialized MemMerge inputs | 34,280 bytes | 7,133 | 12,683 |
| Extend singleton function-ID encoding beyond ID 63 | 23,441 bytes | 1,140 | 1,409 |

At that baseline, type-child IDs used `packed4`: IDs 128 through 16,383 took five bytes
where an unsigned varint would take two. [MemMerge](src/main/java/com/seaofnodes/simple/node/MemMergeNode.java)
was dense in memory, but its serialized representation retained the old alias
slot space; sys's MemMerge input data contained 36,138 zero bytes for default
slots. The sparse model keeps the first two inputs and encodes a non-default
count plus `(alias, node ID)` pairs, including the extra count/pair cost.
[TypeFunPtr](src/main/java/com/seaofnodes/simple/type/TypeFunPtr.java) had a
compact singleton form for non-null functions with fewer than six arguments,
but only below function ID 64. Larger singleton IDs became mostly-zero bitsets.

These estimates concern disjoint fields and together amount to about 21% of
sys's `.simple` payload, 25% of Clock's, and 22% of Breakout's. The implementation
and actual measurements follow below. A generic signed-varint substitution is not equivalent:
zigzag encoding would make ordinary ASCII characters 64 through 127 larger.

The metadata scan also explains HelloWorld's size. One constant pointing to
`class:sys` reaches 672 of its 700 serialized types, including unrelated
namespace members. Consequently, 363 of its 364 alias-map rows are referenced
by the current type representation. Dropping those rows alone is insufficient;
imported-type references or more selective namespace types need investigation.
Return-PC tables are a clearer candidate: only 214 of 7,788 RPC rows written
across sys objects are referenced by the serialized types, nodes, and summaries.

Adding a general XInt dictionary is less promising: storing unique existing
encodings plus references is already slightly larger across sys, before table
headers. The singleton encoding addresses the common case more directly.
Detailed field counts, size models, and reproduction helpers are in
`build/simple-size/`. The instrumented exports preserve `.text` and `.simple`
bytes in all 27 measured objects; that investigation changed no format.

## Pack the serialized IR more tightly

[BAOS.packed4](src/main/java/com/seaofnodes/simple/util/BAOS.java) now uses
signed zigzag varints: -64 through 63 fit in one byte, -8192 through 8191 in
two, and the full signed int range takes at most five bytes. Each byte carries
seven payload bits and a continuation bit. Strings use a packed byte length
followed by raw UTF-8, keeping ordinary ASCII at one byte per character.

MemMerge writes its control/default inputs and explicit `(alias, node ID)`
pairs, omitting default holes. It retains the old alias extent separately
because that value still affects folding eligibility. The reader remaps each
explicit alias into the importing compilation. Non-null singleton function
types with fewer than six arguments now use the compact form through function
ID 65535, with the matching `packed2` reader.

Actual `.simple` sizes against `c21026c5`, including all 22 sys objects:

| Workload | Before, bytes | After, bytes | Reduction |
| --- | ---: | ---: | ---: |
| sys library | 469,881 | 368,694 | 21.5% |
| Clock | 43,854 | 31,906 | 27.2% |
| Breakout | 80,950 | 62,139 | 23.2% |
| Graphical tic-tac-toe | 44,353 | 35,961 | 18.9% |
| HelloWorld | 14,635 | 11,523 | 21.3% |

Across these 26 objects, the payload falls from 653,673 to 510,223 bytes,
saving 143,450 bytes (21.9%). Sys's type records and child IDs shrink from
180,456 to 116,948 bytes; node inputs shrink from 85,189 to 50,635. Together
those two chunks account for 96.9% of sys's saving. Identity mappings remain
73,564 bytes, leaving the metadata questions above for another pass.

Compile timings are mixed. Baseline/new median milliseconds were sys
1,056/1,065, Clock 335/352, Breakout 689/674, graphical tic-tac-toe 282/283,
and HelloWorld 69/68. There is no consistent speed win in this run. The
comparison alternates two isolated compiler class loaders, with three warmups
and six measured rounds per workload, Java 21, two active processors, log10
assertions, and a 256 MiB initial / 2 GiB maximum heap. Each compiler reads
its own freshly generated sys objects. Linking and execution are excluded.

Every pair has matching peephole/no-progress counts, code sizes, and spill
metrics. All 26 `.text` sections are byte-identical. New tests cover signed
integer boundaries and overflow, Unicode strings, singleton function IDs,
sparse memory imports beyond alias 255, and rejection of the old format.
The Windows suite at log8 and Linux suite at log10 each pass 811 test executions.
Raw measurements, byte breakdowns, helpers, and saved baseline classes are in
`build/format-profile/`.

The object header changes from `C0DE` to `C0D2`. Existing `.simple` objects
must be rebuilt; an old header produces an explicit rebuild error. For an
existing chapter32 build, `make clean` followed by `make -j 8 release` rebuilds
the generated artifacts. Rebuild any separately maintained Simple libraries too.

## Receiver backport and spill investigation

The shared class receiver is taught in
[chapter 28a](../chapter28a/README.md#globals-use-the-same-receiver-as-fields),
with imported summaries in [chapter 28b](../chapter28b/README.md).
Those fixes are carried through the later snapshots. Phi factoring after
strength reduction starts in
[chapter 5](../chapter05/README.md#factoring-after-strength-reduction), using
multiply-by-two there and shift-by-one where that representation is introduced.

The receiver investigation also exposed a missed notification in
[RegionNode](src/main/java/com/seaofnodes/simple/node/RegionNode.java): flattening
regions adds Phi inputs, so their definitions must be queued too. A newly added
precise memory user can require its bulk-memory input to split immediately.
That prerequisite accompanies the receiver backport.

The following measurements describe the chapter32 investigation before backporting.

Validation: all 817 test executions pass on Windows with log8 verification and
Linux with the default log10. A focused 62-test run passes with log1, checking
every progress step. Native callbacks, imported receivers, FileIO, and ARM/RISC-V
execution remain covered.

There is a compilation/code-size cost to review. The following Windows comparison
uses the preceding uncommitted assignment fix as its baseline, assertions enabled,
two JVM processors, seed 456, and each compiler's freshly built libraries. Times
are medians of four warm rounds after discarding the first round; this is a small
local sample, not a timing guarantee. Sys sizes sum its 22 object files.

| Compile | Time, ms (before → after) | Peepholes (before → after) | `.text` bytes (before → after) |
| --- | ---: | ---: | ---: |
| sys | 961 → 977 | 185,960 → 208,552 | 37,725 → 40,355 |
| Clock | 256 → 381 | 86,873 → 106,615 | 6,717 → 7,053 |
| Breakout | 553 → 593 | 158,712 → 174,500 | 14,868 → 15,031 |
| TicTacToe | 161 → 177 | 56,222 → 63,774 | 4,251 → 4,411 |

The small String and HelloWorld examples also retain additional initialization
checks, and their spill expectations have been updated. The receiver-only change
exposed an existing ordering sensitivity in Sieve: across seeds 120–127, both
compilers produced seven 28-split/343-weight results and one 26-split/215-weight
result, with the smaller result moving from seed 126 to 121.

The spill investigation separates compile time from generated moves: Clock's
weighted split score improves from 785 to 762. These scores count all retained
SplitNodes, including register copies, with weight `8^loopDepth`; they are not
counts of stack spills. Sieve's 215-to-343 change is two extra register copies
inside its depth-two loop. Early `p+p` strength reduction in
[AddNode](src/main/java/com/seaofnodes/simple/node/AddNode.java) turns the entry
value into `p<<1` before [PhiNode](src/main/java/com/seaofnodes/simple/node/PhiNode.java)
can factor `Phi(p+p,i+p)` into `Phi(p,i)+p`.

Phi factoring now views `x<<1` as `x+x` when matching mixed Add/Shl arms. It uses
the existing factoring checks and trial graph, without expanding a shift into
an Add that would immediately strength-reduce again. Pure shift arms retain
ordinary shift factoring. Multiply-by-two already reduces to this shift, so it
is covered too. This is a local canonicalization: peeling one `x` from `k*x` for
arbitrary k would require a new `(k-1)*x` expression, with less clear cost and
rewrite-order benefits. The implementation stops at doubling.

All eight Sieve seeds now produce 26 splits and score 215, with every-step
verification; the spill expectation is 215. Direct graph tests check factoring
before and after strength reduction, either arm order, and rejection of a
different shift count. The 819-test Windows sweep passes with log8 verification
(the Sieve group rerun after updating its expectation), and 64 focused tests
pass with log1. Native and emulator Sieve results are checked too.

The largest individual sys increase in the receiver-only comparison was
`Canvas._circle`, from 455 to 517.
Allocation keeps the loop-invariant boolean in a general register and the pixel
pointer in an XMM register; recovering the pointer adds a transfer at each pixel
store. The baseline makes the opposite choice. Both ranges receive the default
score 1000 in [IFG.pickRiskyScore](src/main/java/com/seaofnodes/simple/codegen/IFG.java),
leaving their selection sensitive to ordering. An isolated trial giving SetX86
candidates one extra priority point brings `_circle` to 454 and total sys to
8869, versus baseline 8719 and current 8932. Other sys functions' weighted scores
are unchanged by that trial. This identifies an allocator sensitivity; the
opcode-specific preference is diagnostic, not an adopted heuristic.

With Phi factoring fixed, sys's weighted score is 8850 and `_circle` returns to
455 through another ordering change. Clock stays at 762, Breakout changes from
3387 to 3393, and TicTacToe stays at 1468. A bounded allocator follow-up tried
three cheap refinements of the default score: breaking ties toward fewer excess
neighbors (degree minus available colors), toward more excess neighbors, and a
bonus for cheap single-def/single-use splits based on loop depth. Sys scored
9039, 8844, and 8850 respectively; the three games were unchanged. The tiny
six-point gain does not justify adopting the degree tie-break on this evidence.
The production allocator remains unchanged; no additional graph walks or
searches were added.

Backporting retains each chapter's allocator. Code-quality expectations are
therefore checked locally: chapter21's RISC-V Sieve score changes from 92 to
164, despite the chapter32 improvement above, and chapter31's forward-field
scanner test changes from 23 to 31 on x86-64/Win64. Execution checks remain in
place; these earlier snapshots do not inherit the later allocator experiments.
Chapter31 also groups `TimeTest` with its sys tests: its wide-library compile
passes in a fresh JVM, but following the large catch-all suite exhausted that
snapshot's existing 16-bit type IDs. This keeps the checks and the older type
implementation intact; it does not remove the compiler's type-ID limit.

The saved baseline, comparison drivers, measurements, and verification logs are
under `build/receiver-profile/`. Proving initialization checks redundant is the
main follow-up suggested by these measurements.

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
