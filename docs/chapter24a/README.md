# Chapter 24a: SCCP Within a Function

English | [日本語](README.ja.md)

[Previous: Chapter 23](../chapter23/README.md) |
[Next: Chapter 24b](../chapter24b/README.md)

This chapter introduces [SCCP](https://en.wikipedia.org/wiki/Sparse_conditional_constant_propagation)
within each function.  Next chapter, we'll go interprocedural with SCCP.

Calls retain conservative interfaces here. In particular, mutually recursive
functions whose return types need inference across calls can still fail with
`No defined return type`; Chapter 24b solves those recursive return cycles.


## Sparse Conditional Constant Propagation

[SCCP](https://en.wikipedia.org/wiki/Sparse_conditional_constant_propagation)
is the optimistic version of the same peephole/constant-folding work we have
been doing all along. Types start at the top of the lattice and fall as we
discover reachable values. The rules for the algorithm are well known from the
literature and commonly taught at the college level;
[example slides](https://www.cse.psu.edu/~gxt29/teaching/cse522s23/slides/08monotoneFramework.pdf).

- We have a lattice already
- We have SSA form already (hence the algorithm is *sparse*)
- We have a mapping from Nodes to Types (`Node._type`)
- We have transfer functions (`Node.compute`) already.

We initialize all `_type` fields to ⊤ (as opposed to ⊥), and place all Nodes on
a worklist.  We pull a Node from the worklist, run the transfer function
`node.compute()`, and if the result changes from `node._type` we update
`node._type` and put its output neighbors on the worklist.  A simplified
version, omitting dependency tracking and most debug checks:

```java
while( (n = code._iter._work.pop()) != null ) {
    Type nval = n.compute();
    if( n._type != nval ) {
        assert n._type.isa(nval);     // Types start high and always fall
        n._type = nval;               // Update type in Node
        code._iter.addAll(n._outputs);// Neighbors might change, need to inspect
    }
}
```

With monotone transfer functions and bounded range widening, this algorithm
terminates at a *fixed point*. Widening can lose range precision, so this need
not be the sharpest answer the lattice can express. The resulting types are
at least as precise as those found by pessimistic analysis. We can then run
the peephole rules on nodes
with improved types, to see if unrelated peepholes can fire - such as newly
discovered constants replacing computations with ConstantNodes.

The following is SSA pseudocode:

```text
int x0 = 1;          // x0 = 1
while( rand ) {
    x1 = phi(x0,x2); // x1 = BOT = phi(1,BOT)
    x2 = 2 - x1;     // x2 = BOT = 2 - BOT
}
return x1
```

Without SCCP, this small program cannot fold the computation of `x`.
A glance at the program tells us `x` must always be a `1` but
the bottom-up (pessimistic) approach decides that since x1 is BOT, x2 must be
BOT so x1 must be BOT.  We need the optimistic approach to break the
stalemate, and just *assume* x1 is `1`, and then we can discover that x2 is
also a `1`. Folding the value does not prove the loop terminates or make
its condition and side effects removable.


The table shows one possible worklist order, from left to right. The solve
stops when no more changes happen:

|Var|init|x0=1|x1=phi(1,T)|x2=2-x1|x1=phi(1,1)|
|---|----|----|-----------|-------|-----------|
|x0 | T  | 1  | 1         | 1     | 1         |
|x1 | T  | T  | 1         | 1     | 1         |
|x2 | T  | T  | T         | 1     | 1         |



### Conditional

Since our control flow is in the same graph as our data flow (that's the major
point of Sea-of-Nodes!), we need not do anything special and our algorithm is
*conditional* already.  Again an example is interesting:

Again using SSA pseudocode:

```text
int x0 = 1;          // x0 = 1
while( rand ) {
    x1 = phi(x0,x4); // x1 = BOT = phi(1,BOT)
    if( x1 == 1 )    // Since x1 is BOT, both true and false arms are taken
        x2 = 2 - x1; // x2 = BOT = 2 - BOT
    else x3 = 99;    // x3 = 99
    x4 = phi(x2,x3); // x4 = BOT = phi(BOT,99)
}
return x1
```

Again the table shows one possible order, although this time not all
paths are reachable.  The `if` result is a pair of Control values, shown as
either `C` if the path is alive or `X` if not.  This is used when we compute
`x4 = phi(x2,x3)`; the `PhiNode` ALSO checks the corresponding control input
from the `if` and only merges alive paths.


|Var|init |x0=1|x1=phi(1,T)| 1 == 1? | x2=2-x1 | x3=99   |x4=phi(C/1,X/99)|x1=phi(1,1)|
|---|-----|----|-----------|---------|---------|---------|----------------|-----------|
|x0 | T   | 1  | 1         | 1       | 1       | 1       | 1              | 1         |
|x1 | T   | T  | 1         | 1       | 1       | 1       | 1              | 1         |
|if | T   | T  | T         | [C,X]   | [C,X]   | [C,X]   | [C,X]          | [C,X]     |
|x2 | T   | T  | T         | T       | 1       | 1       | 1              | 1         |
|x3 | T   | T  | T         | T       | T       | 99      | 99             | 99        |
|x4 | T   | T  | T         | T       | T       | T       | 1              | 1         |

This kind of conditional constant propagation really helps if a condition can
be proven true or false, as this removes a test and branch from the optimized
program.  A common way this happens is with null tests; here is the same
idea as above, but using null pointers instead of integers (in pseudocode):

```text
List head = notNullList();
while( rand ) {
    if( head ) head = new List(head,payload);
    else { head = null; default_action(); }
}
```

Here since `head` starts not-null, it can never be null and the
`default_action` can never happen, and the loop can
simplify:

```text
List head = notNullList();
while( rand ) {
    head = new List(head,payload);
}
```

## Convergence and calls

Integer ranges can grow one value per loop trip. A loop Phi widens its range
after a bounded number of such steps. Constants stay precise; above-center
ranges must remain narrow so they can fall to constants monotonically.

During SCCP, each function retains its unknown-caller Start input. Call edges
are disconnected, and call results keep the summaries already established by
pessimistic analysis. Those summaries can be sharper than the written
signature, so discarding them would lose valid facts. Calls still carry
whole-memory effects. The solve discovers no new call edges.

After the solve, ordinary peepholes relink calls and apply inlining as before.
FinalizeIR removes uncalled bodies and detaches calls for scheduling. This
separates local optimistic analysis from the existing pessimistic call machinery.

`SccpTest` checks a loop fixed point and a function called with 1 and 2: its
parameter remains general here, while the following chapter infers [1,2].

A recursive function whose return type needs optimistic propagation through
calls can still report "No defined return type" here. The next chapter adds
that inference and the widening needed to make it converge; its
`Chapter24Test.testRecursiveWidening` exercises both recursive data cycles.

## Building and checking

Run `make lib` once for Java test dependencies, then `make tests` in this
directory. From the repository root, use `make tests CHAPTERS=chapter24a`.
The inherited tests retain their historical class names.

The [grammar](24-grammar.md) includes chained comparisons introduced in
Chapter 23 beside short-circuit expressions. Allocator tuning is covered in
the [appendix](../appendices/optimizer-tuning.md).
