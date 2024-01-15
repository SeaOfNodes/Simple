# Chapter 9



Unorganized notes...

Goal for Ch 9 is to "finish out" the Peepholes.  Not that no more peeps will
appear, on the contrary lots will - but this is the infrastructure to organize
them, engineer them safe & sane; to apply them until a fixed point is reached.

Really the goal here is to be fearless about adding a new peephole; if you got
it wrong you'll assert pretty fast.  If you got it right, that new peep will
just run alongside all the others and they will all mutually benefit each other.

The overall algorithm is to iterate the peepholes to a fixed point - so no more
peepholes apply.  This should be linear because peepholes rarely (never?)
increase code size.  The graph should monotonically reduce in some dimension,
which is usually size.  It might also reduce in e.g. number of MulNodes or
Load/Store nodes, swapping out more "expensive" Nodes for cheaper ones.


Main issues to deal with:

- Nodes have uses; replacing some set of Nodes with another requires more graph
  reworking.  Not rocket science, but it can be fiddly.  Its helpful to have a
  small set of graph munging utilities, and the strong invariant that the graph
  is stable and correct between peepholes.
  
- Changing a Node also changes the graph "neighborhood".  The neigbors need to
  be checked to see if THEY can also peephole, and so on.  This requires a
  "todo" worklist where updating a Node puts its uses and defs on the list to
  be visited later.
  
- For some peepholes the "neighborhood" is farther away than just the immediate
  uses or defs; for these we need a longer range plan.  E.g. some peephole
  inspects "this -> A -> B -> C" and will swap itself for "D"; however it fails
  some test at "B".  Should "B" ever update we want to revisit "this" and
  recheck his peepholes.  So we record a dependence of "this" in "B".  Updating
  "B" throws the dependent list ("this") onto the "todo" list.
  
- Its easy to miss recording dependencies on remote nodes.  We want the
  invariant that "if you can make progress, then you're on the worklist".  This
  invariant is easy to check, although expensive.  Basically the normal
  "iterate peepholes to a fixed point" is linear, and this check is linear at
  each peephole step... so quadratic.  Its a useful assert, but one we will
  disable once the overall algorithm is stable - and then turn it back on again
  when some new set of peepholes is misbehaving.
  
- Global Value Number fits in here as Yet Another (global) Peephole, sorta like
  swapping a constant-valued Node out for a ConNode.  GVN is a simple hash
  table lookup, where the hash is a function of the opcode (usually Java Node
  subclass) and a Nodes inputs.  E.g. if we have Nodes N3 and N7, then any (Add
  N3 N7) looks like any other (Add N3 N7) and they can be combined.  This fits
  naturally in the normal `peephole` function.
  
- GVN has one major engineering issue: the hash depends on the inputs.  If we
  edit the graph to change a Node inputs, we change its hash - meaning graph
  edits and GVN have a delicate handshake.  E.g. we swap out the Add for a Con
  in `(Mul (Add 4 8) x)` making it `(Mul 12 x)`.  The `Mul` changes hash.  In
  times past I did this with an "edge lock"; graph edits "unlock" a Nodes edges
  first, pulling it from the GVN table while the hash still works.  The Node is
  hacked, and left out of the GVN table - since its likely to be hacked again
  soon!  Instead its put on the "todo" worklist, which will eventually get
  around to re-hashing and re-inserting it into the GVN table.
  
- The theorectical overall worklist is mindless just grabbing the next thing
  and doing it.  This has some drawbacks:
  - Dead & dying stuff might get peepholes done... and then die.  Wasted work.
  - Dead inf loops often lead to infinite peephole cycles... if only we would
    get around to working on the "base" of the dead loop it would fold up.
  - Some peepholes naturally reduce the graph directly; some keeps its size the
    same but reduce other things (e.g. swapping a Mul-by-2 with a Shift), and
    we might end up with a few which try to grow the graph briefly before
    collapsing.
  - All this means is there's some benefit to running peepholes that reduce the
    graph directly (e.g. Dead Code Elim), before running peeps that reduce
    other things, before running other peeps.
  - Also there's a benefit to not always grabbing from either end of the list -
    many peep patterns might go quadratic if approached from one end or
    another, because they modify something then push it back onto the list
    where it immediately gets pulled again.  I.e., you end up spinning in a
    loop repeating the same peeps.  A psuedo-random pull uses randomization to
    defeat bad peep patterns.
    
- We'll probably want to break up the peeps as minimum to:
  - Dead code: same as the recursive kill we're doing now, but with a worklist
    so we can stack up a bunch of things to be killed.  This can be done with
    out a worklist with some care, so open engineering question yet.
  - Pseudo random worklist pull.  Easy cheesy, also we can try different seeds
    and verify we get the same final answer (graph shape).
  - Optional: break up the peeps into those that definitely reduce the graph
    from those that do not.  Always shrink first.

 - New Node field: an "edge lock", just a boolean flag.
 
 - New static: the GVN hash table
 
 - New static: the "to do" worklist.  Might include a dead list, or a reduce vs
   other lists.  Includes a seed for random pull, and a fast test for already
   on the worklist (e.g. BitSet).
 
 - New Algorithm "Iterate" called once after parse.
    
    
    