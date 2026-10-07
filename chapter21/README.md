# Chapter 21: Instruction Encoding and ELF

English | [日本語](README.ja.md)

[Previous: Chapter 20](../chapter20/README.md) |
[Next: Chapter 22](../chapter22/README.md)

This chapter turns the scheduled, register-allocated graph into executable
instructions. Each machine node writes its bytes; the compiler places blocks,
resolves local references, and exports an ELF object for the linker. We can now
run generated code, which also tests instruction selection, register allocation,
and calling conventions together.

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter21)
in the linear revision history and
[compare it to Chapter 20](https://github.com/SeaOfNodes/Simple/compare/linear-chapter20...linear-chapter21).

Here is the [complete language grammar](docs/21-grammar.md) for this chapter.

## From machine nodes to bytes

Instruction selection chooses the operation and register constraints. Register
allocation assigns physical registers and stack slots. Encoding then combines
the opcode, assigned registers, and immediate operands into instruction bytes.
An instruction cannot choose a different register at this point: the surrounding
instructions already depend on the allocator's choices.

[Encoding.java](src/main/java/com/seaofnodes/simple/codegen/Encoding.java) owns the
byte stream and records each node's offset and encoded length. Its driver:

1. Lays out basic blocks and inserts or reverses branches as needed.
2. Calls `MachNode.encoding(Encoding)` in scheduled order.
3. Settles short and long instruction forms.
4. Patches references whose targets are inside this compilation.

The `add1`, `add2`, `add4`, and `add8` helpers append little-endian values. Encoding
functions obtain allocated registers through `Encoding.reg`. Stack operands use
the function's frame layout, including space for saved registers and outgoing
arguments. A successful coloring alone does not establish a correct calling
convention; the generated program must preserve the caller's values too.

## Three instruction sets

The targets share this driver. Machine nodes resolve compiler operands, then
call the shared [ISA encoders](../isa/README.md).

| Target | Main encoding concerns |
|---|---|
| RISC-V | Register and immediate fields in instruction words; larger constants and addresses need multiple instructions. |
| AArch64 | Register forms, restricted immediate encodings, and multi-instruction constant construction. |
| x86-64 | Variable instruction lengths, register prefixes, addressing forms, and short versus long branches. |

For example, a small integer can fit directly in an instruction, while a large
one may require several instructions or a load from a constant pool. Consequently,
even a target with fixed-width instructions can emit different numbers of bytes
for different machine nodes.

The [encoding reference](docs/encoding-reference.md) retains the detailed bit
layouts, worked examples, and ISA links. Those details matter when implementing
a particular instruction, but do not change the compiler pipeline described here.
The machine-node wiring lives under
[node/cpus](src/main/java/com/seaofnodes/simple/node/cpus). The shared
[X86 encoder](../isa/src/main/java/com/seaofnodes/isa/X86.java) owns x86 byte
emission; [Arm64](../isa/src/main/java/com/seaofnodes/isa/Arm64.java) and
[RiscV](../isa/src/main/java/com/seaofnodes/isa/RiscV.java) pack instruction words.

For example, `AddX86` chooses opcode `0x03`. Its `RegX86` base reads the two
allocated registers and calls `X86.reg(enc,opcode(),dst,src)`. The shared method
writes REX.W, the opcode, and ModRM directly into `Encoding`'s byte buffer.
For `add r9,r10`, the bytes are `4d 03 ca`. Register constraints and the
two-address requirement remain on the chapter's machine node. This separates
the decision to use ADD from the fixed bit representation of that instruction.

Loads use the same boundary: the chapter resolves width, sign extension, and
register bank from its types and allocated operands. `X86.load` receives those
facts and the concrete address; it has no dependency on the chapter's graph or
type lattice. Later chapters reuse these encodings as their compiler evolves.

ARM and RISC-V execution tests also reuse the evaluators in `isa/src/test-support`.
The chapter's test harness prepares and links a memory image; the evaluator
executes its bytes. This keeps instruction semantics in one place while image
construction follows each chapter's compiler. They compile with the tests;
Make's release jar continues to bundle the test harness as well as the compiler.

## Relocation and ELF

A forward branch cannot know its final displacement when its bytes are first
written. Its target's offset depends on all the intervening encodings. Shortening
or expanding another branch can move that target again. The layout pass settles
instruction sizes before local relocations patch the final displacements.

External references need a later patch. For example, the compiler can emit a
call to `malloc` without knowing where the linker will place it. The object file
records the symbol, patch location, and relocation kind. That kind must agree
with the instruction: an x86 call displacement and a split RISC-V address occupy
different fields and cannot use the same patch operation.

[ElfFile.java](src/main/java/com/seaofnodes/simple/codegen/ElfFile.java) writes the
object's code, data, symbols, and relocation records. Large constants introduce
the same placement problem as external calls: their eventual addresses must be
connected to the instructions that load them. Local relocation and object-file
relocation are two stages of the same obligation to make references correct.

## Execute the result

Chapter 11's lazy memory partitioning now carries through encoding. Parameters,
calls, and returns carry whole memory; `BulkMemPhi`, `MemPhi`, and `MemMerge`
discover precise aliases inside each function. New consumes `{ctrl, $mem, size}`
and produces `{ptr, $mem}`. Its memory covers the allocated struct's aliases;
the surrounding merge retains unrelated aliases. Nonzero initialization still
uses explicit Stores. Memory nodes and ordering edges require no registers.

Scheduling follows aliases through aggregates to order reads before matching
Stores, allocations, and calls. Calls retain their argument lists, with no added
ordering operands. Synthetic loop exits collect precise memory by alias.

The execution regressions cover constructors, non-inlined and recursive calls,
inlining, and array updates, using native x86 and both emulators. A full-width
x86 load uses the allocated register bank when choosing its encoding, since an
integer may reside in XMM. ARM emulation handles MOVZ/MOVK halfword construction
for the large constants in these checks.

`make tests` includes C harnesses that link and run generated x86 code, and
RISC-V/AArch64 emulator checks. The tests compare results as well as spill counts.
Native process failures retain their full exit status. Emulator checks must read
their own result memory; matching another target's result is insufficient.

Run `make spill-stats` to collect allocations from the same tests with assertions
enabled. It reports each test/CPU/ABI, then cohort totals. A spill-golden mismatch
is recorded without skipping the remaining targets or execution checks; any such
mismatch still makes the command fail. Structural allocator regressions are
excluded from the spill totals.

## RegAlloc improvements: eliminating copies

A split inserts a copy between two live ranges. If both can safely become one
range, the copy can disappear before coloring. [Coalesce.java](src/main/java/com/seaofnodes/simple/codegen/Coalesce.java)
checks that the ranges do not interfere, intersects their allowed-register masks,
and combines their neighbors. It merges only when the resulting neighbor count
is smaller than the number of available registers. This simple conservative rule
preserves a color choice even if every neighbor takes a different register.

Merging also updates neighboring adjacency lists and removes sample references
to the deleted copy. A rejected merge restores the original adjacency list.
Mask compatibility alone is insufficient: two values that are simultaneously
live cannot share a register merely because both permit it.

After coloring, a copy with one use in the immediately following instruction
can also disappear when that operand accepts the source register and is not
its two-address operand. For example, `mov rpc=s8; st1 [rpc+4],s1` becomes
`st1 [s8+4],s1`. Adjacency proves that nothing overwrites the source in between.
Phi and control-flow users are excluded. This local cleanup leaves spill
selection unchanged; it complements coalescing without another CFG analysis.

Chapter 20's legality and progress fixes carry forward, including compatible
rematerialization and clobber-aware copy reuse. Chapter 21 retains its native
frame and ABI support. Stronger color bias and cheap-spill ranking are reserved
for Chapter 22, popular-use grouping for 23, cold-first loop splitting for 24,
and area/cost ranking for 25.

These are measured sums with **this chapter's compiler**, default optimizer seed
123, on Windows x86-64. A split is a retained `SplitNode`, including register
moves; it is not necessarily a memory spill. Weighted counts multiply each split
by `8^loopDepth`.

| Program cohort | Compilations | Retained moves | Loop-weighted moves |
|---|---:|---:|---:|
| Chapter 20 | 39 | 380 | 576 |
| Chapter 21 | 52 | 467 | 1,076 |
| **Total** | **91** | **847** | **1,652** |

The Chapter 20 row freezes all 13 original inputs, including BrainFuck and
MergeSort, on three SystemV targets. They live in `Chapter20Test`. The revised
sources/ABI cases previously in that file are retained in `Chapter21AllocTest`;
together with `Chapter21Test` and the native BrainFuck/MergeSort tests they form
the Chapter 21 row. That row includes 17 ARM/SystemV, 17 RISC-V/SystemV, eight
x86/SystemV, and ten x86/Win64 compilations. Native host ABI changes affect this
row; compare the same host and targets.

Before adjacent-copy forwarding, the memory port changed the previous total of 840 / 1,456 to 845 / 1,559,
with identical cohort membership and allocator heuristics. These are all changed
rows; each entry is retained / loop-weighted splits:

| Cohort / program | Target / ABI | Before | Lazy memory |
|---|---|---:|---:|
| 20 / MergeSort | ARM / SystemV | 44 / 44 | 41 / 41 |
| 20 / BrainFuck | RISC-V / SystemV | 35 / 42 | 37 / 58 |
| 20 / Alloc2 | x86 / SystemV | 3 / 3 | 4 / 4 |
| 20 / Alloc2 | ARM / SystemV | 10 / 10 | 9 / 9 |
| 20 / String | x86 / SystemV | 7 / 14 | 8 / 15 |
| 20 / String | RISC-V / SystemV | 14 / 14 | 10 / 10 |
| 20 / String | ARM / SystemV | 13 / 13 | 10 / 10 |
| 21 / Sieve | x86 / Win64 | 24 / 178 | 24 / 185 |
| 21 / Sieve | RISC-V / SystemV | 19 / 89 | 22 / 92 |
| 21 / Sieve | ARM / SystemV | 23 / 93 | 21 / 91 |
| 21 / BrainFuck | RISC-V / SystemV | 35 / 42 | 46 / 130 |

RISC-V BrainFuck accounts for most of the weighted increase. A diagnostic run
omitting only read-before-New ordering changes its two rows to 35 / 42 and
37 / 58. That explains most of the cost; the compiler retains the ordering
constraint. No heuristic tuning is included in the memory port.

The subsequent Load-search improvement folds BrainFuck's fixed program length
through the loop's N-way memory merge. Every backedge arm must fold or return
to unchanged memory for the same pointer. It removes 907 executed heap loads
in the RISC-V Hello World run and reduces stack traffic from 1,006 loads / 572
stores to 15 / 15. Before copy forwarding, that row had 144 / 165 moves / weighted moves,
and the complete 91-entry total was 943 / 1,594. Adjacent-copy forwarding
reduces these to 35 / 42 and 821 / 1,444 respectively. The RISC-V Hello World
run executes 21,787 instructions instead of 21,920, with unchanged heap and
stack traffic. These are emulator instruction counts, not hardware timings.
The current native BrainFuck test and all runtime assertions are enabled.

Restoring GCM's anti-dependence dominator walk subsequently changes each
RISC-V BrainFuck row from 35 / 42 to 48 / 146, giving the current table above.
A conditional writer need not dominate a later Load placement, so checking
only the writer's exact block misses required ordering. The restored walk
fixes a read-before-conditional-store program that returned 140 instead of 120.
Both BrainFuck execution checks and the complete 91-entry spill audit pass;
the increased move count is recorded without changing allocator heuristics.

The following coalescing comparison predates lazy memory. Both sides use the
same compiler from that audit; it is not a fresh ablation of the current graph:

| Earlier controlled comparison, same 91 compilations | Splits | Loop-weighted splits |
|---|---:|---:|
| Audit compiler with coalescing disabled | 1,070 | 1,889 |
| Audit compiler with coalescing | 840 | 1,456 |

The controlled run disables only `Coalesce.coalesce` in `graphColor`. Allocation,
register constraints, and runtime checks pass in both runs. The disabled run
reports nine changed spill goldens and exits unsuccessfully, as intended.
Coalescing saves 230 moves (21.5%) and 433 weighted moves (22.9%). Local increases
can still occur; the aggregate, rather than one example, judges the tradeoff.

The final audit backported narrow x86/RISC-V store masks and corrected ARM
register-bank selection and floating-point memory operations. Restricting byte
stores to legal registers lowers RISC-V BrainFuck from 211 to 42 weighted moves
in each of its two cohorts, reducing the previous table by 44 raw / 338 weighted
moves. These correctness fixes are included on both sides of the comparison.
Earlier measurements made with the broader masks are historical, not the current
baseline. Chapter 20's 357 weighted moves are not a coalescing baseline either:
Chapter 21 changes machine lowering and implements native ABI obligations.
