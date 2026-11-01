# Chapter 29: Tech Debt Cleanup

English | [日本語](README.ja.md)

[Previous: Chapter 28b — Separate Compilation](../chapter28b/README.md)

This chapter makes the compiler easier to read and change while preserving
Chapter 28b's language and separate-compilation behavior. It starts by breaking
the parser into grammar-sized pieces. Further cleanup belongs here; earlier
chapters retain their teaching snapshots.

## Following the grammar

The old `Parser.java` had grown beyond 3,000 lines, mixing recursive descent,
name resolution, scope management, and IR construction. The
[`parser/` package](src/main/java/com/seaofnodes/simple/parser/README.md) separates
those responsibilities without introducing an AST: parsing still builds
Sea-of-Nodes IR directly.

`Parser` owns the source cursor, active scope, type registry, and common token
and IR helpers. Each component shares that state through `Parser P`.
`Statements`, `Expressions`, `Declarations`, `Types`, `Functions`, and `Structs`
follow the corresponding grammar productions. `References` handles name and
field resolution; `Allocations`, `Conversions`, and `ControlFlow` hold shared
graph-building operations. `Lexer` owns tokenization and source locations, and
`Var` records lexical bindings.

The [grammar and code map](docs/29-grammar.md) connects productions to methods.
Start with `Statements.parseStatement` or `Expressions.parseAssignment` and
follow the calls alongside it. Deferred parsing remains explicit: `for` updates
are parsed after their bodies, and declared constructors after field discovery.

The [source layout](src/main/java/com/seaofnodes/simple/README.md) describes the
other packages. `IterPeeps` now lives in `codegen/` beside the compilation passes.

## Checking constructor completion

`addReturn` merges field values across exit paths. After `parseBody` finishes,
`Structs.parseInitializer` checks the completed helper Returns; no separate exit
maps survive scope teardown. Required fields without an assignment on every
returning path retain `TOP` in private memory. They emit no Store, so numeric
conversions cannot hide the missing assignment.

The declaration initializer and user constructor have separate Returns. Check
the user constructor when present, or the declaration initializer for the default
constructor. The older same-named constructor binding can precede field
declarations; fields absent from its Return use the declaration initializer's
result. Nonreturning helpers require no completed object. Completion errors
point to the relevant helper's source location.

`ConstructorCompletionTest` covers partial numeric assignments, early returns,
nonreturning paths, declaration defaults, and fields declared after constructors.

## Further cleanup

Source privacy is deferred. Leading underscores retain the existing export
filtering behavior; this chapter does not yet enforce access restrictions on
source uses. The [roadmap](../docs/roadmap.md) retains that proposal and the other
language candidates.

## Build and test

From the repository root:

```sh
make lib tests release CHAPTERS=chapter29 CTAGS=
```

The suite includes the inherited compiler, evaluator, native execution,
serialization, and separate-compilation tests, plus parser regression tests for
deferred fragments. Historical test class names retain their original identities.
The [Chapter 28b library/client example](../chapter28b/README.md) also runs from
this chapter directory.

IntelliJ has a `chapter29` module and a **chapter29 tests** run configuration.
Build the native runtime and `sys` library with Make before debugging tests
that need them; see [the IntelliJ setup](../docs/intellij.md).
