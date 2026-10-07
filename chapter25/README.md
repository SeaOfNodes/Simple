# Chapter 25: Modules, Separate Compilation, and SSA Construction

English | [日本語](README.ja.md)

[Previous: Chapter 24](../chapter24/README.md) |
[Chapter index](../README.md#chapters)

This chapter compiles Simple source files into reusable object files, loads
their types and ideal IR into later compilations, and links native programs
against a separately built system library. It also contains a substantial
parser, type, and memory-SSA redesign to support incomplete information.

These are the contents of the current snapshot. Independent correctness fixes
will move to earlier chapters first; moving the larger SSA redesign backward,
or splitting this chapter, is deferred. See the [backport queue](../docs/chapter-backports.md).

The memory work from Chapters 21-24 carries forward here: Load search proves
all arms of a merge before moving a read out of a loop, Phi factoring checks
memory clobbers, and scheduling follows aliases through memory aggregates.
The search follows `EscapeNode` to private or public memory and waits for
unresolved Store aliases. Array-length reads have no element bounds check;
array-element reads retain control. Integer value Phis use the existing typed
conversion machinery to preserve the load's width across loop widening.
New still produces private constructor memory, with publication handled by
Escape; the earlier chapters' allocation-input design does not replace it.

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter25)
in the [linear history](https://github.com/SeaOfNodes/Simple/tree/linear) and
[compare it with Chapter 24](https://github.com/SeaOfNodes/Simple/compare/linear-chapter24...linear-chapter25).

Here is the [complete language grammar](docs/25-grammar.md) for this chapter.

## Compilation units and names

`CompUnit` represents a source or object file. `ParseAll` discovers dependencies,
parses needed sources, and loads previously compiled units. Source and build
trees use corresponding relative paths; class names use dotted paths. For
example, `a/b/foo.smp` supplies the namespace `a.b.foo`.

Each unit has Start/Stop nodes. A function's ownership by a compilation unit is
distinct from an edge representing unknown external callers: retaining code
for emission must not imply that arbitrary callers can reach it.

The driver accepts `--root` for the project root, `-o` for output, and `-L` for
external Simple object search paths, including an object file or a directory.
The compiler also resolves external C symbols for native linking. Names beginning
with `_` control privacy; public reachability depends on enclosing names too.

Explicit numeric C declarations bind directly to mutable native storage:

```java
i32 counter = "C";
counter += 1;
return counter;
```

The native linker resolves `counter`. Reads and writes access that C variable;
the declaration neither copies nor initializes it. Repeated declarations and
module imports share the same storage alias. Integer and floating-point data
bindings are supported; pointer and aggregate data bindings are not yet supported.
External fields carry a storage flag and occupy no bytes in their declaring
class. Layout resolves their field offsets to `ExternOffset` symbols; instruction
selection replaces class-base-plus-offset with the native symbol address.
Native object output uses the existing AMD64 ELF writer; ARM and RISC-V support
these addresses through the in-memory linker. Thread-local variables and C macros
need accessor functions: the system library exposes `libc.errno()` through a small
native runtime accessor.

## Object files contain ideal IR

The ELF writer stores native code and a `.simple` section containing canonical
types, ideal graph nodes, and dependencies. The reader reconstructs that graph
so imported functions can participate in optimization, including inlining.
Calls that remain out of line are resolved by native linking.

`GlobalBits` maps file identities and per-file indices to local dense indices
for aliases, function targets, and return points. Equal local numbers from
independent compilations must not be mistaken for equal identities. Types are
closed and upgraded after loading, including cross-unit cyclic references.

Serialization preserves declaration types separately from sharpened flow types.
Every semantic node/type field needs matching read/write support. The source's
expensive serialization bijection check is currently disabled, and the larger
incremental-rebuild test `testModule0` is ignored. Neither is an active
validation guarantee.

The external-field flag and symbolic offsets change the serialized layout.
The magic remains `C0DE`; there is no separate format version yet. Rebuild all
Simple object files before importing them with this compiler.

## Constructing SSA before types are complete

Simple still parses directly to SSA without an AST. Forward references mean an
expression's eventual type may be unavailable while parsing it. Syntax and
resolved lexical binding therefore choose topology; types subsequently refine,
optimize, and validate the graph.

- Arithmetic nodes defer integer versus floating-point mode selection, including
  resolution through recursive graph components after SCCP.
- Calls preserve their syntactic receiver slot before the callee kind is settled.
  Symbolic field information defers layout-dependent decisions.
- Loads and ordinary Phis no longer carry parser-supplied lower-bound types.
- `TypeScalar` represents integer, float, memory-pointer, and function-pointer
  values separately from global control/memory bottom.
- `TypeStruct._open` describes an incomplete field set; `_fref` records the
  absence of an authoritative definition. Discovering fields does not define a type.
- Guards carry branch-proven zero/nonzero facts before the input family is known.
  `FunPtrNode` retains an explicit edge to its function's return graph.

The driver runs parsing, pessimistic iteration, SCCP, and final type checking
before serialization and machine selection. Nontrivial inlining is coordinated
with iteration instead of always occurring as an immediate peephole.

## Memory and construction

The parser carries ordinary bulk memory. The optimizer recovers precise aliases
using `MemMerge`, `MemPhi`, and `BulkMemPhi`. A bulk Phi covers all aliases
except its exclusions, represented by parallel precise Phis. Every slice must
be covered exactly once.

User constructors are declared and called as follows:

```java
struct Box {
    i64[] values;
    new Box = { i64[] initial -> values = initial; };
};
return new Box(new i64[1]);
```

An allocation helper is generated at the declaration. It allocates, calls the
hidden `<init>` and then the user constructor, and publishes initialized private
memory through `EscapeNode`s. Parser `Var` metadata detects early field reads
and missing initialization on constructor exits. Store widths come from target
declarations, not from the values being stored.

File-level code is represented by a class initializer (`<clinit>`), distinct
from instance initialization. The older [module design notes](module.md) and
[roadmap](ROADMAP.md) contain proposals and alternatives, especially concerning
initialization order; they are not a specification of all implemented rules.

## System library and examples

The former single `sys.smp` library is organized into units for libc bindings,
I/O, characters, scanners, arrays, growable buffers, and bitsets. The native
driver supports real program arguments. Examples in `docs/examples` include
Bubble Sort, Capitalize, Dijkstra, and FileIO.

`Chapter25Test` exercises library loading and native linking, including a Hello
World call deliberately kept out of line. That test checks the client's external
symbol before linking against `sys.o`. Other tests cover forward constructors,
field updates through calls, literal escapes, and reduced compiler failures.
Independent regressions are candidates for earlier chapters.

## Building and checking

From this directory, `make lib` supplies Java test dependencies and `make tests`
runs the raw chapter suites, standalone suites, system tests, and fuzzer
regressions. `make release` builds the compiler jar and native library artifacts;
`make tags` builds editor tags. Native tools and the selected CPU/ABI must match
the environment; the Makefile currently defaults to x86-64/win64.

Serialized IR uses the `C0DE` header; pointer types include an independent
read-only access bit. The format is not versioned yet, so rebuild object files
after format changes. Backward compatibility is not currently supported.

Compiler changes can invalidate both serialized IR and native code in `sys.o`.
Rebuild it before interpreting linked-program test results. A source-only subset
is not equivalent to this chapter's full `make tests`.

## RegAlloc improvements: live-range area and split cost

Chapter 24 tried cold copies before splitting a loop Phi's hot edges. This
chapter also estimates how much scheduled code a spill frees from register
pressure, divided by the estimated cost of the new copies. Callee-save ranges
cover a whole function. Cloneable constants can be rebuilt nearer their uses.
Copies inside loops cost more: the estimate weights them by `8^loopDepth`.
These are inexpensive approximations, especially for values with many uses.

The other Chapter 25 conflict-handling rules remain here: direct splitting of
several fixed-register uses and deeper splits for some multi-definition ranges.
Their size thresholds are heuristics, not general invariants to backport to the
first allocator. Shared legality fixes still start in the earliest affected
chapter. The final audit corrected fixed-neighbor color bias in 20-25, narrow
store masks in 20-21, and ARM register-bank/float-memory encoding in 21-25.

Chapter 21's adjacent-copy forwarding carries forward here as well: a copy
used only by the next instruction is removed when the operand accepts its
source register and is not tied to the instruction's result.

The table below is the complete 2026-10-02 replay, after repairing three saved
String sources with explicit constructors for their required `cs` field.
All 212 historical entries now pass. This establishes a new baseline after the
fixture adaptations and recent compiler corrections; it is not an allocator-only
comparison with the earlier audit. The [backport record](../docs/chapter-backports.md)
retains the older partial comparisons.

Run `make spill-stats`. Each row uses **Chapter 25's compiler**. The first five
rows preserve the earlier 212 program/target entries, adapted to this chapter's
syntax and library organization; the [fixture notes](src/test/java/com/seaofnodes/simple/spill/README.md)
list those adaptations and the measurement limits. They use optimizer seed 123.
The last row includes thirteen recorded allocations from `Chapter25Test` at its existing
seeds plus a fresh system-library compilation at the driver's seed 456. Reusing
`sys.o` must not make that substantial workload disappear from the measurements.
These results are from Windows x86-64; earlier cohorts also include RISC-V/ARM
SystemV and x86 SystemV. The reporter prints per-target totals as well.

| Program cohort | Compilations | Split/move count | Loop-weighted count |
|---|---:|---:|---:|
| Chapter 20 | 39 | 375 | 606 |
| Chapter 21 | 52 | 479 | 1,172 |
| Chapter 22 | 24 | 103 | 103 |
| Chapter 23 | 30 | 129 | 353 |
| Chapter 24 | 67 | 696 | 1,410 |
| Chapter 25 | 14 | 786 | 1,815 |
| **Total** | **226** | **2,568** | **5,459** |

`_spills` counts retained SplitNodes, including register-to-register moves;
`_spillScaled` applies the loop weight. Neither measures just memory traffic.
The old cohorts are allocation replays with register-legality checks, not reruns
of their native harnesses. All 23 `Chapter25Test` tests pass, including their
native/result checks, with no spill-golden failures.
Use `make -j 4 tests` for the full chapter, including inherited emulator tests.

### Comments on the measurements

The earlier allocator audit found no uniform improvement. Substituting Chapter
24's ranking while keeping the rest of that compiler unchanged saved five moves on the 221
client compilations (2,206 / 4,719 versus 2,211 / 4,724), but failed to allocate
the system library within eight rounds. It is therefore an incomplete comparison,
not a lower valid whole-suite total. The existing area/cost ranking is retained;
the round limit and correctness checks are unchanged. The system library itself
contributed 386 moves / 855 weighted moves in that audit; the current replay
measures 373 / 828.

A trial that favored all multi-use cloneable constants saved one move against
the earlier ranking on those clients, but also failed the fresh library build.
It was rejected. This is why both aggregate statistics and complete compilation
and execution checks matter; a favorable subtotal cannot justify a change.

In that earlier audit, the historical-cohort total rose from Chapter 24's
1,332 / 2,956 to Chapter 25's 1,814 / 3,753.
Chapter 25 adds class initializers, module ownership, different escape analysis,
and syntax/library adaptations. These are different machine graphs; the increase
cannot be attributed to allocator quality alone. Compare a heuristic within one
compiler and fixed corpus before drawing conclusions from the chapter tables.
