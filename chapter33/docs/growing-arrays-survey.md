# Growing arrays: where grow-in-place fits

Surveyed 2026-11-21. Most current byte builders have a single construction
phase and could finish as ordinary arrays. The persistent counterexamples are
bitsets, timers, and canvas buffers. The choice belongs to a use of a collection,
not to every instance of `aryu8` or `aryi64`.

This is a source survey, not an implemented placement proof or a runtime profile.
It covers chapter33's Simple library, examples, Simple programs embedded in Java
tests, and retained regression workloads. Earlier chapter copies are not counted
again. Compiler Java collections appear separately as future Simple-in-Simple
requirements. No language or library behavior changed during the survey.
The [first source rewrites](growing-array-rewrites.md) show BrainFuck and direct
block reads using scope-ended growth and a proposed writable array length.

## Construction that finishes

“Candidate” below means growth has a recognizable end. It does not yet prove
that the physical allocation end is free, or solve returning variable-sized data.

| Code | Observed growth and subsequent use | Grow-in-place assessment |
|---|---|---|
| [Interpolation](../src/main/java/com/seaofnodes/simple/parser/Strings.java), [text.integer/string](../src/main/smp/sys/text.smp) | Parser creates one builder, appends literal pieces and formatted expressions, then calls `end`. Integer formatting uses a fixed 22-byte digit scratch array. | Strong candidate. Fixed scratch needs placement before/beside the growing storage; formatting helpers need to borrow the active builder. Arbitrary interpolation expressions can introduce nested allocation. |
| [text.floating/time](../src/main/smp/sys/text.smp) | Both obtain a temporary string and append it to the outer builder. `floating` gets a native allocation; `time` calls another Simple formatter. | Candidate after arranging temporary storage or changing to destination-writing helpers. Not automatically one free growing end; see below. |
| [aryi64.str](../src/main/smp/sys/aryi64.smp) | Appends brackets, numbers, and separators, removes the final separator, then returns the builder. [BubbleSort](examples/BubbleSort.smp) and [StreamIOTest](../src/test/java/com/seaofnodes/simple/StreamIOTest.java) append a newline afterwards. | Construction is localized, but its present API returns a still-growable value. Prefer an append-into-destination formatter, with the caller adding the newline before finishing. |
| [parseAryI64](../src/main/smp/sys/aryi64.smp) | One scanner and one result vector; append parsed numbers, validate closing syntax, return. BubbleSort sorts the result; Dijkstra only reads it. Neither appends more numbers. | Strong candidate for a finished mutable `i64[]`. Freeze length, not element stores. Returned variable-sized storage still needs a destination. |
| [io.read](../src/main/smp/sys/io.smp) | Allocates an 8 KiB scratch chunk before starting the builder. Reads until EOF, appends each received prefix, then copies to an exact-length result. Errors discard the partial result. | Strong candidate. The scratch buffer is already separate from the growing output. Finish could avoid the final copy if the result's lifetime and placement permit it. Keep short-read/EINTR/error behavior. |
| [BrainFuckTest](../src/test/java/com/seaofnodes/simple/BrainFuckTest.java) | One output builder, one fixed 100-byte tape, no other growing collection in the interpreter. Output is consumed only after execution finishes. | Strong candidate. Place the fixed tape before the growing output, or in reserved fixed storage. Finish at interpreter exit; the current wrapper-return API needs changing or an explicit result destination. |
| [TicTacToe._draw](examples/TicTacToe.smp) | Builds one board rendering and immediately writes it. No builder escapes. | Simplest local candidate. This particular rendering is exactly 60 bytes, so a fixed array also suffices. |
| [FileIO](examples/FileIO.smp), [Dijkstra's diagnostic output](examples/Dijkstra.smp) | FileIO builds its message before opening/writing/reading the file, then compares it with the readback. Diagnostics build and write once. | Finish FileIO's message before later I/O allocations; its storage remains live for comparison. The literal message can also be sized exactly. Diagnostics are local builders. |
| [time._format/elapsed/zoneName](../src/main/smp/sys/time.smp) | Formatters build and finish one result. `zoneName` first fills a fixed 128-byte buffer, learns `n`, then builds another buffer and copies again in `text.end`. | `_format`/`elapsed` are localized builders. `zoneName` needs no growing array: after the native call, allocate exactly `n` bytes and copy once. All returned results retain the destination question. |

The [original BrainFuck workload](../src/test/java/com/seaofnodes/simple/spill/BrainFuckTest-testBrainFuck.smp)
does `new u8[output# + 1]` and copies the old output on every `.` command.
For N emitted bytes that copies N(N-1)/2 old bytes, in addition to writing the
N new bytes. It is the clearest avoidable-copy example. The aryu8 version already
uses geometric growth. Keep the historical workload if its purpose remains
cross-target compiler comparison; add a new builder version alongside it.

## Growth interleaved with continued use

The production Simple library has five implementations that replace backing
arrays to increase capacity: `aryu8`, `aryi64`, `bitset`, `Timers`, and `Canvas`.
Timers replaces three arrays together. The first two support the construction
cases above; the other three illustrate why we still need another storage choice.

| Code | Why a single temporary construction scope is insufficient | Suitable direction |
|---|---|---|
| [adt.bitset](../src/main/smp/sys/adt/bitset.smp) | Random indexed `set`/`tset` can increase capacity between reads, clears, and other work. | General instance: retain a replaceable backing array or use chunks. [Dijkstra](examples/Dijkstra.smp) knows the bit universe from `nodes`, so reserve `(nodes+63)/64` words before its loop instead. |
| [time.Timers](../src/main/smp/sys/time/Timers.smp) | IDs, deadlines, and periods grow together; registration, cancellation, and polling interleave. [TimeTest](../src/test/java/com/seaofnodes/simple/TimeTest.java) forces growth past eight slots. Clock itself registers only one timer. | A mini-arena owned by the Timers instance can hold replacement arrays. Alternatively, fixed chunks of inline timer entries would avoid copying and exercise value objects; that is a representation change, not just append syntax. Three independent arrays cannot all own one contiguous growing end. |
| [draw.Canvas.resize](../src/main/smp/sys/draw/Canvas.smp) | Capacity increases across window events, interleaved with drawing, timers, and text formatting. Smaller sizes reuse capacity. On growth the old pixels are discarded, not copied. | Retain replacement-buffer behavior in the Canvas lifetime group. A deliberately reserved non-moving region is plausible for a small number of windows, but should be an explicit choice. Chunking pixels directly would change the contiguous graphics interface. |

The generic aryu8 API also permits `clear`, `undo`, reads, and later appends.
[StreamIOTest](../src/test/java/com/seaofnodes/simple/StreamIOTest.java) exercises
that mutable-container behavior. The small `_Vec` in
[Chapter21Test.testArmVectorGrowth](../src/test/java/com/seaofnodes/simple/Chapter21Test.java)
is another explicit doubling/copy implementation outside sys. Its particular
caller builds five integers in one expression; the generic type allows later
growth. The frozen BubbleSort and seed-reproduction sources contain older copies
of the same byte/integer vector pattern.

## Two constraints exposed by the survey

### Explicit containers for interleaved growth

Discussion direction: infer one active growing end, with properly nested
temporary work allowed. If multiple live arrays need interleaved extension at
that end, reject with a concrete rewrite path. Do not automatically synthesize
independent regions or a general collector to preserve the source representation.

A proposed diagnostic at the failed extension:

```text
Cannot extend 'ids' here: live storage for 'due_ms' follows it.
  'ids' starts growing at ...
  'due_ms' is allocated at ... and is still needed at ...
Use one array of inline timer entries, or explicit resizable containers
such as aryu32 and aryi64 for independently growing buffers.
```

This diagnoses unavailable tail space while growth is still permitted. Trying
to grow an array whose constructor has already ended instead reports that its
length is fixed, with the constructor boundary and the attempted growth shown.

For Timers the programmer can choose either representation:

- Array of inline Timer entries containing id, due, and period, with explicit
  resize logic. Under the previously agreed notation this is `new *Timer[8]`
  (with appropriate element initialization), rather than an array of Timer
  references. No per-entry object allocation or pointer chase is required;
  padding and field-scan locality can still differ from struct-of-arrays.
- Keep the struct-of-arrays layout, using three explicit container instances:
  one aryu32 and two aryi64 collections. These containers need not own the
  shared stack's growing end.

Agreed allocator direction: growable containers use power-of-two backing arrays
and deleted backing arrays are recycled through a buddy allocator. Resize obtains
a larger chunk, copies the used elements, publishes the replacement, and calls
`old.del()` to retire the old backing allocation. This moving representation
does not promise stable
direct interior references through resize. Container/index handles and copied
primitive/reference values remain useful interfaces.

Agreed safety rule: a deletable array has a different type from an ordinary
array; its provisional modifier is `^`. It disallows interior references and
requires unique access and no escaping aliases. Deletion consumes the owning
access, with no outstanding borrows or other usable aliases surviving it.
The compiler checks this rule; it is not an unchecked promise by the library.
Copied primitive elements and copied object references are not references into
the backing storage. Container/index handles can resolve through the current
buffer without retaining the retired one.

In resize, replacing the container's backing field removes that owning access
to the old buffer; the local `old` then supplies the access consumed by delete.
If another usable alias remains, diagnose that alias and the attempted deletion.
Ordinary arrays retain their automatic-lifetime behavior and cannot be deleted.
Raw-address borrowing must also respect the no-surviving-use requirement.

This differs from deletable class slots, whose stale references remain type-safe.
Buddy splitting/coalescing can change the array extent and layout. An old array
header can become element data after reuse, so even bounds checks can read the
wrong length. Merely preserving the element type is not enough to inherit the
class-slot post-delete guarantees. The agreed uniqueness rule excludes those
stale accesses, allowing the buddy allocator to recycle the whole block.
Moving the length field out of the allocation is therefore not needed for this
safety argument; it remains a separate, useful layout optimization.
The same buddy allocator can serve every element type and keep metadata inside
freed blocks. Automatic deletion on a proven last-reference overwrite is deferred;
the first interface uses explicit `.del()`. The
[first ownership experiment](../README.md#array-deletion-first-ownership-checks)
now accepts source `E[^]` and `.del()` and checks local aliases and field kills.
The [migration survey](array-delete-survey.md) records which real resize bodies
pass these local checks and which proof obligations remain. The runtime
intrinsic is not implemented yet.

`text.begin()` currently returns a newly created builder. For the proposed
scope-owned growing array, interpolation should establish that storage in the
expression's owning scope, then let helpers borrow it. Keeping `begin()` unchanged
and hoping the inliner erases its call would make a language lifetime rule depend
on optimization. The same issue applies to factories returning fresh growable
wrappers; it is separate from a helper appending to an existing destination.

One logical builder does not imply one free physical end. For example,
[Clock._draw](examples/Clock.smp) interpolates `sys.time.formatLocal(...)` into
an outer string. The outer builder is started before that expression is evaluated.
The inner formatter returns its finished array, then the outer builder grows
while reading that array. With both on the same growing end, the inner result
still occupies the space the outer wants. This is not the easy nested case where
the child has unwound before the parent resumes growing.

Agreed follow-up: append time formatting directly into a supplied destination.
For floating formatting, pre-grow the destination by a sufficient bound, pass
the writable tail and its capacity to the C helper, and receive the actual byte
count. Trim the unused tail afterwards. The helper fills the destination directly;
it does not allocate a returned string or copy from an intermediate output array.
The bound must account for the value, format, width, precision, and any terminating
NUL needed by the C implementation. The NUL is not part of the final string length.
The existing [simple_text_float](../src/main/c/simple_crt.c) still allocates;
changing it to this interface remains implementation work.

Arbitrary interpolation expressions follow the ordinary rules for code executing
inside any growable-array constructor. Their allocation/lifetime plan must leave
the required growing end free when extension resumes. If no legal plan exists,
the normal allocation analysis rejects the program. There is no separate
interpolation allocation rule. Helpers growing a caller's destination likewise
use the general call-frame placement rules.

The agreed return destination is now a separate growable data stack. `io.read`,
parsed number arrays, formatted strings, and BrainFuck results finish growth
before returning; their bytes survive the callee's control frame. The hidden
data-stack top is updated on return, and the owning lifetime later restores its
mark. This supplies contiguous non-moving storage without a final copy; lowering
and the calling convention remain implementation work.

## Aliases and future inline elements

Backing-array references are already observable, even without interior pointers:

- `aryi64` accepts a caller-provided backing array; it need not own the only reference.
- `buf`/`pixels` are exposed. StreamIOTest saves `b.buf`; DrawTest saves old and
  grown pixel arrays and checks reuse when resizing within capacity.
- `text.end(b)` currently copies into a read-only array and leaves `b` usable.
  A consuming finish operation changes that contract. Existing callers that are
  done with `b` can migrate; the general API cannot silently change meaning.
- `aryi64.str()` callers demonstrably continue growing the returned value.

A mini-arena makes its allocated blocks stationary. It does not make a vector
stationary if the vector later copies its contents into a larger block. An
array of future inline objects can promise stable direct interior references
only with the non-moving choice; those references also inherit the backing
storage's lifetime. Movable containers can expose values or `(container,index)`
handles instead. Truncating/clearing and reusing inline slots will need its own
reference-validity rule, even when the storage address stays fixed.

Old backing arrays cannot be recycled merely because the wrapper now points to
a new array: another reference may still name the old one. Retaining all blocks
until the lifetime group unwinds is the simple safe policy. Earlier recycling
needs proof that those references are gone. Allocator chunk recycling here is
distinct from the language's explicit `delete` and its weaker post-delete rules.

## Compiler collections: future Simple-in-Simple cases

| Existing Java use | Storage implication |
|---|---|
| [Node inputs/outputs](../src/main/java/com/seaofnodes/simple/node/Node.java), backed by [Ary](../src/main/java/com/seaofnodes/simple/util/Ary.java) | Many independently growing collections, mutated throughout optimization. Embedded small vectors plus overflow storage fit; one reserved growing region per Node does not. Entries currently hold references, not inline Nodes. |
| [CodeGen linker/call/CFG lists](../src/main/java/com/seaofnodes/simple/codegen/CodeGen.java) | Broad lifetimes, but several lists grow interleaved. Being long-lived does not alone make their ends free. Separate explicit areas or a common chunk allocator are options. |
| [Serialize.write](../src/main/java/com/seaofnodes/simple/codegen/Serialize.java), [Lexer.stringPart](../src/main/java/com/seaofnodes/simple/parser/Lexer.java) | Local output accumulation followed by a completed byte result. Candidates for finishable builders, subject to helper temporaries and the return destination. |
| [Encoding](../src/main/java/com/seaofnodes/simple/codegen/Encoding.java), [BAOS](../src/main/java/com/seaofnodes/simple/util/BAOS.java) | Instruction bytes, constant data, and writable data have separate buffers; emission also creates metadata, patches earlier bytes, and can replace the instruction buffer during compaction. This needs an explicit layout plan or independent buffers, not blanket conversion of BAOS. |
| [IntHashMap](../src/main/java/com/seaofnodes/simple/util/IntHashMap.java) | Growth rehashes entries. Reserving more address space does not remove that semantic relocation. A future inline-entry reference cannot promise stability through rehash. |

The native MiniFB adapter has a separate resizable repaint buffer in
[simple_minifb.c](../src/main/c/simple_minifb.c). It copies the Simple pixels;
the retained native buffer is not a retained pointer into the Simple array.

## Suggested first cases

1. BrainFuck output: one unknown-length append stream, fixed scratch, finish;
   first consume it within the same activation to isolate growth from return placement.
2. `io.read`: chunked input, error unwind, and a finished variable-sized result.
3. Integer/string interpolation, then nested time formatting: borrowed builder
   helpers, fixed scratch, and the first real competing-temporary case.
4. Timers: persistent growth and three coupled arrays; compare grouped replacement
   buffers with chunks of inline entries rather than forcing it into the first case.

Some current variable-size failures are not growing arrays at all: `str0`,
`Canvas` construction, and `text.end` know their length when they allocate.
Supporting runtime-sized fixed arrays is independently useful. This survey does
not predict a new accepted-function count: the current pass also rejects loop
retention, returned variable-sized storage, and recursive Timers._add before
these proposed storage representations exist.
