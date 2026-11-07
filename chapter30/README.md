# Chapter 30: Work in Progress

English | [日本語](README.ja.md)

[Previous: Chapter 29 — Tech Debt Cleanup](../chapter29/README.md)

This chapter starts from the completed Chapter 29 compiler. Its next topic is
not yet selected. The parser organization, constructor checks, partial field
views, and regression tests are inherited unchanged.

The [grammar and code map](docs/30-grammar.md),
[source layout](src/main/java/com/seaofnodes/simple/README.md), and
[construction notes](docs/construction-groups.md) describe the starting point.
Open projects are listed in the [roadmap](../docs/roadmap.md).

## Build and test

From the repository root:

```sh
make lib tests release CHAPTERS=chapter30 CTAGS=
```

IntelliJ has a `chapter30` module and a **chapter30 tests** run configuration.
Build the native runtime and `sys` library with Make before debugging tests
that need them; see [the IntelliJ setup](../docs/intellij.md).
Inherited test class names retain their historical identities.
