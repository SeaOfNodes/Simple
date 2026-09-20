 package com.seaofnodes.simple;

import com.seaofnodes.simple.IterPeeps.WorkList;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.*;

public abstract class GlobalCodeMotion {

    // Arrange that the existing isCFG() Nodes form a valid CFG.  The
    // Node.use(0) is always a block tail (either IfNode or head of the
    // following block).  There are no unreachable infinite loops.
    public static void buildCFG( StopNode stop ) {
        schedEarly();
        Parser.SCHEDULED = true;
        schedLate( stop);
    }


    // ------------------------------------------------------------------------
    // Visit all nodes in CFG Reverse Post-Order, essentially defs before uses
    // (except at loops).  Since defs are visited first - and hoisted as early
    // as possible, when we come to a use we place it just after its deepest
    // input.
    private static void schedEarly() {
        ArrayList<CFGNode> rpo = new ArrayList<>();
        BitSet visit = new BitSet();
        _rpo_cfg(Parser.START, visit, rpo);
        // Reverse Post-Order on CFG
        for( int j=rpo.size()-1; j>=0; j-- ) {
            CFGNode cfg = rpo.get(j);
            cfg.loopDepth();
            for( Node n : cfg._inputs )
                _schedEarly(n,visit);
            // In dead infinite loops, entire code blocks may be unreachable
            // from below.  Reach down from the CFG to their Phis so their
            // inputs are scheduled too.
            if( cfg instanceof RegionNode )
                for( Node phi : cfg._outputs )
                    if( phi instanceof PhiNode )
                        _schedEarly(phi,visit);
        }
    }

    // Post-Order of CFG
    private static void _rpo_cfg(Node n, BitSet visit, ArrayList<CFGNode> rpo) {
        if( !(n instanceof CFGNode cfg) || visit.get(cfg._nid) )
            return;             // Been there, done that
        visit.set(cfg._nid);
        for( Node use : cfg._outputs )
            _rpo_cfg(use,visit,rpo);
        rpo.add(cfg);
    }

    private static void _schedEarly(Node n, BitSet visit) {
        if( n==null || visit.get(n._nid) ) return; // Been there, done that
        assert !(n instanceof CFGNode);
        visit.set(n._nid);
        // Schedule inputs first, except Phis: following their backedges would
        // enter a data cycle before its control has been scheduled.
        for( Node def : n._inputs )
            if( def!=null && !(def instanceof PhiNode) )
                _schedEarly(def,visit);
        // An existing edge 0 already supplies control (or a Phi/Proj binding).
        if( n.in(0)==null ) {
            // Schedule at deepest input
            CFGNode early = Parser.START; // Maximally early, lowest idepth
            for( int i=1; i<n.nIns(); i++ )
                if( n.in(i)!=null && n.in(i).cfg0().idepth() > early.idepth() )
                    early = n.in(i).cfg0(); // Latest/deepest input
            n.setDef(0,early);              // First place this can go
        }
    }

    // ------------------------------------------------------------------------
    private static void schedLate( StopNode stop) {
        CFGNode[] late = new CFGNode[Node.UID()];
        Node[] ns = new Node[Node.UID()];
        // Record Load NIDs at all their CFG block choices, then check against
        // Store block choices to force a Load above an anti-dependent Store.
        int[] anti = new int[Node.UID()];
        // Breadth-first scheduling
        breadth(stop,ns,late,anti);

        // Copy the best placement choice into the control slot
        for( int i=0; i<late.length; i++ )
            if( ns[i] != null && !(ns[i] instanceof ProjNode) )
                ns[i].setDef(0,late[i]);
    }

    private static void breadth(Node stop, Node[] ns, CFGNode[] late, int[] anti) {
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
                if( n instanceof LoadNode ld )
                    for( Node memuse : ld.mem()._outputs )
                        if( late[memuse._nid]==null &&
                            // Load-use directly defines memory
                            (memuse._type instanceof TypeMem ||
                             // Load-use indirectly defines memory
                             (memuse._type instanceof TypeTuple tt && tt._types[ld._alias] instanceof TypeMem)) )
                            continue outer;

                // All uses done, schedule
                _doSchedLate(n,ns,late,anti);
            }

            // A use just finished; reconsider its inputs and waiting loads,
            // even when the shared memory input was already scheduled.
            for( Node def : n._inputs ) {
                if( def==null ) continue;
                if( late[def._nid]==null ) work.push(def);
                for( Node out : def._outputs )
                    if( out instanceof LoadNode ld && late[ld._nid]==null )
                        work.push(ld);
            }
            if( n instanceof LoopNode loop )
                for( Node phi : loop._outputs )
                    if( phi instanceof PhiNode && late[phi._nid]==null )
                        work.push(phi);
        }
    }

    private static void _doSchedLate(Node n, Node[] ns, CFGNode[] late, int[] anti) {
        // Walk uses, gathering the LCA (Least Common Ancestor) of uses
        CFGNode early = n.in(0) instanceof CFGNode cfg ? cfg : n.in(0).cfg0();
        assert early != null;
        CFGNode lca = null;
        for( Node use : n._outputs )
            if( use != null )
              lca = use_block(n,use, late).domLCA(lca,null);

        // Loads may need anti-dependencies, raising their LCA
        if( n instanceof LoadNode load )
            lca = find_anti_dep(lca,load,early,late,anti);

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
                found = phi.region().cfg(i).domLCA(found,null); // Can be more than one matching input.
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

    private static CFGNode find_anti_dep(CFGNode lca, LoadNode load, CFGNode early, CFGNode[] late, int[] anti) {
        // We could skip final-field loads here.
        // Walk LCA->early, flagging Load's block location choices
        for( CFGNode cfg=lca; early!=null && cfg!=early.idom(); cfg = cfg.idom() )
            anti[cfg._nid] = load._nid;
        // Walk load->mem uses, looking for Stores causing an anti-dep
        for( Node mem : load.mem()._outputs ) {
            switch( mem ) {
            case StoreNode st:
                assert late[st._nid]!=null;
                lca = anti_dep(load,late[st._nid],st.cfg0(),lca,st,anti);
                break;
            case NewNode st:
                assert late[st._nid]!=null;
                lca = anti_dep(load,late[st._nid],st.cfg0(),lca,st,anti);
                break;
            case PhiNode phi:
                // Repeat anti-dep for matching Phi inputs.
                // No anti-dep edges but may raise the LCA.
                for( int i=1; i<phi.nIns(); i++ )
                    if( phi.in(i)==load.mem() )
                        lca = anti_dep(load,phi.region().cfg(i),load.mem().cfg0(),lca,null,anti);
                break;
            case LoadNode ld: break; // Loads do not cause anti-deps on other loads
            case ReturnNode ret: break; // Load must already be ahead of Return
            case ScopeMinNode ret: break; // Mem uses now on ScopeMin
            case NeverNode never: break;
            default: throw Utils.TODO();
            }
        }
        return lca;
    }

    //
    private static CFGNode anti_dep( LoadNode load, CFGNode stblk, CFGNode defblk, CFGNode lca, Node st, int[] anti ) {
        // Preserve the full store range for the earlier evaluator scheduler.
        // It places nodes independently of GCM and may hoist this store.
        for( ; stblk != defblk.idom(); stblk = stblk.idom() ) {
            // Store and Load overlap, need anti-dependence
            if( anti[stblk._nid]==load._nid ) {
                lca = stblk.domLCA(lca,null); // Raise Loads LCA
                if( lca == stblk && st != null && st._inputs.find(load) == -1 ) // And if something moved,
                    st.addDef(load);   // Add anti-dep as well
                return lca;            // Cap this stores' anti-dep to here
            }
        }
        return lca;
    }

}
