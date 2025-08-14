# Chapter 24: Chaining relationals and Sparse Conditional Constant Propagation

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter23) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter22...linear-chapter23) it to the previous chapter.


## Chaining relationals

Chainged relationals offer a cleaner, more readable way to write chained
comparisons without repeating the same variable.

```java 
if (min <= x && x < max)
```

becomes

```java 
if (min <= x < max)
```

This syntax saves space and makes the condition more intuitive, similar to how
range checks are written in mathematics.  E.g.

```java 
int score = 75;
if (60 <= score < 90) {
    sys.io.p("Pass");
}
```
This checks if score is between 60 (inclusive) and 90 (exclusive), without repeating `score`.

Expressions that mix opposite directions of comparison, like using both <= and
>=, are not allowed, because they create ambiguous logic.

```java
if (a <= b >= c)
```


## Operator direction rules
Stacked comparisons are *only valid* if all the comparison operators
"point" the same way:
- `<` with `<`
- `<` with `<=`
- `>` with `>`
- `>` with `>=`

It is not allowed to mix directions in a single chain:
```java 
if (a <= b >= c) // Invalid
```

Comparisons can be forced into numeric context using booleans.
This is valid but unusual:
```java
return (0 < arg) > 1;
```
Depending on the result of (0 < arg) this will turn into either
```java
return 0 > 1;
```
OR
```java
return 1 > 1;
```
The equality operator is valid in this form, but since it has lower precedence than comparisons,
```java 
a < b == c
```
is parsed as:
```java
(a < b) == c
```
This might not be the intended chained comparison.
A more explicit alternative is: 
```java 
a < b && b == c
```

Comparisons can be chained together in any length, and different comparison
operators may be mixed freely as long as they point in the same direction.
Equality(`==`) and inequality(`!=`) operators may be combined with any other
comparisons at any point in the chain. E.g:

```
return 0 < arg < arg+1 < 4;
```

## SCCP(top-down)