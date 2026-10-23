# Construction and Private Memory

English | [日本語](README.ja.md)

[Previous: SSA Before Types Are Known](../chapter-incomplete-types/README.md) |
[Next: Escape Analysis and Unknown Callers](../chapter-escape/README.md)


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
    int x;
    new Counter = { int n -> while (x < n) x++; };
};
return new Counter(arg).x;
```

The constructor loop carries the value of `x`. It needs no memory Phi and
performs no field Store on each trip.  At the constructor exit, the compiler
stores the final value into the new object.  This is the result scalar
replacement would give us, built directly from the construction rules.

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
path.  This is rejected when `arg` can be zero:

```simple
struct Box {
    int[] data;
    new Box = { int n -> if (n) data = new int[2]; };
};
return new Box(arg);
```

This is rejected because `int[] data` requires a non-null initial value, and if
`arg` is null, `data` is never set not-null.  This check is based on the type
of every field at the constructor exit; if type analysis (e.g. SCCP) determines
the field is set, the code is good; if not then not.

## Build and check

From this directory:

```bash
make lib tests CTAGS=
```

Or from the repository root:

```bash
make tests CHAPTERS=chapter-construction CTAGS=
```

`ConstructionTest` exercises constructor calls, independent instances, dynamic
defaults, completion errors, forward fields and methods, private loops, native
execution, and ARM/RISC-V execution. The inherited language, memory, lattice,
and backend suites run alongside it.
