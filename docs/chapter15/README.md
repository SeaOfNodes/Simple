# Chapter 15: Arrays

[Previous: Chapter 14](../chapter14/README.md) |
[Next: Chapter 16](../chapter16/README.md)

# Table of Contents

1. [Parser changes](#parser)
2. [TypeArray is TypeStruct](#type-array-is-typestruct)
3. [Allocation and partial memory](#allocation-and-partial-memory)
4. [New Address Math](#new-address-math)
5. [Struct Layout](#struct-layout)
6. [Some Simple Address Math Peeps](#some-simple-address-math-peeps)
7. [Discussion](#discussion)


At long last we introduce arrays!  These require more complex addressing math,
which in turn means we need to change how Loads and Stores do addressing -
with true field *offsets* instead of field *names*.  This means we also
will do a struct field *layout* in this chapter.

Arrays are created with the standard `new int[len]` syntax, and can be any base
type and any integer expression length.  Arrays are read and written in the usual
style: `Person p = persons[idx];` and `persons[idx] = new Person;`. The length
is accessed with post-fix `#`; e.g. `persons#`.

Like Java, arrays are always safety checked, and failing a runtime check
will panic the Evaluator.

- Indexing out of bounds:
- `ary[-1]`
- `ary[ary#]`
- Creating an array with a bad length:
- `new int[-1];    // Negative length`
- `new int[1<<63]; // Too large`
- `new int[3.14];  // Not an integer`


You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter15) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter14...linear-chapter15) it to the previous chapter.


## Parser

The Parser changes to support arrays are fairly minimal.  The main change is
when parsing a `type()` we look for array syntax: `int[]` is now a valid type.
Any dimension of arrays are allowed, and the base type can be any type so
e.g. `Person[][]?` is a two-dimensional array of `Person`s which might be null.

Arrays are always created with zero/null bodies, so implicitly always allow
`null` values.

We make a new array with much the same syntax as making a new object: `new
int[99]`.  The length is required and is any integer expression, so
e.g. `new int[arg]` is an integer array of length `arg`.  Multi-dimensional arrays
only make the outer dimension; the elements themselves are all null and typed
as the next lower dimension array.

Arrays implicitly have two fields: the length and the body.  The length is a
read-only field set when the array is allocated; it is referenced with the
post-fix `#` operator; e.g. `ary#`.  Example to sum an array:

```java
int[] ary = new int[arg];
int sum = 0;
int i = 0;
while( i < ary# ) {
    sum = sum + ary[i];
    i = i + 1;
}
```

Because arrays have two implicit fields, they get 2 unique alias numbers per
base array type.  This means that `int[]` and `flt[]` types never alias, but
all `int[]` types all alias - and all references into an array body all alias
with each other.  This can lead to some conservative code (no optimization)
with mixing loads and stores in an array in a loop.  Some of this can be
addressed in a later chapter - simple 1-d stencil calculations can be heavily
optimized - but Simple the compiler is not targeting complex vectorization.


## Type Array is TypeStruct

Arrays have a unique type, which is actually just a `TypeStruct` with fields
named `#` and `[]`.  Since these are not available as field names from within
the Parser, users cannot write their own structs which get confused as arrays.

These `TypeStruct` arrays otherwise behave exactly as a `TypeStruct`.


## Allocation and partial memory

This chapter keeps Chapter 11's single hidden `$mem` variable and lazy
`BulkMemPhi`/`MemPhi` partitioning. Start produces `{ctrl, $mem, arg}` and
Return consumes `{ctrl, $mem, value}`. Array initialization changes New:
instead of separate initializing Stores, New allocates and zeros the object.

New consumes `{ctrl, $mem, size}` and produces just `{ptr, $mem}`. The pointer
is result 0 because New has no control result. Its memory input and result
cover only the field aliases in New's internal `TypeStruct`, also available
through its `TypeMemPtr`. Alias numbers are identities, not projection indices.

For an allocation affecting aliases `A`, the parser builds:

```text
input = MemMerge(default: absent, each a in A: before[a])
new   = New(ctrl, input, size)
ptr   = Proj(new, 0)
after = MemMerge(default: before, each a in A: Proj(new, 1))
```

All affected entries share the same memory projection. The input MemMerge is
partial: a null default means an unlisted alias is **not covered**. The output
aggregate retains the previous memory for unrelated aliases. An empty struct
has an empty partial input and leaves whole memory unchanged.

Memory types now pair an alias with the type of values stored in that slice.
An alias query through New combines the incoming contents with the field's
initializer: integer zero, floating zero, or null. The query follows aggregates
and allocations, stops at cached Phi types, and records optimizer dependencies.
MemPhi merges these value types for its one alias. No per-alias New projections
or new lattice domain are needed.

A Load using New's own pointer can fold to the initialized value. A pointer
from a distinct New can bypass the allocation to its memory input. Mere node
inequality is insufficient: a Phi or cast can still denote the allocated object.
Array length is set by a subsequent Store; the array body is zeroed by New.
Array accesses retain their existing control input as the bounds-check proxy.

Both schedulers follow an alias through MemMerge to find New's memory effect.
MemMerge itself does not overwrite memory, but must not hide the ordering of
a read before allocation and a later write to an existing object.

## New Address Math

Since arrays need addressing math to index into them, Loads and Stores need a
way to compute an offset.  Previously Loads and Stores only worked on structs,
and included the field name within them - thus the implicit addressing was field
name (e.g. a string lookup!).  New in this chapter we create a struct *layout*
and compute field offsets to all structs.

Loads and Stores now take an `off` instead of a field name, and the field
name is included for debugging only.

```java
public MemOpNode(String name, int alias, Type glb, Node mem, Node ptr, Node off) {
    super(null, mem, ptr, off);
    _name  = name;
    _alias = alias;
    _declaredType = glb;
}
```

For arrays, the `off` is computed via the normal array math sequence:

  `off = base + (idx << scale)`

The base and index are computed for arrays from the element type, and used
by the parser to build the offset.  For structs, the layout is used.

```java
// Get field type and layout offset from base type and field index fidx
Field f = base._fields[fidx];  // Field from field index
Node off;
if( name.equals("[]") ) {      // If field is an array body
    // Array index math
    Node idx = require(parseExpression(),"]");
    off = new AddNode(con(base.aryBase()),new ShlNode(idx,con(base.aryScale())).peephole()).peephole();
} else {                       // Else normal struct field
    // Hardwired field offset
    off = con(base.offset(fidx));
}
```


## Struct Layout

New in this chapter is struct *layouts*: a fixed offset inside a block of
memory where a particular field is stored.  The offset is computed and cached
in `TypeStruct`.  Every field type now has a `log_size`.  These fields are
packed by size, with alignment padding and the whole struct is padded out to
mod 8.  This means the actual field offset and the field declaration order are
*unrelated*: large fields pack low, and smaller fields pack next.

```java
struct s {
  i8 b;        // log_size=0
  u16 char;   // log_size=1
  Person p;      // log_size=2
  flt pi;        // log_size=3
}
// Layout will be:
struct s {
  flt pi;        // offset= 0, log_size=3
  Person p;      // offset= 8, log_size=2
  u16 char;   // offset=12, log_size=1
  i8 b;          // offset=14, log_size=0
  void pad;      // offset=15, size = 1
}                // size  =16
```

Field size is mostly obvious except perhaps for `TypeInteger`.  Floats are
either 32 bits or 64 bits.  Pointers are set at 4 bytes in `TypeMemPtr` and
this could be changed at a later date to support a larger range of pointers.
`TypeInteger` only has valid `log_size` fields for a fixed set of user-visible
integer ranges.  Other ranges are possible in Simple via `compute` calls, but
these will never show up as base field types:

   `struct S { i16 x; }`

Here `x` will have offset 0 and size 2 bytes, and `S` will be padded to size 8.

   `S s = new S; s.x = 3;`

The same `x` field now holds either a 0 or a 3, so the `TypeInteger` for `x`
will be a range `[0-3]` which could be represented in just 2 bits ... but this is not used to compute the field size.


## Some Simple Address Math Peeps

Shift-left by 0 is an identity: `(x<<0)` becomes `x`.

Shift-left with a constant add distributes, to allow more constant folding:
`(x + c) << i` becomes `(x << i) + (c << i)`.

This means the IR for:

  `x[i]`  becomes  `16/*base*/ + (i << 3/*scale*/)`
  becomes `Load(mem,x,Add(Shl(i,3),16))`


  `x[i+1]` becomes `16/*base*/ + ((i+1) << 3/*scale*/)` becomes
  `Load(mem,x,Add(Shl(i,3),24))`

Notice the `idx+1` folds the `+1` into the base math.

Also notice this form is perfect for the X86 addressing modes.

## Discussion

Included in the test cases is a simple rolling sum, and a Sieve of Eratosthenes.

```java
int i=0;
while( i < ary# ) {
    ary[i] = i;
    i = i+1;
}
```

Here we really see the lack of an `i++` operator and a `for` loop.  You can
imagine another syntax:

```java
for( int i=0; i < ary#; i++ )
    ary[i] = i;
```

that would be entirely syntactic-sugar over the existing parser, but would
really help make array looping syntax clearer.  There are lots of options
here, which should really be the focus of another chapter!
