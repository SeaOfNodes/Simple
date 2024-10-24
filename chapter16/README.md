# Chapter 16: Constructors and Final fields

In this chapter, we add constructors and final fields.

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter16) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter15...linear-chapter16) it to the previous chapter.

## Constructors

A big problem with the [Chapter 13](../chapter13/README.md) refs is that
not-null fields always start out `null`.  This is fixed in this chapter, where
a *constructor* syntax is required to initialize `final` and not-null fields.

Fields are initialized in three ways:

1. A default zero/null initialization for fields that allow it.  No special
   syntax is required.
```
int x;                    // x is initialized to 0
struct Ref { Ref ptr?; }; // Instances of Ref will have ptr initialized to null
return new Ref.ptr;       // Returns a null
```

2. The type declaration can specify an initial value.  Later allocations will
   start with this value. 
```
int x = 5;                // x is initialized to 5
struct Point { int x=1; int y=1; }; // Point x and y will start as 1, not 0
return new Point.x;       // Returns a 1
```

3. The allocation can specify an initial value.
   
```
struct Point { int x=1; int y=1; }; // Point x and y will start as 1, not 0
return new Point { x=3; }.x;        // Returns a 3
```

Not-null and final fields *must* be initialized before first use and before the
end of the allocation.  They can be initialized in either the declaration or
allocation.  They do not start with the default value, although the
initialization can be to the default.

```
struct Person { u8[] name; }
return new Person; // ERROR: not-null field 'name' not initalized
```


## Initialization code

The parser allows a full Block statement to be parsed in either the type
declaration or the allocation.  Any amount of code is legal, including
`while` loops and `return`s.

```
struct Square {
    flt side = arg;
    flt diag = arg*arg/2;    
    // Newtons approximation to the square root, computed in a constructor.
    // The actual allocation will copy in this result as the initial
    // value for 'diag'.
    while( 1 ) {
        // The next-guess variable "next" is not a field in Square, 
        // because it does not appear at the top level
        flt next = (side/diag + diag)/2;
        if( next == diag ) break;
        diag = next;
    }
};
return new Square;
```

```
struct Buffer {
    if( arg < 0 || arg > 1000000 )
      return null; // Size out of bounds
    u8[] buffer = new u8[arg];
};
return new Buffer;
```


## Final fields

Final fields are declared with a `!` before the name:

```
int !x = 17; // Final field x
x = 3;       // ERROR: cannot re-assign a final field
```

```
struct Person { u8[]? !name; }
return new Person { name = null; }; // OK: final name is assigned
```

```
struct Point { int !x; int !y; int !z; }
return new Point { x=arg; y=z; z=x; } // ERROR: final field z used before initialized
```

```
struct Point { int x; int y; int z; } // Fields not-final, default to 0
return new Point { x=arg; y=z; z=x; } // Ok: x=arg, y=0, z=arg
```


### Multiple declarations of the same type

Also new this chapter is allowing multiple declarations with the same type, as
is commonly seen in other languages:

```
int x,y; // Two int variables declared
struct Point { int x,y,z; } // Three fields declared
```

```
int !x=3,!y=5; // Two int variables declared, both are final and initialized
```
