# Chapter 16: Constructors

[Previous: Chapter 15](../chapter15/README.md) |
[Next: Chapter 17a](../chapter17a/README.md)

# Table of Contents

1. [Constructors](#constructors)
2. [Initialization code](#initialization-code)
3. [Multiple Decls](#multiple-declarations-of-the-same-type)
4. [Memory through constructors](#memory-through-constructors)

This chapter adds field defaults and constructor blocks. Chapter 17a will add
fixed bindings and read-only access, using these constructors to initialize them.

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter16) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter15...linear-chapter16) it to the previous chapter.

## Constructors

A big problem with the [Chapter 12](../chapter12/README.md) refs is that
not-null fields always start out `null`.  This is fixed in this chapter, where
a *constructor* syntax is required to initialize not-null fields.

Fields are initialized in three ways:

1. A default zero/null initialization for fields that allow it.  No special
   syntax is required.
```
int x;                    // x is initialized to 0
struct Ref { Ref? ptr; }; // Instances of Ref will have ptr initialized to null
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

Not-null fields *must* be initialized before first use and before the
end of the allocation.  They can be initialized in either the declaration or
allocation.  They do not start with the default value, although the
initialization can be to the default.

```
struct Person { u8[] name; };
return new Person; // ERROR: 'Person' is not fully initialized, field 'name' needs to be set in a constructor
```


## Initialization code

The parser allows a full Block statement to be parsed in either the type
declaration or the allocation.  Any amount of code is legal, including
`while` loops and `return`s.

```
struct Square {
    flt side = arg;
    flt diag = arg*arg/2;
    // Newton's approximation to the square root, computed in a constructor.
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

Another example:

```
struct Buffer {
    if( arg < 0 || arg > 1000000 )
      return null; // Size out of bounds
    u8[] buffer = new u8[arg];
};
return new Buffer;
```


### Multiple declarations of the same type

Also new this chapter is allowing multiple declarations with the same type, as
is commonly seen in other languages:

```
int x,y; // Two int variables declared
struct Point { int x,y,z; }; // Three fields declared
```

## Memory through constructors

The lazy memory partitioning from [Chapter 11](../chapter11/README.md) continues
through constructors. The parser tracks one `$mem` variable alongside its scalar
variables. Branches and loops merge that binding with BulkMemPhi; field access
splits out precise MemPhis when needed. The variable records for declared types live in ScopeNode. There is no separate parser table of memory aliases.

A constructor computes its field values before the allocation. Reads, writes,
and loops in its body update the same `$mem` binding as ordinary code. New then
takes `{ctrl, $mem, size, field values...}` and produces `{ptr, $mem}`. The input
memory is a partial MemMerge containing the struct's aliases; the output memory
covers those same aliases. A whole-memory MemMerge preserves unrelated slices.
Arrays use the same layout, supplying their length and default element value as
initializer inputs.

A Load from its own New can use the matching initializer input. Alias contents
combine the initializer's type with incoming contents from older objects, so a
new object does not erase facts or effects for existing objects. Loads bypass
only allocations proven to be distinct. Scheduling follows partial aggregates
when establishing read-before-write order.

Sharing one allocation memory result makes Store-to-New folding more
conservative: the existing sole-use check now sees users of every covered
alias. This can leave a field Load where separate memory projections previously
allowed a constant fold. Phi factoring also retains the simple one-step Load
safety check.
