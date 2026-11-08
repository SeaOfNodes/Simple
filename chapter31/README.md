# Chapter 31: Work in Progress

English | [日本語](README.ja.md)

[Previous: Chapter 30 — Text Formatting and Diagnostics](../chapter30/README.md)

This chapter starts from the completed Chapter 30 compiler and standard library.
Its next topic is open; the [roadmap](../docs/roadmap.md) lists the candidates.

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
