# Simple
A Simple showcase for the Sea-of-Nodes compiler IR

This repo is intended to demonstrate the Sea-of-Nodes compiler IR.

The Sea-of-Nodes is the core IR inside of HotSpot's C2 compiler
and Google's V8 compiler and Sun/Oracle's Graal compiler.

Since we are show casing the SoN IR, the *language* being implemented is less
important.  We're using a very simple language similar to C or Java, but with
far fewer features.  Simple is strongly typed, object-oriented, with first-
class *functions* not *closures*.  Object references are pointers, and null
pointer exceptions are disallowed by the typing system.  Arrays will probably
be range-checked at some point, making Simple a fully safe language.  Simple
has a minimal syntax that can be parsed with a recursive descent parser.

The Sea-of-Nodes is used for machine code generation in these industrial
strength systems - but for this demonstration the backend is both difficult and
less important.  This repo will eventually target X86 and at least one more
machine with ahead-of-time compilation - but with an eye to JIT compilation.

This repo also is not intended to be a complete language in any sense, and so
the backend starts with levering Java: the Evaluator (first appears in Chapter
10) directly slowly interprets the SoN IR.  Code-gen first appears in Chapter
19.


## Chapters

The following is a rough plan, subject to change.

Each chapter will be self-sufficient and complete; in the sense that each chapter will fully implement
a subset of the Simple language, and include everything that was created in the previous chapter.
Each chapter will also include a detailed commentary on relevant aspects of the
Sea Of Nodes intermediate representation.

The Simple language will be styled after a subset of C or Java

* [Chapter 1](docs/chapter01/README.md): Script that returns an integer literal, i.e., an empty function that takes no arguments and returns a single integer value. The `return` statement.
* [Chapter 2](docs/chapter02/README.md): Simple binary arithmetic such as addition, subtraction, multiplication, division
  with constants. Peephole optimization / simple constant folding.
* [Chapter 3](docs/chapter03/README.md): Local variables, and assignment statements. Read on RHS, SSA, more peephole optimization if local is a
  constant.
* [Chapter 4](docs/chapter04/README.md): A non-constant external variable input named `arg`. Binary and Comparison operators involving constants and `arg`. Non-zero values will be truthy. Peephole optimizations involving algebraic simplifications.
* [Chapter 5](docs/chapter05/README.md): `if` statement. CFG construction.
* Chapter 6: Peephole optimization around dead control flow.
* Chapter 7: `while` statement. Looping construct - eager phi approach.
* Chapter 8: Looping construct continued, lazy phi creation, `break` and `continue` statements.
* Chapter 9: Global Value Numbering. Iterative peepholes to fixpoint. Worklists.
* Chapter 10: User defined Struct types. Memory effects: general memory edges in SSA. Equivalence class aliasing. Null pointer analysis. Peephole optimization around load-after-store/store-after-store.
* Chapter 11: Global Code Motion - Scheduling.
* Chapter 12: Float type.
* Chapter 13: Nested references in Structs.
* Chapter 14: Narrow primitive types (e.g. bytes)
* Chapter 15: One dimensional static length array type, with array loads and stores.
* Chapter 16: Constructors
* Chapter 17: Mutability & Syntax Sugar: `var`, `val`, `x+=y`, `for(init;test;next)body`
* Chapter 18: Functions and calls.
* Chapter 19: Code gen to X86-64.
