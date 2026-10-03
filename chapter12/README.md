# Chapter 12: References

[Previous: Chapter 11](../chapter11/README.md) |
[Next: Chapter 13](../chapter13/README.md)

Chapters 10 and 11 gave structs integer fields and represented their memory
effects in SSA. This chapter lets fields contain references to other structs,
including structs whose definitions have not yet appeared. Linked lists and
mutually referring objects become possible without changing the memory graph.

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter12)
in the linear Git history and [compare it with Chapter 11](https://github.com/SeaOfNodes/Simple/compare/linear-chapter11...linear-chapter12).

## Typed fields and forward references

```java
struct Person {
    int age;
    FamilyTree? tree;
}
struct FamilyTree {
    Person? father;
    Person? mother;
}
```

The parser's `TYPES` table maps names to types. It initially contains `int`
(and any other primitive types, more are showing up in later chapters).  When
`type()` recognizes a declaration using an unknown name, it installs a
name-only `TypeStruct`, with `_fields == null` - this a struct where we only
know the struct name, and no other information.  A pointer to that struct is
enough to describe a reference field,`?` allows the pointer to be null.

`parseStruct()` collects all fields before installing the complete struct in
`TYPES`. It replaces the table entry; it does not mutate the old type or
publish successive prefixes of the field list. Field aliases are allocated
with the completed declaration, as before.

See [the parser](src/main/java/com/seaofnodes/simple/Parser.java), particularly
`type()`, `parseField()`, and `parseStruct()`, and the
[grammar](docs/12-grammar.md).

## Recursive structs: `L0` and `L1`

```java
struct LLI { LLI? next; int i; }
```

Two descriptions are useful for remembering what the compiler actually builds:

```text
L0 = TypeStruct("LLI", fields = null)
L1 = TypeStruct("LLI", fields = { next: pointer-to-L0?, i: int })
TYPES["LLI"] = L1
```

`L0` is created when parsing the `LLI? next` field. After the closing brace,
`L1` replaces it in the name table. The `next` field inside `L1` still points
to `L0`; it is not patched to point back to `L1`. 

The design is **immutable shallow references, resolved by name when needed**.
The runtime objects may form a cycle; their compiler type descriptions remain
finite and acyclic. This is an intentional decision to **stop recursive type
expansion**. Recursively substituting the completed definition into every
reference would either expand forever or require cyclic type interning,
equality, hashing, and lattice operations (which indeed show up in a lattice
chapter).

[`TypeStruct`](src/main/java/com/seaofnodes/simple/type/TypeStruct.java)
instead stops at the name-only description. Its `dual()` and `glb()` leave that
description alone. Meeting a complete description with a name-only description
of the same struct yields the name-only description.  That loses field detail
without losing the struct's identity.  There is no pass trying to expand `L0`
into an ever deeper sequence of `L1`, `L2`, and so on.

## Resolving a shallow reference

When parsing `head.next`, `parsePostfix()` takes the pointer's struct name and
looks up the current declaration in `TYPES`. It gets the field layout and alias
from that declaration, even if the pointer's own type still contains `L0`.
Doing the lookup again for the next field access is sufficient for a chain
such as `head.next.next.i`.

For a local declaration or assignment, `parseExpressionStatement()` also
resolves a name-only pointer before checking assignment compatibility. This
"deepening" changes the temporary type used for the check; it does not rewrite
the expression node's type or replace all old references in the graph.

```java
struct LLI { LLI? next; int i; }
LLI? head = null;
while (arg) {
    LLI x = new LLI;
    x.next = head;
    x.i = arg;
    head = x;
    arg = arg - 1;
}
if (!head) return 0;
LLI? next = head.next;
if (next == null) return 1;
return next.i;
```

The null checks refine the pointer on the surviving control path. In
particular, the false path of an `if` must record its refinement even when
there is no explicit `else`. Resolving a name supplies the field definition;
it does not prove that the pointer is non-null.

## Initialization and incomplete definitions

`newStruct()` initializes each field using its type's `makeInit()`: zero for
integers and null for references. Loads and Stores use the same aliases and
memory optimizations as Chapter 11. A Load from an initializing Store can
therefore fold to null.

An unused forward reference need not ever be defined:

```java
struct Holder { Missing? ref; }
Holder h = new Holder;
return h.ref;                  // null; no definition of Missing is needed
```

There is no way here to construct a non-null `Missing` without its complete
definition: `new Missing` is rejected. Accessing one of its fields also needs
a definition, as well as a non-null pointer. Simply mentioning a nullable
reference type does not impose a requirement to define it before the end of
the program.

There is one temporary initialization allowance in this chapter:

```java
struct N { N next; int i; }
N n = new N;
return n.next;                 // null, despite the non-null field declaration
```

The initializing Store may write null even into a non-null reference field.
An ordinary `n.next = null` is rejected. Chapter 16 adds constructors and
checks initialization; this chapter keeps the distinction explicit in
[`StoreNode`](src/main/java/com/seaofnodes/simple/node/StoreNode.java).

The [reference tests](src/test/java/com/seaofnodes/simple/Chapter12Test.java)
exercise linked lists, mutual references, null checks, and incomplete types.
Run them with `make tests`; `make view` displays the same unscheduled graph.
Chapter 13 now has the memory and reference semantics it needs to introduce
global code motion, including load/store anti-dependencies.
