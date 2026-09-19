# Chapter backport review

This is a queue of pending work. Remove completed corrections from the queue. Independent
corrections should start in the earliest affected chapter and propagate through
every later affected snapshot. A regression in Chapter 25's `Chapter21Test`
does not test the compiler in the `chapter21` directory.

For now, keep building SSA with incomplete types in Chapter 25. Moving that
architecture earlier, or splitting Chapter 25, is deferred while small changes
establish the review workflow. No renumbering is committed.

## Small changes

Keep each correction's reduced failure, smallest patch, and test results together
for review.

An **audit** item needs an old-chapter reproducer before it is scheduled. Copy
the correction into the chapter's representation, not the entire modern file.

| ID | Change | Proposed destination | Scope and acceptance evidence |
|---|---|---|---|
| B09 | Make diagnostic function lookup read-only | 18 onward; **audit** first mutating printer lookup | Separate `lookupFun` from cleanup-performing `link`. Verify printing preserves graph/linker state. Do not import FunPtrNode or compilation units for this fix. |
| B10 | Preserve widening state in integer nonzero refinement | **Audit** first chapter with both widening and `nonZero`; no later than 24 | Chapter 25 `TypeInteger.nonZero` preserves `_widen`. Check lattice laws and loop refinement. Exclude TypeScalar, storage-type, and serialization changes. |
| B11 | Diagnose return types using the optimized return expression | 18; 19 already has the correction | `ReturnNode.err()` uses `expr()._type` instead of the parse-time `mt` aggregate. Chapter 18 rejects `struct S { u8 x; }; return new S; return 0;` with a mixed integer/reference error; Chapter 19 accepts it. Also test genuinely reachable incompatible returns. This is a candidate, not yet an applied or isolated-patch-verified fix. |
| B12 | Encode the actual destination register for two-address immediate multiply | 21 | `MulIX86` inherits `ImmX86.encoding`, whose ModRM.reg is fixed to zero and whose REX.R is clear. With source/destination both `rcx`, multiplying by 11 emits `48 6b c1 0b` (destination `rax`) instead of `48 6b c9 0b`. With both `r9`, it emits `49 6b c1 0b` instead of `4d 6b c9 0b`. Give multiply its proper register fields while preserving Chapter 21's two-address allocation contract; do not change the opcode-extension fields used by other `ImmX86` subclasses. Later chapters use a separate multiply encoder. |
| B14 | Check leaf type kinds before cyclic equality | 23-24; 25 already checks | `Type.cycle_eq` calls leaf `eq` without checking `_type`. Intern a struct with a `Type.BOTTOM` field, then same-named structs with float constants 1.0 and 2.0; meeting the latter throws ClassCastException in `TypeFloat.eq`. Reproduced independently in 23 and 24; the adapted probe passes in 25. Add a deterministic TypeTest before backporting the type-kind check. This is separate from B13; no type implementation changed. |

After the first two reviews, batch only corrections with established independence
and regressions. Keep one logical correction per commit across affected chapters.

## Larger or entangled changes: defer

| Group | Eventual home | Why not in the small queue yet |
|---|---|---|
| Dominator caches, scheduling, global constant cloning | 11 / 18 / 19 as applicable | Current changes mix inlining, scheduling, and compilation-unit ownership. Need a reduced old-IR failure. |
| Register allocation and spilling | 20 onward | Need constrained-register/spill regressions; changed golden spill counts are insufficient evidence. |
| Conditional Store and array Load control | Memory/arrays chapters | Reproduce under the earlier alias model before extracting fixes from the new memory implementation. |
| SCCP dependencies, function revival, reachability | 24; some foundations may fit 18 | Separate old-IR corrections from new Guard/Escape/BulkMemPhi and external-caller machinery. |
| Lattice fixes in `7f3b8856` | Owning type introduction | The commit mixes TypeFunPtr, TypeMem, and new XInt/escape-summary behavior. Not a generic earlier-lattice patch. |
| TypeScalar, numeric modes, guards, symbolic fields, open/forward types | Revisit earlier homes later | A connected incomplete-types architecture, including phase ordering and errors. |
| BulkMemPhi/MemPhi, private constructor memory, allocation helpers | Revisit memory / constructors / methods | Move invariants and regressions together. |
| Serialization, global identity remapping, module escape summaries | Separate compilation | Remain with modules. |

## Review and test protocol

1. Run the destination's unmodified `make tests`. Record baseline failures and
   resolve them before accepting a compiler backport into that chapter.
2. Add a deterministic regression proving the old failure. Prefer tiny source
   programs; use encoding checks when register placement is the property at issue.
3. Apply the smallest correction; run the focused regression and full chapter
   `make tests`. Stop for Cliff's review before the next trial fix.
4. Propagate the accepted fix and regression through every affected later
   snapshot. Run each snapshot's full `make tests`, not just 25's inherited tests.
5. Run TypeTest for lattice changes and rebuild system objects when compiler
   changes invalidate their IR/native code. Force a full Java rebuild when shared
   APIs or constants change: older Makefiles do not track all Java dependencies.
   Do not weaken checks or drop tests.
6. Report chapters, commands, and results for review; remove completed items from
   this queue. Keep unrelated dirty changes;
   do not push without explicit approval.

The top-level runner supports all 25 chapters:

```sh
make lib                   # dependencies, before tests on a fresh checkout
make tests
make -k tests              # visit all chapters; failures still fail the command
make tests CHAPTERS="chapter21 chapter22"
make tags release CHAPTERS="chapter01 chapter24 chapter25"
```

Runs are sequential, and child failures propagate. `make tag` aliases `tags`.
Java, GNU make, bash, ctags (for tags), and the existing native test tools must
be on PATH. Early release targets package classes into jars; they do not imply
standalone executable compiler drivers. Older Makefiles discover jars when make
starts, so run `make lib` separately before `make tests`.

## Validation record

### Issue #246: Chapter 25 reproduction and local correction

On 2026-09-19, rebuilt Chapter 25 with
`make build/classes/main/.mtag build/classes/test/.ttag` and ran the following
through `new CodeGen(src).driver(CodeGen.Phase.TypeCheck)` with Java assertions
enabled. Before the correction it failed in `MemOpNode.err()` with
`Might be null accessing 'x'`.
The original issue's `new Point { x = 42; }` syntax is no longer accepted;
only object construction was adapted to Chapter 25's constructor syntax.

```text
struct Point { int x; new Point = { int v -> x = v; }; };
Point?[] !points = new Point?[2];
points[arg] = new Point(42);
Point? p = points[1];
if (p != null)
    return p.x;
return -1;
```

That failure prevented evaluation of either expected runtime result.

Debugging isolated the first bad phase to parsing. `Parser.parseEquality`
builds `!(p == null)`, and `BoolNode.EQ.idealize` rewrites the inner comparison
to `!p`. `ScopeNode._addGuards` records nonzero `!!p` and zero `!p` on the
true arm, but does not descend again to record non-null `p`. Meanwhile,
`CProjNode.idealize` strips the negations from the control test. The resulting
graph therefore tests `p` directly without refining the pointer used by `p.x`.

With default seed 126, the original's `LoadNode#150` uses nullable
`ReadOnlyNode#126` directly after Parse, Iter, and Opto. Changing only the
condition to `p` produces `GuardNode#132` on that same pointer, consumed by
`LoadNode#135`, and returns `-1` and `42` as expected. Explicit `!!p` fails;
`if (p == null) return -1; return p.x;` passes with both expected results.
The original failure and successful `if (p)` control were also checked with
seeds 1, 42, and 123, with assertions enabled.

The local correction recurses into a Not operand when it is another Not or a
short-circuit Phi, flipping the proven truth. Existing single-negation handling
remains in place; its guards need not be duplicated. Each recursive call still
checks dominance/schedulability, and the predicate is kept alive while recursive
peepholes run. Boolean expression values retain their original meaning.

`Chapter10Test.testNullGuards` covers both comparison orders,
nested negations, early returns, a reused Boolean value, and negated
short-circuit logic. Each case checks both runtime outcomes and scheduling with
seeds 1, 42, and 126. `testNullGuardErrors` verifies rejection of
unguarded access, a check on a different pointer, and a guard used beyond its
branch. The positive regression failed before the compiler change.

Final `make tests` passed all 427 tests with assertions enabled: 362 raw0,
32 raw1, 7 standalone, 18 system, 1 fixed-seed fuzzer wrapper, and 7 remaining
tests. The build regenerated `sys.o`. Log: `chapter25/build/issue246-tests.log`.
A broader intermediate version that duplicated single-negation handling exposed
a Dijkstra SCCP assertion; it was not retained. The original compiler passed
Dijkstra in a comparison run with freshly rebuilt `sys.o`, and the final
correction passes it too. No assertions or existing test expectations changed.

Separate pre-existing diagnostic concern: using the same array setup with
`if (p == null) return p.x; return -1;` was accepted before the correction,
despite dereferencing null on the taken arm. This remains outside B13's fix.

### B13: earliest chapter and regression placement

Null-check refinement begins in Chapter 10, alongside nullable struct pointers;
it is documented in `chapter10/README.md` and implemented by
`ScopeNode.upcast`. Chapter 9 has no corresponding pointer guards. The reduced
failure already reproduces in Chapter 10:

```text
struct Point { int x; };
Point point = new Point;
point.x = 42;
Point? p = null;
if (arg) p = point;
if (p != null) return p.x;
return -1;
```

The same program with `if (p)` passes. The array version reproduces in Chapter
15, where arrays first appear; its `if (p)` control also passes.

Substantial guard implementation differences before the backport:

| Chapters | Representation and behavior |
|---|---|
| 10-16 | `ScopeNode.upcast` refines only values present directly in local scope inputs. A non-null pointer uses `CastNode(TypeMemPtr.VOIDPTR, ...)`; a zero/null fact replaces a local with a constant. There is no expression-guard table and no general nonzero integer refinement. |
| 13 onward | The parser also refines the false arm without an explicit `else`, allowing a fact to survive an early return. Chapters 10-12 require an explicit `else` for that test. |
| 17 | `addGuards`/`removeGuards` introduce a scoped predicate/cast table and `upcastGuard` applies facts to later matching expressions, including loads. Refinements use `nonZero`/`makeZero` and type-bearing CastNodes. |
| 18-24 | The same scoped Cast architecture; `_addGuard` factors the two cases and rejects high joins. Chapter 23 adds short-circuit syntax, but these scopes do not recursively decompose its Phis as Chapter 25 does. |
| 25 | GuardNode stores zero/nonzero intent and derives the value family from its input, supporting incomplete types. Scope guard discovery checks dominance, decomposes short-circuit Phis, and now follows nested negations. |

The tests have been moved from Chapter 25's `Chapter25Test.java` to
`Chapter10Test.java` and forward-ported into that same test class in every
snapshot from 10 through 25. Chapters 10-14 use the reduced pointer setup;
15-24 use arrays with the allocation syntax available there; 25 retains the
constructor-based array setup, seed rotation, and scheduling checks. Boolean
temporaries use `int` in earlier snapshots, early-return coverage uses an
explicit `else` before Chapter 13, and short-circuit coverage starts at 23.
The tests are active and assert successful guarded access, not the old error.

The compiler backport now retains each chapter's existing guard representation
and adds negation traversal there, as detailed below. Importing Chapter 25's
GuardNode/incomplete-type architecture remains a separate design change.

Before adding the tests, `make -k tests` passed in all snapshots 10-24.
Log: `chapter25/build/issue246-review/baseline.log`.

After moving/porting the tests, ran `make -k tests` across 10-25:

- Chapters 10-23 each fail only `Chapter10Test.testNullGuards`, with
  `Might be null accessing 'x'`. The new rejection test and all existing tests
  in those suites pass. These were the exposed regressions before the compiler
  backport recorded below.
- Chapter 24's isolated `Chapter10Test` runs 26 tests with only the same B13
  failure. Its full suite also exposes a type-equality failure, starting at
  `Chapter16Test.testSquare`: `TypeFloat.eq` receives a plain `Type` through
  `Type.cycle_eq`, causing a ClassCastException and later cascading failures
  (197 failures total, including B13). A forced `make -B tests CHAPTERS=chapter24`
  reproduces this, so a rebuild does not resolve it. No type implementation
  was changed. This additional failure must be reviewed separately.
- Chapter 25 passes all 427 tests after relocation: 364 raw0, 32 raw1,
  7 standalone, 16 system, 1 fuzzer wrapper, and 7 remaining tests.

Logs: `chapter25/build/issue246-review/ported-tests.log`,
`chapter25/build/issue246-review/chapter24-focused.log`, and
`chapter25/build/issue246-review/chapter24-rebuilt.log`.

### B13: completed compiler backport

Applied locally on 2026-09-19 to every snapshot from Chapter 10 through 25;
nothing pushed. The same paired-negation traversal starts in Chapter 10 and
continues forward, with representation-specific adaptations:

- 10-16 recurse within `upcast`, retaining local-binding casts and constants.
  No dominance machinery, expression-guard table, or modern type architecture
  is introduced.
- 17-22 separate the public guard-set marker from recursive `_addGuards`.
  Nested negations add facts within the existing scoped CastNode table;
  recursive calls do not add extra scope markers.
- 23-24 additionally use Chapter 25's short-circuit Phi decomposition, since
  short-circuit syntax starts in 23. A small `availableAt` helper walks data
  inputs and the existing CFG immediate-dominator chain to reject RHS-only
  values unavailable at the proposed guard. Phi availability is checked at
  its Region. This avoids importing 25's folding-aware `earlyCFG` machinery.
- 25 retains its existing GuardNode, dominance, and short-circuit mechanisms;
  only the nested-negation discovery is added.

All versions retain the original single-negation handling and keep the
predicate alive during recursive peepholes. The added guards do not change
the Boolean expression's value.

The reused-Boolean regression also exposed `Eval2` casting a pointer to Long
when evaluating Not in 18-24. Those test evaluators now share Chapter 25's
null/integer/float-aware Not helper. Compiler type implementations are unchanged.

`Chapter10Test` remains the home of the forward-ported regression in every
snapshot 10-25. In 23-25, `testShortCircuitGuardScheduling` additionally checks
an RHS-only call result through local scheduling and evaluates both branch
outcomes and a zero result. Chapter 25's pointer cases retain seed rotation.

Each full `make tests` target passes after the correction:

| Chapters | Ordinary tests per chapter | Additional suites |
|---|---|---|
| 10-17 | 149, 164, 166, 181, 201, 212, 230, 282 | Each complete chapter target passed. |
| 18-22 | 309, 350, 359, 374, 386 | Each also passed its fixed-seed fuzzer wrapper. |
| 23-24 | 403, 425 | Each also passed its fixed-seed fuzzer wrapper. |
| 25 | 428 total | 365 raw0, 32 raw1, 7 standalone, 16 system, 1 fuzzer wrapper, 7 remaining. |

Commands were `make tests CHAPTERS=chapter10`, then `make -k tests` with
`CHAPTERS='chapter11 ... chapter22'`, `CHAPTERS='chapter18 ... chapter24'`,
and finally `CHAPTERS='chapter23 chapter24 chapter25'` (the ellipses here
abbreviate explicitly enumerated chapter names). Logs under
`chapter25/build/issue246-review/`: `chapter10-fixed.log`,
`chapters11-22-fixed.log` (11-17 passed; 18-22 exposed the evaluator issue),
`chapters18-24-fixed.log`, and `chapters23-25-final.log`.

The separate cyclic-equality failure is not fixed by B13's green suites.
It has a deterministic standalone reproducer in 23 and 24 and is queued as
B14. The probe interns `Probe { x: BOTTOM }`, `Probe { x: 1.0 }`, and
`Probe { x: 2.0 }`, then meets the latter two. It throws in `TypeFloat.eq`
through `Type.cycle_eq`; Chapter 25's equivalent probe succeeds. This records
the previously order-sensitive full-suite failure without expanding the guard
fix into a lattice change.

### Initial setup baseline

On 2026-09-17, using Windows/Cygwin and OpenJDK 22.0.2:

| Check | Result |
|---|---|
| All 25 chapter `tags` targets | Passed via `make -k tags`. |
| All 25 chapter `release` targets | Passed, in two batches (1-22 and 23-25). |
| `make -k tests`: Chapters 1-17, 19-21, 23-25 | Passed each complete chapter target. |
| Chapter 18 | 300 ordinary tests passed; `fuzzPeepsSmall` failed on seed `973358943756616234`. This test chooses seeds from wall-clock time. Preserve the failure rather than rerunning until green. |
| Chapter 22, initial run | `testLatticeTheory` failed with a TypeConAry/TypeRPC cast error. |
| `make -B tests CHAPTERS=chapter22` | Passed after full rebuild: 369 ordinary tests and the fuzzer test. This is consistent with stale classes; the original failure remains recorded. |

That initial test baseline was **not green** because of Chapter 18. The follow-up
below records the explicitly requested change to default fuzzing coverage; the
underlying compiler failure remains open.

Chapter 25 initially could not run its system suite because the Hello World
example was deleted in the working tree. Cliff restored it; the final full run
passed (362 raw0, 32 raw1, 7 standalone, 16 system, and 1 fuzzer test).

Build-only fixes made during setup: supply missing early Makefiles and release
targets, alias Chapter 25's `TAGS` as `tags`, use the existing older-chapter ctags
option spelling in 21-24, and correct malformed `$(file ...)` argument-list
writes in 22-24. These changes do not move compiler behavior between chapters.

Local logs (ignored build artifacts): `build/review/tests-verified.log`,
`build/review/chapter22-rebuilt-tests.log`, `build/review/tags-verified.log`,
`build/review/release-01-22.log`, and `build/review/release-23-25.log`.

The Chapter 18 fuzzer printed this reduced input:

```java
struct s0 {
    u8 v1;
};
s0? !UmPOLQK=null;
if(0)
    while(0&UmPOLQK.v1) {}
if(UmPOLQK.v1) {}
return new s0;
while(0) {}
```

### Deterministic fuzzer follow-up

Chapters 8-24 now follow Chapter 25's seed-list structure. Each wrapper has its
own `REGRESSION_SEEDS` and ignored `OPEN_FAILING_SEEDS`. Exploratory fuzzing is
opt-in, including the previously automatic random runs. Chapters 8-15 now invoke
their wrappers from `make tests` as well. Fixed-seed failures throw immediately
with the seed and original cause, without entering the exploratory reducer.
Each chapter retains its generator and checking strategy; seeds are not copied
between chapters.

The regression lists start empty. Chapter 18's open list contains only
`973358943756616234`; explicitly invoking that list still reproduces the failure.
Passing default tests therefore does not mean that this compiler bug is fixed
or that new fuzz regression coverage has been established. After a correction,
move its seed to that chapter's regression list once the full seed passes.

Validation: `make -k tests` passed across all 25 chapters after the harness
changes. After adding wrapper invocation to Chapters 8-15, their complete
`make tests` targets passed again. Logs: `build/review/seed-lists-all-tests.log`,
`build/review/seed-lists-08-15-tests.log`, and
`build/review/chapter18-open-seed.log` (intentional failure).

The reduced Chapter 18 source exposes two separate concerns:

- With peepholes disabled, nullable field lookup can fail during parsing;
  with peepholes enabled, the reduced original reaches a mixed-return error.
  Chapter 19 changes the fuzzer comparison to two optimizer worklist seeds,
  rather than peepholes disabled/enabled. That harness change deserves separate
  review; it is not proof that the compiler discrepancy was corrected.
- The mixed-return diagnostic reduces further to
  `struct S { u8 x; }; return new S; return 0;`. Chapter 18 rejects it with
  peepholes both disabled and enabled. Chapter 19 accepts it under both fuzzer
  worklist orders. A plain `return new S;` is accepted in both chapters. The
  corresponding Chapter 19 `ReturnNode.err()` change is B11 above. Dead
  `if(0) return 0;` and a trailing `while(0) {}` also trigger the Chapter 18
  error. No compiler code has been changed yet.

Additional source probes across Chapters 19-25 show differing treatment of
unused nullable loads. A live `return p.x;` with null `p` was rejected in every
tested snapshot. Keep the original seed open: B11 alone has not been shown to
resolve its separate parser/checking discrepancy.
