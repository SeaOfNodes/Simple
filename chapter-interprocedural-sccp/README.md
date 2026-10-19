# SCCP Across Functions

English | [日本語](README.ja.md)

[Previous: SCCP Within a Function](../chapter-sccp/README.md) |
[Split outline](../docs/chapter-split.md)

The previous chapter solves each function with conservative call interfaces.
This chapter lets the same worklist discover call edges and propagate values
between functions. It is an independent snapshot of the interprocedural
compiler previously taught in Chapter 24.

## Discovering calls while solving types

In **Simple**, we let our SCCP run interprocedurally, again by (nearly) doing nothing:
`CallNodes` call `FunNodes`, `ReturnNodes` return to `CallEndNodes` and they
pass their type information along in exactly the normal way... except for one issue:
there are no edges between `Calls` and `Funs` (nor `Returns` and `CallEnds`).
Such edges make up a [Call Graph](https://en.wikipedia.org/wiki/Call_graph).

We extend the normal SCCP algorithm to observe when a `Call`s function pointer
input changes - when it picks up new function targets.  There are only a finite
number so we represent them exactly with *function indices* as described in an
earlier chapter.  (Separate compilation adds a special index for the infinite
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
builds a precise Call Graph which allows our SCCP to run interprocedurally.
Other than discovering a few new edges as the algorithm proceeds, the core
algorithm is unchanged.  We are guaranteed to terminate with a fixed point
solution, and may discover e.g. certain call parameters are constants (or
e.g. not-null) on all calling paths.

## Recursion is a loop in disguise

An argument can flow from a Parm through arithmetic into a recursive call,
then back to the same Parm. A result has a separate cycle: Return -> CallEnd
-> arithmetic -> Phi -> Return. Widening at Parm alone cannot stop a return
range from growing in `f(x-1)+1`.

Loop Phis, Parms, and CallEnds therefore need widening points. Their comments
cross-reference each other. Constants and above-center ranges remain narrow;
the growing below-center ranges widen within their type bounds.

`SccpTest` uses the same two calls as the previous chapter and checks that the
parameter now receives [1,2]. `Chapter24Test.testRecursiveWidening` exercises
argument and return cycles at several worklist seeds and evaluates the results.

## Unknown callers

A function address that survives outside a direct call may be called by code
we cannot see. Preserve its unknown-caller input. Precise escape analysis is
introduced later; this chapter does not infer exposure through memory.

## Building and checking

Run `make lib` once for Java test dependencies, then `make tests` in this
directory. From the repository root, use
`make tests CHAPTERS=chapter-interprocedural-sccp`.
The inherited tests retain their historical class names.

The [grammar](docs/24-grammar.md) includes chained comparisons inherited from
the extraction baseline. Their planned teaching home is with short-circuit
expressions. Allocator tuning is covered separately by the planned appendix.
