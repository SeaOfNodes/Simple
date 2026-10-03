# Codex notes for Simple Chapter 25

This file is intentionally AI-facing. It records durable invariants, debugging
habits, and project-owner preferences that are easy to miss when reading only
the implementation. `WIP-HANDOFF.md` and `parser-simplification-plan.md` retain
the detailed history; portions of their old branch/status reports are stale
after the Chapter 25 squash.
For cross-chapter work, read [the backport queue and validation record](../docs/chapter-backports.md).
It owns pending reproductions and a condensed completion record; these notes
capture reusable lessons. Detailed superseded validation history remains in Git.
For shared viewer work, read [the graph viewer notes](graph-viewer-codex-notes.md).

## Collaboration preferences

- Build with Make alone; Maven must never be required. Cliff prefers few tests:
  avoid tests for every small viewer edit or rapidly changing display experiment.
  Use focused existing tests and disposable probes when they answer a real risk.
- Prefer concrete ArrayList to List, common base classes to unnecessary
  interfaces, and short names (`pred`, `proj`, `def`, `use`). Avoid redundant
  Objects.requireNonNull. Prefer int[]/AryInt to List<Integer>; avoid large boxed
  ID collections (use arrays/BitSet or NBHML as appropriate). Node ID 0 means null.
- Cliff often debugs the same failure in parallel. If asked to work in a side
  repository, update `tmp/` and do not edit the live files he is debugging.
- Stop and discuss before changing a central lattice, constructor, memory, or
  call-graph invariant if the required fix starts cascading. Reduced tests and
  concrete node-number traces are preferred over speculative broad rewrites.
- Printers are valid debugging tools. If printing changes behavior, fixing the
  printer/accessor side effect is immediately high priority.
- Keep edited text files LF-only, including on Windows.
- When mixing `||` and `&&` in one expression, always parenthesize the groups
  explicitly rather than relying on operator precedence.
- For any node consuming or producing both control and memory, control belongs
  in slot 0 and memory in slot 1; other inputs/results follow. This applies to
  Start, Return, and future forward ports of Call, CallEnd, and other such nodes.
- Preserve unrelated dirty changes. Emacs lock/backup files are common and are
  not permission to clean the tree.
- Do not push without explicit approval. Prefer commits at meaningful test
  frontiers with messages describing the architectural change.
- Give brief progress updates during long investigations and test runs. Cliff's
  PowerShell UI can appear blank while Codex is thinking; report concrete
  findings, the current check, and any blocker.

## Mutability and chapter boundaries

- Constructors start in 16, independent binding/access permissions in 17a,
  syntax sugar and inference in 17b. Historical Chapter 17 references mean 17b.
- `!Point` is writable access, `~Point` deep read-only access; `!p` allows
  rebinding, `~p` fixes a binding. Primitives default mutable; struct refs default
  fixed/read-only. Arrays default writable and use `[~]` per layer. `var`/`val`
  change only binding mutability, preserving initializer access.
- Keep access in TypeMemPtr through named/cyclic type resolution. A read-only
  load may observe writes through another alias; its memory edge is essential.
  Writable array slots are invariant in element permissions. Allocation and
  declaration spelling must agree on that element view.
- Construct cyclic pointers with their access bit already set before interning;
  creating a provisional pointer and then changing access can orphan temporary
  cyclic types. Serialized objects use the `C0D2` header and require a rebuild.

## Tutorial backports

- Shared ISA encoding lives in `isa/` for Chapters 21-25: x86 bytes behind
  `CodeSink`, ARM/RISC-V word packing, and test-only evaluators. Chapter code still
  owns machine nodes, register masks, operand/type interpretation, layouts and
  relocations. Fix shared forms once there; keep their byte tests independent
  of compiler IR and retain chapter execution tests. See `isa/README.md` for
  the exact boundary. Only 21-25 add ISA main/test sources to their builds;
  the linearized Maven build adds them at 21. Make's release jars still bundle
  tests and their evaluators; Maven treats the evaluators as test sources.

- Chapters 10-14 now introduce whole memory (10), lazy memory splitting (11),
  references (12), GCM (13), and numeric types including floats (14).
  Earlier notes using 10a/10b refer to today's 10/11. Float regressions are
  named Chapter14FloatTest; reference tests are Chapter12Test and GCM tests
  Chapter13Test. Every directory is a standalone compiler snapshot. Chapter 12
  deliberately retains immutable shallow struct references resolved by name;
  see its README's L0/L1 notes before trying to expand recursive types.
  Keep Chapter 25's separate constructor-memory and incomplete-type design.
  See the concrete boundary notes
  in `docs/chapter-backports.md`. GCM readiness and anti-dependency checks must both
  filter by alias and ignore MemMerge as a clobber; keep the evaluator's same
  alias filtering too. MemMerge still needs ordinary data-dependency placement.

- Chapter 15 New consumes `{ctrl, $mem, size}` and produces `{ptr, $mem}`:
  no control result, pointer slot 0, memory slot 1. Its TypeStruct/TypeMemPtr
  defines the covered field aliases. Use a partial input MemMerge (null default),
  one memory output projection shared across those aliases, and a whole-memory
  aggregate preserving unrelated prior slices. Missing partial aliases are
  uncovered, not zero or unknown memory. Typed alias queries follow New's input
  and meet in each field's initializer; cached MemPhi types break cycles, and
  explicit dependencies preserve worklist progress. Start's contents are empty
  in this chapter because it has no heap arguments; revisit that in Chapter 18.
  Both schedulers must follow aggregates to find New's effect for a read alias.
  Only a proven distinct allocation permits Load to bypass New; unequal pointer
  nodes alone do not prove disjoint objects.

- The preferred direction is: introduce a fix in the earliest applicable chapter,
  then use the same implementation in later snapshots where practical. Tutorial
  progression takes priority over importing the fully general Chapter 25 solution.
  Use a locally sensible fix when the general solution requires concepts not yet
  introduced; report substantial representation changes for review.

- Chapter 16 extends New inputs to `{ctrl, $mem, size, fields...}` while
  retaining `{ptr, $mem}` outputs and partial alias coverage. Constructor values
  determine alias contents; register dependencies on their nodes as well as
  New. ScopeNode owns Var records and the single memory binding. The existing
  sole-use Store-to-New guard is intentionally conservative with a shared memory
  result; do not add alias traversal just to recover a small constant fold.

- Chapter 18 keeps memory Parm 1 and CallEnd memory opaque: heap arguments make
  the earlier empty-entry-heap assumption invalid. Local/return merges use
  BulkMemPhi; calls carry the complete memory state and clobber all aliases.
  MemMerge no longer tracks parser state. GCM can raise a Load before a Call's
  terminating block position, but must not append anti-dependence inputs to
  Call: the last input is its function pointer. The one-step Phi Load guard
  stops at Calls. Eval2's New initializer indexing and tuple printer use ptr 0,
  memory 1, size input 2, field inputs 3 onward.

- Chapter 19 lowers nonzero initializers into explicit Stores. New has inputs
  `{ctrl, $mem, size}` and results `{ptr, $mem}`; use zero field types for alias
  contents queries, not constructor inputs. Both CPU selectors copy BulkMemPhi
  and MemPhi with their alias metadata. GCM handles reads through MemOpNode,
  including folded x86 arithmetic/comparison reads; selected TypeMem results
  identify writers. Trailing anti-dependence edges on writers have no register
  requirement. Direct machine Calls embed the target, but still must not gain
  scheduling-only inputs. The one-step Phi Load guard remains unchanged.

- Chapter 20 includes ARM and moves CodeGen to the codegen package. Memory
  Phis and projections have no live range; the existing allocator already
  excludes TypeMem. Preserve its inlining-safe Return compute and rejected-Phi
  operand dependencies. Folded x86 Add requires a register value in slot 4 even
  for a constant addend, because that value is also its two-address result.
  Spill counts use the same fixed 39-entry cohort and seed 123: the lazy-memory
  port changes 238 / 357 moves / weighted moves to 234 / 360 without changing
  allocator heuristics. Selected-read ordering explains the extra hot array move.

- Chapter 21 reverses addDep's direction: use `consumer.addDep(producer)`,
  including explicit forward dependencies. New's register masks are cached in
  the common node during allocation; size uses input 2 and pointer output 0.
  Its GCM already places writers before adding read-before-write constraints;
  preserve that chapter-specific rule. LoopNode.forceExit reconstructs memory
  using actual MemPhi aliases and merges return memory with BulkMemPhi. Machine
  execution tests must find main's encoded offset, rather than assuming PC 0.
  Full-width x86 loads choose their instruction from the allocated register
  bank, preserving integer bits when allocated to XMM. ARM's emulator needs
  MOVK and full MOVZ immediate/shift support. The 91-entry spill cohort changes
  840 / 1,456 to 845 / 1,559; read-before-New ordering explains most of the
  RISC-V BrainFuck increase. No allocator heuristics changed in that port.

- Synthetic never-exit TOP values remain in the graph for neutral type merges,
  but have no runtime value to allocate. Chapters 20-25 exclude TOP definitions
  and Phi arms from LRG construction, Phi-input liveness and spill copies; TOP
  arms also do not contribute to spill loop-depth estimates. Register-bias walks
  must stop at an input with no LRG. Do not give TOP a permissive register mask:
  that still creates false liveness, interference and spills. Coalescing assumes
  both ends of every actual copy have LRGs; fix the creation of bogus copies,
  rather than adding a null guard there.

- From Chapter 18, stop loop-tree discovery at each Return. Seed the function's
  outer tree before the post-order body walk, since `forceExit` can add a return
  Region and projections before the walk reaches the function entry. Those
  exits inherit the function's outer tree; Never and the backedge projection
  inherit the actual loop. Function bodies are depth 0, real loops add depth.
  In Chapter 25, Stop/Start and StopCU/StartCU links describe external-world
  type propagation, not executable loop backedges. Walking through them created
  inconsistent depths and caused the RISC-V pointer-return spill-round failure.

- Chapter 21's Load search is separate from the one-step factoring guard.
  It creates no nodes or rewiring, but records dependencies and returns folding
  witnesses. All arms of a non-loop memory Phi must succeed; a BitSet records
  successfully checked merges. Only the original loop Phi closes an unchanged
  path, and only for a loop-invariant pointer. Build value Phis after proving
  profitability, and enqueue the root value Phi even when it folds locally:
  self-edges otherwise keep an obsolete Phi alive outside the worklist. Reject
  hoisting controlled array accesses. BrainFuck's program length now folds;
  its RISC-V run loses 907 heap loads and all hot stack spills. Static move
  counts initially rose from repeated initialization-store pointer copies;
  adjacent-copy forwarding removes them. Measure executed memory traffic too.
  Carry this search with subsequent memory ports.

- Chapters 23-24 exclude class-wide (`Field._one`) fields from New's alias
  coverage and keep symbolic field offsets until layout. Alias contents must
  preserve cyclic field types and readonly information. Phi factoring must not
  introduce a pointer/integer operand Phi even when the result is boolean.
  A precise Phi must let its bulk input split before collapsing to it; inserting
  a scalar-style upcast would hide the alias request from the bulk Phi.
  Chapter 24 MemPhi skips both TOP and XCONTROL predecessor arms during SCCP;
  New's outputs and alias-contents query stay high while its control is high.
  Check registers before encoding, which may add untyped branch nodes.

- Chapter 25 Load search follows Escape to private/public memory; New has no
  public-memory input to bypass. Alias 1 on a Store is unresolved, so it blocks
  the search. External storage cannot use allocation disjointness. Array length
  Loads can drop control, while element Loads retain it. New integer value Phis
  start at i64 and use Convert at the result to preserve the declared width;
  decline hoisting when that would lose a sharper inferred range. Queue every
  new value Phi, including nested merges, and queue new precise memory Phis
  instead of eagerly folding them while bulk splitting is still in progress.
  User-count dependencies must wake from Node.delUse, including unkeep.

- Generalized Phi factoring now starts in 10a and is forwarded through 25.
  Memory-specific eligibility lives in `MemOpNode.canDrop`, with virtual Load
  and Store checks. Load owns `clobbered`: all these chapters check only
  immediate memory users, without recursion or a visited set. Stop at Stores,
  Phis, MemMerges (10b+), New (15+), and Calls (18+); do not chase aliases or aggregates just
  to save one Load. Keep this teaching optimization simple and conservative.
  Preserve exact operation attributes with clone-based `copyEmpty`, type each
  operand Phi separately, reuse identical inputs, and reject type widening.
  Stores require sole use by the Phi; Loads inspect memory users for clobbers
  before GCM has made anti-dependence edges. From 10b, create precise memory
  Phis for the factored operands. Do not factor aggregates or bulk Phis;
  wait for other BulkMemPhis at the Region to disappear, since their slice
  lookup assumes Region/alias identifies the completed memory point. Existing
  control-bound Stores in 11-14 remain ineligible. `delUse` wakes recorded
  dependents for user-count queries; Region's empty-diamond fold depends on
  projection rewiring as well as projection types. Carry these with the 21+
  memory port; do not duplicate the later chapters' existing `copyEmpty`.
  `subsume` must wake each rewired user's recorded dependents, just as `setDef`
  does: operand identity changes can enable a Phi fold without changing the
  intervening operation's type (`Chapter14Test.testCloneAnd` in 16).
- Find the chapter where the relevant feature first appears, not just the chapter
  that can parse the original reproducer. Reduce away later syntax/features when
  possible. Put the regression in that earliest `ChapterNTest.java`, and forward
  port it into the same test class in every later affected chapter directory.
- Every directory contains its own compiler snapshot. Testing Chapter 25's
  inherited `Chapter10Test` does not validate the Chapter 10 compiler. Use the
  top-level runner, e.g. `make -k tests CHAPTERS="chapter10a chapter10b chapter11"`, with all
  affected directories explicitly listed. Establish the destination baseline
  before changing it; older Makefiles may need a forced rebuild after API changes.
- Keep unrelated discoveries separate in `docs/chapter-backports.md`. Completed
  fixes leave the pending queue; retain a concise validation summary and move
  unresolved discoveries out of the history into the actual queue. A green suite
  after changing graph/test order does not prove an intermittent failure fixed.
- Store disposable probes/logs under an appropriate ignored build directory.
  Preserve durable reproducers in tests or the review record, not only scratch
  files. The B13 `chapter25/tmp` probes were removed at Cliff's request after he
  reviewed and pushed the fix; future sessions must not rely on those files.

## Dominator caches and searches

- Real dominator searches start in Chapter 6 (If and Region), before CFGNode
  appears in 11. Regions recompute their dominator from current predecessors
  using the shared `domLCA` walk; do not cache a dominator pointer just because
  the old node is still alive. Rewiring a merge can change its dominator.
- Chapters 6-17 cache a `char` depth with zero meaning unset. From 18, inlining
  requires a separate `char` cache version and a checked global version bump.
  Keep depth/version as separate fields rather than packing arithmetic into an
  int. Assert before narrowing a depth or incrementing the version: 65535 is
  the largest representable value, and wrapping must fail clearly.
- Copy both cache fields when copying a CFG node. Ordinary `Node.copy()` clones
  already preserve them; CFG copy constructors must do so explicitly. Inlining
  invalidates afterward, but future local CFG transforms may preserve valid
  caches outside their edit region.
- Every cached depth override, including Region, Loop and Stop, participates in
  version validation. Start has fixed depth zero; an unfolded function is a
  root, while a folding function uses its new caller-side depth. Preserve the
  chapter's dependency direction and dead-predecessor rules when sharing walks.
- Cliff requested removing the dedicated dominator bookkeeping tests/helpers;
  do not restore them without a substantive failure to cover. Keep existing
  printer cache snapshots checking both fields; cast char depths to int for
  numeric display.
- Keep single-return accessors and overrides compact on one line, including
  `idepth()` and `validIDepth()`.


## Global code motion and constant ownership

- GCM starts in 11; function-local constant graphs start with functions in 18.
  Use an early definitions-first walk and a late uses-first worklist. Visit
  Region/Loop Phis during early scheduling, and wake loop Phis and waiting loads
  during late scheduling. The two walks must agree: otherwise late scheduling
  can reach unscheduled arithmetic through a loop Phi. For ArrayList-backed
  outputs, walk the original index range when early scheduling appends uses.
- `isPinned()` is unnecessary: early scheduling preserves an existing input 0
  and computes a block only for nodes with a null input 0, after walking their
  inputs. Proj input 0 names its producer, so it must not be replaced by control.
  For ordinary values, existing control is an earliest-placement bound; late
  scheduling can still move them downward. Chapters 11-14 retain their fixed
  late-placement cases explicitly in GCM (Proj, New, Parser.ZERO, and Cast from
  13). Later GCM already handles fixed CFG/Phi/Proj placement structurally.
- After early scheduling, global constant-building operations have Start in
  input 0. Snapshot and keep those originals, then clone their input graphs with
  one identity map per function. Reuse each copy throughout its function; keep
  originals intact until all users are rewritten. This covers Cast/Constant
  stacks and machine expansions without recursively inferring users' ownership.
- `Node.copyEmpty()` makes an exact-class clone with fresh ID and empty edges.
  GCM wires it with `addDef`; do not use machine `copy()` overrides here, whose
  rematerialization contracts differ about whether input edges are registered.
  Preserve ordinary copy/cache fields; clear graph edges, dependencies and hash.
- In 18-19, calls remain linked: a Parm input is evaluated in its caller, and
  the unknown-caller input stays global. From 20, unlinked Parm values belong to
  the callee. Preserve 25's compilation-unit ownership checks.
- Let early scheduling place machine constant expansions. Pinning them during
  instruction selection can attach them to the old ideal Start rather than the
  selected Start, forcing later code to recover from the wrong root.
- Keep anti-dependence marks in a pass-local array, not CFGNode. In 11-20 the
  evaluator independently reschedules nodes, so retain the full store placement
  range when constraining loads; using only GCM's final store block breaks
  `SchedulerTest.testStoreInIf2`. Retain the dominator walk from 21 onward too:
  a conditional Store can precede a Load's late block without dominating it.
  Its exact block need not be marked, but an ancestor intersects the Load's
  placement range. `Chapter21Test.testReadBeforeConditionalStore` checks this.
  Preserve each chapter's alias/tuple memory representation when finding stores.
- Changes in node order can expose encoding bugs. Chapter 21's empty-block scan
  mistook entry to a nested loop for a backedge; require the same loop-tree node,
  as in 22 onward. Native Sieve is sensitive to this and must actually execute.
- Test function-local chains with two surviving function bodies. In 24, returning
  function pointers alone does not put their bodies in the scheduled graph;
  recursive calls keep this regression meaningful. Check all chain members,
  registered data edges, and one shared copy per function.

## Register allocation review

- Allocation starts in 20; native encoding in 21. Backport legality and
  no-progress fixes to the first applicable chapter, but introduce spill-quality
  heuristics gradually. Do not import the complete Chapter 25 allocator into 20.
  Printing, deterministic ordering, and diagnostic support may start in 20.
- Compare measured spill totals over the same tests, targets/ABIs, and seeds.
  Local increases are acceptable when offset elsewhere; report per-target and
  overall totals. `_spills` counts surviving SplitNodes, and `_spillScaled`
  weights those moves by `8^loopDepth`; neither means only memory stores.
- Use a fixed optimizer seed for routine allocator/scheduler/encoder validation.
  Seeds shuffle IterPeeps/Opto worklists; optimization is intended to normalize
  to the same graph modulo node IDs and equivalent operand orderings. Repeating
  the backend on essentially the same graph adds little coverage. Reserve seed
  sweeps for investigating optimizer normalization, missing dependencies, or a
  demonstrated order-sensitive failure; inspect post-Opto graph differences
  before expanding backend runs. Prefer distinct programs, register constraints,
  targets/ABIs, and actual execution for backend coverage.
- Separate allocation completion/register legality/runtime results from quality
  expectations. Reaching the round limit is a correctness bug; raising the
  limit or changing spill goldens does not prove progress fixed. Heuristics
  which defer necessary splitting must retain a bounded fallback.
- Review the README with each allocator chapter. End it with a RegAlloc
  improvements section, a measured table, and commentary. Each row names a
  program cohort, all compiled by that directory's compiler: 20 in Chapter 20,
  20-21 in Chapter 21, through 20-25 in Chapter 25. Include the BrainFuck and
  MergeSort allocator cases in the Chapter 20 cohort (39 compilations, not just
  Chapter20Test's 33). Keep diagnostic machine graphs separate from quality data.
- `make spill-stats` starts in 20. Helpers explicitly name the program cohort;
  the JUnit listener labels individual cases and sums CPU/ABI and cohort totals.
  Assertions stay enabled and failures propagate. Do not silently change cohort
  membership, seeds, or targets when comparing chapters.
- The staged review is complete through 25. Keep 21's shortened README and its
  separate encoding reference. The final narrow-store mask fixes changed the
  measured totals for 20 and 21; use the current READMEs, not older review counts.
- Chapter 21 changed several inherited inputs/ABIs. Its Chapter20Test now freezes
  all 13 original programs; Chapter21AllocTest retains the four revised cases,
  counted with the native variants as cohort 21. On Windows the cohorts are
  39 and 52 compilations. Preserve source/target membership when moving forward.
- Chapter 21 adds adjacent-copy forwarding after coloring; carry it through 25.
  A Split with exactly one use, in the next scheduled instruction, can be removed
  if the operand accepts the source register and is not two-address tied. Exclude
  Phi/CFG users. Adjacency supplies the no-clobber proof; do not expand this into
  CFG lifetime partitioning. Chapter 20 keeps its simpler baseline.
- Chapter 22 adds stronger copy-chain/backedge bias and cheap-spill ordering;
  popular-use grouping remains for 23. Preserve `person21` (64-bit age) separately
  from 22's narrower person example, and keep its revised infinite-loop input in
  cohort 22. The Windows cohort counts are now 39, 52, and 26; the two added C return
  ABI checks have zero moves.
- Chapter 23 groups popular single-def uses by compatible register masks. One
  intersection pass suffices: narrowing a class preserves its earlier users,
  and newly added classes are disjoint. Snapshot distinct users before rewiring,
  ignore null register masks, and count a call once despite repeated arguments.
  The grouping correction/regression is already forwarded through 25.
- Chapter 23's current cohorts count 39, 52, 26, and 30 allocations. Preserve
  `stringHash21` (with the old guard); its revised source and newly enabled Jig
  tests belong to 23. Grouping on/off gives identical 933 raw / 1,745 weighted
  moves here; report the zero gain rather than implying every heuristic helps
  this suite. The earlier-cohort increase includes changed optimizer behavior.
- Chapter 24 gives loop-Phi self-conflicts one cold-only splitting attempt;
  remember Phi identity across rebuilt LRGs and force the ordinary fallback on
  the next conflict. A backedge-only attempt can make no graph change and must
  still consume that allowance. The same regression runs in 24 and 25.
- Chapter 24 preserves cohorts 20-23 and adds 67 allocations for cohort 24.
  Distinguish moved inline sources from changed programs: keep the old 64-bit
  Person and small emulator argument list, and attribute revised inputs to 24.
  Cold-first splitting saves only 6 raw / 6 weighted moves here; the much larger
  cross-chapter reduction includes optimizer changes. Its frozen String program
  also found a lost null guard in the inlining dominator walk; keep that fix on
  both sides when measuring the allocator heuristic.
- Validate the instruction masks independently of the chosen register: a broad
  mask can hide behind favorable color bias. Byte/short x86 and RISC-V stores
  must exclude floating-point registers. Size fields such as `_sz` may hold a
  printable character: compare to `'4'`, not integer `2`. The narrow-store
  restrictions are now present from x86 20 and RISC-V 21 onward.
- The historical Chapter 22 seed sweep exposed failures outside allocation;
  it is not a template for routine allocator validation.
  Chapter 22's frozen String source fails before allocation and its returned
  function-pointer case fails during relocation at seeds 0-29, on all targets.
  Both reproduce in the original snapshot; report these failures explicitly.
  Do not replace the seed or add a return merely to make the sweep green.
- Use an ablation in the same compiler to measure each staged heuristic. Native
  ABI/lowering changes make the Chapter 20 and 21 totals different even with the
  same sources. Include the same legality fixes on both sides. Mask corrections
  can substantially change coalescing results; refresh old tables and commentary.
- Chapter 21's spill reporter defers quality-golden failures until reporting ends,
  so all target/runtime checks still run. The command must still exit nonzero.
  Keep full native process exit codes and check each emulator's own result memory.
- An ARM store of Top can originate in the optimizer, before allocation. Inlining
  deletes the function entry before its Return necessarily folds away; compute
  the return type from its live inputs. Never infer unreachable return data from
  `_fun.isDead()` alone. This correction is now present in 20-25, with a seeded
  interpreted-result regression; 20 first uses linked Return types at call ends.
- Validate beyond encoding. The repaired seed-9 String case then exposed missing
  register-register SUB support in the ARM emulator (now corrected in 21-25).
  Check real results and unchanged flags; successful allocation/encoding alone
  does not establish execution correctness. The 1,170-case Chapter 21 seed sweep
  now passes through encoding, and the original ARM/RISC-V hash results agree.
- For ARM execution probes, finish in-memory export so external relocations
  (especially calloc) are patched, then select the intended function entry.
  Encoding alone leaves placeholder branches. Check return PC, SP, callee-saved
  registers, and the result; passing an instruction budget is not completion.
- ARM call/frame fixes are present in 21-25: ADD/SUB immediates are unsigned
  magnitudes, frames use byte counts and 16-byte alignment, BL writes only X30,
  and RET names X30 explicitly. Test instruction words independently of the
  compiler so matching encoder/emulator mistakes cannot validate each other.
- Copy cleanup/reuse must check kill masks even when an instruction has no LRG;
  before coloring, a fixed-register definition can clobber despite `_reg==-1`.
  Cloning is valid only if its output mask can satisfy the use. A fixed-register
  clone with flexible uses needs use-side splits, not a no-op def-side path.
- Check function ownership and register masks immediately after allocation,
  before encoding rewrites tail calls or adds untyped branches. Call.regmap can
  itself depend on CFG.fun/idom. The test-only CheckedCodeGen override keeps
  these checks at the right phase without changing the driver; in 25 this also
  preserves ideal-graph serialization/import unlinking before instruction selection.
- Shared correctness/support can be forwarded as one review batch, while leaving
  each chapter's quality heuristics and historical cohort/README review staged.
  Compare each destination's unchanged local suite before/after for such a batch;
  do not present those unequal local suites as the chapter progression table.
- Chapter 25's spill corpus preserves the earlier 212 source/target entries,
  with documented constructor, receiver, library and mutability adaptations.
  These are allocation/legality replays, not historical runtime harnesses. Its
  own cohort includes current Chapter25Test allocations and a fresh library
  encoding. See `chapter25/src/test/java/com/seaofnodes/simple/spill/README.md`.
  Do not silently use current inherited tests as though their sources were frozen.
- Rebuild and measure the system library even if sys.o is up to date. In the
  final 25 review, the older ranking and a multi-use-clone preference looked
  better on clients but exhausted allocation rounds on fresh sys compilation.
  Both trials were rejected. Keep the existing area/cost ranking; client-only
  totals do not prove whole-suite improvement. The driver uses seed 456, old
  cohort replay uses 123, and current test helpers normally use 126.
- ARM load/store opcodes must follow the allocated register bank, not the
  source Type. Emit the SIMD bit for both addressing modes, normalize D0-D31
  to register numbers 0-31, and permit FP registers only at supported widths.
  A GPR-only float-store mask made 25's old Newton programs repeatedly split
  the wrong side of a loop. The paired fixes start in 21's executable backend.
  Emulator FP memory operations must preserve bits and scale double offsets
  by eight. Use independent instruction words as well as compiler round trips.
- Fixed-neighbor bias must clear the computed register, including a neighbor's
  sole allowed register before it is colored; `_reg` can still be -1. The shared
  correction and regression start in 20. A passing corpus can miss this path.
- Chapter 25's extra multi-def/fixed-use thresholds and kill/rematerialization
  choices remain local strategies. Earlier splitEmptyMaskSimple lacks the extra
  branch that needs 25's original-def-use-count snapshot. Do not label a textual
  difference a missing correctness fix without establishing the affected path.

## Null guards: B13 / issue #246 lessons

- Null refinement starts in Chapter 10; arrays in 15; scoped expression guards
  in 17; short-circuit syntax in 23. Chapters 10-16 refine local bindings with
  casts/constants, 17-24 use scoped CastNode facts, and 25 uses GuardNode with
  incomplete types. Before Chapter 13, the early-return regression needs an
  explicit `else` to get the false-arm refinement.
- `p != null` can become `!!p`. Control simplification may strip both negations
  while guard discovery still misses non-null `p`. Compare the guarded load's
  input after parsing, not just the simplified If predicate.
- Existing code already handles one Not. Recurse for nested Not (or Not of a
  short-circuit Phi where supported), flip the proven truth, and preserve the
  Boolean value. Unconditionally duplicating single-Not guards caused a Dijkstra
  SCCP assertion during B13 development; that approach was discarded.
- Keep the predicate alive across recursive peepholes (`keep`/`unkeep`), and
  separate recursive guard discovery from the public guard-set marker. Otherwise
  temporary predicates can die or recursive calls can disturb guard removal.
- Short-circuit refinement must not guard an RHS-only value where it is not
  available. Chapters 23-24 use `availableAt` with existing CFG dominators;
  Chapter 25 uses its existing `earlyCFG`/dominance machinery. Do not import this
  machinery into Chapter 10 merely to handle nested negation.
- `Chapter10Test.testNullGuards` and `testNullGuardErrors` cover positive and
  rejected uses throughout 10-25; `testShortCircuitGuardScheduling` adds RHS-only
  call-result coverage in 23-25. Check both runtime outcomes and scheduling, not
  just successful type checking. Eval2's Not must handle pointers/null as well as
  numeric zero; B13 ported that helper from 25 to 18-24.
- As of the 2026-09-19 review, B13 is complete and Cliff reports it pushed.
  The separate B14 cyclic-equality failure had a deterministic type-only
  reproducer despite green B13 suites; see the type lessons below.

## Central parser rule

Simple parses directly to SSA without an AST. Forward declarations mean types
can be incomplete during parsing. The governing rule is:

> Syntax and resolved lexical binding choose SSA topology. Types may sharpen,
> optimize, and reject the graph, but provisional types must not choose its
> shape.

Consequences:

- `compute()` must accept the weakest legal inputs and remain monotonic.
- Parser-only lower bounds such as the old Load/Phi `_con` fields are suspect.
- Calls keep syntactic shape. `ptr.field(args)` supplies `ptr` in the hidden
  self slot even before the eventual function kind is known.
- Numeric operations carry an unresolved mode until graph evidence settles
  integer versus floating behavior.
- A Load flow type is not guaranteed during parsing. Static declaration facts
  may instead be recovered from symbolic field offsets or lexical `Var` state.

## Constructors

- A user constructor is declared `new N = { args -> body }` and invoked as
  `new N(args)`.
- Allocation first calls the hidden private `<init>`, then the user constructor.
- Constructor hidden arguments are public memory at index 1, self at index 2,
  private self-memory at index 3, followed by user arguments.
- Constructors return private self-memory. `EscapeNode`s publish it into public
  memory after construction.
- The hidden initializer supplies defaults/poison. User constructors must set
  non-defaultable fields on every exit before the object escapes.
- Constructor field state is tracked in parser `Var` metadata. Early reads of
  possibly-uninitialized fields are errors.
- Check constructor completion in the parser, independent of allocation and
  optimized memory. Save each constructor's merged exit field types by Var
  identity and validate against the complete declaration at struct close.
  No user constructor means an implicit empty constructor using declaration
  defaults; a non-returning constructor has no completion obligation. Nested
  blocks retain the enclosing constructor context, but nested functions do not.
- Recursive constructors may reasonably be rejected; constructor chaining must
  preserve private-memory initialization state.

## Memory and alias invariants

- The parser carries one ordinary bulk-memory value. Precise alias partitioning
  belongs to the graph and optimizer, not nested parser Scope state.
- Alias `#1` is bulk/unresolved memory. A precise alias is `#N`, with `N != 1`.
- Precise memory shapes always use `MemPhiNode`; bulk memory slices use
  `BulkMemPhiNode`. Plain `PhiNode`s do not represent memory. If a memory bug
  seems to require generic Phi memory state, suspect a representation/worklist
  bug instead.
- `MemMerge` inputs are disjoint slices whose union covers all memory. Its
  default input means all aliases not explicitly split out.
- `BulkMemPhi` carries `All - exclusions`; parallel `MemPhiNode`s carry the
  aliases in the exclusion set.
- A precise consumer may select `MemMerge.alias(N)`. A bulk consumer may not
  silently replace a `MemMerge` with `alias(1)` because that discards every
  precise side effect.
- In particular, Store-after-MemMerge bypass is valid only when the Store
  already has a precise alias. This is covered by Chapter25Test's chained
  `grow(1).buf[len++]` regression.
- When an unresolved Store sharpens, rebuild a precise Store plus `MemMerge`;
  do not mutate a whole-memory Store in place after users have treated it as
  the complete memory state.
- Stores retain control when it represents conditional execution. Removing
  the control from the Store after an early return can make the write execute
  unconditionally.
- Store width is semantic state (1/2/4/8 bytes, with 0 unresolved) and comes
  from the target field declaration, never from the stored value's GLB or the
  removed parser constraint.

## Types and monotonicity

- Integer widening starts in Chapter 24. `nonZero()` preserves `_widen` when
  narrowing a range. B10's correction is in 24, with `TypeTest.testNonZeroWidening`
  in 24-25. Both now use widening 0-3 and dual widening `3-widen`, preserve
  singleton widening, and give TypeConAry the same widening coordinate. Loop Phis
  widen only below-center nonconstant ranges: above-center widening can fall to
  an unwidened constant, violating SCCP monotonicity. Existing Chapter14/21 tests
  exposed this during the backport.
- TypeConAry's `_make(any,widen)` shares the backing array and returns an
  uninterned type. `xdual()` must leave interning to Type.intern's dual-pair setup;
  ordinary factories and ymeet's widening replacement intern their results.
  Chapters 24-25 use this API and a distinct array tag. Chapters 22-23 only receive
  compatible cleanup; their sentinel-based representations predate widening.
- Cyclic structural equality starts in Chapter 23. Chapters 9-22 compare
  interned children by identity and check type kinds in `Type.equals`.
  In recursive equality, leaf dispatch must also check `_type` before calling
  subclass `eq`: that method assumes matching kinds. B14 adds this check in
  23-24; 25 already had it. No earlier representation change is needed.
- Struct hashes in 23 onward omit field types to handle cycles. Same-named
  structs with different leaf kinds therefore collide intentionally. Exercise
  that path deterministically by interning a BOTTOM-field struct before
  meeting two float-constant variants; `TypeTest.testCyclicLeafKinds` in 23-25
  checks the resulting field, canonical identity, and meet/dual behavior.
- Function signatures acquire implicit open/closed argument tails in 23.
  Normalize trailing defaults before interning: global BOTTOM/TOP in 23-24,
  scalar BOT/TOP in 25. Preserve defaults before a later non-default argument.
  Keep normalization out of raw cyclic allocation, whose child slots are still
  incomplete. Add representative signatures to `TypeFunPtr.gather` for the
  existing lattice-law tests; no separate test harness is needed.
- The type lattice must remain complete, symmetric, and bounded. Run TypeTest
  after changing Type/Field equality, hashing, duality, meet/join, interning,
  serialization, or gather sets.
- `TypeScalar` contains the scalar values ordinary Loads and Stores handle:
  integers, floats, memory pointers, and function pointers. Scalar TOP/BOT sit
  inside global TOP/BOT and avoid interpreting global BOT as all functions.
- `TypeStruct._open` means fields may still be discovered. `_fref` means the
  named structure has been referenced but never authoritatively defined. A
  real definition meets away `_fref`; field discovery alone does not.
- Moving an unresolved pointer or checking it for null is valid. Operations
  requiring layout or fields must eventually diagnose a never-defined struct.
- `TypeNil` owns pointer nullability. Required pointer fields begin nullable as
  an initialization poison and must become their declared non-null type.
- One-step idealization must preserve the type knowledge already established.
  `IterPeeps.progressOnList` and `Opto.worklistCheck` are invariant checks, not
  assertions to weaken.
- Worklist order must not affect semantics or prevent optimizer normalization.
  Use deterministic seed variation when investigating missing dependencies or
  order-sensitive optimization; never fix a bug by merely favoring one order.

## Functions and escape analysis

- Unified function returns start in Chapter 18. Check return compatibility from
  the optimized return expression, not a parse-time meet that includes dead
  exits. Parse-time type-kind flags may describe an error, but must not decide
  whether an error exists. Earlier snapshots keep separate Return nodes; they
  already accept dead mixed-type exits and do not enforce one common return type.

- Chapters 22-24 use Return-linked FunPtrNode constants. No linked calls does
  not mean no callers when a function address survives. Direct call targets use
  the existing call graph; any other address use conservatively preserves the
  unknown-caller Start input. Phi-selected functions therefore keep general
  parameter types. Escape propagation stays in 25; Cliff may split 25 into
  smaller chapters later. Stop retains callable Returns through instruction
  selection, which removes the lifetime edge from machine operands before
  scheduling and cloning. Never substitute relocation-array growth or a linker
  address scan for correct retention.
- The direct-call exemption requires the pointer to occur only as the call
  target: `call.fptr()==ptr && call._inputs.find(ptr)==call.nIns()-1`. A pointer
  also passed as an argument still needs unknown callers retained.
- Validate returned addresses by calling them. Include anonymous and library
  functions, plus unused-body removal. Methods gain a receiver argument in 23;
  native/emulator probes must honor it even when the body does not use self.
  A wrong native signature initially passed because a stale argument register
  happened to contain the right pointer; a passing call alone cannot prove ABI
  correctness.
- Chapter 21's empty-main heuristic can remove main for `return {->42;};`.
  Use `if(arg) return {->42;}; return {->43;};` to probe returned addresses,
  and invoke main explicitly at its encoding offset. Its CodeGen.driver cannot
  resume after an explicit opto() call; use the remaining phases explicitly or
  start a fresh driver.
- Function-address encoding begins in 21. RISC-V AUIPC/ADDI occupies eight bytes;
  patch the second instruction at opStart+4 and round the high part for signed
  low bits. Anonymous bodies need emitted code, but no exported ELF symbol.
- Removing a use can enable a distant rewrite without changing the definition's
  type. Node.kill in 24-25 wakes that definition's dependencies immediately; only
  queueing the definition missed single-call inlining after another call died.
- A constant Simple function address must be a `FunPtrNode`, not an ordinary
  `ConstantNode`, so the pointer retains an edge to the function Return.
- Before Opto, a live FunPtr keeps its function callable even if no Call is
  currently linked. After Opto, an orphan FunPtr may remain as a null/equality
  sentinel while the function body becomes dead; this deserves careful error
  checking rather than a pre-Opto deletion.
- Extern C functions have no Simple FunNode/Return and are handled only while
  linking.
- Unknown-caller Start edges and post-Opto compilation-unit ownership are
  separate concepts. Do not overload Start edges merely to keep surviving
  functions attached to a compilation unit.
- A global escape fallback means all public functions, not all functions.
  Public `<clinit>` functions escape when their class and all ancestor names
  are public (no direct name component begins with `_`).
- Escape summaries are monotone discovery facts. If a later SCCP state widens
  to FULL/public-only, it must not forget precise private bits discovered from
  the same Return earlier.

## Serialization and code generation

- Narrow direct C integer returns are normalized during instruction selection
  in 22-25, using the declaration's width and signedness. An ideal cast would
  fold away because the CallEnd already advertises the narrow semantic type.
  In 25, identify external functions through their FIDX mapping: ExternNode can
  fold into an ordinary ConstantNode. x86 upper return bits can be garbage;
  RV64 sign-extends even u32 returns, which Simple must zero-extend again.
  Native/emulator regressions start in Chapter22Test; Chapter25Test's errno
  check also verifies the negative close result after importing sys.o.
- Machine expansions must use the copying constructor and directly populate
  input arrays, leaving reverse edges to CodeGen._instOuts. Using the ordinary
  edge-registering constructor duplicates uses; the narrow-return expansion
  exposed this during FileIO register splitting.
- Keep Serialize's magic C0DE unchanged for now. Cliff explicitly unwound the
  proposed magic bump; a real format version is future work. Rebuild all object
  files after incompatible layout changes while there are no external users.
- Class storage must be writable during `<clinit>`, even when all final field
  values are constant. `Encoding.Relo.readOnly()` owns this distinction for
  pool emission, ELF symbols, in-memory linking, and assembly printing. Ordinary
  constant objects remain read-only. `val x = 42; return 0;` exposed a native
  read-only write that emulator memory did not reject.
- Object files carry canonical types plus ideal Simple IR. Every new semantic
  type field, node field, tag, alias map, or mode needs balanced serialization,
  deserialization, type upgrading, equality, and bijection testing.
- Avoid smart peepholes after Opto. Machine selection briefly has incomplete
  graph edges, and GVN already guarantees uniqueness where required.
- `PtrToIntNode` is the explicit non-null pointer-to-integer conversion used for
  C FFI calls. User code must handle null before converting.
- x86 REX arguments are `(ModRM.reg, ModRM.r/m, SIB.index)`. Keep that ordering
  aligned with `modrm`; reversing it silently selects the wrong extended
  registers. The Chapter 25 Bubble Sort failure exposed this in `MulIX86`.

## Side-effect-free diagnostics: B09 lessons

- Shared debug printing lives in `print/`; see its README for the API. All
  chapter Nodes extend `BaseNode<Node>`, which owns the ID field, unique naming,
  print/toString, and default expression formatting. Compiler constructors pass
  IDs from their existing allocator to super; clone still assigns a fresh ID.
  Simple hooks override `format()` with input slots (`%1`, `%10`, literal `%%`).
  Custom protected `_print1` hooks use `p.p(...)`, `p.n(...)`, open/close, and
  matching-suffix unchar. Every print tracks repeats; short constant types expand
  repeatedly. Both IR and graph adapters consume the common indexed edge and
  dependency accessors. Graph record/edge construction belongs in GraphAdapter;
  chapters supply only their semantic roles, names, projections and grouping.
- Shared IR ordering finishes CFG RPO before placing data. Walking control and
  data uses together can move a loop's closing Region before its body via Phi
  backedges. Keep Loop/Region Phis contiguous with their header without pulling
  backedge definitions up. Parm inputs belong to callers; stop at function
  boundaries, and print globally shared floating expressions once with globals.
  All traversal/placement state uses private identity maps and raw edges, without
  invoking scheduling or dominator queries. Machine hooks must format correctly
  into an initially empty buffer; multiline expansions are laid out centrally.

- `Node._inputs` remains allocated after `kill()`. Use `isDead()` when printing
  or filtering dead nodes; an empty input array alone does not imply death.
  Bounds-safe edge inspection only needs null-node and input-count checks.

- Type construction and interning are allowed during printing. Do not add
  alternate linker-key comparisons or other machinery merely to avoid interning;
  canonical types and fast identity comparisons are normal type-system operations.
- Type layout queries are forbidden during printing, including queries that
  currently return cached answers. Layout lives in TypeStruct for convenience,
  but is separate from type identity and lattice operations. It may eventually
  move elsewhere to support removing dead fields or packing fields with limited
  states. Capture size, alignment, and section choice during encoding; printers
  display that recorded layout instead of asking Types to compute one.
- Use the `_` prefix for no-side-effect variants of accessors: `CodeGen._link`,
  `RegAlloc._lrg`, `Var._type`, and the existing leaf `_isConstant` predicates.
  Shared printer placement must not populate compiler dominator caches.
  Keep mutating compiler accessors available for their normal jobs.
- Audit the whole call chain from `Node.p(depth)`, `print`/`toString`, labels,
  scope/graph viewers, and machine `asm` methods. `link` prunes dead functions
  starting in Chapter 24; 19-23 can use ordinary `link`, including its interned
  return-erased key. Scope memory reads can create lazy Phis, `Var.type()` can
  resolve declarations, and register lookups can compress union-find links.
- Use identity bookkeeping for graph inspection: `Node.hashCode()` writes
  `_hash`, which also controls GVN locking. Leaf diagnostic type accessors can
  use their existing `_` implementations without entering shared Type.VISIT
  recursion; this is separate from permission to intern types.
- Test cold caches, unresolved declarations, lazy memory, stale linker entries,
  uncompressed register chains, and forbidden layout queries. Do not assert that
  printing leaves the type intern table unchanged. B09 regressions start in
  Chapter11Test/18Test/19Test/20Test/23Test/24Test and propagate to later snapshots;
  Chapter25Test also covers constant-pool display.

## Debugging workflow

1. Reduce the first semantic or invariant failure before changing architecture.
2. Refer to graph nodes by class and node ID, e.g. `Store#107`.
3. Find the first phase or IterPeeps count where the graph becomes wrong.
4. For memory bugs, verify both exactly-once alias coverage and that a precise
   consumer never requests an alias excluded from bulk without a parallel slice.
5. Distinguish ideal-graph correctness from selection, scheduling, register
   allocation, and byte encoding. Compare IR, `CodeGen.asm()`, and `objdump`.
6. Rebuild `sys.o` after compiler changes that affect serialized IR or native
   code; stale system objects can make results appear inconsistent.
7. Preserve the full process exit status and capture stderr. Chapter 22's Hello
   World printed its expected output before crashing; Cygwin returned 2816,
   which `(byte)waitFor()` turned into zero. Expected stdout alone cannot prove
   successful execution. Normal TestC/driver execution now throws on failure;
   the legacy module-test helper explicitly treats exit codes as results.
8. Chapter 25's `make -j 4 tests` runs test groups in separate JVMs. Native
   artifact names must be distinct across groups: Chapter22Test uses
   `helloWorld`, Chapter25Test uses `helloWorldSys`, and driver variants have
   separate output directories. `NativeExecutionTest` covers overwriting an
   executable between link and execution, full exit statuses, and driver errors.
9. CI uses `make lib tests CTAGS=` so native runtime and sys prerequisites are
   built. Chapter 25's default ABI must match TestC: win64 on Windows, SystemV
   elsewhere. Native spill expectations can differ by ABI (Newton is 42/34);
   validate Linux as well as Windows when changing native test setup.

Useful test ladder:

```text
focused test
chapter test
make tests_raw0
make tests_raw1
make tests_alone
TypeTest after lattice changes
serialize/deserialization bijection after persisted graph changes
```

Default fuzzer wrappers use explicit regression seed lists; exploratory fuzzing
is opt-in. A passing wrapper with an empty list is not exploratory coverage, and
`OPEN_FAILING_SEEDS` remain unresolved until explicitly verified and promoted.
For dated suite counts and known failures, consult the backport validation record
rather than treating an old count as the current baseline.

Peepholes are always enabled in every chapter, by Cliff's request on 2026-09-26.
Do not reintroduce an unoptimized compiler mode. Incomplete Loop/Phi nodes still
defer rewrites that require their missing inputs; this is part of normal graph
construction. Early README diagrams showing unfurled arithmetic are schematics.

Chapter 8 fuzzing checks compilation/evaluation for failures; 9 onward compare
optimizer worklist seeds. Historically 8-18 compared peepholes off/on. A change
of oracle does not prove an old discrepancy fixed. Preserve actual generated
source when comparing chapters, because the generators and dialects change.
Cliff removed the frozen Chapter 18 fixture, replay tests, related reductions,
and seed-list entry on 2026-09-27 as unnecessary test overhead. Retain the
evaluator fixes; do not recreate that test suite. Do not describe timeout-skipped
comparisons as equal runtime results.
