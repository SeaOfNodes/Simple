# Chapter 33: Automatic Memory Management

English | [日本語](README.ja.md)

[Previous: Chapter 32 — Compiler Internal Speedups](../chapter32/README.md)

This chapter will explore automatic memory management. The design is still
open; no memory-management changes have been implemented yet. It starts from
the complete, runnable Chapter 32 compiler, libraries, examples, and tests.
The speedup measurements remain in [Chapter 32](../chapter32/README.md).

## Build and use this chapter

From `chapter33`:

```sh
make -j8 release
make -j8 sanity
make -j8 tests
```

`sanity` keeps the full lattice checks and focused compiler regressions.
Run the full suite before finishing a change. For a fully headless full run,
use `make -j8 tests_nographics` to disable MiniFB and miniaudio.

Run one test class, or enable every-step verification for an investigation:

```sh
make test CLASS=TypeTest
make test CLASS=WorklistTest "TEST_JAVA_OPTS=-XX:ActiveProcessorCount=2 -Dsimple.assert.expensive=1"
```

The [grammar and parser map](docs/33-grammar.md) describes this snapshot.
The [examples](docs/examples/README.md) include launch commands for the games.
Library references live with [drawing](src/main/smp/sys/draw/README.md),
[window/input](src/main/smp/sys/gfx.md), [audio](src/main/smp/sys/audio.md), and
[time](src/main/smp/sys/time/README.md). Future projects are in the
[roadmap](../docs/roadmap.md).
