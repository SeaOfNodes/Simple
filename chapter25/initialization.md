# Lazy class initialization design

Design and implementation plan, revised 2026-10-10. Checkpoint 1 implements
post-Opto initialization dependencies and cycle checking for review. Declared
constant final fields already have static-data lowering. General lazy run-once
initialization, serialized dependency validation, and initialization guards
remain to be implemented; the compiler does not yet enforce the full protocol.

Initialization metadata belongs with class identity and compilation-unit
ownership, not solely on the `<clinit>` FunNode. Represent active touches in
ideal IR, validate an acyclic graph across source and imported objects, and
lower remaining checks to a hidden flag in the class object and a conditional
call. The initializer itself owns the store that sets the flag.
Use one runtime bit in the first, single-threaded implementation.

## Language rules

- Dynamic class initialization runs once, on the first active touch.  A class
  never actively touched executes no initialization code.
- A dynamic parent completes before a dynamic child starts.  There is no early
  publication point or special tail-call escape from this rule.
- Outside the initializer's own checked initialization context, no code may
  observe fields before their required initialization completes.
- Class identity exists before initialization.  Taking a class reference, as
  in `sys`'s `val io = io`, is passive and does not execute the child's body.
  Storing a function identity is also distinct from invoking it.
- Proven static-data accesses require no dynamic work.  This does not authorize
  a called function to observe unfinished dynamic state in an ancestor or any
  other class.
- Initialization requirements form a DAG across the complete linked program,
  including module boundaries. Count only requirements surviving Opto and its
  call-graph analysis. Reject cycles behind conditionals that remain unresolved;
  eliminated branches and call targets contribute no edges. Ordinary recursive
  functions are still legal.
- Initialization order follows actual execution and parent requirements, not
  parser discovery order, worklist seed, or filesystem enumeration order.

For example, A conditionally reading `B.foo`, with B initializing `foo` from
`A.bar`, is rejected if that read survives Opto. Neither an initial zero nor an early evaluation of
`A.bar` resolves that cycle. If B is A's child, the parent requirement already
provides the reverse dependency.

An initializer may read its own previously initialized fields under the existing
definite-initialization rules.  That is not an ordinary external active touch.
Do not infer permission for arbitrary callbacks or same-class helper calls from
the fact that their owner is currently being initialized.  Such helpers need a
proof that they do not observe unfinished state; otherwise reject their use.

## Ownership and identity

Keep `CompUnit._deps` for source/object discovery and rebuild dependencies.
Do not reuse it as the initialization graph: passive references belong there,
and its elements are files rather than necessarily individual classes.

Use CompUnit directly for the current runtime initialization graph. The parser
creates a file `<clinit>` in `parseStruct(true, ...)`; nested struct declarations
use `parseStruct(false, ...)` for instance initialization. Do not introduce a
separate ClassInit object or invent a dynamic class initializer for those
instance constructors.

Reuse `_cname` for stable identity, `_par` for the filesystem parent, `_clz` for
the canonical class object and hidden field, and existing function identities
for the body. Add only initialization-specific information:

| Member | Meaning |
|---|---|
| `_classInitDeps` | Lazily allocated `Ary<CompUnit>` of direct initialization requirements |
| `_initComplete` | All initializer touches and relevant call targets have resolved |
| `_initStatic` | Proven that this unit's own initializer needs no execution |

Keep diagnostic witnesses in the checker's temporary function summaries: source
locations and indirect call witnesses where applicable. CompUnit retains only
the edges and analysis flags after validation. Derive the flag alias from `_clz`'s
`$init` field rather than maintaining a duplicate mapping. Locate the body by
its stable function identity; a transient FunNode cache may be useful but does
not define class identity. An unloaded or eliminated FunNode is not proof of
static initialization.

Static class objects for nested struct declarations keep their existing named
TypeStruct identity and defining CompUnit. Their materialized data need no new
runtime graph vertex; an active use that requires the enclosing file initialized
maps to its CompUnit. Preserve initialization-independent static accesses. If a
future language feature adds an independent dynamic initializer inside one file,
then introduce the additional per-class graph identity that feature needs.

`CompUnit.addClassInitDep(code, target)` allocates the collection lazily, deduplicates
edges, and invalidates a compilation-wide validation stamp. Keep self-edges so
they can be diagnosed. Explicit namespace-only parents have no dynamic body;
a missing parent definition must not silently become an empty initializer.

There are two different dataflows. Runtime `$init` values use ordinary memory
aliases and support flag-load/CLInitNode optimization. Dependency discovery
records which initializers may be requested, independently of incoming flag
values and normal return. A dedicated analysis-only memory slice could encode
that second forward dataflow, but the first implementation uses explicit
CompUnit edges plus small function summaries. It does not add a second family
of memory aliases, and it does not infer cycles from runtime `$init` types.

## Discovering and validating dependencies

Use the convention `A -> B` to mean that initializing A may require B completed.
Topological output therefore places prerequisites before dependents. A dynamic
child has a structural dependency on its parent. An active touch of another
class during A's initializer contributes an edge; a passive reference does not.

Insert active touches as CLInitNodes on executable control. After Opto finishes
SCCP, inlining, and cleanup, collect surviving touches and calls. Use final
function-pointer target sets and propagate initialization effects through the
resulting call graph with a worklist. Recursive function SCCs reach a fixed
point of finite dependency sets; they are not themselves initialization cycles.
Indirect calls contribute the union of targets still possible after optimization.

Do not accumulate historical targets or source touches eliminated by Opto.
There is no side table watching Node replacements or type changes. An inlined
helper's surviving CLInitNodes belong to its caller; an uninlined helper's
summary propagates through its surviving calls. Walk executable control
forwards so touches before nonreturning calls and infinite loops are included.
Preserve initializer boundaries until validation; expanding/inlining CLInit
operations early must not erase the class identity needed for checking.

A field value becoming constant does not prove its initializer unnecessary.
CLInitNode survives independently of the load. Dead control may remove both;
a static-initialization proof may remove the obligation. Such proofs must not
assume the very acyclicity being checked.

Acceptance now depends on what Opto proves. Worklist determinism remains an
implementation goal, but preserving every intermediate target set is not the
language rule. Before declaring a graph complete, every surviving required
initialization-time call must have a complete effect summary.

An unknown function-pointer target or native callback is not an empty summary.
Reject such a call during initialization unless an explicit trusted contract
establishes its initialization effects, including that it cannot call back into
unfinished Simple initialization. Ordinary native I/O can use such a contract;
this is independent of whether the call has memory or I/O effects.

Proving a class entirely static is a separate, deterministic classification
step. Static object/reference cycles are legal and need no dynamic ordering.
Dropping a dynamic requirement requires proof that the relevant operation needs
no initialization, not merely that its guard or result optimized away. A real
cycle must not bootstrap its own static-ready proof.

Validate the union of parent edges and active initialization requirements after
the source/import closure is resolved and again after loading or replacing
summaries. Report a cycle with class names and the source/call witnesses for
its edges. An incomplete library may retain explicitly unresolved obligations;
it cannot be certified as a complete runnable program.

Use a stable name order to break ties in diagnostic/topological output. Cache
module-local results, but validate cross-module edges at final composition.
One module's list is not necessarily a contiguous block: `X.A -> Y.B -> X.C`
in prerequisite-first order requires interleaving X and Y. Retain edges as the
authority rather than serializing a local order as a global proof.

The topological order validates permitted completion order; it is not a startup
schedule. If A conditionally uses independent B, B initializes only on the
taken path. A may start before B and finish after it. That is safe precisely
because B cannot directly or indirectly require unfinished A. Do not eagerly
initialize every possible dependency, or a prefix of the topological list.

## Ideal IR and runtime protocol

Introduce an effectful `CLInitNode` with a stable target CompUnit identity:

```text
CLInit(C, ctrl, memory) -> (ctrl, memory)
```

Control is input/output slot 0 and memory slot 1. Store the target class as
semantic metadata or a separate constant input, never in the control/memory
slots. Include target identity in equality, copying, printing, and serialization.
Its normal return guarantees the required initialization of C and its ancestors.
It can execute arbitrary initializer effects, so it is not a pure flag test.

At dynamic class-field reads and writes, required instance creation, and calls
requiring initialized class state, consult parser flow facts first and insert
CLInitNode only if the required initialization is not already established.
A check omitted by a valid dominating initialization proof needs no separate
historical dependency record: its prerequisite is represented on the covering
path. Passive Simple class references remain subject to checks at later field accesses.
Native escape of class references is a separate boundary described below.
Forward references must preserve the pending check until the target is known.

For `ary.copy(args)`, check the class at the field access:

```text
CLInit(ary)
f = load ary.copy
call f(args)
```

The ordinary function header, calling convention, and static function-pointer
field do not change. There is no per-function checked wrapper. Folding the
field load to a constant function pointer does not remove the preceding
initialization effect unless that effect is independently redundant. Once the
checked load yields `f`, it can be passed along and called without repeating
the check for `ary`. Materializing a function address in static data is not a
source-level field access. An initializer's raw body remains private compiler
machinery, not a callable reset operation.

Keep CLInitNode in ideal IR so its peepholes can eliminate or simplify frequent
checks. Lower a remaining node to ordinary Load/If/Call/Region/Phi IR before
machine selection, using a shared class-initialization helper where an
out-of-line guard is useful. The checking code contains no flag store:

```text
CLInit_C(ctrl, memory):
    (ctrl, memory) = CLInit_parent_if_required(ctrl, memory)
    if load(C.$init, memory):
        return (ctrl, memory)
    return call C.<clinit>(ctrl, memory)

C.<clinit>(ctrl, memory):
    memory = store(C.$init, true, memory)
    ... original initializer body ...
    return (ctrl, memory)
```

Entry to `<clinit>` already means that its check took the not-initialized path.
Place the flag store in the initializer, naturally at its entry. This covers
all its normal exits without replicating a store in each CLInitNode expansion.
Inlining the body may of course copy its store, just as it copies other body
instructions. The body must not guard itself through ordinary method-entry
instrumentation.

There is no requirement to schedule the store last. With a validated acyclic
initialization graph and one thread, no other user can observe this class's
flag while its initializer is running. A nonreturning initializer can leave
the bit set because no permitted observer can resume. The physical flag store
is not an early publication point: the class's initialization still completes
only when its body returns. Cycle validation and definite-initialization checks
must remain independent of that store and of optimizations which expose it.

The first runtime contract is single-threaded initialization, with no recovery
and retry after initializer failure. The validated DAG rules out recursive
entry to an unfinished initializer. Thus no runtime `initializing` state is
needed. Concurrent first touches or recoverable initialization failures require
an extended protocol; the flag is not a synchronization mechanism.

## Hidden flag and known completion

Add a hidden boolean field `$init` to the ordinary `<clinit>` class object.
There are few such objects, one per class; no separate allocation, symbol, or
special bit-packing scheme is needed. Use the existing small-integer field
representation and normal struct layout/offset machinery. It is a logical bit,
initially false for a dynamic body, stored true by that body, and inaccessible
to user writes. Separate importers share it through the existing `C.$class`
identity and its canonical field layout.

Allocate/remap these aliases during class discovery, before the current
`freezePublicInterface()` boundary. Hidden flags are not source-visible fields,
but their memory effects must participate in call summaries and memory merging.
Keep CLInitNode's complete public-memory effect until a narrower summary is
proved; tracking only the flag would lose the initializer's field and I/O effects.

There are two different facts:

1. **Static body completion:** `_initStatic` proves that C's own initial contents
   are supplied by data and there are no remaining initializer effects. Its
   `$init` field starts true, and its redundant body/store can disappear.
2. **Completion at a program point:** normal return from CLInitNode establishes
   readiness in that control/memory state. At a permitted external touch, a
   true flag branch proves completion of C's own body; its ancestors must also
   be known complete before concluding that an ordinary active use is safe.

Inside C's own initializer, the flag can already be true while fields are still
being initialized. Keep that lexical initialization context and its field-read
checks; a folded true flag does not grant access to unfinished fields. In
particular, it must not make a callback or cyclic initialization dependency
legal. Record and validate those dependencies before suppressing their checks.

Neither fact means mutable fields remain constant. Do not refine their declared
types to their static initial values. Also, a static-only child with a dynamic
ancestor is not globally ready for every active use: ordinary ensures still
establish the ancestor. Passive references and proven initialization-independent
static accesses need no such ensure, including during the parent's initializer.

## Parser flow and ideal peepholes

CLInitNode will occur frequently. Avoid constructing obvious duplicates in the
parser, then use normal ideal optimization as more types, call effects, and
control-flow facts become available. A separate late must-analysis is optional,
not a prerequisite for removing straightforward redundant checks.

Carry known-initialized class identities with parser control flow, for example
as a BitSet in ScopeNode keyed by the existing remapped `$init` alias IDs. These
are path facts, not mutable global properties of CompUnit or its declared
TypeStruct:

- On an active touch, consult the facts. If C and its required ancestors are
  known ready, omit the CLInitNode. Otherwise insert the obligation in the IR;
  post-Opto analysis collects surviving requirements.
- Normal return from CLInit(C) adds C and its required ancestors. Do not add
  every possible dependency: a conditional initializer may never touch one.
- Copy facts on a branch and intersect them at a reachable merge. An abrupt
  or nonreturning path contributes no completion facts to a reachable merge.
- A loop's first iteration has only entry-path guarantees. Body/backedge facts
  alone cannot suppress its first check or establish readiness after a
  potentially zero-trip loop. Preserve pre-loop facts and refine only with a
  proof covering all relevant incoming paths.
- A call cannot reset established facts. Add new ones only from a proven normal
  return summary; do not seed arbitrary exported function entries with facts
  learned at one caller. Checks at field-access sites establish their facts.

Give CLInitNode ideal peepholes for these cases:

| Proof | Rewrite |
|---|---|
| Fully static class / flag known true at a permitted touch | Remove the class's own check and call; retain any still-required ancestor initialization |
| Flag known false at this memory/control point | Replace the test with an unconditional initializer call; retain the call's effects and results |
| Earlier normally returning CLInit of the same class dominates | Forward control/memory through the redundant node |
| Dominating true flag test covers this path | Remove the covered check, as with redundant if-test reasoning |
| All reachable arms of a merge establish completion | Fold the repeated check after the merge |

Known false must come from the memory reaching the node, not simply the false
byte in the original object image. Known true on one branch is not known true
after a merge with an unchecked branch. Match stable class identity and required
ancestors, not just a pointer-shaped value or a similar field name.

Use the existing dependency/wakeup mechanism when a node consults a flag value,
memory state, or another node's proof, so later refinement revisits the check.
Retain control and whole-memory ordering through the conditional call: removing
a check must preserve preceding initialization effects, and hoisting a check
onto an untaken path would violate laziness. In particular, CLInitNode is not
freely speculatable or an ordinary pure expression suitable for unrestricted GVN.

Parser omission must have an enduring IR justification: earlier effectful
CLInit/control edges, a proven caller precondition, or static metadata.
When bodies are inlined or deserialized, those proofs travel with the IR;
transient parser facts alone cannot justify an unchecked exported body. A
shared declared type must not be mutated to encode a path-local true flag.

## Static data classification

A class is fully static only when every required initial value is representable
in data/relocations and no runtime effect remains: no I/O, observable allocation,
potential failure, or required dynamic dependency. A missing/dead body alone
does not prove this, and startup arguments are not static inputs.

Ignore the compiler-inserted `$init = true` bookkeeping store when checking
whether the original initializer is entirely static. Otherwise every inserted
store would prevent this optimization. Once the proof succeeds, emit `$init`
as true in the object image and eliminate the runtime store with the body.

Mutable fields may have static initial values. Folding their initializing
stores must still preserve reads and escapes during the initializer. Merely
knowing the final value and that the body runs once is insufficient. Preserve
object identity and canonical layout separately from initial contents, and do
not collapse distinct class objects with equal bytes.

This extends the current StaticData machinery; it does not change pointer
widths or require class objects to become read-only. An object with a dynamic
`$init` field stays writable even if its source fields are final. A fully static
object whose source fields are also immutable can later use read-only storage,
with its hidden field already true and no runtime writes to it.

## Serialization and final composition

Add a class-initialization directory to the `.simple` data that can be read
without materializing every function body. Serialize:

- Stable class and parent names, defining-unit identity, and initializer symbols.
- Static/dynamic/unknown classification and summary-completeness information.
- Direct initialization requirements and function-effect summaries, including
  unresolved obligations and useful cycle-diagnostic locations.
- The hidden `$init` field's type, alias identity, initial image value, and
  canonical layout, alongside the other class fields. Remap aliases using file
  identity and local order rather than copying process-local numbers.
- CLInitNode operations and the initializer's own flag Store in ideal IR,
  including all semantic fields needed to rebuild them.

Do not serialize transient node references, a cached global topo index, or the
runtime value of a dynamic flag as an unconditional type fact. A definition
and its imported declarations must resolve to the same owning CompUnit and
canonical class/flag identity.

On load, register identities first, resolve parent/dependency edges second,
then resolve function summaries and IR. Check conflicting owners, missing
definitions, alias remapping, summary completeness, and combined cycles.
Loaded bodies must agree with their summaries; report newly found requirements
and revalidate rather than trusting an earlier module-local topo sort.

Preserve these summaries before `unlinkImports()` removes imported bodies.
Serialize ideal CLInitNode operations before lowering to machine code. Native
objects retain checks at their class-field access sites even when a particular
importing compiler inlines and removes its local checks. Ordinary function
symbols and function-pointer constants are not replaced by checked wrappers.

Do not serialize a parser's transient BitSet as unconditional entry knowledge.
Its local alias IDs can change on reload; retained checks, control flow, and
explicit entry contracts must support any omitted checks. Recompute flow facts
for loaded graphs and verify hidden-field ownership/layout and that the flag
store belongs to `<clinit>`, not to each checking caller.

Final validation needs a Simple-aware executable/library composition step.
An ordinary native linker cannot validate dependencies stored only in `.simple`.
Enumerate the complete intended Simple object set, including exported entry
points that native code could invoke; loading only the source-discovered
references is insufficient. Validate that metadata set before handing objects
to GCC/the native linker. Dynamically loading additional Simple modules is
outside this first protocol and would require validation before activation.

Keep the current unversioned `C0DE` convention and require rebuilding objects
when the serialized layout changes. Retain/validate summaries for the exact
objects being linked, not cached descriptions of older builds.

## Program entry and other boundaries

Program startup is another CLInitNode on the starting class. In a fresh runtime
image with a dynamic starting class, its `$init` is known false, so the check
folds to the initial `<clinit>` call. The starting class sets its own bit just
like every other class; there is no adapter-specific flag store or protocol.
A proven fully static starting class needs no dynamic initialization call.

The current file `<clinit>` can receive `arg` and return a program/test result.
Preserve those arguments and the call result on the known-first-call startup
path. Ordinary class-use checks need only control and memory; they do not need
to cache or invent a result for the skip path. For fully static startup, the
program result must also be available as a constant or retained computation;
static object contents alone do not prove the entry computation unnecessary.
Existing tests executing file code with different arguments use independent
runtime images, not a reset of an already initialized class. The startup fact
must not be reused for an arbitrary native entry into a live image.

Own-initializer helper calls and construction of a special instance during
class initialization need an explicit definite-initialization proof. Do not
carry forward the old module notes' unrestricted “golden instance” exception.
Initially reject cases that would publish or inspect unfinished class state.

## Native handoff

Native callers are not required to understand or explicitly run Simple class
initialization. The compiler/runtime must establish initialization before
exposing a supported value, or reject an unsupported handoff.

For the first implementation, reject passing a class object or raw `<clinit>`
function to native code, including through a class-to-address conversion.
Do not confuse these with an ordinary function pointer obtained by a checked
field access: loading `ary.copy` already initializes `ary`, so that value does
not need a new initialization wrapper when handed to native code, wherever the
ordinary FFI supports its signature. Initialization-time callbacks still need
the effect analysis described above; an uninitialized class cannot be hidden
behind a callback to evade cycle validation.

Later, support a selected class value at the handoff point:

```text
X = rand ? B : C               // Passive class references
CLInit(selected class X)       // Initialize the actual value being passed
libc.native(X)
```

Validate the initialization graph for all possible targets, B and C, including
their parents and transitive requirements, regardless of the selected value.
At runtime initialize only the selected target before the native call. This
can lower to checks on the corresponding control-flow arms, or dispatch on a
known target identity. The plan's initial CLInitNode has a fixed class identity;
this dynamic selection is a later extension, not an assumed existing facility.

Initializing every possible target before handoff is an alternative policy,
but it runs effects for the unselected class. Prefer the selected-target form
to retain the rule that untouched classes run no code. Unknown target sets or
native traversal to further uninitialized class objects require additional
analysis; initializing just X is not a blanket proof for everything reachable
through X. Until supported, reject the handoff rather than transferring that
responsibility to native code.

A native call directly to an exported symbol, without a preceding Simple
handoff, likewise needs compiler/runtime initialization support. Its eventual
export/startup mechanism is deferred; it must not require the native caller to
invoke `<clinit>`. Existing native-linking tests are not evidence that this
future initialization guarantee is already implemented.

## Implementation sequence

Implement in Chapter 25 only, with the following reviewable checkpoints. Keep
existing source/native entry conventions and pointer widths. Each checkpoint
must build; do not claim the runtime guarantee until the executable protocol,
serialization, and final-composition validation are all present.

### 1. CompUnit dependencies and cycle checking

Implemented for review (2026-10-10). Files: `CompUnit.java`, `ParseAll.java`,
`Parser.java`, `ClassInitDependencies.java`, `CLInitNode.java`, `CallEndNode.java`,
`Opto.java`, and `CodeGen.java`. Generic `Node` needs no changes.

- CompUnit holds direct `_classInitDeps`, `_initComplete`,
  and reserved `_initStatic`. Existing file-discovery `_deps` is unchanged.
  Diagnostic witnesses remain local to the dependency check.
- Parser inserts a control-flow CLInitNode for an active class-field touch or
  a receiver whose class is not yet resolved. Ordinary instance receivers
  fold the obligation away when resolved. Passive references and direct
  own-initializer field accesses do not insert a check.
- This checkpoint's ideal node has control at input 0 and receiver at input 1;
  it neither consumes nor produces memory yet. The full runtime form described
  above will add memory at slot 1 and move the receiver to slot 2. No hidden
  flag or runtime initialization call is generated in this checkpoint.
- After Opto, the collector walks live control forwards in each surviving
  function, reads final call-target sets, and propagates sparse may-touch
  summaries. Ordinary helper inlining naturally relocates its checks into the
  caller. Class initializers are not inlined before validation.
- Reachable nonreturning calls retain a conservative CallEnd during dataflow.
  Calls left out-of-line retain their continuations, so otherwise unreachable
  touches after them can contribute initialization dependencies. This extra
  ordering is accepted; users can remove such unreachable code.
  Inlining discovers bodies forwards from their entry, including non-exiting
  paths, and explicitly retains the Return node even when disconnected from
  executable control. Nonreturning functions can therefore inline normally.
- TypeCheck builds fresh CompUnit requirements from those summaries and parent
  edges. It rejects cycles with source/call witnesses and computes a stable
  prerequisite-first order. No source-history or Node watcher table is kept.
- Following validation, the temporary CLInit obligations are erased for the
  existing backend. Executable lowering and serialized obligations remain
  later checkpoints. The full lazy-initialization runtime guarantee is not
  implemented by this batch.

Imported bodies, native calls, and unresolved targets leave `_initComplete`
false; the returned order covers known edges and is not a completeness
certificate. `_initStatic` remains false until static classification is added.

Validation covers direct, helper-mediated, forward, conditional, indirect,
sibling, and parent/child cycles; legal ordinary recursion and passive
references; unknown native/imported effects; dependencies eliminated by SCCP;
unused helpers; discarded field values; and effects before versus after a
nonreturning call. Cycle and optimized-target cases run with three worklist
seeds. A bounded Eval2 run verifies that a retained nonreturning call executes
rather than falling through; its x86, ARM, and RISC-V encoding checks also pass.
All 492 tests in the parallel Chapter 25 Make suite pass. The encoding checks
were added afterward and pass in the focused suite. Two existing RISC-V spill
expectations change from 9 to 7; execution and allocation checks pass.
Generated fixtures are under
`chapter25/build/init-deps/`; full-suite logs are under `build/init-deps-opto*.log`.

### 2. Serializable flags and dependency summaries

Files: `Parser.java`, `Serialize.java`, `ElfReader.java`, `GlobalBits.java`,
`ParseAll.java`, and `StaticData.java`.

- Add hidden `$init` fields with the normal field/alias/layout mechanism before
  `freezePublicInterface()`. Declared flag type is boolean; initial image value
  is false unless static initialization has been proved. Do not make a dynamic
  flag's declared type the constant false.
- Serialize per-unit initialization metadata and function may-touch summaries
  in `.simple`, early enough for graph validation without loading every body.
  Preserve stable owner/alias/function identities and diagnostic witnesses.
- Register imported units first, resolve/remap edges and hidden fields second,
  then validate the combined graph. Neither a module-local topological order
  nor a summary from an older object file is a final-link proof.
- Keep metadata for fully static or body-eliminated units. Rebuild objects
  under the existing unversioned `C0DE` convention; add no compatibility layer.

Checkpoint: a two-module cycle rejected from source is also rejected after
serialization; an acyclic diamond resolves to one shared class and flag.
Two local topo orders can be merged even when their modules must interleave.

### 3. CLInitNode and the executable protocol

Files: new `node/CLInitNode.java`; `Node.java`, `Parser.java`, `CodeGen.java`,
`Serialize.java`, `Eval2.java`, and the printer adapters as needed.

- Add an effectful, serializable CLInitNode targeting the resolved CompUnit,
  with control/memory inputs and outputs in slots 0/1. Pending targets remain
  explicit until resolved. Its type computation is conservative about the
  initializer's whole memory effects; final normal output guarantees completion.
- Integrate its possible call with existing Call/CallEnd linking, argument and
  return propagation, and body liveness. A reference to CompUnit metadata alone
  must not let SCCP discard the initializer as uncalled. Reuse the call machinery
  or a generated guard helper, rather than implementing a second independent
  calling convention in the node. Test an out-of-line imported initializer as
  well as an inlined one.
- Insert checks at class-field accesses, including function-pointer loads;
  use the same operation for required instance creation and startup. Do not
  instrument ordinary function headers or rewrite their function-pointer data.
- Insert `$init = true` once in each dynamic `<clinit>` body, naturally at entry.
  The checking node performs the test/conditional call and contains no store.
  A false startup test becomes a direct call preserving `arg` and the result.
- Give Eval2 the same shared-object/flag behavior. Lower checks to existing
  control, memory, and call IR for machine compilation; use an out-of-line
  helper if necessary rather than adding target-specific machine operations.
- Reject the presently unsupported native class/raw-initializer handoffs. Keep
  ordinary checked-field function pointers unchanged. Do not add an explicit
  initialization obligation to C drivers or other native callers. Direct native
  entry into a dynamic class remains outside the new guarantee until a
  compiler-managed startup/export mechanism exists.

Checkpoint: sequential touches run the body once; parent effects precede child
initialization; an untaken branch runs no child code. Verify result/argument
preservation and flag sharing in Eval2, ARM/RISC emulation, and native execution
through Simple startup. Existing direct native tests must not be mistaken for
coverage of the deferred dynamic-export case.

### 4. Parser flow and ideal simplification

Files: `ScopeNode.java`, `Parser.java`, `CLInitNode.java`, and existing
control/memory lookup helpers.

- Add path-local known-initialized facts to ScopeNode. Copy in `dup`, intersect
  reachable predecessors in merges, and respect entry/backedge distinctions in
  loop completion, break, continue, and return handling. The established
  control/memory path must justify each check omitted by the parser.
- Implement constant-true, constant-false, preceding dominating CLInit, and
  dominating true-test peepholes. Use dependencies to revisit proofs after SCCP
  or inlining sharpens values. Keep remaining parent requirements when only a
  child's own body is static.
- Preserve initialization effects when folding field loads into function
  constants. Never hoist a check onto a path that previously did not touch the
  class. Do not confuse an early true flag inside `<clinit>` with permission
  to read that initializer's unfinished fields.
- Add fully-static classification using the initializer body and its effects,
  excluding the generated flag store from the test. Emit a true initial flag
  only after proof; remove the now-redundant body/store/checks. Extend mutable
  constant-field lowering only where observations during initialization remain
  correct. Do not use classification to erase evidence of a genuine cycle.

Checkpoint: distinguish a dominating check from a one-arm-only check, a
zero-trip loop from a definitely executed touch, and a static child from an
unfinished dynamic parent. Check that early flag folding cannot hide a cycle.

### 5. Phase ordering and final composition

Files: `CodeGen.java`, `ParseAll.java`, `Serialize.java`, `ElfReader.java`,
`Main.java`, and the native linking path in `TestC.java` as appropriate.

The current driver runs parse, Iter, Opto, TypeCheck, LoopTree,
`unlinkImports`, serialization, then selection and scheduling. Add explicit
initialization-analysis boundaries without relying on a particular peephole
worklist order:

1. During parse/load, register identities and represent active initialization
   obligations in ideal IR. Ordinary Opto resolves targets and removes dead
   control; do not collect a historical union of temporary types or calls.
2. After Opto finishes, collect surviving CLInitNodes and calls, complete
   function summaries, classify provably static work without circular
   assumptions, and validate the graph. Any remaining unknown required
   initialization effects prevent certification.
3. Before `unlinkImports`, retain imported summaries needed for validation and
   serialization. Serialize ideal CLInitNodes and their proof-bearing control
   and memory edges, plus the independent requirements.
4. Lower remaining CLInitNodes before final loop-tree construction and machine
   selection. Because lowering introduces branches/calls, rebuild affected
   control information and run ordinary ideal/type validation on that graph.
   Move the final LoopTree step if needed rather than using a stale tree from
   before expansion. Keep the serialized representation ideal and reloadable.
   Ensure the generated call participates in call linking before the final
   unlink step; imported bodies may remain represented by symbolic targets.
5. Before native executable composition, load metadata for the exact full
   Simple object set being linked, merge requirements, and validate again.
   A plain GCC/ELF link is not an initialization-graph checker. Do the same
   validation for a complete in-memory image.

If optimization or loading discovers another target, mark summaries dirty and
rerun validation before emission. No runtime `initializing` state is introduced;
compile-time DFS bookkeeping is independent of the single runtime flag.

Checkpoint: source, imported ideal IR, and final linked metadata agree on
acceptance/rejection for equivalent optimized graphs. Exercise different
worklist seeds and object-discovery orders to check determinism; eliminated
branches and targets must not leave stale dependencies. Missing required
metadata fails diagnostically.

### Validation and scope

Use a small set of focused fixtures covering several boundaries each, plus the
existing full Chapter 25 Make suites. Include effects before a nonreturning
call so dependency checking cannot accidentally depend on returned memory.
Exercise duplicate touches, conditional cycles, native-handoff diagnostics,
serialization alias remapping, class/flag ownership, and normal startup results.
Run the fixed allocation cohorts only after runtime semantics stabilize; graph
and spill changes are expected, and any new golden counts must follow execution
and register-correctness checks. Keep Jig tests ignored.

Do not backport this feature, change pointer widths, introduce concurrent or
recoverable initialization, add dynamic module loading, or solve the general
native-export policy in these checkpoints. The extra analysis-only alias slice
remains an alternative representation if explicit summaries prove awkward;
implementing both would add work without improving the initial guarantee.

## Module goals retained from the old notes

Keep one-line programs easy to compile/run, filesystem-based nested namespaces,
leading-underscore privacy, separate compilation without source include files,
and ordinary object-file/native-linker interoperability. Classes may be declared
within a file or supplied by nested files. Source and object builds must have
the same initialization semantics. The current CLI is documented in the
[chapter README](README.md); historical command examples and conflicting
initializer-order proposals are superseded by this design.
