# Chapter 17: Syntax Sugar


## Pre/Post-increment

Allow `arg++` and `arg--` with the usual meanings; the value is updated and the
expression is the pre-update value.  Greedy match is used so `arg---arg` parses
as `(arg--)-arg`.

Also allows `s.fld++` and `ary[idx]++`, and their `--` variants.

Allow pre-increment for identifiers only, for now: `--pc`.


## var & val

`var` can be used in the `type` position to indicate a "variable" (mutable
value), whose type will be inferred from the required initalizing expression.

`val` is the same as `var`, except it is a "value" (not mutable).

The inferred value is the `glb` of the peephole type, which means `var` and
`val` will not infer types like `u8` or `f32`, instead inferring `int` and
`flt` respectively.  Reference types will always infer as nullable, so e.g. 

`var s = new S;` 

infers as

`S s? = new S;`


## Mutability

Typed primitive fields are always mutable.  Typed reference fields without an
initializer must be mutable to get a value.  References with an initializer are
immutable by default and can be made mutable with a leading `!`.  `var` and
`val` keep their current sense and can be used to make any field mutable or
immutable.  Fields are always mutable during construction, but will become
immutable at the end of either constructor.

`int x; x=3; x++; // OK, primitive so mutable`
`int x   =3; x++; // Ok, primitive so mutable, despite initializer`

Assume in the next examples this struct exists:
`struct S { int x; };`

Then:
`S s; s = new S     ; s.x++; // OK, no initializer so s.x is   mutable`
`S s; s = new S{x=3}; s.x++; // OK, no initializer so s.x is   mutable`
`S s    = new S     ; s.x++; // Error, initializer so s.x is immutable`
`S s    = new S{x=3}; s.x++; // Error, initializer so s.x is immutable`

Leading `!` makes mutable:
`S !s = new S; s.x++; // Ok, has '!' so mutable`

'var' is variable:
`var s = new S; s.x++; // Ok, has var so mutable`

'val' is a "value": not mutable through this reference, but may be mutable
through other references.
`val s = new S; s.x++; // Error, val so s.x is immutable`



### References with an initializer are deeply immutable

References with an initializer are deeply immutable, but a mutable version of
the same reference can exist.  This works for `var` and normal type
declarations: `var s = new S;` and `S s = new S` both make `s` an 
immutable reference to a `struct S`.


```cpp
struct Bar { int x; }
Bar !bar = new Bar;
bar.x = 3;         // Ok, bar is mutable

struct Foo { Bar !bar; int y; }
Foo !foo = new Foo;
foo.bar = barl     // Ok bar is mutable
foo.bar.x++;       // Ok foo and foo.bar and foo.bar.x are all mutable

val xfoo = foo;    // Throw away mutability
xfoo.bar.x++;      // Error, cannot mutate through xfoo

print(xfoo.bar.x); // Ok to read through xfoo, prints 4
foo.bar.x++;       // Bumps to 5
print(xfoo.bar.x); // Ok to read through xfoo, prints 5
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