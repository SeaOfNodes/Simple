# Growing arrays: first source rewrites

Design notes, 2026-11-21. Writable array length and the constructor return rules
below are agreed; the parser and allocator do not implement these examples yet.
They follow the
[growing-array survey](growing-arrays-survey.md).

The proposed rule is: `new E[]` creates an initially empty array whose growth
belongs to the allocating lexical scope. Leaving that scope ends growth,
including returning the array. Its storage can outlive that scope; its permission
to grow cannot. Element mutation remains allowed. Returning from a helper that
merely borrows someone else's builder does not end the owner's growth phase.

## BrainFuck

The interpreter body stays the same. The relevant old code in
[BrainFuckTest](../src/test/java/com/seaofnodes/simple/BrainFuckTest.java) is:

```text
val output = new sys.aryu8();
u8[] !data = new u8[100];
// ... interpreter loop ...
output.add(data[d]);           // the '.' command
// ...
return output;                // a growable wrapper
```

Proposed replacement:

```text
val data = new u8[100];        // fixed tape, before the growing tail
val output = new u8[];
// ... same interpreter loop ...
output += data[d];            // the '.' command
// ...
return output;                // ends this scope: result has fixed length
```

The caller changes from `brain_fuck().write(sys.libc.STDOUT)` to
`sys.io.write(sys.libc.STDOUT, brain_fuck())`.
`+=` appends to the existing array; it does not reassign the `val` binding.
There is no wrapper, final trim-copy, or explicit finish operation.

The proposed whole-array constructor gives the boundary a dedicated body:

```text
val data = new u8[100];
val output = new u8[]{ ->
    // ... interpreter; '.' does self += data[d] ...
};                            // length is now fixed
sys.io.write(sys.libc.STDOUT, output);
```

The tape is scratch, while the output survives. Their placement still needs to
permit reclaiming scratch without invalidating the output; ending growth alone
does not establish that layout. The existing frozen BrainFuck workloads remain
useful compiler regressions and need not be rewritten with the new example.

## Agreed constructor scope and exits

The whole-array body executes immediately as a lexical construction scope.
It can read and write outer-scope variables, subject to their ordinary mutability
and lifetime rules. It has its own return target and result destination, as if
it were a function, but does not impose ordinary function capture restrictions.
`self` is a fixed binding to the array being constructed.

| Within `new E[]{ -> ... }` | Meaning |
|---|---|
| Reach the closing brace | Finish growth and yield `self`, regardless of the last statement's value |
| `return self;` | Finish successfully now, skipping remaining constructor statements |
| `return null;` | Abandon construction now and yield null |
| `return anythingElse;` | Error in this context; the explicit result choices are self and null |
| `return;` | Use `return self;` for explicit successful completion |
| `break;` | Exit the innermost loop in this constructor; continue after that loop |
| `continue;` | Continue the innermost loop in this constructor |

The nearest function-like body owns a return. A constructor-local return does
not return from the enclosing function. Conversely, returning from a helper
called by the constructor returns only from the helper. Break and continue
cannot cross the constructor boundary to a surrounding loop; without a local
loop they are errors. EOF can therefore use `break` to reach final trimming,
while a read failure uses `return null` to bypass the successful completion path.

This fits the existing indexed generator's return scope:

```text
new int[N]{i -> if(i==0) return 7; return i*i;}
```

There, `return` supplies one element and the generator proceeds to the next
index. In the zero-argument body it supplies the outcome of the whole-array
construction. A null return in a generator of nullable elements remains a null
element, not cancellation of the array. That distinction follows which body is
executing; it must not depend on whether a loop happens to surround the return.
The current indexed-generator parser rejects assignments to captured locals;
that existing implementation restriction must not be carried over to the new
whole-array construction scope.

A whole-array body with a possible null exit has a nullable result. A body
with no possible null exit has a non-null result. This does not change
allocation-failure behavior or make every `new Foo` expression nullable.
Generalizing fallible construction to ordinary classes is a separate choice;
successful class construction would still have to satisfy initialization rules.

Failure abandons the private construction storage, not its earlier side effects:
file bytes already read remain consumed, and writes to outer locals and external
state remain.
Cleanup must preserve errno. References into abandoned storage must not survive
its reclamation; the lifetime check includes the failure edge.

### Other exit spellings considered

- `self = null` with an implicit exit hides control flow in assignment; rejected.
- `self = null; break` separates the failure marker from the exit, makes self
  nullable inside the body, and normally breaks only the innermost loop. Avoid it.
- `self# = -1; break` violates the valid-length invariant and overloads an unsigned
  length with an error sentinel. Negative lengths remain invalid.
- `break null` could be part of a general value-producing loop/block design, but
  here it needs a new target rule: does it exit the loop or the constructor?
  A labeled value-break could disambiguate it, at the cost of more syntax.

The chosen forms are `return self` / `return null`, ordinary loop breaks, and implicit
successful completion at the constructor's closing brace. No magic assignment,
negative-length sentinel, or dedicated finish keyword is required.

## Growing and trimming with `#`

Try extending the existing length notation rather than adding a finish keyword:

| Expression | Proposed meaning during construction |
|---|---|
| `buf#` | Current accessible element count |
| `buf# = n` | Extend or trim the tail to checked length `n` |
| `buf# += n` | Add `n` accessible elements at the end |
| `buf# -= n` | Remove `n` elements; reject/check underflow |
| `buf += value` | Append one initialized element |

Length changes are permitted only while the allocating scope still owns growth,
and when placement permits the tail adjustment. Exiting that scope removes the
permission through all aliases. Ordinary `new E[N]` arrays never acquire it.
Zero-length and same-length updates are legal; neither underflow nor length
overflow wraps. Even a zero-length request to change a finished array's length
uses an operation for which the growth permission is absent.

For `u8`, extending the length gives initialized, zero-valued elements, consistent
with current array construction. That keeps the entire visible array safe to
read before the native call. A later optimization may omit initialization when
it proves the bytes are overwritten or trimmed before observation. Zero-copy
does not itself promise zero initialization cost. For inline objects without a
default initializer, increasing `#` alone cannot construct valid elements; those
arrays need append/construction into the new element destinations.

## `io.read`: direct read into the growing tail

The [current implementation](../src/main/smp/sys/io.smp) has three buffers over
its lifetime: a fixed 8 KiB read chunk, the builder's replacement buffers, and
the final exact-length array. Its data path is:

```text
libc.read(fd, chunkAddress, chunk#);
buf._grow(count);
ary.copy(buf.buf, buf.len, chunk, 0, count);
buf.len += count;
// At EOF:
val out = new u8[buf.len];
return ary.copy(out, 0, buf.buf, 0, buf.len);
```

Proposed replacement, including short reads, interrupts, errors, and the array
length limit:

```text
val read = { i32 fd ->
    return new u8[]{ ->
        u32 used = 0;
        while( true ) {
            i64 room = sys.integer.MAX_U32 - used;
            if( room > 8192 ) room = 8192;

            // An exactly maximum-sized stream can still end successfully.
            // Only this boundary case needs a separate byte for the EOF probe.
            if( room == 0 ) {
                val probe = new u8[1];
                i64 ptr = probe;
                i64 got = libc.read(fd, ptr, 1);
                if( got < 0 ) {
                    if( libc.errno() == libc.EINTR ) continue;
                    return null;
                }
                if( got != 0 ) {
                    libc.set_errno(libc.EOVERFLOW);
                    return null;
                }
                break;
            }

            self# = used + room;    // space for the next block, without moving self
            i64 got = 0;
            {
                i64 ptr = self;
                got = libc.read(fd, ptr + used, room);
            }                     // end this raw-address borrowing scope
            if( got < 0 ) {
                if( libc.errno() == libc.EINTR ) continue;
                return null;      // abort this constructor
            }
            if( got == 0 ) break;
            used += got;          // native read promises 0 <= got <= room
        }
        self# = used;              // drop unwritten tail; no data copy
    };                        // implicit success: yield finished self
};
```

Here `used` counts input bytes and `self#` includes the writable tail. A short read
advances `used` without shrinking on every iteration. The next length assignment
restores up to 8 KiB of free tail. EINTR leaves `used` unchanged, so retry does not
accumulate more space. EOF trims once. Empty input returns an empty array.
The one-byte limit probe preserves acceptance of a stream whose length equals
the maximum; reporting overflow before probing would wrongly reject it. On an
oversized stream this version consumes one extra byte before failing, rather
than the old implementation's possible full extra chunk.

No regular read chunk and no final copy remain. Physical alignment or mapped-page
slack can remain after trimming; the logical result length is exactly `used`.

## What these examples settle, and what they do not

Floating formatting uses the same extend/fill/trim pattern as direct I/O:
remember the current length, extend by enough space for the requested format,
let a bounded C helper fill that tail and return the actual count, then trim to
the old length plus that count. This is an agreed interface direction; the helper
name and signature are not yet implemented. Any snprintf terminator needs space
in the writable tail but is excluded from the published length. No temporary
formatted-string allocation or final copy is needed.

An arbitrary expression within interpolation is ordinary code running while an
array is growing. It must satisfy the same stack-allocation rules and leave the
growing end free for the next extension; interpolation adds no special exemption
or additional lifetime mechanism.

Both examples have a natural end of growth, so neither needs `finish(buf)` or
`stop growing` syntax. The proposed new operation is a checked, writable `#`
while growth is allowed. Scope exit does not guess the number of bytes a native
call wrote: `io.read` must explicitly set the final length from `used`.

For a non-moving implementation, the returned storage must already be in an
area that survives the callee, or its owning storage group must transfer to the
result's destination. Ordinary callee-local RSP storage cannot simply survive a
return. A chunk-chain handle can transfer ownership of storage, but a chain of
nonadjacent chunks is not one contiguous `u8[]`. Neither syntax example resolves
that placement choice by itself.

Growing a non-moving array preserves existing interior references. Trimming is
different: references into the removed tail must be gone before that storage is
reclaimed or reused. For these byte programs there are no typed interior
references, and the native borrow ends before the final trim. Future inline
objects need that restriction; non-moving alone does not make shrinking safe.
