# Chapter 17: Syntax Sugar

# Post-increment

Allow `arg++` and `arg--` with the usual meanings; the value is updated and the
expression is the pre-update value.  Greedy match is used so `arg---arg` parses
as `(arg--)-arg`.


var/val
invert !
C++-style for-loop
for-interator




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