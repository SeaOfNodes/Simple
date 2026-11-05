# Deferring construction checks: experiment

The extra construction pass is removed from the working implementation.
`Construction.check()`, its retained source-use lists, `Iter.types()`, and the
separate constructor-Return walk are gone. Errors now belong to the existing
TypeCheck walk; ConstructNode retains final private memory for completion checks.
No additional whole-graph pass was added. Constructor rejection tests remain active.

## Constant folding

`TypeScalar.BOT` is not an initialized integer. Arithmetic, comparisons,
negation, numeric conversions, and typed Stores must preserve it rather than
manufacturing an integer or floating-point result. In particular, `x*0` and
`x==x` cannot become constants while `x` has this type. An initialized unknown
integer still permits those folds.

The scan found and changed these related cases:

- Multiplication by zero now folds in `compute`, after checking numeric inputs.
  Integer strength reduction also waits for integer operands; an unfinished
  loop Phi could eventually become a float.
- Same-input comparisons fold in `compute`. Floating comparisons keep NaN
  semantics; `x==x` is not necessarily true. The integer range implementation
  of `!=` also had the equality answers reversed.
- Arithmetic pushed through Phis uses each copied operation's `compute`,
  including floating-point constants, instead of an unchecked integer cast.
- Known array length offsets and element shifts fold in
  `ConFldOffNode.compute`, rather than its `idealize`.
- `Not`, unary minus, conversions, and Stores preserve unknown initialization.
  `RoundF32` already passed that type through.

This does not ban creating constant *operands* in rewrites such as `x*8` to
`x<<3`. Memory forwarding still has cases that return a stored constant or an
array's zero-filled element; these were inspected but not rewritten into a new
memory analysis. `Load.idealize` retains a `Scalar.BOT` load.

The changes exposed a rejected-Phi-fold worklist cycle. Factoring
`Phi(bool & 7, integer & bool)` widens the result from `bool` to `[0-7]` and
must fail. Building and then killing the candidate woke the same dependencies
forever. The trial now computes its types before registering use edges.

## Preserving the constructor body through optimization

This regression must reject a live incomplete lookup:

```simple
struct Child {
    Test.Parent? parent;
    new Child = { Test.Parent? ^par -> parent = par; };
};
struct Parent {
    Child child;
    int~ payload;
    new Parent = { ->
        child = new Child(self);
        payload = child.parent == null;
    };
};
return new Parent().payload;
```

`child` is incomplete because its constructor received incomplete `self`.
Reading `child.parent` must therefore fail. The read supplies the final result;
this is not a discarded local or dead-code issue.

The decisive failure was private-memory Parm typing. During Iter, inlining
removed a real caller while the function still had its unknown-caller edge.
The special case returned MemTop anyway. That erased the memory merge, Store,
and the bad Load feeding it, before TypeCheck could report the error.

Parms now use their normal merge. Hidden initializer/constructor helpers have
only actual allocator callers; the source-visible constructor binding denotes
the allocator. Public allocators still receive unknown callers, so unused public
constructor bodies remain checked. This also avoids poisoning a helper's private
memory with a fictitious external default.

Inlining presents a second issue: a complete actual pointer must not grant
permission to read a restricted formal. A partial-only ConstructNode gives
self and ^ parameters their body view independently of Parm typing. It remains
until TypeCheck. Load's alias search recognizes that this view preserves the
address, while compute/err continue to see the qualified pointer.

A smaller regression reads an unwritten field through a self cycle:

```simple
struct C {
    C next = self;
    int~ x;
    new C = { -> x = next.x; };
};
return new C().x;
```

Both examples now reject at TypeCheck. A positive regression initializes x to
7, increments it in the constructor, and checks that the result folds to 8.

The broader run exposed a function-lifetime bug during SCCP: unlinking calls
could delete a body subsequently rediscovered from a type-level function
reference. Returns are kept during the existing reset walk and released once
call edges have been rebuilt. No additional graph walk is needed.

Class-init dependency discovery also skips a CLInit whose receiver has not
resolved to a class. A Scalar.BOT receiver is left for its Load's TypeCheck
error, rather than dereferencing a null class name.

The trial successfully uses the existing passes to prove a chain built from
`null` complete. It also rejects direct `par.payload`, `par.payload*0`, and
`int x = par.payload; value = x*0` after the arithmetic/conversion repairs.

Genuinely unused reads are allowed to disappear under late-check semantics.
Rejection tests must keep the read live. `testUnusedIncompleteReadCanDie` checks
that an unused `par.payload` read is eliminated and the program returns 5.

## Optimization differences and register allocation

`Chapter18Test.testFcn9` exposed an independent escape bug. StopCU treated the
class initializer's synthetic self as an object already held by an external
caller. Its broad function-valued field type exposed the local x function even
though the initializer never returned and x had no callers after inlining.
Skipping that synthetic argument restores the empty escape set for x and the
expected `return Top;`. Actual public-call arguments still escape normally.

That correction exposed `Chapter18Test.testFcn8`: a Store computed from TOP
memory interpreted its optimistic singleton bit as established private memory.
It widened a function value to the declared signature, then narrowed its FIDX
set when memory resolved. Store now returns TOP while its memory is TOP, so
SCCP remains monotone.

The String differences have a different cause. In Chapter19, the private
_hashCodeString body dies; the surviving functions are the initializer,
allocator, equals, and public hashCode. Its golden string now omits the two dead
private-helper MemTop Returns and matches the current addition order.
`Add.spine_cmp` breaks this tie by node ID. Strength reduction creates the Sub
in IterPostOpto, after the byte Load (Sub#594 versus Load#504 in one traced copy),
so the Sub sorts to the left. This is node ordering, not a lost value type.

In the Chapter20/21 fixtures, _hashCodeString really escapes through the implicit
return, and hashCode contains an inlined copy. Before the allocator repair, both loops needed an extra x86
register copy with the new addition operand order. A disposable probe
swapped only `((hash<<5)-hash) + byte` to `byte + ((hash<<5)-hash)` after Unlink:
no types or live functions changed, but the weighted split score fell from 37
to 21. Each hash body fell from 17 to 9; the allocator stayed at 3.

Reconstructing the earlier source by reversing the saved construction-pass
removal patch reproduces the expected 29. Its standalone _hashCodeString costs
17, inlined hashCode costs 9, and allocator costs 3. Before the allocator repair,
these costs were 17, 17, and 3. Both versions contain two hash loops; no inlining decision changed.
Current code emits two fewer functions, the dead initializer/constructor helpers,
which contributed zero. Thus the entire increase is one loop-weighted register
copy in the inlined hashCode body, not another retained function or stack spill.

With the old operand order, the byte Load can write the loop's hash register
and Add accumulates the shifted/subtracted value into it. With the new order,
Add's destructive input is the Sub result. Its two-address tie plus the Phi
backedge joins the shift/Sub/Add chain to the old hash live range, which overlaps
the shifted value at the subtraction. `RegAlloc.splitSelfConflict` inserts a
`use/self/two` copy before Add. The existing commute heuristic in `splitByLoop`
is not reached: self conflicts take an earlier splitting path. Commuting Add
before splitting is the concrete optimization opportunity.

The allocator now does this in `splitSelfConflict`, using the existing failed-
range traversal and scratch list. It commutes two-address operators when the
RHS has one use and belongs to a different live range, then omits the copy for
the tie it broke. An allocator-generated Split is excluded: its single use can
hide a longer-lived value, and commuting onto it can undo an earlier repair.
The existing coloring retry rebuilds live ranges. There is no new whole-IR pass
or analysis structure. The machine's `commutes()` hook covers Add, Mul, Xor,
And, and Or (and commutative floating instructions).

The String score is now 21: each hash body costs 9 and the allocator costs 3.
The three x86 expectations are updated to 21. A single regression exercises
all five integer operators in both machine operand orders, checks register
constraints, and encodes the results. Existing testFlags2 guards against the
Split-operand regression.

## Incremental Iter setup

Iter no longer resets and requeues the whole graph after parsing. `Node.init()`
queues initialized nodes, and Calls queue themselves so Iter can link targets
loaded after the call was parsed. Iter seeds its new global escape summary,
then drains the worklist. Import loading still resets the newly loaded nodes
and repairs affected neighbors.

Removing the sweep also exposed an unused receiver left behind when field
lookup switches from an instance to its class. That path now releases the keep
and kills the receiver if unused.

FldOffNode's base is data in slot 1. Slot 0 is reserved for control; putting a
data parameter there made inliner body discovery exclude an unresolved offset.
The regression `CallEndTest.testUnresolvedOffsetInInlineBody` checks that the
offset belongs to the function body even before its type resolves.

## Type upgrades in Opto setup

ParseAll still closes the named types and freezes the public interface after
loading all units. It no longer walks the IR. Opto installs those closed types
and resolves forward references during its existing reset walk. Setup runs no
peepholes against the partly reset graph: resolved FRefs propagate their input
type in SCCP and disappear during subsequent peepholes. Construction identities
also wait until this setup has installed complete field declarations.

That walk retains Returns while call edges are rebuilt, gathers unresolved
numeric modes, and records dependencies for cleanup after SCCP. Type upgrades
also register cleanup dependencies, even if the eventual value type is unchanged.
Old Iter types are snapshots for change detection, not upper bounds after
recursive declarations have been closed. SCCP still asserts monotone falling
types and a complete worklist within its own solve.

The move exposed assumptions formerly hidden by early upgrades. Resolving an
already known class name must retain its known fields instead of replacing them
with an empty placeholder. A Load must not forward a stored pointer whose open
shape is weaker than the Load's computed type. Escape must retain escapes from
the actual private value before narrowing it to its declared field type, so
selecting a Store out of a MemMerge does not introduce new escapes.

An unknown return or memory effect carries FULL escape sets. StopCU and
GlobalEscape preserve that bound: narrowing it to public names would omit
private objects and callbacks that a subsequent load-forwarding rewrite can
reveal. Both summaries recompute from their inputs without retaining previous
escape sets. Refining a value removes possibilities; it never adds escapes
outside the old bound. IterPeeps has no special summary handling, and its
worklist assertion requires every node's type to lift, including queued nodes.
`testUnknownEscapesRefineToPrivateValues` checks both escape sinks;
`testPrivateRecursiveReturn` covers cyclic private objects and field loads.

The ordinary dependency mechanism also covers signature refinement and lazy
imports. FunNode depends on its Return when refining its signature. Calls
depend on their target FunNode; while a target is absent, they depend on Start
and retry when a linker mapping is installed. Loading bodies and adding call
edges still use `Iter.repair` for graph changes, not for value refinement.

SCCP's Start linking honors FULL for private targets too. Incoming arguments
are not themselves outgoing escapes; Stores, Returns, and external-call
arguments describe those effects. Constant singleton field slices carry the
stored value's escapes, not unrelated bulk-memory escapes frozen at the time
of folding. This preserves precision for local recursive callbacks.

As bounds sharpen after SCCP, FunNode removes unknown callers even if concrete
callers remain. Called initializers retain their Return through StopCU because
CLInit has no Return edge. Removing an uncalled function queues the affected
Stops, and compilation-unit wrappers retain distinct identities even when
empty. These are worklist notifications and ownership rules, not extra passes.

### Remaining precision issue with FULL escapes

The monotone bounds expose a feedback problem in the existing grand cycle.
In `Chapter12Test.testPrivateRecursiveReturn`, SCCP sees BOTTOM for the final
load chain and therefore FULL escapes. These feed through Start's memory into
the function memory results. Post-SCCP forwarding resolves the return to `_B`,
but that memory cycle remains at MemBot and keeps private constructors callable
by unknown callers. This is conservative, but loses the previous dead-function
elimination. It does not justify narrowing BOTTOM to public names or adding
the IterPeeps widening repair back.

For this example, the root Return goes from `[Ctrl, MemBot, Bot]` after Opto to
`[Ctrl, MemBot, *_B]` after TypeCheck; Stop's memory remains MemBot. The remaining
design question is how to avoid this feedback while retaining conservative
bounds and the existing pass budget. The current experiment leaves the printed
IR expectations unchanged so they continue to expose this loss of precision.

The source-validity audit separates these cases from Chapter10 `testIfOrNull`.
That test originally returned the nullable pointer itself. A historical edit
changed it to `return bar.a;` without adding TypeCheck, leaving a real null
error hidden behind a printed-IR assertion. It now again returns `bar`, runs
through TypeCheck, and passes with `return Phi(Region,null,Test._Bar);`.

All four remaining programs pass TypeCheck. The Chapter12 recursive-object
tests have one pending bad Load after Opto; forwarding eliminates every Load
and resolves the result to `17`, `_B`, or `_R`, while escape memory stays MemBot.

The two Chapter17b forward-type tests now pass their original IR expectations
and explicitly run TypeCheck. Their undeclared `B` is an opaque external type,
not an arbitrary unknown local value. EscapeNode uses the frozen public
interface as its fallback for that type, preserving escapes already present
in its private and public memory inputs. This applies to both the actual private
value and the declared field bound: in `testForward1`, the private array element
type is already `B?`. Bounding only FIDXs is insufficient; FULL aliases expose
private class fields, which then expose their constructor FIDXs.

CodeGen caches the fallback when ParseAll freezes the public interface. It
returns FULL until Opto's existing reset walk installs the closed types into
the IR. Only then does an open `_fref` reliably denote an external object.
Ordinary open local structs, `TypeScalar.BOT`, and abstract pointers still
require FULL. No new pass or IterPeeps hook is involved.

### Escape-bound audit

All Chapter29 `escapesFrom`, `XInt.FULL`, and `TypeMem.BOT` uses were reviewed,
including the equivalent `escapes` helper and indirect `makePrivate` uses.
The following remaining sites warrant work; they have not been narrowed here:

* **StoreNode's three `escapesFrom(val)` paths.** The singleton constant,
  private instance, and ordinary aliasing paths still treat an opaque external
  value as FULL. A concrete example is:

  ```simple
  struct _A { B? !b; new _A = { B? p -> b=p; }; };
  val wrap = { B? b -> return new _A(b); };
  return wrap;
  ```

  This passes TypeCheck, but the surviving Stores of `b` have FULL FIDX and
  alias sets and keep an extra `_A` allocator. Do not mechanically substitute
  the public default: singleton slices deliberately discard unrelated incoming
  memory escapes. A narrower opaque bound must retain private values previously
  given to external code, without freezing unrelated transient escapes into
  singleton constants. Ordinary aliasing Stores already retain incoming sets.

* **StopCU return values, CallNode external arguments, and SummaryNode arguments.**
  These use `escapes`, which currently duplicates the unbounded `escapesFrom`.
  `val identity = { B? b -> return b; }; return identity;` passes TypeCheck but
  gives StopCU MemBot. Likewise,
  `val send = { B? b -> { B? -> int } retain = "C"; return retain(b); }; return send;`
  gives the native CallEnd MemBot. These are concrete instances of the same
  opaque-type precision loss. A future change can share the bounded helper,
  while retaining incoming/returned memory, explicit private arguments, and
  imported-summary effects. StopCU's public function seed and `escapesAliases`
  class seed describe known declarations; GlobalEscape's `escapes` calls merely
  union already computed TypeMem sets.

* **CLInitNode's possible-initialization result.** Even a known initializer
  yields TypeMem.BOT until its ready flag is true; only the initialized flag's
  slice is precise. This loses both memory-content and escape precision.
  CLInit has an analysis call edge but no Return edge supplying callee effects.
  Improving it needs a dependency on the initializer's effects, including
  recursive dependencies. The public interface alone is not a safe cap: that
  initializer can publish private callbacks. Its unresolved-flag BOT fallback
  is also conservative, but normally disappears when the receiver resolves.

* **CallEndNode's unknown or unlinked target result.** Both return TypeMem.BOT.
  The unlinked case covers invalid calls and transient missing edges; SCCP links
  valid finite targets before computing CallEnd. Truly unknown targets can be
  local private functions as well as external functions, so a public-only cap
  would be wrong. Native calls already use CallNode's argument/memory effects.
  Any refinement must distinguish target provenance and retain explicit private
  arguments; it is not the same small fix as an opaque external object.

The other broad defaults appear justified:

* TypeMem's generic BOTTOM/scalar/pointer cases and TypeStruct's unknown scalar
  fields must include private callbacks that later value refinement can reveal.
  Private-memory declarations/Phis use `makePrivate(BOTTOM)` for the same reason.
  MemMerge's `makePrivate(struct)` describes actual fields, not an external
  opaque object.
* Parser/function memory declarations, initial node types, TypeTuple.STATE,
  and low-family error fallbacks in Store, MemMerge, BulkMemPhi, Start, and
  TypeTuple need worst-case memory. CallNode and SummaryNode likewise cannot
  promise bounded effects when their inputs are still arbitrary low values.
* Iter's import reset gives newly loaded nonconstant memory FULL before repairing
  affected neighbors. Saved types belong to another caller context; constants
  retain their own computed types. This is a safe pessimistic seed, though it
  can contribute to the existing memory-cycle precision problem.
* TypeFunPtr's FULL signatures and TypeRPC.BOT describe unknown function/return
  targets. Serialization/remapping preserves the FULL sentinel; type gathering
  exercises it; LoopNode's `isa(TypeMem.BOT)` is a type-family check. These do
  not independently manufacture an escape effect.

Ignoring assertions, the remaining whole-node walks through TypeCheck are
Opto's setup/reset and TypeCheck's error scan. Class-init checking separately
scans function control flow and runs SCC over the resulting dependency graph.

## Validation

Chapter29's latest run tested 594 active cases with assertions: 590 passed and
four printed-IR comparisons failed. `make -k tests` ran raw0 (400/404), raw1
(38), standalone (13), system (29), and fuzzer (1); the aggregate group (109)
was run separately because Make skips it after a prerequisite failure.
The remaining failures retain extra private constructors: Chapter12 `testCoRecur`,
`testCoRecur2`, `testCoRecur3`, and `testPrivateRecursiveReturn`. Both Chapter17b
forward tests pass unchanged goldens. TypeTest.testOpaqueExternalEscapes checks
both fallback sets, preservation of prior private escapes, and FULL for ordinary
unknowns. System, spill, import, callback, and constructor tests pass. The only
golden changed during the earlier source-validity audit belongs to the restored
`return bar;` source; none were changed to accept extra functions.

The offset regression fails with the old slot-0 layout. Constructor and
boundary tests pass, including both missed rejections and the prior NPE.
Printed-IR expectations omit the dead private-helper MemTop Returns; no
rejection expectations were relaxed. The three String spill checks now expect
21 on x86, with the same live functions and inlining decisions.
