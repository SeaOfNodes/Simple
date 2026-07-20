# Direct-to-SSA Parser Simplification Plan

## Purpose

The constructor rewrite exposed a deeper problem: the one-pass parser sometimes
uses provisional type information to choose SSA graph structure.  Forward
references make that information incomplete and parse-order dependent.

The design rule for this work is:

> Syntax and resolved lexical bindings determine graph shape.  Types refine and
> validate the graph, but provisional types do not decide its shape.

This is an experiment, not an irreversible cleanup.  We need to be able to
abandon the simplification completely, or restore individual mechanisms that
turn out to have been necessary.

## Preserve the old design before removing it

Do not preserve old code as large commented-out blocks.  Git is the archive;
the working implementation should remain small enough to understand.

Before implementation begins:

1. Confirm the worktree is clean and record the current failing-test baseline.
2. Create a named, permanent anchor at the last commit before simplification,
   for example an annotated tag `ch25-parser-before-simplification`.
3. Do the experiment on a dedicated branch, for example
   `ch25-parser-simplification`.
4. Push the anchor and branch if a remote backup is desired before large
   deletions.
5. Never squash the experimental commits until the design has proved itself.

The anchor is the complete "this was a giant mistake" escape hatch.  Switching
back to a branch made from that anchor restores the old implementation exactly.
No reset of the experimental branch is required.

Removed mechanisms must each have their own small commit.  A commit should
remove one idea, not mix its deletion with several replacements.  Suggested
commit sequence:

1. Add characterization tests and inventories.
2. Remove one old constraint mechanism.
3. Make weak-input computation total.
4. Add the smallest replacement demanded by a failing test.

This history supports several levels of recovery:

- Recover the entire old design by creating or switching to a branch at the
  preservation tag.
- Restore a complete deleted file from the tag with `git restore --source`.
- Restore selected hunks interactively with `git restore -p --source`.
- Reintroduce a logically complete mechanism by reverting its deletion commit
  or cherry-picking a later restoration commit.
- Compare the experiment with the old implementation at any time using the
  preservation tag as the diff base.

Avoid using a stash as the archive: stashes are unnamed working state, are easy
to drop, and do not provide the reviewable sequence needed for selective
restoration.

When an old mechanism is restored, bring it back in a new, named commit and
record the test that required it.  Do not silently copy code out of history.
That preserves evidence about which complexity is essential.

## Working discipline

Work through `tests_raw0` chapter by chapter.  Keep all earlier chapters passing
before advancing.  The expected difficulty landmarks are Chapter 10, where
functions are exercised seriously, and Chapter 13, where recursive forward
references and cyclic types become prominent.

Use the following test ladder:

1. The single test or test class driving the current change.
2. The current chapter.
3. All earlier raw0 chapters.
4. `tests_raw0` when a milestone is reached.
5. `TypeTest` after every change to `Type`, `Field`, lattice operations,
   interning inputs, equality, hashing, or duality.

Golden-string mismatches are not a gate during structural work unless the
string reveals a semantic or graph-shape regression.  Record and defer expected
text churn rather than continually rewriting goldens.

Treat these as compiler invariant failures, not ordinary test failures:

- `compute()` must be monotonic, including when inputs are weak or unresolved.
- `Opto.worklistCheck` failures require finding the non-monotonic computation;
  they are not assertions to weaken.
- IterPeeps' `Non-monotonic peep` check applies the same rule to iterative
  transformations.
- `ideal()` runs only during iterative optimization, not Opto, but its
  replacement must preserve the type knowledge already established.

Commit whenever the test frontier advances or one architectural mechanism is
removed.  Each commit message should state the highest passing chapter and
whether `TypeTest` was run.

## Phase 0: Baseline and inventory

1. Fix the local `_Scan` constructor migration so it does not obscure parser
   work.
2. Record the current raw0 failures by chapter and test name.
3. Inventory parser-written constraints and type-sharpening side channels:
   `_con` fields, mutable signatures, parser mutations of `TYPES`, forced memory
   types, and assertions that assume already-sharp inputs.
4. For every candidate deletion, record its writers, readers, and the first
   test that appears to need it.
5. Add small characterization tests when behavior is important but undocumented.

The inventory is a deletion checklist, not a commitment to replace every item.
Code that no passing test requires stays deleted.

## Phase 1: Remove parser-supplied node constraints

Start with `_con`-style fields in `PhiNode`, `LoadNode`, `StoreNode`, and related
nodes.

For each node kind:

1. Delete the parser writer and the node field in a narrow commit.
2. Make `compute()` accept the weakest legal inputs.  A `Type.BOTTOM` input must
   produce a conservative weak output instead of triggering a cast or sharpness
   assertion.
3. Separate structural slot invariants from type-sharpness assumptions.  It is
   valid to know that an input position is the memory edge without knowing a
   sharp `TypeMem` for it.
4. Restore only the structure that a failing test proves necessary.

If an ordinary Phi cannot preserve the structural fact that it merges memory,
introduce a `MemPhiNode` or an explicit immutable Phi kind.  It may lift weak
inputs to the weakest memory type.  It must not contain a parser-chosen sharp
lower bound disguised as another `_con`.

## Phase 2: Separate name binding from type inference

Locals remain define-before-use.  The unresolved case is an unqualified field
name used inside a function before a later instance or class field declaration
determines its owner.

Introduce unresolved SSA operations rather than guessing from provisional
types:

- `UnresolvedLoad` records the identifier, source position, definitive lexical
  scope object, ordered possible base pointers, and bulk memory input.
- `UnresolvedStore` records the same binding information plus its value and the
  constructor context needed to decide initialization legality later.
- Closing the relevant scope resolves all pending operations by lexical order.
- Resolution selects the base pointer and alias and rewrites the node to the
  ordinary resolved operation.

Do not use pointer-type sharpening to choose ordinary lexical bindings.  Types
may reject a resolved operation, but the closed scope is authoritative about
which declaration the name denotes.

The `init` bit on a resolved Store is decided at this same point.  It is true
only when the winning declaration belongs to the receiver being initialized by
the current constructor.  Merely being textually inside some constructor is not
enough.

## Phase 3: Recover alias precision after unresolved stores

Before binding, an unknown store uses bulk alias `#1` and conservatively updates
bulk memory.  Preserve enough information in `UnresolvedStore` to recover the
pre-store alias bundle when its target becomes known.

After resolution, Opto may transform a Phi of memory merges into a merge of
per-alias Phis:

```text
Phi(MemMerge(entry...), MemMerge(back...))
    =>
MemMerge(aliasA = Phi(entryA, backA),
         aliasB = Phi(entryB, backB), ...)
```

For a resolved store to alias A, alias A's loop backedge passes through the
store; unrelated aliases bypass it.

Requirements for this idealization:

- Handle missing slices through the MemMerge default/bulk input.
- Create alias Phis on demand; aliases need not all be known at loop creation.
- Memoize a replacement loop Phi before recursively processing its backedge.
- Use only the canonical direction shown above to prevent transform oscillation.
- Keep unresolved memory operations resistant to destructive idealization until
  their owning scope closes.

Pre-splitting memory during parsing remains an eager optimization when aliases
are already known.  This transform is the lazy equivalent when they are not.

## Phase 4: Keep call shape syntactic

Call SSA shape must not depend on whether a provisional function type currently
looks like an instance function.

The syntax `ptr.fld(args)` always supplies `ptr` in the hidden self slot:

```text
call(load(ptr, fld), self=ptr, args...)
```

This remains true if the field later resolves to a stored top-level function;
that function ignores the universal self argument.  An ordinary call through a
separately loaded function value uses the normal non-instance/default self.

Function types validate calls after construction.  They do not change call
arity or add/remove SSA inputs after resolution.

## Phase 5: Split declared function contracts from inferred signatures

Audit `FunNode._sig` after the earlier constraints are removed.

Function arguments have authoritative declared syntax, while returns are graph
derived.  A declared forward type such as `B` has stable symbolic identity but
only a provisional current realization.

Represent the external/default caller with a non-constant source node tied to
the declared type binding:

```text
UnknownCaller(B-binding)
```

Its computed runtime value type begins as weak open `B{...}` and monotonically
sharpens when the binding resolves.  Updating the binding schedules its users.
Private functions with completely known call sites may omit this default caller
and sharpen their parameters from actual arguments.  Escaping functions retain
it as their worst-case caller.

Classify every use of `_sig` as one of:

- immutable declared argument contract;
- cached graph-derived information;
- unresolved symbolic binding.

Delete cached derived state.  Keep authoritative declarations explicitly.
Derive return information from Fun/Parm/Return graph edges.  Recursive functions
must converge through weak-input-tolerant `compute()` rather than parser mutation
of a parallel signature cache.

## Phase 6: Make field finality a four-state type fact

Use two bits in `Field`, with accessors hiding the representation:

```text
00 unknown
01 declared mutable
10 declared final
11 conflicting finality (BAD_FINAL)
```

The discovery-combination operation combines evidence; with this encoding it
can normally be bitwise OR.  `UNKNOWN` is the identity and `BAD_FINAL` is
absorbing.  Confirm the operation matches the repository's meet/join lattice
orientation.

Add the bits to equality, hashing, duality, printing, and all Field construction
paths.  A weak forward reference carries `UNKNOWN`, never guessed mutable.
Conflicting authoritative declarations produce `BAD_FINAL`; computation remains
total and type checking later reports the program error.

Add representative Fields for all four states to TypeTest's `gather()` universe.
Verify pairwise operations plus commutativity, associativity, idempotence, duals,
hashing, and interning behavior.  Cyclic handling and interning should fall out
of the existing machinery, but TypeTest must demonstrate that they do.

Final-store validation happens only after binding and type closure:

- final and initializing store: legal;
- final and non-initializing store: error;
- mutable field: initialization marker is irrelevant;
- bad finality: declaration/type error.

## Phase 7: Preserve cyclic type closure

Keep `Type.closeOver()` responsible for tying recursive named types together.
For mutually recursive A/B structures it must collect partial versions, merge
same-name fields (including four-state finality), remove the open marker when
the declaration is complete, and make recursive occurrences reference the same
canonical Java object.

Do not make SSA construction depend on whether `closeOver()` has already run.
Closing types sharpens existing nodes and wakes their users; it does not rebuild
parser-chosen graph topology.

Chapter 13 is the first major acceptance gate for this phase.

## Phase 8: Advance the chapter frontier

For each failing chapter:

1. Reduce the first semantic or invariant failure to the smallest useful test.
2. Decide whether it demonstrates missing required structure or merely an old
   golden string.
3. Add back only the smallest mechanism justified by the test.
4. Run the chapter, all earlier chapters, and TypeTest when applicable.
5. Commit the new frontier before moving on.

Expected order of pressure:

- Chapters 1-9: establish that weak-input computation did not disturb basics.
- Chapter 10: functions, calls, and parameter computation.
- Chapter 13: recursive forward functions and cyclic structures.
- Later chapters: fields, finality, memory alias recovery, escaping functions,
  and separate compilation interactions.

Do not begin by restoring later-chapter machinery preemptively.

## Decision gates and rollback criteria

Pause and compare against the preservation anchor when any of these occurs:

- A replacement requires more state or special cases than the mechanism removed.
- Parser graph shape still depends on provisional types.
- Alias recovery requires global reconstruction with no monotonic canonical form.
- Recursive type or function convergence requires non-monotonic mutation.
- Earlier chapters repeatedly regress as later mechanisms are added.

At a gate, choose explicitly among:

1. Continue with the simplified mechanism.
2. Restore one old component and document the test proving its value.
3. Start a narrower experiment branch from the preservation anchor.
4. Abandon the experiment and resume from the preserved design.

Abandoning the experiment is a supported result, not a failed recovery.  The
tag, unsquashed commits, and narrow deletions guarantee that both the complete
old parser and individual removed mechanisms remain available.

## Completion criteria

The simplification is successful when:

- `tests_raw0`, then `tests_raw1`, and finally the broader suite pass apart from
  deliberately deferred golden updates;
- `TypeTest` passes with all finality states included;
- no parser decision about SSA topology depends on a provisional inferred type;
- unresolved lexical operations resolve deterministically at scope close;
- memory precision lost during unresolved parsing is recovered monotonically;
- calls obtain self from syntax, not inferred callee kind;
- declared function arguments remain authoritative without a parallel mutable
  inferred signature;
- removed code that was never demanded by tests remains absent; and
- the preservation anchor and commit sequence still provide complete and
  selective rollback paths.
