
A Simple Reply to https://www.reddit.com/r/Compilers/comments/1jjldhu/land_ahoy_leaving_the_sea_of_nodes/
Because reddit crashes when I try to post this.

## prolog
I kept quiet for awhile on this one, as any response I make is going to sound either defensive or mis-aligned, and in some sense that's right.  Also chat/email/IRC are terrible places to hold in-depth tech discussions.  But I've had quite a number of people rise up to the defense of SoN both here and in other forums, so I finally decided to join the fray.  :-P
Looking at the design choices, it seems clear to me that V8 hobbled itself from the start.  V8 made a number of  choices leading to a number of problems which in my mind have obvious fixes.

## Effects 
*Equivalence class aliasing* is a powerful tool, and a key part of SoN.  It appears to me that V8 threw this away from the start, and then spent the next several years dealing with its lack.
#### "Too many nodes on the effects chain"
Equivalence class aliasing comes for free, literally free, in any strongly typed language.  It comes directly out of the parser (be it JVM bytecodes, Simple, C), and need not be ever touched again.  With it, Java/Simple/C have very sparse and clean effects chains.  Definitely not "too many".
JS is *not* strongly typed, so there's an engineering tradeoff being made.  I was under the believe that V8 (and other JS engines) used *specialization* aggressively to bring back strong typing in code regions; in those areas again ECA should just work directly.
#### "Managing the effect and control chains manually is hard"
Equivalence class aliasing is *directly* represented in SoN, as just normal nodes and edges.  There's no special handling for it anywhere, the normal graph maintenance functions manipulate those nodes and edges just the same as they do anything else.  Two nodes that might alias directly have connecting edges, nodes that do not - do not, and hence *cannot* be influenced about what happens in other alias classes.
As result of those two observations (free to obtain from strong typing, normal node/edge maintenance) this form of effect handling is been vastly simpler to build and maintain, and vastly faster to use than any other compiled I've ever worked on - and that's quite a few.  25yrs of C2 on Java shows that it can be very effective for performance and plenty fast in a JIT.

Me thinks V8 did itself no favors here.

## Control on Effect nodes
Guard tests for correctness: either null checks on fields or range checks on arrays.  Perhaps for JS (and certainly C2/Java) also some type/specialization checks.  C2 also adds a control edge to guard effect nodes.  These then get optimized away (controlling edge removed) in one of two generic ways:
*Lattice*: For null and type checks there is a Node `Type` which includes things like null-ness or sub-classing.  Not-null pointers are known not-null based on the type; null checks against them constant-fold, etc.  (The `Types` form a *lattice* with strong properties like symmetric, complete, bounded (ranked), and meet is commutative and associative.)
After a guarding test we can lift the type - using a Cast tied to the guard test, where the Cast does a lattice *join* (not *meet*).  Effect nodes that need that guard pick up the Cast on the base pointer - not the control edge. If, later, we prove the Cast's *join* is useless e.g. `_t.join(in(1)._type)==_t`, the Cast constant folds away, and the effect Node can now float more freely.  This analysis (comes as part of the normal c-prop) removes > 90% of Java's casting needs.
The other generic test is range-checks, which start life as a direct control edge dependency.  These generally do not trivially fold away - something with all constant sizes and array indices might, but mostly no.  Then C2 runs *Range Check Elimination*, a special pass just for Java safe arrays, and removes dynamically nearly all of them.  I would expect to see the same come from any serious safe-language optimization.

## Debugging
Yeah, don't use graphs to debug.  Use normal ASCII dumps like the ones being shown in this article.  That's what *I* do anyways.  Certainly I didn't have access to fast easy graph layouts back when C2 started, I just debugged it "the normal way".
Graphs are nice for intuition but they don't scale well.  That being said I've seen more recent successes in getting better graphs-for-debug scenarios (don't use D3, sucks on cycles, dump to a file; file watcher picks up and  refreshes window, keep graph window visible aside of editor, etc).  Maybe I'll revisit this, but for now, for me, ASCII dumps rule.

## Complex schedule
The Click thesis scheduler missed out on anti-dependencies.  C2 - and then the easier to read Simple Chapter 10 corrected this.  The algorithm is fast and simple enough, no more complex than e.g. the natural loop finding version of SCC.  Source code is available from Simple.

### Late arriving control flow from expanding small "diamonds"
Yup, same solution for C2; I kept things like `min` or `max` as single pure nodes.  At some late point in the game they expanded.  For things as trivial as `min` which does NOT need any internal registers - I expanded them in the encoding phase post RA.  For more complex things, I expanded them just after Global Code Motion brought me back to an official CFG.  
In any case, this problem (and its solution) seem... trivial to me.  Doesn't carry much weight on the IR choice argument; each IR has its issues all will need dozens of such "fixes".

## Visitation order.

#### Finding a good visitation order is hard"
Yes, so don't bother.  Use a worklist!  Hard guaranteed linear time to completion, fast and simple.  Back-edges are needed and they are well worth the cost.  I start C2 without them, couldn't get closure with a few passes, added backedges and everything got lots *faster*.  Edge maintenance code deals with the work, so I don't have to think about it, its all cache-resident L1 hits.  Fast, fast, fast.

## Other things?
Dead code elimination: free with backedges; basically when the backedge/ref-count drops to zero, recursively delete nodes. There's maybe 10 lines dedicated to this in Simple.
"Hard to introduce new control flow": Hello compiler writers?  Sorry, can't hardly believe this one; editing a CFG is morally the same as editing the SoN, what's the issue?  In the specific `min` case, the goal is to pick a place in the CFG to expand... which means a place needs to be picked, which for SoN usually means after Global Code Motion when "places" (e.g. classic CFG) is available again.
"Hard to figure out what's inside a loop": Again, Hello compiler writers?  C2 certainly does lots of aggressive loop optimizations - which start by running a standard SCC, finding loop headers, walking the graph area constrained by the loop, producing a loop body for further manipulations... yada yada.  Basically, I build loops and a loop tree via the Olde Fashioned method of running SCC.
"Compiling is slow" - Compiling is fast.  He-said-She-said.  Neen-neener. Data?  Perf discussions with facts?  Apples to apples comparisons?  No?  So how about the "compilation speed vs code quality" didn't meet our goals?
"Cache-unfriendly" - Same (lack of) argument.  Certainly when I did C2 I was highly focused on cache-friendly and based on the vast compile speedup I obtained over all competitions (at that time every AOT compiler, GCC, LLVM) I was larger successful.  C2 compiles take far less footprint (both cache and memory) that my "competitors".  Mostly C2 compiles, despite aggressive inlining, fit easily in L2, nearly always in L1.  Getting this right is important, so I suspect other things happened to make it miss for you. But also... no numbers from me either, so neen-neener again.

## postlog
Although I'll point out most of the above discussion is things that surely made V8's life harder - and slower - and are things I never would have done.  OTOH I've not done a JS compiler, so I have no facts here also.  

Cliff
