# Chapter 0: Prolog and Motivation

This repo is intended to demonstrate the Sea-of-Nodes compiler IR, and contains
a fully fledge stand-alone compiler.

As a demonstration compiler, it is **not** intended to be a production ready
- although it has a fairly large test suite.

This Ahead-of-Time compiler is not intended (yet) to be a Just-In-Time
compiler, although that may happen in a later chapter.

It is intended to be *readable* and *debuggable*, fast to learn and modify.  It
is **not** designed to be a super fast compiler, although it is pretty quick.
It could be made much quicker with modest effort, and that may happen in some
later chapter.

## Target Audience 

My target audience is both for traditional compiler writes, and medium skill
programmers curious about how a compilers.  I do expect some knowledge about
how compilers work; things like like source-code-in and binaries-out.  It will
definitely help to have some jargon words sorted out (see end of this document)
and be comfortable with under-graduate level graph algorithms.


## Why Java

Simple is written in Java.  Why?  Because Java has shown itself to be fast to
learn, write, and debug.  It is certainly fast enough for large scale batch jobs
(see [The One Billion Row Challenge](https://github.com/gunnarmorling/1brc),
which Ahead-of-Time compilers are.

A Just-In-Time variant of Simple may revist the implementation language
decision.


## Jargon

Each of these terms is common in compilers, and there is a wealth of literature
available online for each of them.  Wikipedia is a great starting point for
learning more.


* IR: Intermediate Representation - source code is hard to directly manipulate,
and machines only understand Machine Code.  The IR bridges this gap; source
code is translated into an IR, the IR is manipulated (e.g. type-check and
optimize), and finally the IR is converted to machine code (binary format).

The notion of an IR generally covers a high level CFG view, and a mid level BB
view and a low level instruction or opcode view.


* BB: Basic Block - a collection of IR instructions or opcodes.  These are all
expected to execute from start to finish without any changes in control flow.
At different points in time, the opcodes might represent some high-level
language concept (e.g. allocation, or a function call), or might represent a
direct hardware instruction (`add r1,r2,r3` or `call malloc`).

In a traditional compiler, all opcodes are kept inside BB's and some care has to
be taken to move opcodes from one BB to another.

In the Sea of Nodes compiler, this restriction is dropped until right before
code generation.


* CFG: Control Flow Graph - a *graph*, where the *nodes* are BB's and the
*edges* represent changes in program execution flow.  In the Sea of Nodes
compiler, the BB notion is mostly dropped and some normal-ish opcodes are the
CFG nodes.  


* SCC: Strongly Connected Components - the Tarjen algorithm for finding
loops in a graph.  


* SCCP: Sparse Conditional Constant Propagation - A particularly fast and
simple way to analysis an IR.  Used to optimize programs, generally by
replacing computations (which require some work) with constants (which require
almost no work).


* "Peep", or Peephole Optimization - an optimization which relies on only local
information, as-if viewing the program "though a peephole".  Something like
replacing `add x+0` with `x`, which removes an `add` instruction.  This
transformation is correct without reguard to the rest of the program.  All
compilers do some kind of peephole optimizations, Sea of Nodes makes much more
extension use of these.


