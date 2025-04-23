# Chapter 22: Strings and I/O

It's high time for Strings and printing!  In this chapter we look at what it
takes to make a nice user-mode string.  We are implementing strings in the base
Simple language (except for perhaps some minor Parser shortcuts).  This means
we'll need some canonical representation and a library of string functions.



You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter21) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter20...linear-chapter21) it to the previous chapter.


## Unifying Structs and Arrays

The idea is that structs have fields indexed by names, and arrays have fields
indexed by numbers, and these ideas are compatible with each other.  This idea
has been used by the C language for a long time - following a normal struct we
allow an array, and index it with the usual array syntax.

```java
struct str {
    int  hash; // A cache of the strings hash code
    u32  #;    // The array length
    u8   [];   // Array of bytes
}
```

Here, the field named `#` holds the array length and the field named `[]` is
the actual array, which follows the other fields directly in memory.
Here is another example for variable length array:

```java
struct vecInt {
    u32  size; // Actual number of elements
    u32  #;    // The array length or capacity
    int  [];   // Array of integers.
};
```

The obvious limit here is that the array cannot be directly resized; it is
literally in the same memory block as the leading fields.  Such a `vecInt` can
be *re-allocated* to a new larger `vecInt`, but the original `vecInt` will be
reclaimed - you cannot use the same `vecInt` reference forever.

Any number of named fields are allowed:

```java
struct Class {
    str     className;
    str     professor;
    int     time;
    int     credits;
    Class   prerequisite;
    u16     #; // Limit of 65535 students per class
    Student [];
};
```

Or self-reference:

```java
struct NTree {
    Some   val;
    u8     #;   // Length is 0-255
    NTree  [];  // More NTrees
};
```

The syntax rules are:
- the last field is named `[]` and can be of any type.
- the second-to-last field is named `#` and must be some unsigned integer type
  representing the maximum array length.
  
  
  
### Constructors and Arrays

The allocation includes the array length, and can include a constructor:
`new NTree{val=17;}[2];`

The constructor is optional, the array length is not.

Optional function for initializing arrays, allows for write-once final arrays:
`new u8[buffer#,{ u32 idx -> buffer[idx].toUpperCase() }`



### Changing array max length

An issue for large numbers of range-checked small arrays is that reserving a
full 4 bytes for array length can substantially increase the array overhead.
Simple allows for arrays to have smaller max lengths, although these are
treated as a different type than arrays with different max lengths.

```java
struct x0 { u8  #; u8[]; }; // Array limited to 0-255 length
struct x1 { u16 #; u8[]; }; // Array limited to 0-65535 length
struct x2 { u32 #; u8[]; }; // 
struct x3 { u64 #; u8[]; }; // 

x0 x = "abc"; // 4 bytes total: [3, 'a', 'b', 'c']
x1 y = "def"; // 6 bytes total: [3,  0 , 'd', 'e', 'f', 0,]; 1 pad byte to acheieve 2-byte alignment
x = y;  // ERROR, "x1 is not x0", no auto-widening
x += y; // OK, 7 bytes total: [6, 'a', 'b', 'c', 'd', 'e', 'f']
y += x; // OK, 8 bytes total: [6,  0 , 'd', 'e', 'f', 'a', 'b', 'c']

x += "VeryLongString.... ....255_chars"; // Runtime RangeCheck

// Explicit conversion to widen string
y = "" + x;
```


## Methods and Final Fields and Classes

Methods are simply function values (final fields) declared inside a struct that
take an implicit `this` argument.

Final field declared in the original struct definition are *values* and cannot
change; they are the same for all structs.  They get moved out of the struct's
memory footprint and into a *class* - a collection of values from the final
fields.  Classes are just plain namespaces and have no concrete implementation;
they are not reified.

```java
struct vecInt {
    // Since "add" is declared with `val` in the original struct definition,
    // it is a final field and is moved out of here to the class.
    
    
    // Add an element to a vector, possibly returning a new larger vector.
    val add = { int e ->
        if( size >= # ) return copy(# ? #*2 : 1).add(e);
        [size++] = e; 
        return this;
    };
    // Copy 'this' to a new larger size
    val copy = { int sz2 -> 
      val v2 = new vecInt[sz2];
      for( int i=0; i<size; i++ )
        v2[i] = [i];
      v2.size = size;
      return v2;
    }

    u32  size; // Actual number of elements
    u32  #;    // The array length or capacity
    int  [];   // Array of integers.
};

val primes = new vecInt[0].add(2).add(3).add(5).add(7);

```

Here the `val add = ` field is a *value* field, a constant function value.  It
is moved out of the basic `vecInt` object into `vecInt`'s *class*, and takes no
storage space in `vecInt` objects.  The same happens for `copy`.  The size of a
`vecInt` is thus 2 `u32` words, plus `#*sizeof(int)`, plus any padding
(probably none here).

The actual usage of this `vecInt` class differs from other similar classes in
other languages:

- The data and header object are *inlined*, one level of indirection (and
  possible cache miss cost) is avoided.
- The array cannot be resized without resizing the entire object.  Once the
  array is full, further adds will return a **new** larger `vecInt`.
  
Lifetime management to avoid using an older version of `vecInt` awaits a later
chapter.


### Two flavors of extendable arrays/strings: an extra indirection or not

With an extra indirection you get e.g. Java `ArrayList` or C++ `Vector`.

Without, then any call which extends the extendable array return a potentially 
new object, only growing as needed.

```java
val vec1 = new vecInt[0].add(2); // len=1, capacity=1
val vec2 = vec1.add(3); // NEW vecInt returned, only is deleted; len=2; capacity=2;
vec1[0]; // ERROR, vec1 has been freed by call to `vec1.add(3)`
vec2[0]; // OK
vec2[1]; // OK
vec2[2]; // Runtime error, AIOOBE
```


## A String Implementation

Default strings have names like "str", "cstr", "ustr" or such.

Always unicode8 with explicit u8 manipulations.  Unicode "characters" will come
as function calls over the base implementation, probably in another string class.

## Default string

Efficiently growable via concantenation.  Supports a variety of IO/printf functions.
The underlying array can be shared mutable if you ask for this.
The defaults will always be private mutable and read-only shared.

```java
struct str {

    // Add a string to the string, possibly growing
    val add = { str str ->
        val self = this;
        for( int i=0; i<str#; i++ )
            self = self.add(str[i]);
        return self;
    }

    // Add an char to the string, possibly growing
    val add = { u8 e ->
        if( len >= # ) return copy(# ? #*2 : 1).add(e);
        [size++] = e; 
        return this;
    };
    
    // Copy 'this' to a new larger size
    val copy = { int sz2 -> 
      val v2 = new str[sz2];
      for( int i=0; i<len; i++ )
        v2[i] = [i];
      v2.len = len;
      sys.libc.free(this); // Needs a better Mem Management solution
      return v2;
    }

    val print { -> sys.libc.write(1/*stdout*/,[]/*array base as argument to C*/,len); return this; }

    u32 len; // In-use size
    u32 #;   // Max length is 4Gig, although variants can request smaller lengths
    u8  [];  // Character data
}
```


### A Final String

Intended to be a **immutable** string, possibly used as a base for a **secure**
string.  This needs to be created all at once, and probably requires a
defensive copy.

```java
struct sstr {
    // Possible static call syntax; no references to "this"
    val make { str str -> new sstr[str#,str.at] };
    
    // Array contents are immutable.
    // Still figuring out good syntax for this.
    u8 #;
    u8 []!; // Trailing '!'?  Makes primitive array contents *immutable* and requires constructor syntax
};

// Construction requires a function that produces the array contents.
// Here we are defining a defensive copy over `buf`.
sstr safe = new sstr[buf.len,{ int idx -> buf[idx]; }];
sstr safe = sstr.make("abc"); // Defensive copy is made
```


### A Extendable String (aka StringBuilder or StringBuffer)

This API is intended to be a drop-in for a `StringBuilder` style.

```java
struct xstr {
    str !_str; // The extra indirection is here
    val add = { str str -> _str = str.add2(str); }
    val write = { int fd -> _str.write(fd); }
};
```

### A Unicode String

This API is intended to support unicode character manipulation over a plain `str`

```java
struct ustr {
    // Assuming idx is at the start of a character, return the character
    // as a 32b integer.  Returns junk integer if not at a character start.
    // For all ASCII strings this will amount to a simple byte load.
    // For bytes with high order bits set, this may read 1-4 characters.
    val at = { int idx -> ... };
}
```

### A "C" string

A zero-terminated string, but the length has to be available directly

```java
struct cstr {
  u8 #; // length including the trailing zero
  u8[]; // always ends in a zero byte; suitable for direct passing to C string functions
};
```

### A "german string": a very short string that can exist in a register

Length in memory is 1 byte (3 bits?) of length, then 0-7 chars; max 8 chars.
Fits in a 64 bit register.

```java
struct gstr { // aka germanString, or blend string and register "streg"
    u3 #;   // Size is 0-7 bytes???
    u8 [];  // Up to 7 bytes
}
// Using inlined object (see below).  All these strings are short and are
// represented as packed integers in a 64b register.
gstr *prize1 = "gold"
gstr *prize2 = "silver";
gstr *prize3 = "bronze";

```


## FFI and I/O Calls

Calling external / FFI functions.
No linker support for getting the correct signature; name `write` has to be singular and unique.

`{ i32 fd, u8[] buf, i32 len -> i32 } write = "C";`


## Importing name spaces

Ponder a `struct sys{}` default import struct.
Includes `sys.io{}` struct with I/O calls.
Includes `sys.libc{}` with default libc calls.



## Main becomes `main(str[])`

### Allow structs to inline

This is a name-space change only, you cannot take the address of the internal
struct without entirely consuming its lifetime.  No "escaping".

```java
// 2 64-bit words, no other overheads
struct Complex { f64 x,y; var len = { ->Math.sqrt(x*x+y*y); }; };

// Current Simple rules: always by-reference
struct ByRef {
    Complex c; // 4-byte pointer to a Complex
};
print(new ByRef.c.y); // Lookup requires 1 extra memory load from ref to c

// New behavior: by value (inlining a struct)
// The '*' syntax indicator can be something else, e.g. a keyword "inline"
// I am pronouncing '*' as "contents of"
struct ByValue {
    *Complex c; // c is inlined, full 16 bytes into ByValue
};
print(new ByValue.c.y); // x,y inlined into ByValue, no extra memory load

// Mixing Refs and Struct Values
ref.c =  val.c; // Error, mixing refs and values
ref.c = *val.c; // Allowed, whole structure copy.  Does not allocate, requires ref.c not-null
val.c =  ref.c; // Error, mixing refs and values
val.c = *ref.c; // Allowed, whole structure copy
ref.c =  ref.c; // Allowed, pointer copy
val.c = *val.c; // Allowed, whole structure copy.
val.c =  val.c; // Allowed, whole structure copy, same as above, convenience

// Alternative syntax, to avoid the "contents of" being on LHS and field applying to being on RHS:
ref.c = val.*c; // Assign into ref.c the "contents of" val.c

// Arrays of inlined structures
var ary = new *Complex[99]; // Array of 99 Complex objects, inlined
```

### RoadMap for other chapters

`&&` and `||`
Drop `int` to `i32`.
Add optimistic pass SCCP to `opto`
Default parser for-loop construction, or partial peel.
    `for( init; test; next ) body`
becomes:
```java
{ init;
if( !test ) { // Zero-trip count test
    do {
        body;
        next;
    } while( test ); // Exit test at loop bottom by default
} }
```