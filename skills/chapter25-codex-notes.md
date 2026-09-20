# Codex notes for Simple Chapter 25

This file is intentionally AI-facing. It records durable invariants, debugging
habits, and project-owner preferences that are easy to miss when reading only
the implementation. `WIP-HANDOFF.md` and `parser-simplification-plan.md` retain
the detailed history; portions of their old branch/status reports are stale
after the Chapter 25 squash.
For cross-chapter work, read [the backport queue and validation record](../docs/chapter-backports.md).
It owns pending items and detailed reproductions; these notes capture reusable
lessons rather than duplicating that history.

## Collaboration preferences

- Cliff often debugs the same failure in parallel. If asked to work in a side
  repository, update `tmp/` and do not edit the live files he is debugging.
- Stop and discuss before changing a central lattice, constructor, memory, or
  call-graph invariant if the required fix starts cascading. Reduced tests and
  concrete node-number traces are preferred over speculative broad rewrites.
- Printers are valid debugging tools. If printing changes behavior, fixing the
  printer/accessor side effect is immediately high priority.
- Keep edited text files LF-only, including on Windows.
- Preserve unrelated dirty changes. Emacs lock/backup files are common and are
  not permission to clean the tree.
- Do not push without explicit approval. Prefer commits at meaningful test
  frontiers with messages describing the architectural change.
- Give brief progress updates during long investigations and test runs. Cliff's
  PowerShell UI can appear blank while Codex is thinking; report concrete
  findings, the current check, and any blocker.

## Tutorial backports

- The preferred direction is: introduce a fix in the earliest applicable chapter,
  then use the same implementation in later snapshots where practical. Tutorial
  progression takes priority over importing the fully general Chapter 25 solution.
  Use a locally sensible fix when the general solution requires concepts not yet
  introduced; report substantial representation changes for review.
- Find the chapter where the relevant feature first appears, not just the chapter
  that can parse the original reproducer. Reduce away later syntax/features when
  possible. Put the regression in that earliest `ChapterNTest.java`, and forward
  port it into the same test class in every later affected chapter directory.
- Every directory contains its own compiler snapshot. Testing Chapter 25's
  inherited `Chapter10Test` does not validate the Chapter 10 compiler. Use the
  top-level runner, e.g. `make -k tests CHAPTERS="chapter10 chapter11"`, with all
  affected directories explicitly listed. Establish the destination baseline
  before changing it; older Makefiles may need a forced rebuild after API changes.
- Keep unrelated discoveries separate in `docs/chapter-backports.md`. Completed
  fixes leave the pending queue; preserve their validation history. A green suite
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
  `SchedulerTest.testStoreInIf2`. From 21, the chosen store block suffices.
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
- Chapter 20's corrected baseline costs one extra weighted move (354->355 over
  39 compilations); document this as a correctness cost. Advanced quality
  heuristics remain staged. Chapters 20-22 are now implemented; common correctness
  fixes and diagnostic hooks have also been forwarded through 25 at Cliff's request.
  Resume the quality/cohort/README review at 23. Chapter 21's shortened README links the retained encoding reference.
- Chapter 21 changed several inherited inputs/ABIs. Its Chapter20Test now freezes
  all 13 original programs; Chapter21AllocTest retains the four revised cases,
  counted with the native variants as cohort 21. On Windows the cohorts are
  39 and 52 compilations. Preserve source/target membership when moving forward.
- Chapter 22 adds stronger copy-chain/backedge bias and cheap-spill ordering;
  popular-use grouping remains for 23. Preserve `person21` (64-bit age) separately
  from 22's narrower person example, and keep its revised infinite-loop input in
  cohort 22. The Windows cohort counts are 39, 52, and 24.
- Validate the instruction masks independently of the chosen register: a broad
  mask can hide behind favorable color bias. Byte/short x86 and RISC-V stores
  must exclude floating-point registers. Size fields such as `_sz` may hold a
  printable character: compare to `'4'`, not integer `2`. Chapters 22-24 now carry
  25's store restrictions; x86 20-21 and RISC-V 21 remain queued.
- The historical Chapter 22 seed sweep exposed failures outside allocation;
  it is not a template for routine allocator validation.
  Chapter 22's frozen String source fails before allocation and its returned
  function-pointer case fails during relocation at seeds 0-29, on all targets.
  Both reproduce in the original snapshot; report these failures explicitly.
  Do not replace the seed or add a return merely to make the sweep green.
- Use an ablation in the same compiler to measure each staged heuristic. Native
  ABI/lowering changes make the Chapter 20 and 21 totals different even with the
  same sources. In 21, coalescing saves 127 weighted moves, but deferring later
  heuristics costs 174 against the old combined snapshot; disclose both.
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
- The staged plan and historical fix inventory are in
  `docs/chapter-backports.md`; its review is not evidence of completed backports.

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

- Chapters 22-23's post-Opto name-based function pruning is not escape analysis:
  no linked calls does not mean no callers when a function address survives.
  In 22, `return {->42;};` and `return sys.io.p;` lose their bodies but retain
  pointer constants and stale ideal linker targets, then fail in relocation after
  machine NIDs reset. Diagnose retention before resizing relocation tables. A
  named-function workaround or keeping only anonymous functions is insufficient
  for the library-pointer case. Cliff wants Chapter 25's FunPtrNode investigated
  as the backport, including the Return edge and callable-function retention
  rules, after register allocation work is complete. Keep the Chapter 22 examples
  as documented known failures until then; do not add an interim address scan.
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
  The printer's local `_idepth` does not populate compiler dominator caches.
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

When comparing historical fuzzer results, check what the harness compares:
Chapter 18 compares peepholes off/on; Chapter 19 switches to optimizer worklist
seeds. A change of oracle does not establish that an older discrepancy is
fixed. Validate the full failing seed before promoting it, even when a
reduced source exposes a separately fixed diagnostic.
