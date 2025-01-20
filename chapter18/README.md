# Chapter 18: Functions


I hardly know where to begin!  So many things changed here, mostly as indirect
consequences of supporting functions.

Highlights of the changes:

- Function types, and a rework of the type lattice diagrams.
- Return Program Counter types, or Return PC or RPC for short.
- A distinguished `null` which can be used for functions, RPCs and struct pointers.
- Functions themselves can be parsed and evaluated; recursive functions are supported.
- Function calls work, call sites can link, and functions can be inlined.
- The top-level is now a call to function `main`, and this changed most tests.
- A top-level compile driver, which enforces optimization steps.
- A local scheduler.
- A new evaluator which relies on at code motion, both global and local.
- A graphic animated viewer for peepholes.

Functions are *not* closures in this chapter; that brings about far more
changes and subtle complexities than sensible for such a large chapter.

Functions can only refer to out of scope variables if they are some final
constant.  This generally includes e.g. recursive function pointers.

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter18) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter17...linear-chapter18) it to the previous chapter.

## Function and RPC Types

```
{ argtype, argtype, ... -> rettype }
```

A leading `{` character denotes the start of a function or function type, then
a list of argument types, and an arrow `->` and the return type.

Functions variables are normal variables and assigned the same way:

`functiontype fcn = function-typed-expr;`

and function typed variables can be inferred, so `val` and `var` on the left
hand side is fine:

`val sq = { int x -> x*x; };`

Internally, functions have a `TypeTuple` for the arguments, a return `Type`,
and a *function index* spelled as a `fidx` in the code.  Function indices are
unique small dense integers, one per unique function.  They are mapped
1-to-1 to the final code address of the generated function code.

Function pointers can refer to more than one function with the same signature,
and the `TypeFunPtr` tracks this as a bit set with the same tuple for signature
and return.

Function types can be `null` just like references.

Functions cannot return `void`, but they can return `null` and have their
return value ignored.  A syntactic sugar for void returns may be added in the
future.


### Return Program Counters

Just like functions have a unique "function index" mapped one to one with
function start addresses, call sites generate a unique "return program
counter", one per unique call site.  These are required for the evaluator to return
from functions without itself relying on the implementing language's
(e.g. Java) stack.  This is the type for such values and `TypeRPC` is very
similar to a `TypeFunPtr` except that the signature doesn't matter.

In spirit, this is part of a `continuation` and would be required to do IR
analysis of a hypothetical `call/cc` operation.  That is outside our current
scope, so for now these are only used in the evaluator.


## Functions

Functions themselves using the same syntax with as function types, but filled in:

```
{ flt x, flt y ->  // Signature
  sqrt(x*x+y*y);   // Body
}
```

Used in a declaration statement:
```
// TYPE        VAR  =   EXPR
{flt,flt->flt} dist = { flt x, flt y ->
  sqrt(x*x+y*y);
}
```

With `var`:
```
var dist = { flt x, flt y -> sqrt(x*x*,y*y); }
```

Functions are called in the usual way:
`dist( 1.2, 2.3 ) // yields 2.59422435`

Functions can have zero arguments, in which case the `->` argument seperator is
optional.  This allows any section of code to be "thunked" by wrapping it in
`{}`.

`just5 = { -> 5 } // With    arrow`

`just5 = {    5 } // Without arrow`

Called:
`just5() // Returns 5`

Functions are always anonymous.  When finally assigned to a variable, they will
pick up the variable name for debugging and display purposes, but this has no
semantics meaning.  Here is a function variable referring to more than one
anonymous function; the resulting function either doubles or squares:

`val fcn = arg ? { int x -> x+x; } : { int x -> x*x; };`

`fcn(3) // Prints either 6 or 9, depending on arg`

Functions can be recursive:
```
val fact = { int n -> 
  n <=1 ? n : n*fact(n-1); 
}; 
return fact(4); // Returns 4! or 24
```

You can early return as normal out of functions:
```
val find = { int[] es, int e ->
  for( int i=0; i<es#; i++ )
    if( es[i] == e )
      return i;  // Found matching element, return the index
  return -1;     // Return -1 for not-found
};
return find;
```

`FunNodes` define functions, have a pointer to the one `ReturnNode` (which
itself has a back pointer to the `Fun`), extend a `RegionNode` and are followed
by `ParmNodes` which themselves extend `PhiNodes`.  All `Calls` which reach a
`Fun` merge all their arguments into the `Parms`; there is an extra `Parm` for
memory and for the return point back to the `CallEnd`: a RPC.  So a `Fun` is
basically a fancy merge point, merging all calls that reach here.

Unlike prior chapters, all the `returns` from a single function are gathered
together into a single `ReturnNode` point, and they must all be of the same
general type.  `Returns` take in a Control, a Memory, a return value, and the
`RPC` that was handed to the function when called.

When looking at the returned IR, the `StopNode` now reports one return for each
function, including `main`: 
`Stop[ return find; return Phi(Region,int,-1); ]`


### Calls

Calls have the usual syntax: `fcn(3)`.  Internally a `CallNode` takes in
Control, Memory, all the normal arguments, and a hidden last argument which is
the function pointer.  For calls to named functions this last argument will be
a `ConstantNode` of the named function type, but in general it can be the
result of any function-typed expression.

The call arguments passed to the matching `ParmNode`s in the function, 
with the `Call`s constant `RPC` being passed to the matching RPC `Parm`.

After a `Call` is a `CallEndNode`, internally abbreviated as `cend`.  The
`CallEnd` will take the `Call` as an input, and also every *linked* function:
functions the call-site *knows* it will call.  This will be expanded later to
be a conservative approximation to the *Call Graph*, with each `CallEnd`
*linked* to ever function it *may* call; if a function is not linked it can not
be called from here.  This requires a global analysis (fast, cheap,
incremental, and global), not in this chapter.  So for the moment we only
link exact constant functions.

If a call site is linked to a single function, and that single function is only
called by this one call site (its function pointer is only used here, obvious
from GVN) then the function inlines in the IR.

`CallEnd`s are followed by projections for Control, Memory and the return value.



## CodeGen - The Compile Driver

There is now a top-level compile driver that enforces a phase ordering, and
allows multiple compilations; the Fuzzer uses this to compare various generated
programs.  The phases (for now) are:

- *Parse* - Convert program text to Simple IR
- *Opto* - General optimizations; for now iterate peepholes.
- *TypeCheck* - Error checking after all types have propagated.  This mostly
  reports on failed null checks.
- *Schedule* - Global Code Motion scheduler.  After this phase, all nodes belong
  in some basic block, with the normal suspect CFGNodes being basic blocks.
- *LocalSched* - a local scheduler.  Its completely naive, except it enforces
  some required rules: Phis appear at block heads, branchs at block exits.
  There's room in the algorithm for a much more sophisticated list scheduler.
  The `Eval2` evaluator requires this information.
- *RegAlloc* - Not implemented (yet).

Several globals moved from the Parser to CodeGen, and probably several others
ought to move here.

There is a very nice `toString()` here; hovering over the `code` variable in
the debug winow will pretty-print the IR "as if" globally scheduled, and the
code becomes very readable.


## Eval2 - A new Evaluator

There is now a second evaluator that uses the scheduling information to
evaluate in a very straightforward way.  Essentially the normal IR nodes are
treated like a special "machine instruction set" with infinite registers, and a
globally correct schedule.  This evaluator supports functions and calls (and
recursive calls).


## Graph Visualizer

Run as `make view` or `java JSViewer`, type your program in the text box, click
outside the box and then use the arrow keys to view IR generator both forwards
and backwards.

Nodes are color coded according to type, same as the lattice diagrams.  Nodes
are shaped according to node function as well.  At the bottom are `ScopeNode`s,
which only exist for the Parser but are actual Nodes and have `use->def` edges
into the IR.

