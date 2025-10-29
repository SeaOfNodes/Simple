# Chapter 25: Modules


You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter25) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter22...linear-chapter24) it to the previous chapter.


## Modules

Goal: A Module plan (not yet a "system")
No syntax for the start 1-file helloWorld.smp "module"
External symbols are in ".o" files, like Java ".class" - not an source file #include like C/C++

Needs a search strategy for ".o" files, so they mirror the source code directory structure.
Means there is a "build" root for .o files.
Means the CLI has a --flag for the build root, or some sensible default ($cwd/build)

Projects with subdirectories pick up all symbols qualified names from the path-to-project-root



## more

Has a defined project root (default: CWD).

Has a path to root: %PATH, e.g. $ROOT/a/b/c/

Has a file name: $ROOT/ a/b/c/  foo.smp

Everything in a dir is implicitly in name-space above it.

Suppose `foo.smp` contains `PI=3.14;`

Then the fully qualified name is: a.b.c.foo.PI

Code is implicitly in the `struct a.b.c.foo {}` namespace.

Final constant values can be accessed by direct names.

Non-finals or non-constant need a constructed object.



Top-level code in `foo.smp` is implicitly in the foo class init,
which runs the first time foo is touched,
which requires a "has been init" touch on every possible-first-access,
which is every non-local ("sideways") reference.

Always the init for parents are done before children,
so `a` completes before `a.b`, and so on.

Means `a` init can NOT reference e.g. `b,c,foo`, although
you can define top-level fcns that refer to `b`.

Legal in `a.smp`:  `val make_b = { -> new b{} };` // fcn is final constant

Legal in `a.smp`:  `val      b =      new b{}  ;` // not constant, so requires instance & <clinit> is done

Means making an `a` instance in a's <clinit>, will also run a's <init>
which can refer to instance fcns, which can refer to not-init finals?
Limit: all fcns can only refer to instance vars *before* them.



Would like to make golden instances, static singletons:
val special = new a{/*anything already in-scope and <clinit>ed*/};



The default 1-file main then:
$ROOT==$CWD
Code is implicitly in the *empty* name space, is in the <clinit> for the empty
name space and gets run on project start.

------

Implications for e.g. sys:

    .../simple/sys.smp - top level, has sys.parseAryI64
     ../simple/sys/sys.io.smp - has `struct io { ... }`


For now just gather the exported TYPE symbols, and a count of them, and export/input ELF.
NO: for now split into `sys` class from instance, 