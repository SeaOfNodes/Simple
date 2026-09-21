# Chapter 22: A Hello, World!

It's high time for strings and printing!  In this chapter we look at what it
takes to make a basic string - not really a full fledged String class, that
will wait for another chapter.

```java
sys.io.p("Hello, World!");
```

Yeah!  A 1-liner `Hello, World`!  This example can be found in
`docs/examples/A_helloWorld.smp`.


Let's break it down.

- `sys`: is Just A Variable Name.  Like other variables, it is looked up in the
  current scope... which looks completely empty.  Every Simple program now starts
  with the `sys` variable in-scope.  `sys` itself a normal `struct`
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


You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter22) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter21...linear-chapter22) it to the previous chapter.


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
need to be stored in each instances.  Instead one copy is kept in a global
space (not really a *class* object yet) and loaded from there.

In this example:

- Instances of the `Outer` struct have a single `int` field `out`.
- Instances of the `Inner` struct have a single `int` field `in`.
- There exists a `Outer.Inner.pi` field in a global space with value `3.14`.

The `sys` struct is another example, with nested `libc` and `io` structs at
least.  All the fields in these structs are all final, hence static.



## FFI and I/O Calls

We can now call external / FFI functions, and they are declared like any other
variable except being assigned "C".  Here is the binding for the libc `write`
call:

`{ i32 fd, i64 buf, u32 len -> u32 } write = "C";`

Any final variable assigned as "C" will be considered defined externally.  The
syntax of this might change slightly to avoid ambiguity with assign the same
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

Chapter 21 introduced conservative copy coalescing. Here we improve the register
preferences used when related live ranges cannot coalesce. A copy chain can take
an already assigned register even at its terminal definition; a loop Phi prefers
its backedge's register, avoiding a move on each iteration when possible.

When no live range is trivially colorable, cheap values make better spill
candidates. We favor a cloneable definition with distant or multiple uses over
one already adjacent to its only use, then callee-save values whose long spans
are cheap to split. Register order breaks ties between callee saves. These are
small preferences, not a general cost model. Grouping popular values by register
class is left for Chapter 23, cold loop splits for 24, and area/cost ranking for 25.

Run `make spill-stats` in this directory. Each row below uses **this chapter's
compiler**, default worklist seed 123, and the same source/target combinations
as the earlier cohort. The Windows run combines x86 SystemV/Win64 and RISC-V/ARM
SystemV. Diagnostic graphs and mask regressions do not contribute to the totals.

| Program cohort | Compilations | Split/move count | Loop-weighted count |
|---|---:|---:|---:|
| Chapter 20 | 39 | 324 | 443 |
| Chapter 21 | 52 | 447 | 986 |
| Chapter 22 | 24 | 67 | 67 |
| **Total** | **115** | **838** | **1,496** |

`_spills` counts retained SplitNodes, including register moves; `_spillScaled`
weights each by `8^loopDepth`. These are compiler estimates, not measured runtime
memory traffic. The reporter also prints individual allocations and CPU/ABI sums.
Chapter 21's original `int age` person example is preserved as `person21`; this
chapter's `i32 age` version and revised infinite-loop example belong to cohort 22.

For a controlled comparison, the same compiler and correctness fixes with
Chapter 21's color preferences and spill ordering produce 885 moves and 1,543
weighted moves. This chapter saves **47 weighted moves (3.0%)**. RISC-V BrainFuck
improves from 42 to 28 and ARM from 34 to 28 in each of its two cohorts. Some
MergeSort cases and RISC-V Sieve each cost one more move. Summing the whole suite
shows whether those local tradeoffs pay off.

The original Chapter 22 snapshot, which mixed several later heuristics, produced
854 moves and 1,736 weighted moves on these same 115 compilations. The corrected,
staged version saves 240 weighted moves (13.8%). Neither comparison should be
confused with comparing entire compiler chapters: lowering also changes. For
example, the frozen Chapter 20 String input has no explicit return, and Chapter
22's default-return handling eliminates that workload at the default seed.

The full suite and statistics runner pass. Routine backend comparisons use a
fixed optimizer seed:
shuffling optimizer worklists should normalize to essentially the same graph,
so repeating allocation on those graphs adds little coverage. Use seed variation
when investigating optimizer normalization or worklist-order failures.

Two pre-existing failures found during the review remain in the
[backport queue](../docs/chapter-backports.md): String fails during loop
analysis/scheduling at other optimizer seeds, and returning a function pointer
fails during relocation after Opto incorrectly deletes the referenced function.
Their reproductions and validation history are retained
there as separate correctness work.
