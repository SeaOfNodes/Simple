# Chapter 25: SSA Before Types Are Known

English | [日本語](README.ja.md)

[Previous: Chapter 24b](../chapter24b/README.md) |
[Next: Chapter 26](../chapter26/README.md)

The previous compiler solved for recursive types and calls, but its parser
still needs types to choose e.g. float-vs-integer arithmetic instructions or
field types. Forward references, external references, or even simple loop
structures can hide what's needed.

This chapter records the operations first and resolves their type-dependent
parts later.  Scalar types, numeric modes, conversions, guards, symbolic
fields, and unbounded value Phis and Loads have to get typed *together*, as
they all have mutually interlocking type dependencies.


## A definition can follow its use

```simple
val add = { Later p -> return p.x + 1; };
struct Later { flt x; };
return add(new Later { x = 2.5; });
```

When parsing `p.x + 1`, the compiler knows the receiver's name (type `Later`),
but not its fields!  It makes a Load with a symbolic field offset `FldOff` and
an `Add` whose numeric mode is still unset: might be an integer add or a
floating-point add. Later we parse `Later`, which supplies the field and its
storage type.  Type propagation then selects floating-point addition and
converts the integer `1` operand.  The result is some floating point math
`2.5 + (flt)1`, which folds to `3.5`.

Forward writes work the same way.  Until a field is known, its Store carries
whole memory - it aliases everything until we know what it aliases.  Once
resolved, the Store converts its value to the declared field type and updates
that field's alias, which generally will result in a cascade of peepholes
sharpening the memory edges. Resolving the alias preserves the Store's control
input. Update operators such as `x += y` work like `x = x + y`, while evaluating
the destination only once. Storing into a `u8` field may narrow an integer
result; storing into an `f64` field may instead convert it to floating point.

`FldOff` deserves more mention: it represents a named field reference (Load or
Store) to an incomplete `TypeStruct`. In the example above, that's `p.x`: field
`x` of a `Later` whose fields are not yet declared. The struct can gain more
fields, and the field type itself is still unknown, so
`FldOff` really knows very little about what's going on. It's more of a *shape*
placeholder, and awaits stronger types to peephole into a "real" field
reference.


## Values need their own bounds

The parser always knows when it has *values* vs memory or control, but maybe
not whether something is an int or a flt until much later.  Instead it uses
`TypeScalar` for this. `TypeScalar` contains integers, floats, object
pointers, and function pointers.  These are classic *values* in the sense that
they represent small concrete things that typically fit in a machine register.
Values exclude control and memory.  Scalar bottom says that a value's family is
not known; its top represents an unreachable scalar result.  A scalar union can
still be tested for truth, even when its alternatives use different families;
integers, floats, and pointers all carry a notion of zero.

A scalar Phi now meets its live inputs; it no longer carries a parser-supplied
lower bound.  An unfinished loop Phi starts at scalar bottom because its
backedge is missing.  Parameters retain their declared interface types, and
memory Phis retain their memory role. Loop Phis, Parms, and recursive CallEnds
still do widening.


## Record conversions and branch facts

The parser ends up inserting `Convert` nodes where it used to directly convert
values. This includes converting integers to match float operators and narrowing
values stored into integer fields. Loads derive their value types from memory;
they no longer impose a parser-supplied bound. A bad load or store still produces
bottom. Type checking looks through a conversion with an already-broken input
so it can report the original error.

`Convert` records a known destination type, such as a local declaration or
function parameter.  It waits for its input type to sharpen, and then peepholes
into a widening, truncation, permission check, or no operation.

`Guard` records a branch fact: zero/null or nonzero/non-null.  Its value family
comes from its input when known.  The control edge keeps the fact on the path
where it was proved.  A guard output is the same runtime value as its input; it
emits no instruction, just the sharpened type result.  Guards are typically
used to track not-null pointer tests across large areas.

Arithmetic nodes carry a `mode`: a 3-state enum tracking an unset, integer, or
floating-point mode.  Known inputs can settle it during peepholes.  SCCP
resolves remaining recursive numeric components, then propagates the result
again.  Once the mode is known, the machine instruction selection sections will
pick the correct machine ops.


## Complete declarations, then update the graph

```simple
val foo = { A a -> a.b; };
struct A { B b; };
```

When parsing `foo`, we know that `a` points to an `A`, but not the fields of
`A` or the type of `a.b`. The load records that `A` must have a field named `b`.
Forward shapes (`A`) remain *open* until their declaration, as in prior
chapters. When parsing `struct A` we find `B`, but `B` here is never declared... and
does not need to be.

After parsing, the compiler *closes* all declarations as a group, including
type cycles (such as a `struct LinkedList` having a `next` field typed as a
`LinkedList`) and references through function signatures. It then replaces old
type shapes in cached node types and stored declarations, preserving pointer
permissions and nullability. Declared types now have their complete field sets.
An undeclared type such as `B` may remain opaque: this example only passes its
pointer around and does not need to allocate a `B` or access its fields.

Closing declarations does not itself prove the program will type-check.

At this point `FldOff` can begin to peephole into a `ConFldOff` - moving
from a symbolic field reference of an incomplete type to a concrete
field of a known type.  Later, after structures get a *layout*, `ConFldOff`
will become a concrete field offset, a number like `4` or `8`.


## The construction boundary

This snapshot keeps the preceding chapter's constructor model: collect field
values in the constructor scope, check completion, then allocate and initialize.
An unknown expression type is no longer evidence of an unwritten field, so the
completion check follows explicit missing-initialization values through merges.

Allocation helpers, hidden initializer calls, private self-memory, and explicit
publication belong to the next chapter. Escape discovery, source modules, lazy
class initialization, and serialized ideal IR follow later. These changes use
the existing conservative policy for functions exposed to unknown callers.
Method binding retains the preceding parser's requirement that the owner's
field declaration be known. Forward data fields are resolved here; moving
method and initializer bodies out of that parser scope is construction work.

## Building and checking

Run `make lib tests CTAGS=` here, or from the repository root run
`make tests CHAPTERS=chapter25 CTAGS=`.

`IncompleteTypesTest` exercises a forward field with deferred numeric mode and
a forward store whose byte width must be resolved. The inherited suite covers
recursive objects, null guards, initialization errors, loops, all three code
generators, and native calls. Earlier parse-only graph expectations now show
conversions that disappear after type propagation.
