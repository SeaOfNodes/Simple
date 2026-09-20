# Chapter backport review

This is a queue of pending work. Remove completed corrections from the queue. Independent
corrections should start in the earliest affected chapter and propagate through
every later affected snapshot. A regression in Chapter 25's `Chapter21Test`
does not test the compiler in the `chapter21` directory.

For now, keep building SSA with incomplete types in Chapter 25. Moving that
architecture earlier, or splitting Chapter 25, is deferred while small changes
establish the review workflow. No renumbering is committed.

Keep each correction's reduced failure, smallest patch, and test results together
for review.

## AOT class initialization: larger independent work

Proposed on 2026-09-19; deferred behind the smaller BXX cleanup above. This is
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
| Register allocation and spilling | 20 onward | Chapter 20 correction/support pass complete locally; review before Chapter 21. Staged quality work remains below. |
| Conditional Store and array Load control | Memory/arrays chapters | Reproduce under the earlier alias model before extracting fixes from the new memory implementation. |
| SCCP dependencies, function revival, reachability | 24; some foundations may fit 18 | Separate old-IR corrections from new Guard/Escape/BulkMemPhi and external-caller machinery. |
| TypeScalar, numeric modes, guards, symbolic fields, open/forward types | Revisit earlier homes later | A connected incomplete-types architecture, including phase ordering and errors. |
| BulkMemPhi/MemPhi, private constructor memory, allocation helpers | Revisit memory / constructors / methods | Move invariants and regressions together. |
| Serialization, global identity remapping, module escape summaries | Separate compilation | Remain with modules. |

## Register allocation: correctness first, staged improvements

Review on 2026-09-20. The staged plan follows; Chapter 20
has since been implemented and validated as recorded below. Later snapshots
remain pending. Compared RegAlloc, BuildLRG, IFG,
LRG, Coalesce, RegMask, and split support across 20-25, with the original fix
commits. Allocation starts in 20; 19 has instruction selection/register masks
but no coloring allocator. Most changes are in 20->21 and 24->25. The 22->23
allocator changes are imports/API cleanup; 23->24 has no allocator changes.

Cliff's direction: correctness fixes belong at the earliest applicable chapter;
quality improvements should accumulate gradually. Debugging/printing/support
can start in 20. Judge spill changes across a fixed suite, allowing a local
increase when compensated elsewhere. Do not copy the entire Chapter 25 allocator
into 20, and do not classify a whole historical commit by its title.

### Correctness work to extract

| Work | Current evidence / source | Earliest intended home |
|---|---|---|
| Register-mask and LRG bookkeeping | Original 20: `RegMask(int)` assigns `bit=64` instead of subtracting 64, and empty `firstReg()` returns 128. `LRG._union` replaces `_machUse` without its `_uidx`, and can retain null from an immutable mask intersection. Fixed in 21; see `7e1deb4c`, `8d6de0e1`. | 20 |
| BuildLRG/IFG instruction contracts | 21 checks null use masks, avoids revisiting CFG projections, processes kills even without an output LRG, and distinguishes an instruction's fixed output from a range narrowed elsewhere. The 25 commutative-input fallback uses `mach.outregmap()` rather than a possibly nonexistent LRG. | 20, after reduced graph/source regressions |
| Self-conflicts and effective splits | 21 visits extending uses before rewriting definitions, permits needed backedge splits, checks intervening clobbers/self-conflicts before reusing splits, and avoids immediately folding away capacity splits. `35057d50`, `cc32b0cf`, and `880329e6` explicitly address failed progress. | 20; preserve Phi parallel-assignment semantics |
| Legal rematerialization | 22 checks a clone's output mask against the use mask before choosing a clone over a move. Otherwise repeated cloning can preserve the original hard conflict. The 25 kill handling chooses the live cloneable value to split rather than the killing instruction (`c05d7df4`). | 20; isolate correctness from ranking cloneable spill candidates |
| Empty-mask split bookkeeping | 25 saves the original def use-count before inserting a split, checks for a missing sample use, and skips null traversal roots. | 20 where the affected helper/path exists |
| Persistent multi-def/fixed-register conflicts | 25 directly splits single-register uses and sometimes splits the deep side of a loop (`c05d7df4`). Its `_ns._len > 5` and related thresholds mix progress with tuning. | Reduce a no-progress case in 20, then extract the smallest sufficient rule; do not transplant thresholds as a proven invariant |
| Encoding and stack ABI support | Correct frame sizing, incoming/outgoing stack arguments, and stack-to-stack move encoding are required once native emission exists (`7e1deb4c`). | 21, where encoding is introduced; retain CPU/ABI distinctions |

Each correctness packet needs a failure demonstrated against the destination's
old implementation, then allocation completion and legal register use. From
21, also execute native/emulated results. An eight-round cutoff failure is a
correctness failure, not permission to increase the cutoff. Some entries above
are established historical fixes; applicability of the larger 25 changes to
older graphs still needs reproduction.

### Proposed quality progression

| Chapter | Additional quality technique |
|---|---|
| 20 | Keep the existing basic coloring, biased coloring, rematerialization, and loop-boundary splitting, corrected for legality/progress. |
| 21 | Conservative copy coalescing, already introduced here. Keep its mask/adjacency correctness fixes with it. |
| 22 | Stronger color preferences: follow loop backedges for bias, improve copy-chain searches, and refine cheap-spill ordering for callee saves/cloneables. |
| 23 | Group popular single-def values by their uses' required register classes instead of splitting each use. Include safe use-list mutation and call-crossing restrictions when introduced. |
| 24 | Try cold splits first for loop-Phi self-conflicts, with a remembered one-attempt limit and aggressive fallback. The delay is optional; its fallback is mandatory. |
| 25 | Rank recovered live-range area against loop-scaled split cost (`a03bc567`), plus any further measured tuning. |

This progression would move some heuristics currently bundled into 21 later;
it is not just a forward copy. Keep each introduced technique in subsequent
chapters and update the chapter prose with it. Chapter 20's README already
discusses popular-value grouping and coalescing beyond its current code, so
that prose needs realignment too. The one-attempt loop-Phi delay from
`a03bc567` is a quality feature with its own progress safeguard, not a reason
to add persistent deferral state to the first allocator chapter.

### Support and measurement

- Start deterministic split ordering, null-safe split printing, useful LRG/mask
  diagnostics, and an optional function-local-edge verifier in 20. Preserve the
  existing side-effect-free `_` printer accessors. Actual stack offsets require
  the frame layout introduced in 21; module-specific exceptions stay in 25.
- Record actual `_spills` and `_spillScaled` for each compilation, keyed by test,
  CPU, ABI, and fixed seed. Both count retained SplitNodes; `_spillScaled` weights
  each by `8^loopDepth`. These are move/split metrics, not solely memory traffic.
- Compare total scaled counts as the existing quality measure, and report raw
  totals plus the largest local changes. Report per CPU/ABI as well as the whole
  suite so a regression on one target is visible. Include compilations without
  a current golden spill assertion; do not exclude failures or disable checks
  to obtain an aggregate. Freeze test membership and seeds for each comparison.
- Compare before/after within each chapter. For tutorial progression, also use
  the common program/target subset: totals from different suites or changed IR
  are not a controlled allocator comparison.
- Existing helpers assert individual spill goldens (often with tolerance).
  Review those local changes against the measured aggregate before updating
  expectations. Correctness tests must assert completion/results independently
  of the heuristic's exact spill count.
- `splitBypass` originally scanned from `j-1` with `idx++` throughout 20-25,
  reaching its own destination and rejecting nonadjacent bypasses. Corrected
  in 20 with intervening kill-mask checks and a regression; 21-25 remain pending.
  Pre-color copy reuse also checks fixed-register definitions before they have
  an assigned `_reg`. Chapter 25 is not a complete correctness reference.

Suggested execution order: support/measurement, small mask/LRG correctness
fixes, constrained-register and self-conflict regressions/fixes, then one
quality technique at a time. Run each affected snapshot's full suite at each
accepted boundary. Chapter 20 has now been implemented; do not proceed to 21 until Cliff reviews it.
Review each chapter's README along the way. Chapter 21's encoding discussion
should be shortened when that chapter is reached. End each README with its
RegAlloc improvement, measured cohort table, and commentary; the Chapter 25
table must have rows for cohorts 20-25 using the Chapter 25 compiler.

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

### Chapter 20 allocator: ready for review, 2026-09-20

Implemented only in 20, per Cliff's chapter-by-chapter review gate. Corrected
high-word/empty register masks and boundary iteration, LRG mask merging/use-slot
bookkeeping, null register-mask dependencies, commutative outputs used by Phis,
CFG projection visitation, and kills from instructions without output LRGs.
Splits now preserve progress through self-conflicts/backedges and intervening
clobbers. Rematerialization must satisfy the use mask; fixed-register clones
with flexible uses go through use-side splitting instead of a no-op simple
split. Copy cleanup scans backward and respects both fixed definitions and kill
masks. Removed the unused `splitEmptyMask` path and its `_killed` bookkeeping.

Support includes deterministic failed-range ordering, null-safe split printing,
a function-local-edge diagnostic, and `make spill-stats`. The program tests also
check assigned register masks, two-address constraints, and Phi agreement.
Seven small machine-graph/mask regressions fail against the saved original
implementation and pass after correction, including a round-limit failure for
incompatible clone/use register classes. These are separate from the quality
corpus; they do not supply extra zero-spill examples to improve a total.

The complete program cohort includes Chapter20Test's 11 examples plus BrainFuck
and MergeSort on all three SystemV targets: 39 compilations, default seed 123.
The fresh original baseline passes 368 tests plus the fuzzer regression. After
forced rebuilds, `make -j 4 tests` passes 375 plus 1. The 11 ordinary examples
also pass 990 compilations across seeds 0-29 and three CPUs, with the current
register/ownership checks enabled. Native execution begins in Chapter 21.

| SystemV target | Compilations | Original splits | Corrected splits | Original weighted | Corrected weighted |
|---|---:|---:|---:|---:|---:|
| x86-64 | 13 | 80 | 82 | 150 | 152 |
| RISC-V | 13 | 82 | 80 | 103 | 101 |
| ARM | 13 | 73 | 74 | 101 | 102 |
| Total | 39 | 235 | 236 | 354 | 355 |

This is a one-move correctness cost, not a quality win. Adjusted three local
weighted goldens after reviewing the full aggregate: x86 array 3->5, x86 String
18->21, ARM String 3->5 (the original actual ARM count was 2 within tolerance).
All assertions remain active. The stats runner emits per-compilation data and
fails on any JUnit failure; the Chapter20Test collector allows all three target
checks to run before reporting golden differences. The README preserves Cliff's
new introduction, removes premature coalescing/popular-use-grouping claims,
and ends with the measured baseline and discussion of statistical comparison.
No advanced coalescing, color-bias, grouping, cold-first, or area/cost heuristic
was imported. Logs and the original-code snapshots are under
`chapter20/build/regalloc-review/`. Chapters 21-25 are unchanged.

### TypeFunPtr normalization: complete locally, 2026-09-20

Backported the trailing-default normalization from `7f3b8856` to 23-24, where
open/closed function argument tails first appear. Their complete `make` factory
trims trailing BOTTOM arguments for open signatures and TOP arguments for closed
signatures before interning; the existing meet uses that factory too. Raw cyclic
allocation stays unchanged. Earlier chapters use fixed TypeTuple signatures.
Chapter 25 already normalizes with scalar BOT/TOP and retains its implementation.

Added only `gather` cases in 23-25: open/closed signatures, repeated trailing
defaults, all-default signatures, and defaults before a real argument. No new
test methods or files. The expanded existing lattice-law tests also passed
before the 23-24 fix; these cases extend coverage rather than prove an old law
failure. The other changes in `7f3b8856` concern Chapter 25's TypeMem final flags
and XInt sets, already fixed there; neither representation exists earlier.

Fresh baselines and forced-rebuild validation passed `make -j 4 tests` in
23 (416 + 1), 24 (439 + 1), and 25 (449 total), with assertions enabled.
Focused TypeTest runs also passed all six tests in each chapter. Edited files
are LF-only and `git diff --check` passes. Logs:
`chapter25/build/tfp-normalization/{baseline,repro,validate}.log`.

### Scheduling without isPinned: complete locally, 2026-09-20

Removed `isPinned()` and all overrides in 11-25. Early scheduling walks inputs,
then assigns a block only when input 0 is null. Existing control and Phi/Proj
bindings stay intact. For ordinary values, that control is an earliest-placement
bound, not a prohibition on sinking during late scheduling.

Chapters 11-14 also used the predicate for late placement; GCM now preserves
those cases explicitly (Proj, New, Parser.ZERO, and Cast from 13), alongside its
existing CFG/Phi handling. From 15, late scheduling already handles its fixed
CFG/Phi/Proj cases structurally.

The preceding GCM suites supplied the passing baseline. After forced Java
rebuilds, every full snapshot suite in 11-25 passed `make -j 4 tests`, with
assertions enabled and unchanged expectations/counts (449 total in 25). Existing
constant-chain, guard, loop, allocation, native, and emulator tests all pass.
No remaining `isPinned` declaration or call exists in the chapter sources.
Edited files retain LF endings; `git diff --check` passes.
Logs: `chapter25/build/pinned-review/{chapter25,backports}.log`.

### GCM and global constant cloning: unified locally, 2026-09-20

GCM starts in 11; functions make constant ownership relevant in 18. Chapters
11-25 now share the early definitions-first / late uses-first worklist strategy,
including Region/Loop Phi discovery, waiting-load wakeups, and LCA over every
matching Phi input. Anti-dependence marks moved from CFGNode into a temporary
GCM array. Removed the obsolete fixed-register placement heuristic in 21-24.

Chapters 18-25 clone entire global constant-building graphs with one identity
map per function, reusing shared subgraphs within that function. Originals stay
intact until all rewrites finish. `Node.copyEmpty()` supplies exact-class clones
with fresh IDs and empty edges, avoiding incompatible machine `copy()` contracts.
Removed 25's redundant instruction-selection pinning to the old ideal Start.

Necessary chapter differences remain explicit: 18-19's linked Parm inputs
belong to their callers; from 20, unlinked Parms belong to the callee. In 11-20,
the evaluator independently reschedules nodes, so anti-dependencies retain the
full store placement range. Trying the later single-block rule fails the existing
`SchedulerTest.testStoreInIf2`. Alias/tuple memory representations stay local to
their chapters; 25 retains its compilation-unit ownership check.

The new Chapter18Test regression in every snapshot 18-25 exercises two surviving
recursive functions, repeated uses, registered edges, and function-local chains:
stacked Cast/Constant nodes in 18-20 and RISC LUI/AddI expansions in 21-25. It
fails against isolated original GCM classes in 18 and 20, and passes afterward.
Original 21 and 25 already pass the machine-chain case; those changes simplify
and unify the implementation rather than correcting that particular case.

Changed ordering exposed 21's existing empty-block layout bug: entering a nested
loop was mistaken for a backedge, producing a bad native Sieve branch. Backported
22's same-loop-tree check; native and emulated Sieve pass. Updated 21's tighter
scaled spill expectations: stringHash RISC 5->3 and ARM 6->3; BrainFuck RISC
44->28. BrainFuck executes on both emulators; a separate direct-entry RISC
hashCode probe verifies both initial hashing and the cached result.

Validation: unmodified full baselines passed in 11-25. After forced Java rebuilds,
all full chapter targets pass with assertions and `make -j 4 tests`: ordinary
counts in 11-24 are 166, 169, 184, 204, 215, 233, 285, 316, 358, 368, 385, 397,
416, and 439, plus the separately invoked fuzzer wrappers where applicable.
Chapter 25 passes 449 tests across its groups. All edited files use LF endings.
Logs: `chapter25/build/gcm-review/{baseline,before-test,focus-all,final,final25}.log`;
`final.log` covers successful 11-24 runs and `final25.log` the final 25 rebuild/run.

Independent follow-up: a direct-entry ARM emulator probe of 21's exported
`hashCode` traps with code 3 under both original and updated GCM. It uses the
existing stringHash.smp library, a String containing the u8 array "test", and a
zero cached hash; the cause is not diagnosed. Do not claim the green suite or
lower spill count validates this extra case. Scratch reproducer: `StringProof.java`
in the same review directory; the RISC equivalent returns/caches 3556498.

### Dominator caches: unified locally, 2026-09-19

Real dominator searches begin in 6. The original Region pointer cache in 6-9
returned the old, still-live dominator after its predecessors were rewired.
Regions now recompute through the shared depth-based `domLCA` walk in 6-25,
including one, two, or more predecessors. Existing dependency direction and
24-25's dead-predecessor filtering are preserved.

Per review, 6-17 use a char depth with no version. Chapters 18-25 use separate
char depth/version fields, checked before depth narrowing and global version
increments. Added the invalidation hook to 18-20's inlining; 21-25 already had
it. Region, Loop and Stop all validate their caches, and folding functions use
their caller-side depth. CFG copy constructors preserve both fields, as do
ordinary clones. Packed versions in 21-24 failed after 100 invalidations;
25's packed depth overflowed at 2148. Both limits are now 65535, with explicit
overflow assertions.

Development checks covered rewiring, depth overflow, actual inlining with warmed
caches, repeated invalidation, copy preservation, and version overflow. At
Cliff's request, the dedicated dominator helpers/tests and their Makefile
accommodations were subsequently removed as unnecessary for this bookkeeping.
Existing printer regressions still check that both cache fields stay unchanged.

Baseline full targets passed in 6-24. After full Java rebuilds, all affected
snapshot suites passed with assertions enabled. Chapter 25 initially passed
`make tests` and `make -j 4 tests` in a scratch copy with a pre-existing stray
`v` removed from Node.java. After Cliff authorized removing that typo from the
live tree, its Java sources and system object rebuilt and `make -j 4 tests`
passed there too (451 tests, exit status zero).

Logs: `chapter25/build/dominator-review/{baseline,fixed,later,final18-24,chapter25-scratch,chapter25-final,chapter25-live}.log`.
Reusable cache rules are in the skills notes.

### B11: optimized return-type checking backported locally, 2026-09-19

The false mixed-return rejection begins in Chapter 18, where function returns
first merge through one Return/return-value Phi. `ReturnNode.err()` now checks
`expr()._type`, as in Chapter 19. The parse-time `mt` meet could retain a type
from an unreachable return forever. Removed that aggregate from 18-24; it was
already unused in 19-24 and absent in 25. Parsed kind flags remain only for
formatting diagnostics. Chapter 24's additional TOP check is preserved.

Earlier snapshots were checked with reduced syntax. Struct/reference returns
appear in 10; floating-point returns in 12. Chapters 10-17 already accept the
dead reference/int examples, and 12-17 accept the dead float/int examples.
They retain separate Return nodes and do not require one common type across
reachable returns. The B11 false rejection therefore has no earlier compiler
patch; introducing common-return-type checking there would be separate work.

`Chapter10Test.testDeadReferenceReturn` starts in 10 and is copied through 25;
`Chapter12Test.testDeadNumericReturns` starts in 12 and is copied through 25.
They cover both constant If arms and unreachable returns after an unconditional
return; numeric cases also verify execution results. Both tests failed in 18
before the correction with `No common type amongst ...`.
`Chapter18Test.testReachableMixedReturns` in 18-25 still rejects reachable
int/float and int/reference alternatives, using each chapter's diagnostic.

Unmodified baseline targets passed in 9-25. Final full `make tests` targets
passed with assertions enabled in every changed snapshot, 10-25; Chapter 25
passed 448 tests. Its reference test selects the entry function explicitly,
because constructors add other Returns to the same compilation unit.
Logs: `chapter25/build/b11-review/{probe,probe-later,baseline,red,fixed,chapter25-fixed}.log`.

### B12: immediate multiply destination corrected locally, 2026-09-19

The x86 backend has distinct integer register (`MulX86`, 0F AF), integer
immediate (`MulIX86`, 69/6B), and floating-point (`MulFX86`, MULSD) encoders.
Chapter 21 incorrectly routed immediate multiply through `ImmX86`, which
encodes an opcode extension in ModRM.reg. IMUL needs the destination there,
including its high bit in REX.R. Chapter 20 has no executable encoder yet;
22-25 already have a dedicated immediate multiply encoder.

Chapter 21 now uses that dedicated encoder while preserving its
`twoAddress() == 1` allocation contract. Other `ImmX86` subclasses are unchanged.
For `rcx *= 11`, the regression failed with ModRM C1 instead of C9; the fix emits
`48 6b c9 0b`. For `r9 *= 11`, it emits `4d 6b c9 0b`.

`Chapter21Test.testX86MultiplySameRegister` is carried through 21-25. It forces
rax/rcx/r9/r15 and positive/negative imm8/imm32 values, independently of allocator
choices, and checks register fields, opcode, immediate, length, and each
chapter's allocation contract. Existing distinct-register checks remain in
22-24. Unmodified baselines and final full `make tests` targets passed in all
five snapshots, with assertions enabled; Chapter 25 passed 446 tests.
Logs: `chapter25/build/b12-review/{baseline,red,fixed}.log`.

### Issue #251: dead-node printing corrected locally, 2026-09-19

[Issue #251](https://github.com/SeaOfNodes/Simple/issues/251) identifies dead-node
checks against a null `_inputs` array. Constructors and clones allocate the
array; `kill()` empties it without setting it to null. Line printers now use
`isDead()` throughout 7-25, including both columnar and LLVM formats in 10-17.
Chapter 25's whole-program printer uses the same predicate to omit dead
functions and nodes. Its bounds-safe input accessor simply checks the input
count. Printing does not prune the linker.

`Chapter07Test.testDeadNodePrinting` in every snapshot 7-25 checks retained input
storage, the `DEAD` marker, and live inputless nodes. Chapter 25 also tests
omitting a dead function while preserving its linker entry. The regressions
fail against isolated original printers in 7, 10, 18, and 25. Full `make tests`
passed with assertions enabled in every affected snapshot, including 447 tests
in 25. The initial Chapter 17 test assumed an inputless Start; its setup was
corrected for that chapter's representation and its full target rerun.
Logs: `chapter25/build/issue251-review/{original,fixed,chapter17-fixed}.log`.

### Issue #247: emulator 64-bit stores corrected locally, 2026-09-19

[Issue #247](https://github.com/SeaOfNodes/Simple/issues/247) identifies `st8`
writing the upper half with `st2`. The identical bug exists in EvalRisc5 and
EvalArm64 in every snapshot 21-25, starting with their introduction. Both now
use `st4` for the upper half, writing all eight bytes rather than leaving the
upper two unchanged.

`Chapter21Test.testRisc64BitStore` and `testArm64BitStore` are carried through
21-25. They check individual little-endian bytes independently of the load
helper, round trips, positive/negative/extreme values, overwriting with zero,
and untouched neighboring memory. All ten cases failed before the correction
at the first unwritten byte (0xA5 instead of 0x23).

Full `make tests` passed with assertions enabled in each snapshot 21-25,
including 445 tests in Chapter 25. Log:
`chapter25/build/issue247-review/fixed.log`.

### B10 audit: still pending in Chapter 24, 2026-09-19

The fix is present in Chapter 25 (introduced with commit `51f1f8f4`), but has
not reached Chapter 24. Integer `nonZero` appears in 15; `_widen` first appears
in 24, so earlier snapshots do not need this backport.

An assertions-enabled probe of the current compiled snapshots tested `[0,255]`
and `[-255,0]` at widening levels 0-3. Chapter 24 resets all six nonzero widening
levels to zero; Chapter 25 preserves all of them. Both pass refinement (`isa`),
idempotence, double-dual, singleton normalization, and zero-input checks. This
establishes the missing type-level backport; it is not a loop-failure reproducer.
Probe: `chapter25/build/b10-review/B10Probe.java`. No compiler correction was
made during this audit; B10 remains in the pending queue above.

### B09: complete locally, 2026-09-19

Audited `Node.p(depth)`, recursive printing, labels, scope/graph viewers, and
assembly helpers. Corrections follow the first affected representation:

- 11-24: scheduled IR display uses local `_idepth` state; 11-14 also use
  identity maps to avoid setting Node hashes/GVN locks.
- 18-24: memory display reads raw lazy aliases instead of creating Phis;
  scope display uses `Var._type` without resolving forward references.
  25's stale alias-based memory display reads its actual bulk-memory input.
- 19-23: printers use ordinary `link` and tolerate missing targets. Per Cliff's
  review, type interning is allowed: the proposed non-interning linker scan and
  `sameTarget` helper were removed. 24-25 use `CodeGen._link` to preserve dead
  entries, including through 25's `funcName`; optimizing `link` still cleans up.
- 20-25: register display uses `_lrg` without compressing chains or rewriting
  node mappings. Function predicates use the existing leaf `_isConstant` from
  23; integer size/value accessors also avoid entering shared recursion state.
- 21-25: pool display uses identity bookkeeping and recorded alignment, plus
  recorded struct size from 22 and section choice in 25. Printers do not ask
  Types for layouts, even when an answer might already be cached. Layout is
  separate from type identity and may eventually move out of TypeStruct.

Regressions cover graph caches/edges, lazy Phis, unresolved declarations, stale
linker entries, register chains, leaf type accessors, and forbidden layout queries.
Isolated original Chapter 18 classes fail the lazy-memory and forward-reference
tests; original printers also reproduce cache mutation, register compression,
shared-scratch assertions, and dead-linker pruning. Tests permit type interning.

Every affected compiler snapshot (11-25) passed its full `make tests` target with
assertions enabled after the review adjustment, including 443 tests in 25.
The constant-pool regression rejects size/alignment queries even if they would
return cached answers. Log: `chapter25/build/b09-review/revised.log`.
Durable rules are in the skills notes.

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
