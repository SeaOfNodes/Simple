# Chapter 25 direct-to-SSA parser rewrite handoff

Updated 2026-08-04 for transfer to another AI session/machine.

## Read this first

- Repository: `Simple/chapter25`
- Branch: `ch25-parser-simplification`
- Preservation tag: `ch25-parser-before-simplification` at `4d6a59ed`
- Last pushed commit: `4f8759b3 Rotate random seed and fix more bugs`
- Remote branch was at the same commit when this handoff was written.
- The working tree is dirty **after** that pushed checkpoint. Preserve it.
- The full original design/rollback plan is in
  `parser-simplification-plan.md`. This file supersedes the old WIP handoff.

Do not reset, clean, or mechanically restore the working tree. The dirty files
contain Cliff's next experiment. Untracked `.argsM.txt` and `.out.txt` are
transient local files, not source changes; `.argsM.txt` was locked when the
handoff test run was attempted.

## Why this branch exists

The constructor syntax rewrite exposed a deeper architectural flaw. This repo
is an experiment in parsing a high-level language directly into SSA in one
pass, without first constructing an AST. Forward declarations therefore remain
unknown during parsing. The parser had nevertheless been consulting provisional
types to choose permanent graph structure.

The central rule of the rewrite is:

> Syntax and resolved lexical binding choose SSA topology. Types may sharpen,
> optimize, and reject that graph, but provisional types must not decide its
> shape.

The branch is deliberately unsquashed. Git, especially the preservation tag,
is the “this was a giant mistake; walk it back” path. Removed mechanisms should
be restored by named revert/cherry-pick/new commits, not by copying mysterious
pieces out of history.

## Original goals

The original plan had these major parts:

1. Remove parser-supplied `_con` lower-bound types from Phi, Load, Store, and
   similar nodes. Make `compute()` total on weak inputs.
2. Stop using inferred types for integer-versus-floating arithmetic topology.
3. Separate unresolved lexical field binding from type inference, eventually
   using scope-owned unresolved loads/stores resolved at scope close.
4. Parse unresolved stores against bulk alias `#1`, then recover precise alias
   structure monotonically after the alias is known.
5. Keep call shape syntactic: `ptr.fld(args)` always carries `ptr` in the hidden
   self slot, even if the loaded function is ultimately a top-level function.
6. Separate authoritative declared function arguments from inferred return and
   call-site information; reconsider/remove `FunNode._sig` as cached state.
7. Replace boolean field-final knowledge with four states: unknown, mutable,
   final, and conflicting/BAD_FINAL, including TypeTest coverage.
8. Preserve `Type.closeOver()` and canonical cyclic named-type closure without
   making parser topology depend on whether closure has happened yet.
9. Advance chapter-by-chapter, keeping earlier chapters and TypeTest green.

Those remain the completion goals unless explicitly revised below.

## Goals and invariants learned during implementation

The following refinements became equally important:

- Optimization/worklist order must not affect semantics or trigger structural
  assertions. Random seed changes are a diagnostic tool, not a way to select a
  preferred result. Minor graph-print and register-spill goldens may vary, but
  should eventually be hardened where practical.
- `compute()` must be monotonic and simpler than `idealize()`. When both inspect
  the same facts, `idealize()` should inspect them in the same order.
- An idealization must preserve already-known type information immediately;
  the expensive `IterPeeps.progressOnList` assertion is intentionally catching
  one-step Church-Rosser/monotonicity failures.
- Maintain Node keep/unkeep lifetime discipline when moving, cloning, or
  replacing graph pieces.
- The parser carries only one bulk memory state. Precise alias slicing and
  `MemMergeNode` are graph semantics, not nested parser state.
- At every program slice, alias memories are disjoint and collectively cover
  every alias exactly once. Bulk memory means “all aliases not split out here.”
- Calls, call ends, returns, and memory parameters deliberately use opaque bulk
  memory to control graph size. Precision across those boundaries comes from
  careful inlining.
- Public concrete bulk alias-`#1` memory is never final. A single final bit
  cannot truthfully describe an aggregate of unrelated unsplit aliases.
  Private singleton memory and precise slices may retain finality.
- Precise aliases can peek through a `MemMerge`. Bulk memory can peek through
  only when its excluded-alias coverage agrees with the merge.
- A bulk Phi carries an exclusion BitSet (`All - E`); parallel `MemPhiNode`s
  carry the precise aliases in `E`.
- Mode selection for unified numeric nodes is a one-shot structural event.
  `_mode` participates in GVN identity; flipping it unlocks and reinitializes
  the node before IterPeeps observes it.

## What has been completed on this branch

### Preservation and early constraint removal

- Created `parser-simplification-plan.md`, the dedicated branch, and permanent
  pre-experiment tag.
- Fixed the `_Scan` constructor migration that initially obscured the work.
- Removed the parser constraint from ordinary Phis. Weak loop Phi inputs are
  tolerated. The dead ordinary-Phi type field and serialization were removed.
- Split memory Phis out of ordinary Phi logic:
  - `MemPhiNode` represents one precise alias.
  - `BulkMemPhiNode` represents `All - exclusions`.
  - Memory-specific compute/ideal behavior moved out of base Phi where useful.

### Casts, guards, and conversions

- Split the old overloaded Cast concept into:
  - `GuardNode`: delayed zero/nonzero branch refinement with direction.
  - `CheckCastNode`: proof/runtime type check which must fold or diagnose.
  - `ConvertNode`: the one numeric/value conversion abstraction.
- Removed the old `liftExpr`-style broad parser helper in favor of explicit,
  smaller conversion sites.
- Removed the obsolete `Evaluator`; `Eval2` is authoritative.

### Parser memory simplification and alias recovery

- Removed the parser's eager nested precise-memory slicing. Parser `ScopeNode`
  state now tracks bulk memory rather than a parser-owned `MemMerge` bundle.
- Alias `#1` is the unresolved Store state. A Store sharpens once to `#N`; all
  later alias evidence must agree.
- Store sharpening inserts/reconstructs precise memory plus a `MemMerge`,
  preserving the bulk remainder locally.
- Added lazy creation/widening of precise memory Phis from bulk Phis and
  MemMerges, including recovery when a parallel precise Phi already collapsed.
- Added checks around the exactly-once memory-slice invariant and fixed several
  load/store push-through oscillations and missing slice cases.
- `EscapeNode` is recognized as a precise alias consumer of a bulk memory Phi.
- Parser constructor handling now recognizes named constructors structurally
  and threads private constructor memory correctly; class-initializer return
  memory handling was also repaired.

### Unified integer/floating operations

- Replaced split integer/FP Add/Sub/Mul/Div opcodes with unified source-level
  operations carrying a one-shot mode: unknown, integer, or float.
- Bool and Minus share the small `ModeNode` contract where useful; Bool output
  remains integer even for floating inputs.
- ULT is permanently integer-only.
- Added mode serialization and removed obsolete commented float-node/tag code.
- Recursive numeric SCCs are resolved after SCCP using graph evidence; float
  wins when both families are present because integer operands can convert.
- Mode resolution now reinitializes cached type immediately before IterPeeps.
- Error checking for Arith/Bool/Minus was unified without allocating argument
  arrays.

### Load and memory type cleanup

- `LoadNode` no longer stores a parser-provided `_con`. Its declared field type
  is obtained via an accessor once the pointer/field exists; late type checking
  reports missing/invalid fields.
- Deeply read-only pointer information is honored by loads instead of being
  widened back to the generic declared struct field type.
- `MemPhiNode` deliberately starts weak enough that later precise field
  knowledge remains monotonic.
- Public concrete bulk memory is forced non-final in `TypeMem`; cyclic dual
  construction handles its temporary null payload. `TypeTest` passed after
  this Type change.

### Calls, cloning, and optimizer order

- Instance-call hidden-self shape is syntactic rather than inferred from the
  eventual function value.
- Function-body cloning/inlining was repaired for loop backedges; an earlier
  bug left cloned inlined nodes pointing into the original function loop.
- IterPeeps replacement scheduling now revisits both old and replacement inputs
  and replacement outputs.
- Monotonicity diagnostics now include node IDs and detailed input types/classes.
- Worklist seeds 124, 125, and 126 exposed and drove fixes for collapsed
  precise Phis, bulk finality, numeric-mode cached types, and Escape alias use.

## Known-good pushed checkpoint

At pushed commit `4f8759b3`:

- Default worklist seed is `126` (also used by `TestC`).
- `TypeTest` passed: 6 tests.
- Chapters 1 through 20 passed: 347 tests, zero failures.
- The seed-126 Ch5 graph shape and Ch20 x86 spill count were accepted as
  goldens after structural failures were eliminated.
- `git diff --check` was clean before that commit was pushed.

Recent milestone commits, newest first:

```text
4f8759b3 Rotate random seed and fix more bugs
14a1a028 Fix general memory split issues
eea3d783 Fix cloning functions with loops
f036a040 minor fixes, thru ch18
eaee86b8 Handle ambiguous self-recursive int/flt conversions
b9434562 Fixed up to ch17, plus some ch18
18f07318 Uniform int/fp errors
f729484c Hey ch1-13 working again
58e012e6 Remove LoadNode dependence on _con
0bc690ae Final cleanup around unified int/fp ops
9720093f Finish off unified int/flt opcodes
54e5b83b Refactor Phi into MemPhi, BulkMemPhi
fe7ce3c4 WIP parse ordinary memory as bulk partitions
21352840 WIP rethread stores after alias resolution
1f7cb4ac Replace Lift with one destination ConvertNode
bbe7ebdd Rename proof casts to CheckCast
b3da12fd Delay branch zero refinements with GuardNode
fa4f33e7 Remove parser type constraint from ordinary phis
```

## Current dirty WIP after `4f8759b3`

At handoff, tracked modifications are:

```text
src/main/java/com/seaofnodes/simple/codegen/Opto.java
src/main/java/com/seaofnodes/simple/node/LoadNode.java
src/main/java/com/seaofnodes/simple/node/StoreNode.java
src/test/java/com/seaofnodes/simple/Chapter10Test.java
src/test/java/com/seaofnodes/simple/Chapter21Test.java
```

These appear to be an active experiment around weak Load/Store computation and
preserving precise Store users while an unresolved bulk Store sharpens:

- `LoadNode.compute()` was reordered to inspect pointer and memory weak states
  explicitly. A non-pointer low input currently falls back to `mem._t`.
- `StoreNode.compute()` currently comments out the `err() -> BOTTOM` fallback,
  and `escapesFrom` was changed from the met value `t` to the stored `val`.
- When an alias-1 Store expands into a precise Store plus MemMerge, users which
  already require the same alias are repointed directly to the new precise
  Store before the bulk Store is replaced.
- Several Chapter 10 goldens currently retain a `Stop[...]` with returned
  memory instead of expecting it to disappear.
- Chapter 21 `testAntiDeps1` x86 spill expectation is changed from 7 to 9.
- `Opto.linkStart` has a commented-out high-escape-set early return. There is
  also an accidental comment typo (`asn inst`); clean that before committing.

Treat all of those as WIP, not settled design. In particular, reconsider
whether changing goldens to retain memory hides incomplete dead-memory cleanup.
Do not discard the changes before understanding the failing case that motivated
them.

The attempted handoff test command could not run because Make reported:

```text
Makefile:111: *** open: .argsM.txt: Permission denied. Stop.
```

This is probably another active-process/Windows file-lock issue. Previously an
IntelliJ Java debugger was conclusively found to lock JAR/class outputs; killing
the debug process allowed compilation immediately. Close relevant Java/IDE or
Make processes, preserve or remove only the transient `.argsM.txt` as
appropriate, then rerun tests. Do not delete source files to work around it.

## Work still required to finish the rewrite

### Immediate frontier

1. Recover the intent and test state of the five dirty WIP files.
2. Clear the `.argsM.txt` lock and run, in order:
   - the motivating focused test;
   - `TypeTest`;
   - Chapters 1–20;
   - Chapter 21, especially `testAntiDeps1` and `testStringExport`;
   - then all of `tests_raw0`.
3. Decide whether same-alias Store users being repointed during sharpening is
   the canonical local rewrite. Verify it preserves every user's old type in
   one step and the exactly-once alias coverage invariant.
4. Restore the `Opto.linkStart` high-set error handling unless a reduced test
   proves it wrong; never iterate `XInt.next` over a high/infinite set blindly.
5. Separate legitimate golden changes from leaked/dead returned memory.

### Store `_con` and weak diagnostics

`LoadNode._con` is gone, but `StoreNode._con` remains. It still sharpens from a
Field and trims stored values. Continue the original small-step experiment:

- Determine whether `_con` is authoritative declared storage width or merely a
  cache of `ptr.field(name)._t`.
- If it is derivable, replace uses with an accessor as was done for Load.
- Keep error reporting in `err()`/type checking rather than manufacturing
  parser-time graph topology or accidental BOTTOM propagation.
- Use a diagnostic whose offending node has no BOTTOM inputs when validating
  late error selection.

### True unresolved lexical fields (original Phases 2/3)

The memory alias machinery now tolerates an alias-1 Store and sharpens it
later, but the full lexical design is not complete:

- Unqualified instance/class field references before declarations must resolve
  by closed lexical scope, not by whichever pointer type happens to sharpen.
- A scope-owned unresolved load/store representation may still be needed.
- Resolution must select self versus class initializer base, precise alias, and
  constructor `init` status together.
- Locals remain define-before-use and need no unresolved mechanism.
- Verify loops can lazily widen precise alias runs without global O(n²)
  reconstruction or Phi/MemMerge oscillation.

### Field finality (original Phase 6)

The planned two-bit four-state Field finality has not been implemented:

```text
00 unknown
01 declared mutable
10 declared final
11 conflicting / BAD_FINAL
```

This still needs accessors, meet/join specification, equality, hashing, dual,
printing/serialization as needed, representative `gather()` entries, and full
`TypeTest`. Parser-declared final stores require the structural `init` marker;
non-init writes to a resolved final field are errors. Cyclic named structs must
merge finality during `Type.closeOver()`.

Do not confuse Field finality with bulk-memory finality: public bulk memory is
now always conservatively non-final.

### Function contracts (original Phase 5)

`FunNode._sig` still deserves an audit:

- Declared argument syntax is required and authoritative.
- Return type is graph-derived.
- Private functions may sharpen from known call sites.
- Escaping functions need a worst-case/default unknown caller tied to the
  declared type binding.
- Forward-declared argument types need a changing type source, not a true
  ConstantNode.
- Remove cached signature state that can be derived from Fun/Parm/Return edges;
  retain explicit declared contracts.

### Final acceptance

- Sweep several deterministic worklist seeds, not just 126. Semantic graph
  results must converge independent of order.
- Harden graph-print and spill-count tests where harmless scheduling variation
  remains. Do not weaken monotonicity or memory-coverage assertions.
- Run `tests_raw0`, then `tests_raw1`, then `tests_alone`.
- Run `TypeTest` after every Type/Field lattice change.
- Confirm serialization/deserialization after every new semantic node field or
  mode bit.
- Audit the parser for remaining type-dependent structural choices, especially
  conversions, field base selection, function self shape, and alias slicing.

## Debugging guidance

- Node IDs are central to debugging; refer to nodes as `Store#107`, `Phi#105`,
  etc.
- Stop at the earliest `IterPeeps` count where the graph becomes invalid.
- `Opto.worklistCheck` catches missed/non-monotonic SCCP updates.
- `IterPeeps.progressOnList` catches one-step type and idealization failures;
  its current detailed input dump was added specifically for order bugs.
- For memory bugs, check both:
  1. each program slice covers every alias exactly once; and
  2. a precise consumer never obtains an alias excluded from its bulk input
     without a parallel precise slice.
- A precise `MemPhi#N` fed by a `MemMerge` whose default bulk excludes `N` and
  which has no explicit `N` input is malformed.
- Avoid “fixes” which merely queue a favored node earlier. Change the seed and
  demand order-independent semantics.
- Golden changes are acceptable only after semantic evaluation, type
  monotonicity, and graph invariants are known good.

## Workflow and safety

- Cliff wants to review every push. Ask permission immediately before pushing.
  In this handoff Cliff said he would push the current completed checkpoint;
  do not assume that grants future push permission.
- Push remote checkpoints for backup once approved.
- Commit at useful test frontiers with messages describing the architectural
  change, not only the symptom.
- Preserve unrelated dirty edits. Emacs temporary files are common and need
  not be reported repeatedly.
- Windows IntelliJ debug sessions may lock JARs/class files. Report the lock,
  have Cliff stop the debugger, and retry before changing build machinery.
- Prefer narrow changes; unwind experiments that cause cascading regressions.

## Rollback paths

- Whole experiment: branch from tag `ch25-parser-before-simplification`.
- Individual mechanism: revert/cherry-pick the narrow branch commits.
- Inspect old implementation without mutating this branch:
  `git diff ch25-parser-before-simplification..HEAD` or `git show <old>:<path>`.
- Do not squash until the rewrite is accepted; the commit sequence is part of
  the recovery design.
