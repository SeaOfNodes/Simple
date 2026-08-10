# Chapter 27: Escape Analysis and Unknown Callers

English | [日本語](README.ja.md)

[Previous: Chapter 26](../chapter26/README.md) |
[Next: Chapter 28a](../chapter28a/README.md)


We run a simple form of escape analysis in this chapter, tracking both
functions and some aliases across all calls and memory updates.  One of the
goals here is to discover when we've found *all* the callers of a function; at
this point we can drop the conservative "unknown caller" input and run
constant propagation through a function's arguments.

The preceding compiler kept an unknown caller for every surviving function
address. That was safe, but it lost facts even when all uses stayed within the
program. Escape analysis makes that boundary more precise.

## A callback that stays within known code

```simple
val run = { int n ->
    val power = { int base, int exponent ->
        if (exponent <= 0) return 1;
        int smaller = power(base, exponent - 1);
        return base * smaller;
    };
    val sumPowers = { int n, {int int -> int} f ->
        if (n <= 0) return 0;
        return f(2,n) + sumPowers(n-1,f);
    };
    return sumPowers(n,power);
};
return run(arg);
```

For `arg == 3`, this computes `2 + 4 + 8 == 14`.  Both functions are recursive;
their calls remain in the graph.  In the prior chapters, the address passed as
`f` keeps an unknown caller for `power`, so `base` stays `i64`.

Here the compiler follows the address through the arguments and recursive
calls.  Neither function returns it, stores it where unknown code can find it,
nor passes it to an unknown callee.  Every call to `power` is accounted for.
SCCP can then infer `base == 2`, including in its recursive body.  The multiply
by 2 can become a shift without inlining or cloning either recursive body - and
we open ourselves up to further optimizations.

Passing `power` to an unknown callee, or passing it to a native function,
changes the answer.  The external caller may choose another base.  The compiler
must retain `power` and give its parameters their declared bounds.


## Publishing an object and exposing it to unknown code

The construction chapter's `EscapeNode` publishes initialized fields into
ordinary public memory. That alone does not expose the completed object to
unknown code; exposure depends on what happens to the `self` pointer.

Escape analysis starts at the boundary with unknown code.  Returning an object
across that boundary exposes its field aliases.  An exposed field can contain
another object or a callback, so discovery continues through memory:

```text
external result -> Wrap.child -> Holder.f -> callback -> unknown caller
```

The same rule applies to arguments passed to native code.  Native code may keep
an address after the call returns, or use it on a path that never returns.
`Stop` therefore also collects exposure directly from reachable native calls.
A call to a known Simple function instead passes the values and memory to that
function's graph; SCCP follows those uses to determine what escapes.

This is a conservative analysis by field alias.  Two instances of the same
struct share their field aliases.  Exposing one may therefore expose addresses
stored in the same fields of another.  It does not identify each allocation
separately.

Another way to think of this: this analysis is what we get "for free" from just
running SCCP. We extend and sharpen the lattice a little more, to allow more
precise tracking.


## Memory carries exposure sets

`TypeMem` now carries two interned sets alongside its contents:

* `_escFs`: function identities reachable through this memory.
* `_escAs`: field aliases reachable through this memory.

`XInt`, already used for function sets, represents both.  An empty set means no
address has been discovered; a full set is the conservative unknown case.
Meeting summaries unions the sets.  Dual types complement them, so the existing
lattice operations and tests still apply. The `XInt` utilities operate directly
on shared, immutable `int[]` arrays; they add no wrapper object. Interning lets
these sets follow the same sharing rules as the rest of the Types.

Stores record addresses carried by their values.  Memory Phis combine those
facts over live paths. `MemMergeNode` gathers the slices whose aliases are
exposed, rather than making every locally stored callback externally callable.
If an exposed field reveals another alias, that slice participates as well.  In
short, we follow escaping values through memory using the existing
`Node.compute` and `Type` machinery.

The hidden constructor helpers still keep fields in local SSA values and use
ordinary Phis.  Their private-memory arguments and results describe the object
being built; they are not external returns.  Initializing Stores and
publication preserve the address facts when those fields enter ordinary memory.



## Start and Stop close the cycle

Start represents unknown callers. Stop collects what known code can expose to
them.  The two form an analysis cycle:

```text
Start -> callable functions -> returns and exposed memory -> Stop
  ^                                                         |
  +---------------- newly exposed addresses ----------------+
```

Before SCCP, parsing and pessimistic iteration keep conservative callers and
memory. During SCCP, functions other than the chosen entry points lose their
unknown-caller edges unless their function pointers escape. Concrete calls
rebuild the known call graph.

Stop combines the results and memory visible to external callers.  Those
callers already hold their object arguments, so writes through such arguments
also count.  Start feeds the resulting alias set back into memory summaries.
If a function identity escapes, SCCP restores a Start edge and its conservative
parameter inputs.  That function can reveal further addresses through its own
calls and returns.  The ordinary worklist repeats until types, exposure sets,
and call edges agree - i.e. the normal SCCP reaches a fixed point.

An explicit `main`, or one synthesized for a top-level return, starts discovery.
Without one, the code is treated as a library: ordinary named functions outside `sys`
seed the unknown entry points. Module privacy refines this rule in the next
chapter.

Once SCCP finishes, you might have ordinary public functions callable from
anywhere, or functions called via their name (direct field load holding the
function pointer), or via a function pointer that's only used in a few locations,
or it might escape in a region and get mixed with other function pointers, or
you might never call a function at all.  If a function is getting called, but
never from unknown code, its arguments can sharpen across the `meet` of all
call sites, perhaps proving an argument non-null and avoiding explicit tests.

After optimization, `FinalizeIR` freezes the external summary from `Start/Stop`
and removes the `Stop->Start` edge SCCP uses to track escapes across external
calls. This summary says which functions need unknown callers; it does not
control ELF symbol visibility. The current ELF writer emits every retained
named function as a global symbol.


## Construction boundary to review

The language still prevents a partially built object from escaping. Constructor
fields remain local values until helper exit. Exact local aliases of `self`
can access those values; passing `self` to ordinary code, storing it in an
object, or accessing fields through a merge that may choose another object
remains rejected in this snapshot.

Discovering that a completed object stays private does not prove that an early
read of an uninitialized field is legal. These are separate checks. The runnable
construction and escape snapshots now make that border available for review.
Neither topic requires the other to be taught first.

## Build and check

From this directory, run `make lib tests CTAGS=`. From the repository root, run:

```bash
make tests CHAPTERS=chapter27 CTAGS=
```

`EscapeTest` checks the recursive callback, direct and indirect returned
addresses, and native exposure. The inherited construction, language, memory,
lattice, evaluator, and native/backend suites also run.
