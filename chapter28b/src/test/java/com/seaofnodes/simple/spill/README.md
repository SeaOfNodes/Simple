# Historical allocation cohorts

English | [日本語](README.ja.md)

`cohorts.tsv` freezes 199 compilation entries matching Chapter 24's
`SpillStats`: 39, 52, 26, 24, and 58 entries for cohorts 20-24. The source files
are deduplicated by contents and named for their first referring test. The
manifest preserves test identity, target, ABI, and the original stopping phase.
`Win64` becomes `win64`, the Chapter 25 ABI spelling. Argument type is i64 and
the replay seed is 123 throughout.

The original 212 entries were captured from the Chapter 24 tests before the
allocator audit's new correctness regressions. The 2026-10-07 refresh adds
Chapter22Test.testNarrowCReturns on x86 SystemV and Win64, matching the two
entries already measured in Chapters 22-24. The source is unchanged; only the
manifest's ABI spelling changes. The [cross-chapter matrix](../../../../../../../../docs/regalloc-spills.md)
checks matching test/CPU/ABI membership, including repeated compilations.
The same refresh removes all 15 Jig entries: six from cohort 23 and nine from
cohort 24. Jig methods are ignored scratch pads for debugging, not regression
tests or allocation benchmarks. Their frozen source copies were removed too.
These inputs are a stable comparison corpus; changing one
requires explaining the resulting break in comparability. Synthetic allocator
and instruction regressions do not contribute allocations to these cohorts.

Chapter 25 cannot parse all earlier sources unchanged. Adaptations are explicit:

- String, Scan, and linked S constructors replace `new S { field=...; }` with
  declared constructors and arguments. The explicit-receiver scanner functions
  become instance methods, preserving their operations.
- The saved `Chapter21Test-testStringExport`, `Chapter21AllocTest-testString`,
  and `Chapter23AllocTest-testString` sources declare
  `new String = { u8[] data -> cs=data; };` to initialize the required non-null
  `cs` field. This repairs ten replay entries (four, three, and three), without
  changing their equality/hash bodies or manifest membership. These adapted
  sources are a new comparison baseline; their totals cannot be compared as
  identical inputs with the earlier constructor-free fixtures.
- The two short-circuit RHS initializer loops become equivalent guarded blocks,
  preserving allocation, side effects, and the final result. Their RHS assigns
  the field value directly: Simple's `||` returns an operand, not a normalized
  boolean. The 2026-10-07 cleanup removes an erroneous `!!` in those adaptations;
  both fixtures return 44 for even arguments and 1 for odd arguments.
- Simple I/O cases include the old `write` binding and print helper directly.
  Bubble Sort includes the old library bodies, with namespace prefixes flattened,
  explicit constructors, and mutable replacement buffers. This retains the old
  workload instead of substituting Chapter 25's substantially revised example.
- Local implementation names are private in eleven fixtures: the four Newton
  variants that call their helper from the top level, Chapter 20's MergeSort,
  String and Cast programs, and Chapter 22's three sign-extension programs and
  InfiniteReturn. Their helpers/classes acquire an underscore prefix; operations,
  inputs and final results are unchanged. This avoids exporting implementation
  functions and allocation factories solely because Chapter 25 treats top-level
  declarations as module members. Export/API fixtures, including NewtonExport,
  StringExport, Person and the C-return wrappers, retain their public names.
  These visibility adaptations change the generated workload and are recorded
  separately from the allocator improvement in the cross-chapter comparison.
- The remaining visibility cleanup makes local classes/functions private in
  AntiDeps1, Infinite, the Chapter 21/23 allocation String cases, the Chapter 23
  scanner/pointer/short-circuit/function cases, and Stack3/Stack5. Bubble Sort's
  copied library implementation is private; its `main` stays public. The three
  adapted String API fixtures end in `return 0;`, avoiding an accidental return
  of the last declared function. Their equality/hash APIs stay public, and the
  named StringExport fixture also keeps its public String factory. No cohort
  entries, workload operations, or intentional export APIs are removed.
- Other source texts are retained.

All entries replay **through RegAlloc**, checking scheduled register constraints.
The historical phase column is provenance, not a promise to run those old native
harnesses in Chapter 25. The adapted Bubble Sort retains its historical
`i32 errno="C"` declaration for allocation comparability. Direct data bindings
now encode on all three targets, but this declaration cannot link to a libc
whose errno is thread-local or accessor-based. Current system-library code uses
`libc.errno()` instead. Do not count this corpus as historical runtime coverage.

`SpillStats` then compiles the current system library through encoding at seed
456, and runs Chapter25Test with its existing seeds and native checks. Together
these form cohort 25. A library or test failure makes the report fail; partial
totals cannot establish an improvement. The ordinary full chapter suite remains
the execution validation for the current compiler.
