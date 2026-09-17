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
| B03 | Correct x86 logical-not encoding | 21 through 24 | `NotX86`: preserve a shared source/destination until after testing; correct SETcc operand fields and zero-extend its byte result. Check zero/nonzero, overlapping registers, and byte registers requiring REX. Exclude Not/Phi optimizations. |
| B04 | Preserve comparison results while releasing parser temporaries | 24 | `parseComparison` lifetime fix in `414931c8`. Reduce seed `-8212834489697770130`; exclude unrelated `<clinit>` cleanup in that commit. |
| B05 | Preserve RHS control, memory, and local updates in logical OR | 23 through 24; **audit** earliest implementation | Chapter 25 `parseIf` retains `_scope` for the false-side `||` expression. Test scalar and field updates and short-circuit behavior. Avoid importing GuardNode. |
| B06 | Decode string escapes; reject unknown/truncated escapes | 22 through 24 | Literal scanner only. Check newline, tab, carriage return, backslash, quote, NUL, and malformed input. |
| B07 | Decode character escapes; reject truncated literals | 22 through 24 | Separate patch sharing B06's helper. Check values and errors; `parseChar` first appears in 22. |
| B08 | Complete `&=` and `|=` syntax sugar | 17 onward; **audit** availability of bitwise operators | A small language addition, not a correctness-only patch. Move lexer and assignment expansion together. Check locals, fields, arrays, and exactly-once address evaluation. |
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
