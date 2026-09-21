# Chapter 21: Instruction Encoding and ELF

This chapter turns the scheduled, register-allocated graph into executable
instructions. Each machine node writes its bytes; the compiler places blocks,
resolves local references, and exports an ELF object for the linker. We can now
run generated code, which also tests instruction selection, register allocation,
and calling conventions together.

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter21)
in the linear revision history and
[compare it to Chapter 20](https://github.com/SeaOfNodes/Simple/compare/linear-chapter20...linear-chapter21).

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

The targets share this driver, but their machine nodes own the bit details.

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
The implementations live under
[node/cpus](src/main/java/com/seaofnodes/simple/node/cpus).

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

`make tests` includes C harnesses that link and run generated x86 code, and
RISC-V/AArch64 emulator checks. The tests compare results as well as spill counts.
Native process failures retain their full exit status. Emulator checks must read
their own result memory; matching another target's result is insufficient.

Run `make spill-stats` to collect allocations from the same tests with assertions
enabled. It reports each test/CPU/ABI, then cohort totals. A spill-golden mismatch
is recorded without skipping the remaining targets or execution checks; any such
mismatch still makes the command fail. Structural allocator regressions are
excluded from the spill totals.

## RegAlloc improvements: conservative copy coalescing

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

Chapter 20's legality and progress fixes carry forward, including compatible
rematerialization and clobber-aware copy reuse. Chapter 21 retains its native
frame and ABI support. Stronger color bias and cheap-spill ranking are reserved
for Chapter 22, popular-use grouping for 23, cold-first loop splitting for 24,
and area/cost ranking for 25.

These are measured sums with **this chapter's compiler**, default optimizer seed
123, on Windows x86-64. A split is a retained `SplitNode`, including register
moves; it is not necessarily a memory spill. Weighted counts multiply each split
by `8^loopDepth`.

| Test cohort | Allocations | Splits | Loop-weighted splits |
|---|---:|---:|---:|
| Chapter 20 | 39 | 379 | 484 |
| Chapter 21 | 52 | 461 | 972 |
| Total | 91 | 840 | 1,456 |

The Chapter 20 row freezes all 13 original inputs, including BrainFuck and
MergeSort, on three SystemV targets. They live in `Chapter20Test`. The revised
sources/ABI cases previously in that file are retained in `Chapter21AllocTest`;
together with `Chapter21Test` and the native BrainFuck/MergeSort tests they form
the Chapter 21 row. That row includes 17 ARM/SystemV, 17 RISC-V/SystemV, eight
x86/SystemV, and ten x86/Win64 compilations. Native host ABI changes affect this
row; compare the same host and targets.

| Controlled comparison, same 91 compilations | Splits | Loop-weighted splits |
|---|---:|---:|
| Current compiler with coalescing disabled | 1,070 | 1,889 |
| Current compiler with coalescing | 840 | 1,456 |

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
