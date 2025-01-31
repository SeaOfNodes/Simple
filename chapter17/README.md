# Chapter 17: Syntax Sugar

# Table of Contents

1. [Pre/Post-increment](#prepost-increment)
2. [Operator assignment](#operator-assignment)
3. [var/val](#var--val)
   - [Example(1)](#example1)
   - [Example(2)](#example2)
4. [Mutability](#mutability)
5. [Reference immutability](#reference-variables-with-an-initializer-are-deeply-immutable)
6. [Trinary](#trinary)
7. [For Loops](#for-loops)

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter17) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter16...linear-chapter17) it to the previous chapter.

## Pre/Post-increment

Allow `arg++`, `arg--`, `++arg`, and `--arg` with the usual meanings:

- Post-Increment (`arg++`): The value is updated after the expression is evaluated, and the expression evaluates to the pre-update value.


- Post-Decrement (`arg--`): The value is updated after the expression is evaluated, and the expression evaluates to the pre-update value.


- Pre-Increment (`++arg`): The value is updated before the expression is evaluated, and the expression evaluates to the post-update value.


- Pre-Decrement (`--arg`): The value is updated before the expression is evaluated, and the expression evaluates to the post-update value.

Greedy match is used so `arg---arg` parses as `(arg--) - arg`.

Also allows `s.fld++` and `ary[idx]++`, and their `--` variants.

Allow pre-increment for identifiers only, for now: `--pc`.


## Operator assignment

Along the same lines as post-increment, we now allow `op=` assignment(compund assignemnt)
with semantics similar to other languages.

- `x += y`

- `x -= y`

- `x *= y`

- `x /= y`


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

#### **Example(1)**:
```java
var a = 1; // infers as TypeInteger.BOT
return a; 
```
The type inferring happens here:
```java
// var/val, then type comes from expression
if( inferType )
    t = expr._type.glb();
```
`var` allows modyfing the value, while `val` does not.

#### **Example(2)**:
```java
val a = 1; 
a = 2; // Error: Cannot assign to final 'a'
return a;
```
```java 
if( _scope.in(def._idx)._type!=Type.TOP && def._final && !_scope.inCon() )
throw error("Cannot reassign final '"+name+"'");
```
## Mutability

Typed primitive fields are always mutable.  Typed reference fields without an
initializer must be mutable to get a value.  Initialized reference variables
are immutable by default and can be made mutable with a leading `!`.  `var` and
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


### Reference variables with an initializer are deeply immutable

Initialized reference variables are deeply immutable, but a mutable version of the same
reference can exist.  This works for `val` and normal type declarations: `val s
= new S;` and `S s = new S` both make `s` an immutable reference to a `struct S`.

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