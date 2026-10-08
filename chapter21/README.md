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
checks that the ranges do not interfere, intersects their allowed-register
masks, and combines their neighbors.  It merges only when the resulting
neighbor count is smaller than the number of available registers - meaning it
can always color.

After coloring, a copy with one use in the immediately following instruction
can also disappear when that operand accepts the source register and is not
its two-address operand. For example, `mov rpc=s8; st1 [rpc+4],s1` becomes
`st1 [s8+4],s1`.  Adjacency proves that nothing overwrites the source in between.
Phi and control-flow users are excluded.  This local cleanup leaves spill
selection unchanged; it complements coalescing without another CFG analysis.

For looking at allocator progress, we will keep a table of weighted spill costs
a chapters' tests - and use the later chapter compiler and register allocator
on them.  The later chapters also modify the graph (sometimes adding boiler
plate graph) so its not really a 100% fair comparison of how well this
*heuristic* does, but it does give us some idea that we're not losing ground.

Rows are fixed test cohorts; columns are the compiler/allocator chapters.  Each
cell is the sum of `_spillScaled`: retained split moves, including register
copies, weighted by loopDepth.  Lower is better.  **# tests counts
compilations** (program/CPU/ABI cases), including zero-spill cases, rather than
JUnit methods.  The count and membership of each row stay fixed across columns.

<!-- spill-matrix:start -->
| Test cohort | # tests | Ch 20 | Ch 21 |
|---|---:|---:|---:|
| Ch 20 | 39 | 360 | 576 |
| Ch 21 | 52 |  | 1,072 |
<!-- spill-matrix:end -->

