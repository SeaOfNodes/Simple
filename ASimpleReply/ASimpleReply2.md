# A Simple Reply (2)

Hi Dadals, we got to stop meeting like this.

The whole reply chain:

https://github.com/SeaOfNodes/Simple/blob/main/ASimpleReply/ASimpleReplyChain.md

I find long, involved, multi-part discussions difficult to follow because
basically we're really having 6 unrelated technical discussions.  I broke up my
replies so each part is easier to consider on its own.


## Reply - "Guard tests for correctness"

Score one for static typing!

Here perhaps there is another win, but not really SoN related as least not
directly.  Every "guardable" aspect in Java - except range checks - falls
neatly into a type *lattice*.  This includes all kinds of subtype checks
(including instanceof, checkcast, user-written, arraystore, etc), null checks,
and constant bounds.  A type *lattice* allows Sparse Conditional Constant
Propagation on all these types, which makes for a linear time, super cheap
analysis which wipes out that 90% of checks.


## Reply - "Graphs are nice for intuition but they don't scale well."

Could be that graph viz tech has improved enough - although I tried hard with
GraphViz on Simple, and other than some pretty demos its not cut out for
serious debugging work.  Meanwhile, ASCII dumps in C2 run into the 100
thousands of lines, are instantly quick, land in my editor buffer where they
are ready to be searched, grep'ed, folded, stapled and mutalilated.  Of course
they appear as a full CFG, with loop structure, typed, backlinks to source code,
etc.  So maybe the answer is: "if you work on your debugging tools long enough,
they work for you".


## Reply - "Use a worklist!"
I think we agree, at least in theory, here.


## Reply - "Data? Perf discussions with facts? Apples to apples comparisons? No?"

Yeah, apples & oranges.  The obvious observation to make about any comparison
is that many benchmarkers have a desired outcome in mind... and will pick their
comparison according.  Turboshaft was compared with the V8 variant SoN.  
I'll betcha when doing comparable work C2 is *very* competitive with
Turboshaft, *especially* on a cache miss basis.  This was *the number one
timing thing* I tracked in during C2's development.  "Back in the day" nearly
all small to medium methods (plus inlines) fit in those processors *L1* cache.


## Reply - Late arriving control flow from expanding small "diamonds"
## Reply - Hard to introduce new control flow

Full optimization of "diamond like things", without being a diamond and
remaining "pure" (no CFG at least), is done generally via the type lattice on
higher level common ops.  They get lowered after there's no hope of other
optimization (provable no-progress by the lattice) - which is generally during
instruction scheduling for C2.

If that's not enough and if you want to introduce control flow - its the same
problem in a CFG as in SoN (which *has* a CFG embedded).  So whatever solution
you have for your CFG approach... SoN can use directly.


## Reply - It's easy in Simple

Yeah, yeah, more opcodes and complexier semantics.  C2 also has a zillion
opcodes, and deopts and re-opts, handles large graphs, multi IR levels, and
sings and dances.... and Simple has all the right compiler pieces to slot into
a runtime as complex as a JVM.  I don't buy the "its easy because its Simple",
its easy because... its *simple*.  Simplicity is a virtue in its own right, and
many hard looking things become, well, *simple* when you start from the right
base.


## Reply - Using multiple effect chains based on equivalence classes

Well, this is the biggest failure I see in the V8 SoN effort and deserves the
most inspection... and has the most room for valid design tradeoffs.  So I'm
saying a little more here, to match your larger writings, but also because
there's just more stuff to chew on.


"It requires typing the whole graph throughout the whole pipeline" - yes.  This
is basically free in Simple/SoN, or at least costs some modest count of cpu
cycles (and generally no cache misses).  Then it sits as a pointer in an IR
node.  There's no bulk copy/bulk read going on, so... it costs nothing to sit
there.  But then, when you need some type info - lo!  There it is!  C2
maintains type info end-to-end, because its free to do so and because its
useful.

"Equivalence classes are much weaker for arrays" - sure.  And basically make no
differece to 99% of all Java programs.  1-d arrays vectorize just fine; stencil
calculations also fine.  Register blocking and tiling?  First Java needs to
change how multi-D arrays are laid out then we can worry about loop carried
aliasing.  Basically, Java was never intended to replace Fortran, although in
the 1-D space it can hold its own.

"Every call to user-defined functions, as well as most builtin calls have
arbitrary side effects" - sure same as Java.  Doesn't really matter in the
grand scheme of things.  If you can inline, the problem goes away.  If not,
well *There's Yer Problem*.  The lack of call aliasing info means you didn't
optimize some things around the call - all dwarfed by the call overhead itself.

"Memory optimizations also cannot typically float past deoptimization state" -
plenty of those in Java too.  C2 also does not sink stores *below* a deopt
state.  At least for Java, we can float loads *up* past a deopt state (and do
so plenty).  

Also, call inline points *completely* disappear in C2.  There is no
optimization barrier after inlining, even for memory effects.  This is
different from *deoptimization* in this regard.

# "Because of polymorphism.... but rather union types".  

Here I honestly don't know.  I can fit some kinds of union types in a type
*lattice*, and then we're back to "basically free types".  On the other hand,
the usefulness of the equivalence class model is based on "since you *cannot*
alias, the other aliases are not even representable".

This gets into a weak argument from me about how after specialization guards,
we're back to equivalence classes... but I honestly dunno.  Not tried it in JS
at least (C2 certainly does this, but its not so common as JS).  
Very curious though...

Curious enough for Google to fund a reprise with me lending a hand?

:-)

Cliff

