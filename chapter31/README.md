# Chapter 31: Software Drawing, Time, and Graphical Games

[Previous: Chapter 30 — Text Formatting and Diagnostics](../chapter30/README.md)

In this chapter we use small graphical games to drive the language and library
work.  We add graphics, sound, and time support, with a few small C adapters
for native services.  We also add array initializers and read-only arrays, hex,
octal parsing, move constant arrays into the ELF constant pool.  We then write
a few games, with the drawing and game logic written in Simple.


## Games and demos

All graphical demos support resizing. Canvas scales their base layouts and
mouse coordinates together, preserving proportions; Breakout's physics remains
in base coordinates. The pixel gradient fills the resized window.

[Clock](docs/examples/Clock.smp) is a classic clock demo.  It shows local time as
digital digits or an analog dial.  Click its button or press Space to switch
views.  It combines timers, mouse input, text, and drawing in one event loop.

[Graphical Tic-tac-toe](docs/examples/TicTacToeGraphical.smp) accepts mouse
clicks or keys 1 through 9.  It highlights the winning line and plays move and
result sounds.  Game logic is the same as in the terminal version from
the prior chapter, with the graphics added to the event loop.

[Breakout](docs/examples/Breakout.smp) has a ball, a paddle, and a wall of
bricks.  Move the paddle with the arrow keys or mouse.  The ball's bounce angle
depends on where it hits the paddle.  Deeper bricks score more points, make
higher-pitched sounds, and increase the ball's speed.  Hitting the *back* of a
brick gives a one-time larger speed boost.  Clear all the bricks before using
up three balls.


## Remainder

`%` computes remainder with Java's sign convention. It has the same
precedence as `*` and `/`, and associates left to right. For example,
`17 % 5` is `2`, `-17 % 5` is `-2`, and `17 % -5` is `2`.
The remainder follows the dividend's sign; it is not always nonnegative.
Integer `MIN_I64 % -1` is zero. Like Simple's integer division, integer
remainder by zero produces zero. Unused arithmetic can be removed.

Floating-point operands use the same truncating-quotient rule: `7.5 % 2.0`
is `1.5`. A zero divisor or infinite dividend produces NaN; a finite dividend
modulo infinity is unchanged, and a zero result retains the dividend's sign.
Mixed integer/float operands convert to floating point, as with `*` and `/`.
The compound assignment `x %= y` stores `x % y` back into `x`.
`mod` is an ordinary identifier.

Clock uses `second % 10` for its last digit and `i % 5 == 0` for major
dial ticks. The time library uses the operator for leap years and calendar
fields, adding a positive period when it needs a nonnegative result.

[`ModNode`](src/main/java/com/seaofnodes/simple/node/ModNode.java) follows
`DivNode`: two operands, one result, and shared arithmetic folding and type
checking. It has no control or memory effects. Integer machine encodings handle
zero divisors and signed overflow to preserve the ideal operations' results
without hardware faults. Floating remainder uses a memory-preserving runtime
helper selected as a machine operation; the ideal graph stays ordinary arithmetic.
`make test CLASS=ModTest` covers folding, evaluation, native execution, imports,
and code generation for all three targets.


## Graphics and sound

Graphics and sound are optional.  The Simple language and compiler do not need
them.  The `sys` library includes bindings to the native C backends, but you do
not need those backends for programs without a display or sound.  Their
dependencies download with the existing `make init` setup command.

Each pixel holds 24 bits of RGB color in a `u32`.  A pixel buffer is a `u32[]`.

[`sys.draw.Canvas`](src/main/smp/sys/draw/Canvas.smp) draws pixels, rectangles,
circles, thick lines, and text into a `u32[]` pixel buffer.  Shapes clip to the
canvas.  The 3x5 font includes digits, letters, and punctuation such as colons
and slashes.  Its glyphs are stored in a read-only array.

`sys.draw` supplies a few color constants plus `rgb(r,g,b)` to clamp and pack
computed color components.

`sys.draw.Button` handles button hover, press, release, and disabled states.

`sys.gfx` opens a window, presents pixels, and reads keyboard and mouse input.
The window backend uses MiniFB.  `gfx.wait(timeout_ms)` waits for input or a
timeout, so animation need not spin continuously.

`sys.audio` plays built-in cues or sample arrays generated in Simple. Its
optional miniaudio backend plays sound while the game continues.  The games
can run silently when audio is unavailable.

See [drawing](src/main/smp/sys/draw/README.md), [windows](src/main/smp/sys/gfx.md), and
[audio](src/main/smp/sys/audio.md) for the APIs.


## Time library

[`sys.time`](src/main/smp/sys/time.smp) provides wall-clock time, monotonic
time, sleep, calendar conversion, ISO date parsing, and formatting.  Use
wall-clock time for dates.  Use monotonic time for elapsed time and deadlines -
perfect for games.

Instants and durations normally use `i64` milliseconds. Constants show their
units directly: `SECOND=1000`, `MINUTE=60*SECOND`, and `HOUR=60*MINUTE`.

`sys.time.Timers` supports one-shot and repeating timers.  The event loop polls
for due timer IDs; no timer thread or callback is needed.  This might be a mindset
change for you game writers, it depends on what era you started writing them.
Here it is all single-threaded and event-loop driven - but when there is nothing
left to do, you need to wait for an event.

Time also works in string interpolation:

```simple
sys.io.p("UTC %tT(sys.time.now())\n");
```

Millisecond parameters and fields end in `_ms` to distinguish them from other
integers.

See [time and timers](src/main/smp/sys/time/README.md) for local-time formatting and other forms.


## Array initializers

An array can use a list of values or an expression evaluated for each index:

```simple
val primes = new int[]{2,3,5,7};
val squares = new int[10]{int i -> i*i;};
val names = new u8[~][~2]{"Alice","Bob"};
```

The generator runs once per element, from index zero through length minus one.
Its body can contain statements and return the element value.  It can read
surrounding locals but cannot reassign them; it is not a true function.

If the array has a fixed initialization list, then the length can be inferred.


`[~n]` (or `[~]` with a list) makes an array read-only.  `val` prevents
reassignment of the array variable, but not changes to its contents.  In C,
this is the classic `const u8* const` distinction: the first `const` protects
the contents; the second protects the pointer.  Array initialization normally
runs at runtime; read-only does not require compile-time evaluation.

When the compiler knows a read-only integer array's complete contents, it can
emit them as static ELF data.  Identical constant arrays can share storage,
just like strings.  Canvas's font now uses this path instead of allocating
and filling its table at startup.  The trigger is a `TypeConAry` constant type,
regardless of which node computes it.  Constant lists are the first new
producer; generators still run at runtime.

Initializers also allow arrays of non-null references: every element is
supplied before the array becomes accessible.  In the example, `names` is a
read-only array of two non-null, read-only strings.


## Math support and intrinsics

[`sys.math`](src/main/smp/sys/math.smp) provides `PI`, `E`, `min`, `max`, and
five native math functions, each with signature `{ f64 -> f64 }`:

| Function | Result |
| --- | --- |
| `sin(x)` | Sine of an angle in radians |
| `cos(x)` | Cosine of an angle in radians |
| `exp(x)` | e raised to x |
| `log(x)` | Natural logarithm of x |
| `sqrt(x)` | Nonnegative square root of x |

```simple
f64 diagonal = sys.math.sqrt(2.0);
f64 height = sys.math.sin(sys.math.PI/6.0);
f64 power = sys.math.exp(2.0);
f64 exponent = sys.math.log(power);
```

These are direct bindings to the host's libm; the standard linker already
includes `-lm`. Results and domain/range errors, including `errno`, follow
that library. See the native references for
[trigonometry](https://sourceware.org/glibc/manual/2.43/html_node/Trig-Functions.html)
and [exponents, logarithms, and square roots](https://sourceware.org/glibc/manual/latest/html_node/Exponents-and-Logarithms.html).
They remain ordinary C calls, so they are not constant-folded or discarded as
pure intrinsics. Clock uses `sin` and `cos` for its dial and hands.
`make test CLASS=MathTest` checks native results, special values, indirect calls,
and code generation for all three targets.

Two representation operations are compiler intrinsics:

```simple
i64 bits  = sys.math.f64Bits(1.0);      // 0x3ff0000000000000
f64 value = sys.math.f64FromBits(bits); // 1.0
```

They *reinterpret* the same 64 bits, preserving signed zero, infinities,
subnormals, and NaN signs and payloads, including the signaling bit. They perform
no arithmetic and have no memory effects. Applying either operation followed
by the other is an identity. Ordinary assignments between integer and
floating-point types still perform numeric conversion; floating-point
arithmetic may change a NaN representation.

These functions introduce compiler intrinsics: ordinary C bindings with reserved
external symbols, fixed signatures, and known meanings. The compiler recognizes
the external function's identity, including aliases and imported bindings, and
can replace its calls with ideal IR nodes. It does not recognize the spelling of
a local variable or an unrelated Simple function with the same name. No new
expression syntax is needed.

| External C symbol | Signature | Public alias |
| --- | --- | --- |
| `simple_f64_bits` | `{ f64 -> i64 }` | `sys.math.f64Bits` |
| `simple_f64_from_bits` | `{ i64 -> f64 }` | `sys.math.f64FromBits` |

[`Intrinsic.java`](src/main/java/com/seaofnodes/simple/node/Intrinsic.java) owns the
reserved-symbol table, signatures, effect queries, inlining, and ideal nodes.
Call/CallEnd delegate to it. The cheap inliner checks intrinsics before requiring
a Simple body or Return edge. It validates the signature and actual arguments,
then replaces the complete control/memory/value state. For these pure operations,
control and memory pass through unchanged.

Unknown indirect targets keep ordinary calls. The runtime's
[`simple_crt.c`](src/main/c/simple_crt.c) supplies C definitions using `memcpy`
to preserve representations without aliasing tricks. A replacement definition
must honor the same contract. Ordinary C functions remain opaque to the optimizer.

The ideal nodes support constant folding, inverse cancellation, evaluation, and
serialization. Floating-point constant interning and serialization retain raw
NaN bits. x86 emits `MOVQ` between XMM and integer registers; ARM emits `FMOV`,
and RISC-V emits `FMV.X.D` / `FMV.D.X`. Register allocation may add copies or
spills, and normal module-initialization checks still apply.

Future intrinsics such as compare-and-swap or fences must define their memory
and escape effects explicitly, using nodes with the required ordering
dependencies. They must not inherit the pure conversions' passthrough memory
behavior. Atomic operations and hardware square root are not introduced here.

`make test CLASS=IntrinsicTest` checks representation preservation, folding,
type errors, side effects, indirect C fallback, binary imports, and instruction
selection on all three backends, including emulation and native x86 execution.

Simple also now recognizes hexadecimal (`0xff0000`) and leading-zero octal
(`025755`) integer literals.


## Compiler fixes

- Syntax errors raised through JUnit now show the source line and error pointer.
  Actual compiler failures still report Java stack traces.
- Integer memory operations on x86 use the correct register class.  This fixes
  the instruction-encoding failure exposed by Dijkstra.
- Floating-point comparisons handle NaNs correctly in branches and Boolean
  results.  Floating-point negation also handles signed zero correctly.
- Array-initializer memory tracking no longer adds an unrelated function to
  the returned result.  Loop-load optimization checks that the pointer is
  available before moving a load.
- Imported read-only primitive arrays retain their types.  Object files can
  represent more than 255 distinct memory regions.
- Floating-point constants and register copies preserve raw NaN bits.

## Further reading

The [grammar and code map](docs/31-grammar.md) is this chapter's language
reference. Runnable programs and their controls are in the
[examples](docs/examples/README.md). Library API references live beside their
implementations: [drawing](src/main/smp/sys/draw/README.md),
[windows](src/main/smp/sys/gfx.md), [audio](src/main/smp/sys/audio.md), and
[time](src/main/smp/sys/time/README.md).

For inherited compiler behavior, start with [escape analysis in Chapter 27](../chapter27/README.md)
and [construction and partial references in Chapter 29](../chapter29/README.md).
Chapter 29 also links the detailed construction and escape implementation notes.

## Build and run

From the `chapter31` directory:

```sh
make libs                     # Download dependencies once.
make run-graphical-tictactoe
make run-clock
make run-breakout
```

Dependencies stay cached across `make clean`. The run targets build the needed
window and audio backends. Use `graphical-tictactoe`, `clock-example`, or
`breakout` to build an executable without opening a window.

Run the ordinary tests without a display or audio device:

```sh
make tests_nographics
```
