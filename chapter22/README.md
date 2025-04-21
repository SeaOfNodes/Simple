# Chapter 22: Strings and Such

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
    str className;
    str professor;
    int time;
    int credits;
    Class prerequisiste;
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
- The allocation includes the array length, and can include a constructor:
  `new NTree{val=17;}[2];`


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


## A String Implementation

## FFI and I/O Calls
