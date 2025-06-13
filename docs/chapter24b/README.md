# Chapter 24b: SCCP Across Functions

English | [日本語](README.ja.md)

[Previous: Chapter 24a](../chapter24a/README.md) |
[Next: Chapter 25](../chapter25/README.md)

The previous chapter runs SCCP within each function, and assumes very
conservative typing across calls.  This chapter lets the same worklist
discover call edges and propagate values between functions.

## Discovering calls while solving types

In **Simple**, we let our SCCP run interprocedurally, again by (nearly) doing nothing:
`CallNodes` call `FunNodes`, `ReturnNodes` return to `CallEndNodes` and they
pass their type information along in exactly the normal way... except for one issue:
there are no edges between `Calls` and `Funs` (nor `Returns` and `CallEnds`).
Such edges make up a [Call Graph](https://en.wikipedia.org/wiki/Call_graph).

We extend the normal SCCP algorithm to observe when a `Call`s function pointer
input changes - when it picks up new function targets.  There are only a finite
number so we represent them exactly with *function indices*, or *fidx*
internally, as described in earlier chapters. Unknown targets still require
conservative summaries. Later, separate compilation must also preserve function
identities across object files.

Once we observe a new *fidx* at a `Call`, we *link* the `Call` with the `Fun`,
and the `Return` with the `CallEnd`.  Such linkage acts exactly as-if we added
a CFG edge from the `Call` to the `Fun`, and data edges from the `Call`s arguments
to the `Fun`s `ParmNodes`.  `FunNodes` extend `RegionNodes` (and `ParmNodes` extend
`PhiNodes`), so this linkage uses the same semantics.  `Fun`s become reachable
when called; `Parm`s merge (*meet*) all calling arguments, and so on.
Similarly, `CallEnd`s merge results from all called functions, computing the
*meet* over all the `Return`s.

This *link* step adds a few checks to our algorithm's inner loop, and also
builds a conservative Call Graph from the targets found by type propagation.
Other than discovering a few new edges as the algorithm proceeds, the core
algorithm is unchanged.  We are guaranteed to terminate with a fixed point
solution, and may discover e.g. certain call parameters are constants (or
e.g. not-null) on all calling paths.

## Recursion is a loop in disguise

We use *widening* of integer ranges at loop heads, as described in the
[previous chapter](../chapter24a/README.md#convergence-and-calls).
This allows us to have a sharper notion of integers over `BOTTOM` without also
allowing the range to grow one integer at a time. The same issue arises during
recursive calls: an argument can flow from a Parm through arithmetic into a
recursive call, then back to the same Parm.

```simple
val f = { int x -> x == 0 ? 0 : f(x-1)+1; };
```

If the only outside call is `f(999)`, parameter `x` starts with the range
`[999,999]`. We'll then track the recursive call to `f(998)` via normal SCCP
and discover that `x` has range `[998,999]`. Lather, rinse, repeat,
and we're at `[997,999]`. We need to *widen* integer parameters at `Parms`,
not just loop-head `Phis`.

The same issue arises during the recursive return: the first return will be
with `0` when `x` is zero, then we return `0+1` or the range `[0,1]`, then range
`[0,2]`, etc. `CallEnds` also need to widen.

Loop Phis, Parms, and CallEnds get widening points, and since this same issue
hits in 3 unrelated places, their comments all cross-reference each other.


## Unknown callers

A `FunPtrNode` (function address) that survives outside a direct call may be
called by code we cannot see.  We have to assume the address escapes into
unknown code, which later calls it.  We preserve its unknown-caller input,
since we cannot tell what that unknown caller will pass.  Precise escape
analysis is introduced later to improve this.

## Building and checking

Run `make lib` once for Java test dependencies, then `make tests` in this
directory. From the repository root, use
`make tests CHAPTERS=chapter24b`.
The inherited tests retain their historical class names.

The [grammar](24-grammar.md) includes chained comparisons introduced in
Chapter 23 beside short-circuit expressions. Allocator tuning is covered in
the [appendix](../appendices/optimizer-tuning.md).
