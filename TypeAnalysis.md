# Type Analysis vs Constant Propagation

## Prelude

While I was working on the C2 compiler in HotSpot, I noticed something
interesting: although Java generics are *erased*, C2 is able to nearly recover
them completely via simple [constant
propagation](https://en.wikipedia.org/wiki/Sparse_conditional_constant_propagation).

Afterwards I developed two languages which are directly designed to do all
their type-checking via constant propagation:
[Simple](https://github.com/SeaOfNodes/Simple) and
[AA](https://github.com/cliffclick/aa).  Both languages are statically typed
and type-safe; Simple uses constant propagation exclusively while AA uses a
blend of extended Hindley-Milner and constant propagation.

In both languages, after the first round of *pessimistic* constant propagation
(and extensive peephole optimizations) completes, every program point (node in
the IR) has a type - and if there are no error types, then the program is
type-safe.  No seperate typing pass is needed.  Optionally, an *optimistic*
constant propagation (an extended SCCP) can be run to type more programs than
the *pessimistic* approach alone.

These discussion relies on the program being in SSA form, which I assume the
reader is familiar with.

## Table of Contents

* [The Basics](#the-basics)
* More Lattices (floats, bool, int ranges, small FP)
* Tuples
* Structs, Round#1 - includes tuples-as-structs
* Functions
* Structs, Round#2 - methods
* Subclassing
* Duck Typing
* Nomative Typing and Traits/Interfaces
* Call Graph Discovery
* Separate compilation
* Performance Concerns
* Typing other simple properties: mutable-or-not, [initialized/destructed]-or-not


## The Basics

Variations of constant propagation have a long history in compilers, and here I
am referring to the well understood [Monotone Analysis
Framework](http://janvitek.org/events/NEU/7580/papers/more-papers/1977-acta-kam-monotone.pdf);
a quick google search finds plethora of courseworks on the topic.  I assume the
reader is familiar, and I give a very brief overview here.  Constant
propagation has long been used to discover interesting facts about program
points, generally to further optimize a program.

At the core of the problem is a
[lattice](https://en.wikipedia.org/wiki/Lattice_(order)) used to track what
values a program point can take on.  Here is a common
[example](chapter02/docs/lattice.svg) for tracking integer constants.
The lattices being used here are:
* Symmetric - every lattice element has a *dual*
* Complete - the *meet* of any two elements is another element
* Bounded (ranked) - the lattice has finite height
and of course the *meet* is commutative and associative.

The lattice *join* is defined from the *meet* and *dual* in 
the normal way: `~(~x meet ~y)`


### The Constant Propagation Algorithm

All program points are initialized to either *top* ⊤ or *bottom* ⊥, and also
put on a worklist.  We pull work off the worklist until it runs dry, evaluating
the program points's *transfer function* to partially evaluate wrt the lattice.
If the evalutation produces a new lattice element, we put dependent points'
back on the worklist (using e.g. use-def edges).

The beauty of this algorithm is that it hits a *least fixed point*, a "best
answer" in time proportional to the program size and lattice depth.  That
single "best answer" will also become our *typing*, our mapping from program
points' to their *type*.


### Pessmistic vs Optimistic

In general these lattices can be used in either a *pessimistic* or *optimistic*
direction.

The *pessimistic* direction starts with all program points at the least
element, here ⊥, and applies the program points' *transfer function* to
partially evaluate wrt the lattice, potientially discovering new facts or
constants.

Example#1:
```python
var x    # declare a variable 'x', untyped
x = 2    # assign a 2
print(x) # print x, which is 2
```

Here we see `x=2` at the `print(x)` and this program can be simplified to
`print(2)`, dropping the `x`.

Example#2:
```python
x0 = 1              # Note the SSA renaming
while( rand() ):
    x1 = phi(x0,x2)
    x2 = 2-x1       # can we discover x2 = 1 here?
print(x1)
```

While evaluating this program top-down, we can see that `x0=1` when we reach
the loop entry, but what happens to `x` around the backedge?  Since we start at
⊥, the `phi(x0,x2)` becomes `phi(1,⊥)` which does a *meet* to yield `⊥`.  Then
`x2 = 2-x1` becomes `x2 = 2-⊥` which is `⊥` which is what we started with.  We
have then hit a fixed point with no values further changing: `x0 = 1; x1 = ⊥;
x2 = ⊥`.

Let's repeat Example#2 using the *optimistic* starting point: all values at `⊤`.
Following down the worklist, by the loop end we know so-far:

```python
x0=⊤, x1=⊤, x2=⊤
          x0 = 1              # Note the SSA renaming
          while( rand() ):
x0=1, x1=⊤, x2=⊤
              x1 = phi(x0,x2)
x0=1, x1=1, x2=⊤
              x2 = 2-x1       # can we discover x2 = 1 here?
x0=1, x1=1, x2=1
```

Since x2 changed from `⊤` to `1`, we need to repeat uses of x2:

```python
x0=1, x1=1, x2=1
              x1 = phi(x0,x2)
x0=1, x1=1, x2=1
```

With no change from before and after the `phi(x0,x2)` we hit our fixed point -
and discover that `print(x)` is really `print(1)` and all the `x` math and the
loop can be dropped.

The general rule here is that the *optimistic* approach may find more
constants, but never less; while the *pesimistic* approach starts from the
lowest possible types and can be stopped any time yielding a correct analysis
(but a possibly weaker find result).  In fact, the *pessimistic* approach can
be interleaved with optimizations as long as those optimizations do not lose
any type information - and indeed this is the mode the C2 compiler has been
running in since 1997.
