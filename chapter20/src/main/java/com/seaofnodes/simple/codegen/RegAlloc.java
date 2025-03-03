package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import java.util.Arrays;
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
    public LRG lrg( Node n ) {
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
    }


    public int regnum( Node n ) {
        LRG lrg = lrg(n);
        return lrg==null ? -1 : lrg._reg;
    }

    // Printable register number for node n
    String reg( Node n ) {
        LRG lrg = lrg(n);
        if( lrg==null ) return null;
        if( lrg._reg == -1 ) return "V"+lrg._lrg;
        return _code._mach.reg(lrg._reg);
    }

    // -----------------------
    RegAlloc( CodeGen code ) { _code = code; }

    public void regAlloc() {
        // Insert callee-save registers
        for( CFGNode bb : _code._cfg )
            if( bb instanceof FunNode fun )
                insertCalleeSave(fun);

        // Top driver: repeated rounds of coloring and splitting.
        byte round=0;
        while( !graphColor(round) ) {
            split(round);
            assert round < 7; // Really expect to be done soon
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
            // Color attempt
            IFG.color(round,this);      // If colorable
    }

    // Insert callee-save registers.  Walk the callee-save RegMask ignoring any
    // Parms, then insert a Parm and an edge from the Ret to the Parm with the
    // callee-save register.
    private void insertCalleeSave( FunNode fun ) {
        RegMask saves = _code._mach.calleeSave();
        ReturnNode ret = fun.ret();

        for( short reg = saves.firstReg(); reg != -1; reg = saves.nextReg(reg) ) {
            ret.addDef(new CalleeSaveNode(fun,reg,_code._mach.reg(reg)));
            assert ((MachNode)ret).regmap(ret.nIns()-1).firstReg()==reg;
        }
    }


    // -----------------------
    // Split conflicted live ranges.
    void split(byte round) {

        // In C2, all splits are handling in one pass over the program.  Here,
        // in the name of clarity, we'll handle each failing live range
        // independently... which generally requires a full pass over the
        // program for each failing live range.  i.e., might be a lot of
        // passes.
        for( LRG lrg : _failed.keySet() )
            split(round,lrg);
    }

    // Split this live range
    boolean split( byte round, LRG lrg ) {
        assert lrg.leader();  // Already rolled up

        if( lrg._selfConflicts != null )
            return splitSelfConflict(round,lrg);

        // Register mask when empty; split around defs and uses with limited
        // register masks.
        if( lrg._mask.isEmpty() ) {
            if( lrg._1regDefCnt <= 1 &&
                lrg._1regUseCnt <= 1 &&
                (lrg._1regDefCnt + lrg._1regUseCnt) > 0 )
                return splitEmptyMaskSimple(round,lrg);
            return splitEmptyMask(round,lrg);
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
        if( lrg._1regDefCnt==1 ) {
            if( lrg._machDef.isClone() )
                throw Utils.TODO();
            // Force must-split, even if a prior split same block because register
            // conflicts.  Example:
            //   alloc
            //     V1/rax - forced by alloc
            //   alloc
            //     V2/rax - kills prior RAX
            //   st4 [V1],len - No good, must split around
            _code._mach.split("def/empty1",round,lrg).insertAfter((Node)lrg._machDef, true);
        }
        // Split just before use
        if( lrg._1regUseCnt==1 )
            insertBefore((Node)lrg._machUse,lrg._uidx,"use/empty1",round,lrg);
        return true;
    }

    // Split live range with an empty mask.  Specifically forces splits at
    // single-register defs or uses everywhere.
    boolean splitEmptyMask( byte round, LRG lrg ) {
        boolean all = (lrg._1regDefCnt + lrg._1regUseCnt)==0;
        findAllLRG(lrg);
        for( Node n : _ns ) {
            if( !(n instanceof MachNode mach) ) continue;
            // Find def of spilling live range; spilling everywhere, OR
            // single-register DEF and not cloneable (since these will clone
            // before every use)
            if( lrg(n)==lrg && (all || (!mach.isClone() && mach.outregmap().size1() )) )
                _code._mach.split("def/empty2",round,lrg).insertAfter(n,true);
            // Find all uses
            for( int i=1; i<n.nIns(); i++ ) {
                Node def = n.in(i);
                // Skip any new splits inserted just this pass
                while( def instanceof SplitNode && lrg(def)==null )
                    def = def.in(1);
                // Main def (past splits) is of the spilling lrg, and spilling
                // single-register USE (or everywhere)
                if( lrg(def)==lrg && (all || mach.regmap(i).size1()) )
                    insertBefore(n,i,"use/empty2",round,lrg);
            }
        }
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
            // Split after the Phi which extends the LRG.  Split also before
            // Phi slot 1 (and not all inputs), because Phis extend the live range.
            // TODO: split before all inputs (except the last; at least 1 split here must be extra)
            if( def instanceof PhiNode phi && !(def instanceof ParmNode) ) {
                SplitNode split = _code._mach.split("def/self",round,lrg);
                split.insertAfter(def,false);
                if( split.nOuts()==0 )
                    split.kill();
                insertBefore(phi,1,"use/self/phi",round,lrg);
            }
            // Split before two-address ops which extend the live range
            if( def instanceof MachNode mach && mach.twoAddress()!= 0 )
                insertBefore(def,mach.twoAddress(),"use/self/two",round,lrg);
            // Split before each use that extends the live range; i.e. is a
            // Phi or two-address
            for( int i=0; i<def._outputs._len; i++ ) {
                Node use = def.out(i);
                if( (use instanceof PhiNode phi && !(phi.region() instanceof LoopNode && phi.in(2)==def ) ) ||
                        (use instanceof MachNode mach && mach.twoAddress()!=0 && use.in(mach.twoAddress())==def) )
                    insertBefore( use, use._inputs.find(def), "use/self/use",round,lrg );
            }
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
                        ld = ldepth(ld,n.in(i),n.in(i).cfg0());
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
                    // Single user is already a split
                    !(n.nOuts()==1 && n.out(0) instanceof SplitNode) )
                    // Split after def in min loop nest
                    _code._mach.split("def/loop",round,lrg).insertAfter(n,false);
            }

            // PhiNodes check all CFG inputs
            if( n instanceof PhiNode phi && !(n instanceof ParmNode)) {
                for( int i=1; i<n.nIns(); i++ )
                    // No split in front of a split
                    if( !(n.in(i) instanceof SplitNode) &&
                        // splitting in inner loop or at loop border
                        (min==max || phi.region().cfg(i).loopDepth() <= min) &&
                        // and not around the backedge of a loop (bad place to force a split, hard to remove)
                        !(phi.region() instanceof LoopNode && i==2) )
                        // Split before phi-use in prior block
                        insertBefore(phi,i, "use/loop/phi",round,lrg);

            } else {
                // Others check uses
                for( int i=1; i<n.nIns(); i++ ) {
                    if( lrgSame(n.in(i),lrg) && // This is a LRG use
                        // splitting in inner loop or at loop border
                        (min==max || n.cfg0().loopDepth() <= min) &&
                        // Not a single-use split same block already
                        !(n.in(i) instanceof SplitNode && n.in(i).nOuts()==1 && n.cfg0()==n.in(i).cfg0()) )
                        // Split before in this block
                        insertBefore( n, i, "use/loop/use", round,lrg );
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

    void insertBefore(Node n, int i, String kind, byte round, LRG lrg) {
        // Effective block for use
        Node def = n.in(i);
        CFGNode cfg = n instanceof PhiNode phi ? phi.region().cfg(i) : n.cfg0();
        if( cfg==def.cfg0() && def instanceof SplitNode && def.nOuts()==1 && !(n instanceof MachNode mach && mach.regmap(i).size1()))
            return;
        Node split = (def instanceof MachNode mach && mach.isClone()
                      ? mach.copy()
                      : _code._mach.split(kind,round,lrg));
        split.insertBefore(n, i);
    }



    // -----------------------
    // POST PASS: Remove empty spills that biased-coloring made
    private void postColor() {
        for( CFGNode bb : _code._cfg ) { // For all ops
            for( int j=0; j<bb.nOuts(); j++ ) {
                Node n = bb.out(j);
                if( !(n instanceof SplitNode lo) ) continue;
                int defreg = lrg(n      )._reg;
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
}
