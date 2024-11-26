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

With `var`:
```
var dist = { flt x, flt y -> sqrt(x*x*,y*y); }
```

Functions are called in the usual way:
`dist( 1.2, 2.3 ) // yields 2.59422435`

Functions can have zero arguments, in which case the `->` argument seperator is
optional.  This allows any section of code to be "thunked" by wrapping it in
`{}`.

`just5 = { -> 5 } // With    arrow`
`just5 = {    5 } // Without arrow`

Called:
`just5() // Returns 5`


Functions can be declared inside `struct`s, and will take a hidden `self`
argument to allow access to the struct members - basically the function becomes
a method.

Also new this chapter, fields with a leading underscore `_` are private fields,
and only accessible to 'self'.

```cpp
struct String {
  u8[] _chars; // Private, not nullable, final, must be initialized in constructor
  
  // Functions declared inside of a struct become instance methods
  // and have access to the struct members.
  val len = { -> _chars#; }

  val at = { idx -> _chars[idx]; }
  
  // 'self' keyword returns an instance of itself.
  val append = { String? str ->
    // Appending a null appends nothing
    if( !str ) return self;
    
    // Manual array copy _chars to cs
    u8[] cs = new u8[_chars# + str.len()];
    for( int i=0; i<_chars#; i++ )
      cs[i] = _chars[i];
      
    // Manual array copy str._chars to cs
    for( int i=0; i<str.len(); i++ )
      cs[_chars#+i] = str.at(i);

    // Append returns a new String
    return new String { _chars=cs; };
  }


};

```

With some syntax sugar from the parser we can produce `Hello, Cliff`
with method calls.
```
String name  = "Cliff";
String greet = "Hello";
return greet.append(", ").append(name);
```

There's only a little special syntax going on here with method calls.  The
normal field lookup is used for `greet.append`, yielding a function-typed
value.  Since it was loaded from the field, the function is **pre-bound**
(curried) to the `self` it was loaded from.

`{int->u8} atCliff = name.at; // The "at" for Cliff; function does not take a String anymore`
`var       atHello = greet.at;`
`atCliff[0]; // Returns 'C'`
`atHello[0]; // Returns 'H'`
