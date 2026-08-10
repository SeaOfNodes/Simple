# Runnable examples

These programs live beside the documentation so readers can find and run them.
Their non-graphical native regression tests run as part of `make tests`.
The optional [pixel-window example](Graphics.smp) uses MiniFB; see
[graphics setup](../graphics.md). `make run-graphics` opens it explicitly.
The [graphical tic-tac-toe](TicTacToeGraphical.smp) uses the Simple
[drawing library](../drawing.md). Run it with
`make run-graphical-tictactoe CTAGS=`; click a cell or press 1–9, R/Space to
replay, and Q/Escape to quit. The terminal game below remains available.
Optional [background audio](../audio.md) supplies soft move and win/loss cues.

The [digital and analog clock](Clock.smp) demonstrates the [time library](../time.md),
green seven-segment digits, smooth clock hands, and a reusable software button.
Run `make run-clock CTAGS=`; click the button or press Space to switch faces,
and Q/Escape to exit. Both faces show system-local time.
`make clock-example CTAGS=` builds it without starting a window.

The [Breakout game](Breakout.smp) has 24 columns and 8 rows, with colors and
point values by depth: 1 at the bottom through 8 at the top, 864 points total.
Clear the wall before losing all three balls. Mouse movement or the arrow keys
move the paddle; Space/left click serves, P pauses, R restarts, Q/Escape exits.
Run `make run-breakout CTAGS=` or build only with `make breakout CTAGS=`.
The familiar paddle, serve, and limited-ball rules follow
[Atari's Breakout instructions](https://atari.com/pages/breakout).

The deepest row reached with the current ball sets the base speed: 320 pixels/second plus 35 for
each additional depth. After touching the back wall, hitting the top of a brick
activates a **one-time 1.65x speed boost**; side/front hits do not. Returning to
the paddle disarms that trip's back-wall opportunity. Losing a ball resets depth
and boost, so each new ball starts at 320 pixels/second and can earn its own
back-wall boost. Edge hits on the paddle give a wider
rebound than center hits. Clear all bricks to win; no new wall replaces them.

Physics uses fixed 8 ms ticks with movement split into steps of at most two
pixels, including at boosted speed. Rendering runs roughly every 16 ms. The
brick and sound tables demonstrate array initializers; the short bounce tones
use read-only PCM arrays made by index functions. Brick boops rise in pitch with
row depth, from about 333 Hz at the bottom to 800 Hz at the top. Audio is optional. Native
tests in `BreakoutTest` exercise collisions, scoring, lives, pause, paddle angles,
the boost, and clearing the whole wall without a display or audio device.

From `chapter31`, build the tic-tac-toe executable (without starting it):

```sh
make lib tictactoe CTAGS=
```

Run the Windows build from PowerShell:

```powershell
.\build\examples\x86_64_v2_win64\TicTacToe.exe
```

Or from Cygwin:

```sh
./build/examples/x86_64_v2_win64/TicTacToe.exe
```

The Windows executable uses Cygwin's runtime; its `bin` directory must be on
`PATH` (as it is when building with Cygwin GCC).

For a native Linux build, run these commands from this same `chapter31`
directory in WSL. Separate Java and native output directories let the Windows
and Linux builds coexist:

```sh
make lib tictactoe CLZDIR=build/classes-linux CTAGS=
./build/examples/x86_64_v2_SystemV/TicTacToe
```

From PowerShell, prefix either command with `wsl -d Ubuntu-24.04 --`.
On a standalone Linux checkout, `CLZDIR` can be omitted.
The compiler's `--link` option produces an executable without running it;
`--norun` continues to produce object files only.

`TicTacToe.smp` currently prints the welcome, square numbering, and empty board,
then waits for a move. Press **1–9** without Enter to place X, or
**q**, Escape, Ctrl-C, or Ctrl-D to quit. Enter is silently ignored; other
invalid keys and occupied squares prompt again without taking a turn. The board
is printed after each player and CPU move; only accepted move digits are echoed.
Play continues until someone wins or the board is
full. The CPU takes an immediate win, blocks an immediate loss, then prefers
the center, corners, and edges.

Other examples are Hello World (`A_helloWorld.smp`), `BubbleSort.smp`,
`Capitalize.smp`, `Dijkstra.smp`, and `FileIO.smp`. The latter creates, checks,
and removes `output.txt` in the working directory.

## Single-keystroke input

`sys.io.getch()` blocks for one byte without terminal editing or echo. It returns
0–255 for a byte, or -1 for EOF or an error. On -1, `sys.libc.errno()` is
`sys.libc.EOF` (-1) for end-of-input, otherwise the OS error code.
Terminal Ctrl-C and Ctrl-D are bytes 3
and 4, so the application decides how to handle them. Unicode and function keys
may produce multiple bytes; this is an ASCII-oriented byte API, not a key-event
decoder.

The small C runtime adapter uses [POSIX terminal modes](https://man7.org/linux/man-pages/man3/termios.3.html),
including Cygwin on Windows. It restores the saved mode before returning,
including after an interrupted read. Files and pipes use an ordinary one-byte
read. `make test CLASS=TerminalInputTest` runs the Java regression tests for
redirected input, errors, prompt output, Enter, and Escape. Check terminal
editing, echo, and mode restoration by running the executable in a terminal.
`make test CLASS=TicTacToeTest` checks complete games, all eight winning lines,
draws, occupied squares, and CPU blocking.
