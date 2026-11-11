# Runnable examples

These programs live beside the documentation so readers can find and run them.
Their non-graphical native regression tests run as part of `make tests`.
The optional [pixel-window example](Graphics.smp) uses MiniFB; see
[graphics setup](../graphics.md). `make run-graphics` opens it explicitly.

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
