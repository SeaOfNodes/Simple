# Chapter 30: Text Formatting and Diagnostics

English | [日本語](README.ja.md)

[Previous: Chapter 29 — Tech Debt Cleanup](../chapter29/README.md)

This chapter uses a small game to drive improvements in the compiler and the
`sys` standard library.  The game provides a concrete workload; the chapter's
subject is the language and library work uncovered.

The first program is ASCII [tic-tac-toe](docs/examples/TicTacToe.smp).
The player moves first with single keystrokes; the CPU responds, occupied
squares are rejected, and play ends with a win or draw.
Runnable programs, including sorting, file I/O, and shortest-path examples,
live in [`docs/examples`](docs/examples/).

## Terminal input and diagnostics

[`sys.io.getch()`](src/main/smp/sys/io.smp) reads one byte without waiting for
Enter or echoing it.  A C adapter changes and restores the terminal mode;
redirected files and pipes also work.  It returns `0..255`, or `-1` with
`sys.libc.errno()` set to `sys.libc.EOF` or an OS error.  Escape and UTF-8
sequences arrive as multiple bytes.

The game echoes accepted moves, ignores Enter, and quits on `q`, Escape,
Ctrl-C, Ctrl-D, or end of input.

We also exposed a parser bug: branch guards from one function could affect the
next function.  Function boundaries now isolate those guards.  Loop-path merges
also preserve forward references discovered after a break or continue.

The CLI reports parser and type errors as `file:line:column: error: message`,
followed by the source line and an underscore-and-caret pointer - suitable for
older text editors (VI, Emacs).  Language errors now exit with status 1 instead
of throwing an exception; unexpected compiler errors keep their Java stack
traces.  Quite a few error reporting points are cleaned up: malformed numbers,
invalid escapes, unterminated comments or quoted text are a few.

## Stream I/O and resizable arrays

`sys.io.read(fd)` grows its buffer and continues through short reads until
EOF.  It returns an exact-sized byte array, empty for immediate EOF, or `null`
on error with `sys.libc.errno()` preserved.  Partial input is discarded on an
error. `stdin()` uses the same behavior.

`write`, `writeOff`, and `aryu8.write` write the complete requested data,
retrying partial writes.  Reads and writes retry `EINTR`; other errors return
to the caller.  Writes return a 64-bit byte count, or `-1` with errno set; a
failure can follow a successfully written prefix.  A zero-progress write
reports `EIO`.  `writeOff` rejects offsets past the array end with `EINVAL`,
caps the length at the remaining bytes, and accepts empty slices at the end.

The buffering and retry loops are Simple code.  C supplies access to errno and
its platform constants; the native read/write bindings use 64-bit counts and
results on the supported targets.  Tests cover files, pipes, empty and large
inputs, and injected short transfers, interruptions, and failures.

`aryu8` and `aryi64` store their lengths as `u32`, matching built-in array
lengths.  Growth handles empty backing arrays, uses existing capacity when it
fits exactly, and calculates the next capacity in 64-bit arithmetic before
narrowing.  Requests beyond the `u32` length limit are rejected: stream reads
return `null` with `EOVERFLOW`, while direct resizable-array growth exits with
status 1.  The tests check arithmetic at the unsigned boundary without allocating
multi-gigabyte buffers.

## Default integer type

`int` now aliases `i32`.  Its fields and array elements occupy four bytes;
assignments and updates use the existing signed 32-bit narrowing rules.
Explicit `i64` remains available for wide arithmetic and native addresses.
Literal inference, `var`/`val` inference, and intermediate arithmetic retain
their existing rules: `int x=2147483647; return x+1;` produces `2147483648`,
whereas assigning that sum back to `x` produces `-2147483648`.

The standard library spells out `i64` where it needs full-width formatting or
native addresses/sizes. Resizable-array lengths, growth counts, and indices
use explicit `u32`.

Function entries normalize narrow signed/unsigned parameters, including
stack arguments, using the backend extension operations.  This also applies
to internal Simple calls.  Loop type widening preserves signed and unsigned
32-bit bounds of values narrowed on every assignment.

## Strings and formatting

Strings can embed expressions from the surrounding scope:

```java
sys.io.p("CPU plays %(reply+1).\n");
sys.io.p("score=%04d(score), average=%.2f(average)\n");
sys.io.p("name=%-12.8s(name), key=%c(key)\n");
```

`%(expression)` selects decimal integer, floating-point, or byte-string
formatting from the expression's type.  Booleans are integer aliases, so the
default prints `0` or `1`; `%b(expression)` prints `false` or `true`.
Expressions execute once, from left to right. Each value is formatted before
evaluating the next expression. The result is an ordinary immutable `u8[~]`
with its exact byte length. Strings without embedded expressions remain
constants.

An explicit format has the form `%[flags][width][.precision]kind(expression)`:

| Kind | Argument and output |
| --- | --- |
| `d`, `i` | Integer, signed decimal |
| `u`, `o`, `x`, `X` | Integer, unsigned decimal, octal, or hexadecimal; negative values use their 64-bit representation |
| `f`, `F`, `e`, `E`, `g`, `G`, `a`, `A` | Float, using the corresponding C format |
| `c` | Integer, one byte from its low eight bits |
| `s` | Byte string, including embedded zero bytes |
| `b` | Integer, `false` for zero and `true` otherwise |

Flags are `-` (left alignment), `+` (explicit sign), space (positive sign
padding), `#` (alternate form), and `0` (zero padding), where applicable.
Width and precision are literal decimal integers.  String precision limits
bytes; integer precision supplies a minimum digit count.  `c` and `b` allow
width and left alignment, but no precision.  Default floating-point formatting
uses `g` with 17 significant digits; explicit floating formats use C defaults
unless a precision is given.  Positional arguments, `*` widths, C length
modifiers, and `%n` are not supported.

Write `%%` or `\%` for a literal percent.  Every other `%` must introduce a
complete format with parentheses.  Inside those parentheses the normal
expression parser resumes, so nested strings work directly:

```java
val text = "outer %("inner %(value)")";
```

[`sys.text`](src/main/smp/sys/text.smp) provides explicitly named converters:
`int`, `flt`, `f32`, `f64`, `char`, `bool`, and `hex`.  For example,
`sys.text.int(17)` returns `"17"`.  Primitive type names are allowed as member
names and in member declarations; they remain reserved for local variables and
parameters. `sys.io.p` continues to require a byte string.

`Strings` builds ordinary calls to `sys.text`, with normal module lookup,
class initialization, and control/memory edges.  A small `FormatNode`
selects the default typed formatter as normal type flow resolves an expression,
including forward references; it folds away in valid programs.

The formatting library is written in Simple: `begin` creates an `aryu8`,
`integer` and `string` append formatted values, and `end` copies the populated
bytes into the immutable result.  The integer formatter covers signed and
unsigned 64-bit values, including minimum `i64`, and handles signs, prefixes,
width, and precision.  Character and boolean formatting use the same builder.
`aryu8.num` shares the integer formatter, including its full-range handling.
Only binary floating-point conversion calls C, through one fixed-signature
adapter returning a byte string.  There is no boxing or variadic Simple call.

`sys.text` uses no interpolated strings, including in its named converters, so
the library builds from source without a staged bootstrap.  Non-float
formatting can also run in the IR evaluator.  The lexer shares its string and
format scanner with deferred constructor-body and loop-update scanning.

## Runtime support in Simple

Native startup also uses Simple: [`simple_args.smp`](src/main/smp/sys/rt/simple_args.smp)
allocates the argument array and copies each argument into a byte array.  The
C entry adapter calls this compiled routine before `simple_main`; C supplies
only access to `argv[i]`, `strlen`, and `memcpy`.  The converter fills every
nullable array slot before publishing the result as `u8[~][]`, including the
program name at index zero.  It builds independently of `sys`, so even programs
without standard-library imports use it.

The `sys.rt` module holds Run Time support, like Java's `rt.jar` or C's `crt.o`.
Make emits `simple_args.o` alongside `simple_crt.o`; the native linker driver
includes both runtime objects automatically.

## Register allocation

The allocator handles each function independently, retaining its completed
register assignments while reusing scratch space for the next function.
Building live ranges, constructing interferences, coalescing, coloring, and
splitting all run on that function's scheduled blocks.  The eight-round limit
applies per function.  A hard register-mask conflict in one function therefore
cannot prevent another function from reaching coloring or consume its retry
budget.

Two changes avoid unnecessary retries. First, liveness ignores inputs whose
operand register mask is null: these are metadata or scheduling dependencies,
not runtime register uses. In particular, a static call's unused hidden class
argument must not keep its class-pointer constant live across other calls and
trigger rematerialization.

Second, splitting retains both transfers when a value must move out of a fixed
register before a kill and back into it afterward.  A buggy attempt at early
coalescing would undo a split needed to dodge the kill.  Interference-aware
coalescing decides when they can be joined.

Loop self-conflicts initially try cold transfers.  If that does not separate
the ranges, the fallback also splits backedge inputs.  Distinct backedge nodes
can still share a range through two-address instructions; copying only the Phi
outputs leaves that conflict intact.

The spill-ranking heuristic and calling conventions are unchanged.  Saving a
callee-save register can still require several coloring attempts as pressure
changes.  The [allocator regressions](src/test/java/com/seaofnodes/simple/codegen/RegAllocTestSupport.java)
check function independence in both scheduling orders, preservation across
register kills, and dependencies that do not consume registers.

## Related follow-up work

[Narrow integer instruction selection](../docs/roadmap.md#integer-instruction-widths)
and [per-function calling conventions](../docs/roadmap.md#per-function-calling-conventions)
remain separate backend projects. C pointer types and compact references are
also deferred; the `int` change does not shrink pointers.

The [grammar and code map](docs/30-grammar.md),
[source layout](src/main/java/com/seaofnodes/simple/README.md), and
[construction notes](docs/construction-groups.md) describe the starting point.
Open projects are listed in the [roadmap](../docs/roadmap.md).

## Build and test

From the repository root:

```sh
make lib tests release CHAPTERS=chapter30 CTAGS=
```

Build the game without starting it:

```sh
make -C chapter30 tictactoe CTAGS=
```

The Windows/Cygwin build produces
`chapter30/build/examples/x86_64_v2_win64/TicTacToe.exe`, which can be launched
from Cygwin or PowerShell. Linux produces
`chapter30/build/examples/x86_64_v2_SystemV/TicTacToe`.
When building in WSL alongside Windows, keep Java build outputs separate:

```sh
make -C chapter30 tictactoe CLZDIR=build/classes-linux CTAGS=
```

IntelliJ has a `chapter30` module and a **chapter30 tests** run configuration.
Build the native runtime and `sys` library with Make before debugging tests
that need them; see [the IntelliJ setup](../docs/intellij.md).
Inherited test class names retain their historical identities.
