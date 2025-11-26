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
        int pi = 3.14;
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

`{ i32 fd, i32 buf, i32 len -> i32 } write = "C";`

Any final variable assigned as "C" will be considered defined externally.  The
syntax of this might change slightly to avoid ambiguity with assign the same
"C" string.

There is no linker support for getting the correct signature or namespace; the
name `write` has to be unique when linking.


## Auto-Import and the "sys." namespace

The `sys` struct includes `libc` bindings, and any default library code
including e.g. printing support and collections.  As of this chapter it
includes minimal libc bindings and an easy print:

```java
// top-level default import
struct sys {
    // libc bindings
    /** https://www.man7.org/linux/man-pages/man2/<libc call>.2.html */
    struct libc {
        // fd  buf len -> len
        {  i32 i64 i32 -> i32 } write = "C";
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
            i32 len = str#; // cast length to signed
            return sys.libc.write(1,ptr,len);
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

In the rush to get executable code from 
[Chapters 19](https://github.com/SeaOfNodes/Simple/tree/linear-chapter19),
[Chapters 20](https://github.com/SeaOfNodes/Simple/tree/linear-chapter20), and
[Chapters 21](https://github.com/SeaOfNodes/Simple/tree/linear-chapter21), we
wrote a lot of code in a hurry.  That also means we wrote a lot of bugs (sad
face), and will be paying the price for a few chapters.  In this chapter we
also found and fixed a number of bugs, here's a sample:

- x86 encoding bugs:
- - `imul` with immediate forms and `setXX` had incorrect `REX` forms.
- - x86 `div` was taking `RAX,RDX` as the second *input* register; they are already 
    hardwired as the *first* input register.
- - Many x86 ops that kill `flags` did not state so in the port.
- - Short forms for some x86 ops (e.g. using the `inc` vs `add+1`)
- Global Code Motion `ScheduleLate` missed visiting values only visible around
  loop backedges; also was adding extra dependence edges between loads and
  stores with different aliasing.
- Inlining would reset the **idepth** (as expected) but then use the existing
  `CallNode` and `FunNode` that are *folding* but not yet peep'd away - leading
  to incorrect **idepth** calculations.
  
Many more bugs got fixed, but this sample should give an idea of the continuous
improvements going on.  These happen because we have finally reached a point
where we can aggressive test again - we are writing Real (Simple) Code (tm) and
running it... and crashing on these bugs.  Compilers are big, complex, beasties
and some amount of bugs and bug fixing is to be expected.
