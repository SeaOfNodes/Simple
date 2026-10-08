# Register allocation across chapters

Read a row from left to right to follow a fixed test cohort through successive
compilers. Columns identify the compiler/allocator chapter; each cell is its
sum of `_spillScaled`. Blank cells precede the cohort's introduction.
There is no total across different cohorts.

<!-- spill-matrix:start -->
| Test cohort | # tests | Ch 20 | Ch 21 | Ch 22 | Ch 23 | Ch 24 | Ch 25 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Ch 20 | 39 | 360 | 576 | 430 | 442 | 441 | 491 |
| Ch 21 | 52 |  | 1,072 | 960 | 961 | 948 | 1,077 |
| Ch 22 | 26 |  |  | 63 | 63 | 63 | 71 |
| Ch 23 | 24 |  |  |  | 103 | 103 | 152 |
| Ch 24 | 58 |  |  |  |  | 1,387 | 1,503 |
| Ch 25 | 14 |  |  |  |  |  | 1,862 |
<!-- spill-matrix:end -->

Here **# tests counts compilations**, including the same program on different
CPU/ABI combinations and separately recorded compilation phases. It is not the
number of JUnit methods. Every filled cell in a row uses the same number of
compilations and the same test/CPU/ABI membership, including zero-spill cases.
The reporter checks the multiset of those identities before emitting the table.
Jig methods are temporary debugging placeholders and are always ignored. Their
15 replay entries are excluded consistently: six from cohort 23 and nine from
cohort 24. The two cohort-23 Jigs duplicated one Fibonacci scratch program;
Chapters 23-24 discarded its default main, while 25 compiled it and an exported
function. Its removal eliminates 228 weighted moves from the Chapter 25 column
without changing the compiler.
Chapter 20's BrainFuck and MergeSort tests were moved into Chapter20Test in 21;
the reporter recognizes that rename.

`_spillScaled` counts retained SplitNodes, including register-to-register moves,
weighted by `8^loopDepth`. Lower is better; these are estimated move costs,
not measured memory traffic or execution time.

Measured on Windows x86-64, 2026-10-07. The earlier cohorts combine x86-64
SystemV/Win64 with ARM and RISC-V SystemV, at optimizer seed 123. Cohort 25
contains 13 recorded native-test allocations at their existing seeds and a
fresh system-library compilation at seed 456.

The rows compare whole chapter compilers. Optimizations, scheduling, ABI
lowering, and library representation also change, so a row need not decrease
at every step. In particular, Chapter 25 replays earlier programs with the
[documented syntax, constructor and visibility adaptations](../chapter25/src/test/java/com/seaofnodes/simple/spill/README.md).
The two Chapter 22 C-return ABI cases are included there as well, keeping that
row at 26 compilations. They cost no moves in 22-25 after Chapter 25's targeted
function-address rematerialization preference.
Use a controlled comparison within one compiler to isolate an allocator heuristic.

The Chapter 25 comparison below separates that allocator change from making
local helpers/classes private in eleven replay fixtures. The ranking trial uses
the same sources, seeds, targets and ABIs as its baseline. Private names remove
unneeded exported bodies and allocation factories; their effect is a change to
the lowered program, not an allocator gain. Export/API fixtures stay public.

| Test cohort | Before | Ranking only | + Private helpers | + Tiny-body check |
|---|---:|---:|---:|---:|
| Ch 20 | 608 | 608 | 491 | 491 |
| Ch 21 | 1,170 | 1,170 | 1,077 | 1,077 |
| Ch 22 | 127 | 99 | 71 | 71 |
| Ch 23 | 152 | 152 | 152 | 152 |
| Ch 24 | 1,558 | 1,545 | 1,506 | 1,503 |
| Ch 25 | 1,851 | 1,829 | 1,829 | 1,862 |

These four successful Chapter 25 reports have been recomputed from their saved
per-allocation records with the Jig entries excluded, leaving 199 replay
allocations. They also passed fresh library encoding and 23 native/system tests.
The final compiler also passes its complete
Make suite and a fresh SystemV library encoding on Windows. The earlier compiler
columns are unchanged.

The tiny-body check bypasses an oversized parse-time estimate only for recognized
straight-line boilerplate containing at most 15 nodes. Parsing an initializer
also builds its nested methods, so that estimate can greatly exceed the surviving
body. Dependencies on Return inputs and the bounded inspected prefix allow retries
after folding, without walking a large body on every change. The walk follows uses
from entry to Return, with bounded fanout, avoiding sparse memory input arrays.
This removes six constructor calls in the replay corpus, all in Bubble Sort:
estimates 276, 352 and 479 represented bodies of only 11, 11 and 14 nodes. Public
constructor bodies remain exported. The optimized-body limits remain 100 for
ordinary functions and 200 for `<new>`/instance `<init>`; selection order is unchanged.
It reproduces all 213 retained allocation records from the preceding 1,000-node constructor
parse-allowance trial, replacing that allowance with a bounded structural check.

This is a boilerplate reduction, not a uniform spill improvement. Bubble Sort's
weighted moves change by -10 on x86, +3 on ARM and +4 on RISC-V. The fresh Win64
library improves 842 to 809 and Dijkstra 584 to 582, while FileIO rises 293 to 361.
The separately checked SystemV library improves 803 to 777. The broader inliner
redesign is deferred until after the Chapter 25 split.

Chapters 20-24 also recognize these tiny bodies, copying only straight-line
stores and memory merges after ordinary peepholes settle. They already expand
constructor defaults directly, so there are no analogous constructor calls to
remove. Rerunning their full suites and allocation reports after this backport
changed no raw or scaled allocation record; the matrix above remains unchanged.

Run `make spill-stats` from the repository root to rerun all six chapters and
write logs plus `matrix.md` under `build/spill-stats/`. A failed test or changed
cohort membership makes the command fail; partial results are not published.
To refresh these tables after a successful run:

```sh
python build-support/spill-matrix.py --reuse --update-docs
```

Each Chapter 20-25 README contains the matrix up to that chapter. The per-chapter
`make spill-stats` commands still provide individual allocations and CPU/ABI sums.
