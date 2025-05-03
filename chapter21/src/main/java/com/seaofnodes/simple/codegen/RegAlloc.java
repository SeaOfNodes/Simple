package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;

/**
  * "Briggs/Chaitin/Click".
  * Graph coloring.
  * <p>
  * Every def and every use have a bit-set of allowed registers.
  * Fully general to bizarre chips.
  * Multiple outputs fully supported, e.g. add-with-carry or combined div/rem or "xor rax,rax" setting both rax and flags.
  * <p>
  * Intel 2-address accumulator-style ops fully supported.
  * Op-to-stack-spill supported.
  * All addressing modes supported.
  * Register pairs could be supported with some grief.
  * <p>
  * Splitting instead of spilling (simpler implementation).
  * Stack slots are "just another register" (tiny stack frames, simpler implementation).
  * Stack/unstack during coloring with a marvelous trick (simpler implementation).
  * <p>
  * Both bitset and adjacency list formats for the interference graph; one of
  * the few times it's faster to change data structures mid-flight rather than
  * just wrap one of the two.
  * <p>
  * Liveness computation and interference graph built in the same one pass (one
  * fewer passes per round of coloring).
  * <p>
  * Single-register def or use live ranges deny neighbors their required
  * register and thus do not interfere, vs interfering and denying the color
  * and coloring time.  Basically all function calls do this, but many oddball
  * registers also, e.g. older div/mod/mul ops. (5x smaller IFG, the only
  * O(n^2) part of this operation).
  */

public class RegAlloc {
    // Main Coloring Algorithm:
    // Repeat until colored:
    //   Build Live Ranges (LRGs)
    //   - Intersect allowed registers
    //   If hard conflicts (LRGs with no allowed registers)
    //   - Pre-split conflicted LRGs, and repeat
    //   Build Interference Graph (IFG)
    //   - Self conflicts split now
    //   Color (remove trivial first then any until empty; reverse assign colors
    //   - If color fails:
    //   - - Split uncolorable LRGs

    // Top-level program graph structure
    final CodeGen _code;

    public int _spills, _spillScaled;

    // -----------------------
    // Live ranges with self-conflicts or no allowed registers
    private final IdentityHashMap<LRG,String> _failed = new IdentityHashMap<>();
    void fail( LRG lrg ) {
        assert lrg.leader();
        _failed.put(lrg,"");
    }
    boolean success() { return _failed.isEmpty(); }


    // -----------------------
    // Map from Nodes to Live Ranges
    private final IdentityHashMap<Node,LRG> _lrgs = new IdentityHashMap<>();
    short _lrg_num;

    // Define a new LRG, and assign n
    LRG newLRG( Node n ) {
        LRG lrg = lrg(n);
        if( lrg!=null ) return lrg;
        lrg = new LRG(_lrg_num++);
        LRG old = _lrgs.put(n,lrg); assert old==null;
        return lrg;
    }

    // LRG for n
    LRG lrg( Node n ) {
        LRG lrg = _lrgs.get(n);
        if( lrg==null ) return null;
        LRG lrg2 = lrg.find();
        if( lrg != lrg2 )
            _lrgs.put(n,lrg2);
        return lrg2;
    }

    // Find LRG for n.in(idx), and also map n to it
    LRG lrg2( Node n, int idx ) {
        LRG lrg = lrg(n.in(idx));
        return union(lrg,n);
    }

    // Union any lrg for n with lrg and map to the union
    LRG union( LRG lrg, Node n ) {
        LRG lrgn = _lrgs.get(n);
        LRG lrg3 = lrg.union(lrgn);
        _lrgs.put(n,lrg3);
        return lrg3;
    }

    // Force all unified to roll up; collect live ranges
    LRG[] _LRGS;
    void unify() {
        Ary<LRG> lrgs = new Ary<>(LRG.class);
        for( Node n : _lrgs.keySet() ) {
            LRG lrg = lrg(n);
            lrgs.setX(lrg._lrg,lrg);
        }
        _LRGS = lrgs.asAry();
        // Remove unified lrgs from failed set also
        for( LRG lrg : _failed.keySet().toArray(new LRG[0]) ) {
            if( !lrg.leader() ) {
                LRG lrg2 = lrg.find();
                _failed.remove(lrg);
                _failed.put(lrg2,"");
            }
        }
    }


    public short regnum( Node n ) {
        LRG lrg = lrg(n);
        return lrg==null ? -1 : lrg._reg;
    }

    // Printable register number for node n
    String reg( Node n ) { return reg(n,null); }
    String reg( Node n, FunNode fun ) {
        LRG lrg = lrg(n);
        if( lrg==null ) return null;
        // No register yet, use LRG
        if( lrg._reg == -1 ) return "V"+lrg._lrg;
        // Chosen machine register unless stack-slot and past RA
        String[] regs = _code._mach.regs();
        if( lrg._reg < regs.length || _code._phase.ordinal() <= CodeGen.Phase.RegAlloc.ordinal() || fun==null )
            return RegMask.reg(regs,lrg._reg);
        // Stack-slot past RA uses the frame layout logic
        return "[rsp+"+fun.computeStackOffset(_code,lrg._reg)+"]";
    }

    // -----------------------
    RegAlloc( CodeGen code ) { _code = code; }

    public void regAlloc() {
        // Insert callee-save registers
        String[] regs = _code._mach.regs();
        long neverSave= _code._mach.neverSave();
        for( CFGNode bb : _code._cfg )
            if( bb instanceof FunNode fun ) {
                ReturnNode ret = fun.ret();
                int len = Math.min(regs.length,64);
                for( int reg=0; reg<len; reg++ )
                    if( !_code._callerSave.test(reg) && ((1L<<reg)&neverSave)==0 ) {
                        ret.addDef(new CalleeSaveNode(fun,reg,regs[reg]));
                        assert ret.regmap(ret.nIns()-1).firstReg()==reg;
                    }
            }
        // Cache reg masks for New and Call
        for( CFGNode bb : _code._cfg ) {
            if( bb instanceof CallEndNode cend ) cend.cacheRegs(_code);
            for( Node n : bb._outputs )
                if( n instanceof NewNode nnn ) nnn.cacheRegs(_code);
        }

        // Top driver: repeated rounds of coloring and splitting.
        byte round=0;
        while( !graphColor(round) ) {
            split(round);
            if( round >= 7 )    // Really expect to be done soon
                throw Utils.TODO("Allocator taking too long");
            round++;
        }
        postColor();                       // Remove no-op spills
    }

    private boolean graphColor(byte round) {
        _failed.clear();
        _lrgs.clear();
        _lrg_num = 1;
        _LRGS=null;

        return
            // Build Live Ranges
            BuildLRG.run(round,this) && // if no hard register conflicts
            // Build Interference Graph
            IFG.build(round,this) &&    // If no self conflicts or uncolorable
            // Conservative coalesce copies
            Coalesce.coalesce(round,this) &&
            // Color attempt
            IFG.color(round,this);      // If colorable
    }

    // -----------------------
    // Split conflicted live ranges.
    void split(byte round) {

        // In C2, all splits are handling in one pass over the program.  Here,
        // in the name of clarity, we'll handle each failing live range
        // independently... which generally requires a full pass over the
        // program for each failing live range.  i.e., might be a lot of
        // passes.

        // Sort, to avoid non-deterministic HashMap ordering
        LRG[] splits = _failed.keySet().toArray(new LRG[0]);
        Arrays.sort(splits, (x,y) -> x._lrg - y._lrg );
        for( LRG lrg : splits )
            split(round,lrg);
    }

    // Split this live range, top level heuristic
    boolean split( byte round, LRG lrg ) {
        assert lrg.leader();  // Already rolled up

        if( lrg._selfConflicts != null )
            return splitSelfConflict(round,lrg);

        // Register mask when empty; split around defs and uses with limited
        // register masks.
        if( lrg._mask.isEmpty() && (!lrg._multiDef || lrg._1regUseCnt==1) ) {
            if( lrg._1regDefCnt <= 1 &&
                lrg._1regUseCnt <= 1 &&
                (lrg._1regDefCnt + lrg._1regUseCnt) > 0 )
                return splitEmptyMaskSimple(round,lrg);
            // Repeated single-reg uses from a single def.  Special for archs
            // with more fixed regs.
            if( !lrg._multiDef && lrg._1regDefCnt <= 1 && lrg._1regUseCnt > 2 )
                if( splitEmptyMaskByUse(round,lrg) )
                    return true;
        }

        // Generic split-by-loop depth.
        return splitByLoop(round,lrg);
    }

    // Split live range with an empty mask.  Specifically forces splits at
    // single-register defs or uses and not elsewhere.
    boolean splitEmptyMaskSimple( byte round, LRG lrg ) {
        // Live range has a single-def single-register, and/or a single-use
        // single-register.  Split after the def and before the use.  Does not
        // require a full pass.

        // Split just after def
        if( lrg._1regDefCnt==1 && !lrg._machDef.isClone() )
            // Force must-split, even if a prior split same block because register
            // conflicts.  Example:
            //   alloc
            //     V1/rax - forced by alloc
            //   alloc
            //     V2/rax - kills prior RAX
            //   st4 [V1],len - No good, must split around
            insertAfterAndReplace( makeSplit("def/empty1",round,lrg), (Node)lrg._machDef, false/*true*/);
        // Split just before use
        if( lrg._1regUseCnt==1 || (lrg._1regDefCnt==1 && ((Node)lrg._machDef).nOuts()==1) )
            insertBefore((Node)lrg._machUse,lrg._uidx,"use/empty1",round,lrg);
        return true;
    }

    // Single-def live range with an empty mask.  There are many single-reg
    // uses.  Theory is there's many repeats of the same reg amongst the uses.
    // In of splitting once per use, start by splitting into groups based on
    // required input register.
    boolean splitEmptyMaskByUse( byte round, LRG lrg ) {
        Node def = (Node)lrg._machDef;

        // Look at each use, and break into non-overlapping register classes.
        Ary<RegMask> rclass = new Ary<>(RegMask.class);
        boolean done=false;
        int ncalls=0;
        while( !done ) {
            done = true;
            for( Node use : def._outputs )
                if( use instanceof MachNode mach ) {
                    if( mach instanceof CallNode ) ncalls++;
                    for( int i=1; i<use.nIns(); i++ )
                        if( use.in(i)==def )
                            done = putIntoRegClass( rclass, mach.regmap(i) );
                }
        }

        // See how many register classes we split into.  Generally not
        // productive to split like this across calls, which are going to kill
        // all registers anyways.
        if( rclass._len <= 1 || ncalls > 1 ) return false;

        // Split by classh
        Ary<Node> ns = new Ary<>(Node.class);
        for( RegMask rmask : rclass ) {
            ns.addAll(def._outputs);
            Node split = makeSplit(def,"popular",round,lrg);
            split.insertAfter( def );
            if( split.nIns()>1 ) split.setDef(1,def);
            // all uses by class to split
            for( Node use : ns ) {
                if( use instanceof MachNode mach && use!=split ) {
                    // Check all use inputs for n, in case there's several
                    for( int i = 1; i < use.nIns(); i++ )
                        // Find a def input, and check register class
                        if( use.in( i ) == def ) {
                            RegMask m = mach.regmap( i );
                            if( m!=null && mach.regmap( i ).overlap( rmask ) )
                                // Modify use to use the split version specialized to this rclass
                                use.setDefOrdered( i, split );
                        }
                }
            }
            ns.clear();
        }
        return true;
    }


    // Put use into a register class, perhaps adding a class or perhaps
    // narrowing a class (and causing a repeat)
    private static boolean putIntoRegClass( Ary<RegMask> rclass, RegMask rmask ) {
        for( int i=0; i<rclass._len; i++ ) {
            RegMask omask = rclass.at(i);
            if( omask.and(rmask) == omask ) return true; // Within the same register class
            if( omask.overlap(rmask) ) {
                rclass.set(i,new RegMask(omask.copy().and(rmask)));
                return false;   // Need go again
            }
        }
        // Add a new class, no need to go again
        rclass.push(rmask);
        return true;
    }

    // Self conflicts require Phis (or two-address).
    // Insert a split after every def.
    boolean splitSelfConflict( byte round, LRG lrg ) {
        // Sort conflict set, so we're deterministic
        Node[] conflicts = lrg._selfConflicts.keySet().toArray(new Node[0]);
        Arrays.sort(conflicts, (x,y) -> x._nid - y._nid );

        // For all conflicts
        for( Node def : conflicts ) {
            assert lrg(def)==lrg; // Might be conflict use-side
            // Split before each use that extends the live range; i.e. is a
            // Phi or two-address
            for( int i=0; i<def._outputs._len; i++ ) {
                Node use = def.out(i);
                if( (use instanceof PhiNode phi &&
                     !(phi.region() instanceof LoopNode loop && phi.in(2)==def && def.cfg0().idepth() > loop.idepth() ) ) ||
                        (use instanceof MachNode mach && mach.twoAddress()!=0 && use.in(mach.twoAddress())==def) )
                    insertBefore( use, use._inputs.find(def), "use/self/use",round,lrg );
            }
            // Split after the Phi which extends the LRG.  Split also before
            // Phi slot 1 (and not all inputs), because Phis extend the live range.
            // TODO: split before all inputs (except the last; at least 1 split here must be extra)
            if( def instanceof PhiNode phi && !(def instanceof ParmNode) ) {
                SplitNode split = makeSplit("def/self",round,lrg);
                insertAfterAndReplace(split,def,false);
                if( split.nOuts()==0 )
                    split.killOrdered();
                insertBefore(phi,1,"use/self/phi",round,lrg);
            }
            // Split before two-address ops which extend the live range
            if( def instanceof MachNode mach && mach.twoAddress()!= 0 )
                insertBefore(def,mach.twoAddress(),"use/self/two",round,lrg);
        }
        return true;
    }


    // Generic: split around the outermost loop with non-split def/uses.  This
    // covers both self-conflicts (once we split deep enough) and register
    // pressure issues.
    boolean splitByLoop( byte round, LRG lrg ) {
        findAllLRG(lrg);

        // Find min loop depth for all non-split defs and uses.
        long ld = (-1L<<32) | 9999;
        for( Node n : _ns ) {
            if( lrg(n)==lrg ) // This is a LRG def
                ld = ldepth(ld,n,n.cfg0());
            // PhiNodes check all CFG inputs
            if( n instanceof PhiNode phi ) {
                for( int i=1; i<n.nIns(); i++ )
                    ld = ldepth(ld, phi.in(i), phi.region().cfg(i));
            } else {
                // Others check uses
                for( int i=1; i<n.nIns(); i++ )
                    if( lrgSame(n.in(i),lrg) ) // This is a LRG use
                        ld = ldepth(ld,n,n.cfg0());
            }
        }
        int min = (int)ld;
        int max = (int)(ld>>32);


        // If the minLoopDepth is less than the maxLoopDepth: for-all defs and
        // uses, if at minLoopDepth or lower, split after def and before use.
        for( Node n : _ns ) {
            if( n instanceof SplitNode ) continue; // Ignoring splits; since spilling need to split in a deeper loop
            if( n.isDead() ) continue; // Some Clonable went dead by other spill changes
            // If this is a 2-address commutable op (e.g. AddX86, MulX86) and the rhs has only a single user,
            // commute the inputs... which chops the LHS live ranges' upper bound to just the RHS.
            if( n instanceof MachNode mach && lrg(n)==lrg && mach.twoAddress()==1 && mach.commutes() && n.in(2).nOuts()==1 )
                n.swap12();

            if( lrg(n)==lrg && // This is a LRG def
                // At loop boundary, or splitting in inner loop
                (min==max || n.cfg0().loopDepth() <= min) ) {
                // Cloneable constants will be cloned at uses, not after def
                if( !(n instanceof MachNode mach && mach.isClone()) &&
                    // Single user is already a split adjacent
                    !(n.nOuts()==1 && n.out(0) instanceof SplitNode split && sameBlockNoClobber(split) ) )
                    // Split after def in min loop nest
                    insertAfterAndReplace( makeSplit("def/loop",round,lrg), n,true);
            }

            // PhiNodes check all CFG inputs
            if( n instanceof PhiNode phi && !(n instanceof ParmNode)) {
                for( int i=1; i<n.nIns(); i++ )
                    // No split in front of a split
                    if( !(n.in(i) instanceof SplitNode) &&
                        // splitting in inner loop or at loop border
                        (min==max || phi.region().cfg(i).loopDepth() <= min) &&
                        // and not around the backedge of a loop (bad place to force a split, hard to remove)
                        !(phi.region() instanceof LoopNode && i==2 && (phi.in(i) instanceof PhiNode pp && pp.region()==phi.region())) )
                        // Split before phi-use in prior block
                        insertBefore(phi,i, "use/loop/phi",round,lrg);

            } else {
                // Others check uses
                for( int i=1; i<n.nIns(); i++ ) {
                    // This is a LRG use
                    // splitting in inner loop or at loop border
                    if( lrgSame( n.in( i ), lrg ) &&
                        (min == max || (n.in(i) instanceof MachNode mach && mach.isClone()) || n.cfg0().loopDepth() <= min) )
                        // Split before in this block
                        insertBefore( n, i, "use/loop/use", round,lrg, false );
                }
            }
        }
        return true;
    }

    private boolean lrgSame(Node x, LRG lrg) {
        LRG xlrg = lrg(x);
        while( xlrg==null && x instanceof SplitNode )
            xlrg=lrg(x = x.in(1));
        return xlrg==lrg;
    }

    private static long ldepth( long ld, Node n, CFGNode cfg ) {
        // Do not count splits
        if( n instanceof SplitNode ) return ld;
        // Collect min/max loop depth
        int min = (int)ld;
        int max = (int)(ld>>32);
        int d = cfg.loopDepth();
        // if n will lower the min loop and is in the tail end of the loop
        // header, splitting "around" the loop will not help.  Treat n as being
        // in the loop.
        if( d < min ) {
            if( cfg.uctrl() instanceof LoopNode loop && loop.entry()==cfg ) {
                for( int i=cfg.nOuts()-2; i>=0; i-- ) {
                    Node out = cfg.out(i);
                    if( n==out )
                        { d = loop.loopDepth(); break; } // Treat n as being "in the loop"
                    if( !((out instanceof MachNode mach && mach.isClone()) || out instanceof SplitNode ) )
                        break;  // Treat b as "normal", out of loop
                }
            }
        }

        // lower min, raise max, and re-fold
        min = Math.min(min,d);
        max = Math.max(max,d);
        return ((long)max<<32) | min;
    }

    // Find all members of a live range, both defs and uses
    private final Ary<Node> _ns = new Ary<>(Node.class);
    void findAllLRG( LRG lrg ) {
        _ns.clear();
        int wd = 0;
        _ns.push((Node)lrg._machDef);
        _ns.push((Node)lrg._machUse);
        while( wd < _ns._len ) {
            Node n = _ns.at(wd++);
            if( lrg(n)!=lrg ) continue;
            for( Node def : n._inputs )
                if( lrg(def)==lrg && _ns.find(def)== -1 )
                    _ns.push(def);
            for( Node use : n._outputs )
                if( _ns.find(use)== -1 )
                    _ns.push(use);
        }
        for( Node n : _ns ) assert !n.isDead();
    }

    void insertBefore(Node n, int i, String kind, byte round, LRG lrg, boolean skip) {
        Node def = n.in(i);
        // Effective block for use
        CFGNode cfg = n instanceof PhiNode phi ? phi.region().cfg(i) : n.cfg0();
        // Def is a split ?
        if( skip && def instanceof SplitNode ) {
            boolean singleReg = n instanceof MachNode mach && mach.regmap(i).size1();
            // Same block, multiple registers, split is only used by n,
            // assume this is good enough and do not split again.
            if( cfg==def.cfg0() && def.nOuts()==1 && !singleReg )
                return;
        }
        makeSplit(def,kind,round,lrg).insertBefore(n, i);
        // Skip split-of-split same block
        if( skip && def instanceof SplitNode && cfg==def.cfg0() )
            n.in(i).setDefOrdered(1,def.in(1));
    }
    void insertBefore(Node n, int i, String kind, byte round, LRG lrg) {
        insertBefore(n,i,kind,round,lrg,true);
    }

    // Replace uses of `def` with `split`, and insert `split` immediately after
    // `def` in the basic block.
    public void insertAfterAndReplace( Node split, Node def, boolean must ) {
        split.insertAfter(def);
        if( split.nIns()>1 ) split.setDef(1,def);
        for( int j=def.nOuts()-1; j>=0; j-- ) {
            Node use = def.out(j);
            if( use==split ) continue; // Skip self
            // Can we avoid a split of a split?  'this' split is used by
            // another split in the same block.
            if( !must && use instanceof SplitNode split2 && sameBlockNoClobber(split2) )
                continue;
            int idx = use._inputs.find(def);
            use.setDefOrdered(idx,split);
            if( j < def.nOuts() ) j++;
        }
    }

    private Node makeSplit( Node def, String kind, byte round, LRG lrg ) {
        Node split = def instanceof MachNode mach && mach.isClone()
            ? mach.copy()
            : _code._mach.split(kind,round,lrg);
        _lrgs.put(split,lrg);
        return split;
    }
    private SplitNode makeSplit( String kind, byte round, LRG lrg ) {
        SplitNode split = _code._mach.split(kind,round,lrg);
        _lrgs.put(split,lrg);
        return split;
    }


    // -----------------------
    // POST PASS: Remove empty spills that biased-coloring made
    private void postColor() {
        int maxReg = -1;
        for( CFGNode bb : _code._cfg ) { // For all ops
            if( bb instanceof FunNode fun )
                maxReg = -1;   // Reset for new function
            // Compute frame size, based on arguments and largest reg seen
            if( bb instanceof ReturnNode ret )
                ret.fun().computeFrameAdjust(_code,maxReg);
            // Raise frame size by max stack args passed, even if ignored
            if( bb instanceof CallEndNode cend )
                maxReg = Math.max(maxReg,cend._xslot);

            for( int j=0; j<bb.nOuts(); j++ ) {
                Node n = bb.out(j);
                if( lrg(n)!=null )
                    maxReg = Math.max(maxReg,lrg(n)._reg+1);
                // Raise frame size by max stack args passed to New
                if( n instanceof NewNode nnn )
                    maxReg = Math.max(maxReg,nnn._xslot);

                if( !(n instanceof SplitNode ) ) continue;
                int defreg = lrg(n     )._reg;
                int usereg = lrg(n.in(1))._reg;
                // Attempt to bypass split
                if( defreg != usereg && splitBypass(bb,j,n,defreg) )
                    usereg = lrg(n.in(1))._reg;

                // Split has same reg?  Useless!  Can remove it!
                if( defreg == usereg ) {
                    n.removeSplit();
                    j--;
                    continue;
                }
                // Split is kept; count against split score
                _spills++;
                _spillScaled += (1<<bb.loopDepth()*3);
                assert _spillScaled >= 0;
            }
        }
    }

    private boolean splitBypass( CFGNode bb, int j, Node lo, int defreg ) {
        // Attempt to bypass split
        Node hi = lo.in(1);
        while( true ) {
            if( !(hi instanceof SplitNode && lo.cfg0() == hi.cfg0()) )
                return false;
            if( lrg(hi.in(1))._reg==defreg )
                break;
            hi = hi.in(1);
        }
        // Check no clobbers
        for( int idx = j-1; bb.out(idx) != hi; idx++) {
            Node n = bb.out(idx);
            if( lrg(n)!=null && lrg(n)._reg == defreg )
                return false;   // Clobbered
        }
        lo.setDefOrdered(1,hi.in(1));
        return true;
    }


    public boolean sameBlockNoClobber( SplitNode split ) {
        Node def = split.in(1);
        CFGNode cfg = def.cfg0();
        if( cfg != split.cfg0() ) return false; // Not same block
        // Get multinode head
        Node def0 = def instanceof ProjNode ? def.in(0) : def;
        int defreg = lrg(def)._reg;
        if( defreg == -1 ) defreg = lrg(def)._mask.firstReg();
        if( defreg == -1 ) return false; // no allowed registers -> clobbered
        for( int idx = cfg._outputs.find(split) -1; idx >= 0; idx-- ) {
            Node n = cfg.out(idx);
            if( n==def0 ) return true;    // No clobbers
            if( lrg(n) == lrg(def) ) return false; // Self conflict
            if( lrg(n)!=null && lrg(n)._reg == defreg )
                return false;   // Clobbered
        }
        throw Utils.TODO();
    }
}
