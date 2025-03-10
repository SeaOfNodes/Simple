# Chapter 20: Graph Coloring Register Allocation

## Some Reading Material 

This is a Briggs-Chaitin-Click allocator, and is very similar to the one used
in HotSpot's C2 allocator for the past 25 years with great success.

Wikipedia has a nice overview of register allocation in general: https://en.wikipedia.org/wiki/Register_allocation

Chaitin's original: https://en.wikipedia.org/wiki/Register_allocation#CITEREFChaitin1982

Briggs additions: https://dl.acm.org/doi/10.1145/177492.177575

There are several other useful link that can be found by searching for Briggs
or Chaitin in combination.  Basically there was a spate of register allocation
improvements in this era that made it into many high-end mainstream compilers.

I've included a very old unpublished paper on speeding up graph coloring
allocators: [Interference Graph Triming](docs/ifg_trim.pdf).


You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter20) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter19...linear-chapter20) it to the previous chapter.


## The Basic Theory

The reading material includes several explainations of the basic concepts, but
at the risk of being redundant I'll present a short version again here.

I assume you (the reader) know *why* register allocation is required: machine
registers are a high value finite resource, and as with all finite resources
they need to be allocated well.

Graph Coloring is one such allocation method; an *live range interference
graph* is built, with each *live range* requiring its own register.  Coloring
the graph ensures every live range gets a unique register, specificially when
it overlaps or *interferes* with another live range.  Here the colors are
actually the machine registers.

A successful coloring ends the allocation, and after some bookkeeping the
program is ready for e.g. code emission.  A failed coloring requires spilling
or splitting conflicting live ranges; in this allocator we will be doing live
range *splitting*.  Splitting live ranges makes them more colorable; they
become shorter with fewer interfering other live ranges.  Generally large
functions might require a few rounds of splitting before becoming colorable.
Since the problem is NP-complete there are no guarantees here, and the final
allocation quality becomes heavily dependent on splitting and coloring heuristics.

Hence, much of the "high theory" will be used to drive the core coloring
algorithm, but much of the "actual success" will depend on a large collection
of heuristics.

### Coloring Rounds

Coloring proceeds in rounds until we get a coloring:

- Build the Live Ranges; live ranges require the same register for all
  instructions in the live range.  Phi functions will force unrelated def/use
  edges to become part of the same live range and this can drive large
  interconnected live ranges with a lot of conflicts.  Some hardware ops
  require the same register for both inputs and outputs (X86 is notorious here)
  which also makes fewer larger live ranges.  We'll be using the disjoint
  Union-Find algorithm to rapidly build up live ranges in one forward pass over
  the program.
  
- Build the Inteference Graph: every live range which is alive at the same time
  as any other, now *interferes* or conflicts.  This is built using a LIVE-ness
  pass, which is a backwards pass over the CFG, and can require more than one
  pass according to loop nesting depth.  The data structure here is generally a
  2-D bitset, triangulated to cut its size in half.  As part of LIVE, we'll
  also have a set of reaching defs per-block.
  
- Color the interference graph.  Nodes (live ranges) in the graph which are
  strictly low degree (more available registers than neighbors) will guaranteed
  get a color.  These can be removed from the graph - since they will
  guaranteed get a register (color) because they have so few conflicting
  neighbors.  Removing these trivial nodes makes more nodes go trivial, and
  this process repeats until either we run out (and are guaranteed a coloring)
  or we find a high-degree clique: a set of mutually interfering live ranges
  where none of them are guaranteed a color.  In this case we pull an "at risk"
  live range out; this one might not color.  Once the graph is emptied, we
  reverse and re-insert the live ranges, coloring as we go.  All the trivial
  live ranges will color; the at-risk ones may or may not.

- If every live range gets a color, then Register Allocation is done.  If not,
  we enter a round of splitting.  Live ranges which did not color are now split
  via a series of heuristics into lots of small live ranges.  Splitting has a
  tension: too much and you got a lot of spills (and a generally poor
  allocation).  Too little, and you remain uncolorable - and do not make
  progress towards an allocation.  Once we're done splitting, we start a new
  round of coloring (Building the Live Ranges, then the Interference Graph,
  then Coloring).


## Registers and Register Masks

Registers are the thing that Register Allocation is all about!  They are
represented as a small dense integer, starting up from 0, and the numbering
generally follows from the hardware encodings.  So for an X86_64, RAX will be
register 0, RCX register 1 and so on up to the 16 GPRs.  The XMM registers at
16 and go up to 32.  Register numbers must be unique; this is how the register
allocator tracks them.

A collection of registers live in a Register Mask.  For smaller and simpler
machines it suffices to make such masks an i64 or i128 (64 or 128 bit
integers), and this presentation is by far the better way to go... if all
register allocations can fit in this bit limitation.  The allocator will need
bits for stack-based parameters and for splits which cannot get a register.
For a 32-register machine like the X86, add 1 for flags - gives 33 registers.
Using a Java `long` has 64 bits, leaving 31 for spills and stack passing.  This
is adequate for nearly all allocations; only the largest allocations will run
this out.  However, if we move to a chip with 64 registers we'll immediately
run out, and need at least a 128 bit mask.  Since you cannot *return* a 128
bit value directly in Java, Simple will pick up a `RegMask` class object.

One of the Click extensions is this notion of treating "stack slots" are Just
Another Register.  They get colored like other registers, which in turn leads
to very efficient use of the stack; C2 is known for having very small stack
frames.  Another benefit is callee save registers will get split preferentially
(they have very long lifetime and only one def on entry and one use on
exit)... which the splits then end up coloring to the stack, building the call
prolog and epilog naturally.  This allocator will not otherwise dedicate any
effort to stack frame management; it will "fall out in the wash".

Many of the register masks are immutable; e.g. allowed registers for particular
opcodes, or describing registers for calling conventions.  Other masks
represent mutable bitsets, with the allocator routinely masking off bits when
accumulating a set of constraints.  To help keep these uses apart, the
`RegMask` class includes mutable and immutable mask objects.


## Live Ranges and Conflicts

A live range is a set of nodes and edges which must get the same register.
Live ranges form an interconnected web with almost no limits on their shape.
Live ranges also gather the set of register constraints from all their parts.
This leads to a set of levels of conflicts within a live range.

### Hard-Conflicts

A live range can have a *hard confict*: conflicting register requirements,
leading to no valid register choices.  The obvious case is an incoming
parameter fixed in e.g. `rdx` but also required as an exit value in `rax`.  You
can't pick both registers at once, so a split is required.  During the build
live ranges phase, every register constraint from every def and use are AND'd
together; the `RegMask` might lose all valid registers, or remain with just a
handful of register choices.

Hard-conflicts are found while build live ranges, and will trigger a round of
splitting before building the interference graph or coloring.

### Avoiding conflicts

Once of the Click extensions to the Briggs-Chaitin allocator is to take
advantage of register constraints to lower the interference graph degree (which
otherwise becomes an O(n^2) edge collection).  If a live range LR1 is reduced
to a single register (such as a call argument requiring `rdx`) and would
otherwise conflict with another live range LR2 which has more choices - we
remove `rdx` from the LR2's choices.  Without having `rdx`, LR1 and LR2 have no
registers in common - and hence do not conflict.  We just don't add the
interference graph edge between them.  Call argument restrictions are very
common and this kind of edge-removal can dramatically shrink the O(n^2) edge
collection - and hence speedup allocation by a large constant factor.

### Self-Conflicts

A live-range can *self-conflict* be alive twice with 2 different values but
require the same register.  This live range *must* split and thus picks up a
copy from the split.  This happens with loop-carried dependencies as seen in
e.g. a simple `fib` program:

```java
 int x=1, y=1;
 while( true ) {
   tmp = x + y;
   y = x;
   x = tmp;
 };
 
```

In SoN SSA form:
```java
  int x0 = 1, y0 = 1;
  while( true ) {
    x1 = phi(x0,x2);
    y1 = phi(y0,x1);
    x2 = x1 + y1;
  }
```

The variables `x0,x1,x2,y0,y1` all form part of the same live range, since they
are all joined by `Phis`.  However, `x1` and `y1` hold different values from
different iterations of `fib`.  The SSA form does not have a copy, but the
register-allocated form - just like the original code - DOES require a copy.

Self-conflicts are found during building the interference graph, and will
trigger a round of splits before attempting the coloring phase.


## Coloring Heuristics

Both register coloring and register splitting are full of heuristics, and the
quality of these heuristics goes a long way to deciding the quality of the
final allocation.  Graph coloring is NP-complete; there is no efficient
algorithm for coloring and many programs are simply not colorable without
splitting in any case.


### Biased Coloring

Biased coloring is a heuristic that attempts to "pick a better color" or "bias
the color choice", such that a Split is removed.  A Split splits one live range
into two; sometimes some are inserted that are not required (i.e., over-
splitting has already happened).  If one side (live range) of a split is
already colored and we can color the other side (live range) the same color -
then this split can be removed.  If neither side is colored, but one or the
other side is recursively connected to a colored live range, we can still bias
the color in the hopes that a chain of splits will all get the same color and
thus all get removed.

When the coloring process is reversing, and colors are being picked for live
ranges, we often have many register choices.  A first choice is made
arbitrarily (lowest numbered register), and then we go looking for a better
color.  Biased coloring inspects first a sample def (if there is only one, then
the One Def is inspected), and then a sample use.  Inspection looks to see if
"the other side" of a split is already colored; if so, and that color is
available it is choosen.  If there is a split, but it is not colored - we
recursively repeat the process on that split's other side.  This is also done
for the other input of two-address ops and one of a Phi node inputs.

In all cases, the goal is to pick a valid available color - that might also
eventually be pick-able for this Split's other side - and lead to this split
having the same color for both sides, and then being removed.


### Pick Risky

### Split Hard Conflict

### Split Self Conflict

### Split By Loop Nest

