# Chapter 17: Syntax Sugar


## Pre/Post-increment

Allow `arg++` and `arg--` with the usual meanings; the value is updated and the
expression is the pre-update value.  Greedy match is used so `arg---arg` parses
as `(arg--)-arg`.

Also allows `s.fld++` and `ary[idx]++`, and their `--` variants.

Allow pre-increment for identifiers only, for now: `--pc`.


## var & val

`var` can be used in the `type` position to indicate a "variable" (mutable
value), whose type will be inferred from the initalizing expression.

`val` is the same as `var`, except it is a "value" (not mutable).

The inferred value is the `glb` of the peephole type, which means `var` and
`val` will not infer types like `u8` or `f32`, instead inferring `int` and
`flt` respectively.  Reference types will always infer as nullable, so e.g. 

`var s = new S;` 

infers as

`S s? = new S;`


## Initialized fields are immutable by default

Typed field declarations with an initializer are immutable by default, and can
be made mutable with a leading `!`.  `var` and `val` keep their current sense.
Fields are immutable at the end of either constructor, but can be changed
during construction.


Examples:


```java
struct Point { int x,y; };  // Fields x and y are not initialized, mutable
Point p = new Point;
p.x++;                      // Mutate is ok
return p.x;
```

```java
struct Point { int x=3,y=4; };  // Fields x and y are initialized, immutable
Point p = new Point;
p.x++;                          // Error!
return p.x;
```

```java
struct Point { int x=3,y=4; };  // Fields x and y are initialized, immutable
Point p = new Point { x=x*x; y=y*y; }; // Ok to mutate in the constructor
p.x++;                          // Error!
return p.x;
```

```java
struct Point { int !x=3, !y=4; }; // Fields x and y are initialized, mutable
Point p = new Point; 
p.x++;                            // Mutate is OK
return p.x;
```

```java
struct Point { var x,y; }; // Error, var and val require an initializer
Point p = new Point; 
p.x++;
return p.x;
```

```java
struct Point { var x=3,y=4; };  // Fields x and y are mutable
Point p = new Point; 
p.x++;                          // Mutate is OK
return p.x;
```

```java
struct Point { val x=3,y=4; };  // Fields x and y are immutable
Point p = new Point; 
p.x++;                          // Error!
return p.x;
```


## Deep Immutability (proposal)

```cpp
struct Bar { int x; };
Bar  b  = new Bar; /* b = new Bar; *//* b.x++; */ // immutable & deep immutable
Bar !b  = new Bar;    b = new Bar;      b.x++;    //   mutable & deep   mutable
val  b  = new Bar; /* b = new Bar; *//* b.x++; */ // immutable & deep immutable
var  b  = new Bar;    b = new Bar;      b.x++;    //   mutable & inherits deep mutability from RHS
```


## Trinary

Allow `pred ? e_true : e_false`.  Also allow `pred ? e_true`, where the false result
is the zero type version of the true result.


## For Loops

Allow C/C++ style `for` loops:

`for( init; test; next ) body`

Example:

```cpp
int sum=0;
for( int i=0; i<arg; i++ )
    sum = sum + i;
return sum;
```

Any of `init`, `test` and `next` can be empty.
`init` allows for declaration of a new variable, with scope limited to the `for` expression.

```cpp
int sum=0;
for( int i=0; i<arg; i++ )
    sum = sum + i;
return i; // ERROR: Undefined name 'i'
```



finalize final
oper-assign +=, -=
TODO
for-interator
switch





# Chapter 18: Functions

Some thinking on function syntax:

Function Types:
```
{ argtype, argtype, ... -> rettype }
```

Leading `{` character denotes the start of a function or function type.

Functions are normal variables and assigned the same way:

```functiontype var = function-typed-expr```

Functions themselves using the same syntax with as types, but filled in:

```
{ int x, int y ->
  sqrt(x*x+y*y);
}
```

Used in a declaration statement:
```
// TYPE        VAR  =   EXPR
{flt,flt->flt} dist = { flt x, flt y ->
  sqrt(x*x+y*y);
}
```

Because it gets clunky to write the function type, and because it can be
directly inferred from forwards-flow analysis (not even HM) which we're already
doing at parse-time, we add a new variable declaration syntax: "var" - for
auto-inferring variable types.  Not allowed in function headers.
```
var dist = { flt x, flt y -> sqrt(x*x*,y*y); }
```

`var` can be used for normal variables as well:

```
// Newtons approximation to the square root
var sqrt = { flt x ->
    var guess = x*x/2;
    while( 1 ) {
        var next = (x/guess + guess)/2;
        if( next == guess ) break;
        guess = next;
    }
    guess;
}
```

When inferring pointer types, it will use the `glb` of the initial expression
type, which may not be what you want:

```
struct LLI { LLI? next; int i; };
var head = LLI(); // Want this declared not-null, but GLB will allow null
head = null;      // Allowed
```