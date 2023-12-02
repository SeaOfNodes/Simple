# Chapter 7

In this chapter we introduce the `while` statement.

Here is the [complete language grammar](docs/07-grammar.md) for this chapter.

## Dealing With Back Edges

The complication introduced by looping constructs is that variables flow back into the body of the loop on iteration.
For example:

```java
while(arg < 10) {
    arg = arg + 1;
}
return arg;
```

The variable `arg` is assigned a new value inside the body of the loop, and this then flows back into the body of the loop.

In general, we will rewrite the looping construct as follows:

```java
loop_head:
if ( !(arg < 10) ) 
    goto loop_exit;
arg = arg + 1;
goto loop_head;

loop_exit:
```

Above is for illustration only, we do not have labels and `goto` statements in the language.

From an SSA[^1] point of view, since `arg` flows back, it requires a `phi` node at the head. Conceptually we would like the outcome to be:

```java
// arg1 represents incoming arg
//
loop_head:
arg2 = phi(arg1, arg3);
if ( !(arg2 < 10) ) 
    goto loop_exit;
arg3 = arg2 + 1;
goto loop_head;

loop_exit:
```

Notice that the phi for `arg2` refers to `arg3`, which is not known at the time we parse the `while` loop predicate. This is the crux of the problem that we need 
to solve in order to successfully construct the Sea of Nodes graph, which is always in SSA form.

Recall from [Chapter 5](../chapter05/README.md) that when parsing `if` statements, we clone the symbol tables as we go past the `if` predicate.
Later we merge the two sets of symbol tables at a `Region` - creating pis for all names that encountered a change in definition within the two 
branches of the `if` statement. In the case of the `if` statement, the phis are created at the merge point when we already know the definitions
that are being merged.

The essential idea for loop constructs is to eagerly create phis for all names in the symbol tables *before* we enter the loop's `if` condition,
since we do not know which names will be redefined inside the body of the loop. When the loop terminates, we go back and remove unnecessary
phis. We call this approach the "eager phi" approach.[^2]

In [Chapter 8](../chapter08) we will implement a "lazy phi" approach that creates phis only when we encounter redefinition of a name.

## New Node Types

Our list of nodes remains the same as in [Chapter 5](../chapter05/README.md), however, we create a subtype of `Region` named `Loop` to better
encapsulate some of the additional logic required. A key aspect of this is to temporarily disable peepholes of the `Region` and any phis
created until we complete parsing the loop body. This is because our phis are not fully constructed until the loop end.

## Detailed Steps

1. We start by creating a new subclass of `Region`, the `Loop`. The `Loop` gets two control inputs, 
   the first one is the entry point, i.e. the current binding to `$ctrl`, and second one (`null`) is a placeholder for the back edge that is 
   set after loop is parsed. The absence of a back edge is used as an indicator to switch off peepholes of the region and
   associated phis. 

```java
01         ctrl(new LoopNode(ctrl(),null).peephole());
```

   The newly created region becomes the current control.

2. We duplicate the current Scope node. This involves duplicating all the symbols at
   every level with the scope, and creating phis for every symbol except the `$ctrl` binding.

```java
            // Make a new Scope for the body.
01          _scope = _scope.dup(true);
```
   
   Note that the `dup` call is given an argument `true`. This triggers creating phis. The code
   that creates the phis in the `dup()` method is shown below.

```java
01          // boolean loop=true if this is a loop region
02
03          String[] reverse = reverse_names();
04          dup.add_def(ctrl());      // Control input is just copied
05          for( int i=1; i<nIns(); i++ ) {
06             if ( !loop ) { dup.add_def(in(i)); }
07             else {
08                 // Loop region
09                 // Create a phi node with second input as null - to be filled in
10                 // by endLoop() below
11                 dup.add_def(new PhiNode(reverse[i], ctrl(), in(i), null).peephole());
12                 // Ensure our node has the same phi in case we created one
13                 set_def(i, dup.in(i));
14             }
15          }
```
   Note that both the duplicated scope and the original scope, get the same phi (lines 11 and 13 above).

3. Next we setup the `if` condition, very much like we do with regular ifs.

```java
01          // Parse predicate
02          var pred = require(parseExpression(), ")");
03          // IfNode takes current control and predicate
04          IfNode ifNode = (IfNode)new IfNode(ctrl(), pred).<IfNode>keep().peephole();
05          // Setup projection nodes
06          Node ifT = new ProjNode(ifNode, 0, "True" ).peephole();
07          ifNode.unkeep();
08          Node ifF = new ProjNode(ifNode, 1, "False").peephole();
```

4. We set the control token to the `True` projection node `ifT`, but before that we make another clone of 
   the current scope. 

```java
01          // The exit scope, accounting for any side effects in the predicate
02          var exit = _scope.dup();
03          exit.ctrl(ifF);
```

  The new scope is saved as the exit scope that will live after the loop ends, therefore `$ctrl` in the exit scope is 
  set to the `False` projection. The exit scope captures any side effects of the loop's predicate.

5. We now set the control to the `True` projection and parse the loop body.

```java
01          // Parse the true side, which corresponds to loop body
02          ctrl(ifT);              // set ctrl token to ifTrue projection
03          parseStatement();       // Parse loop body
```

6. After the loop body is parsed, we go back and process all the phis we created earlier.
   
```java
01          // The true branch loops back, so whatever is current control gets
02          // added to head loop as input
03          head.endLoop(_scope, exit);
```

  The `endLoop` method sets the second control of the loop region to the control from the back edge.
  It then goes through all the phis and sets the second data input on the phi to the corresponding entry
  from the loop body; phis that were not used are peepholed and get replaced by the original input.

```java
01          Node ctrl = ctrl();
02          ctrl.set_def(2,back.ctrl());
03          for( int i=1; i<nIns(); i++ ) {
04             PhiNode phi = (PhiNode)in(i);
05             assert phi.region()==ctrl && phi.in(2)==null;
06             phi.set_def(2,back.in(i));
07             // Do an eager useless-phi removal
08             Node in = phi.peephole();
09             if( in != phi )
10                 phi.subsume(in);
11          }
```

7. Finally, both the original scope (head) we started with, and the duplicate created for the body are killed.
   At exit the false control is the current control, and exit scope is set as the current scope.

### Visualization

The example quoted above is shown below at an intermediate state:

![Graph1](./docs/07-graph1.svg)

* Three Scopes are shown, reading clockwise, the loop head, exit and the body.

The final graph looks like this:

![Graph2](./docs/07-graph2.svg)

## More Complex Examples

### Nested Loops

```java
int sum = 0;
int i = 0;
while(i < arg) {
    i = i + 1;
    int j = 0;
    while( j < arg ) {
        sum = sum + j;
        j = j + 1;
    }
}
return sum;
```

![Graph2](./docs/07-graph3.svg)

### Nested With Nested If

```java
int a = 1;
int b = 2;
while(a < 10) {
    if (a == 2) a = 3;
    else b = 4;
}
return b;
```

![Graph2](./docs/07-graph4.svg)


[^1]: Cyton, R. et al (1991).
    Efficiently computing static single assignment form and the control dependence graph, in ACM Transactions on Programming Languages and Systems, 13(4):451-490, 1991.

[^2]: Click, C. (1995).
    Combining Analyses, Combining Optimizations, 103.