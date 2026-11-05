# Construction: field types and incomplete references

Instance constructors use ordinary field Loads and Stores. There are two checks:
whether a field has a value, and whether an object reference is usable outside
construction. Neither requires a collection of constructor-group identities.

## Field initialization

New produces private `TypeMem` containing the object's `TypeStruct`, with
`TypeScalar.BOT` for fresh fields. Defaults emit ordinary Stores: zero for an
ordinary integer, null for a nullable pointer, no Store for a required field.
An assignment strongly updates its field in private memory. Phi meets the
incoming memories; a missing live arm survives because `BOT.meet(7) == BOT`.
An initialized unknown integer has `TypeInteger.BOT`, not `TypeScalar.BOT`.
Arrays retain their language-level zero filling.

One `#selfMem` binding carries this memory through scopes and returns. A Load
checks its incoming field type. Constructor completion checks the merged Return
memory; the explicit constructor receives the declaration initializer's merged
result, including early exits. There is no backwards initialization query,
per-field parser state, call-site snapshot, or store-all instance exit.

The existing Iter/Opto passes propagate these types; TypeCheck reports errors.
There is no extra construction type solve or retained source-use list. An
initialized field carried unchanged around a loop needs the optimistic SCCP
solution. Numeric fields in loop memory widen as scalar loop values do.
Arithmetic, conversions, and numeric Stores preserve Scalar.BOT, so a live bad
read cannot disappear merely because it is multiplied by zero. Genuinely unused
reads may be removed by DCE.

The allocator's ConstructNode retains the constructor's final private memory
for its completion check. TypeCheck gathers these nodes during its existing
error walk and removes checked boundaries before lowering. The pass removal
exposes known failures; see [the late-check notes](deferred-construction.md).

Internal construction memory parameters use actual allocation/initializer call
edges; the hypothetical external caller does not supply their memory. They
retain their memory family when there are no live callers. Function-valued
fields retain their declared signatures in private summaries, avoiding repeated
unfolding of a constructor whose returned memory contains its own function field.
Class singleton initialization keeps its older store-at-exit implementation.

## A yes/no rule for references

`self` and incoming `C ^arg` constructor parameters are incomplete references.
`C? ^arg` also accepts null. The qualifier is permitted only on struct parameters
of explicit constructors, not on arrays or ordinary function parameters.

- An allocation result is incomplete if any actual argument is incomplete.
- With only completed arguments, including null, the result is complete.
- An incomplete reference may be stored into self or passed to a `^` parameter.
- Ordinary calls and stores into other objects require completed references.
- Field lookup on an incomplete object is forbidden, except through self.
  Exact local aliases of self share its current private memory.
- An object-reference field read from incomplete self is conservatively
  incomplete. There is no inference of initialized-field requirements on `^`
  arguments, nor permission to traverse a constructed member before its root
  construction finishes. Arrays are already initialized and cannot receive
  incomplete elements, so an initialized array field can be used normally.

For example:

```simple
struct Child {
    Test.Parent? parent;
    new Child = { Test.Parent? ^par -> parent = par; };
};
struct Parent {
    Child child;
    int~ payload;
    new Parent = { int n ->
        child = new Child(self); // Incomplete result: self is incomplete.
        // child.parent is forbidden here.
        payload = n;
    };
};
val parent = new Parent(arg);
val local = new Child(parent);  // Complete argument, complete result.
return local.parent==parent;    // Lookup is allowed.
```

Here `Test` names the source unit. After the root Parent constructor completes,
the entire cycle is usable: all members have initialized their own fields, and
none could expose an incomplete reference to ordinary code during construction.
The Child constructor still treats its `^par` as incomplete when the actual
argument is complete; it may retain or forward it, but cannot read `par.payload`.
Field sharpening and inferred contracts are deliberately deferred.

## Aliases and loops

Incomplete is a property of a value, not just the current contents of a parser
Var. Consider `while (n--) { use(p); p = self; }`: the backedge introduces an
incomplete value after `use(p)` has been parsed. `TypeMemPtr._partial` carries
that restriction through ordinary forward typing. It is an independent
two-point lattice: meet ORs the restriction and dual complements it. Copies,
conversions, Stores, and Phis preserve it; a merge is incomplete if any live
input is incomplete. A `^` formal stays incomplete even when its known callers
are complete. The receiver's private memory identifies the own-self exception;
another incomplete pointer of the same class has no such permission.

A small `ConstructNode` wraps each source `new`. Its runtime value is the
allocator result; its type adds the OR of that call's actual argument qualifiers.
This distinguishes `new Child(self)` from `new Child(null)` even when both call
the same constructor. Looking only at the shared callee Return would lose this
distinction. The node disappears after TypeCheck, before lowering.

A completed receiver gives a completed view of its pointer fields. Construction
may retain incomplete references but cannot publish them, so reaching the root
`new` boundary with complete arguments makes the entire reachable cycle usable.
There is no group identity or traversal to mark members complete. Conversely,
object-reference fields read through incomplete self remain conservative, even
if the particular stored reference was complete.

The existing optimistic SCCP solve also handles qualifier cycles. For
`p = null; while (n--) p = new Link(p);`, no input introduces incompleteness,
so `p` stays complete and a subsequent field read is allowed. A backedge carrying
self instead makes the Phi and its uses incomplete.

`Construction` selects private self memory during parsing; it retains no
source-use lists or flow facts and performs no recursive Load/Phi/Call query. The old
root and allocation bitsets are gone. There are no field-requirement sets,
hidden `^` memory arguments, or runtime group objects.

`ConstructionGroupTest` exercises cycles, complete/null arguments, prohibited
lookups/publication, aliases, branch merges, complete and incomplete loop
backedges, and declared restrictions with only complete callers, including an
uninlined constructor through machine encoding. `ConstructorCompletionTest`,
`FinalConstructionTest`, and `BoundaryTest` exercise field-memory semantics.
The former inferred-requirement litmus tests are preserved, explicitly deferred,
in `ConstructionRequirementsTest`. Separate compilation of `^` constructors is
still deliberately unsupported. The pointer qualifier has an object-file
representation, but importing and checking the full construction contract is
outside this experiment. Rebuild chapter 29 object files after the type-format
change.
