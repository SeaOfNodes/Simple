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
per-field parser state, or store-all instance exit.

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

## Partial references and readable fields

`self` and incoming `C ^arg` parameters are incomplete references. `C? ^arg`
also accepts null. Ordinary functions and constructors accept the qualifier on
struct parameters; arrays cannot be qualified this way.

- An allocation result is incomplete if any actual argument is incomplete.
- With only completed arguments, including null, the result is complete.
- An incomplete reference may be stored into self or passed to a `^` parameter.
- Ordinary calls and stores into other objects require completed references.
- A borrowed partial can read fields its incoming type proves initialized.
  It cannot write fields, even non-finals. Exact local aliases of self retain
  the original constructor's access to its current private memory.
- An object-reference field read through a partial is conservatively
  incomplete, with no initialized-field guarantees for its referent. There is
  no inference of initialized-field requirements on `^` arguments.
  Arrays are already initialized and cannot receive
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
        // Retention does not authorize reading parent.payload through child.
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
argument is complete. This public constructor also has unknown callers, whose
partial arguments promise no initialized fields, so `par.payload` is unreadable.

## Forward field views

At a use of self, `InitViewNode` combines the pointer with that program point's
private memory. Its TypeMemPtr contains declared types for initialized fields
and Scalar.BOT for missing fields. The node retains the declaration separately
from the incoming flow shape, so an exact self alias captured before a Store
can receive a fresh view afterward. Earlier calls retain their earlier views.

For example, a private helper can read `x` while `y` is still unset:

```simple
struct _C {
    int~ x;
    int~ y;
    new _C = { int n -> x=n; y=_read(self); };
};
val _read = { _C ^p -> return p.x; };
return new _C(arg).y;
```

Parm meets the incoming shapes using the normal type flow. If any live caller
lacks `x`, Scalar.BOT survives and the Load reports the missing initialization.
Unknown callers supply an empty field promise. Parsing must also start borrowed
parameters at that bound: otherwise early folding of `p.x*0` could hide a bad
read before SCCP. The hidden constructor receiver retains its declaration;
its private memory supplies its read checks and precise field updates.

Values still come from normal memory. Before a call borrowing self, EscapeNode
borrow slices carry the current constructor Stores onto the ordinary call
memory edge. These slices are aliasable and do not publish the private identity
or its fields into the global escape sets. No hidden partial-memory arguments,
field bitsets, additional passes, or recursive flow queries are introduced.

InitView folds before TypeCheck when its input already has the same type and
retains a structural partial view. Nested partial-only Construct wrappers also
collapse. A Parm's current partial type alone is insufficient: inlining may
later substitute a complete actual argument, which must not grant the borrowed
parameter permission to write or publish that object. The completion form of
Construct already folds once its memory proves initialization and its result
type matches its input.

TypeCheck checks the consumers and erases remaining InitViews along with the
checked Construct boundaries in its existing walk. A surviving field view is
not necessarily erroneous: a reader may need only the initialized subset.
Construct remains necessary for
final-field completion and for the partial qualifier of each source `new`.
Alias Phis and nested reference loads are conservative; they do not acquire
the original constructor's write permission. This prototype adds no general
ban on overlapping allocations of the same class: only the owning receiver
uses private memory, while borrowed reads use aliasable memory.

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

`PartialReadTest` exercises field reads, missing fields, forwarding, merged
callers, loops, exact aliases, constructor retention, and borrowed write and
publication restrictions. `ConstructionGroupTest` exercises cycles, complete/null arguments, prohibited
lookups/publication, aliases, branch merges, complete and incomplete loop
backedges, and declared restrictions with only complete callers, including an
uninlined constructor through machine encoding. `ConstructorCompletionTest`,
`FinalConstructionTest`, and `BoundaryTest` exercise field-memory semantics.
The former inferred-requirement litmus tests are preserved, explicitly deferred,
in `ConstructionRequirementsTest`. Separate compilation of `^` functions is
still deliberately unsupported. The pointer qualifier has an object-file
representation, but importing and checking the full construction contract is
outside this experiment. Rebuild chapter 29 object files after the type-format
change.

## Deferred field-aware contracts (2026-11-07)

Decision: do not implement inferred contracts now. Cliff considers the required
infrastructure too large for the feature alone; revisit when a general
backwards/liveness flow is justified, potentially for memory management.
The later forward-field-view prototype above does not implement this contract
discovery proposal. Separate compilation of `^` interfaces remains rejected.

The intended extension would let functions and constructors receive partially
initialized objects, read initialized fields, retain partial references with
their restrictions, and write non-final fields. Only the original constructor
would retain permission to initialize/reassign its own final fields; passing a
pointer must not pass that permission. Final-field reads during construction
would not establish value stability against later writes by that constructor.

The proposed contract representation uses normal types, not a separate field
bitset. For a function reading only `P.pi`, its unknown-caller default would be
schematically `Partial{pi:declared-pi-type, name:TypeScalar.BOT}`. Scalar.BOT
means no initialization guarantee, not a requirement that the caller leave the
field unset. Required fields receive their declared types, covering every legal
external caller. MEET with actual arguments preserves those guarantees only
when the callers satisfy them; extra initialized fields supplied by locals do
not strengthen the body beyond the external contract. TypeStruct/Field already
meet field contents, and nested/cyclic types can represent nested contracts.
TOP is not the missing-initialization marker.

Cliff proposed discovering conservative, context-insensitive requirements after
Iter and before SCCP. Freeze the resulting default argument types on functions;
SCCP consumes them without refining the contracts. Reads on branches SCCP later
eliminates may still impose requirements, as may reads following assignments.
That rejection of some valid programs is acceptable for an opt-in `^` feature.
Combine compatible requirement shapes with JOIN; combine possible caller states
with MEET. Keep the contract distinct from the merged Parm type, and check each
actual argument against it for a call-site diagnostic such as
"foo requires Partial.pi to be initialized". Serialize contracts with declaration
metadata so checking imported calls does not require loading bodies.

The complication is discovering and propagating those contracts. Local SSA
aliases share values; Phi demands reach every input. A demand on a loaded field
becomes a nested receiver requirement. Calls substitute actual arguments for
formals, and recursive forwarding needs a convergent propagation rule. Cyclic
types represent recursive requirements but do not by themselves prevent a
discovery algorithm from unfolding progressively deeper paths. Indirect calls
need contracts available through their callable interfaces; returns need usable
guarantees without retroactively strengthening an exported contract for a client.
Stored references and helper writes also require a coherent account of private
construction memory and object identity; that discussion was deliberately left
unfinished. None of this requires H-M or bidirectional type inference, but it
does require a substantial backwards demand analysis to handle well.

The ignored ConstructionRequirementsTest retains useful litmus cases, not an
approved implementation plan. Do not restart this work without a new request.
