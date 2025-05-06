# Chapter 22: A Hello, World!

It's high time for strings and printing!  In this chapter we look at what it
takes to make a basic string - not really a full fledged String class, that will
wait for another chapter.


You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter21) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter20...linear-chapter21) it to the previous chapter.


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
- Instances of the `Outer` class have a single `int` field `out`.
- Instances of the `Inner` class have a single `int` field `in`.
- There exists a `Outer.Inner.pi` field in a global space, with value `3.14`.


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

The `sys` import will eventually include libc bindings, and any default library
code including printing support, and probably collections.  As of this chapter
it includes minimal libc bindings and an easy print:

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

The `sys` import itself is Just Another `struct` like any other; it has fields
and types declared internall.  Note that these assignments are all final and in
the struct declaration, hence these are all *static* fields.


## Casting a Pointer to an `i64`

You can now cast a pointer to an i64, although not the other way around.
`i64 ptr = str; // cast array base to i64`

For arrays, this cast is to the array *base* skipping the `#` length field, and
is suitable for passing to external "C" calls.  For structs, this is just the
struct base.


## Arrays of Constants and Constant Arrays

The Simple type system now supports the notion of a "constant array" - an array
of fixed constants, stored in the ELF file in the RODATA section.  We only
support strings at the parser level: "Hello, World!" makes a `u8[13]` array
containing ASCII bytes.

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
val sum = { i32[~] is ->
    int sum = 0;
    for( int i=0; i<is#; i++ )
        sum += is[i];
    return sum;
};
return sum(is);
```

Note that this affects the deep contents of the array, and not the array
variable itself.
