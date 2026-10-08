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
| Ch 20 | 22 / 13 | — / — / 161 | 55,173 / 5,407 / 174 | 58,751 / 9,099 / 175 | 58,736 / 9,084 / 184 | 58,720 / 9,068 / 183 | 58,965 / 9,076 / 208 |
| Ch 21 | 20 / 18 |  | 6,207 / 261 / 461 | 6,324 / 379 / 475 | 6,326 / 381 / 475 | 6,325 / 380 / 470 | 6,196 / 219 / 534 |
| Ch 22 | 14 / 8 |  |  | 198 / 60 / 7 | 194 / 56 / 7 | 194 / 56 / 7 | 252 / 92 / 11 |
| Ch 23 | 14 / 8 |  |  |  | 630 / 160 / 36 | 630 / 160 / 36 | 864 / 358 / 36 |
| Ch 24 | 36 / 20 |  |  |  |  | 4,923 / 1,341 / 790 | 4,646 / 1,335 / 609 |
| Ch 25 | 2 / 12 |  |  |  |  |  | 20 / 0 / 1,856 |
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

## Remaining initialization costs

Chapter 25 now places declared constant final fields directly in static data,
including function addresses, and omits their initialization stores. The same
fixed inputs and test cases give:

| Cohort | Ch 24 Ops | Ch 25 Ops | Change |
|---|---:|---:|---:|
| Ch 20 | 58,720 | 58,965 | +0.4% |
| Ch 21 | 6,325 | 6,196 | -2.0% |
| Ch 22 | 194 | 252 | +29.9% |
| Ch 23 | 630 | 864 | +37.1% |
| Ch 24 | 4,923 | 4,646 | -5.6% |

Chapter 25 retains top-level bindings in its class object; earlier chapters can
eliminate them as locals. Mutable fields and final fields whose declarations
do not identify their exact constant still need runtime initialization. This
fixed cost remains visible in small programs even after helper calls inline.

For cohort 23, the RA subset is 160 in Chapter 24 and 358 in Chapter 25. It
includes allocator-created constant/address rematerialization as well as copies
and stack traffic; it must not be read as a count of spills alone. These tables
compare whole chapter compilers, including frontend and inlining changes.

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
