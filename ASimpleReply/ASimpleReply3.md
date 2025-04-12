# A Simple Reply (3)

The whole reply chain:

https://github.com/SeaOfNodes/Simple/blob/main/ASimpleReply/ASimpleReplyChain.md


## Unzipping

Starting from Dadal's code, which has inlined some specialization checks:

```
foo (o) {
  let x;
  if (o.map == Map1) {
    x = RawLoad(o, offset=8);
  } else if (o.map == Map2) {
    x =  RawLoad(o, offset=16);
  } else if (o.map == Map3) {
    x =  RawLoad(o, offset=12);
  } else throw deopt; // Cliff added
   ....   // Cliff adds: call this code "S1"
  let y;
  if (o.map == Map1) {
    y =  RawLoad(o, offset=0);
  } else if (o.map == Map2) {
    y = RawLoad(o, offset=20);
  } else if (o.map == Map3) {
    y = RawLoad(o, offset=4);
  } else throw depopt; // Cliff added
}
```

I added the "throw deopt", which I assume Dadal implies when `x` and `y` are
none of `Map1, Map2, Map3`.

Dadal then adds: 

> "Here, it's not like any of the type-checks when loading `y` could be
> removed. Well, in some cases, we do things like double-diamond elimination to
> remove some checks, but often it's not possible."

C2 sees this kind of pattern fairly often as well; generally replacing `o.map
== Map1` with `o instanceof Map1`; not that it matters.  C2 uses a "unzipping"
xform for repeated checks; I've talked about it publically numerous times.
Here C2 will look at the `S1` code above, and based on a heuristic (mostly
size) it will clone `S1` to remove the excess checks:

```
foo (o) {
  let x,y;
  if (o.map == Map1) {
    x = RawLoad(o, offset=8);
    S1;
    y = RawLoad(o, offset=0);
    
  } else if (o.map == Map2) {
    x = RawLoad(o, offset=16);
    S1;
    y = RawLoad(o, offset=20);
    
  } else if (o.map == Map3) {
    x = RawLoad(o, offset=12);
    S1;
    y = RawLoad(o, offset=4);
    
  } else throw deopt; // Cliff added
}
```

Now you get 3 chunks of specialized code where in each case you know the type
of `o.map` throughout.  This process can be repeated at all kinds of levels,
and assuming the strong knowledge of `o.map` allows more optimizations, can pay
for itself despite cloning some amount of `S1`.  Its certainly common for C2 to
clone long runs of repeated checks such that in the end there's a fast-path
where all checks pass "the good way", and bail-out points all long that go to a
correct but slow path.


## Common Case vs Deopt

```
foo (o) {
  let x = o.x;
  bar();  // Might change the "shape" of `o`
  let y = o.y;
}
```

Dadal: 

> "If `bar` isn't inlined, then we have to assume that `bar` could change
> the shape of `o`, and thus a type-check is needed before loading `y`."

In a similar situation C2 will do the same.  Of course, for sufficiently hot
`foo` and modest `bar`, inlining is the answer.  This makes me wonder if C2's
inlininhg budget is substantially higher than V8's.

Dadal: 
> "Oh, and `bar` could be changing the shape of `o` only some times"

In any of these "sometimes Bad Things Happens" situations, C2's go-to answer is
to register a dependency and deopt if the Bad Thing happens.  The set of Bad
Things that happen between JS and the JVM are different, but it seems to me the
general theory still holds.  Similar (not exact) situation:

```java
foo( Object o ) {
    var x = ((Cat)o).x;  // Dependency that Cat has no subclasses
    x.bar();             // Always a `Cat` so call Cat.bar
}
```

If later `Cat` is extended with `Siamese` which implements an alternative
`bar`, this code is deopt'd.  The existing calls to `foo` can finish - they are
correct because they got called with a `Cat` and cannot change that - but no
new calls are allowed; they will use an alternative more conservative execution
until a fresh JITd code for `foo` can be made.


## Summary

Yeah, the problems between JS and the JVM are different... but not hugely so.
And the problems shown here are *utterly* unrelated to the Sea-of-Nodes.
I'd do this stuff in a normal CFG framework just the same.

Cliff
