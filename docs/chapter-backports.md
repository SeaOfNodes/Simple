# Chapter backport review

This is a queue of pending work. Completed corrections leave the queue; the
[validation summary](#validation-record) records their scope. Detailed old logs
are disposable build artifacts. Reusable rules live in
[the AI notes](../skills/chapter25-codex-notes.md).

Introduce independent fixes in the earliest applicable chapter and propagate
them through every affected snapshot. Testing Chapter 25's inherited tests does
not validate the compilers in the earlier chapter directories. Chapters 10 and 11 introduce bulk memory and lazy memory partitioning;
Chapter 12 adds references, Chapter 13 schedules the graph, and Chapter 14
introduces floats and narrow numeric types. Keep the
rest of SSA construction with incomplete types in Chapter 25 for now. Cliff wants to revisit splitting Chapter 25
into smaller chapters; escape analysis remains in 25 until that larger review.

Reset checkpoint (2026-09-28): Cliff committed the completed FunPtr work as
`75390f47` (`Backport FunPtr`), following `586c8f65` (direct C data bindings) and
`8212a66f` (constructor checks). The working tree was clean before this notes
update. No FunPtr implementation or validation remains outstanding; the full
21-25 results are recorded below. The subsequent narrow C integer-return ABI
correction starts in 22 and is forwarded through 25; see the validation record.
Splitting Chapter 25 is deferred, not an instruction to renumber.

## Chapter numbering after the memory reorganization

| Previous chapter | Current chapter |
|---|---|
| 10a: whole memory | 10 |
| 10b: lazy memory splitting | 11 |
| 13: references, plus typed fields from 12 | 12 |
| 11: GCM, retaining reference support | 13 |
| 12: floats, and 14: narrow types | 14 |

Chapters 15-16 and 18-25 retain their numbering; the subsequent mutability split
replaces 17 with 17a and 17b. New Chapter 12 has no GCM or floating
point nodes; Chapter 13 introduces GCM without floats. Chapter 14 adds both
floating point and narrow numeric types. The inherited float test suite is
now `Chapter14FloatTest`; reference and GCM suites are `Chapter12Test` and
`Chapter13Test`. The README in Chapter 12 records the intentional `L0`/`L1`
shallow-reference boundary and resolving field definitions by name.

Historical validation entries below use the chapter numbers and test names
in effect when the work was done. In particular, old 10a/10b records refer to
current 10/11, and old `Chapter12Test` float regressions now live in
`Chapter14FloatTest`. The pending float-to-int conversion belongs with the
numeric material in Chapter 14; it is not implemented by this reorganization.

Validation of the reorganization (2026-10-03): Make suites pass in every
chapter 10-25, including the shared printer/ISA tests and Chapter 25's native
runtime tests. Chapters 10-14 produce release jars. Fresh standalone checkouts
of 12, 13, and 14 pass their suites and package without parent chapter sources.
Browser playback checks pass in 12 and 13. POM module order, portable IDEA
XML, chapter navigation, local links, SVG XML, shell syntax, and UTF-8/LF checks
pass. Logs are in `build/chapter-reorg/`.

The new main-Java increments (added + removed lines) are 529 for 10 to 11,
280 for 11 to 12, 867 for 12 to 13, and 1,185 for 13 to 14. These exclude tests,
prose, assets, and generated files. The references chapter is now a small,
separate language extension; the larger increments introduce scheduling and
numeric types respectively.

## Mutability syntax and chapter split (2026-10-03)

Chapter 16 introduces constructors. Chapter 17a introduces independent binding
and reference permissions; Chapter 17b adds inference, updates, conditional
expressions, and `for`. POMs, portable IDEA descriptors, Make discovery, README
navigation, and inherited test names follow the split. `Chapter17aTest` owns
the former final-field constructor tests; `Chapter17bTest` owns syntax sugar.

From 17a through 25, `!Point`/`~Point` control pointee access and `!p`/`~p`
control reassignment. Primitive bindings default mutable; references default
fixed and struct access defaults read-only. Arrays default writable, with
`[~]` specifying a read-only layer. `var` and `val` preserve initializer access.
Read-only views are deep but do not freeze other aliases or remove memory
dependencies. Writable array slots require matching element permissions.

`TypeMemPtr` carries the access bit independently of fields and preserves it
through forward-reference resolution, cyclic interning, inference, loads,
calls, and Chapter 25 serialization. Cyclic factories build the complete type
before interning to avoid orphaning temporary types. Array aliases are shared
across access views, while their element permissions remain distinct. The
23/24 static-field distinction now requires an explicit initializer; a fixed
nullable field's implicit null is still an instance field.

Constructor permissions cover only allocation fields, including assignments in
nested blocks. They do not permit rebinding surrounding or constructor-local
fixed variables. Chapter 25's constructor-exit check also handles required
primitive fields. The object format changes to `C0D2`; rebuilding `sys.o` is
required and exercised by its native tests.

The changed graphs exposed two small existing assumptions: Chapter 24 must
recognize a Phi whose own backedge is still under construction, and a memory
query through New must record a dependency even when the queried loop MemPhi
is a user of the Store. The memory-query dependency is propagated across the
applicable bulk-memory implementations.

Validation: Make suites pass in 16 (244), 17a (255), 17b (304), 18 (339),
19 (383), 20 (405), 21 (444), 22 (465), 23 (487), and 24 (517); applicable
chapter-local fuzz regression wrappers and the shared printer/ISA suites pass.
Chapter 25's complete Make suite passes, including native/library execution and
rebuilt serialized objects. Fresh standalone 17a/17b copies pass their tests
and produce release jars; 16 also packages. POMs, IDEA XML, and navigation
links are checked. Test-export tooling recognizes 17a/17b and its suite passes;
unsupported Java test patterns are skipped per method rather than per class.
Existing spill expectations changed in the String fixture:
22's RISC-V count is 1 instead of 3; 24's x86 count is 0 instead of 9.

## Pending corrections

### Shared ISA encoders and evaluators: Chapters 21-25 (review checkpoint)

Issue #257's extraction is implemented in [`isa/`](../isa/README.md). `X86`
accepts concrete registers, widths, immediates and addresses; `Arm64` and `RiscV`
pack instruction words. Chapter `Encoding` classes implement `CodeSink` using
their existing byte writers. ARM logical immediates and constant construction
are shared too. None of these helpers imports compiler classes.

Instruction selection and masks, compiler-type interpretation, frame and block
layout, branch relaxation, relocations, constants, and ELF remain chapter-local.
Branch byte sizes and patching are shared; the chapter still runs relaxation
and supplies targets. One shared `EvalArm64` and `EvalRisc5` replace the five
copies of each. Image construction and test assertions stay in chapter harnesses.
Evaluators compile as test support. Make continues to bundle tests in its release
jars; Maven and IDEA retain separate main/test dependencies.

Direct byte tests also cover corrections exposed by consolidation: x86 legacy
prefix order for 16-bit memory operations, indirect register calls and their REX
bits, imm8 shift counts, high-register PUSH/POP, low-byte SETcc, and five-byte
unconditional near jumps. RISC-V JAL now takes its sign bit from displacement
bit 20. ARM masks negative literal-load offsets, checks byte displacement ranges
correctly, and its evaluator distinguishes MOVN from AND-immediate.
A disposable GNU assembler comparison agrees on all 5,760 tested x86
load/store/LEA instructions (`build/IsaOracle.java`). Shared tests add fixed ARM
and RISC-V words and evaluator checks for multi-instruction ARM constants.
Make, Maven source paths, IDEA wiring, and linearized checkouts include `isa/`
starting at Chapter 21; Chapters 1-20 have no ISA dependency.

Validation: full Chapter 21-25 Make suites pass, including native execution and
the existing allocator checks (`build/isa-final-tests.log`). The independent
shared ISA tests pass; a fresh linear Chapter 21 release contains all three
encoders and shared evaluators (`build/isa-linear21-complete-release.log`).
Maven/IDE XML parses; the standalone POM gains main and test ISA source roots
at Chapter 21 only. Shell syntax and LF checks pass. Nothing has been pushed.

### Chapter 15 allocation design (implemented)

Chapters 12-14 retain explicit initialization Stores. Chapter 15 replaces them
with New's zeroing operation while keeping one parser-visible `$mem`.

| Interface | Chapter 15 |
|---|---|
| New inputs | `{ctrl, $mem, size}` |
| New outputs | `{ptr, $mem}`; no control result or per-alias projections |
| Coverage | field aliases from New's internal TypeStruct/TypeMemPtr |
| Input memory | partial MemMerge, no default, entries for affected aliases |
| Result integration | whole-memory MemMerge, prior default, affected entries all using the same New memory projection |
| Memory types | existing alias plus stored-value type; no lattice extension |

A null MemMerge default means absent aliases are uncovered. Empty structs
therefore have an empty partial input; their allocation does not change the
whole-memory aggregate. New's output also covers only its own aliases.

An alias-specific contents query combines incoming values with the field's
initializer from New's struct (integer zero, floating zero, or null). It records
optimizer dependencies and stops at cached Phi types to break loops. MemPhi
merges the scalar contents of its precise alias. Start's incoming heap is empty
in this chapter; functions with heap arguments will require a different entry
query in Chapter 18.

Loads fold against their own New and bypass a proven distinct allocation via
New's memory input. Different pointer nodes alone do not establish disjointness.
Array length remains a subsequent Store; array access control inputs remain.
Global code motion and the evaluator scheduler both follow an alias through
MemMerge to discover the allocation's ordering constraint. MemMerge is packaging,
not itself a clobber. The viewer and evaluator use the new slot conventions.

### Chapter 16 constructor integration (reviewed)

ScopeNode now owns the variable records and one `$mem` binding; the obsolete
ScopeMin alias table is removed. New inputs are `{ctrl, $mem, size, fields...}`,
with initializer inputs in struct-field order. Outputs remain `{ptr, $mem}`;
partial memory coverage and whole-memory integration are unchanged from 15.
Alias contents read initializer types with dependencies, rather than assuming
zero. The evaluator reads these same inputs, including array length. Constructor
body effects participate in ordinary lazy branch/loop memory SSA.

The sole-use Store-to-New check is deliberately unchanged. A shared allocation
memory result has more users than a per-alias result, so it permits fewer folds.
`Chapter15Test.testBasic5` retains a `.y` Load instead of folding it to 3.14;
both runtime results remain checked. Other changed printed expectations only
adjust Region/Loop IDs after removing parser memory nodes.

### Chapter 18 function memory (reviewed)

Chapter 17 retains readonly casts, forward reference type updates, lexical
guards, and increment/assignment semantics while replacing ScopeMin with one
memory binding. Chapter 18 keeps its top-level Var class and makes MemMerge an
ordinary optimizer node rather than a parser alias table. Memory Parm 1 stays
opaque; local branches, loops, and merged returns use BulkMemPhi/MemPhi.
Function entry and CallEnd memory have unknown contents, including heap arguments.
Calls consume and return the complete memory state. There are no alias summaries
or private constructor effects. Inlining can expose the existing alias rewrites.

New retains `{ctrl, $mem, size, fields...}` inputs and `{ptr, $mem}` outputs.
The evaluator, Eval2, graph viewer, and scheduling use those slots. Call is a
clobber for every alias. GCM raises a Load before the Call's block terminator;
it must not append the Load to the Call's inputs, which encode arguments and a
last-slot function pointer. The one-step Phi Load guard also stops at Calls.

### Chapter 19 instruction selection (reviewed)

Both selectors preserve BulkMemPhi exclusions and MemPhi aliases. Selected
memory instructions carry their alias through MemOpNode; GCM follows aggregates
for ordinary and folded reads, waits for matching writers, and inserts ordering
edges that require no register. Direct machine calls embed their target and must
keep their argument lists intact, just like ideal Calls.

Preserve this chapter's existing lowering of nonzero initializers into Stores.
New now takes `{ctrl, $mem, size}` and produces `{ptr, $mem}`; there are no field
value inputs at this stage. Partial coverage and whole-memory integration are
unchanged. New's alias contents meet incoming memory with the field's zero type.
Both CPU allocation register masks and both evaluators use the new slots.

The nested-loop regression reaches x86's existing right-hand Load/Add TODO;
that commutative case now uses AddMemX86 just like a left-hand Load. No other
unfinished instruction patterns or encoding work is included.

### Chapter 20 register allocation (reviewed)

ARM joins x86-64 and RISC-V in preserving bulk/precise memory Phis, allocation
slots, and selected memory effects. The allocator already excludes TypeMem Phis
and gives memory/ordering edges no register constraints; no allocator heuristic
changes are needed. Allocation regressions check that memory stays unallocated
and the pointer projection receives a legal register on all three targets.

Folded x86 Add must retain a value input for its two-address result even when
that value is constant. The old immediate form omitted input 4 and caused a null
live range in BuildLRG. Both left- and right-hand Load patterns now retain the
other addend; the existing constant selector materializes it.

Keep Chapter 20's inlining-safe Return typing and the dependency registrations
on a rejected Phi factoring attempt. Those fixes predate this memory port.

### Chapter 21 encoding (reviewed)

All three targets retain the memory Phi subclasses and New's pointer slot 0,
memory slot 1, and size input 2. Chapter 21 caches New's register masks in the
shared node during register allocation; update that shared interface. Writers
are placed before adding read-before-write constraints; the dominator walk
must still find conditional writers above a Load's proposed merge placement.
Calls receive no scheduling-only operands.

Dependency registration reverses direction here: `consumer.addDep(producer)`.
The forwarded memory queries and explicit forward dependencies follow that API.
Synthetic no-exit loops collect MemPhi inputs by alias and use BulkMemPhi for
the merged return memory. The one-step Load factoring guard is unchanged.

Actual execution exposed a full-width integer Load allocated to XMM but encoded
as a GPR load. LoadX86 now selects the encoding from the allocated register bank,
preserving register masks. ARM's emulator now supports MOVK and MOVZ's complete
16-bit immediate and halfword shift. Carry these narrow fixes with later ports.

BrainFuck follow-up: both the original and ported compilers retain 12 ideal
Loads and 114 Stores (106 Stores initialize the program). In the RISC-V Hello
World run, both execute 3,261 heap loads and 646 heap stores; stack loads/stores
change from 15/15 to 1,006/572, mostly spilling `d+4`. There are also pre-existing
missed length optimizations: `program#` reloads the known 106 on all 907 loop
tests, and `old#` is reloaded after allocating the replacement output array.
Load forwarding stops at memory Phis; its loop-profit test does not see through
the backedge's multiway Phi. The old-output pointer is itself a Phi, beyond the
two-direct-allocations disjointness test. Field aliases distinguish array length
from array data, but do not distinguish separate arrays of the same element type.
A scratch source replacing only `program#` with 106 passes Hello World, removes
907 heap loads, and returns stack traffic to 15/15. Its weighted split score
nevertheless rises from 130 to 165 as allocation inserts many pointer copies
before initialization stores; this score is not a dynamic memory-traffic measurement. No compiler
optimization was changed in this investigation. Probe sources and before/after
logs: `build/BrainMemory{Probe,Variants}.java`, `build/brain-memory-*.log`, and
`build/brain-variants-*.log`.

The subsequent Chapter 21 Load search now folds `program#` through the loop.
The search returns a folding witness without creating nodes or rewiring edges;
it retains optimizer dependencies. At a non-loop MemPhi it must prove every
arm, with a BitSet remembering successful merges. Returning to the original
loop memory is success only for an invariant pointer. Unknown clobbers, other
loops, and incomplete merges fail. A separate builder creates value Phis after
the proof; unchanged paths refer to the new loop value Phi. Enqueue that Phi:
a self-edge can otherwise keep an obsolete Phi alive after local folding.
Controlled array accesses are not hoisted across a merge. The one-step
`Load.clobbered` factoring guard is unchanged. Carry this search with later ports.

The unmodified BrainFuck source now has 11 Loads. RISC-V Hello World executes
2,354 heap loads, 646 heap stores, and 15/15 stack loads/stores, compared with
3,261 / 646 / 1,006 / 572 before this search. Emulator instruction count drops
22,965 to 21,920 (not a hardware timing). Retained/weighted moves change
46/130 to 144/165 as allocation inserts repeated initialization-store pointer copies.
Four reduced cases cover unchanged N-way arms, a possibly aliasing arm,
loop-carried pointers, and distinct values folding on all arms; they pass
2,800 evaluations across 100 seeds. The existing 9,800 memory evaluations pass.
At that checkpoint, the 432-test suite had only the user-edited BrainFuck golden failure
(42 versus 165). A temporary test copy restoring native BrainFuck execution
and using 165 passes all 91 spill-cohort entries and their runtime checks:
943 / 1,594 moves / weighted moves. The user's test edits were preserved.
Logs: `build/load-search21-tests5.log`, `build/load-search21-seeds.log`,
`build/load-search21-loop-seeds.log`, `build/brain-variants-search.log`, and
`build/load-search21-spills-complete.log`.

### Chapters 22-24 lazy memory (implemented, review checkpoint)

The forward port now reaches Chapter 24. Parameters, calls, and returns carry
whole memory; BulkMemPhi/MemPhi discover aliases within functions. New consumes
partial memory for its instance fields and produces `{ptr, $mem}`. The Chapter
21 loop Load search, one-step Load factoring guard, dependency fixes, and
alias-aware scheduling/evaluation carry through all three snapshots.

Chapter 22 preserves C calls and its default-main handling. Chapter 23 keeps
cyclic field types, deep-final Loads, and symbolic field offsets; class-wide
fields do not participate in an allocation's memory coverage. Phi factoring
rejects operand Phis mixing pointers and integers, even for boolean operations.
A precise Phi waits for its bulk input to split instead of hiding that request
behind an upcast. Eval2 keeps unknown-caller pointer placeholders distinct from
actual heap constants.

Chapter 24 retains its optimistic solver: MemPhi ignores TOP/XCONTROL arms,
New returns high results while its control is high, and alias contents register
a dependency on that control. The existing integer widening and call-graph
solver remain intact. Register checks run before encoding adds untyped branches.
Private constructor memory, escape tracking, and incomplete-type SSA stay in 25.
Pretty-printer consolidation is implemented below.

### Chapter 25 memory follow-through (implemented, same review batch)

The memory representation was already present. The remaining port carries the
pure loop Load search, generalized Phi factoring with the one-step Load guard,
aggregate-aware scheduling, Call operand protection, and dependency wakeups.
It also carries the x86 register-bank Load encoding and ARM MOVZ/MOVK emulator
corrections. Bulk splitting queues new precise Phis instead of assuming an
eager fold still returns a MemPhi.

The search preserves private constructor memory and Escape publication. A Store
whose alias is still 1 blocks the proof; external storage cannot be bypassed
using allocation identity. Length reads can drop control, while indexed array
reads retain it. Integer loop value Phis use Convert to preserve the declared
load width, and every new nested value Phi is queued for optimization. The
constructor, escape, serialization, and incomplete-type architecture stays intact.

### Adjacent-copy forwarding: Chapters 21-25

Chapter 21 now removes a post-color Split when its sole use is the immediately
following non-Phi, non-CFG instruction, the operand accepts the source register,
and the operand is not two-address tied. Adjacency proves no intervening clobber.
The same rule and focused guard regression are present in 22-25; Chapter 20
retains its baseline allocator. No spill-selection or CFG splitting rule changed.

RISC-V BrainFuck keeps its original source and now has 35 / 42 retained / weighted
moves instead of 144 / 165. Hello World executes 21,787 emulator instructions
instead of 21,920; heap traffic remains 2,354 loads / 646 stores and stack traffic
15 / 15. Full Make suites and fuzzer targets pass in 21-25. Chapters 21-24 also
pass complete spill reports after refreshing reduced-move expectations:

| Compiler | Allocations | Retained moves | Weighted moves |
|---|---:|---:|---:|
| 21 | 91 | 821 | 1,444 |
| 22 | 117 | 826 | 1,470 |
| 23 | 147 | 913 | 1,711 |
| 24 | 214 | 1,310 | 2,969 |

Cohort 22 includes the two zero-move C return ABI checks added since the older
24-entry audit. Chapter 25's frozen 212-entry manifest remains unchanged.
At that audit, its reporter failed on ten pre-existing String-constructor parse errors;
the unmodified allocator reproduces all ten. The same 216 successful allocations
(including 14 current cohort-25 allocations) improve from 2,563 / 5,496 to
2,494 / 5,399, but these are partial totals. The subsequent historical String
fixture repair below restores complete reporting. Fresh system-library encoding and
native cohort-25 checks pass. Logs: `build/copy-forward21-spills-final.log`
through `build/copy-forward24-spills-final.log`, `build/copy-forward25-spills.log`,
`build/copy-forward25-baseline.log`, and `build/copy-forward*-tests*.log`.

### Shared debug printers: Chapters 2-25 (implemented)

The new [`print/`](../print/README.md) module owns expression recursion, IR
row formatting and ordering, scheduled dumps, and assembly listing layout.
Chapters retain their node spelling and small adapters for graph/machine facts.
All chapter Nodes extend the shared `BaseNode<Node>`, providing one read-only
node contract for printers and the viewer. Chapter 2 arithmetic needs only a
one-line `format()` hook such as `"(%1+%2)"`; variable forms use protected hooks
with `p.p(...)` and `p.n(...)`. Repeat tracking is universal, with repeated
expansion for short constants. Common graph edge and snapshot construction has
also moved out of chapter adapters.
This also addresses issue #259's outside-package subclass use case. PR #260's
public `_print1` change is superseded by the context API, rather than merged.

The shared CFG-first RPO carries the Chapter 21 BrainFuck ordering correction
through 25: loop headers precede their bodies, closing Regions follow them,
and Phis stay with their headers. Function boundaries exclude caller arguments;
shared floating expressions of constants print once with globals. All traversal
and placement state is private; raw edges and captured encoding/layout metadata
keep diagnostics read-only. `Node.toString()`/`print()` remain the expression
entry points; `Node.p(depth)` starts structured dumps in Chapter 8, and
`CodeGen.toString()` starts whole-program dumps in Chapter 18. The unused
Chapter 7 IR adapter, `Node._printLine`, legacy LLVM-format wrappers, and
`prettyPrintScheduled` aliases are removed. Evaluator/compiler `printLine`
callers and the scheduled whole-program path in Chapters 20-24 remain.
The wrapper cleanup passes all 26 chapter suites and the shared printer
contracts (`build/prune-print-tests.log`, Chapters 1-9, and
`build/prune-print-later-tests.log`, Chapters 10a-25).

Make, Maven source paths, IDEA module dependencies, release packaging, and
linearized checkout support include the shared sources. Shared contracts cover
cycles, function boundaries, shared globals, Phi/projection grouping, multiline
assembly, and captured pool metadata. Disposable real-graph checks cover
BrainFuck and call/loop programs through Chapter 25, including repeatability,
complete unique output, CFG order, and graph/cache identity. An outside-package
Chapter 2 subclass compiles and prints nested operands using the protected hook.

Validation: all 26 chapter Make suites and the shared contracts pass
(`build/print-final-tests.log`). Chapters 21-24 each pass 18 real-graph snapshots;
25 passes 12 (`build/print-probe-*.log`). Assembly checks cover all available
CPUs in 19-25 before/after allocation and encoding (`build/asm-probe-*.log`).
Fresh linearized Chapter 2 and 21 releases contain the shared printer classes;
Chapter 2's fresh tests also pass (`build/print-linear{02,21}.log`). Maven/IDE XML
parses, the linearization script passes its syntax check, and changes are LF-only.

The subsequent BaseNode cleanup also passes all 26 suites
(`build/base-early-final-tests.log`, `build/base-late-tests.log`) and the updated
shared contracts, including multi-digit slots, escaped percent signs, missing
inputs, cycles and matching-suffix trimming. Three Chapter 5-6 print expectations
now use a short reference on a repeated Region. Nine Chapter 21 viewer snapshots
match their pre-cleanup baseline except for canonical constant labels; both 21
and 25 pass nine read-only, repeatable captures. Outside-package expression and
format hooks compile. Fresh linearized Chapter 2/21 releases contain BaseNode and
viewer classes (`build/base-linear{02,21}.log`). The graph IDEA module now depends
on print. This cleanup removes about 2,800 node-source lines and 550 viewer-adapter
lines beyond the original extraction.

### Other pending corrections

- **Float-to-integer conversion (new feature).** Missing from Chapters 14-25;
  Chapter 14's `RoundF32Node` only narrows floats to `f32`. Add an explicit
  conversion, with Chapter 14 as the proposed introduction, and carry it
  through the later chapters. Decide syntax, rounding versus truncation,
  and behavior for NaN, infinities, and out-of-range values before implementing.
  Cover constant folding, evaluation, and machine lowering on all three targets;
  update the Chapter 12 explanation and add boundary-case regressions.

- **Existing Chapter 18 floating-array assertion.** The unchanged
  `TypeStruct.makeAry` assertion accepts integers and nullable references but
  excludes TypeFloat; `return new flt[1];` fails under `-ea`. The Chapter 18
  memory probe uses floating struct fields for initialization coverage instead.
  Investigate separately; this port does not change the array type lattice.

- **Chapter 24 Load BOTTOM-on-error backport, unwound/deferred (2026-09-28).**
  Cliff requested starting with 24 and analyzing failures before proceeding.
  Literal global BOTTOM memory/pointer inputs already propagate BOTTOM. The
  missing behavior is `LoadNode.compute()` returning BOTTOM instead of the
  declared field type when `err()!=null`; Cliff unwound that one-line experiment.
  Chapter 25's Convert nodes supply the surrounding expression/loop's typed
  result while retaining the erroneous BOTTOM-producing Load for TypeCheck.
  Chapter 24 relies on the Load itself supplying that typed result. Defer this
  backport with the conversion split rather than importing it alone.
  The experimental full Make suite had four failures out of 477
  tests (`build/load-bottom-24.log`); the fuzzer recipe was not reached.
  - `BrainFuckTest.testBrainFuck`: during parsing, the unfinished loop Phi for
    mutable `program` has its declared nullable type. The element load becomes
    BOTTOM, and reading the initialized `command` local triggers Parser's
    BOTTOM-means-uninitialized check before the loop can finish. Reduced valid
    source: `var !program="a"; for(int pc=0; pc<program#; pc++) { var command=program[pc]; return command; } return 0;`.
  - `Chapter10Test.testWhileWithNullInside`: the loop's result Phi starts at its
    declared `i64` while incomplete. Closing the loop exposes a BOTTOM Load on
    the backedge; Phi.compute explicitly propagates BOTTOM, violating the
    pessimistic `setType` narrowing assertion. Trace: Phi#1458 goes from i64 to
    BOTTOM with Load#1462 on its backedge. This occurs during parsing, before SCCP.
  - `Chapter10Test.testNullGuardErrors`: ReturnNode sees BOTTOM without any
    recorded scalar return kind, so its `No defined return type` error wins
    before the Load's `Might be null` diagnostic.
  - `Chapter13Test.testLinkedList0`: `head.next` becomes BOTTOM, and parsing the
    following `.i` reports `Expected reference but found Bot` first.
  Scratch instrumentation lives entirely under `build/load-bottom-probe`.
  Reduced sources pass or report the intended null error with the HEAD Load
  compiled separately, and reproduce the failures with the one-line change
  (`build/load-bottom-reduced-baseline.log`, `build/load-bottom-reduced.log`,
  `build/load-bottom-trace.log`). No earlier chapter was changed. Fixing these
  requires coordinating parse-time incomplete types, typed loop-Phi bounds,
  and diagnostics; the assertion and expected errors have not been weakened.

- **Chapter 25 BOTTOM-on-error backport boundary (2026-09-28).** Cliff requested
  investigating the earlier arithmetic contracts. In 24, changing ArithNode's
  non-integer fallback to global BOTTOM breaks the valid `testCoRecur`: the
  declaration `val az = x*2` refers to `x` defined later, and the allocation-time
  check confuses its temporary BOTTOM with an unset field. Three arithmetic
  diagnostics also become generic parser errors (`testNullRef5`, `testMaskFloat`,
  `testSubZeroTypeError`). Full-suite evidence: `build/arithmetic-contract-24.log`.
  Restoring the integer fallback fixes all four in a 40-test focused run
  (`build/arithmetic-top-24.log`). The Java return signature itself has no typed
  caller dependency. Further BOTTOM work is paused for Cliff's decision about
  separating forward/unresolved expressions from initialization errors in the
  earlier parser. The independent Load experiment is recorded above. The
  known-null regression is already rejected
  during parsing in 10-24. Keep this separate from the constructor/memory fixes.

## AOT class initialization: larger independent work

Proposed on 2026-09-19; deferred behind the smaller queue above. This is
a substantial, self-contained Chapter 25 change, not an entangled backport.
Earlier chapter applicability has not been established.

The goal is to pre-allocate AOT class objects in ELF data and pre-fill their
fields, letting ordinary graph optimization remove redundant initialization.
Lazy-loaded classes retain runtime initialization for now.

- Represent static object creation with a dedicated node, or a ConstantNode
  carrying a TypeStruct with the appropriate final field values. Preserve the
  class's identity and canonical layout independently of its current field
  values; equal contents must not merge distinct class objects.
- Use normal StoreNode peepholes to remove same-value-over-same-value stores.
  If needed, add a peephole that folds initializing stores into the static
  creator's type. Create a replacement ConstantNode/type; never mutate a shared
  constant's type. Prove that folding preserves initialization ordering and
  does not expose a final value prematurely through another reference.
- When `<clinit>` reduces to returning the static object, with no remaining
  I/O or public-memory effects, its runtime work can disappear. Initially it
  is sufficient to emit a trivial `<clinit>` and keep its calls. Omitting the
  function or calls is a later optimization; mixed initializers retain their
  remaining effects. Adapt this conceptual return to the existing class-memory
  representation rather than assuming the current `<clinit>` returns a pointer.
- Extend encoding's relocation information to describe addresses stored inside
  data: functions, other class objects, and constant objects such as strings.
  Emit the required data-section relocations, including their symbol, field
  offset, width, and addend. Keep native linking and emulator linking consistent.
- Give the function and data distinct stable symbols, e.g. `A.B.<clinit>` and
  `A.B.$class`. The defining CompUnit owns the class object and emits its one
  definition in its ELF. Other compilation units, including source currently
  being compiled, reference those symbols without emitting another copy of the
  class data. Unifying loaded Simple types/IR alone does not unify native data;
  preserve ownership and identity across partial compiles and serialization.
- Target four-byte function **and data** pointers, with code and data addresses
  restricted to the low 4 GB. Function pointers stay four bytes; data pointers
  must be brought into agreement. Account for layout, loads/stores, relocations,
  native runtime/FFI boundaries, and actual placement; do not silently truncate
  an out-of-range address. This is a proposed representation constraint, not a
  claim that the current runtime already satisfies it.
- Emit immutable, fully preinitialized objects in read-only storage. Objects
  with mutable fields or remaining runtime initialization writes stay writable.
  Retain the current writable-class-storage correction until those writes have
  actually been eliminated.

Acceptance should cover scalar and pointer fields, shared/cyclic object
references, preserved effects in mixed initializers, and serialization
round-trips. A diamond of separately compiled users must share one class-object
address and observe each other's mutations. Check ELF definitions/relocations,
address-range enforcement, native execution, emulator execution, and the full
Chapter 25 suite, including `make -j 4 tests`.

## Larger or entangled changes: defer

| Group | Eventual home | Why not in the small queue yet |
|---|---|---|
| Conditional Store and array Load control | Memory/arrays chapters | Reproduce under the earlier alias model before extracting fixes from the new memory implementation. |
| SCCP dependencies, function revival, reachability | 24; some foundations may fit 18 | Separate old-IR corrections from new Guard/Escape/BulkMemPhi and external-caller machinery. |
| TypeScalar, numeric modes, guards, symbolic fields, open/forward types | Revisit earlier homes later | A connected incomplete-types architecture, including phase ordering and errors. |
| BulkMemPhi/MemPhi, private constructor memory, allocation helpers | Revisit memory / constructors / methods | Move invariants and regressions together. |
| Serialization, global identity remapping, module escape summaries | Separate compilation | Remain with modules. |

## Register allocation: completed chapter progression

The allocator review is complete through Chapter 25. Each README ends with the
introduced technique, measured program cohorts, and commentary. `make spill-stats`
reports actual retained moves, per target and cohort, with assertions enabled.

| Chapter | Additional technique | Final cohort sizes |
|---|---|---|
| 20 | Basic coloring, rematerialization and loop splitting, with shared legality/progress fixes | 39 |
| 21 | Conservative copy coalescing and adjacent-copy forwarding | 39, 52 |
| 22 | Stronger copy-chain/backedge bias and cheap-spill ordering | 39, 52, 26 |
| 23 | Group popular single-def uses by compatible register classes | 39, 52, 26, 30 |
| 24 | One cold-only attempt for loop-Phi self-conflicts, then mandatory fallback | 39, 52, 26, 30, 67 |
| 25 | Existing area/cost ranking and later conflict strategies | 39, 52, 24, 30, 67, 14 |

Shared corrections include RegMask/LRG bookkeeping, null use masks, kills without
an output LRG, self-conflict splitting, compatible rematerialization, clobber-aware
copy reuse/bypass, deterministic ordering, and side-effect-free diagnostics. The
final audit adds the missing narrow x86 masks in 20-21 and RISC-V mask in 21;
fixed-neighbor color bias in 20-25; and ARM memory register-bank selection,
float opcodes, and matching emulator fixes in 21-25.

Chapter 25's additional fixed-use/deep-side splitting thresholds remain there.
The def-use-count snapshot and null-use guard support its extra split branch;
20-24 do not have that branch. Earlier kill handling subtracts killed registers;
25 can choose to split cloneables earlier. No independent earlier-chapter
correctness failure was established for these strategy differences. They are
not uncompleted promises to copy the complete allocator backwards. Compilation-
unit ownership and void/metadata handling also stay with the later representation.

The final ARM failure was substantive: the original Newton float programs reached
the allocation round limit because a float store demanded a GPR; the deeper loop
split kept cutting the wrong portion of the range. Permitting the supported FP
registers removes that conflict. Encoding must then use the allocated register
bank, with five-bit register numbers, for both immediate and indexed loads/stores.
The emulator must preserve floating bits and scale double offsets by eight.

Chapter 25 freezes the 212 earlier compilation entries in
[documented source fixtures](../chapter25/src/test/java/com/seaofnodes/simple/spill/README.md).
Constructor/library adaptations change IR, so this is a program-cohort comparison,
not identical machine graphs. Those rows replay allocation and legality checks;
current Chapter25Test native checks and a fresh system-library encoding contribute
14 more recorded allocations. The complete 2026-10-02 replay totals **2,568 moves /
5,459 loop-weighted moves** over 226 compilations after the fixture repair below.
Earlier audit total, before adjacent-copy forwarding: **2,597 moves / 5,579 loop-weighted moves** over 222
compilations. Chapter 25's area/cost implementation is retained. Substituting the
earlier ranking saves five moves on the clients but fails a fresh `sys` allocation
at the eight-round limit; a failed library cannot be omitted from the comparison.

The earlier tables are refreshed where the final mask fixes changed them:
Chapter 20 was 238 / 357 before the subsequent lazy-memory port (now 234 / 360);
Chapter 21 was 840 / 1,456 before lazy memory and adjacent-copy forwarding.
The audit's coalescing on/off comparison saves
230 / 433 moves in Chapter 21 with identical legality fixes. Current totals for 21-24 appear in the adjacent-copy record above. Full details and comparison limits belong in
the individual READMEs rather than a second evolving set of tables here.

## Review and test protocol

1. Establish the destination baseline. Reproduce a real failure with a small
   source or constrained machine graph before classifying a change as a bug fix.
2. Apply the smallest correction, propagate it to all affected snapshots, and
   run each snapshot's focused checks and full `make tests`. Respect the current
   user-authorized review boundary; do not push without explicit instructions.
3. Force Java rebuilds when older Makefiles miss dependencies. Rebuild `sys.o`
   after compiler changes; include a fresh library compilation in allocator
   measurements rather than relying on an up-to-date object.
4. Keep legality/progress/runtime checks separate from spill expectations.
   Eight-round exhaustion is a failure; neither increasing the cutoff nor
   changing a golden proves it fixed. Preserve test membership, targets/ABIs,
   and fixed seeds. Do not sweep optimizer seeds for routine backend validation.
5. Report aggregate and local changes. Use same-compiler heuristic comparisons;
   changed frontend/lowering graphs are not allocator-only measurements.
6. Keep edited text LF-only, preserve unrelated changes, and run `git diff --check`.

The top-level runner accepts explicit chapter lists, e.g.
`make -k tests CHAPTERS="chapter20 chapter21"`. Tests in 25 alone are insufficient.

## Validation record

- **Chapter 25 historical String fixtures (2026-10-02).** Added an explicit
  constructor initializing the required non-null `cs` field in
  `Chapter21Test-testStringExport.smp`, `Chapter21AllocTest-testString.smp`, and
  `Chapter23AllocTest-testString.smp`. Equality/hash bodies and the frozen
  212-entry manifest are unchanged; all ten formerly failing entries now pass.
  These source adaptations establish a new comparison baseline, rather than
  demonstrating an allocator-only improvement. `make -C chapter25 spill-stats`
  passes all 212 historical entries, fresh system-library encoding, and all 23
  Chapter25Test tests, with zero spill-golden failures. The complete 226 recorded
  allocations total **2,568 retained moves / 5,459 weighted moves**; the README
  contains the cohort breakdown. Log: `build/string-fixtures-spill.log`.

- **Function loop-tree boundaries, Chapters 18-25.** Stop the post-order loop
  walk at Return, seed each function's outer tree before visiting its body, and
  use that tree for synthetic exit projections, return Regions and the parent
  of otherwise exitless loops. In Chapter 25, walking through Return/StopCU/Stop
  into Start mistook the external-world cycle for another loop: the function
  entry had depth 1 while its return Region retained depth 0. Function bodies
  now consistently have depth 0, with real loops adding depth. Chapters 18-20
  also assign the tree to the newly created Never and both projections.
  `Chapter18Test.testNeverExitLoopDepth`, forwarded through 25, covers single,
  multiple, unconditional and nested synthetic exits. The RISC-V pointer-store
  reproducer `Chapter25Test.testNeverReturnPointerRiscV` now runs through
  Encoding without changing the spill heuristic or eight-round limit; its
  `@Ignore` is removed. Baseline failures are recorded in
  `build/loop-tree18-baseline.log` and `build/loop-tree25-baseline.log`.
  Full Make suites pass in `build/loop-tree-all.log`: 18 **333+1**, 19 **377+1**,
  20 **399+1**, 21 **438+1**, 22 **459+1**, 23 **481+1**, 24 **511+1**, and
  25 **403+39+9+23+1+14**, plus shared printer/ISA tests. Spill goldens remain
  unchanged.

- **Synthetic TOP returns, Chapters 20-25.** TOP remains neutral in the type
  merge but has no runtime register value. BuildLRG excludes TOP definitions
  and Phi inputs; IFG does not propagate TOP arms into predecessor liveness;
  spilling ignores TOP for loop depth and inserts no copies for it. Chapter
  20's register-bias walk also stops when a Phi arm has no LRG. Coalesce keeps
  its normal requirement that both ends of a real copy have live ranges.
  `Chapter20Test.testAllocatorTopPhi` checks either Phi slot in scheduled graphs
  from Chapter 20 onward. Source regressions in 21-25 (where loop repair precedes
  instruction selection) cover integer, non-null pointer and floating returns,
  multiple synthetic exits, no real exit, and the original two-field memory
  reproducer. Existing `testNeverMemory` tests now continue through Encoding.
  Full Make suites pass: 20 **398+1**, 21 **437+1**, 22 **458+1**, 23 **480+1**,
  24 **510+1**, and 25 **402+39+9+22+1+14**, plus shared printer/ISA tests.
  Spill goldens were unchanged. The RISC-V pointer-store convergence test was
  ignored in that run; the subsequent loop-tree correction above enables it.
  Chapter 21 ARM/RISC-V execution
  of the original reproducer returns 0 for `arg=0` and stays in the loop for
  10,000 instructions for `arg=1`. Logs: `build/top-ra-all.log` (20-24 and
  shared modules), `build/top-ra25-final.log`, `build/top-ra-execution.log`.

- **GCM conditional-writer ordering, Chapters 21-25.** Restored the `idom()`
  walk from a writer's late block through its early bound, including matching
  memory-Phi predecessor paths. Chapters 11-20 already retained this walk.
  Exact-block checking missed a conditional Store that executes before a Load
  sunk below the merge. `Chapter21Test.testReadBeforeConditionalStore`, forwarded
  through 25, checks 120 for the aliasing case and 60 for the distinct-pointer
  case. Generated ARM/RISC-V execution agrees in Chapter 21. The walk was
  removed in `798bd959` (April 12, 2025, PR #201), before the Chapter 21 squash.
  Chapter 21's two RISC-V BrainFuck rows change from 35 / 42 to 48 / 146
  retained / weighted moves; the full 91-entry audit is now 847 / 1,652.
  Runtime checks pass and the two quality goldens are updated; no allocator
  heuristic changed. Validation logs: `build/gcm-walk-tests.log` (22-24),
  `build/gcm-walk21-final.log` (complete 21 rerun and spill audit), and
  `build/gcm-walk25-final.log` (complete 25 rerun).

- **Chapter 25 memory follow-through.** Full Make validation passes **482 tests
  plus the 17-seed fuzzer wrapper**, and the release jar and system library build.
  The loop-search and read-before-write regressions fail against the original LoadNode and pass with the port. A
  scratch run across **100 optimizer seeds / 3,100 evaluations** checks the
  invariant length, a possibly aliasing arm, a changing loop pointer, differing
  stored values at a multiway merge, and reads before writes. Seeds 0 and 9 are
  retained in the regression for unresolved Store aliases and nested Phi queueing.
  At that audit, the historical spill replay retained exactly its **ten known String-constructor
  parse failures**, with no spill-golden or native-check failures. The same 216
  successful allocations total **2,488 moves / 5,379 weighted moves**, compared
  with 2,494 / 5,399 before this port; this is not a complete-suite total.
  The subsequent historical String fixture repair above restores a complete replay.
  Logs: `build/mem25-negative.log`, `build/mem25-seeds.log`,
  `build/mem25-final2.log` (spill replay), and `build/mem25-final4.log`
  (full tests and release).

- **Chapters 22-24 lazy memory.** Full Make suites pass **454 / 476 / 506
  tests**, respectively, plus each chapter's fuzzer wrapper. Release jars build
  for all three chapters. Forwarded checks cover nested memory, constructors,
  calls and recursion, allocation registers, native x86 and emulated RISC-V/ARM
  execution, no-exit loop memory, and the loop Load search. Existing Chapter 23
  null-guard tests cover the mixed pointer/integer factoring rejection; Chapter
  24's BubbleSort workload covers the precise-Phi/bulk-input collapse guard.
  Spill reports pass all **117 / 147 / 214** compilation entries, including
  runtime and allocation checks, with no golden failures. Current raw/weighted
  totals are **823 / 1,474**, **901 / 1,699**, and **1,307 / 3,008**; prior
  totals were 826 / 1,470, 913 / 1,711, and 1,310 / 2,969. These include graph
  changes outside allocation, so they are not allocator-only comparisons.
  The chapter README tables contain the current cohort breakdowns.
  Logs: `build/mem22-final.log`, `build/mem22-release.log`,
  `build/mem23-final2.log`, `build/mem23-release.log`, and
  `build/mem24-final3.log`.

- **Chapter 21 lazy memory and encoding.** Baseline: **412 tests plus the
  fuzzer wrapper**. Final: **431 tests plus the fuzzer wrapper**. Make release
  and spill-stats pass. The 16 forwarded memory checks retain scheduling and
  allocation coverage; three execution/synthetic-exit tests add 42 native x86
  and 84 ARM/RISC-V evaluations with independent expected results, plus checks
  of no-exit loop memory through scheduling. **9,800 scheduled evaluations**
  pass across 100 optimizer seeds. Emulated multi-function programs enter at
  main's encoded offset, which need not be zero.
  The unchanged **91-entry** spill cohort changes **840 / 1,456** retained /
  weighted moves to **845 / 1,559**. RISC-V BrainFuck changes 35 / 42 to
  37 / 58 in the Chapter 20 cohort and 35 / 42 to 46 / 130 in Chapter 21.
  An ablation omitting only read-before-New ordering returns those rows to
  35 / 42 and 37 / 58; the ordering is retained, with no allocator tuning.
  All eleven changed rows are recorded in the chapter README.
  Logs: `build/memory21-baseline.log`, `build/memory21-final.log`,
  `build/memory21-seeds.log`, `build/memory21-spills-baseline.log`,
  `build/memory21-spills-final.log`, `build/memory21-spills-ablation.log`,
  and `build/memory21-release.log`. The later 22-24 port is recorded above.

- **Chapter 20 lazy memory and register allocation.** Baseline: 380 tests plus
  the fuzzer wrapper. Final: **396 tests plus the fuzzer wrapper**, with the
  15 forwarded regressions and one allocation regression spanning all three
  targets. **9,800 scheduled runtime evaluations** pass across 100 optimizer
  seeds. **42 selected/scheduled/allocated graphs** pass legality checks at fixed
  seed 123 on x86-64, RISC-V, and ARM. Make release and spill-stats pass.
  The unchanged 39-entry spill cohort changes from **238 / 357** retained /
  weighted moves to **234 / 360**. The x86 array prefix sum changes 5 / 5 to
  6 / 13 because selected-read ordering adds a loop move; a scratch ablation
  omitting that ordering returns to 5 / 5. No allocator heuristics or benchmark
  membership changed. The chapter README records all six changed program rows.
  Logs: `build/memory20-baseline.log`, `build/memory20-final.log`,
  `build/memory20-seeds.log`, `build/memory20-machine.log`,
  `build/memory20-spills-baseline.log`, `build/memory20-spills-final.log`,
  `build/memory20-spills-ablation.log`, and `build/memory20-release.log`.
  This checkpoint was reviewed and committed as `f01ee052`.


- **Chapter 19 lazy memory and instruction selection.** Baseline: 360 tests
  plus the fuzzer wrapper. Final: **375 tests plus the fuzzer wrapper**; release
  jar builds with Make alone. Forwarded 13 memory regressions and added two
  instruction-selection checks: alias/Phi preservation and allocation/call slots
  on both targets, and folded x86 reads ordered before clobbering Stores.
  **9,800 scheduled evaluations** and **2,800 selected/scheduled graphs** pass
  across 100 optimizer seeds, with x86_64_v2 and riscv selection exercised.
  The latter checks graph scheduling and shape; this chapter does not execute
  encoded machine code. Logs: `build/memory19-baseline.log`,
  `build/memory19-final.log`, `build/memory19-seeds.log`,
  `build/memory19-machine-seeds.log`, and `build/memory19-release.log`.
  Chapter 14's inherited And graph factors here without widening; its runtime
  assertion remains. This checkpoint was reviewed and committed as `612555ee`.


- **Lazy memory through 17 and 18.** Baselines: 17 has 288 tests; 18 has 318
  plus its fuzzer wrapper. The port passes **298 tests in 17** and **331 tests
  plus the fuzzer wrapper in 18**. Both carry the nine memory regressions and
  constructor regression; 18 adds checks for non-inlined calls, recursive heap
  updates, and allocation inside an inlined function. The non-inlined check
  also verifies Call's input count and last-slot function pointer after GCM.
  **7,700 / 9,800 evaluations across 100 optimizer seeds** pass in 17 / 18,
  checking final memory values, complete scheduling, partial New inputs, and
  two-result New. Full suites and both release jars build with Make alone.
  Logs: `build/memory17-18-baseline.log`, `build/memory17-18-final.log`,
  `build/memory17-seeds.log`, and `build/memory18-seeds.log`.
  Chapter 17's And graph remains unfactored when the result would widen; the
  existing runtime assertion is unchanged. Shared allocation memory retains
  the `.y` Load in its inherited array example, as in 16. Nullable diagnostics
  select the earlier array dereference in 17/18. The Chapter 17 null test now
  makes `p2` mutable so its setup does not fail first on an unrelated final-field
  store. This checkpoint was reviewed and committed as `2d51b647`.

- **Chapter 16 lazy memory and constructors.** Baseline: 237 tests. The port
  passes 247 tests, including nine carried memory regressions and one constructor
  case combining reads, writes, a loop, and an unrelated alias. Full Make tests
  and release builds for 10a-16 pass: **1,568 tests across eight snapshots**.
  An additional **7,700 evaluations across 100 optimizer seeds** check memory
  behavior, complete scheduling, two-result New, and partial allocation inputs.
  Logs: `build/memory16-baseline.log`, `build/memory16-all-tests.log`, and
  `build/memory16-seeds.log`.
  The changed graph order exposed a dependency notification gap in
  `Chapter14Test.testCloneAnd`: replacing Minus#25 rewired And#28 without waking
  its dependent Phi#26. `Node.subsume` now wakes each rewired user's recorded
  dependents in 10a-16. The existing test passes without weakening its assertions.
  Chapters 17+ are unchanged; constructor integration is the review boundary.

- **Generalized Phi factoring, 10a through 15.** Replaced binary-only factoring
  and the blanket memory exclusion with operand-wise Phis and exact-class
  `copyEmpty`. Matching Loads and sole-use Stores are eligible when unbound to
  control; operation attributes, field/alias identity, operand types, and
  result-type monotonicity are preserved. Load safety examines memory users
  before explicit GCM anti-dependencies exist. The final guard checks only
  immediate users, stopping at Stores, Phis, MemMerges, and New in 15. Memory factoring waits for bulk
  partitioning at its Region; MemMerge and BulkMemPhi are never factored.
  Stores bound to control in 11-14 remain excluded. User removal now wakes
  recorded dependents, and empty-diamond folding observes projection rewiring.
  Three regressions live in each snapshot's `Chapter10Test`; the original 10a
  Phi fails both positive folding checks, and disabling the Load safety guard
  exposes an invalid read/write schedule in the negative probe.
  Full Make tests pass **159 / 162 / 177 / 181 / 196 / 216 / 230** and all seven
  release jars build. A 10a Phi label and one 14 Region ID were refreshed;
  expression/value expectations remain unchanged. Each snapshot also passes
  **3,500 evaluations across 100 optimizer seeds**, and 15 passes another
  **7,000 allocation/memory evaluations**. Logs: `build/phi-drop-baseline.log`,
  `build/phi-drop-tests.log`, `build/phi-drop-seeds*.log`,
  `build/phi-drop-negative.log`, `build/phi-drop-antidep-negative.log`, and
  `build/phi-drop-allocation-seeds15.log`. Chapters 16+ are unchanged.
  The subsequent guard refactor moves memory eligibility into virtual
  `MemOpNode.canDrop` overrides and clobber inspection into Load. Removed 10a's
  unused visited set. All 1,321 tests and seven release builds passed
  (`build/phi-guards-refactor.log`). At Cliff's request, the subsequent
  simplification also removes recursive aggregate traversal from 10b-15:
  a one-step check conservatively stops at possible indirect clobbers,
  keeping the teaching cost proportionate to saving one Load. The aggregate
  example now keeps two Loads in 10b-15; its runtime checks remain unchanged.
  All 1,321 tests and seven release builds pass with the simpler guard
  (`build/phi-guards-one-step.log`).

- **Chapter 15 partial allocation memory (review checkpoint).** Implemented the
  approved two-result New and carried the lazy memory model through parser,
  scheduling, evaluator, viewer roles, and chapter documentation. Full Make
  suite passes **227 tests** and the release jar builds, up from baseline **218**: six carried memory
  regressions plus three allocation regressions. They cover unrelated aliases,
  mixed integer/float/pointer contents, reads across allocation, and allocation
  inside loops. A disposable probe also passed **7,000 evaluations across 100
  optimizer seeds**, including empty structs, arrays, both slot conventions,
  partial coverage, and complete scheduling of live definitions. Region/Loop
  golden changes only alter IDs; the nullable-access diagnostic matches 13-14.
  Logs: `build/memory15-baseline.log`, `build/memory-forward-15.log`, and
  `build/memory15-seeds.log`; final tests and release: `build/memory15-final.log`.

- **Memory forward port through Chapters 12-14.** Preserved
  floating-point operations, typed reference initialization, and narrow stores
  while forwarding the reviewed parser, memory nodes, GCM, Return slots, and
  evaluator. Each chapter inherits all six memory regressions. Make tests pass
  **178 / 193 / 213**, up from baselines **172 / 187 / 207**; all three release
  jars build. Updated printed Region/Loop IDs without changing expression
  expectations. The invalid `head.next.i` test in 13 and 14 now reports the
  possibly-null `i` access first; both accesses remain invalid, and the test
  still requires rejection. Logs: `build/memory-forward-baselines.log` and
  `build/memory-forward-12-14.log`. The subsequent allocation port is recorded above.

- **Control/memory slot convention (10a, 10b, 11).** Return now takes
  `{ctrl, $mem, result}` and describes its result tuple in that order, with
  whole memory typed as `TypeMem.BOT`. Evaluators use `expr()` rather than a
  literal result index. Updated viewer roles, existing projection checks,
  documentation, and numbered diagram edges. Make tests pass **156 / 159 / 174**
  respectively, and all three release jars build. Log:
  `build/return-memory-slots.log`. Preserve control slot 0 and memory slot 1
  when forwarding Call, CallEnd, and other nodes in later chapters.

- **Chapter 11 memory forward port (2026-09-29, review checkpoint).** Parser,
  Start, Scope, and Return now use 10b's one-memory model and its three memory
  nodes. GCM schedules MemMerge as a dependency node, filters both Load
  readiness and anti-dependencies by alias, and retains the full Store placement
  range needed by the evaluator. Synthetic never-taken loop returns remain
  scope-free. Updated the evaluator, viewer Return edge roles, walkthrough
  diagrams, and scheduling code excerpts. The baseline passed **168 tests**;
  the port passes **174**, including all six forwarded memory regressions.
  Fifty existing printed expectations changed only Region/Loop node IDs.
  Five memory programs pass seven inputs across 100 optimizer seeds, with
  scheduling assertions and final heap/value checks (**3,500 evaluations**).
  Make also builds the release jar and regenerates all six updated walkthrough
  diagrams; `git diff --check` passes.
  Logs: `build/memory-forward-11-baseline.log`, `build/memory-forward-11.log`.
  Chapters 12-24 are untouched pending review.

- **Chapter 10a/10b split (2026-09-29).** Replaced `chapter10` with two
  independently buildable snapshots: whole-memory SSA in 10a, then lazy
  `MemMerge`/`MemPhi`/`BulkMemPhi` partitioning in 10b. Updated Make discovery,
  Maven modules, IDEA descriptors/local module list, chapter navigation,
  linear-history link handling, and regenerated the memory diagrams.
  Fresh Make builds pass **156 tests in 10a and 159 in 10b**, including the
  chapter-local fuzzer regression wrapper. Both release jars and tags build.
  Offline Maven tests pass in both modules. Five memory programs each pass
  seven inputs across 100 optimizer seeds in each compiler (**3,500 executions
  per chapter**). Seed 97's missed bulk-Phi revisit is retained as a regression.
  Logs: `build/chapter10-clean-build.log`, `build/chapter10-maven.log`,
  `build/10a-seeds.log`, and `build/10b-seeds.log`. At this checkpoint Chapters
  11-24 had not yet adopted the new memory representation; forward-port progress
  is recorded above.

This is a condensed completion record, not a list of current suite counts for
old revisions. Earlier detailed traces are in Git history; durable invariants
have been consolidated into the [AI notes](../skills/chapter25-codex-notes.md).
Unresolved reproductions have been promoted to the pending queue above.

On 2026-09-28, the constructor/memory-check backport passed the full Make suites
in every compiler snapshot from 10 through 24, including their default fuzzer
wrappers (`build/error-backport-tests.log`). The destination baseline was also
green (`build/error-backport-baseline.log`). The arithmetic contract experiment
and its separate validation are recorded independently.

| Completed work | Scope and evidence retained |
|---|---|
| Narrow C integer returns | On 2026-09-28, instruction selection in 22-25 sign/zero-extends direct C call results according to the declared 8/16/32-bit return type. x86 uses MOVSX/MOVZX/MOVSXD or a 32-bit MOV; ARM uses SBFM/UBFM; RISC-V uses shifts, including zero-extension of its sign-extended u32 ABI result. Chapter 25 consults external function identities because optimization can replace ExternNode with an ordinary constant. Native regressions run both x86 calling conventions with garbage upper return bits; ARM/RISC-V stubs exercise signed/unsigned boundaries and unchanged i64 results. Chapter 25's errno test now also requires `close(-1)==-1`. Original selectors reproduce failures in 22 and 25, including the libc case (`build/c-return-negative-{22,25}.log`). Full Make suites passed: 22 (433), 23 (455), 24 (485), 25 (479), plus each default fuzzer wrapper; 25 rebuilt sys.o (`build/c-return-final2.log`). |
| ARM bitfield emulation | SBFM/UBFM decoding now handles both immediate fields, covering signed/unsigned extracts as well as ASR/LSR/LSL. The previous emulator treated all SBFM as ASR and all UBFM as LSL, hiding a logical-right-shift error and rejecting the new extension results. Introduced the correction and independent instruction-word regression in 21 and forwarded through 25. Full 21 suite passed (412 tests plus fuzzer; baseline 411); 22-25 validation is included above. |
| FunPtr lifetime and callable entries | On 2026-09-28, introduced Return-linked FunPtrNode in 22-24, including semantic constant folding. Direct call targets retain existing call-graph treatment; all other address uses conservatively retain unknown callers, without escape analysis. Stop retains each callable Return through instruction selection; machine operands omit the lifetime edge. Anonymous/library returns failed relocation on all three targets before the change; 24 also failed named returns. New tests call returned anonymous/named/library pointers on ARM/RISC-V and from native C, and check discarded anonymous bodies disappear. Full Make suites passed: 21 (411), 22 (430), 23 (452), 24 (482), each plus its fuzzer wrapper. Baselines passed at 410/427/449/479. Chapter 24's Phi-selected functions retain general parameter types; graph expectations reflect that conservative rule. |
| Function-address backend and worklist corrections | RISC-V TFPRISC now patches ADDI at opStart+4, rounds AUIPC for signed low bits, and reports its actual eight-byte size (21-24). ELF export emits anonymous bodies without exported names (21-24). Actual invocation exposed both bugs after retention was repaired; Chapter 21's reduced conditional-return probe trapped before the encoding fix. Chapter 22 gains the later inlining dominator null guard. In 24, killing Call#618 reduced FunPtr#540 to one use without waking dependent CallEnd#560; Node.kill now wakes the remaining definition's dependencies after removing a use (24-25). The no-return String workload loses unused hash bodies; its spill expectations become zero on all targets. Chapter 24 spill-stats passes all 212 original compilation entries at 1,351 moves / 3,115 weighted moves; its README separates this measurement from the original allocator ablation. Full Chapter 25 Make validation also passed: 476 ordinary tests plus its 17-seed fuzzer wrapper, with sys.o rebuilt. |
| Chapter 25 direct C data bindings | On 2026-09-28, full `make tests` passed after the Field/ExternOffset refactor: 476 ordinary tests plus the fuzzer wrapper (17 seeds). Coverage checks zero layout space for external fields, native shared integer/float storage across C calls, compound updates, source/precompiled imports sharing aliases and preserving storage metadata, ARM/RISC-V emulated address relocation and signed loads, and the errno accessor after a failed native call. Direct bindings remain in 25: 22-24 have different singleton-pointer constant and instruction-selection contracts. Serialization retains magic C0DE; the changed layout requires rebuilding all objects. Only the independent RISC-V fixes below were selected for backport. |
| RISC-V right shifts and pointer relocation | Backported from 25 to 22-24 on 2026-09-28. EvalRisc5 selects arithmetic/logical right shifts correctly; TMPRISC rounds the AUIPC high part to account for signed ADDI low bits. New regressions cover immediate/register shifts and forward/backward relocation boundaries. Both fail before the Chapter 22 fixes (arithmetic shift becomes logical; delta 0x800 lands 4 KB low). Full Make suites passed: 22 (427), 23 (449), 24 (479), each plus its fuzzer wrapper. Baseline suites also passed (425/447/477). |
| Chapter 25 Load fuzzer recheck | At `8212a66f`, seed `-4628356252269023530` passes its direct fuzzPeepsRegression replay (`build/load-seed-recheck.log`). The complete `make fuzzer` wrapper also passes all 17 seeds on 2026-09-28 (one JUnit wrapper test). The seed remains a regression; OPEN_FAILING_SEEDS is empty. No new compiler fix was needed. The older Load TODO report is superseded. |
| Arithmetic high results at the SCCP boundary | Chapter 24 integer and float arithmetic now return global TOP for high operands, matching 25. Widened ArithNode.compute's Java return signature to Type; no caller requires TypeInteger. Retained the typed integer fallback for unresolved/error operands, for the parser dependency in the pending queue. The TOP change starts with SCCP in 24; earlier snapshots retain their typed contracts. Full Chapter 24 Make suite passed: 477 tests plus its fuzzer wrapper (`build/arithmetic-top-full-24.log`). |
| Required-field and memory-check backport | Chapters 16-24 retain allocation-time checks for inline `new S { ... }` initialization. Chapter 17 had its TOP/unset-field check commented out; restored it. Expanded the existing missing-initializer regression through 24 (introduced its array-field cases in 16), and forwarded the known-null load regression to 10-24 using their existing parse-time diagnostic. Added the fresh-allocation zero-fold monotonicity assertion in 19-24 and the TOP-pointer store-diagnostic guard in 18-23 (already present in 24). Before 18, TOP pointers already produce a diagnostic before the store's pointer cast. Chapter 25's constructor-exit snapshots and implicit-constructor declaration check stay with its separate allocation/constructor model. Its CallEnd return and MemMerge load-dependency corrections already have equivalent behavior in the applicable earlier implementations; storage-type nullability machinery stays with 25. Global error-result type changes are paused in the queue above. |
| Chapter 25 required constructor fields | Parser records each explicit constructor's merged exit field types by Var identity and checks them against the complete field list when the struct closes. With no user constructor, declaration defaults are checked as an implicit empty constructor. Never-returning constructors have no completion obligation; nested blocks retain constructor context. Declaration-body assignments to required fields now emit their initializing stores. Load's fresh-allocation zero fold is restored with a monotonicity guard. Existing constructor tests cover partial/early returns, shadowing, later fields, valid defaults and non-returning constructors; older unused-struct/String fixtures now provide constructors, with measured spill expectations updated. Seed `6506797708065910879` is rejected during parsing and passes its regression. On 2026-09-28, all 472 non-fuzzer tests passed across `build/ctor-check-tests.log` and `build/ctor-remaining-tests.log`; the full Make run remains red solely on the independent Load TODO seed above. Allocation-time initialization checking was lost in the August 10 rewrite (`51f1f8f4`); this restores the obligation in the parser without depending on optimized private memory. |
| Chapter 25 null-dereference diagnostic | Existing `Chapter10Test.testNullGuardErrors` includes `if (p == null) return p.x; return -1;`. The earlier recursive constant-fold error guard was removed by Cliff in favor of erroneous nodes computing BOTTOM, with related eager-folding and monotonicity corrections. The earlier full-suite pass in `build/null-deref-arith-top.log` applies to the superseded guard implementation, not the current edits. The exposed uninitialized-field failure is fixed by the constructor checks recorded above. |
| Chapter 22 String without an explicit return | Backported 23's default-main teardown: when top-level control falls through, replace main's Start input with XCTRL and queue its users. Previously 22 omitted the return while retaining live control, leaving loop fragments with missing exits. Current seed 0 reproduced a missing-successor failure in Encoding; the earlier GCM changes had moved it past the originally recorded failure. The unchanged `Chapter20Test.testString` now also checks seed 0 through Encoding on x86/RISC-V/ARM, preserving its existing seed-123 allocator checks. Two Chapter09Test graph expectations now reflect removal of a default main with no live return; their sources are unchanged. Baseline and final full Chapter 22 suites passed (425 tests plus fuzzer), logged in `build/string-baseline.log` and `build/string-final.log`. Chapters 23-24 already contain this teardown; no later compiler changes were needed. |
| Infinite-loop evaluation | Eval2 now runs the missing loop-tree phase in 21 and explicitly follows NeverNode projection 0 in 21-25. Existing `testFcn9` expects timeout in 21-24. Full 18-25 Make suites passed on 2026-09-26 (`build/never-exit-tests.log` and `build/never-exit-final.log`). On 2026-09-27, Cliff requested removal of the frozen-source fixture, replay tests, related reductions, and seed-list entry as unnecessary test overhead; the evaluator fixes remain. |
| Always-on peepholes | Removed the global disable flag and branches in 2-18, the Chapter 18 parse overload, test toggles, and transpiler metadata/Rust output. Updated early graph expectations and READMEs; removed duplicate no-peephole scope tests. Chapter 8 fuzzing checks compilation/evaluation for failures; 9-18 compare worklist seeds 123/456 like later chapters. The Chapter 18 Load type workaround was unnecessary with normal forwarding and was removed. All 1-25 default Make suites passed across `build/always-peeps-tests.log` and `build/always-peeps-final.log`; later evaluator corrections are recorded above. The baseline was green in `build/always-peeps-baseline.log`. Direct checks exercised both worklist seeds in 9-18. The transpiler compiles and its focused grammar/Rust-output check passes; its existing repository-wide test stops at source-free chapter00 with `no source files`. |
| B10 and integer/constant-array widening | Widening starts in 24. Both 24-25 now use levels 0-3, dual widening `3-widen`, retained singleton widening, and widening on TypeConAry. `nonZero` preserves the coordinate. The same TypeTest regression and integer gather ranges run in both snapshots; before the backport, Chapter 24 fails the widening regression and lattice associativity. Loop Phis widen only below-center nonconstant ranges: widening an above-center range to level 1 then falling to zero at level 0 violated SCCP monotonicity in 24's existing `testCloneAnd` and `testInfinite`; both now pass, with the guard also in 25. |
| TypeConAry API backport and validation | 24-25 share the reduced raw `_make(any,widen)` API, integer meet dispatch through the distinct `TCONARY` tag, and gathered samples. Preserve 24's GLB hooks and ordinary byte stream; scalar envelopes and serialization stay in 25. Chapters 22-23 consolidate gather and remove redundant wrappers/arguments, retaining their pre-widening representations and needed sentinels. Full Make suites passed on 2026-09-26: 22 (428), 23 (450), 24 (480), 25 (474), each plus its fuzzer wrapper; all include TypeTest, and 25 rebuilt `sys.o`. Logs: `build/conary-backport-*` and `chapter25/build/conary-backport-25.log`. |
| #254 subtraction zero identities | Require integer operands in 4-25 and resolved integer mode in 25. Preserve floating-point signed zero and subtraction diagnostics. `Chapter12Test.testSubZeroFloat` runs in 12-25; `Chapter22Test.testSubZeroTypeError` runs in 22-25. Against the original PR, the signed-zero test fails in 25 and the diagnostic test fails in 22-25. Full Make suites passed in every chapter 4-25 on 2026-09-25, including fresh `sys.o` compilation in 25. |
| Short type extrema | Memory, struct and memory-pointer BOT/TOP names from 10; function-pointer names from 18. Exact types use shortcuts in ordinary, nested and HTML prints; precise types retain their details. Full Make suites in 10-20 and type/frontend suites in 21-25 passed; canonical/nested print checks passed in 10-25. |
| Integer result types | Integer-op fallbacks in 4-13 and remaining unary cases through 17 retain integer types; 5's Phis meet their data-input types. Full Make suites in 4-17 passed, plus a chapter 5 snapshot check of Phi/Add types. Integer BOT starts in 4; later arithmetic already has typed fallbacks, with unresolved bimorphic modes preserved in 25. |
| B14 cyclic leaf-kind equality | 23-24 backport; 25 already correct. Cyclic equality starts in 23. `TypeTest.testCyclicLeafKinds` fails before the fix; earlier interned-child equality already checks kinds. Full affected suites passed. |
| B13 / #246 null guards | Nested Not/null guard discovery from 10; short-circuit Phi availability in 23-24. Regression and full suites in 10-25 passed. Separate Chapter 25 null-dereference diagnostic is recorded above. |
| B09 and #251 debug printing | Side-effect-free print/accessor paths from their first applicable chapters; `_` diagnostic naming, type interning allowed, layouts forbidden. Dead nodes use `isDead()`, not null `_inputs`, from 7. Original-printer negative checks and full affected suites passed. |
| #247 emulator stores | Full eight-byte writes in ARM/RISC-V emulators, 21-25. Independent byte-pattern/overwrite regressions failed before the fix; all affected suites passed. |
| B12 x86 immediate multiply | Dedicated destination-register encoder in 21; 22-25 already had it. Low/extended-register encoding regressions and full 21-25 suites passed. |
| B11 optimized return checking | Remove parse-time meet in 18-24; 18 checks the optimized result type. Earlier separate Returns already accept dead mixed-type exits. Regressions start in 10/12; full 10-25 suites passed. |
| Dominator caches | Shared searches from 6; char depth in 6-17, separate char depth/version from 18, checked overflow and preserved clone fields. Inlining invalidation in 18-20. Full affected suites passed; dedicated bookkeeping helpers/tests were removed at Cliff's request. |
| GCM, constant cloning, isPinned removal | Shared scheduling from 11, per-function cloning of stacked constants from 18; preserve chapter-specific anti-dependencies and linked Parm ownership. Full 11-25 suites passed. Keep clone-based copyEmpty and non-final graph fields per review. |
| TypeFunPtr normalization | Trailing-default normalization in 23-24; 25 already correct. Only gather cases added; lattice laws and full 23-25 suites passed. Other `7f3b8856` representations remain in 25. |
| Inlining Return types and ARM execution | Live return expressions outlast a deleted inline entry (20-25); ARM register SUB, call instructions, frame byte counts/alignment, and vector growth (21-25 as applicable). Reduced negative cases and full affected suites passed. The earlier standalone ARM String/hash failure is resolved. |
| Native test reliability | Full process exit status, stdout plus successful execution, and distinct concurrent HelloWorld artifact names. Crashes no longer pass merely because output matches or an exit status narrows to zero. |
| Allocator progression 20-25 | Fixed cohorts, legality checks, native/emulated execution from 21, and same-compiler comparisons. Final results below. |
| Build/fuzzer harness baseline | All 25 chapter targets passed after build setup and explicit seed lists. Empty default lists were not exploratory fuzz coverage. |

Final allocator audit, 2026-09-20, Windows/Cygwin with assertions:

| Snapshot | Full suite | Spill report |
|---|---|---|
| 20 | 378 tests + fuzzer | 39 compilations, 238 / 357 |
| 21 | 408 tests + fuzzer | 91 compilations, 840 / 1,456 |
| 22 | 422 tests + fuzzer | 115 compilations, 838 / 1,496 |
| 23 | 444 tests + fuzzer | 145 compilations, 933 / 1,745 |
| 24 | 473 tests + fuzzer | 212 compilations, 1,332 / 2,956 |
| 25 | 468 tests across parallel groups, including fuzzer | 222 compilations, 2,597 / 5,579 |

All full suites and final spill reports passed. The Chapter 25 run rebuilt
`sys.o`. Negative checks against saved original classes reproduce fixed-neighbor
bias, narrow-store masks, and ARM floating-memory encoding failures. The original
Newton ARM allocation also failed before the fix. The Chapter 21 coalescing-off
comparison passes all allocation/runtime checks but reports nine changed spill
goldens, intentionally exiting nonzero. The Chapter 25 earlier-ranking ablation
fails fresh library allocation, so its client-only totals are explicitly partial.
Logs and saved original classes: `chapter{20..25}/build/regalloc-final/`.
