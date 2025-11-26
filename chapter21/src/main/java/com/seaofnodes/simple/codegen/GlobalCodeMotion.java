package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.IterPeeps.WorkList;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.*;

public abstract class GlobalCodeMotion {

    // Arrange that the existing isCFG() Nodes form a valid CFG.  The
    // Node.use(0) is always a block tail (either IfNode or head of the
    // following block).  There are no unreachable infinite loops.
    public static void buildCFG( CodeGen code ) {
        Ary<CFGNode> rpo = new Ary<>(CFGNode.class);
        _rpo_cfg(null, code._start, code.visit(), rpo);
        // Reverse in-place
        for( int i=0; i< rpo.size()>>1; i++ )
            rpo.swap(i,rpo.size()-1-i);
        // Set global CFG
        code._cfg = rpo;

        schedEarly(code);

        // Break up shared global constants by functions
        breakUpGlobalConstants(code._start);

        code._visit.clear();
        schedLate (code);
    }

    // Post-Order of CFG
    private static void _rpo_cfg(CFGNode def, Node use, BitSet visit, Ary<CFGNode> rpo) {
        if( use instanceof CallNode call ) call.unlink_all();
        if( !(use instanceof CFGNode cfg) || visit.get(cfg._nid) )
            return;             // Been there, done that
        if( def instanceof ReturnNode && use instanceof CallEndNode )
            return;
        assert !( def instanceof CallNode && use instanceof FunNode );
        visit.set(cfg._nid);
        for( Node useuse : cfg._outputs )
            _rpo_cfg(cfg,useuse,visit,rpo);
        rpo.add(cfg);
    }

    // Break up shared global constants by functions
    private static void breakUpGlobalConstants( Node start ) {
        // For all global constants
        for( int i=0; i< start.nOuts(); i++ ) {
            Node con = start.out(i);
            if( con instanceof MachNode mach && mach.isClone() ) {
                breakUpGlobalConstants(con);
                // While constant has users in different functions
                while( true ) {
                    // Find a function user, and another function
                    FunNode fun = null;
                    boolean done=true;
                    for( Node use : con.outs() ) {
                        FunNode fun2 = use instanceof ReturnNode ret ? ret.fun() : use.cfg0().fun();
                        if( fun==null || fun==fun2 ) fun=fun2;
                        else { done=false; break; }
                    }
                    // Single function user, so this constant is not shared
                    if( done ) {
                        if( con.in(0)==start ) i--;
                        con.setDef(0,fun);
                        break;
                    }
                    // Move function users to a private constant
                    Node con2 = mach.copy();  // Private constant clone
                    con2._inputs.set(0,null); // Preserve edge invariants from clone
                    con2.setDef(0,fun);
                    // Move function users to this private constant
                    for( int j=0; j<con._outputs._len; j++ ) {
                        Node use = con.out(j);
                        FunNode fun2 = use.cfg0().fun();
                        if( fun2==fun ) {
                            use.setDef(use._inputs.find(con),con2);
                            j--;
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Visit all nodes in CFG Reverse Post-Order, essentially defs before uses
    // (except at loops).  Since defs are visited first - and hoisted as early
    // as possible, when we come to a use we place it just after its deepest
    // input.
    private static void schedEarly(CodeGen code) {
        // Reverse Post-Order on CFG
        for( CFGNode cfg : code._cfg ) {
            cfg.loopDepth();
            for( Node n : cfg._inputs )
                _schedEarly(n,code );
            if( cfg instanceof RegionNode )
                for( Node phi : cfg._outputs )
                    if( phi instanceof PhiNode )
                        _schedEarly(phi,code );
        }
    }

    private static void _schedEarly(Node n, CodeGen code) {
        if( n==null || code._visit.get(n._nid) ) return; // Been there, done that
        assert !(n instanceof CFGNode);
        code._visit.set(n._nid);
        // Schedule not-pinned not-CFG inputs before self.  Since skipping
        // Pinned, this never walks the backedge of Phis (and thus spins around
        // a data-only loop), eventually attempting relying on some pre-visited-
        // not-post-visited data op with no scheduled control.
        for( Node def : n._inputs )
            if( def!=null && !(def instanceof PhiNode) )
                _schedEarly(def,code);

        // If not-pinned (e.g. constants, projections, phi) and not-CFG
        if( !n.isPinned() ) {
            // Schedule at deepest input
            CFGNode early = code._start; // Maximally early, lowest idepth
            if( n.in(0) instanceof CFGNode cfg ) early = cfg;
            for( int i=1; i<n.nIns(); i++ )
                if( n.in(i)!=null && n.in(i).cfg0().idepth() > early.idepth() )
                    early = n.in(i).cfg0(); // Latest/deepest input
            n.setDef(0,early);
        }
    }

    // ------------------------------------------------------------------------
    private static void schedLate( CodeGen code ) {
        CFGNode[] late = new CFGNode[code.UID()];
        Node[] ns = new Node[code.UID()];
        // Breadth-first scheduling
        breadth(code._stop,ns,late);

        // Copy the best placement choice into the control slot
        for( int i=0; i<late.length; i++ )
            if( ns[i] != null && !(ns[i] instanceof ProjNode) )
                ns[i].setDef(0,late[i]);
    }

    private static void breadth(Node stop, Node[] ns, CFGNode[] late) {
        // Things on the worklist have some (but perhaps not all) uses done.
        WorkList<Node> work = new WorkList<>();
        work.push(stop);
        Node n;
        outer:
        while( (n = work.pop()) != null ) {
            assert late[n._nid]==null; // No double visit
            // These I know the late schedule of, and need to set early for loops
            if( n instanceof CFGNode cfg ) late[n._nid] = cfg.blockHead() ? cfg : cfg.cfg(0);
            else if( n instanceof PhiNode phi ) late[n._nid] = phi.region();
            else if( n instanceof ProjNode && n.in(0) instanceof CFGNode cfg ) late[n._nid] = cfg;
            else {

                // All uses done?
                for( Node use : n._outputs )
                    if( use!=null && late[use._nid]==null )
                        continue outer; // Nope, await all uses done

                // Loads need their memory inputs' uses also done
                if( n instanceof MemOpNode ld && ld._isLoad )
                    for( Node memuse : ld.mem()._outputs )
                        if( late[memuse._nid]==null &&
                            // New makes new memory, never crushes load memory
                            !(memuse instanceof NewNode) &&
                            // Load-use directly defines memory
                            (memuse._type instanceof TypeMem ||
                             // Load-use directly defines memory
                             memuse instanceof CallNode ||
                             // Load-use indirectly defines memory
                             (memuse._type instanceof TypeTuple tt && tt._types[ld._alias] instanceof TypeMem)) )
                            continue outer;

                // All uses done, schedule
                _doSchedLate(n,ns,late);
            }

            // Walk all inputs and put on worklist, as their last-use might now be done
            for( Node def : n._inputs )
                if( def!=null && late[def._nid]==null ) {
                    work.push(def);
                    // if the def has a load use, maybe the load can fire
                    for( Node out : def._outputs )
                        if( out instanceof MemOpNode ld && ld._isLoad && late[ld._nid]==null )
                            work.push(ld);
                }
        }
    }

    private static void _doSchedLate(Node n, Node[] ns, CFGNode[] late) {
        // Walk uses, gathering the LCA (Least Common Ancestor) of uses
        CFGNode early = n.in(0) instanceof CFGNode cfg ? cfg : n.in(0).cfg0();
        assert early != null;
        CFGNode lca = null;
        for( Node use : n._outputs )
            if( use != null )
              lca = use_block(n,use, late)._idom(lca,null);

        // Loads may need anti-dependencies, raising their LCA
        if( n instanceof MemOpNode load && load._isLoad )
            lca = find_anti_dep(lca,load,early,late);


        // Nodes setting a single register and getting killed will stay close
        // to the uses, since they will be forced to spill anyway.  The kill
        // check is very weak, and some may be hoisted only to spill in the RA.
        if( n instanceof MachNode mach ) {
            RegMask out = mach.outregmap();
            if( out!=null && out.size1() ) {
                int reg = mach.outregmap().firstReg();
                // Look for kills
                outer:
                for( CFGNode lca2=lca; lca2 != early; lca2 = lca2.idom() ) {
                    if( lca2 instanceof MachNode mach2 ) {
                        for( int i=1; i<lca2.nIns(); i++ ) {
                            RegMask mask = mach2.regmap(i);
                            if( mask!=null && mask.test(reg) ) {
                                early = lca2 instanceof IfNode ? lca2.idom() : lca2;
                                break outer;
                            }
                        }
                        RegMask kill = mach2.killmap();
                        if( kill != null )
                            throw Utils.TODO();
                    }
                }
            }
        }

        // Walk up from the LCA to the early, looking for best place.  This is
        // the lowest execution frequency, approximated by least loop depth and
        // deepest control flow.
        CFGNode best = lca;
        lca = lca.idom();       // Already found best for starting LCA
        for( ; lca != early.idom(); lca = lca.idom() )
            if( better(lca,best) )
                best = lca;
        assert !(best instanceof IfNode);
        ns  [n._nid] = n;
        late[n._nid] = best;
    }

    // Block of use.  Normally from late[] schedule, except for Phis, which go
    // to the matching Region input.
    private static CFGNode use_block(Node n, Node use, CFGNode[] late) {
        if( !(use instanceof PhiNode phi) )
            return late[use._nid];
        CFGNode found=null;
        for( int i=1; i<phi.nIns(); i++ )
            if( phi.in(i)==n )
                found = phi.region().cfg(i)._idom(found,null);

        assert found!=null;
        return found;
    }


    // Least loop depth first, then largest idepth
    private static boolean better( CFGNode lca, CFGNode best ) {
        return lca.loopDepth() < best.loopDepth() ||
            lca instanceof NeverNode ||
            lca.idepth() > best.idepth() ||
            best instanceof IfNode;
    }

    private static CFGNode find_anti_dep(CFGNode lca, MemOpNode load, CFGNode early, CFGNode[] late) {
        // We could skip final-field loads here.
        // Walk LCA->early, flagging Load's block location choices
        for( CFGNode cfg=lca; early!=null && cfg!=early.idom(); cfg = cfg.idom() )
            cfg._anti = load._nid;
        // Walk load->mem uses, looking for Stores causing an anti-dep
        for( Node mem : load.mem()._outputs ) {
            switch( mem ) {
            case MemOpNode st:
                if( !st._isLoad ) {
                    assert late[mem._nid] != null;
                    lca = anti_dep( load, late[mem._nid], mem.cfg0(), lca, st );
                }
                break; // Loads do not cause anti-deps on other loads
            case CallNode call:
                assert late[call._nid]!=null;
                lca = anti_dep(load,late[call._nid],call.cfg0(),lca,call);
                break;
            case PhiNode phi:
                // Repeat anti-dep for matching Phi inputs.
                // No anti-dep edges but may raise the LCA.
                for( int i=1; i<phi.nIns(); i++ )
                    if( phi.in(i)==load.mem() )
                        lca = anti_dep(load,phi.region().cfg(i),load.mem().cfg0(),lca,null);
                break;
            case NewNode st: break;
            case ReturnNode ret: break; // Load must already be ahead of Return
            case MemMergeNode ret: break; // Mem uses now on ScopeMin
            case NeverNode never: break;
            default: throw Utils.TODO();
            }
        }
        return lca;
    }

    //
    private static CFGNode anti_dep( MemOpNode load, CFGNode stblk, CFGNode defblk, CFGNode lca, Node st ) {
        // Store and Load overlap, need anti-dependence
        if( stblk._anti==load._nid ) {
            lca = stblk._idom(lca,null); // Raise Loads LCA
            if( lca == stblk && st != null && st._inputs.find(load) == -1 ) // And if something moved,
                st.addDef(load); // Add anti-dep as well
            return lca;          // Cap this stores' anti-dep to here
        }
        return lca;
    }

}
