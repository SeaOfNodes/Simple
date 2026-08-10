# Chapter 28b: Separate Compilation

English | [日本語](README.ja.md)

[Previous: Chapter 28a](../chapter28a/README.md)

The previous chapter compiled every discovered source file together.  This
chapter lets a program and the libraries it uses compile separately, with
full type information.  To compile a program that uses a library, we need the
library's *object* file, but not its source.

In spirit this is similar to Java's *class* file, with its full execution and
type semantics stored in the file; we happen to be using an ELF file for the
same reason.  The ahead-of-time compiled code is *also* available for the
linking step.

An ordinary native object contains machine code, data, symbols, and
relocations.  Simple adds a `.simple` section with types and the ideal IR.
Importing `.simple` lets the compiler check types and optimize calls into the
library.  Calls that remain out of line use the library's machine code at
native link time.

## A program that uses a library

The runnable example has two separate source roots:

```text
src/test/smp/separate/library/Counters.smp
src/test/smp/separate/client/Main.smp
```

`Counters.smp` declares shared state and an instance constructor:

```simple
int calls=0;
val next_noInline = { -> calls++; return calls; };

struct Counter {
    val value=1;
    new Counter = { int n ->
        value=0;
        for( int i=0; i<n; i++ ) value=value*2+1;
    };
    val get = { -> value; };
};
```

`Main.smp` uses both parts of the library:

```simple
val run = { int n ->
    if( !n ) return 0;
    val c = new Counters.Counter(n);
    return c.get()+100*Counters.next_noInline();
};
return run(arg);
```

At runtime, `run(0)` returns without reaching or initializing `Counters`.

For `run(3)`, `Counters` initializes, the constructor computes 7 and
`calls`==1, so we get `(7+100*1)` or 107.

Calling `run(3)`, then `run(5)`, in one process increments the shared `calls`
twice.  The first call gives `(7+100*1)`, or 107; the second gives
`(31+100*2)`, or 231.

(The `_noInline` suffix prevents inlining, so the example exercises native
linking as well as imported IR.)

From this chapter directory, build the compiler and compile the library:

```sh
make lib
make build/classes/test/.ttag
java -ea -cp "build/classes/main:build/classes/test:lib/*" \
  com.seaofnodes.simple.Simple --norun -o build/separate-example/lib/ \
  src/test/smp/separate/library/Counters.smp
```

Then compile and evaluate the program using that object:

```sh
java -ea -cp "build/classes/main:build/classes/test:lib/*" \
  com.seaofnodes.simple.Simple --eval \
  -L build/separate-example/lib/Counters.o \
  src/test/smp/separate/client/Main.smp 3
```


## What crosses the separate compilation boundary

Each source compilation unit gets its own object file: compiling `sys.smp`
produces `sys.o`, `sys/io.o`, `sys/libc.o`, and any other discovered classes.
Mutually dependent sources are compiled together and they output to separate
files with a common build timestamp.

The object file has a compact encoding of the optimized IR in the `.simple`
section.  Declarations and function summaries come first so they can be read
without reading all of the IR.  The saved state includes canonical declarations
as well as optimized flow types.  E.g. `int x=7` has declared type `int` and
initial value `7`.  Struct field types (both declared and optimized), function
argument types (both declared and optimized), numeric modes, store widths, and
source locations also survive serialization.

Each loaded `.simple` section restores `CompUnit`s into the shared tree,
mirroring the original code layout.  A dependency shared by several objects
uses the same `CompUnit`.  Loaded aliases and function indices are renumbered
to avoid collisions; all the dense indices end up in the same namespace.
`GlobalBits` has the mapping from the imported file indices to the local
compilation indices; this remapping extends everywhere through the loaded code,
including recursive types that span several files.  Importing skips parsing the
original source, but loaded IR is still analyzed within its new context.

`ParseAll` is the workhorse that discovers source and object dependencies.  It
tracks missing symbol references, looking them up in the search path one by one,
and loading the matching files.

The end result combines locally parsed and imported IR in one graph, while
keeping their separate `CompUnit`s.  This combined compilation can take
advantage of e.g. constants in the library, or inline library code using the
normal inlining heuristics.  The combined graph goes through the normal
compilation phases, including peepholes, Opto and TypeCheck.

Dependencies between `CompUnit`s are recorded.  An explicitly requested source
file is always compiled.  Before reusing another local object, `CompUnit` walks
its recorded dependencies and checks each local object's source.
A missing input or a newer source or dependency object forces parsing
again, before saved types or IR are imported.  This includes transitive
dependencies: if A has inlined B, editing B's source invalidates A even while
B's object is unchanged.  A visited set lets this check follow cycles.

Library dependencies use the same search paths for checking and loading.
Object-only libraries must be supplied as a coherent build; timestamps alone
cannot establish their compatibility.  These checks are not a complete build
driver: explicit requests still compile, and input hashes, compiler/ABI
versions, and automatic relinking remain future work.

An unloaded function has a `FunNode`, `SummaryNode`, and `ReturnNode`. Its
summary supplies result, memory, and escape information; calls keep the usual
graph structure without requiring the body. Summaries cannot be inlined.
At an empty optimization worklist, an interesting local call can request its
callee's object using saved size information. The compiler loads the bodies,
reconciles identities, and analyzes them before deciding whether to inline.

For `sys.io.p`, the compiler can leave `p` out of line without loading `io` IR.
Loading `io.o` makes its functions available, but references inside them carry
types without automatically loading other bodies. An unused imported function
does not cause speculative loading of its callees. Metadata discovery remains
conservative, and a requested object still loads all of its function bodies.



## Using library code and shared state

The compiler can use library IR to optimize a program while leaving the
library's functions and shared data in their own object files.  Separate
compilation must also preserve when each class is initialized, even when
several files use it.

For example, compiling `helloWorld` may load the body of `sys.io.p` to decide
whether to inline it or not.  Inlined code becomes part of `helloWorld.o`.
Ifif it was not inlined that call remains using the function in `sys/io.o`.
Loading did not add another stand-alone copy of `p` to `helloWorld.o`.

In any case, loaded or not, inlined or not, the initialization semantics of
`sys.io` is unchanged; this means we might need CLInit checks in the inlined
code, or where we load `p` from `sys.io`.

If we did load a bunch of IR, but then chose not to inline, that imported code
is still in our compiled graph.  We use `Unlink` before code-gen to remove the
imported bodies.  The not-inlined call sites will keep the target names around
for the system linker to eventually connect all the code.

Shared state follows the same idea as function calls.  In the earlier example,
all calls within the program use the same `Counters.calls` field.  The library
also supplies a shared `$init` flag so its class initializer runs once.
Importing types or loading IR does not run that initializer: execution must
reach a use that needs the class.

A nested class's initializer first ensures that its enclosing class is
initialized, then sets its own flag and runs its body.

The IR represents an initialization check with a `CLInit` node.  Simple saves
this form in the object file so the compiler can analyze it when compiling
a program that uses the library.  For machine code, `Lower` turns the check
into a flag test and a conditional call; Eval2 performs the same steps
directly.  Optimization can remove a check when the class is
already known to be initialized.

The compiler must also reject cycles in which class initializers depend on one
another.  Simple keeps *function summaries* that list calls and classes that
may need initialization, so the compiler can check for cycles before loading
bodies.  After ordinary optimization, any remaining possible cycle is an
error; the compiler does not load more bodies to try to disprove it.  Inlining
and other optimizations can remove a dependency, so changing optimization
decisions can change whether a program passes this check.

All required object files must be found before execution.  Native code is
linked before the program starts.  Eval2 can wait until a call needs a body
to decode its IR, but the object's types and dependencies have already been
read.  These choices change when code is prepared; both execution paths use
the same shared state and class-initialization rules.


## Building and checking

From the repository root:

```sh
make tests CHAPTERS=chapter28b
```

The suite includes the preceding constructor and escape regressions, imported
constructors, initialization cycles across objects, shared static data, native
function thunks, and native programs linked with libraries. Make also builds
the modular `sys` objects used by the library tests. Programs can import
`sys.o` with `-L`, without parsing the standard library's source again.
`IncrementalBuildTest` checks reuse, transitive edits, cycles across objects, missing
inputs, and changed library objects using controlled timestamps. It also
compares native execution after an edit with a clean build.
`SelectiveLoadingTest` checks summarized calls, loading for inlining, native
link dependencies, and rejection of a possible initialization cycle without
loading more bodies.
`make release` builds the launcher jar and standard library; `make view`
starts the shared graph viewer.

The serialized format is internal and currently unversioned beyond its `C0DE`
magic. Rebuild Simple objects when the compiler or layout changes. The expensive
serialization bijection check remains disabled, and `testModule0`, the older
incremental-rebuild experiment, remains ignored. They are not validation
guarantees for this snapshot.

See the appendices on [native libraries](../appendices/native-libraries.md)
and [optimizer/backend tuning](../appendices/optimizer-tuning.md).
