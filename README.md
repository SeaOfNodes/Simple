# Simple
A Simple showcase for the Sea-of-Nodes compiler IR

This repo is intended to demonstrate the Sea-of-Nodes compiler IR.

The Sea-of-Nodes is the core IR inside of HotSpot's C2 compiler
and Google's V8 compiler and Sun/Oracle's Graal compiler.

Since we are show casing the SoN IR, the *language* being implemented is less important.
We're using a very simple language similar to C or Java, but with far fewer features.
The Sea-of-Nodes is used for machine code generation in these industrial
strength systems - but for this demonstration the backend is both difficult
and less important.

This repo also is not intended to be a complete language in any sense,
and so the backend will probably start with levering C or Java.

## Chapters

The following is a rough plan, subject to change.

Each chapter will be self-sufficient and complete; in the sense that each chapter will fully implement
a subset of the simple language, and include everything that was created in the previous chapter.
Each chapter will also include a detailed commentary on relevant aspects of the
Sea Of Nodes intermediate representation.

The simple language will be a small subset of C.

* [Chapter 1](chapter01/README.md): Script that returns an integer literal, i.e., an empty function that takes no arguments and returns a single integer value. The `return` statement.
* [Chapter 2](chapter02/README.md): Simple binary arithmetic such as addition, subtraction, multiplication, division
  with constants. Peephole optimization / simple constant folding.
* [Chapter 3](chapter03/README.md): Local variables, and assignment statements. Read on RHS, SSA, more peephole optimization if local is a
  constant.
* [Chapter 4](chapter04/README.md): A non-constant external variable input named `arg`. Binary and Comparison operators involving constants and `arg`. Non-zero values will be truthy. Peephole optimizations involving algebraic simplifications.
* [Chapter 5](chapter05/README.md): `if` statement. CFG construction.
* [Chapter 6](chapter06/README.md): Peephole optimization around dead control flow.
* [Chapter 7](chapter07/README.md): `while` statement. Looping construct - eager phi approach.
* [Chapter 8](chapter08/README.md): Looping construct continued, lazy phi creation, `break` and `continue` statements.
* [Chapter 9](chapter09/README.md): Global Value Numbering. Iterative peepholes to fixpoint. Worklists.
* [Chapter 10](chapter10/README.md): User defined Struct types. Memory effects: general memory edges in SSA. Equivalence class aliasing. Null pointer analysis. Peephole optimization around load-after-store/store-after-store.
* Chapter 11: Nested references in Structs. Float type.
* Chapter 12: One dimensional static length array type. Array load/store. String type.
* Chapter 13: Functions and calls.
* Chapter 14: Boolean operators `&&` and `||` including short circuit.
* Chapter 15: Global Code Motion - unwind SoN to CFG. Scheduling.
* Chapter 16: Code generation: perhaps to Java bytecodes.
* Chapter 17: Code generation: to native X86 or ARM. Instruction selection, BURS. Register allocation.
* Chapter 18: Exceptions
* Chapter 19: Garbage Collection.