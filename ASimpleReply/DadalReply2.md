
This is Dadal's reply from U-tube, recreated here (permission pending) so its
both easier to find and not lost if U-tube changes their mind.

The original link I copied from:

https://studio.youtube.com/channel/UCbutMk6cKrH8q4sJyQHGPVQ/comments/inbox?filter=%5B%5D

The whole replace chain:

https://github.com/SeaOfNodes/Simple/blob/main/ASimpleReply/ASimpleReplyChain.md



Type checks: to give an example:

```
foo(o) {
  let x = o.x;
  ....
  let y = o.y;
}
```

Assuming polymorphic feedback (which is very frequent in JS), this will become something like (using JS-like syntax, but this happening inside the compiler of course)
```
foo (o) {
  let x;
  if (o.map == Map1) {
    x = RawLoad(o, offset=8);
  } else if (o.map == Map2) {
    x =  RawLoad(o, offset=16);
  } else if (o.map == Map3) {
    x =  RawLoad(o, offset=12);
   }
   ....
  let y;
  if (o.map == Map1) {
    y =  RawLoad(o, offset=0);
  } else if (o.map == Map2) {
    y = RawLoad(o, offset=20);
  } else if (o.map == Map3) {
    y = RawLoad(o, offset=4);
   }
}
```

Here, it's not like any of the type-checks when loading `y` could be removed. Well, in some cases, we do things like double-diamond elimination to remove some checks, but often it's not possible.

For monomorphic cases, subsequent checks will be removed when possible, but objects can change shapes in JavaScript. This means that if you have something like
```
foo (o) {
  let x = o.x;
  bar();
  let y = o.y;
}
```
If `bar` isn't inlined, then we have to assume that `bar` could change the shape of `o`, and thus a type-check is needed before loading `y`. Oh, and `bar` could be changing the shape of `o` only some times, so `o.x` could have monomorphic feedback while `o.y` could have polymorphic feedback. Or the other way around :)
