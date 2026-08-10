# Codex notes for Simple Chapter 25

This file is intentionally AI-facing. It records durable invariants, debugging
habits, and project-owner preferences that are easy to miss when reading only
the implementation. `WIP-HANDOFF.md` and `parser-simplification-plan.md` retain
the detailed history; portions of their old branch/status reports are stale
after the Chapter 25 squash.

## Collaboration preferences

- Cliff often debugs the same failure in parallel. If asked to work in a side
  repository, update `tmp/` and do not edit the live files he is debugging.
- Stop and discuss before changing a central lattice, constructor, memory, or
  call-graph invariant if the required fix starts cascading. Reduced tests and
  concrete node-number traces are preferred over speculative broad rewrites.
- Printers are valid debugging tools. If printing changes behavior, fixing the
  printer/accessor side effect is immediately high priority.
- Preserve unrelated dirty changes. Emacs lock/backup files are common and are
  not permission to clean the tree.
- Do not push without explicit approval. Prefer commits at meaningful test
  frontiers with messages describing the architectural change.

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
- Worklist order must not affect semantics. Rotate deterministic seeds to
  expose missing dependencies; never fix a bug by merely favoring one order.

## Functions and escape analysis

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

As of the Chapter 25 squash preparation, `make tests_raw0` passes 360 tests and
Chapter25Test passes its system, Hello World, and Bubble Sort coverage with a
freshly rebuilt `sys.o`.
