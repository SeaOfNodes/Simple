# Chapter 22: A Hello, World!

English | [日本語](README.ja.md)

[Previous: Chapter 21](../chapter21/README.md) |
[Next: Chapter 23](../chapter23/README.md)

It's high time for strings and printing!  In this chapter we look at what it
takes to make a basic string - not really a full fledged String class, that
will wait for another chapter.

```java
sys.io.p("Hello, World!");
```

Yeah!  A 1-liner `Hello, World`!  This example can be found in
`examples/A_helloWorld.smp`.


Let's break it down.

- `sys`: is Just A Variable Name.  Like other variables, it is looked up in the
  current scope... which looks completely empty.  Every Simple program now starts
  with the `sys` variable in-scope.  `sys` itself is a normal `struct`
  and is defined in `src/main/smp/sys.smp`.
- `sys.io.p`: lookup the `io` field in `sys` struct, which yields the `sys.io`
  struct; then a lookup of `p` (short for "print") in the `sys.io` struct.
  This returns a function.
- `p("Hello, World!")`: call the function just loaded from the `p` field, with
   `"Hello, World!"` as a string argument.
- `"Hello, World!"`: New syntax that yields a constant array of `u8` (chars).
  The array is otherwise a normal range-checked array, with a leading `#`
  length field.  The array backing data eventually ends up in the *constant
  pool*, and from there in an ELF file's RODATA section.


Chapter 11's lazy memory partitioning continues here: the parser carries one
`$mem`, and BulkMemPhi/MemPhi discover field aliases during optimization.
Allocations consume a partial MemMerge and produce `{ptr, $mem}`; calls carry
whole memory. The loop Load search follows every arm of a memory merge, so
BrainFuck's invariant program length folds out of its main loop.

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter22) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter21...linear-chapter22) it to the previous chapter.


Here is the [complete language grammar](22-grammar.md) for this chapter.

## Nested Types and Static Fields

Simple now supports nested types - this is a name-space only change, so no new
semantics - just the ability to nest type definitions.

```java
struct Outer {
    struct Inner {
        int in;
        flt pi = 3.14;
    };
    int out;
};
```

Any final field set in the declaration (as opposed to the constructor) is
automatically a "static" field - it is final for all instances, and does not
need to be stored in each instance.  Instead one copy is kept in a global
space (not really a *class* object yet) and loaded from there.

In this example:

- Instances of the `Outer` struct have a single `int` field `out`.
- Instances of the `Inner` struct have a single `int` field `in`.
- There exists an `Outer.Inner.pi` field in a global space with value `3.14`.

The `sys` struct is another example, with nested `libc` and `io` structs at
least.  All the fields in these structs are all final, hence static.



## FFI and I/O Calls

We can now call external / FFI functions, and they are declared like any other
variable except being assigned "C".  Here is the binding for the libc `write`
call:

`{ i32 fd, i64 buf, u32 len -> u32 } write = "C";`

Any final variable assigned as "C" will be considered defined externally.  The
syntax of this might change slightly to avoid ambiguity with assigning the same
"C" string.

There is no linker support for getting the correct signature or namespace; the
name `write` has to be unique when linking.


## Auto-Import and the "sys." namespace

The `sys` struct includes `libc` bindings, and any default library code
including e.g. printing support and collections.  As of this chapter it
includes minimal libc bindings and an easy print. This excerpt shows the
parts used above:

```java
// top-level default import
struct sys {
    // libc bindings
    /** https://www.man7.org/linux/man-pages/man2/<libc call>.2.html */
    struct libc {
        // fd  buf len -> len
        {  i32 i64 u32 -> u32 } write = "C";
        // addr len prot flags fd  off -> void*
        {  i64  i64 i32  i32   i32 i32 -> i64 } mmap = "C";
        // mmap flags
        i32 PROT_EXEC = 4;
        i32 PROT_READ = 1;
        i32 PROT_WRITE= 2;
        i32 PROT_NONE = 0;
        i32 MAP_PRIVATE = 2;
        i32 MAP_ANON = 32;
    };
    struct io {
        val p = { u8[~] str ->
            i64 ptr = str;  // cast array base to i64
            return sys.libc.write(1,ptr,str#);
        };
    };
};
```

The `sys` import itself is Just Another `struct` like any other struct; it has
fields and assignments and types declared internally.  Note that these
assignments are all final and in the struct declaration, hence these are all
*static* fields.


## Casting a Pointer to an `i64`

You can now cast a pointer to an i64, although not the other way around.
`i64 ptr = str; // cast array base to i64`

For arrays, this cast is to the array *base* skipping the `#` length field, and
is suitable for passing to external "C" calls.  For structs, this is just the
struct base.  This is used to pass a length-checked Simple array to the libc
`write` call in the above `sys.io.p` function.


## Arrays of Constants and Constant Arrays

The Simple type system now supports the notion of a "constant array" - an array
of fixed constants, stored in the ELF file in the RODATA section.  Currently we
only support strings at the parser level: `"Hello, World!"` makes a `u8[13]`
array containing 13 ASCII bytes; like all arrays it has a length and will be
range-checked at some point.

A constant version of a non-constant array can be assigned using the `u8[~]`
syntax.

```java
// Make a mutable array of ints
int N=4;
i32[] is = new i32[N];
// Mutate the array
for( int i=0; i<N; i++ )
    is[i] = i*i;
// Function "sum" can read but not write the array
val sum = { i32[~] nums ->   // Notice the [~] signature marking nums as immutable
    int sum = 0;
    for( int i=0; i<nums#; i++ )
        sum += nums[i];
    return sum;
};
return sum(is);
```

Note that this affects the deep contents of the array and not the array
variable itself.

## Quote Characters

The form  ``` `0` ``` makes a `u8` value with the ASCII character `0`.

## Continuous Improvement

Executing larger programs exposes mistakes that graph-only tests can miss.
During the original development of this chapter, these included x86 instruction
encodings and flag clobbers, scheduling around loop backedges and memory stores,
and stale dominator depths during inlining. Corrections are being moved into the
earliest applicable chapters so readers can build on working compiler snapshots.
Native tests check both output and process exit status; emulator tests check the
returned values and memory as well as successful execution.

## RegAlloc improvements: better color bias

Chapter 21 introduced conservative copy coalescing.  Here we improve the register
preferences used when related live ranges cannot coalesce.  A copy chain can take
an already assigned register even at its terminal definition; a loop Phi prefers
its backedge's register, avoiding a move on each iteration when possible.

When no live range is trivially colorable, cheap values make better spill
candidates.  We favor a cloneable definition with distant or multiple uses over
one already adjacent to its only use, then callee-save values whose long spans
are cheap to split.  Register order breaks ties between callee saves.  These
are small preferences, not a general cost model.  Grouping popular values by
register class is left for Chapter 23, cold loop splits for 24, and area/cost
ranking for 25.

Rows are fixed test cohorts; columns are compiler chapters. Each cell gives
**Ops / RA / X**: executed ARM+RISC-V instructions, the subset emitted for
allocator copies/rematerializations, and x86's loop-weighted split-move estimate.
**Cases D / X** counts measured dynamic program/target cases and x86 compilations;
membership and inputs stay fixed across columns. Native-library work is excluded.
Chapter 20 has no encoder, hence no dynamic counts. Lower is better within each
metric; see the [measurement details](../regalloc-spills.md) for coverage.

<!-- spill-matrix:start -->
| Test cohort | Cases D / X | Ch 20: Ops / RA / X | Ch 21: Ops / RA / X | Ch 22: Ops / RA / X |
|---|---:|---:|---:|---:|
| Ch 20 | 22 / 13 | — / — / 161 | 55,173 / 5,407 / 174 | 58,751 / 9,099 / 175 |
| Ch 21 | 20 / 18 |  | 6,207 / 261 / 461 | 6,324 / 379 / 475 |
| Ch 22 | 14 / 8 |  |  | 198 / 60 / 7 |
<!-- spill-matrix:end -->
