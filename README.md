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

The Common chapter defines code that will be reused throughout.

The remaining chapters will each be self-sufficient and complete; in the sense that each chapter will fully implement 
a subset of the simple language. Each chapter will also include a detailed commentary on relevant aspects of the 
Sea Of Nodes intermediate representation.

The simple language will be a small subset of C. 

* [Common](common/README.md): Common utilities including a lexer.
* [Language](language/README.md): definition.
* [Chapter 1](chapter01/README.md): Simple control flow statements, `if` and `while`. Variables of `int` type. Simple 
  arithmetic such as addition, subtraction, multiplication, division. Non-zero values will be truthy.
* Chapter 2: Function calls.
* Chapter 3: Comparison operators.
* Chapter 4: Boolean operators `&&`, `||` and `!`.
* Chapter 5: `float` and one dimensional static length array type.
* Chapter 6: Visualize Sea of Nodes IR.
* Chapter 7: Optimizations - tbc