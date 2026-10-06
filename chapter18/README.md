# Chapter 18: Functions and Calls

English | [日本語](README.ja.md)

[Previous: Chapter 17b](../chapter17b/README.md) |
[Next: Chapter 19](../chapter19/README.md)

Through Chapter 17b, a program has one body, with an input `arg` and a result.
We can branch, loop, allocate objects, and update memory, but cannot yet break
up the program into functions.  This chapter adds function values and calls,
including recursive calls.

The graph representation builds on familiar pieces.  A function entry is a
Region, its parameters are Phis, and its exits merge into one Return. Calls
connect these pieces across function boundaries.  When a function has just one
caller, removing that boundary lets ordinary peepholes simplify the combined
graph: this is our first form of inlining.

## Table of Contents

1. [Writing functions](#writing-functions)
2. [Functions in the graph](#functions-in-the-graph)
3. [Memory across calls](#memory-across-calls)
4. [Function types and call targets](#function-types-and-call-targets)
5. [Inlining](#inlining)
6. [Scheduling and execution](#scheduling-and-execution)

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter18) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter17b...linear-chapter18) it to the previous chapter.

## Writing functions

A function expression lists typed parameters, followed by `->` and a body:

```simple
val sq = { int x -> x*x; };
return sq(arg);
```

The initializer creates a function value; the declaration binds it to `sq`.
The call `sq(arg)` evaluates its argument, executes the body with that value
bound to `x`, and yields the result.  The final expression supplies the result
when execution reaches the end of the body. An explicit `return` can exit
earlier.

A function type lists the parameter types and result type, without parameter
names.  The same square function can be declared with an explicit type:

```simple
{int -> int} sq = { int x -> x*x; };
return sq(4); // 16
```

Functions are anonymous values.  Assigning one to a variable can give it a name
for diagnostics, but the binding follows the same rules as other variables.
`val` infers a fixed binding; `var` infers a reassignable one.  Functions can
be passed as arguments, returned as results, and selected by expressions.

Parameters use the explicit declaration syntax from
[Chapter 17a](../chapter17a/README.md): `!Point p` allows writes through a fixed
parameter binding, `Point !p` allows reassignment of a read-only reference,
and `int ~limit` fixes a primitive parameter.  Parameter types are required;
`var` and `val` apply to the variable holding a function, not its parameters.

### Returns and scope

The body has the same statements as the surrounding language. Here an early
return finds the first matching array element:

```simple
val find = { int[] es, int e ->
    for( int i=0; i<es#; i++ )
        if( es[i] == e )
            return i;
    return -1;
};
val es = new int[3];
es[0] = 7;
es[1] = 9;
return find(es,9); // 1
```

All reachable results of one function must have a common type. This is checked
after optimization, so an unreachable return does not cause a type error.
There is no `void` type although a function can return `null` with no syntax,
and callers can ignore a result by using the call as a statement.

Functions do not capture an enclosing function's local variables.  An outer
binding is accessible only when it is fixed and its value is a compile-time
constant.  For example:

```simple
val offset = 2;
val addOffset = { int x -> x+offset; };
return addOffset(arg);
```

Changing `val offset` to `int offset` makes the binding mutable and the function
definition is rejected.  Likewise, `val offset = arg` is fixed but not constant,
so it cannot be used from the nested function.  Fixed function constants satisfy
this rule, allowing one function to refer to another without capturing a frame.

### Recursion

A function can refer to its own binding:

```simple
val fact = { int n ->
    n <= 1 ? 1 : n*fact(n-1);
};
return fact(arg);
```

The parser registers the function entry before parsing its body. A forward
reference to `fact` is resolved when the declaration receives its function
value.  Recursive calls can then find the same entry as calls from outside.
Return-type inference is still pessimistic in this chapter; mutually recursive
definitions can require the stronger analysis introduced with SCCP in
[Chapter 24](../chapter24/README.md).

## Functions in the graph

Until now, `Start` supplied the input to one program body. We now parse that
body as an implicit `main` with an integer parameter `arg`.  The evaluator
enters `main` from `Start` and delivers its result to the outside world.
Existing programs therefore keep their source syntax while acquiring the same
function representation as explicitly declared functions.

### Entries, parameters, and exits

[FunNode](src/main/java/com/seaofnodes/simple/node/FunNode.java) extends
`RegionNode`. Where a Region merges control from different branches, a Fun
merges control from different callers.  A linked call supplies one incoming
control edge.

[ParmNode](src/main/java/com/seaofnodes/simple/node/ParmNode.java) extends
`PhiNode`.  Each parameter selects the argument belonging to the incoming call,
just as a Phi selects the value belonging to an incoming branch.  The parameter
index identifies what the caller supplies:

| Parameter index | Value |
|---|---|
| 0 | Return point: where execution resumes in the caller |
| 1 | Whole memory |
| 2 onward | Source-language arguments |

Each function also has an implicit unknown caller, represented by `Start`.
It supplies the declared parameter types and unknown memory.  This keeps the
entry conservative while calls are still being discovered.

Every source-level `return` contributes to one exit Region. A value Phi merges
the results, and a BulkMemPhi merges their memory states. The resulting
[ReturnNode](src/main/java/com/seaofnodes/simple/node/ReturnNode.java) has inputs
`{control, memory, value, return point}`.  A single exit often folds away the
Region and Phis. `Stop` retains the functions' Returns, including `main`'s.

This gives each function a single entry and a single merged exit even when its
body contains branches, loops, and several return statements.

### Calls and return points

[CallNode](src/main/java/com/seaofnodes/simple/node/CallNode.java) takes inputs
`{control, memory, arguments..., function pointer}`. The final input determines
the target; it can be a constant function or the result of a function-valued
expression.  Argument expressions are evaluated before the Call takes its
control and memory inputs.

Each Call has a [CallEndNode](src/main/java/com/seaofnodes/simple/node/CallEndNode.java),
the point where its caller continues.  CallEnd takes the Call and the Returns
of linked targets as inputs.  Its projections provide control, memory, and the
result, in slots 0, 1, and 2 respectively.

The callee needs to know which CallEnd to return to.  Its hidden parameter 0
carries that return point through the body to the Return. `TypeRPC`, short for
*return program counter*, describes sets of possible return points, much as
function-pointer types describe sets of possible entries.  These are internal
values; a source program does not declare RPC variables.

Call-graph edges describe which functions a call may reach.  They are distinct
from the control flow within one function: walking the graph typically requires
taking care around calls and functions, as most walks are only valid from within
a single function.

## Memory across calls

Earlier chapters split memory lazily by field alias.  That still happens within
a function, but a function's entry memory is unknown: an argument may refer to
an object allocated and modified by its caller.  We can no longer assume that
the incoming heap is empty.

A Call receives the complete memory state, including any slices packaged in a
MemMerge.  Its CallEnd produces a new whole-memory value.  This chapter treats
every call as potentially modifying every alias; knowing which function is
called does not yet provide a summary of which fields it writes.

```simple
struct Counter { int n; };
val bump = { !Counter p -> p.n++; return null; };
val p = new Counter;
int before = p.n;
bump(p);
bump(p);
return before*10+p.n; // 2
```

The first read of `p.n` observes its initialized value. The final read must use
memory after both calls; it cannot reuse the first read.  The two calls also
remain ordered by their memory inputs and outputs.  The writable parameter
permission allows the updates, while the memory edges record when they occur.

Inside a function, MemMerge, MemPhi, and BulkMemPhi continue to expose precise
slices and merge branch or loop states.  At the boundary, memory Parm 1 and the
CallEnd memory result stay opaque.  Global code motion respects a call as a
possible writer when placing Loads.  Calls end their blocks, so a Load that
must precede a Call is scheduled before that terminator.

Inlining removes the call boundary and exposes its actual Loads and Stores
to the existing alias optimizations.  More precise effects for calls that remain
in the graph require later escape analysis.

## Function types and call targets

[TypeFunPtr](src/main/java/com/seaofnodes/simple/type/TypeFunPtr.java) records
an argument tuple, a result type, nullability, and a set of function indices
(`fidxs`). Within an argument signature, each function gets a small integer
index. A bit set can then describe one known function or several possible
functions with that signature. `CodeGen` maps a concrete function identity to
its FunNode; no machine code address is needed to execute this chapter's IR.

For example, both arms below have type `{int -> int}`, but different function
indices:

```simple
val f = arg ? { int x -> x+x; } : { int x -> x*x; };
return f(3); // 6 when arg is nonzero, otherwise 9
```

The Phi for `f` combines the two function-pointer types.  Its set of targets
contains both functions; choosing a function does not execute its body.

Function pointers can also be nullable, using the familiar `?` suffix:

```simple
{int -> int}? f = arg ? { int x -> x*x; } : null;
if( f ) return f(3);
return 0;
```

The guard proves the pointer non-null at the call. A call also checks its
argument count, types, and access permissions. Struct pointers and function
pointers share the distinguished `null` value, represented by `Type.NIL`;
`TypeNil` supplies their common nullability machinery.

### Linking known targets

Here *linking* means adding IR edges between a call and a function. It is not
object-file linking. For each known target, the compiler adds the Call as an
input to the Fun, adds arguments to its Parms, and adds its Return as an input
to the CallEnd. Corresponding input positions preserve the relationship
between a caller and its arguments.

This chapter already links finite sets of known targets, including both
functions in the conditional example above.  The infinite set that still
includes unknown functions cannot be enumerated.  We avoid representing that
uncertainty with edges from every call to every function, which would grow
quadratically.

Instead, a Fun retains its *unknown-caller* input.  Its parameters must accept
their declared types even when the calls found so far pass constants.  Finding
one caller is not proof that no other callers exist!  Similarly, CallEnd obtains
its result type from the function-pointer type and keeps its memory result
unknown.  Linked Return edges alone do not remove these conservative assumptions.

Inlining relies on a local proof that a function has only one caller.  The later
SCCP chapter develops stronger analysis of which functions and calls are
reachable, allowing more information to flow across boundaries that remain.

## Inlining

Consider a function whose value is used by exactly one call:

```simple
val inc = { int x -> x+1; };
return inc(arg);
```

Once this call links to `inc`, CallEnd can prove that it has one target and
that the target has no other uses of its function pointer. With valid
arguments and no self-recursive call, the function body can become part of
the caller.

| Before inlining | After inlining |
|---|---|
| ![The call supplies arg to inc's parameter and receives its sum.](docs/inline-before.svg) | ![The same Add uses arg directly and supplies main's return.](docs/inline-after.svg) |

These are schematic slices of the graph, with stable node IDs across the
rewrite.  They show the argument, result, and linking edges; the surrounding
control flow, memory, and RPC plumbing are omitted.  The `int` input to `Parm_x`
represents the unknown caller's argument before inlining.

The rewrite removes the unknown-caller path and connects the function entry to
the call's incoming control.  Ordinary Region and Phi simplifications then
replace the parameter with the actual argument.  CallEnd's projections become
the callee's returned control, memory, and value.  The Call, function boundary,
and return-point machinery disappear as their uses disappear.

The Add remains, but now consumes `arg` directly. If the argument had instead
been `3`, the same constant-folding rules used since Chapter 2 would produce
`4`.  Loads and Stores exposed by inlining can similarly simplify using the
existing memory rules.

This form of inlining moves a body with one caller into that caller; it does
not clone the body for several call sites.  A recursive function such as
`fact` retains its calls and is handled by the evaluator.

## Scheduling and execution

New in this chapter is
[CodeGen](src/main/java/com/seaofnodes/simple/CodeGen.java) which owns the
compilation process and its function lookup table. It enforces the phase order:

| Phase | Purpose |
|---|---|
| Parse | Build the functions and calls directly in the IR. |
| Opto | Iterate peepholes, including call linking and trivial inlining. |
| TypeCheck | Check the surviving graph after types and dead control have settled. |
| Schedule | Use global code motion to place operations in basic blocks. |
| LocalSched | Order operations within each block for execution. |

For example:

```java
CodeGen code = new CodeGen("val sq = { int x -> x*x; }; return sq(arg);");
code.parse().opto().typeCheck().GCM().localSched();
```

Global code motion extends the work of [Chapter 13](../chapter13/README.md) to
multiple functions.  It keeps their bodies separate, gives shared constant
expressions a local copy in each function that uses them, and respects memory
effects across calls.

[ListScheduler](src/main/java/com/seaofnodes/simple/ListScheduler.java) then
orders each block.  It counts unscheduled local dependencies, chooses a ready
operation, and makes its users ready as their dependencies are satisfied.  Phis
belong at entry and control transfers at exit.  There is room for all sorts of
improvements - there is no machine timing model here, but this is out of scope
for this chapter.  The goal remains a legal execution order.

[Eval2](src/test/java/com/seaofnodes/simple/Eval2.java) executes that schedule,
treating IR operations as instructions with an unlimited supply of value
slots. Each invocation has its own frame, so recursive invocations of the same
nodes keep distinct values. At a call it saves the caller's frame and return
point, creates a callee frame, and transfers to the selected function. At a
return it restores the caller and delivers the value to its CallEnd. At a
Region, it reads all selected Phi inputs before assigning any results, so
parallel assignments remain correct.

We now have an executable program made of scheduled functions, still expressed
in machine-independent operations. [Chapter 19](../chapter19/README.md) inserts
instruction selection before scheduling: those ideal operations become
instructions for a chosen CPU, with its register constraints and calling
convention.
