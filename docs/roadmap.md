# Compiler roadmap

English | [日本語](roadmap.ja.md)

Open compiler work and language proposals, reviewed against the current
snapshots on 2026-11-08. These are future changes, not promises about the
language implemented by the tutorial. Language changes still need their own
design decisions and chapter boundaries.

## Contents

- [Compilation and optimization](#compilation-and-optimization) — diagnostics, analysis costs, builds, loading, and code quality.
  - [General cleanup](#general-cleanup) - Some general quality-of-life suggestions
  - [Phase ordering and incremental analysis](#phase-ordering-and-incremental-analysis) — scheduling costs, shared escape scans, and late identity precision.
  - [Reliable incremental builds](#reliable-incremental-builds) — input identities, compatibility, and relinking.
  - [Function-level loading](#function-level-loading-of-library-ir) — load individual library bodies.
  - [Deterministic inlining](#deterministic-inlining) — stable choices using optimized body sizes.
  - [CFG frequency estimates](#cfg-frequency-estimates) — guide scheduling and layout.
  - [Loop rotation](#loop-rotation) — bottom-tested loops with correct entry and exit behavior.
  - [Integer instruction widths](#integer-instruction-widths) — local narrowing patterns, 32-bit instructions, and later demanded-bit analysis.
  - [Per-function calling conventions](#per-function-calling-conventions) — precise clobbers, custom interfaces, and later joint allocation.
  - [Constant instance-field storage](#constant-instance-field-storage) — omit slots whose values are constant across instances.
- [Language boundaries](#language-boundaries) — native argument representation, source access rules, and symbol visibility.
  - [C byte pointers](#c-byte-pointers) — distinct native pointers with byte loads/stores and C-owned memory.
  - [Source privacy](#source-privacy) — enforce private access through imports and optimization.
  - [ELF visibility](#elf-visibility-and-unknown-callers) — separate linker exports from unknown-caller reachability.
- [Initialization and runtime](#initialization-and-runtime) — entry arguments, static initialization, and pointer storage.
  - [Entry arguments](#entry-arguments) — pass argument arrays to class initializers.
  - [Pre-initialize classes](#pre-initialize-classes-from-elf-data) — replace provably static initialization with ELF data.
  - [Compact arenas](#compact-code-and-data-arenas) — four-byte references and explicit native pointer boundaries.
- [Longer-term proposals](#longer-term-language-and-library-proposals) — deferred language and library designs.
  - [Partial-initialization contracts](#inferred-partial-initialization-contracts--deferred) — inferred requirements, nested guarantees, and imported contracts.
  - [Automatic memory management](#automatic-memory-management) — ownership, sharing, cycles, and native retention.
  - [A graphical game](#a-small-graphical-game) — exercise graphics, keyboard, and audio APIs.
  - [Trailing arrays](#structs-with-trailing-arrays) — inline elements after named fields.
  - [Array initialization](#array-initialization) — generate each element during allocation.
  - [String libraries](#string-libraries) — ownership, Unicode, and additional representations beyond byte buffers.
  - [Struct values](#struct-values) — inline storage, copying, and by-value calls.

## Compilation and optimization

### General cleanup

Keep the recursive-descent path aligned with the grammar. Review moving `TYPES`
to `CodeGen`.

Consider whether an allocation-free use of try-with-resources makes keep/unkeep
pairing clearer; this remains an experiment, not a new resource-management
convention.



### Phase ordering and incremental analysis

Remaining work:

- Review the remaining node-ID bitset costs in Eval2 scheduling. Scratch
  arrays now retain capacity and reset only touched entries, but bitsets
  still span previously allocated node IDs. Scheduling a newly loaded
  object should cost only for that graph and any affected existing nodes.
- Make `GlobalEscapeNode`'s shared call/summary scan incremental. Compilation
  unit Stops now reuse its result, but updating that result still scans all
  calls and unloaded summaries. Handle contributors changing or disappearing,
  rather than only accumulating unions. The
  [outside-world escape flow](chapter28a/README.md#escaping-or-the-startstop-cycle) already shares
  the result; the remaining cost is recomputing it.
- Improve precision for identities discovered after Opto's foreign seed is
  established. They currently remain in the cofinite foreign tail. Preserve
  monotone propagation and imported callback behavior when refining this bound.

Whole-program TypeCheck is acceptable for now; incremental checking is an
optional later improvement. Expensive assertion walks are outside this
performance work.

### Reliable incremental builds

Chapter 28b now checks the reachable source-backed inputs before reusing saved
IR, including transitive dependencies and missing inputs.
The remaining work is a build driver with recorded input identities, beyond
those conservative timestamp checks. An unchanged public signature does not
establish compatibility: optimization imports bodies and constants too.

Record the actual resolved input paths and versions, compiler/serialization
versions, target, ABI, and relevant options. Content hashes can cover preserved
timestamps and changed library search paths. Distinguish rebuildable source
from incompatible source-free libraries; their installation timestamps alone
cannot validate a set of objects compiled against one another.

Native separate compilation should also detect a library changing between
reading its types and linking the executable. One possible mechanism is a
SHA-256-named symbol that callers require and the matching library defines.
This remains future link-time validation; Eval2 assumes a coherent build.

An unchanged build should do no compilation or linking. Publish outputs only
after successful compilation, and relink when an object or other link input
changes. Start with a small build driver and conservative rebuilding of every
dependent; finer interface/body fingerprints can follow. The
[incremental-build design](../skills/incremental-builds.md) records the input
ownership rules and validation cases.

### Function-level loading of library IR

Chapter 28b emits one object per compilation unit and reads declarations and
`SummaryNode` information without loading bodies. Optimization can request an
object's IR for inlining; Eval2 materializes bodies on executed calls and
assumes a coherent build. Initialization checks reject possible cycles using
the available graph and summaries, without loading more bodies to disprove
them. A requested object still reconstructs all of its functions.

Further selectivity would require a function-body index within each object
and narrower metadata discovery. Preserve type identity, initialization rules,
and imported escape summaries. Loading, initializing, and emitting a class
remain separate decisions. This optimization is independent of making
incremental rebuilding correct.

### Deterministic inlining

The current inliner has a bounded exception for small initializer boilerplate,
but still uses parse-size estimates and worklist order. After shrinking
rewrites converge, choose one growing inline using current body sizes and a
deterministic ranking. Cache sizes per convergence round and use stable
call-site provenance to break ties, rather than newly allocated node IDs.
Avoid repeated whole-body scans and per-node dependencies while a large
function gradually shrinks.

### CFG frequency estimates

After lowering and loop-tree construction, estimate block frequencies for GCM
and code layout. Treat initialization and known-failure paths as rare; use
bounded loop-trip estimates and branch probabilities when no profile is
available. Keep estimates consistent with control flow and shared between
scheduling and layout. These are backend estimates, not ideal-IR types.

### Loop rotation

Consider lowering `for` loops with a zero-trip entry test and a bottom-tested
loop, or adding partial peeling. Preserve condition side effects, loop-local
scope, and the destinations of `break` and `continue`. The current parser
constructs a loop with its test at the head.

### Integer instruction widths

Keep the Chapter 30 language rule: `int` aliases `i32`, but inferred values
and intermediate arithmetic retain their existing rules. Two `int` operands
can produce a 33-bit sum; narrowing on assignment is a separate operation.

Start with local instruction-selection improvements, without a new analysis:

- Recognize `(x<<32)>>32` as sign-extension instead of two shifts.
- Add an operand-width choice to integer machine operations and the shared
  encoder. On x86, dropping REX.W can remove a prefix byte when no extended
  register needs REX. The encoder already has `rexF(..., wide, ...)`, but
  register arithmetic currently forces wide operations.
- Use range proofs for suitable 32-bit arithmetic with a result in `u32`,
  and 32-bit signed comparisons between operands proven to fit `i32`.
- Combine arithmetic with explicit narrowing: a narrowed add can use a
  32-bit add followed by sign-extension. Bypass input extensions where only
  low bits are consumed, preserving shared producers' other wide uses.

An x86 32-bit result zeroes the upper half; negative signed results still
need sign-extension before a full-width consumer. Keep shifts and division
under separate rules: operand width changes shift-count masking and which
high bits can flow down, and narrow signed division introduces the
`INT_MIN / -1` overflow case. Do not infer signed representability solely
from `TypeInteger.log_size()`; use actual range bounds.

Longer term, distinguish computed bits from demanded bits. `TypeInteger`
already provides ranges and a derived possible-bits mask. Tracking which
bits consumers observe could keep arithmetic chains and loops narrow and
extend only at wide consumers, but requires backwards propagation through
shared uses and Phis. This is a separate analysis proposal, not part of the
local selection work. Narrow operations still occupy whole registers.

### Per-function calling conventions

Chapter 30 now allocates functions independently. Give known direct-call targets
their own machine contracts: argument/return locations, clobbered registers,
stack argument layout/alignment, and integer-extension requirements. Start
with precise clobber summaries under the existing convention, then consider
custom argument/return locations. Allocate callees before callers and freeze
each contract; caller preferences can guide choices without forcing every
call site to agree on its preferred registers.

Preserve machine-call relationships before Unlink discards the ideal call
graph. Replace compilation-wide parameter, return, preservation, and call-kill
masks with contract lookups. Keep C entry points and callbacks on the C ABI,
using wrappers if an internal custom entry is worthwhile. Source privacy
alone does not establish that all calls are known.

Indirect calls need a convention shared by all possible targets, entry
thunks, or a richer function-pointer representation. Public Simple functions
could export their selected contract in target-specific metadata after
allocation; ideal IR is currently serialized before allocation. A changed
contract requires rebuilding callers or rejecting stale objects even when
the source type is unchanged.

Joint caller/callee coloring is a larger, separate proposal. Model argument
and result transfers as preferences, retaining moves when needed; current
LRGs and spill slots belong to individual functions. Values live across a
call must survive the callee's transitive clobbers. Recursive activations
require saves/spills and cannot be represented just by merging static live
ranges. Initially use fixed conventions for recursive groups. If interfaces
are later renegotiated, budget retries by function or recursive group, not
one global round counter shared by unrelated functions.

### Constant instance-field storage

When a final instance field has the same constant value in every instance,
fold accesses or store the value on the class object and omit the instance
slot. Preserve field-access semantics and public function reachability:
externally accessible function fields remain callable regardless of storage.
Existing static images for class constants do not implement this general
instance-layout optimization.

## Language boundaries

### C byte pointers

Introduce an eight-byte C pointer type, initially pointing only to bytes, with
indexed byte Loads and Stores, null checks, and defined offset operations.
Track whether the storage is C-managed or Simple-managed separately from
pointer width and address range. The prospective low-4-GiB Simple heap does not
make low-address C allocations Simple-owned; high-address buffers require the
full-width C pointer. A low-address C buffer might admit a compact reference,
but only with compatible layout and retained C ownership. Revisit whether that
is useful when designing the heap and automatic memory management.

Allow Simple buffers to be borrowed by C under a documented promise that C
does not retain the pointer. Initially this lifetime promise is not enforced.
Returning a pointer must not silently transfer ownership, and Simple memory
management must not free or traverse C-owned storage.

Use normal memory SSA. C views of Simple buffers can alias Simple Loads and
Stores, so representation alone cannot select a disjoint native-memory alias
class. Native calls must expose effects on borrowed Simple memory as well as
C memory. Lengths and writable access need API contracts; a raw C pointer
carries no Simple array header or length.

This would let the runtime scan and copy argument bytes in Simple. Access to
`argv[i]` would still need the small C helper: `char **` requires pointer loads,
which are outside the initial byte-only scope.

Replace existing I/O's `i64` address convention with explicit native views of
Simple buffers, preserving their ownership and the non-retention contract.
A later Simple `C` struct facility could supply C-compatible field layout for
libc interfaces. Such structs have no Simple constructor/destructor and do not
intermingle with the managed Simple object graph; specify native layout and
ownership independently of ordinary Simple structs.

### Source privacy

Underscores already select the public interface, but source access to another
class's private fields is not rejected. Revisit source enforcement and its
preservation across imports. This language change still needs review; it is not
a correction to impose on earlier chapters.

- A field or method beginning with `_` is accessible only from its declaring
  class, including constructors and methods. Enclosing, nested, and sibling
  classes gain no special access. Same-class code may access another instance.
- A private class name is available in its declaring scope and inside the
  class itself. Naming `App._Cache` must not grant access to `_Cache._data`.
  Dotted lookup cannot bypass access rules, and private ancestors keep their
  descendants out of the public interface. Distinguish naming a private class
  from using a public value returned by a factory.
- Reads, writes, updates, method references, type names, and class-value
  references must follow the same rule. Preserve source owner and location
  through unresolved receiver types, inlining, and imports. Folding away a
  Load or an unused result must not erase an illegal source access.
- Native declarations follow the same rules. Public wrappers may deliberately
  expose private bindings; `sys` has no special exemption.

The resizable-array helpers call `libc._exit` directly, and `sys.io.read`
calls `aryu8._grow`. Keep the native exit binding private, add a public `exit`
wrapper in `sys/libc.smp`, and define a public capacity-reservation method for
the buffer. Bitset and array helpers accessing their own private fields remain
legal. String spill fixtures with helpers outside `_String` accessing
`_hashCode` need explicit adaptation; do not grant file-wide access merely to
keep those fixtures unchanged. Validate native execution and imported source
ownership alongside the access diagnostics.

### ELF visibility and unknown callers

Escape analysis tracks functions reachable by unknown callers, while the ELF
writer exports retained named functions owned by the compilation unit. Decide
which symbols should be public, local, or hidden across separate compilations.
Keep source privacy, ownership for emission, and unknown-caller edges distinct;
keeping a body for linking must not imply arbitrary callers can reach it.

## Initialization and runtime

### Entry arguments

Replace the integer class-initializer argument with an argument array. The
selected entry class receives a non-null array, empty when there are no
arguments; other class initializers receive null. Coordinate the parser,
entry signature, runtime driver, and separately compiled interfaces.

The native runtime already adapts process arguments for an explicit
`simple_main` function. That does not implement argument arrays for class
initializers.

### Pre-initialize classes from ELF data

Prove when a class's entire initialization can be represented by static data
and relocations, without remaining runtime effects. Emit those contents and
start its `$init` flag as true. Carry that fact through separate compilation
so importing compilers can remove checks too.

Constant final fields already have static images; that alone does not prove
the whole class can be pre-initialized. I/O and argument-dependent work still
require execution. Preserve dynamic parent requirements, and do not let cyclic
dependencies justify their own pre-initialization. Static reference cycles
can use relocations without requiring a dynamic initialization cycle.

Mutable fields may have static initial contents without becoming constants.
For a wholly immutable, pre-initialized class, eliminate the mutable `$init`
state as well as the checks so the class image can reside in ROM. Native
function thunks already preserve static initialization of function fields.

### Compact code and data arenas

Use four-byte Simple references into bounded code and data arenas. The arenas
need not overlap or live near zero; choose absolute addresses or offsets from
an arena base when implementing placement and indirect calls. Native function
thunks can occupy the code arena while native libraries live elsewhere.
Currently function pointers occupy four bytes and use low-address thunks;
ordinary data pointers still occupy eight bytes.  See [Automatic memory
management](#automatic-memory-management).

Give full-width native pointers separate types and foreign lifetime contracts.
Opaque device addresses may need to pass through without CPU dereference.
Automatic Simple object lifetimes without a GC still need a design for
ownership, borrowing, cycles, and native retention. Compact storage alone
does not establish those rules. Preserve static class images and explicit
native boundary conversions when choosing the representation.

Defer the four-byte data-pointer switch to a later chapter alongside memory
management, which will also establish the allocator and native ownership rules.

## Longer-term language and library proposals

These are language-design alternatives. The sketches below are not executable
examples of the current Simple grammar.

### Inferred partial-initialization contracts — deferred

Chapter 29 already permits local reads of proven initialized fields and limited
retention of partial references through normal forward type flow. The deferred
extension is inferred field requirements, nested guarantees, and contracts for
separately compiled callees, while reserving final-field writes to the original
constructor. Tracing requirements through aliases, nested fields, forwarding,
recursive calls, and returned/stored references adds more infrastructure than
is justified for this feature alone.

Revisit alongside a general backwards/liveness analysis, which may also serve
future memory management. The normal-type contract proposal and its unresolved
details are recorded in the
[construction notes](chapter29/docs/construction-groups.md#deferred-field-aware-contracts-2026-11-07).
Keep the separate-compilation restriction on `^` functions meanwhile.

### Automatic memory management

Explore automatic memory reclamation with few explicit lifetime annotations,
using Rust and [Goose](https://github.com/aardappel/goose/blob/master/docs/tutorial.md)
as design references. Decide ownership, borrowing, sharing, cycles, and native
retention together with the existing object and array model. This is a language
design candidate, independent of compact pointer storage.

### Structs with trailing arrays

Allow named fields followed by an array length and inline element storage:

```simple
struct vecInt {
    u32 size; // Used elements
    u32 #;    // Capacity
    int [];  // Trailing elements in the same allocation
};
```

The final field would be named `[]`, with an unsigned length field `#`
immediately before it. Element types could include references to the same
struct. Current arrays have a fixed `u32` length and a special two-field
layout; supporting arbitrary leading fields requires parser and layout work.

Consider `u8`, `u16`, `u32`, or `u64` length fields to reduce small-array
overhead. Arrays with different length representations would be distinct
types, with explicit conversions and capacity checks. The spelling and
alignment rules remain to be decided.

Growing such an object requires a replacement allocation. An extra level of
indirection, as in a growable-buffer wrapper, instead preserves the wrapper's
identity. Specify ownership before treating a growing call as consuming or
freeing its receiver.

### Array initialization

Allow an element-producing function at allocation, for example
`new u8[buffer#, { u32 idx -> buffer[idx] }]`. This would support final arrays
and fully initialized non-null reference arrays. Define when each element is
evaluated and when the array can be published. Existing zero-filled array
allocation does not provide this constructor operation.

### String libraries

`sys.aryu8` already provides a growable buffer wrapper; `sys.text` formats into
it and copies the result into an immutable byte array. Extend these foundations
with explicit ownership contracts and UTF-8 decoding. Consider:

- A growable string with inline bytes and cached metadata, returning a
  potentially new receiver when capacity increases.
- Immutable-string ownership and sharing beyond the existing formatting result.
  Read-only access alone does not establish exclusive ownership.
- Unicode decoding and iteration over the byte representation, including
  behavior at invalid character boundaries.
- A C-compatible string with a trailing zero and directly available length.
- A short string packed with its length into a machine word, passed by value.

Keep mutable storage private by default and specify sharing, concatenation,
capacity growth, and native I/O contracts. Existing `sys` byte-array and
buffer helpers provide the basic builder and formatting operations.

### Struct values

Allow a struct to be stored inline in another object, array, or argument list
rather than always through a reference. A proposed `*Complex` or `inline`
spelling would place both coordinates of `struct Complex { f64 x,y; };`
directly in the containing value.

Define whole-value copying, reference-to-value conversion, and assignment
through an existing reference. A value must not silently become a reference
or expose an interior address with an invalid lifetime. Decide how consuming
an enclosing object's lifetime could permit such an address. Keep method
syntax uniform and define the calling convention for by-value arguments.
