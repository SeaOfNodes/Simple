# Register allocation across chapters

Read a row left to right to follow a fixed cohort through successive compilers.
Each cell gives **Ops / RA / X**:

- **Ops:** executed ARM + RISC-V machine instructions, with fixed inputs.
- **RA:** executed instructions belonging to allocator-created copies,
  spills/reloads and rematerializations; a subset of Ops. A replacement constant
  materialization counts even when it adds no instruction relative to the
  original program. This measures allocator-generated code, not a net overhead.
- **X:** x86-only `_spillScaled`, counting retained split moves (including
  register copies) with weight `8^loopDepth`. It is a static estimate and does
  not count rematerializations.

**Cases D / X** counts dynamic program/target cases and x86 compilations.
Repeated historical compilation entries remain repeated. These are independent
coverage counts, not a sum; dynamic cases may use several inputs. Membership,
inputs and returned values are checked across compiler columns. Chapter 20 has
no encoder, so its dynamic cells are unavailable. There is no cross-cohort total.

<!-- spill-matrix:start -->
| Test cohort | Cases D / X | Ch 20: Ops / RA / X | Ch 21: Ops / RA / X | Ch 22: Ops / RA / X | Ch 23: Ops / RA / X | Ch 24: Ops / RA / X | Ch 25: Ops / RA / X |
|---|---:|---:|---:|---:|---:|---:|---:|
| Ch 20 | 22 / 13 | — / — / 161 | 55,173 / 5,407 / 174 | 58,751 / 9,099 / 175 | 58,736 / 9,084 / 184 | 58,720 / 9,068 / 183 | 59,043 / 9,094 / 209 |
| Ch 21 | 20 / 18 |  | 6,207 / 261 / 461 | 6,324 / 379 / 475 | 6,326 / 381 / 475 | 6,325 / 380 / 470 | 6,233 / 219 / 533 |
| Ch 22 | 14 / 8 |  |  | 198 / 60 / 7 | 194 / 56 / 7 | 194 / 56 / 7 | 332 / 136 / 11 |
| Ch 23 | 14 / 8 |  |  |  | 630 / 160 / 36 | 630 / 160 / 36 | 1,106 / 442 / 36 |
| Ch 24 | 36 / 20 |  |  |  |  | 4,923 / 1,341 / 790 | 5,044 / 1,470 / 609 |
| Ch 25 | 2 / 12 |  |  |  |  |  | 20 / 0 / 1,862 |
<!-- spill-matrix:end -->

Measured on Windows, 2026-10-07, using optimizer seed 123 for the historical
cohorts. The x86 Chapter 25 cohort retains its native-test seeds and the fresh
system-library compilation at seed 456. Dynamic counts include generated class
initializers and calls, but exclude the bodies of native `calloc`, `read` and
`write`. Their deterministic emulator stubs provide allocation and fixed I/O.
No clock timings, native-library instruction counts or code-size metric are mixed in.

## Inputs and coverage

| Cohort | Dynamic cases | Executions per compiler | x86 compilations |
|---|---:|---:|---:|
| Ch 20 | 22 | 44 | 13 |
| Ch 21 | 20 | 20 | 18 |
| Ch 22 | 14 | 14 | 8 |
| Ch 23 | 14 | 26 | 8 |
| Ch 24 | 36 | 48 | 20 |
| Ch 25 | 2 | 2 | 12 |

Newton, array-fill and Merge Sort use argument 16; allocation-index tests use
0, 1 and 2. Branch/short-circuit and nested-equality cases use 0-3, Fibonacci uses 9, and Sieve uses
100. Bubble Sort reads `[4, 3, 2, 1]`; BrainFuck runs its embedded Hello World
program. Other measured cases use argument 0. The Chapter 25 dynamic row is only
the two existing external-data emulator cases, with `counter` initialized to -7;
it does not represent execution of the whole system library or native suite.

String/Person APIs, the returned mixed-argument function, exported Merge Sort
and BrainFuck, and mixed/stack-argument API tests still need explicit emulator
drivers. Deliberately infinite programs remain compilation-only. The original
allocation cohorts are preserved; these cases are not silently measured as zero.
The previously excluded equality, ARM Newton and ARM Bubble Sort cases now run
in every applicable compiler column. The measurement harness checks their
answers, including nested equality at arguments 0-3, finite Newton results and
Bubble Sort's complete output. The shared ISA tests and full Chapter 21-25
Make suites pass after the backend/evaluator corrections.

## Remaining differences after fixture cleanup

The Chapter 24-to-25 comparison, using the same inputs and cases in each row:

| Cohort | Ch 24 Ops | Ch 25 Ops | Change |
|---|---:|---:|---:|
| Ch 20 | 58,720 | 59,043 | +0.6% |
| Ch 21 | 6,325 | 6,233 | -1.5% |
| Ch 22 | 194 | 332 | +71.1% |
| Ch 23 | 630 | 1,106 | +75.6% |
| Ch 24 | 4,923 | 5,044 | +2.5% |

Chapter 25 retains top-level bindings in the compilation unit's class object.
Initializing its fields generates stores, function-pointer constants and class
addresses, even when helper calls have already inlined and folded. Chapters
through 24 can eliminate these bindings as locals. Small programs make this
fixed initialization cost particularly visible.

For example, `Chapter23Test.testAnd` takes 12 instructions per target in 24 and
32 in 25. The extra 20 are four field stores, four instructions materializing
two function pointers, ten materializing the class address at five uses
(including the hidden call argument), and two integer constants. The actual
short-circuit expression has already folded in both chapters.

Across the entire Chapter 23 cohort, executed-node attribution accounts for
the **476** extra instructions:

| Generated work | Instruction change |
|---|---:|
| Field stores | +126 |
| Function-pointer materialization | +108 |
| Pointer-address materialization | +240 |
| Integer constants | +50 |
| Allocator copies/spills/reloads | +2 |
| Unconditional jumps | +2 |
| Calls, prologues/returns, extensions and loads removed | -52 |

Its RA subset rises **160 to 442**, comprising **224** extra pointer-address
rematerialization instructions, **56** extra integer rematerializations and
only **2** extra copies/spills/reloads. RA labels allocator-created code; it
does not mean all 282 extra instructions are stack traffic. `testAndPtr` also
loses a helper call per execution, so inlining has improved despite the larger
whole-program count. Moving constant initialization to static data would
remove much of this work; reusing class addresses could reduce the repeated
materialization. Neither optimization is included in this measurement update.

Both Bubble Sort targets improve: RISC-V **2,062 / 591 to 1,877 / 493**, and
ARM **2,040 / 579 to 1,868 / 478** (Ops / RA). Together they save 357 instructions;
the other Chapter 24 cases add 478, leaving a net increase of 121. Newton
integer improves **136 / 36 to 116 / 17** across both targets. In the Chapter 20
BrainFuck case, ARM adds 95 instructions: 88 net extra integer materializations
plus seven initialization instructions; its executed split count stays at 33.
These tables compare whole chapter compilers, including frontend and inlining
changes, rather than isolating allocator rankings.

## Reproduce

Run `make spill-stats` at the repository root to compile/run the static cohorts,
capture their historical sources, execute the dynamic cases and validate the
comparison. Capture uses temporary instrumented copies of the existing
SpillStats listeners under `build/dynamic-stats`; it does not edit chapter code.
The measured machine graph is stopped before allocation to identify subsequently
inserted copies/cloned constants. After encoding, their instruction ranges are
matched against the emulator's executed PCs, including multi-instruction expansions.

After a successful run, update the marked tables with:

```sh
python build-support/spill-matrix.py --reuse --update-docs
```

Per-program inputs, results and dynamic counts are written to
`build/spill-stats/dynamic-counts.csv`; emulator logs and explicit skips live
under `build/dynamic-stats/{21..25}/dynamic.log`. The x86 records remain in
`build/spill-stats/spill-matrix-{20..25}.log`. The source adaptations for Chapter
25 are documented in the [replay README](../chapter25/src/test/java/com/seaofnodes/simple/spill/README.md).
