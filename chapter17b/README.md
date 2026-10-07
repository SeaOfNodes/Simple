# Chapter 17b: Syntax Sugar

English | [日本語](README.ja.md)

[Previous: Chapter 17a](../chapter17a/README.md) |
[Next: Chapter 18](../chapter18/README.md)


Here is the [complete language grammar](docs/17-grammar.md) for this chapter.

## Table of Contents

1. [Pre/Post-increment](#prepost-increment)
2. [Operator assignment](#operator-assignment)
3. [var/val](#var--val)
4. [Trinary](#trinary)
5. [For Loops](#for-loops)

Chapter 17a established the permissions checked by assignments. This chapter
adds shorter ways to write assignments and control flow without changing those
permissions. Chapter 18 then introduces functions and calls.

## Pre/Post-increment

Allow `arg++` and `arg--` with the usual meanings; the value is updated and the
expression is the pre-update value.  Greedy match is used so `arg---arg` parses
as `(arg--)-arg`.

Also allows `s.fld++` and `ary[idx]++`, and their `--` variants.

Allow pre-increment for identifiers only, for now: `--pc`.


## Operator assignment

Along the same lines as post-increment, we now allow `op=` assignment
on local variables with semantics similar to other languages. The supported
operators are `+=`, `-=`, `*=`, `/=`, `&=`, `|=`, `^=`, `<<=`, `>>=`, and `>>>=`.
Bitwise and shift assignments require integer operands; `>>=` preserves the sign
while `>>>=` shifts in zeros. The assignment evaluates its right-hand side once,
stores the result narrowed to the variable's type, and yields that stored value.
Assignments associate right-to-left, as in `x |= y <<= 2`.



## var & val

`var` and `val` declare variables and infer their types; `var` infers a
reassignable binding while `val` infers a fixed binding.  Both preserve access
permissions from the initializing expression.  Neither makes a writable object
read-only.  The two independent permissions come from [Chapter
17a](../chapter17a/README.md).

```cpp
struct Point { int x; };
val p = new Point; // Fixed binding to a writable new object
p.x = 3;           // Allowed
// p = new Point;  // Error: fixed binding
Point view = p;    // Explicit read-only view
var q = view;      // Reassignable binding, still read-only access
// q.x = 4;        // Error: cannot restore write permission by inference
```

The initializer is required for type inference.  Primitives infer their broad
numeric type (`int` or `flt`); references retain their access permissions.  A
reassignable inferred reference allows null so it can later hold the empty
value.  Explicit types retain their declared width and nullability.  Function
parameters still require explicit types.

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
    sum += i;
return sum;
```

Any of `init`, `test` and `next` can be empty.  `init` allows for declaration
of a new variable, with scope limited to the `for` expression.

```cpp
int sum=0;
for( int i=0; i<arg; i++ )
    sum += i;
return i; // ERROR: Undefined name 'i'
```

A broken `find` call:
```cpp
for( int i=0; i<ary#; i++ )
  if( ary[i]==e )
    break;
do_stuff(i); // ERROR: undefined name 'i'
```

An example `find` call:
```cpp
for( int i=0; i<ary#; i++ )
  if( ary[i]==e )
    return i;
return -1;
```
