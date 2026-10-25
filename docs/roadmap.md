# Compiler roadmap

English | [日本語](roadmap.ja.md)

Open compiler work and language proposals, reviewed against the current
snapshots on 2026-10-25. These are future changes, not promises about the
language implemented by the tutorial. Language changes still need their own
design decisions and chapter boundaries.

## Correctness

### Narrow field results after method updates

Chapters 25-27 can return an untruncated increment when a method updates a
narrow field and then reads it. With evaluator stores corrected, this example
returns 366 instead of 110. Chapter 25's generated RISC-V code also returns
366: the method's optimized Return uses the raw Add with range `[1-256]`,
although the Store writes a byte. Chapters 23, 24a/24b, and 28a/28b return 110
with the corrected evaluator.

```simple
struct C { u8 x=255; val next_noInline={ -> x++; return x; }; };
var c=new C;
val a=c.next_noInline();
val b=c.next_noInline();
return a+10*b+100*c.x;
```

Find where the field read loses its narrowing during parsing or optimization,
and preserve the converted result independently of the Store. Check both
signed and unsigned fields and compare ideal evaluation with generated code.

## Compilation and optimization

### Reliable incremental builds

Chapter 28b now checks the reachable source-backed inputs before reusing saved
IR, including transitive dependencies, bundled sources, and missing inputs.
The remaining work is a build driver with recorded input identities, beyond
those conservative timestamp checks. An unchanged public signature does not
establish compatibility: optimization imports bodies and constants too.

Record the actual resolved input paths and versions, compiler/serialization
versions, target, ABI, and relevant options. Content hashes can cover preserved
timestamps and changed library search paths. Distinguish rebuildable source
from incompatible source-free libraries; their installation timestamps alone
cannot validate a set of objects compiled against one another.

An unchanged build should do no compilation or linking. Publish outputs only
after successful compilation, and relink when an object or other link input
changes. Start with a small build driver and conservative rebuilding of every
dependent; finer interface/body fingerprints can follow. The
[incremental-build design](../skills/incremental-builds.md) records the input
ownership rules and validation cases.

### Selective loading of library IR

Importing `sys.io.p` currently loads all ten units bundled in `sys.o`.
Independent, unrelated objects can remain unloaded, but selecting an object
reconstructs all its saved IR and follows its recorded dependencies.

Design member-level IR loading and dependency discovery together. Splitting
objects alone is insufficient while reexports and unused function bodies
still pull in their dependencies. Preserve type identity, initialization rules,
and imported escape summaries. Loading, initializing, and emitting a class
are separate decisions. This optimization is independent of making
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

### Constant instance-field storage

When a final instance field has the same constant value in every instance,
fold accesses or store the value on the class object and omit the instance
slot. Preserve field-access semantics and public function reachability:
externally accessible function fields remain callable regardless of storage.
Existing static images for class constants do not implement this general
instance-layout optimization.

## Language boundaries

### Source privacy

Underscores already select the public interface, but source access to another
class's private fields is not rejected. The proposal is to enforce privacy
starting in Chapter 28a, then preserve it across imports in 28b. This language
change still needs review; it is not a correction to impose on earlier chapters.

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

The bundled `sys/io.smp` calls `libc._exit` directly. Keep that native binding
private, add a public `exit` wrapper in `sys/libc.smp`, and change IO to call
the wrapper. Bitset and array helpers access their own private fields and
remain legal. String spill fixtures with helpers outside `_String` accessing
`_hashCode` need explicit adaptation; do not grant file-wide access merely to
keep those fixtures unchanged. Validate native execution and imported source
ownership alongside the access diagnostics.

### Constructor helpers and completion

Local SSA field updates, including final fields, repeated assignments, and
loops, are implemented. The remaining question is broader: should constructors
be allowed to pass unfinished `self` to helpers that become safe after
inlining, and should completion checks wait until SCCP?

Decide those rules before changing the current parser-time completion checks
and early-publication restrictions. Separate reading an initialized field of
an unfinished object from exposing that object to arbitrary code. The
construction/escape teaching boundary can then be reviewed against the chosen
semantics.

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
ordinary data pointers still occupy eight bytes.

Give full-width native pointers separate types and foreign lifetime contracts.
Opaque device addresses may need to pass through without CPU dereference.
Automatic Simple object lifetimes without a GC still need a design for
ownership, borrowing, cycles, and native retention. Compact storage alone
does not establish those rules. Preserve static class images and explicit
native boundary conversions when choosing the representation.

## Data and library proposals

These are language-design alternatives. The sketches below are not executable
examples of the current Simple grammar.

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

Design a family of byte-based strings with UTF-8 decoding supplied by library
functions. Consider these distinct representations and interfaces:

- A growable string with inline bytes and cached metadata, returning a
  potentially new receiver when capacity increases.
- An immutable string constructed all at once, with defensive copying where
  necessary. Read-only access alone does not establish exclusive ownership.
- A StringBuilder-style wrapper holding a replaceable pointer to its buffer.
- Unicode decoding and iteration over the byte representation, including
  behavior at invalid character boundaries.
- A C-compatible string with a trailing zero and directly available length.
- A short string packed with its length into a machine word, passed by value.

Keep mutable storage private by default and specify sharing, concatenation,
capacity growth, and native I/O contracts. Existing `sys` byte-array and
buffer helpers are a starting point, not this complete string design.

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

### Default integer width

Consider making `int` mean `i32` instead of the current `i64`. This changes
overflow behavior, layouts, APIs, and native interfaces, so it needs a language
decision and a deliberate compatibility boundary.

### Parser Cleanup

Get the Parsers' structure closer to an ideal R-D parser, and matching the
grammer doc.  Move TYPES to CodeGen, and generally restructure for readability.
Ponder using try-w-resources to do the keep/unkeep pairing (do NOT do any
allocation in the `try`, just bring up an object w/auto-closable which tracks
the unkeeps) - see if that looks any cleaner.
