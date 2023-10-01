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

* [Language](language/README.md): Simple language grammar.
* [Chapter 1](chapter01/README.md): Script that returns an integer literal, i.e., an empty function that takes no arguments and returns a single integer value. The `return` statement.
* [Chapter 2](chapter02/README.md): Simple binary arithmetic such as addition, subtraction, multiplication, division
  with constants. Peephole optimization / simple constant folding.
* [Chapter 3](chapter03/README.md): Local variables, and assignment statements. Read on RHS, SSA, more peephole optimization if local is a
  constant.
* Chapter 4: Binary and Comparison operators involving constants and variables. Non-zero values will be truthy.
* Chapter 5: `if` statement. CFG construction, peephole optimization around dead control flow.
* Chapter 6: `while` statement.
* Chapter 7: Global Value Numbering.
* Chapter 8: Functions and calls.
* Chapter 9: Boolean operators `&&`, `||` and `!` including short circuit.
* Chapter 10: `float` type.
* Chapter 11: Memory effects: general memory edges in SSA. Peephole optimization around
  load-after-store/store-after-store.
* Chapter 12: Equivalence class aliasing, fine grained peephole optimizations. Free ptr-to analysis in SoN; but does not
  handle aliasing in arrays.
* Chapter 13: One dimensional static length array type. Array load/store.
* Chapter 14: Global Code Motion - unwind SoN to CFG. Scheduling.
* Chapter 15: Instruction selection, BURS.
* Chapter 16: Backend register allocation.
* Chapter 17: Garbage Collection.