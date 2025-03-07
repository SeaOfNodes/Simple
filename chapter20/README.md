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

Graph Coloring is one such allocation method; machine registers are treated as
colors; a *live range interference graph* is built, with each *live range*
requiring its own register/color.  Coloring the graph ensures every live range
gets a unique register, specificially when it overlaps or *interferes* with
another live range.

A successful coloring ends the allocation and after some bookkeeping the
program is ready for e.g. code emission.  A failed coloring requires spilling
or splitting conflicting live ranges; in this allocator we will be doing live
range *splitting*.  Splitting live ranges makes them more colorable; they
become shorter with fewer interfering other live ranges.  Generally large
functions might require a few rounds of splitting before becoming colorable.
Since the problem is NP-complete there are no guarantees here, the final
allocation quality becomes heavily dependent on splitting and coloring
heuristics.

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

#### Why a RegMask class and not a simple `long`?

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

#### Stack Slots

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
represent mutable bitsets with the allocator routinely masking off bits when
accumulating a set of constraints.  To help keep these uses apart, the
`RegMask` class includes mutable and immutable variants.


### Callee/Caller Save Registers

In this allocator, callee- and caller-save registers get almost no special
treatment and no special prolog nor epilog is built to save and restore them.
At the start of the allocator, SoN graph edges are added between each `Return`
and a `CalleeSave` from each `Fun`.  These edges are given register masks
pinning them to the callee-save set as defined by the normal ABI.

Callee-save live ranges thus have a single def (a `CalleeSave` at function
start) and a single use (the `Return`), are cheap to spill and free a register
over a large area - they make ideal spill candidates.  When the allocator needs
to spill something to get some more registers, these will be the first to
spill.  A prolog and epilog will get built as a consequence of the normal live
range splitting process.

`Calls` kill the caller-save registers (just the inverted callee-save mask),
and this is computed during the normal interference graph building process just
like any other operation that kills registers.  Killing a bunch of registers at
a call typically removes all the free registers from whatever is currently
alive - and will thus force spilling... which will quickly end up spilling
callee-save registers as ideal candidates.


## Live Ranges

A live range is a set of nodes and edges which must get the same register.
Live ranges form an interconnected web with almost no limits on their shape.
Live ranges also gather the set of register constraints from all their parts.

Live ranges are held in `LRG` class instances.  Most of the fields in this
class are for spill heuristics, but there are a few key ones that define what a
LRG is.  `RegAlloc` has several functions dealing with LRGs, including a lookup
table from Nodes i.e., a mapping from `Node` to `LRG` (which is very similar to
having a dedicated LRG field in each Node... except that LRGs are only used
during RegAlloc and the field would be dead weight otherwise).

LRGs have a unique dense integer `_lrg` number which names the LRG.  New `_lrg`
numbers come from the `RegAlloc._lrg_num` counter.  LRGs can be unioned
together -
[this is the Union-Find algorithm](https://en.wikipedia.org/wiki/Disjoint-set_data_structure)
- and when this happens the lower numbered `_lrg` wins.  Unioning only happens
during `BuildLRG` and happens because either a `Phi` is forcing all its inputs
and outputs into the same register, or because of a 2-address instruction.
LRGs have matching `union` and `find` calls, and a set `_leader` field.

Post-allocation the `LRG._reg` field holds the chosen register.  During
allocation the `LRG._mask` field holds the set of available registers,
typically all the defaults, minus various conflicts.

The `LRG._adj` holds a list of adjacent neighbors as part of the larger
Interference Graph, and is only used during the Coloring phase.  
[See more about graphs here.](https://en.wikipedia.org/wiki/Graph_(abstract_data_type))
This is a "adjacency list" form of a Graph description, and is built from the
IFG's 2-D collection of bits (itself a 1-D list of `BitSet`s).  Graph coloring
register allocation is one of the few places where changing the layout of a
data structure mid-algorithm pays out.

The rest of the fields are dedicated to various spilling heuristics:

- `_machDef` `_machUse` `_uidx` - A sample def and use.
- `_splitDef` `_splitUse` - Sample splits involved with this LRG; biased
  coloring attempts to align register choices to remove these.
- `_selfConflicts` - A partial set of *self-conflicting* nodes.  These are
  detected during the Interference Graph build phase, and require aggressive
  splitting.
- `_1regDefCnt` `_1regUseCnt` - Count of defs and uses which are pinned to a
  single register.  These typically require a hard-split just before (use) or
  after (def) to free up the register choices.
- `_killed` - LRG lost all registers due to an op killing its available
  registers; commonly happens to LRGs which span a `Call` and generally
  requires splitting some callee-save live range (spilling a callee-save
  register) and move the LRG into the now available register.
  

## Live Ranges and Conflicts

If the live range `_mask` field goes empty, you might have a *hard conflict* or
have gotten *killed*.  Live ranges can self-interfere, making a
*self-conflict*.  During coloring you might discover an uncolorable clique of
interfering live ranges, leading to a *capacity spill*.  This leads to a set of
levels of conflicts within a live range (and thus a different heuristics for
handling them).

### Hard-Conflicts

A live range can have a *hard confict*: conflicting register requirements,
leading to no valid register choices.  The obvious case is an incoming
parameter fixed in e.g. `rdx` but also required as an exit value in `rax`.  You
can't pick both registers at once, so a split is required.  During the build
live ranges phase, every register constraint from every def and use are AND'd
together; the `RegMask` might lose all valid registers, or remain with just a
handful of register choices.

Hard-conflicts are usually found while build live ranges, and will trigger a
round of splitting before building the interference graph or coloring.  They
can also be found during interference graph building, generally caused by a
1-register kill.

### Avoiding interferences

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

Here we are forced to pick one of several *live ranges* that form a large
clique: they have too many mutual neighbors so that no one of them is
guaranteed a color.  There is a conflicting tension here:

Picking a live range with a large "area" (i.e. covering much of the program),
and low "cost" to spill (i.e. fewer defs and uses, and outside of loops) will
give a large win.  A register will become free over a large part of the program
and allow coloring to success elsewhere.  The normal ABI callee-save registers
are ideal in this case, defined on entry and used only on exit they cover the
entire program.  Also, spilling means only a single split on entry and another
at exit.  Spilling such live ranges basically builds a classic function
prolog/epilog sequence.  Here though, the allocator is allowed to e.g.  spill
outside a fast-path exit.

```
  // No registers saved if the initial RDI is null
  test rdi
  jne  BIG_AND_SLOW
  xor  rax,rax
  ret  // Fast path exit: null argument means null result
BIG_AND_SLOW:
  add  rsp,#12
  mov  [rsp+0],rbx // begin spilling callee-save registers
  mov  [rsp+4],r12 // 
  // lots of code needing callee-save registers
```

The other side of the tension is to pick live ranges that are likely to color
more by chance.  If a live range is "very nearly colorable" - say only one more
neighbor than available registers/colors then by chance (and Biased Coloring)
some neighbors might use the same color... freeing up a color and allowing this
risky live range to color after all.


## Splitting

Here we failed to get a coloring - and need to split.  Some specific live
ranges did not color and we will need to decide how to split.  Again there are
tensions here: too much splitting leads to a bad allocation, to little leads to
no-progress bugs (manifested as too many rounds of splitting past some
arbitrary cutoff).

We start by looking at each spilling live range in turn, and deciding
on a splitting strategy.


### Split Self Conflict

Self-conflict live ranges are discovered during the "Build the Interference
Graph" stage.  If this happens we don't attempt a coloring (its nonsensical
with self conflicts), but go straight to splitting.  During the IFG building we
gathered a subset of the conflicting definitions for this live range.  Now
we visit all those definition points and insert spills:

- Before slot #1 on a Phi (which breaks loop-entry Phis from the loop-body) and
  also after the Phi.
- Before a two-address instruction, breaking the live range before the op from
  afterwards.
- Before any use that extends a live range.

This set of splits is fairly aggressive... but test cases requiring a split in
each of the listed locations are including in Chapter 20's test cases.  We
cannot even attempt a color while we have self conflicts, so its important to
break up these live ranges quickly.  This tends to over-split and the allocator
leans on Biased Coloring to remove some of the extras.


### Split Hard Conflict

Hard conflicts are discovered during the "Build the Live Ranges" pass for
direct def/use conflicts (e.g. defined in `rdi` and used in `rax`).  They also
can be discovered during the "Build the Interference Graph" pass (e.g. incoming
argument in `rdi` used later in the program and killed by a `Call`).

For simple hard-conflicts, we have the exact def and use kept in the Live Range
itself.  We split once after the def and once before the use (and only on sides
with restricted registers).

For more complex cases we use the capacity spilling/loop-nest technique.


### Split By Loop Nest

Here we typically see capacity spills: we just need more registers.  This
easily happens when a `Call` crushes all the temporary registers and we need to
carry some values past the call - but it can also happen any time we have a
larger function with lots of things going on at once.

The heuristic here is to split *around* loops, attempting to keep some free
registers available so the hot loop body does not spill.  Live ranges are split
into successively deeper loop nests in progressive rounds of splitting, and in
the final case will split once after each def and before each use, even in the
innermost loop.

The heurstic starts by discovering the min and max loop depth for all defs and
uses.  If these vary, we will split *around* the outermost loop, putting in
splits at the loop border and keeping an inner untouched live range that has no
constraints from outside.  If this fails to color, on the next round of
splitting the outermost loop will have moved in one layer, and we will be
splitting around the next inner loop nest.  This process repeats until we end
up splitting in the inner-most loops.


## Post Allocation

After a successful coloring, we want to pull out any same-same register splits.
A quick pass over the splits and after checking registers, we pull out the
useless splits (and track the rest for "scoring" our allocation).  We'll also
bypass some split-after-splits, which can remove some redundant copying.

The registers remain available in the `RegAlloc` object via `alloc.regnum( Node n )` 
and will be used by a following instruction encoding pass.