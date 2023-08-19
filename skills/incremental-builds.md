# Incremental builds

Implementation notes and remaining build-driver work, reviewed 2026-10-25.

## Current reuse check

Chapter 28b's `CompUnit` decides whether to reuse a local object by walking its
recorded dependencies before loading saved types or IR. It checks all sources
owned by each local object, missing inputs, and dependency object timestamps.
Dependencies use the same local/library resolution for checking and loading.

For example:

1. A depends on B and has inlined some of B.
2. B's source changes, leaving B's object unchanged.
3. A client discovers A. The check follows A's recorded dependency to B and
   finds B's newer source, even though B's object has not changed.
4. A is reparsed, so its saved inlined copy of B is never accepted.

Use a fresh visited set for each candidate object. Cycles terminate safely,
but a provisional success inside a cycle must not be cached for another root.
Each source compilation unit now owns its own object; units compiled together
share a build timestamp so cycles do not invalidate themselves due to write order.
An unchanged public signature is insufficient: Simple imports implementation
details for optimization as well as declarations for type checking.

Object-only libraries are still supplied as a coherent build. Do not reject
them merely because one dependency has a newer installation timestamp: existing
tests compile cyclic libraries against stubs and then combine their real
objects. Validating those combinations requires saved input versions, not a
guess from file times. A newer library object does invalidate a source-backed
local dependent that can be rebuilt.

`IncrementalBuildTest` covers unchanged reuse, transitive source edits,
cycles, missing inputs, library dependencies, and clean/incremental native
execution parity. It sets file times directly, without sleeps.

## Required pieces

- Plan object targets and rebuilds across invocations. Handle cycles as groups
  and newly discovered dependencies. The compiler's per-object reuse check is
  not a complete scheduling or linking policy.
- Record actual input identities: source units and resolved library paths and
  versions. A search
  path change can select a different library even with an older timestamp.
- Record compiler and serialization versions, target, ABI, and relevant
  options. Content hashes would also detect changes with preserved timestamps.
  Specify which changes require rebuilding source and which reject an
  incompatible source-free library.
- Make build targets account for every output of a compilation request, which
  can discover several source units and produce a separate object for each.
- Provide an "ensure current" build operation. Explicit source compilation
  currently always recompiles. An unchanged build should do no compilation or
  linking; a changed object or link input should trigger relinking.
- Publish successful outputs without leaving a partial object accepted after
  failure, and keep the recorded inputs consistent with those outputs.

Initially, rebuilding every dependent of a changed object is a reasonable
conservative rule. Fine-grained interface/body fingerprints can come later.

## Suggested first implementation

Put a small build driver around the compiler with explicit object targets,
recorded inputs, and dependency propagation. Keep individual compilation
requests straightforward. A clean build and an incremental build must produce
the same observable program behavior.

Cover unchanged builds, direct and transitive source edits, inlined constants
and bodies, cyclic dependencies, multiple outputs, removed inputs, library-path
changes, compiler/target changes, and failed builds. Use controlled timestamps
or hashes instead of sleeps. Replace the ignored historical experiment with
tests matching the chosen object ownership model.

Library IR loading is independent of build and native link dependencies.
Summary-only imports still require coherent objects at link time, and inlining
does not erase the build dependency on the object supplying the copied body.
