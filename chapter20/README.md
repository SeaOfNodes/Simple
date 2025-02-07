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

Graph Coloring is one such allocation method; a *live-range graph* is built,
with each *live range* requiring its own register.  Coloring the graph ensures
every live range gets a unique register, especially when it overlaps or
*interferes* with another live range.  Here the colors are actually the machine
registers.  

A successful coloring ends the allocation, and after some bookkeeping the
program is ready for e.g. code emission.  A failed coloring requires spilling
or splitting conflicting live ranges; in this allocator we will be doing live
range *splitting*.  Splitting live ranges makes them more colorable; they
become shorter with fewer interfering other live ranges.  Generally large
functions might require a few rounds of splitting before becoming colorable.
Since the problem is NP-complete there are no guarantees here, and the final
allocation quality becomes heavily dependent on splitting heuristics.

Hence, much of the "high theory" will be used to drive the core coloring
algorithm, but much of the "actual success" will depend on a large collection
of splitting heuristics.

Coloring proceeds in rounds:

- Build the Live Ranges; live ranges require the same register for all
  instructions in the live range.  Phi functions will force unrelated def/use
  edges to become part of the same live range and this can drive large
  interconnected live ranges which a lot of conflicts.  Some hardware ops
  require the same register for both inputs and outputs (X86 is notorious here)
  which also makes fewer larger live ranges.  We'll be using the disjoint
  Union-Find algorithm to rapidly build up live ranges.
  
- Build the Inteference Graph: every live range which is alive at the same time
  as any other, now *interferes* or conflicts.  This is built using a live-ness
  pass, which is a backwards pass over the CFG, and can require more than one
  pass according to loop nesting depth.  The data structure here is generally a
  2-D bitset, triangulated to cut its size in half.
  
- Color the interference graph.  Nodes in the graph which are strictly low
  degree (more available registers than neighbors) will guaranteed get a color.
  These can be removed from the graph - since they will guaranteed get a
  register (color) because they have so few neighbors.  Removing these trivial
  nodes makes more nodes go trivial, and this process repeats until either we
  run out (and are guaranteed a coloring) or we find a high-degree clique: a
  set of mutual interferences where none of them are guaranteed a color.  In
  this we pull an "at risk" live range out; this one might not color.  Once the
  graph is emptied, we reverse and re-insert the live ranges in reverse order,
  coloring as we go.  All the trivial live ranges will color; the at-risk ones
  may or may not.

- If every live range got a color, then Register Allocation is done.  If not,
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

Many of the register masks are immutable; e.g. allowed registers for particular
opcodes, or describing registers for calling conventions.  Other masks
represent mutable bitsets, with the allocator routinely masking off bits when
accumulating a set of constraints.  To help keep these uses apart, the
`RegMask` class includes mutable and immutable mask objects.

