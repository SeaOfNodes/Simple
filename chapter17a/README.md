# Chapter 17a: Mutability and Read-Only Views

English | [日本語](README.ja.md)

[Previous: Chapter 16](../chapter16/README.md) |
[Next: Chapter 17b](../chapter17b/README.md)

Chapter 16 gives an object its initial field values. Now we distinguish which
bindings may be reassigned and which references permit writes after construction.
These are independent questions, including for fields and array elements.
Chapter 17b will add inference and assignment shortcuts using these same rules.

Here is the [complete language grammar](docs/17-grammar.md) for this chapter.

## Two permissions

A modifier before a struct type names the access permission: `!Point` permits
writes, while `~Point` gives a deep read-only view. A modifier immediately before
the variable name controls that binding: `!p` permits reassignment, `~p` fixes it.
Whitespace does not change the meaning.

| Declaration | Reassign `p` | Write through `p` |
|---|---|---|
| `!Point !p` | Yes | Yes |
| `!Point ~p` | No | Yes |
| `~Point !p` | Yes | No |
| `~Point ~p` | No | No |

Primitives default to reassignable bindings. Struct references default to fixed
bindings and read-only access: `Point p` means `~Point ~p`. An initializer does
not change these defaults. The same syntax works for local variables and fields.

```cpp
struct Point { int x; int y; };
!Point p = new Point;   // Fixed binding, writable object
p.x = 3;                // Allowed
Point !view = p;        // Reassignable binding, read-only object view
view = new Point;       // Allowed, view is re-assignable
// view.x = 4;          // Error: view is read-only access
// p = new Point;       // Error: p is  fixed binding
int ~limit = 10;        // Explicitly fixed primitive
int count = 0;          // Mutable primitive by default
count = count + 1;
return p.x;
```

## Construction and fixed fields

A fixed field may be initialized in the struct declaration or the allocation's
constructor block.  A fixed primitive without a default requires init; a
non-null reference also requires a (not-null) value.  Nullable reference fields
may start at null.  Constructors are special, in that they set final fields in
ordinary looking code... but no other code can set final fields.


```cpp
struct Point { int ~x; int ~y; };  // x,y are final
Point p = new Point { x=3; y=4; }; // x,y set in constructor
// p.x = 99;                       // Error; p is final
return p.x + p.y;
```

The binding and access modifiers are independent even within a struct:
`!Point ~origin` is a fixed field containing a writable pointer. Replacing
`origin` and modifying `origin.x` are different operations.

## Deep read-only access and other aliases

A read-only view removes write permission from every reference reached through
it.  It cannot restore write access via any optimization or casting.  The
object is **not** frozen: another writable alias can still change it, and reads
through the read-only view observe those changes.

```cpp
struct Point { int x; };
!Point p = new Point;
Point view = p;
p.x = 3;
int before = view.x;
p.x = 5;       // Write to the shared object, via a mutable reference
return before * 10 + view.x; // 35
```

Consequently, read-only access does not make a Load constant or remove its
memory dependencies - the underlying object may still be mutable from another
reference.

## Arrays: a qualifier at each layer

Fresh arrays start with writable contents so their elements can be filled in.
`[]` retains that access; `[~]` requests a deep read-only view at that array
layer.  The variable itself defaults to a fixed binding, as other references do.

| Declaration | Meaning |
|---|---|
| `u8[] a` | Fixed binding to writable bytes |
| `u8[] !a` | Reassignable binding to writable bytes |
| `u8[~] a` | Fixed binding to a read-only array |
| `Point?[] a` | Writable entries containing read-only Point references |
| `!Point?[] a` | Writable entries containing writable Point references |
| `u8[~]?[] rows` | Writable outer entries containing null or read-only byte arrays |
| `u8[]?[~] rows` | Deep read-only outer view, including the nullable inner arrays |

The `?` is needed when declaring reference arrays: initially each entry
is null.  After initialization, such an array can be cast as read-only.
An outer read-only view also prevents writes through its inner arrays,
even when a writable alias to those arrays exists elsewhere.

```cpp
u8[] bytes = new u8[2];
bytes[0] = 7;
u8[~]?[] rows = new u8[~]?[1];
rows[0] = bytes;       // Outer array remains writable
// rows[0][0] = 9;     // Error: the inner view is read-only
bytes[0] = 9;          // Another alias can still write
return rows[0][0];     // 9
```

Writable array slots require matching element permissions. For example,
`!Point?[]` cannot be assigned to `Point?[]`: the latter could insert a read-only
Point which the former would then expose as writable. A read-only outer view
does not allow that insertion. Use `new !Point?[n]` when the elements must retain
writable Point references; `new Point?[n]` keeps the default read-only references.

## Representation

[ScopeNode](src/main/java/com/seaofnodes/simple/node/ScopeNode.java) and
[Field](src/main/java/com/seaofnodes/simple/type/Field.java) record whether a slot
is fixed.  Independently, [TypeMemPtr](src/main/java/com/seaofnodes/simple/type/TypeMemPtr.java)
records read-only access through a pointer. That permission stays attached when
an incomplete struct is resolved by name; it does not depend on how many fields
are currently known.  Struct definitions retain their immutable shallow
references from Chapter 12.

[ReadOnlyNode](src/main/java/com/seaofnodes/simple/node/ReadOnlyNode.java) removes
write permission without changing the pointer value. Loads propagate that
permission to reference results, including when an optimization forwards a
stored value. Stores check both the field's fixed-binding flag and the incoming
pointer's access permission.  The existing memory SSA representation is unchanged.
