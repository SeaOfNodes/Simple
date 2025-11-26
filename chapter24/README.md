# Chapter 24: Chaining Relationals and Sparse Conditional Constant Propagation

In this Chapter we allow *chaining* conditionals, e.g. `(60 <= score < 90)`
where `score` in the middle does not have to be repeated.

We also present an Interprocedural Sparse Conditional Constant Propagation
algorithm.


You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter23) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter22...linear-chapter23) it to the previous chapter.


## Chaining Relational Tests

Chaining relational tests offer a cleaner, more readable way to write chained
comparisons without repeating the same variable.

```java 
if (min <= x && x < max)
```

becomes

```java 
if (min <= x < max)
```

This syntax saves space and makes the condition more intuitive, similar to how
range checks are written in mathematics.  E.g.

```java 
int score = 75;
if (60 <= score < 90) {
    sys.io.p("Pass");
}
```
This checks if score is between 60 (inclusive) and 90 (exclusive), without repeating `score`.

Expressions that mix opposite directions of comparison, like using both 
`<=` and `=>`, are not allowed, because they create ambiguous logic.

```java
if (a <= b >= c)
```


### Operator direction rules
Stacked comparisons are *only* valid if all the comparison operators
"point" the same way:
- `<` with `<`
- `<` with `<=`
- `>` with `>`
- `>` with `>=`

All operators above can be combined with `==` and `!=`.

It is not allowed to mix directions in a single chain:
```java 
if (a <= b >= c) // Invalid
```

Comparisons can be forced into numeric context using booleans.
This is valid but unusual:
```java
return (0 < arg) > 1;
```
Depending on the result of (0 < arg) this will turn into either
```java
return 0 > 1;
```
OR
```java
return 1 > 1;
```
The equality operator is valid in this form, but since it has lower precedence than comparisons,
```java 
a < b == c
```
is parsed as:
```java
(a < b) == c
```
This might not be the intended chained comparison.
A more explicit alternative is: 
```java 
a < b && b == c
```
Comparisons can be chained together in any length, and different comparison operators may be 
mixed freely as long as they point in the same direction.
Equality(`==`) and inequality(`!=`) operators may be combined with any other comparisons at any point in the chain.

E.g:
```
return 0 < arg < arg+1 < 4;
```

### Tricky edge cases
```java 
return 0 != arg != 1;
```

This is parsed by standard precedence rules as:
```java
return ((0 != arg) != 1);
```
not as chained inequality.

```java
return a == b == c;
```
Similarly, this is parsed as:
```java
return ((a == b) == c);
```



## Sparse Conditional Constant Propagation

[SCCP](https://en.wikipedia.org/wiki/Sparse_conditional_constant_propagation)
is the top-down version of the same peephole/constant-folding work we have
been doing all along.  The rules for the algorithm are well known from the 
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
version with all asserts and debug helpers removed:

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

This algorithm always terminates (and generally fairly quickly) and always
reaches a *fixed point*, as "best answer" subject to the limits of our lattice
and transfer functions.  The resulting types are monotonically better than
those found by the peephole rules; we can then run the peephole rules on nodes
with improved types, to see if unrelated peepholes can fire - such as newly
discovered constants replacing computations with ConstantNodes.

Example:

```java
int x0 = 1;          // x0 = 1
while( rand ) {
    x1 = phi(x0,x2); // x1 = BOT = phi(1,BOT)
    x2 = 2 - x1;     // x2 = BOT = 2 - BOT
}
return x2
```

Without SCCP, this small program cannot remove the computation of `x` and hence
the loop either.  A glance at the program tells us `x` must always be a `1` but
the bottom-up (pessimistic) approach decides that since x1 is BOT, x2 must be
BOT so x1 must be BOT.  We need the optimistic approach to break the
statemate, and just *assume* x1 is `1`, and then we can discover than x2 is
also a `1`.


The algorithm progresses from left to right and stops when no more changes
happen:

|Var|init|x0=1|x1=phi(1,T)|x2=2-x1|x1=phi(1,1)|
|---|----|----|-----------|-------|-----------|
|x0 | T  | 1  | 1         | 1     | 1         |
|x1 | T  | T  | 1         | 1     | 1         |
|x2 | T  | T  | T         | 1     | 1         |



### Conditional 

Since our control flow is in the same graph as our data flow (that's the major
point of Sea-of-Nodes!), we need not do anything special and our algorithm is
*conditional* already.  Again an example is interesting:

Example:

```java
int x0 = 1;          // x0 = 1
while( rand ) {
    x1 = phi(x0,x4); // x1 = BOT = phi(1,BOT)
    if( x1 == 1 )    // Since x1 is BOT, both true and false arms are taken
        x2 = 2 - x1; // x2 = BOT = 2 - BOT
    else x3 = 99;    // x3 = 99
    x4 = phi(x2,x3); // x4 = BOT = phi(BOT,99)
}
return x4
```

Again the algorithm progresses from left to right, although this time not all
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
program above, but using null pointers instead of integers:

```java
List head = notNullList();
while( rand ) {
    if( head ) head = new List(head,payload);
    else { head = null; default_action(); }
}
```

Here since `head` starts not-null, it can never be null and the
`default_action` can never happen, and the loop can 
simplify:

```java
List head = notNullList();
while( rand ) {
    head = new List(head,payload);
}
```

### Interprocedural 

In **Simple**, we let our SCCP run interprocedurally, again by (nearly) doing nothing:
`CallNodes` call `FunNodes`, `ReturnNodes` return to `CallEndNodes` and they
pass their type information along in exactly the normal way... except for one issue:
there are no edges between `Calls` and `Funs` (nor `Returns` and `CallEnds`).
Such edges make up a [Call Graph](https://en.wikipedia.org/wiki/Call_graph).

We extend the normal SCCP algorithm to observe when a `Call`s function pointer
input changes - when it picks up new function targets.  There are only a finite
number so we represent them exactly with *function indices* as described in an
early chapter.  (Separate compilation adds a special index for the infinite
unknown functions that are called outside this compilation unit).

Once we observe a new *fidx* at a `Call`, we *link* the `Call` with the `Fun`,
and the `Return` with the `CallEnd`.  Such linkage acts exactly as-if we added
a CFG edge from the `Call` to the `Fun`, and the `Call`s arguments to the
`Fun`s `ParmNodes`.  `FunNodes` extend `RegionNodes` (and `ParmNodes` extend
`PhiNodes`), so this linkage uses the same semantics.  `Fun`s become reachable
when called; `Parm`s merge (*meet*) all calling arguments, and so on.
Similarly, `CallEnd`s merge results from all called functions, computing the
*meet* over all the `Return`s.

This *link* step adds a few checks to our algorithm's inner loop, and also
builds a precise Call Graph, and allows our SCCP to run interprocedurally.
Other than discovering a few new edges as the algorithm proceeds, the core
algorithm is unchanged.  We are guaranteed to terminate with a fixed point
solution, and may discover e.g. certain call parameters are constants (or
e.g. not-null) on all calling paths.
