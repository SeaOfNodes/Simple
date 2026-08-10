# Chapter 28a: Modules and Lazy Class Initialization

English | [日本語](README.ja.md)

[Previous: Chapter 27](../chapter27/README.md) |
[Next: Chapter 28b](../chapter28b/README.md)

[Next: Separate Compilation](../chapter28b/README.md)


The compiler can now discover several source files and compile them together.
Each file defines a class with one shared class object; files can nest in
directories forming a module structure.

The shared class object is a normal object with fields holding function pointers
or other state. The top-level code in the file forms the `<clinit>`
function, which runs once only on the first active use of the class - i.e.,
lazy class initialization. An initializer can trigger another class's
initialization and wait for it to finish. It cannot observe that other class
half-initialized. Cycles between class initializers are not allowed.

This chapter adds file and class semantics to the escape analysis we already
have. All required Simple source is available in one compilation.


## Example: Lazy class initialization, or Pay-As-You-Go

Suppose an application sometimes needs a `Table` but not often; many runs may
never need it.  Putting its construction in the application entry would make
every run pay (the eager-init model).  Putting it in a getter would need a test
and cached storage.  A class initializer provides `Table` lazily, on demand,
and once for all users of the class.  This is similar to the Java class init
model except nothing ever sees a class mid-initialization, except the class
itself.

The runnable example is in [`src/test/smp/lazy`](../../src/test/smp/lazy).
Its directory tree also defines the namespace:

```text
App.smp
App/Main.smp
App/Stats.smp
App/Tables.smp
```

`App.smp` names its children:

```simple
val Main=Main;
val Tables=Tables;
val Stats=Stats;
```

These are class references being stored into normal fields, to give easy access
to nested classes.  Merely storing the reference does not trigger the class
initializer.

`App/Stats.smp` supplies a visible count:

```simple
int builds=0;  // Static global counter
```

`App/Tables.smp` builds the table and exposes a function that reads it; the
array is marked private and its public function is the intended interface
(see the current access-check limitation below):

```simple
Stats.builds++;  // Number of table builds... only ever zero or 1!

// Build a private table of squares
int[] _squares = new int[256];
for(int i=0; i<256; i++) _squares[i]=i*i;

// Public accessor to get a square
val square = { u8 n -> _squares[n];};
```

`App/Main.smp` is the main entry:

```simple
return arg ? Tables.square(3)+Tables.square(4)+100*Stats.builds : Stats.builds;
```

With `arg == 0`, the result is 0 and `Tables` never initializes.  With `arg == 1`,
the result is 125: the two lookups produce 9 and 16, and the table is built once.
From this chapter directory, the test driver can evaluate it:

```sh
java -ea -cp "build/classes/main:build/classes/test:lib/*" \
  com.seaofnodes.simple.Simple --root src/test/smp/lazy --eval App/Main.smp 1
```

On Windows, use `;` instead of `:` between classpath entries.

The evaluator can also run the example directly after type checking:

```java
CodeGen code=new CodeGen("src/test/smp/lazy", "build/objs", "App/Main",
                         null, 123, true, TypeInteger.BOT)
    .driver(CodeGen.Phase.TypeCheck);
Eval2.eval(code,0); // "0"
Eval2.eval(code,1); // "125"; a fresh evaluation starts a fresh program
```

`BoundaryTest.testLazyTables` checks these results. `ClassInitDependenciesTest`
also runs native code that calls a class repeatedly in one process, checking
both the cold and already-initialized paths.

### Lazy initialization details

The implementation tracks a hidden `$init` variable in every class. It changes
from `false` to `true` at initializer entry, allowing the class's own helpers
to use it while initialization is in progress. The separate cycle check prevents
another class's initializer from reaching back into that unfinished class.
A check before each active use calls the initializer if needed. The optimizer
tracks the flag as a normal alias, distinguishes `false`, `true`, and `bool`,
and can sometimes remove the test and call.


## Name lookup

If a name misses in the file scope, the parser looks for a matching Simple
source file under the module root. It searches the current class's children,
then enclosing scopes. For example, in `App.smp`, the declaration
`val Main=Main;` defines a normal field, but the right-hand `Main` is unresolved.
The compiler finds `App/Main.smp`, which defines the referenced class.

`ParseAll` starts with the requested file and follows unresolved class names
and types to source files.  From `App/Main`, `Tables` can name the sibling
`App/Tables`; a longer name walks down the class tree.  Long dotted paths are
also allowed: `App.Tables`.  The parser also reads parents, since their
initialization must precede their children.  Cyclic source references are
allowed: the compiler closes recursive types after discovery.

`CompUnit` ties a source file, class type, and function graph together.  They
form a tree that mirrors the source directories and tracks which files need
loading and parsing. Each `CompUnit` has a `StartCU` and `StopCU`, beneath
the whole program's `Start` and `Stop`.

### Private

A leading underscore marks a field or class as private. The intended rule is
that private fields are visible only inside their declaring class, not to
siblings or parents. A private enclosing class also keeps its children out of
the public interface.

The current compiler uses these names to choose the public interface and seed
escape analysis, but does not yet enforce the source access rule. For example,
a sibling can currently read `A._secret`. Rejecting that access is still needed;
the underscore alone is not an enforced boundary in this snapshot.

Like in other languages, private fields allow you to hide implementation
details.  They can be exposed via the normal getter/setter route or any other
way that makes sense. Escape analysis can track private fields more precisely
when all uses stay in known code, helping the usual optimizations.

### Other names

The bundled `sys` library uses the same source mechanism. It is a normal module
in all ways except that lookup falls back to the bundled `src/main/smp` when
no local `sys` is found. Native `"C"` declarations still name functions
or data resolved by the native linker.


## Active use and initialization order

Reading or writing a class field, calling its method, or constructing an
instance needs the class initialized; a class reference alone does not.  The
parser inserts `CLInitNode` at these active uses, and uses the usual collection
of peepholes to optimize these away.  E.g. a forward reference *might* be a
class reference, so a `CLInit` is conservatively inserted.  Later the fref
resolves to a normal instance... so the `CLInit` will peephole away.
`CLInit` has peeps for removing repeated checks; for telling that the check
must always be `true` or `false`, etc.


Parents initialize before children.  In the example, `App` initializes before
`App.Main` and `App.Tables`.  Its passive references to the children do not run
them while `App` is still being initialized.

The compiler keeps `CLInit` opaque through optimization.  It carries control
and whole-memory effects, plus the fact that this class's hidden `$init` flag
is `true` afterward.  At the `Lower` pass, the `CLInit` explodes into a true
CFG diamond guarding the `<clinit>` call.  At this point we end up with a real
runtime guard check, i.e. real overhead. Removing redundant checks reduces
that cost; the first active use may still need a runtime check.


## Reject cycles that would observe unfinished classes

An initializer can use a different class after that class finishes initializing.
For example, `A` can start, initialize `B`, then resume. A cycle would instead
ask one of them to use the other before it finishes.

Passive references do not create initialization dependencies. These two sibling
files can refer to each other:

```simple
// A.smp
val b = B;
// B.smp
val a = A;
```

Reading each other's fields during initialization does create a cycle:

```simple
// A.smp
int value = B.value;
// B.smp
int value = A.value;
```

Parents add another dependency: a parent must finish before its child starts.
A parent initializer therefore cannot actively use its child. It can keep a
passive reference, or define a helper that uses the child when called after
the parent finishes. Calling that helper during parent initialization would
still create a cycle.

The resulting dependency graph is a DAG, but initialization remains lazy.
Execution respects the dependencies of each active use; classes that are never
actively used need not initialize.


After optimization and during `TypeCheck`, `ClassInitDependencies` follows
surviving `CLInit` markers and calls.  It checks strongly connected components
and reports cycles involving more than one class initializer.  Ordinary
recursion and recursive helpers within a single initializer are allowed.  Since
this is after SCCP, eliminated code cannot make a cycle. Surviving calls are
checked conservatively, even if a more powerful analysis could prove them dead.

After checking, `Lower` expands each remaining marker into a flag test and a
call on the cold path.  Parent initialization comes first. The initializer itself
sets the flag. Generated calls have conservative memory effects and do not
reopen SCCP's analysis of the optimized bodies.


## Emitting code and data

After cleanup by `FinishCalls`, the live code and data from the discovered
sources are emitted in one ELF file. Final constant fields are emitted as
static data in the ELF using the helper `StaticData`; commonly this includes
static class fields holding function pointers, but any constant data can be
stored here. These fields then do not need any `<clinit>` code.

Mutable field initialization remains in `<clinit>`, even if the initial value
is a constant. A final binding whose value is computed at runtime also needs
initializer code. For example, in `Theme.smp`:

```simple
{int} read_setting = "C"; // Supplied by the native application
val scale = read_setting(); // Final binding; value only known at runtime
```


## Build and review

From the repository root:

```sh
make tests CHAPTERS=chapter28a
```

Or, in this directory, `make lib` once for JUnit, then `make tests`. The suite
covers source discovery, initialization cycles and parent order, native cold/hot
calls, shared class storage, constructor reads and loops, private callbacks,
and x86/ARM/RISC-V code generation. `make view` starts the shared graph viewer.

The next chapter will keep this language behavior while allowing a client to
compile without the library's sources. That requires saving types and ideal IR,
remapping identities on import, and extending the same initialization checks
across imported bodies. The [appendices](../appendices/README.md) cover
library construction and optimizer tuning.
