Trying to solve this problem:

Types are too *strong* upon loading a single compunit from serialized form; I
do not have the *local* monotonicity property.  At the time of serializing, I
have a global monotoncity property (heavily asserted for) but this relies on
having global knowledge.  The act of writing out a compilation unit means this
compunit has only itself to rely on, not any other compunit nor any global
property.  The Serialize.check assert has been weakened to exclude some of
these too-strong types; I want to weaken the types and strengthen the assert so
its an exact type match after readAll.

I want to weaken the types and graph being written out, such that when its read
back in, it has the *local* monotonicity property.  A way to do that is to
break apart the seperate compunit graphs (mostly done already, just needs to
unlink cross-compunit Call/CallEnds), then weaken the types to exclude values
from other compunits: {aliases, fidxs, rpcs}, and their presence in XInt fields
in Types.  These {alias,fidx,rpc} are in the GlobalBits in the CodeGen, and
are labeled which compunit they belong to.

I want to visit all Nodes and their Types (including extra type fields that
e.g. upgradeType calls visit, TypeNode._con and FunNode._sig, etc), and modify
embedded XInt fields such that:
- If they refer to bits in the current CompUnit, then keep them across the
  write/read cycle, compressing them before writing (instead of after reading
  as it does now).
- If no *other CompUnit* bits are present, I think the current bits are
  precise; keep the XInt positive (no infinite extension).
- If *other CompUnit* bits are present, assume that some infinite number of
  other CompUnits will send in bits when compiled together, and set the XInt
  negative/infinite bit.

After Serializing, restore all bits to the same global bit-space, relink
unlinked Calls, and assert once again that the global types are all stable.

As before, after hacking the code, start solving for bugs from the earlier
easier tests before moving on to harder tests.  After this, Chaptr25
testModule0 should work, and testSys up to the point where we load the
compiled sys library anew for compiling against the helloWorld program.
