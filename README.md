# Simple
English | [日本語](README.ja.md)

A Simple showcase for the Sea-of-Nodes compiler IR

This repo is intended to demonstrate the Sea-of-Nodes compiler IR.

The Sea-of-Nodes is the core IR inside of HotSpot's C2 compiler
and Google's V8 compiler and Sun/Oracle's Graal compiler.

Since we are showcasing the SoN IR, the *language* being implemented is less
important.  We're using a very simple language similar to C or Java, but with
far fewer features.  Simple is strongly typed, object-oriented, with first-class
*functions* not *closures*.  Object references are pointers, and null
pointer exceptions are disallowed by the typing system.  Arrays will probably
be range-checked at some point, making Simple a fully safe language.  Simple
has a minimal syntax that can be parsed with a recursive descent parser.

The Sea-of-Nodes is used for machine code generation in these industrial
strength systems - but for this demonstration the backend is both difficult and
less important.  This repo targets x86-64, RISC-V and ARM64 with
ahead-of-time compilation - but with an eye towards JIT compilation.

This repo also is not intended to be a complete language in any sense, and so
the backend starts with a Java evaluator (first introduced in Chapter
8) that directly interprets the SoN IR. Code generation first appears in Chapter
19.


## Chapters

The following is a rough plan, subject to change.

Each chapter will be self-sufficient and complete; in the sense that each
chapter will fully implement a subset of the Simple language, and include
everything that was created in the previous chapter.  Each chapter will also
include a detailed commentary on relevant aspects of the Sea Of Nodes
intermediate representation.

The Simple language is styled after a subset of C or Java.

* [Chapter 1](docs/chapter01/README.md): Script that returns an integer literal, i.e., an empty function that takes no arguments and returns a single integer value. The `return` statement.
* [Chapter 2](docs/chapter02/README.md): Simple binary arithmetic such as addition, subtraction, multiplication, division
  with constants. Peephole optimization / simple constant folding.
* [Chapter 3](docs/chapter03/README.md): Local variables, and assignment statements. Read on RHS, SSA, more peephole optimization if local is a
  constant.
* [Chapter 4](docs/chapter04/README.md): A non-constant external variable input
  named `arg`.  Binary and Comparison operators involving constants and `arg`.
  Non-zero values will be truthy.  Peephole optimizations involving algebraic
  simplifications.
* [Chapter 5](docs/chapter05/README.md): `if` statement. CFG construction.
* [Chapter 6](docs/chapter06/README.md): Peephole optimization around dead control flow.
* [Chapter 7](docs/chapter07/README.md): `while` statement; looping constructs - eager phi approach.
* [Chapter 8](docs/chapter08/README.md): Looping constructs continued, lazy phi creation, `break` and `continue` statements.
* [Chapter 9](docs/chapter09/README.md): Global Value Numbering. Iterative peepholes to fixpoint. Worklists.
* [Chapter 10](docs/chapter10/README.md): User defined structs, pointers and null
  analysis. One memory value in SSA. Loads, stores, and an executable evaluator.
* [Chapter 11](docs/chapter11/README.md): Equivalence class aliasing. Lazy memory
  partitioning with `MemMerge`, `MemPhi`, and `BulkMemPhi`.
* [Chapter 12](docs/chapter12/README.md): Reference fields, forward references and recursive structs.
* [Chapter 13](docs/chapter13/README.md): Global Code Motion - Scheduling.
* [Chapter 14](docs/chapter14/README.md): Numeric types: floats, narrow integers, ranges and rounding to `f32`.
* [Chapter 15](docs/chapter15/README.md): One dimensional static length array type, with array loads and stores.
* [Chapter 16](docs/chapter16/README.md): Constructors
* [Chapter 17a](docs/chapter17a/README.md): Binding mutability, reference permissions, and deep read-only views.
* [Chapter 17b](docs/chapter17b/README.md): Syntax sugar: `var`, `val`, `x+=y`, `for(init; test; next) body`
* Chapter 18: Functions and calls.
* Chapter 19: Instruction selection and portable compilation
* Chapter 20: Graph Coloring Register Allocation
* Chapter 21: Instruction Encodings & ELF
* Chapter 22: A Simple Hello, World!
* Chapter 23: Methods and Types Revisited
* Chapter 24: Chained conditionals and SCCP
* Chapter 25: Modules, Separate Compilation, and SSA Construction with Incomplete Types

## Building across chapters

The optional [interactive graph viewer](graph/README.md) is shared in `graph/`.
Chapters 1–25 launch it with `make view` from the chapter directory.

The [debug printers](print/README.md) are shared in `print/`; Chapter 2 needs only
a one-line expression format. A shared `BaseNode` supplies identity and edge
access to both printers and the viewer; chapter adapters add semantic details
as the IR grows.

The [instruction encoders](isa/README.md) share x86-64, ARM, and RISC-V byte
emission for Chapters 21-25, along with ARM/RISC-V test evaluators. Machine-node
selection, register allocation, and code layout remain in each chapter; the
shared layer takes concrete operands and writes bytes.

The top-level Makefile runs each chapter's `tests`, `tags` (also `tag`),
`release`, or `lib` target. Start with `make lib` on a fresh checkout, then
`make tests`.

CI runs `make lib tests CTAGS=` on Linux with JDK 21. The Make targets also build
Chapter 25's native runtime and `sys` library before running the tests that use
them; Maven's Java build alone does not produce those prerequisites.

The [chapter backport queue](docs/chapter-backports.md) records proposed small
corrections and the per-chapter test/review workflow. Larger architectural moves
are tracked separately there. Chapters 10-14 now group whole memory,
lazy alias splitting, references, GCM, and numeric types in that order.
The memory representation is forwarded through Chapter 25.

To build and test just the memory chapters:

```sh
make lib tests release CHAPTERS="chapter10 chapter11 chapter12"
```

The root Maven reactor includes every chapter. Chapters 10-14 have portable
IDEA module descriptors depending on the shared `graph` and `print` modules.
Make and the linear-history workflow discover the numbered chapter directories.
