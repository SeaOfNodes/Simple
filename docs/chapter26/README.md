# Chapter 26: Construction and Private Memory

English | [日本語](README.ja.md)

[Previous: Chapter 25](../chapter25/README.md) |
[Next: Chapter 27](../chapter27/README.md)


The previous chapter built SSA before it knew all the types.  This chapter uses
that freedom to move object initialization into functions.  The new problem is
memory: a constructor must remember what it has written to this object without
mixing those values with every older object of the same type.

We keep the object private while it is built, then publish its initialized
fields to public memory.  No partially-built instance can ever escape (unlike
some languages where the `this` object can escape before final fields are
actually set and final).  Finding all the ways an object can escape comes next.


## Constructors are calls

```simple
struct Box {
    int x = 7;
    int[] data;
    new Box = { int n ->
        data = new int[2];
        x = n;
        data[0] = x + 1;
    };
    val get = { -> x + data[0]; };
};
val a = new Box(arg);
val b = new Box(arg + 10);
return a.get() * 100 + b.get();
```

For `arg == 2`, this returns `525`.  Each call allocates its own `Box` and array.
The declaration `new Box = { ... };` supplies the constructor body.  The body
still parses as an anonymous function; `new` marks its role as a constructor.
The expression `new Box(...)` allocates an object and calls it.  A constructor
returns *private memory*, not a source-level value.

The parser creates two helpers for each struct:

* `Box.<init>` runs the field initializers, in declaration order.
* `Box.<new>` allocates the object, calls `<init>`, calls the user constructor
  if there is one, publishes the fields, and returns the pointer.

The initializer and user constructor receive hidden `self` and `#selfMem`
arguments.  *Public* memory still travels through input 1 of calls and returns;
the *private* memory is a separate argument and result.  A constructor can allocate
a public array or update an already public object without losing its own field state.

The old `new Box { ... }` syntax remains an inline construction block. It runs
the field initializers, then its block, then publishes; it does not also call
the named user constructor.  Ordinary methods still use public memory.


## Fields are local values during construction

Public memory describes all objects with a given field alias. A public Store
therefore combines the new value with values already in that alias.
E.g. writing `a.x = 3` cannot prove that `b.x` is also 3, so the general `.x`
field alias is usually some form of `BOTTOM`.

During construction we already know which object each field belongs to, and
the language prevents its partial state from becoming visible.  The parser can
therefore keep its fields as local SSA values.  Both `x` and `self.x` refer to
the same local slot, although an argument named `x` can shadow the bare name.
An assignment updates that slot; it does not emit a Store.

Branches merge the values with ordinary Phis.  Loops use ordinary loop Phis,
including their existing integer range widening. For example:

```simple
struct Counter {
    val x = 0;
    new Counter = { int n -> while (x < n) x++; };
};
return new Counter(arg).x;
```

The constructor loop carries the value of `x`. It needs no memory Phi and
performs no field Store on each trip.  At the constructor exit, the compiler
stores the final value into the new object.  This is the result scalar
replacement would give us, built directly from the construction rules.

Even final data fields can change during construction; they become final when
the constructor finishes. `x = 1; x = 2;` leaves the local value 2 for the exit
Store. In the loop above, `x++` updates a local Phi, not memory. No sequence of
final field Stores needs cleaning up inside the loop.

A constant default does not make a field shared by every instance: a later
constructor may change it. Inferred field types therefore describe the value's
family, not just its initial constant. Ordinary local `val` bindings remain
final. Shared function declarations are also fixed; a constructor cannot replace
one of those functions.

Private memory is still useful at the helper boundaries. `NewNode` produces
the pointer and its initial private memory. The hidden initializer computes
defaults as local values and emits ordinary initializing Stores at exit.  The
user constructor loads those fields into local slots, computes their final
values, and emits its own exit Stores.  Each Store produces one precise alias;
`MemMergeNode` packages the slices for the helper result. `TypeMem` records the
field values across the call, without joining them with older objects.

```text
New -> <init>: local defaults -> field Stores
    -> constructor: local values and Phis -> field Stores
    -> publication -> public field aliases
```

Constructor signatures are bound at their declarations.  Their bodies are
parsed after the struct's field list closes, so even a later-declared field
has a local slot before a branch or loop is built.

Arrays use the same publication boundary: zero-filled elements and an
initialized length are published before the array is returned.  Required
non-null fields have no legal zero default.  Reading one before it is written
is an error.

## Publish only after completion

`EscapeNode` publishes one field.  It consumes control, public memory, private
memory, and `self`; its result joins the field's value into the public alias.
Publication has no machine instruction, but its memory edges keep initializing
Stores before later Loads and Stores.  Every field is published independently,
and scheduling may well interleave the initializing writes from many unrelated
allocations and public memory updates.

Before publication, every required field must be initialized on every live
path.  This constructor is rejected:

```simple
struct Box {
    int[] data;
    new Box = { int n -> if (n) data = new int[2]; };
};
return new Box(arg);
```

`int[] data` requires a non-null initial value. If `n` is zero, `data` is never
set. The current parser follows explicit missing-initialization values through
the constructor's merges and checks its exit. This happens before SCCP: even
calling this constructor only with `new Box(1)` does not rescue it. Allowing
later type analysis to prove completion would require deferring this check.


## Helper calls and *final* fields during construction

Constructor code may read the unfinished object, but never an uninitialized
field. Ordinary code must not observe it before construction finishes.

We would like to allow helper calls when optimization proves they do not expose
the unfinished object. Inlining could turn a helper's field accesses into local
values. After optimization reaches a fixed point, each final field would have
a final Store: before that line, accesses use local values; after it, the field
can be read but never written. Mutable fields can still be written later.

This is a proposed relaxation, not implemented in this snapshot. The parser
currently rejects passing `self` to an ordinary method before inlining can
prove it safe. For example, we would like this constructor to work:

```simple
struct Vec {
    int    _max;
    u8[~] !_buf; // Not-null, must be set before reading

    new Vec = { ->
        _buf = new u8[1];
        clear(); // Would be safe after inlining; currently rejected
    };
    val clear = { ->
        // Keep at most 1K of storage in a cleared Vec
        if( _buf# > 1024 ) _buf = new u8[1024];
        _max=0;
    };
};
```

## Build and check

From this directory:

```bash
make lib tests CTAGS=
```

Or from the repository root:

```bash
make tests CHAPTERS=chapter26 CTAGS=
```

`ConstructionTest` exercises constructor calls, independent instances, dynamic
defaults, completion errors, forward fields and methods, private loops, native
execution, and ARM/RISC-V execution. The inherited language, memory, lattice,
and backend suites run alongside it.
`FinalConstructionTest` checks repeated assignments and loops over final fields,
reads through methods, rejection of later writes, and native execution. It also
checks that the loop's field Stores are outside the loop.
