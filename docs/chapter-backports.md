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

Historical results below are dated evidence, not a substitute for a fresh
baseline. Logs live in ignored build directories and may no longer exist.
Reusable implementation lessons are in `skills/chapter25-codex-notes.md`.

### B13 / issue #246: complete, 2026-09-19

Cliff reports the correction pushed. Nested negation in `p != null` / `!!p`
now discovers the underlying pointer guard in every snapshot 10-25, retaining
each chapter's guard representation. Chapters 23-24 also decompose short-circuit
Phis with availability checks; Eval2 in 18-24 now handles pointer/null Not.

`Chapter10Test.testNullGuards` failed before the backport in 10-24 and passes
afterward; `testNullGuardErrors` checks rejected uses. Both tests live in every
snapshot 10-25. `testShortCircuitGuardScheduling` covers RHS-only call results
in 23-25; Chapter 25 also rotates optimizer seeds. Full `make tests` passed in
each directory 10-25. Ordinary test counts were 149, 164, 166, 181, 201, 212,
230, 282, 309, 350, 359, 374, 386, 403, 425 in 10-24; Chapter 25 passed 428
total. No assertions or existing expectations were weakened.
Logs: `chapter25/build/issue246-review/`, particularly
`chapters18-24-fixed.log` and `chapters23-25-final.log`.

Independent findings retained for follow-up:

- B14's cyclic-equality failure remained reproducible despite green B13
  suites; it is corrected separately below.
- A pre-existing Chapter 25 diagnostic concern remains outside B13: with the
  issue's nullable array setup, `if (p == null) return p.x; return -1;` was
  accepted despite dereferencing null on the taken arm. Original setup:
  `struct Point { int x; new Point = { int v -> x = v; }; };`
  `Point?[] !points = new Point?[2]; points[arg] = new Point(42);`
  `Point? p = points[1];`. This needs separate investigation.

### B14: complete locally, 2026-09-19

Added the existing Chapter 25 leaf type-kind check to `Type.cycle_eq` in 23-24.
Cyclic structural equality first appears in 23. Inspection of 9-22 found the
older interned-child identity representation with kind checks in `Type.equals`;
the equivalent BOTTOM/float-struct meet probe passes in 22 without a correction.
No earlier compiler change is needed for B14.

`TypeTest.testCyclicLeafKinds` in 23-25 interns same-named structs with `x`
typed BOTTOM, 1.0, and 2.0, then meets the latter two. Before the correction it
throws ClassCastException through `TypeFloat.eq` in 23 and 24; 25 passes.
Afterward all three pass, checking F32 field type, canonical interning,
commutativity, absorption by the BOTTOM-field struct, and the dual/join result.

Validation with assertions enabled (Windows/Cygwin, JDK 21.0.4):

- Unmodified baseline: `make -k tests CHAPTERS="chapter22 chapter23 chapter24 chapter25"`
  passed each full target.
- After a full Java rebuild of 23-24, the focused regression and full TypeTest
  passed in 23-25 (6 TypeTest cases in each).
- `make -k tests CHAPTERS="chapter23 chapter24 chapter25"` passed: 404 and 426
  ordinary tests plus their fuzzer wrappers in 23 and 24; 429 total in 25
  (365 raw0, 32 raw1, 8 standalone, 16 system, 1 fuzzer, 7 remaining).

Logs: `chapter25/build/b14-review/{baseline,before-fix,fixed}.log`.

### Build and fuzzer baseline, 2026-09-17

Windows/Cygwin, OpenJDK 22.0.2: all 25 chapters passed `tags` and `release`.
Build setup supplied missing early targets and corrected argument-list writes
in 22-24; no compiler behavior was backported. Chapter 22 initially failed
TypeTest with a TypeConAry/TypeRPC cast error, then passed a forced rebuild
(369 ordinary tests plus fuzzer), consistent with stale classes.

The initial all-chapter test baseline failed Chapter 18's random fuzzer on
seed `973358943756616234`. At Cliff's request, wrappers in 8-24 now follow
25's explicit seed-list structure, and 8-15 invoke their wrappers from
`make tests`. Exploratory fuzzing is opt-in. All 25 full chapter targets
passed after these harness changes; 8-15 passed again after wrapper invocation
was added. Logs: `build/review/seed-lists-all-tests.log` and
`seed-lists-08-15-tests.log`.

**The Chapter 18 compiler failure remains open.** Its seed is in
`OPEN_FAILING_SEEDS`, and explicitly running it still failed. Regression lists
started empty, so green default wrappers added no fuzz regression coverage.
Reduced input:

```java
struct s0 { u8 v1; };
s0? !UmPOLQK=null;
if(0) while(0&UmPOLQK.v1) {}
if(UmPOLQK.v1) {}
return new s0;
while(0) {}
```

This exposes two distinct concerns: nullable field lookup can fail during
parsing with peepholes disabled; with peepholes enabled it reaches a
mixed-return error. B11 isolates the latter further to
`struct S { u8 x; }; return new S; return 0;`, rejected in 18 and accepted in
19. Chapter 19 also changes the fuzzer comparison from peepholes off/on to
two optimizer worklist seeds. Neither that harness change nor B11 alone proves
the original seed fixed. Keep it open until the full seed passes.
Log: `build/review/chapter18-open-seed.log` (intentional failure).
