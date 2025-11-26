# Chapter 0: Prolog and Motivation

This repo is intended to demonstrate the Sea-of-Nodes compiler IR, and contains
a fully fledge stand-alone compiler.

It is intended to be *readable* and *debuggable*, fast to learn and modify.  It
is **not** designed to be a super fast compiler, although it is pretty quick.
It could be made much quicker with modest effort, and that may happen in some
later chapter.

As a demonstration compiler, it is **not** intended to be a production ready
(although it has a fairly large test suite).

This Ahead-of-Time compiler is not intended (yet) to be a Just-In-Time
compiler, although that may happen in a later chapter.

## Target Audience 

My target audience is both for traditional compiler writers, and medium skill
programmers curious about compilers.  I do expect some basics about how
compilers work; things like like source-code-in and binaries-out.  It will
definitely help to have some jargon words sorted out (see end of this document)
and be comfortable with under-graduate level graph algorithms.


## Why Java

Simple is written in Java.  Why?  Because Java has shown itself to be fast to
learn, write, and *debug*.  It is certainly fast enough for large scale batch jobs
(see [The One Billion Row Challenge](https://github.com/gunnarmorling/1brc)).

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


* Nodes - nodes in a graph.  For a traditional compiler there are two kinds of
  nodes; nodes in a CFG are BB's and Nodes in a BB are instructions.  For Sea of
  Nodes, this distinction is blurred.  The same kinds of Nodes and Edges are used
  for both control flow and data.


* DU: Def-Use edges - a graph edge from a Defining node to a Using node.  These
  edges track the flow of values through a program and are required to give the
  program its meaning.  The reverse, Use-Def edges, are very useful for program
  optimizations.  Some early program IRs don't start with these, or periodically
  build them from program names, and throw them away.

  Sea of Nodes starts with these def-use edges (and use-def) and maintain them
  throughout the comnpiler's lifetime.


* SSA: Static Single Assigment - a program shape where all program values are
  statically assigned exactly once.  Example before SSA: 
  
```
  if( rand ) x=2; // A first  assignment to x
  else       x=3; // A second assignment to x
  print(x);
```

  Here `x` is assigned twice.  SSA form will rename the `x` variables and add a
  `Phi` function to make a program with the same semantics but all (renamed)
  variables are assigned exactly once (x0, x1, x2 are all assigned once):

```
  if( rand ) x_0=2; 
  else       x_1=3; 
  x_2 = phi( x_0, x_1 );
  print(x_2);
```

  Most source languages do not start this way.  Most modern compilers move to SSA
  at some point, because it allows for fast and simple optimizations (e.g. SCCP).


* Phi, PhiNode - Variously "funny", "fake" or the greek `Ø` character.  A pure
  mathematial "function" which picks between its arguments based on control
  flow and a key part of SSA form.  Scare quotes on "function" because normal
  functions do not pick their arguments based on control flow.  Phi functions
  are generally implemented with zero cost in machine code, by carefully
  arranging machine registers.


* AST: Abstract Syntax Tree - another IR variant, where the lexical structure
  of the program has been converted to a tree.  After some tree-based work, the
  AST is generally converted to a graph-based IR, described above, because trees
  are hard to optimize with.  This is very common in most compilers, and Sea of
  Nodes skips this step.


* SCC: Strongly Connected Components - the Tarjen algorithm for finding loops
  in a graph.  Loops carry most of the work in a program so its important to
  optimize them more heavily.  Acronym does end in a "P", see SCCP.


* SCCP: Sparse Conditional Constant Propagation - A particularly fast and
  simple way to analysis an IR.  Used to optimize programs, generally by
  replacing computations (which require some work) with constants (which require
  almost no work).  Acronym ends in a "P", see SCC.
  

* "Peep", or Peephole Optimization - a transformation which relies on only
  local information as-if viewing the program "though a peephole".  Something
  like replacing `add x+0` with `x`, which removes an `add` instruction.  This
  transformation is correct without reguard to the rest of the program.  All
  compilers do some kind of peephole optimizations, Sea of Nodes makes extensive
  use of these.


* Fixed Point - If, in a series of possible transformations, we keep applying
  transforms until no more apply - and we hit the same state no matter what order
  we apply those transforms, we have hit a *fixed point*.  This is a key
  mathematically term, and you can find plenty math jargon online about it.  The
  SCCP optimization will stop once it hits a *fixed point*.

  For Sea of Nodes, we use this concept for Peeps as well, applying peephole
  transformations iteratively from a worklist until no more apply.  By careful
  design we will hit a *fixed point* - our program graph IR will be the same
  shape, irregardless of transformation ordering.  This lets use run the
  peepholes from a simple worklist algorithm.


* Types - a set of values a particular program point and Node can take on at
  runtime.  In the program semantics literature, types are sometimes described
  by the allowed operations (`int` types can `add`, and `pointer` types can
  `dereference`), and sometimes described as a set of values.
  
  For Sea of Nodes, we use the "set of values" type concept, and we don't
  actually have any use for a seperate value implementation.  The sets of
  values can get fancy; integer types are represented with a range like
  `[0..9]` and integer constants by a short range e.g. `[3]`, the maximum
  integer type is `[MIN_INT..MAX_INT]`.  Types exist for integers, floats,
  structs/class/records, function pointers, memory and control flow.
  
  Types are an important class in Sea of Nodes, and likes nodes and edges it is
  very common to manipulate types.
 

* Lattice - a mathematical concept representing relationships between members
  of a set.  See [Lattice](https://en.wikipedia.org/wiki/Lattice_(order)).
  
  For Sea of Nodes, we use a Lattice over a set of Types (which themselves are
  sets of values).  Our lattice has a number of important mathematical
  properties: symmetric complete bounded (ranked), and these allow our random
  worklist algorithms to hit a *fixed point* in fast linear time.  For actual
  day-to-day working in the compiler, the lattice is in the background and is
  hardly ever seen.