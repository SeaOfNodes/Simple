# Chapter 31: Software Drawing, Time, and Graphical Games

[Previous: Chapter 30 — Text Formatting and Diagnostics](../chapter30/README.md)

In this chapter we use small graphical games to drive the language and library
work.  We add graphics, sound, and time support, with a few small C adapters
for native services.  We also add array initializers and read-only arrays, hex,
octal parsing, move constant arrays into the ELF constant pool.  We then write
a few games, with the drawing and game logic written in Simple.


## Games and demos

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

See [drawing](docs/drawing.md), [windows](docs/graphics.md), and
[audio](docs/audio.md) for the APIs.


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

See [time and timers](docs/time.md) for local-time formatting and other forms.


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

[`sys.math`](src/main/smp/sys/math.smp) provides the `PI` and `E` constants,
plus two operations:

```simple
i64 bits  = sys.math.f64Bits(1.0);      // 0x3ff0000000000000
f64 value = sys.math.f64FromBits(bits); // 1.0
```

They *reinterpret* the same 64 bits, preserving signed zero and NaN payloads.
Ordinary assignments between integer and floating-point types still perform
numeric conversion.

These functions introduce compiler intrinsics: C functions with fixed meanings
that the compiler recognizes and replaces with internal operations.  For the
bit-reinterpretation operations, Simple uses plain register moves.  Calls whose
targets remain unknown at compile time use the C implementations.  Recognition
and expansion live in `Intrinsic.java`.

See [the intrinsic contract](docs/intrinsics.md) for the implemented behavior.

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

## Build and run

From the `chapter31` directory:

```sh
make libs CTAGS=                     # Download dependencies once.
make run-graphical-tictactoe CTAGS=
make run-clock CTAGS=
make run-breakout CTAGS=
```

Dependencies stay cached across `make clean`. The run targets build the needed
window and audio backends. Use `graphical-tictactoe`, `clock-example`, or
`breakout` to build an executable without opening a window.

Run the ordinary tests without a display or audio device:

```sh
make tests_nographics CTAGS=
```
