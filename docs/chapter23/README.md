# Chapter 23: Methods and Revisiting Types

In this chapter add *methods*, functions defined in structs which take a
hidden `self` argument and can access the struct fields.  Here is an
`indexOf` method in a String-like class:

```java
// A String-like class, with methods
struct String {
    u8[~] buf; // Buffer of read-only characters
    
    // Return the index of the first character 'c' or -1
    val indexOf = { u8 c ->           // Hidden 'self' argument
        for( int i=0; i<buf#; i++ )   // Direct access to `buf` field
            if( buf[i]==c )
                return i;
        return -1;                    // 'c' not in 'self'
    };
};
```

Also in this chapter we revisit our Types and make some major changes:
- Types are now *cyclic*: a Type can (indirectly) point to itself.
- Types remain
  [*interned*](https://en.wikipedia.org/wiki/Interning_(computer_science)) or
  [*hash-consed*](https://en.wikipedia.org/wiki/Hash_consing).
- Type objects are managed via an object pool.
- Newly created types probe the interning table, and if a hit is found, the
  original is used, and the new type is returned to the object pool.
- The intern table hit rate is something like 99.9%; nearly all types created
  have already been created!  This last point turns into an interesting
  performance win; much of the work in an optimizing compiler deals with
  manipulating Types, keeping the count of Types small and fitting in the
  faster levels of cache is a nice payoff.
- Many of the interning algorithms require cycle-handling via some kind of
  "visit bit".  One easy way to do this is add a *unique id* UID to every type,
  a small dense integer that is used in bit-sets to avoid visiting a type more
  than once.  Smaller UIDs use smaller bit-sets, which again fit in the faster
  cache levels.
- As a consequence, our types are cyclic (the goal!), and **faster** and have a
  more complex implementation.  **Using** the types remains the same; the same
  Type API is used when creating them, MEETing, making Read-Only, printing,
  etc; only the implementation details change.


You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter23) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter22...linear-chapter23) it to the previous chapter.


## Why Cyclic Types?

Cyclic types give us a sharper analysis than the alternative, and thus admit
more programs or better optimizations or both.  In Simple's case Types are also
used for type-checking, so sharper types means we allow more valid programs.

**Why Cyclic Types Now**?  Because we hit them as soon as we allow a
function definition inside of a struct which takes the struct as an argument,
i.e. *methods* taking a *self* argument.

Lets look at our `String.indexOf` example above.  Here we define a `String`
with an `indexOf` method; a hidden argument string `self` is passed in
and searched.  What is the type of `struct String`?

`struct String { u8[~] buf; { String self, u8 c -> int } indexOf; }`

It is the type named `String` with a field `u8[~] buf` and a final assigned
constant field `indexOf`, itself with type `{ String self, u8 c -> int }`.
i.e., the type of `String` has a reference to itself, nested inside the type of
`indexOf`.... i.e. `String`'s type is *cyclic*.


## Less Cyclic Types

This problem of cyclic types has been around for a long time, and there a
number of tried and true methods for dealing with them.  One easy one is to
have a type *definition* and a separate type *reference*.  The reference refers
to the type by doing some kind of lookup; an easy one is via the type name and
the parsers' symbol table i.e., some kind of hash table lookup.

In this model the cycle is effectively avoided; all "back edges" in the cycle
are really done by the reference edge (itself possibly a hash table lookup).

So why not go down this route?

Because all type references lead back to the same type definition - which
means any specialization of type information is lost, because *all* type
references lead back to the same definition and you end up taking the MEET 
over *all* paths.

Here's example, a Linked List with a Java `Object` or a C `void*` payload:

```java
struct List {
  List next;
  Object payload;  // equivalently: void*
}
// Then walk a collection of ints and build a List:
List nums = null;
for( int x : ary_ints ) 
    nums = new List{next=nums; payload=x; }
    
// Again for strings:
List strs = null;
for( String x : ary_strs ) 
    strs = new List{next=strs; payload=x; }
```

What is the type of `nums`?  It's `*List`... which is:

```
struct List { List *next; Object payload }
```

No sharpening of `Object` to `int` nor `String` because every List object
has a payload of `Object` and `Object` MEET `int` is back to `Object`.

It we allow truely cyclic types then in these kinds of places we can discover
(or infer) a sharper type: `*List<int> { *List<int> next, int payload }`
basically building up *generics* in the optimizer.  This is not generics in the
language nor type-variables per-se, but it goes a long way towards them.


## Handling cyclic types

In this Chapter Simple handles cyclic types directly.  This means we have
`Type` objects whose child types can point back to the original - and that
means 
[*recursive descent*](https://en.wikipedia.org/wiki/Recursive_descent_parser) 
no longer works on Types.

Up till now we've used recursive descent when building up Types: child types
are built first, then interned, then the interned children are used to build up
the next layer.  Because each layer is interned and because recursive descent
stops at interned Types we avoid repeating work at shared Types.  This makes
operations on the current Types linear in the *delta* between the old and new
Types, i.e. incremental and fast.

With the new cyclic types we now run into the problem that we cannot intern a
cycle until we see the whole cycle.  Also hash tables require an equality
check and we cannot use recursive descent to compare a cycle for equality.

The standard way to deal with cycles in a graph is with some kind of *visited*
notion, a "I have been here before" indicator.  We will use a hashtable keyed
from a unique type ID; "visited" just means your unique ID is a key in the
table.  Every Type thus picks up a unique ID from a global counter.  Since we
will be interning Types we only need as many unique IDs as we have unique Types
(plus a few spare to build and intern cycles).  From prior history I know this
number is typically maxes out in the low thousands so a small integer will do.

Another thing - we want to keep as much of the our existing infrastructure as
we can, so we are going to keep all our existing recursive descent visitations
with some small modifications.

`TypeStructs` exist in every Type cycle (except pure function type cycles,
where a function takes *itself* as an argument, which are weird and we kinda
don't care how they get handled).  So in the core part of every `TypeStruct`
generator we'll set an element in a global `VISIT` hashtable to signal the
start of a cyclic Type creation - and we'll check for those elements to stop
cycling.




Time for an example!

Lets go with a linked list, where we might have cycles of unrelated payloads
(but here only strings).  Might you, linked-list is almost always the wrong
structure for any given job (being wildly inefficent compared to easy
alternatives) but it is a great tutorial data structure.


```java
struct List { 
    List? !next; // Next pointer or null
    str !name;   // Payload
};
```

And the type of this struct matches its declaration:

`struct List { List*? next, str !name };`

We can use this to build up a list of strings:

```java
var list = new List{ name="Hello"; };
list = new List { list, name="World"; };
```


### Checking a cyclic type for Read-Only / Final

Suppose at some point we want to make a read-only immutable version:
```java
val hello = list; // Make a read-only version
print(hello);     // Pass along the read-only version
```

What is the type of `hello`?  It is the read-only type of `List`... and `List` is
a cyclic type.  Lets look at `Type.makeRO`:

```java
public final Type makeRO() {
    if( isFinal() ) return this; // First check if already read-only
    return recurOpen()._makeRO().recurClose();
}
```

and `isFinal()`:

```java
// Are all reachable struct Fields are final?
public final boolean isFinal() { return recurClose(recurOpen()._isFinal()); }
boolean _isFinal() { assert _type < TCYCLIC; return true; }
```

We call `recurOpen()` to start/open the recursive walk, do the recursion
calling `_isFinal()` and end/close the recursive walk, and return a boolean
answer.  `_isFinal()` is NOT Java final, and is overridden in the Type subclasses.
Most subclasses just forward the problem along:

`@Override boolean _isFinal() { return _obj._isFinal(); }`

until we hit `TypeStruct`:

```java
@Override boolean _isFinal() {
    if( _open ) return false;     // May have more more non-final fields
    if( VISIT.containsKey(_uid) ) // Test: been here before?
        return true;              // Cycles assume final
    VISIT.put(_uid,this);         // Set: dont do this again
    for( Field fld : _fields )
        if( !fld._isFinal() )
            return false;
    return true;
}
```

Here we see a common pattern in dealing with cycles: the **test-and-set**.  We
check for any hard and fast answers (open structs are never read-only; they are
only partially defined and as-yet-unparsed non-final fields may appear), then
we **test** if we have been here before, and if so stop the recursion and
return some kind of answer.  For "isFinal" the returned answer is "yes final"
as long as everything else in the cycle is also final.

If we have not already checked this Type, we **set** the "visit" bit indicating
we've been here before, and then proceed to do normal recursive-descent on the
fields asking the same question recursively.

For our example, we open the recursion (set a sentinel in `VISIT`), call
`makeRO` on `struct List`, which then calls `_isFinal` which then checks
the `_open` flag (not open), checks the `VISIT` (not visited), sets the
UID in `VISIT`and starts walking the fields.  Fields check:

`@Override boolean _isFinal() { return _final && _t._isFinal(); }`

The first field is `List*? !next`, which is not-final, so immediately returns
`false`, which stops `_isFinal` on `List` and the whole `isFinal()` call returns
`false`, and ends/closes the recursion (clears `VISIT`).


### Making a cyclic type for Read-Only / Final

To make the build-then-intern more obvious, lets put unique IDs on all our
Types.  Here is the Type of `List` with sample UIDs following each unique type.
Fields are also just types so also get UIDs.

```
struct List#2 {       // struct List has UID#2
  List#2*?#3 !next#4, // Field  next has UID#3, type ptr-or-null#3 to List#2
  str#1 !name#5       // Field  name has UID#5, type str#1
};
```

You can see from the repeated UIDs that `List#2` happens twice... because List
is cyclic.

Now we do really need to make a cyclic type, and so our `makeRO()` call falls
into the next line:

`return recurOpen()._makeRO().recurClose();`.

Once again `recurOpen` starts/opens our recursive walk, and then we call
`_makeRO()` which is overridden in every child Type class.  Lets look at
`TypeStruct`s version:

```java
// Make a read-only version
@Override TypeStruct _makeRO() {
    // Check for already visited
    TypeStruct ts = (TypeStruct)VISIT.get(_name);
    if( ts!=null ) return ts;   // Already visited
    ts = recurPre(_name,_open); // Make a new type with blank fields
    Field[] flds = ts._fields;
    for( Field fld : flds ) fld._final = true;

    // Now start the recursion
    for( int i=0; i<flds.length; i++ )
        flds[i].setType(_fields[i]._t._makeRO());

    return ts;
}
```

First up is the **test-and-set**.  The **test** is by type name; we attempt to
get a `List` TypeStruct from `VISIT`, and if successful we return early -
without recursion.  This means we can only have one `List` object in a R/O
cycle of types.  You can imagine a tangle of multiple nested cyclic types where
some are "List-of-int" and some are "List-of-Lists-of-", but here in the name
of simplicity we choose to approximate our types to only a single instance of
`List`.

In this example on first visit of `List` we will miss in `VISIT`.  The next call
to `ts = recurPre(_name,_open)` does common shared pre-recursive work: we make
a new `TypeStruct` and **set** it in the `VISIT` table.  The new `TypeStruct`
is a blank version of `List` will all fields attached; we force these fields to
be final right away but they are missing their types:

```
struct List#12 {
  ____ next#14,
  ____ name#15
}
```


Finally, we recursively call `_makeRO` on all the field types and set them in.
We call `_fields[i]._t._makeRO()` on a `TypeMemPtr` which makes a new
TMP after calling `_makeRO()` on the recursive `List`.

This recursion comes right back to `TypeStruct._makeRO()`, and hits in the
`VISIT` table on the same type name.  Unwinding the first field set, we have:

```
struct List#12 {
  List#12 *? #13 next#14, // Recursve next field makes a cycle
  ____  name#15
}
```

You should notice all new UIDs for all the Types.  Since we cannot intern yet,
we cannot end up with prior types.  The second field `str name` does already
exist, so we end up with:

```
struct List#12 {
  List#12 *? #13 next#14, // Recursve next field makes a cycle
  str#2 name#15           // Reuse the interned type strt#2
}
```

At this point the recursion unwinds and we head into the complex
`Type.recurClose()`.  You might try single-stepping through the code for a few
examples; the `TypeTest.testList` test builds these types.  Here is a high
level summary:

Copy all the visited types out of `VISIT` for easier management; we might find
some of these types already interned, and then we'll remove them from further
processing; this is easier if they are not in a HashMap.

**Pass#1:** look for pre-existing interned parts not in any cycle, and just
replace them.  We could have a new cycle of Types with pointers off to older
interned types... that we made copies of.  After this step, we'll still have
new cycles but the output edges will point to prior types if possible.
Replaced types hit the delayed `FREES` list.

**Pass#2:** All types have their *duals* pre-computed and directly available.
Make the recursive cyclic duals now.

**Pass#3:** Install the new types.  It *is* possible to now discover common
pointers to newly interned types, and so again we need to test and replace as
we go (and put the unused types on the delayed `FREES` list again).  This step
requires hash table probe - which requires a cyclic hash and cyclic equals.

**Pass#4:** Free up all the delayed-free Types now, to be recycled in future
Type productions.

In the end, the new `List` cyclic type is wholly interned - with a R/W version
of `List#2` pointing back to the R/W `List#2` and the R/O version `List#12`
pointing back to the same R/W `List#12`.



### Cyclic HashCode and Equals

Cyclic `hashCodes` and `equals` deserve more discussion.  When compare two
cycles for equivalence we want to be equivalent regardless of where the
comparison starts.  i.e. cycle `A<->B` should be equal to cycle `B<->A`.  This
means they both need the same hashcode, and this means we cannot use
order-varying hashes!

E.g. Suppose A has static hash value 1 (plus something recursively from B's
hash), and vice-versa B has static hash value 3 (and something recursive from
A), and we compute hashes as `parent.hash*7 + child.hash`.  If we compute A's
hash as `A.hash * 7 + B.hash` we get `1 * 7 + 3 == 10`, and then later we start
from B we get: `B.hash * 7 + A.hash` and which then becomes `3 * 7 + 1 ==
22`... and their hashes differ so the hash table might miss the equivalence.
Also, if we simply use recursive descent in the cycle we'll
recurse until stack overflow and crash.  

We fix this by having `TypeStruct.hash()` *not* recurse - thus its hash
function is somewhat weak, depending only on the field names and aliases.

*Cyclic-equals* is triggered by the `VISIT` table being not-empty, and uses
another unrelated *visit* table, the `CEQUALS` table, to seperate concerns from
`VISIT`.  The cycle-equals check proceeds like the normal `eq` with recursive
descent on the parts, and some up-front hard-and-fast static checks - and the
ubiquitous **test-and-set** pattern before the recursion.

If we ever compare the same two types for cycle-equals again, we assume they
will be cycle-equals (all other things being equals).



## Methods and Final Fields and Classes

Methods are simply function values (final fields) declared inside a struct that
take an implicit `self` argument.

Final fields declared in the original struct definition are *values* and cannot
change; they are the same for all instances.  They get moved out of the
struct's memory footprint and into a *class* - a collection of values from the
final fields.  Classes are just plain namespaces and have no concrete
implementation; they are not reified.

```java
struct vecInt {
    u32 !len;   // Actual number of elements
    int[] !buf; // Array of integers.
    
    // Since "add" is declared with `val` in the original struct definition,
    // it is a final field and is moved out of here to the class.
    
    // Method: Add an element to a vector, possibly returning a new larger vector.
    val add = { int e ->
        if( len >= buf# ) _grow(buf#*2)
        buf[len++] = e; 
        self;
    };
    
    // Method: Internal: copy buf to a new size
    val _grow = { int sz -> 
        val buf2 = new int[sz];
        for( int i=0; i<len; i++ )
            buf2[i] = buf[i];
        free(buf);  // Needs a better Mem Management solution
    };

};

val primes = new vecInt{}.add(2).add(3).add(5).add(7);

```

Here the `val add` field is a *value* field, a constant function value.  It is
moved out of the basic `vecInt` object into `vecInt`'s *class*, and takes no
storage space in `vecInt` objects.  The same happens for `_grow`.  The size of
a `vecInt` is thus a `u32` word, a pointer to an `int[]` plus
`buf#*sizeof(int)`, plus any padding (probably none here).
