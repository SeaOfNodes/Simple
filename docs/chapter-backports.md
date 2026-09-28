# Chapter backport review

This is a queue of pending work. Completed corrections leave the queue; the
[validation summary](#validation-record) records their scope. Detailed old logs
are disposable build artifacts. Reusable rules live in
[the AI notes](../skills/chapter25-codex-notes.md).

Introduce independent fixes in the earliest applicable chapter and propagate
them through every affected snapshot. Testing Chapter 25's inherited tests does
not validate the compilers in the earlier chapter directories. Keep building
SSA with incomplete types in Chapter 25 for now; moving that architecture or
renumbering chapters is deferred. Cliff wants to revisit splitting Chapter 25
into smaller chapters; escape analysis remains in 25 until that larger review.

Reset checkpoint (2026-09-28): Cliff committed the completed FunPtr work as
`75390f47` (`Backport FunPtr`), following `586c8f65` (direct C data bindings) and
`8212a66f` (constructor checks). The working tree was clean before this notes
update. No FunPtr implementation or validation remains outstanding; the full
21-25 results are recorded below. The subsequent narrow C integer-return ABI
correction starts in 22 and is forwarded through 25; see the validation record.
Splitting Chapter 25 is deferred, not an instruction to renumber.

## Pending corrections

- **Chapter 24 Load BOTTOM-on-error backport, unwound/deferred (2026-09-28).**
  Cliff requested starting with 24 and analyzing failures before proceeding.
  Literal global BOTTOM memory/pointer inputs already propagate BOTTOM. The
  missing behavior is `LoadNode.compute()` returning BOTTOM instead of the
  declared field type when `err()!=null`; Cliff unwound that one-line experiment.
  Chapter 25's Convert nodes supply the surrounding expression/loop's typed
  result while retaining the erroneous BOTTOM-producing Load for TypeCheck.
  Chapter 24 relies on the Load itself supplying that typed result. Defer this
  backport with the conversion split rather than importing it alone.
  The experimental full Make suite had four failures out of 477
  tests (`build/load-bottom-24.log`); the fuzzer recipe was not reached.
  - `BrainFuckTest.testBrainFuck`: during parsing, the unfinished loop Phi for
    mutable `program` has its declared nullable type. The element load becomes
    BOTTOM, and reading the initialized `command` local triggers Parser's
    BOTTOM-means-uninitialized check before the loop can finish. Reduced valid
    source: `var !program="a"; for(int pc=0; pc<program#; pc++) { var command=program[pc]; return command; } return 0;`.
  - `Chapter10Test.testWhileWithNullInside`: the loop's result Phi starts at its
    declared `i64` while incomplete. Closing the loop exposes a BOTTOM Load on
    the backedge; Phi.compute explicitly propagates BOTTOM, violating the
    pessimistic `setType` narrowing assertion. Trace: Phi#1458 goes from i64 to
    BOTTOM with Load#1462 on its backedge. This occurs during parsing, before SCCP.
  - `Chapter10Test.testNullGuardErrors`: ReturnNode sees BOTTOM without any
    recorded scalar return kind, so its `No defined return type` error wins
    before the Load's `Might be null` diagnostic.
  - `Chapter13Test.testLinkedList0`: `head.next` becomes BOTTOM, and parsing the
    following `.i` reports `Expected reference but found Bot` first.
  Scratch instrumentation lives entirely under `build/load-bottom-probe`.
  Reduced sources pass or report the intended null error with the HEAD Load
  compiled separately, and reproduce the failures with the one-line change
  (`build/load-bottom-reduced-baseline.log`, `build/load-bottom-reduced.log`,
  `build/load-bottom-trace.log`). No earlier chapter was changed. Fixing these
  requires coordinating parse-time incomplete types, typed loop-Phi bounds,
  and diagnostics; the assertion and expected errors have not been weakened.

- **Chapter 25 BOTTOM-on-error backport boundary (2026-09-28).** Cliff requested
  investigating the earlier arithmetic contracts. In 24, changing ArithNode's
  non-integer fallback to global BOTTOM breaks the valid `testCoRecur`: the
  declaration `val az = x*2` refers to `x` defined later, and the allocation-time
  check confuses its temporary BOTTOM with an unset field. Three arithmetic
  diagnostics also become generic parser errors (`testNullRef5`, `testMaskFloat`,
  `testSubZeroTypeError`). Full-suite evidence: `build/arithmetic-contract-24.log`.
  Restoring the integer fallback fixes all four in a 40-test focused run
  (`build/arithmetic-top-24.log`). The Java return signature itself has no typed
  caller dependency. Further BOTTOM work is paused for Cliff's decision about
  separating forward/unresolved expressions from initialization errors in the
  earlier parser. The independent Load experiment is recorded above. The
  known-null regression is already rejected
  during parsing in 10-24. Keep this separate from the constructor/memory fixes.

## AOT class initialization: larger independent work

Proposed on 2026-09-19; deferred behind the smaller queue above. This is
a substantial, self-contained Chapter 25 change, not an entangled backport.
Earlier chapter applicability has not been established.

The goal is to pre-allocate AOT class objects in ELF data and pre-fill their
fields, letting ordinary graph optimization remove redundant initialization.
Lazy-loaded classes retain runtime initialization for now.

- Represent static object creation with a dedicated node, or a ConstantNode
  carrying a TypeStruct with the appropriate final field values. Preserve the
  class's identity and canonical layout independently of its current field
  values; equal contents must not merge distinct class objects.
- Use normal StoreNode peepholes to remove same-value-over-same-value stores.
  If needed, add a peephole that folds initializing stores into the static
  creator's type. Create a replacement ConstantNode/type; never mutate a shared
  constant's type. Prove that folding preserves initialization ordering and
  does not expose a final value prematurely through another reference.
- When `<clinit>` reduces to returning the static object, with no remaining
  I/O or public-memory effects, its runtime work can disappear. Initially it
  is sufficient to emit a trivial `<clinit>` and keep its calls. Omitting the
  function or calls is a later optimization; mixed initializers retain their
  remaining effects. Adapt this conceptual return to the existing class-memory
  representation rather than assuming the current `<clinit>` returns a pointer.
- Extend encoding's relocation information to describe addresses stored inside
  data: functions, other class objects, and constant objects such as strings.
  Emit the required data-section relocations, including their symbol, field
  offset, width, and addend. Keep native linking and emulator linking consistent.
- Give the function and data distinct stable symbols, e.g. `A.B.<clinit>` and
  `A.B.$class`. The defining CompUnit owns the class object and emits its one
  definition in its ELF. Other compilation units, including source currently
  being compiled, reference those symbols without emitting another copy of the
  class data. Unifying loaded Simple types/IR alone does not unify native data;
  preserve ownership and identity across partial compiles and serialization.
- Target four-byte function **and data** pointers, with code and data addresses
  restricted to the low 4 GB. Function pointers stay four bytes; data pointers
  must be brought into agreement. Account for layout, loads/stores, relocations,
  native runtime/FFI boundaries, and actual placement; do not silently truncate
  an out-of-range address. This is a proposed representation constraint, not a
  claim that the current runtime already satisfies it.
- Emit immutable, fully preinitialized objects in read-only storage. Objects
  with mutable fields or remaining runtime initialization writes stay writable.
  Retain the current writable-class-storage correction until those writes have
  actually been eliminated.

Acceptance should cover scalar and pointer fields, shared/cyclic object
references, preserved effects in mixed initializers, and serialization
round-trips. A diamond of separately compiled users must share one class-object
address and observe each other's mutations. Check ELF definitions/relocations,
address-range enforcement, native execution, emulator execution, and the full
Chapter 25 suite, including `make -j 4 tests`.

## Larger or entangled changes: defer

| Group | Eventual home | Why not in the small queue yet |
|---|---|---|
| Conditional Store and array Load control | Memory/arrays chapters | Reproduce under the earlier alias model before extracting fixes from the new memory implementation. |
| SCCP dependencies, function revival, reachability | 24; some foundations may fit 18 | Separate old-IR corrections from new Guard/Escape/BulkMemPhi and external-caller machinery. |
| TypeScalar, numeric modes, guards, symbolic fields, open/forward types | Revisit earlier homes later | A connected incomplete-types architecture, including phase ordering and errors. |
| BulkMemPhi/MemPhi, private constructor memory, allocation helpers | Revisit memory / constructors / methods | Move invariants and regressions together. |
| Serialization, global identity remapping, module escape summaries | Separate compilation | Remain with modules. |

## Register allocation: completed chapter progression

The allocator review is complete through Chapter 25. Each README ends with the
introduced technique, measured program cohorts, and commentary. `make spill-stats`
reports actual retained moves, per target and cohort, with assertions enabled.

| Chapter | Additional technique | Final cohort sizes |
|---|---|---|
| 20 | Basic coloring, rematerialization and loop splitting, with shared legality/progress fixes | 39 |
| 21 | Conservative copy coalescing | 39, 52 |
| 22 | Stronger copy-chain/backedge bias and cheap-spill ordering | 39, 52, 24 |
| 23 | Group popular single-def uses by compatible register classes | 39, 52, 24, 30 |
| 24 | One cold-only attempt for loop-Phi self-conflicts, then mandatory fallback | 39, 52, 24, 30, 67 |
| 25 | Existing area/cost ranking and later conflict strategies | 39, 52, 24, 30, 67, 10 |

Shared corrections include RegMask/LRG bookkeeping, null use masks, kills without
an output LRG, self-conflict splitting, compatible rematerialization, clobber-aware
copy reuse/bypass, deterministic ordering, and side-effect-free diagnostics. The
final audit adds the missing narrow x86 masks in 20-21 and RISC-V mask in 21;
fixed-neighbor color bias in 20-25; and ARM memory register-bank selection,
float opcodes, and matching emulator fixes in 21-25.

Chapter 25's additional fixed-use/deep-side splitting thresholds remain there.
The def-use-count snapshot and null-use guard support its extra split branch;
20-24 do not have that branch. Earlier kill handling subtracts killed registers;
25 can choose to split cloneables earlier. No independent earlier-chapter
correctness failure was established for these strategy differences. They are
not uncompleted promises to copy the complete allocator backwards. Compilation-
unit ownership and void/metadata handling also stay with the later representation.

The final ARM failure was substantive: the original Newton float programs reached
the allocation round limit because a float store demanded a GPR; the deeper loop
split kept cutting the wrong portion of the range. Permitting the supported FP
registers removes that conflict. Encoding must then use the allocated register
bank, with five-bit register numbers, for both immediate and indexed loads/stores.
The emulator must preserve floating bits and scale double offsets by eight.

Chapter 25 freezes the 212 earlier compilation entries in
[documented source fixtures](../chapter25/src/test/java/com/seaofnodes/simple/spill/README.md).
Constructor/library adaptations change IR, so this is a program-cohort comparison,
not identical machine graphs. Those rows replay allocation and legality checks;
current Chapter25Test native checks and a fresh system-library encoding contribute
ten more allocations. Total: **2,597 moves / 5,579 loop-weighted moves** over 222
compilations. Chapter 25's area/cost implementation is retained. Substituting the
earlier ranking saves five moves on the clients but fails a fresh `sys` allocation
at the eight-round limit; a failed library cannot be omitted from the comparison.

The earlier tables are refreshed where the final mask fixes changed them:
Chapter 20 is 238 / 357; Chapter 21 is 840 / 1,456. Coalescing on/off now saves
230 / 433 moves in Chapter 21 with identical legality fixes. Chapters 22-24's
aggregate values are unchanged. Full details and comparison limits belong in
the individual READMEs rather than a second evolving set of tables here.

## Review and test protocol

1. Establish the destination baseline. Reproduce a real failure with a small
   source or constrained machine graph before classifying a change as a bug fix.
2. Apply the smallest correction, propagate it to all affected snapshots, and
   run each snapshot's focused checks and full `make tests`. Respect the current
   user-authorized review boundary; do not push without explicit instructions.
3. Force Java rebuilds when older Makefiles miss dependencies. Rebuild `sys.o`
   after compiler changes; include a fresh library compilation in allocator
   measurements rather than relying on an up-to-date object.
4. Keep legality/progress/runtime checks separate from spill expectations.
   Eight-round exhaustion is a failure; neither increasing the cutoff nor
   changing a golden proves it fixed. Preserve test membership, targets/ABIs,
   and fixed seeds. Do not sweep optimizer seeds for routine backend validation.
5. Report aggregate and local changes. Use same-compiler heuristic comparisons;
   changed frontend/lowering graphs are not allocator-only measurements.
6. Keep edited text LF-only, preserve unrelated changes, and run `git diff --check`.

The top-level runner accepts explicit chapter lists, e.g.
`make -k tests CHAPTERS="chapter20 chapter21"`. Tests in 25 alone are insufficient.

## Validation record

This is a condensed completion record, not a list of current suite counts for
old revisions. Earlier detailed traces are in Git history; durable invariants
have been consolidated into the [AI notes](../skills/chapter25-codex-notes.md).
Unresolved reproductions have been promoted to the pending queue above.

On 2026-09-28, the constructor/memory-check backport passed the full Make suites
in every compiler snapshot from 10 through 24, including their default fuzzer
wrappers (`build/error-backport-tests.log`). The destination baseline was also
green (`build/error-backport-baseline.log`). The arithmetic contract experiment
and its separate validation are recorded independently.

| Completed work | Scope and evidence retained |
|---|---|
| Narrow C integer returns | On 2026-09-28, instruction selection in 22-25 sign/zero-extends direct C call results according to the declared 8/16/32-bit return type. x86 uses MOVSX/MOVZX/MOVSXD or a 32-bit MOV; ARM uses SBFM/UBFM; RISC-V uses shifts, including zero-extension of its sign-extended u32 ABI result. Chapter 25 consults external function identities because optimization can replace ExternNode with an ordinary constant. Native regressions run both x86 calling conventions with garbage upper return bits; ARM/RISC-V stubs exercise signed/unsigned boundaries and unchanged i64 results. Chapter 25's errno test now also requires `close(-1)==-1`. Original selectors reproduce failures in 22 and 25, including the libc case (`build/c-return-negative-{22,25}.log`). Full Make suites passed: 22 (433), 23 (455), 24 (485), 25 (479), plus each default fuzzer wrapper; 25 rebuilt sys.o (`build/c-return-final2.log`). |
| ARM bitfield emulation | SBFM/UBFM decoding now handles both immediate fields, covering signed/unsigned extracts as well as ASR/LSR/LSL. The previous emulator treated all SBFM as ASR and all UBFM as LSL, hiding a logical-right-shift error and rejecting the new extension results. Introduced the correction and independent instruction-word regression in 21 and forwarded through 25. Full 21 suite passed (412 tests plus fuzzer; baseline 411); 22-25 validation is included above. |
| FunPtr lifetime and callable entries | On 2026-09-28, introduced Return-linked FunPtrNode in 22-24, including semantic constant folding. Direct call targets retain existing call-graph treatment; all other address uses conservatively retain unknown callers, without escape analysis. Stop retains each callable Return through instruction selection; machine operands omit the lifetime edge. Anonymous/library returns failed relocation on all three targets before the change; 24 also failed named returns. New tests call returned anonymous/named/library pointers on ARM/RISC-V and from native C, and check discarded anonymous bodies disappear. Full Make suites passed: 21 (411), 22 (430), 23 (452), 24 (482), each plus its fuzzer wrapper. Baselines passed at 410/427/449/479. Chapter 24's Phi-selected functions retain general parameter types; graph expectations reflect that conservative rule. |
| Function-address backend and worklist corrections | RISC-V TFPRISC now patches ADDI at opStart+4, rounds AUIPC for signed low bits, and reports its actual eight-byte size (21-24). ELF export emits anonymous bodies without exported names (21-24). Actual invocation exposed both bugs after retention was repaired; Chapter 21's reduced conditional-return probe trapped before the encoding fix. Chapter 22 gains the later inlining dominator null guard. In 24, killing Call#618 reduced FunPtr#540 to one use without waking dependent CallEnd#560; Node.kill now wakes the remaining definition's dependencies after removing a use (24-25). The no-return String workload loses unused hash bodies; its spill expectations become zero on all targets. Chapter 24 spill-stats passes all 212 original compilation entries at 1,351 moves / 3,115 weighted moves; its README separates this measurement from the original allocator ablation. Full Chapter 25 Make validation also passed: 476 ordinary tests plus its 17-seed fuzzer wrapper, with sys.o rebuilt. |
| Chapter 25 direct C data bindings | On 2026-09-28, full `make tests` passed after the Field/ExternOffset refactor: 476 ordinary tests plus the fuzzer wrapper (17 seeds). Coverage checks zero layout space for external fields, native shared integer/float storage across C calls, compound updates, source/precompiled imports sharing aliases and preserving storage metadata, ARM/RISC-V emulated address relocation and signed loads, and the errno accessor after a failed native call. Direct bindings remain in 25: 22-24 have different singleton-pointer constant and instruction-selection contracts. Serialization retains magic C0DE; the changed layout requires rebuilding all objects. Only the independent RISC-V fixes below were selected for backport. |
| RISC-V right shifts and pointer relocation | Backported from 25 to 22-24 on 2026-09-28. EvalRisc5 selects arithmetic/logical right shifts correctly; TMPRISC rounds the AUIPC high part to account for signed ADDI low bits. New regressions cover immediate/register shifts and forward/backward relocation boundaries. Both fail before the Chapter 22 fixes (arithmetic shift becomes logical; delta 0x800 lands 4 KB low). Full Make suites passed: 22 (427), 23 (449), 24 (479), each plus its fuzzer wrapper. Baseline suites also passed (425/447/477). |
| Chapter 25 Load fuzzer recheck | At `8212a66f`, seed `-4628356252269023530` passes its direct fuzzPeepsRegression replay (`build/load-seed-recheck.log`). The complete `make fuzzer` wrapper also passes all 17 seeds on 2026-09-28 (one JUnit wrapper test). The seed remains a regression; OPEN_FAILING_SEEDS is empty. No new compiler fix was needed. The older Load TODO report is superseded. |
| Arithmetic high results at the SCCP boundary | Chapter 24 integer and float arithmetic now return global TOP for high operands, matching 25. Widened ArithNode.compute's Java return signature to Type; no caller requires TypeInteger. Retained the typed integer fallback for unresolved/error operands, for the parser dependency in the pending queue. The TOP change starts with SCCP in 24; earlier snapshots retain their typed contracts. Full Chapter 24 Make suite passed: 477 tests plus its fuzzer wrapper (`build/arithmetic-top-full-24.log`). |
| Required-field and memory-check backport | Chapters 16-24 retain allocation-time checks for inline `new S { ... }` initialization. Chapter 17 had its TOP/unset-field check commented out; restored it. Expanded the existing missing-initializer regression through 24 (introduced its array-field cases in 16), and forwarded the known-null load regression to 10-24 using their existing parse-time diagnostic. Added the fresh-allocation zero-fold monotonicity assertion in 19-24 and the TOP-pointer store-diagnostic guard in 18-23 (already present in 24). Before 18, TOP pointers already produce a diagnostic before the store's pointer cast. Chapter 25's constructor-exit snapshots and implicit-constructor declaration check stay with its separate allocation/constructor model. Its CallEnd return and MemMerge load-dependency corrections already have equivalent behavior in the applicable earlier implementations; storage-type nullability machinery stays with 25. Global error-result type changes are paused in the queue above. |
| Chapter 25 required constructor fields | Parser records each explicit constructor's merged exit field types by Var identity and checks them against the complete field list when the struct closes. With no user constructor, declaration defaults are checked as an implicit empty constructor. Never-returning constructors have no completion obligation; nested blocks retain constructor context. Declaration-body assignments to required fields now emit their initializing stores. Load's fresh-allocation zero fold is restored with a monotonicity guard. Existing constructor tests cover partial/early returns, shadowing, later fields, valid defaults and non-returning constructors; older unused-struct/String fixtures now provide constructors, with measured spill expectations updated. Seed `6506797708065910879` is rejected during parsing and passes its regression. On 2026-09-28, all 472 non-fuzzer tests passed across `build/ctor-check-tests.log` and `build/ctor-remaining-tests.log`; the full Make run remains red solely on the independent Load TODO seed above. Allocation-time initialization checking was lost in the August 10 rewrite (`51f1f8f4`); this restores the obligation in the parser without depending on optimized private memory. |
| Chapter 25 null-dereference diagnostic | Existing `Chapter10Test.testNullGuardErrors` includes `if (p == null) return p.x; return -1;`. The earlier recursive constant-fold error guard was removed by Cliff in favor of erroneous nodes computing BOTTOM, with related eager-folding and monotonicity corrections. The earlier full-suite pass in `build/null-deref-arith-top.log` applies to the superseded guard implementation, not the current edits. The exposed uninitialized-field failure is fixed by the constructor checks recorded above. |
| Chapter 22 String without an explicit return | Backported 23's default-main teardown: when top-level control falls through, replace main's Start input with XCTRL and queue its users. Previously 22 omitted the return while retaining live control, leaving loop fragments with missing exits. Current seed 0 reproduced a missing-successor failure in Encoding; the earlier GCM changes had moved it past the originally recorded failure. The unchanged `Chapter20Test.testString` now also checks seed 0 through Encoding on x86/RISC-V/ARM, preserving its existing seed-123 allocator checks. Two Chapter09Test graph expectations now reflect removal of a default main with no live return; their sources are unchanged. Baseline and final full Chapter 22 suites passed (425 tests plus fuzzer), logged in `build/string-baseline.log` and `build/string-final.log`. Chapters 23-24 already contain this teardown; no later compiler changes were needed. |
| Infinite-loop evaluation | Eval2 now runs the missing loop-tree phase in 21 and explicitly follows NeverNode projection 0 in 21-25. Existing `testFcn9` expects timeout in 21-24. Full 18-25 Make suites passed on 2026-09-26 (`build/never-exit-tests.log` and `build/never-exit-final.log`). On 2026-09-27, Cliff requested removal of the frozen-source fixture, replay tests, related reductions, and seed-list entry as unnecessary test overhead; the evaluator fixes remain. |
| Always-on peepholes | Removed the global disable flag and branches in 2-18, the Chapter 18 parse overload, test toggles, and transpiler metadata/Rust output. Updated early graph expectations and READMEs; removed duplicate no-peephole scope tests. Chapter 8 fuzzing checks compilation/evaluation for failures; 9-18 compare worklist seeds 123/456 like later chapters. The Chapter 18 Load type workaround was unnecessary with normal forwarding and was removed. All 1-25 default Make suites passed across `build/always-peeps-tests.log` and `build/always-peeps-final.log`; later evaluator corrections are recorded above. The baseline was green in `build/always-peeps-baseline.log`. Direct checks exercised both worklist seeds in 9-18. The transpiler compiles and its focused grammar/Rust-output check passes; its existing repository-wide test stops at source-free chapter00 with `no source files`. |
| B10 and integer/constant-array widening | Widening starts in 24. Both 24-25 now use levels 0-3, dual widening `3-widen`, retained singleton widening, and widening on TypeConAry. `nonZero` preserves the coordinate. The same TypeTest regression and integer gather ranges run in both snapshots; before the backport, Chapter 24 fails the widening regression and lattice associativity. Loop Phis widen only below-center nonconstant ranges: widening an above-center range to level 1 then falling to zero at level 0 violated SCCP monotonicity in 24's existing `testCloneAnd` and `testInfinite`; both now pass, with the guard also in 25. |
| TypeConAry API backport and validation | 24-25 share the reduced raw `_make(any,widen)` API, integer meet dispatch through the distinct `TCONARY` tag, and gathered samples. Preserve 24's GLB hooks and ordinary byte stream; scalar envelopes and serialization stay in 25. Chapters 22-23 consolidate gather and remove redundant wrappers/arguments, retaining their pre-widening representations and needed sentinels. Full Make suites passed on 2026-09-26: 22 (428), 23 (450), 24 (480), 25 (474), each plus its fuzzer wrapper; all include TypeTest, and 25 rebuilt `sys.o`. Logs: `build/conary-backport-*` and `chapter25/build/conary-backport-25.log`. |
| #254 subtraction zero identities | Require integer operands in 4-25 and resolved integer mode in 25. Preserve floating-point signed zero and subtraction diagnostics. `Chapter12Test.testSubZeroFloat` runs in 12-25; `Chapter22Test.testSubZeroTypeError` runs in 22-25. Against the original PR, the signed-zero test fails in 25 and the diagnostic test fails in 22-25. Full Make suites passed in every chapter 4-25 on 2026-09-25, including fresh `sys.o` compilation in 25. |
| Short type extrema | Memory, struct and memory-pointer BOT/TOP names from 10; function-pointer names from 18. Exact types use shortcuts in ordinary, nested and HTML prints; precise types retain their details. Full Make suites in 10-20 and type/frontend suites in 21-25 passed; canonical/nested print checks passed in 10-25. |
| Integer result types | Integer-op fallbacks in 4-13 and remaining unary cases through 17 retain integer types; 5's Phis meet their data-input types. Full Make suites in 4-17 passed, plus a chapter 5 snapshot check of Phi/Add types. Integer BOT starts in 4; later arithmetic already has typed fallbacks, with unresolved bimorphic modes preserved in 25. |
| B14 cyclic leaf-kind equality | 23-24 backport; 25 already correct. Cyclic equality starts in 23. `TypeTest.testCyclicLeafKinds` fails before the fix; earlier interned-child equality already checks kinds. Full affected suites passed. |
| B13 / #246 null guards | Nested Not/null guard discovery from 10; short-circuit Phi availability in 23-24. Regression and full suites in 10-25 passed. Separate Chapter 25 null-dereference diagnostic is recorded above. |
| B09 and #251 debug printing | Side-effect-free print/accessor paths from their first applicable chapters; `_` diagnostic naming, type interning allowed, layouts forbidden. Dead nodes use `isDead()`, not null `_inputs`, from 7. Original-printer negative checks and full affected suites passed. |
| #247 emulator stores | Full eight-byte writes in ARM/RISC-V emulators, 21-25. Independent byte-pattern/overwrite regressions failed before the fix; all affected suites passed. |
| B12 x86 immediate multiply | Dedicated destination-register encoder in 21; 22-25 already had it. Low/extended-register encoding regressions and full 21-25 suites passed. |
| B11 optimized return checking | Remove parse-time meet in 18-24; 18 checks the optimized result type. Earlier separate Returns already accept dead mixed-type exits. Regressions start in 10/12; full 10-25 suites passed. |
| Dominator caches | Shared searches from 6; char depth in 6-17, separate char depth/version from 18, checked overflow and preserved clone fields. Inlining invalidation in 18-20. Full affected suites passed; dedicated bookkeeping helpers/tests were removed at Cliff's request. |
| GCM, constant cloning, isPinned removal | Shared scheduling from 11, per-function cloning of stacked constants from 18; preserve chapter-specific anti-dependencies and linked Parm ownership. Full 11-25 suites passed. Keep clone-based copyEmpty and non-final graph fields per review. |
| TypeFunPtr normalization | Trailing-default normalization in 23-24; 25 already correct. Only gather cases added; lattice laws and full 23-25 suites passed. Other `7f3b8856` representations remain in 25. |
| Inlining Return types and ARM execution | Live return expressions outlast a deleted inline entry (20-25); ARM register SUB, call instructions, frame byte counts/alignment, and vector growth (21-25 as applicable). Reduced negative cases and full affected suites passed. The earlier standalone ARM String/hash failure is resolved. |
| Native test reliability | Full process exit status, stdout plus successful execution, and distinct concurrent HelloWorld artifact names. Crashes no longer pass merely because output matches or an exit status narrows to zero. |
| Allocator progression 20-25 | Fixed cohorts, legality checks, native/emulated execution from 21, and same-compiler comparisons. Final results below. |
| Build/fuzzer harness baseline | All 25 chapter targets passed after build setup and explicit seed lists. Empty default lists were not exploratory fuzz coverage. |

Final allocator audit, 2026-09-20, Windows/Cygwin with assertions:

| Snapshot | Full suite | Spill report |
|---|---|---|
| 20 | 378 tests + fuzzer | 39 compilations, 238 / 357 |
| 21 | 408 tests + fuzzer | 91 compilations, 840 / 1,456 |
| 22 | 422 tests + fuzzer | 115 compilations, 838 / 1,496 |
| 23 | 444 tests + fuzzer | 145 compilations, 933 / 1,745 |
| 24 | 473 tests + fuzzer | 212 compilations, 1,332 / 2,956 |
| 25 | 468 tests across parallel groups, including fuzzer | 222 compilations, 2,597 / 5,579 |

All full suites and final spill reports passed. The Chapter 25 run rebuilt
`sys.o`. Negative checks against saved original classes reproduce fixed-neighbor
bias, narrow-store masks, and ARM floating-memory encoding failures. The original
Newton ARM allocation also failed before the fix. The Chapter 21 coalescing-off
comparison passes all allocation/runtime checks but reports nine changed spill
goldens, intentionally exiting nonzero. The Chapter 25 earlier-ranking ablation
fails fresh library allocation, so its client-only totals are explicitly partial.
Logs and saved original classes: `chapter{20..25}/build/regalloc-final/`.
