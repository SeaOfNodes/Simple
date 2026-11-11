# Chapter 31: Software Drawing and Graphical Games

English | [日本語](README.ja.md)

[Previous: Chapter 30 — Text Formatting and Diagnostics](../chapter30/README.md)

Small graphical games drive this chapter's language and library work, starting
with a pixel window for a Simple-written 2-D graphics library. MiniFB supplies
the optional native window/input backend; drawing stays in Simple.

The [drawing library](docs/drawing.md) provides a pixel canvas, colors,
rectangles, circles, thick lines, and bitmap labels. The
[graphical tic-tac-toe game](docs/examples/TicTacToeGraphical.smp) uses it for
mouse/keyboard play against the terminal game's CPU strategy.
Hexadecimal colors (`0xe47b57`) and leading-zero octal font masks (`025755`)
are supported by this chapter's integer literal syntax.

```sh
make -C chapter31 run-graphical-tictactoe CTAGS=
```

This explicitly builds the optional backend and opens a window. Use
`graphical-tictactoe` instead to build the executable without starting it or
requiring MiniFB.

The default build and test suite need no graphics dependencies. See
[optional graphics](docs/graphics.md) for building MiniFB, running the pixel
example, and handling a missing backend. `make tests_nographics CTAGS=` runs
all ordinary tests with graphics explicitly disabled.

The inherited language and library are described in [Chapter 30](../chapter30/README.md).
The [grammar and code map](docs/31-grammar.md),
[source layout](src/main/java/com/seaofnodes/simple/README.md), and
[runnable examples](docs/examples/) describe this snapshot.

From the repository root:

```sh
make lib tests release CHAPTERS=chapter31 CTAGS=
make -C chapter31 tictactoe CTAGS=
```

The game binary is `chapter31/build/examples/x86_64_v2_win64/TicTacToe.exe`
on Windows and `chapter31/build/examples/x86_64_v2_SystemV/TicTacToe` on Linux.
For a WSL build in the same checkout, use `CLZDIR=build/classes-linux` to keep
Linux Java outputs separate from Windows outputs.

IntelliJ has a `chapter31` module and a **chapter31 tests** run configuration.
Inherited Java test names retain their historical chapter numbers.
