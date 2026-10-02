# Historical allocation cohorts

`cohorts.tsv` freezes the 212 compilation entries measured by Chapter 24's
`SpillStats`: 39, 52, 24, 30, and 67 entries for cohorts 20-24. The source files
are deduplicated by contents and named for their first referring test. The
manifest preserves test identity, target, ABI, and the original stopping phase.
`Win64` becomes `win64`, the Chapter 25 ABI spelling. Argument type is i64 and
the replay seed is 123 throughout.

These inputs were captured from the Chapter 24 tests before this final audit's
new correctness regressions. They are a stable comparison corpus; changing one
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
  preserving allocation, side effects, and the final result.
- Simple I/O cases include the old `write` binding and print helper directly.
  Bubble Sort includes the old library bodies, with namespace prefixes flattened,
  explicit constructors, and mutable replacement buffers. This retains the old
  workload instead of substituting Chapter 25's substantially revised example.
- Other source texts, including both original Newton float variants, are retained.

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
