# Chapter 25 parser simplification handoff

Updated 2026-07-23 on branch `ch25-parser-simplification`.

## Preservation point

The last committed and pushed checkpoint is:

```text
54e5b83b Refactor Phi into MemPhi, BulkMemPhi
```

The working tree after that commit is intentionally dirty with the next
BulkMemPhi sharpening experiment and removal of Phi's dead constructor type.
Do not discard or mechanically reset these changes.

## Architectural invariants

- Parsing goes directly to SSA in one pass. Parser graph shape must be chosen
  from syntax, not provisional inferred types.
- Parser `ScopeNode` carries one bulk memory value. Precise alias partitions
  and `MemMergeNode`s are graph semantics, not nested parser state.
- At every program slice, memory edges are disjoint and together cover all
  aliases exactly once.
- Parm, Call/CallEnd, and Return deliberately carry opaque bulk memory to avoid
  code explosion. More precision across these boundaries comes from inlining.
- `MemPhiNode` is one precise alias and stores that alias explicitly.
- `BulkMemPhiNode` covers `All - E`, where `_aliases` is the set `E` represented
  by parallel precise MemPhis.
- Empty BulkMemPhi exclusion sets share a canonical empty BitSet and use
  copy-on-first-split.
- Precise aliases may always peek through a MemMerge to their matching slice.
- A bulk Phi may peek through a MemMerge default only when both edges represent
  the same alias coverage.
- `compute()` must remain monotonic. `idealize()` should inspect inputs in the
  same order as compute when implementing the same decision.
- Maintain the keep/unkeep discipline while moving or cloning graph pieces.

## Current uncommitted WIP

Twelve files are modified. The main changes are:

- Removed the unused `minType`/type argument from ordinary Phi construction and
  Phi serialization. `ParmNode` retains its authoritative declared type.
- `MemPhiNode.compute()` converts bulk alias #1 input results to its explicit
  precise `_alias`.
- `BulkMemPhiNode.idealize()` has the beginning of:
  - peeking through compatible MemMerge defaults;
  - discovering a demanded alias from inputs or outputs;
  - `slice(alias)`, which creates parallel precise and reduced-bulk Phis and
    wraps them in a MemMerge.
- Chapter10 `test3` only renames a local `_s0 !v0` to `_s0 !v1`, avoiding a
  confusing collision with field `v0`.

The BulkMemPhi work is explicitly unfinished. Do not treat its TODOs or current
assertions as settled design.

## Questions to resolve first

1. The current MemMerge peek test checks only that every explicit MemMerge
   alias is already excluded by the BulkMemPhi:

   ```text
   MemMerge sharp aliases subset-of BulkMemPhi exclusions
   ```

   That may be insufficient. If the MemMerge default's coverage is defined
   exactly by its explicit slots, safe peeking appears to require equality of
   the exclusion sets, not merely subset.

2. `hasDup(alias)` scans users of the BulkMemPhi for a matching MemPhi. The new
   precise Phi created by `slice()` shares the BulkMemPhi's region and inputs;
   it is not a user of the BulkMemPhi. Confirm what structural relationship
   should prove that a parallel precise slice already exists.

3. BulkPhi-to-BulkPhi inputs and outputs with unequal exclusion sets are the
   central widening case and currently throw TODO. Decide which side widens,
   how the missing precise Phi is created, and how the exactly-once slice
   invariant is maintained during the rewrite.

4. `slice()` currently asserts/casts its peepholed precise result to `TypeMem`.
   Some paths temporarily produce plain `Type`, so this is already failing in
   Chapter 10. Avoid using acquired type information as structural proof.

5. `excludedInput()` is incomplete for constants and unequal BulkMemPhis, and
   the output switch currently assumes only Scope, MemMerge, BulkMemPhi,
   Return, or Call users.

## Test state

The current WIP compiles successfully.

Focused command:

```text
java -ea -cp "build/classes/main;lib/*;build/classes/test" \
  org.junit.runner.JUnitCore TypeTest Chapter01Test ... Chapter10Test
```

Result: 148 tests, 9 failures.

- Chapter 1 through Chapter 8 and TypeTest pass.
- Chapter 9: `testFuzz3` reaches the unequal-BulkMemPhi TODO at
  `BulkMemPhiNode.idealize`.
- Chapter 10 failures:
  - `testIf2`: SCCP assertion.
  - `testBug7`: `Type` cast to `TypeMem` in `BulkMemPhiNode.slice`.
  - `testIter`: output-user assertion in `BulkMemPhiNode.idealize`.
  - `testLoop`: incomplete `excludedInput`.
  - `test1`, `test2`, `test3`: SCCP assertions.
  - `testWhileWithNullInside`: same `Type` to `TypeMem` cast surfaces instead
    of the expected null-access diagnostic.

Before this new WIP, TypeTest and Chapters 1-9 passed and Chapter 10 had only
the expected `testIter` and `test3` golden/optimization mismatches.

## Workflow

- Start with the earliest regression (`Chapter09Test.testFuzz3`), then proceed
  through Chapter 10.
- Do not update golden strings to conceal semantic, monotonicity, or graph
  invariant failures.
- Run TypeTest after Type changes.
- Ask Cliff before every push.
