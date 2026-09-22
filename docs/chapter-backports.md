# Chapter backport review

This is a queue of pending work. Completed corrections leave the queue; the
[validation summary](#validation-record) records their scope. Detailed old logs
are disposable build artifacts. Reusable rules live in
[the AI notes](../skills/chapter25-codex-notes.md).

Introduce independent fixes in the earliest applicable chapter and propagate
them through every affected snapshot. Testing Chapter 25's inherited tests does
not validate the compilers in the earlier chapter directories. Keep building
SSA with incomplete types in Chapter 25 for now; moving that architecture or
renumbering chapters is deferred.

## Pending corrections

- **B10: preserve integer widening in `nonZero`, Chapter 24.** Still missing:
  `[0,255]` and `[-255,0]` with widening levels 1-3 become level zero. Chapter 25
  passes `_widen` to `make`; 24 does not. Widening first appears in 24, so 15-23's
  older `nonZero` needs no patch. The previous audit established the type-level
  discrepancy, not a loop-failure reproducer. Source: `51f1f8f4`.
- **FunPtrNode lifetime backport, now next after the allocator review.**
  `return {->42;};` and `return sys.io.p;` fail during relocation in Chapter 22
  on all three targets at seed 123. Opto's name-based pruning equates no linked
  calls with no callers and deletes anonymous/library function bodies. A plain
  function-address ConstantNode keeps the pointer, but has no edge to keep the
  Return alive. Instruction selection resets NIDs; the linker retains an ideal
  target and `patchLocalRelocations` indexes outside the machine graph.

  Backport Chapter 25's FunPtrNode/Return retention rules together with pointer
  creation, unknown-caller pruning, instruction selection, constant cloning, and
  relocation. Determine the earliest applicable home; 21 lacks this pruning
  pass. `val f={->42;}; return f;` surviving is not a fix for anonymous/library
  addresses. Do not enlarge relocation arrays or add an interim address scan.
  Validate returned pointers by calling them, including library pointers, while
  still deleting genuinely unused helpers. Keep module/escape machinery in 25.
- **Chapter 22 String without an explicit return.** The unchanged source in
  `Chapter20Test.testString` fails before allocation in loop-tree construction
  or GCM at the historically tested seeds 0-29, on all targets. The original
  snapshot has the same failures; seed 123 folds the work away. Reduce default
  return/dead-call handling. A new return or a different seed is not a fix;
  this historical sweep is not a reason to sweep routine backend tests.
- **Chapter 25 null-dereference diagnostic.** This setup was accepted despite
  dereferencing null on the taken arm; investigate separately from B13:

  ```java
  struct Point { int x; new Point={int v -> x=v;}; };
  Point?[] !points=new Point?[2]; points[arg]=new Point(42);
  Point? p=points[1];
  if(p==null) return p.x; return -1;
  ```

- **Chapter 18 fuzzer seed `973358943756616234`.** Remains in
  `OPEN_FAILING_SEEDS`. The reduction below exposes nullable field lookup with
  peepholes disabled and, historically, a mixed-return error with them enabled.
  B11 fixes the latter reduced diagnostic; neither it nor Chapter 19's change
  from an off/on oracle to worklist-seed comparison proves the full seed fixed.

  ```java
  struct s0 { u8 v1; };
  s0? !UmPOLQK=null;
  if(0) while(0&UmPOLQK.v1) {}
  if(UmPOLQK.v1) {}
  return new s0;
  while(0) {}
  ```

- **Chapter 25 ARM extern data.** `i32 errno="C"; return 0;`, compiled through
  Encoding with ARM/SystemV, reaches `arm.load_str_imm` with register -1 for the
  extern-data value stored by `<clinit>`. Both the saved original and final
  compiler reproduce it. This is an extern-data lowering/encoding issue, not
  the repaired float-store allocation conflict. The historical Bubble Sort
  replay allocates successfully but also exposes this failure if ARM encoding
  is requested. Do not describe its allocation statistics as runtime coverage.

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

| Completed work | Scope and evidence retained |
|---|---|
| Integer result types | Integer-op fallbacks in 4-13 and remaining unary cases through 17 retain integer types; 5's Phis meet their data-input types. Full Make suites in 4-17 passed, plus a chapter 5 snapshot check of Phi/Add types. Integer BOT starts in 4; later arithmetic already has typed fallbacks, with unresolved bimorphic modes preserved in 25. |
| B14 cyclic leaf-kind equality | 23-24 backport; 25 already correct. Cyclic equality starts in 23. `TypeTest.testCyclicLeafKinds` fails before the fix; earlier interned-child equality already checks kinds. Full affected suites passed. |
| B13 / #246 null guards | Nested Not/null guard discovery from 10; short-circuit Phi availability in 23-24. Regression and full suites in 10-25 passed. Separate null-dereference diagnostic remains queued. |
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
| Build/fuzzer harness baseline | All 25 chapter targets passed after build setup and explicit seed lists. Empty default lists were not exploratory fuzz coverage; the Chapter 18 open seed remains queued. |

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
