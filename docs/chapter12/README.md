# Chapter 12: Floats

In this chapter, we add a floating point type and values.

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter12) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter11...linear-chapter12) it to the previous chapter.

## Floats

Floating point values start with the `flt` type and compute IEEE754 64-bit arithmetic.
Integer expressions auto-widen to floats when involved in a float expression.

```java
flt f = 0.5;
int x = 2;
f = f+x;     // Auto-widen x to 2.0, result is 2.5
f = f+true;  // Since bools are just synonyms for integers 0,1 they auto-widen also
```

There is no corresponding way right now to round a `flt` back to an `int`:
```java
int x = 3.14; // error
```

Here is Newton's method to a square root:
```java
flt guess = arg;  // Initial guess is just argument
while( true ) {
    // Next guess from Newton's method
    flt next = (arg/guess + guess)/2;
    // Break if hit fixed point
    if( next == guess ) break;
    guess = next;
}
return guess;
```
When run with argument 2.0, produces 1.414213562373095.

Floating point operations have their own Nodes:

| Node                   | Op                   |
|------------------------|----------------------|
| AddF, SubF, MulF, DivF | Basic binary FP ops  |
| MinusF                 | Unary negate         |
| EQF, LTF, LEF          | FP compare operators |

And their own section in the Type lattice:

Within the Type Lattice, we now add the following domain:

* Float type - Float values.  The same shape as the Integer values, but floats
  with a TOP, BOT and constants


