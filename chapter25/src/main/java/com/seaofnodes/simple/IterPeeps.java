package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Opto;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.JSViewer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Random;

/**
 * The IterPeeps runs after parsing. It iterates the peepholes to a fixed point
 * so that no more peepholes apply.  This should be linear because peepholes rarely
 * (never?)  increase code size.  The graph should monotonically reduce in some
 * dimension, which is usually size.  It might also reduce in e.g. number of
 * MulNodes or Load/Store nodes, swapping out more "expensive" Nodes for cheaper
 * ones.
 * <br>
 * The theoretical overall worklist is mindless just grabbing the next thing and
 * doing it.  If the graph changes, put the neighbors on the worklist.
 * <br>
 * Lather, Rinse, Repeat until the worklist runs dry.
 * <p>
 * The main issues we have to deal with:
 *
 * <ul>
 * <li>Nodes have uses; replacing some set of Nodes with another requires more graph
 *   reworking.  Not rocket science, but it can be fiddly.  Its helpful to have a
 *   small set of graph munging utilities, and the strong invariant that the graph
 *   is stable and correct between peepholes.  In our case `Node.subsume` does
 *   most of the munging, building on our prior stable Node utilities.</li>
 *
 * <li>Changing a Node also changes the graph "neighborhood".  The neighbors need to
 *   be checked to see if THEY can also peephole, and so on.  After any peephole
 *   or graph update we put a Nodes uses and defs on the worklist.</li>
 *
 * <li>Our strong invariant is that for all Nodes, either they are on the worklist
 *   OR no peephole applies.  This invariant is easy to check, although expensive.
 *   Basically the normal "iterate peepholes to a fixed point" is linear, and this
 *   check is linear at each peephole step... so quadratic overall.  It's a useful
 *   assert, but one we can disable once the overall algorithm is stable - and
 *   then turn it back on again when some new set of peepholes is misbehaving.
 *   The code for this is turned on in `IterPeeps.iterate` as `assert
 *   progressOnList(stop);`</li>
 * </ul>
 */
public class IterPeeps {

    public final WorkList<Node> _work;
    final WorkList<CallEndNode> _workInline;

    public IterPeeps( long seed ) {
        _work       = new WorkList<>(seed);
        _workInline = new WorkList<>(seed);
    }

    @SuppressWarnings("unchecked")
    public <N extends Node> N add( N n ) { return (N)_work.push(n); }

    public void addAll( Ary<Node> ary ) { _work.addAll(ary); }

    /**
     * Iterate peepholes and inlining to a fixed point
     */
    public void iterate( CodeGen code ) {
        boolean didInline = false;
        Ary<Node> defer = new Ary<>(Node.class);
        while( true ) {
            // Clean up everything that does not grow the code
            iteratePeeps(code);

            // Pick an inline candidate, no real heuristic, first come, first served
            boolean inlined = false;
            CallEndNode cend;
            while( (cend=_workInline.pop()) != null) {
                if( cend.isDead() ) continue;
                byte inline = cend.maybeInline();
                if( inline == -1 ) defer.add(cend);
                if( inline > 0 ) {
                    inlined = true;
                    break;
                }
            }
            // Inlined, run peeps until clean again
            if( inlined ) { didInline = true; continue; }
            // No more candidates, check the defer list
            if( !didInline ) break; // No more progress, so all the "maybe inline after cleanup" do not progress
            // Some inlining happened, retry all the "try again after cleanup" calls
            didInline = false;
            code.addAll(defer);
            defer.clear();
        }
    }

    // Run all the code-reduction and type-lifting peeps as possible
    private void iteratePeeps( CodeGen code ) {
        assert !CodeGen.expensiveAssert(1) || (progressOnList(code, _work) && schedulableUses(code));
        int cnt=0;

        Node n;
        while( (n=_work.pop()) != null ) {
            if( n.isDead() )  continue;
            cnt++;              // Useful for debugging, searching which peephole broke things
            Node x = n.peepholeOpt();
            if( n instanceof CallEndNode cend )
                _workInline.push(cend);
            if( x != null ) {
                assert !x.isDead(); // Peepholes return alive answers
                // peepholeOpt can return brand-new nodes, needing an initial type set
                if( x._type==null ) x.setType(x.compute());
                // Changes require neighbors onto the worklist
                if( x != n || !(x instanceof ConstantNode) ) {
                    // All outputs of n (changing node) not x (prior existing node).
                    for( Node z : n._outputs ) _work.push(z);
                    // Everybody gets a free "go again" in case they didn't get
                    // made in their final form.
                    _work.push(x);
                    // A self-returning peephole can have rewritten its input
                    // edges.  The new defs gained a user and may have
                    // backwards, user-sensitive peepholes of their own.
                    for( Node z : x._inputs ) _work.push(z);
                    // If the result is not self, revisit all inputs (because
                    // there's a new user), and replace in the graph.
                    if( x != n ) {
                        //for( Node z : n. _inputs ) _work.push(z);
                        for( Node z : x._outputs ) _work.push(z);
                        n.subsume(x);
                    }
                }
                // If there are distant neighbors, move to worklist
                n.moveDepsToWorklist();
                JSViewer.show(); // Show again
                // Very expensive assert.
                assert !CodeGen.expensiveAssert(cnt) || (progressOnList(code, _work) && schedulableUses(code));
            }
            if( n.isUnused() ) {
                assert !(n instanceof StopNode); // StopNodes can die if all code in the compunit dies
                n.kill();       // Just plain dead
            }
        }
        assert !CodeGen.expensiveAssert(0) || schedulableUses(code);

    }

    // Visit ALL nodes and confirm the invariant:
    //   Either you are on the _work worklist OR running `iter()` makes no progress.

    // This invariant ensures that no progress is missed, i.e., when the
    // worklist is empty we have indeed done all that can be done.  To help
    // with debugging, the {@code assert} is broken out in a place where it is easy to
    // stop if a change is found.

    // Also, the normal usage of `iter()` may attempt peepholes with distance
    // neighbors and these should fail, but will then try to add dependencies
    // {@link #Node.addDep} which is a side effect in an assert.  The {@link
    // #midAssert} is used to stop this side effect.
    // Pessimistic solver assert
    public static boolean progressOnList(CodeGen code, WorkList<Node> list ) {
        code._midAssert = true;
        Node changed = code._stop.walk( n -> {
            Node m = n;
            Type nval = n.compute();
            // Ignore most in-progress things
            if( n.iskeep() ) return null;

            // Types must be forwards, even if on the worklist.
            assert nval.isa(n._type) : "Non-monotonic peep: "+n+"#"+n._nid+" old="+n._type+" new="+nval+" inputs="+inputTypes(n)+" peep="+m;
            if( list.on(n) )
                return null;    // On worklist is ok!
            if( n instanceof CallEndNode cend ) {
                if( code._iter._workInline.on(cend) )
                    return null; // On inline worklist is ok!
                assert cend.maybeInline() <= 0 : "Inline fired and not on worklist, CallEndNode#"+cend._nid;
            }

            assert n.nOuts() > 0 : "Unused live node: "+n;
            m = n.peepholeOpt();
            assert m==null : "Peep fired and not on worklist, "+n.getClass().getSimpleName()+"#"+n._nid+" -> "+m;
            return m;
        });
        code._midAssert = false;
        return changed==null;
    }

    private static String inputTypes(Node n) {
        StringBuilder sb = new StringBuilder("[");
        for( Node in : n._inputs )
            sb.append(in==null ? "null" : in.getClass().getSimpleName()+"#"+in._nid+"="+in+":"+in._type+":keep="+in.iskeep()).append(',');
        return sb.append(']').toString();
    }

    // GCM assumes every movable node can be placed no earlier than its inputs
    // and no later than the LCA of its uses.  Catch graphs where a use escapes
    // above an input-defined branch before the later scheduling pass walks off
    // the top of the idom tree.
    public static boolean schedulableUses(CodeGen code) {
        IdentityHashMap<Node,CFGNode> earlyCache = new IdentityHashMap<>();
        Node bad = code._stop.walk( n -> {
            if( n.iskeep() || n.isDead() || n.isConst() ||
                n instanceof CFGNode || n instanceof PhiNode || n instanceof ProjNode )
                return null;
            CFGNode early = CFGNode.earlyCFG(n,code._start,earlyCache,new BitSet());
            if( early == null || early == code._start )
                return null;
            CFGNode lca = null;
            for( Node use : n._outputs ) {
                CFGNode ublk = useBlock(n,use);
                if( ublk != null )
                    lca = ublk._idom(lca,null);
            }
            if( lca == null )
                return null;
            if( !early.sameFun(lca) )
                return null;
            assert early.dominates(lca) : badSchedule(n,early,lca);
            return null;
        });
        return bad == null;
    }

    private static CFGNode useBlock(Node n, Node use) {
        if( use == null )
            return null;
        if( use instanceof PhiNode phi ) {
            CFGNode found = null;
            for( int i=1; i<phi.nIns(); i++ )
                if( phi.in(i)==n ) {
                    if( i >= phi.region().nIns() )
                        return null;
                    found = phi.region().cfg(i)._idom(found,null);
                }
            return found;
        }
        return CFGNode.safeCFG(use);
    }

    private static String badSchedule(Node n, CFGNode early, CFGNode lca) {
        StringBuilder sb = new StringBuilder("Unschedulable data node ")
            .append(n.getClass().getSimpleName()).append('#').append(n._nid)
            .append(" early=").append(cfg(early))
            .append(" use-lca=").append(cfg(lca))
            .append(" node=").append(n).append("\ninputs:");
        for( int i=0; i<n.nIns(); i++ ) {
            Node in = n.in(i);
            sb.append("\n  in").append(i).append(": ").append(node(in))
              .append(" cfg=").append(cfg(CFGNode.safeCFG(in)));
        }
        sb.append("\noutputs:");
        for( Node use : n._outputs )
            if( use != null )
                sb.append("\n  use ").append(node(use))
                  .append(" block=").append(cfg(useBlock(n,use)));
        return sb.toString();
    }

    private static String node(Node n) {
        return n==null ? "null" : n.getClass().getSimpleName()+"#"+n._nid+" "+n;
    }

    private static String cfg(CFGNode cfg) {
        return cfg==null ? "null" : cfg.getClass().getSimpleName()+"#"+cfg._nid+" "+cfg;
    }

    /**
     * Classic WorkList, with a fast add/remove, dup removal, random pull.
     * The Node's nid is used to check membership in the worklist.
     */
    @SuppressWarnings("unchecked")
    public static class WorkList<E extends Node> {

        private Node[] _es;
        private int _len;
        private final BitSet _on;   // Bit set if Node._nid is on WorkList
        private final Random _R;    // For randomizing pull from the WorkList
        private final long _seed;

        /* Useful stat - how many nodes are processed in the post parse iterative opt */
        private long _totalWork = 0;

        public WorkList() { this(123); }
        WorkList(long seed) {
            _es = new Node[1];
            _len=0;
            _on = new BitSet();
            _seed = seed;
            _R = new Random();
            _R.setSeed(_seed);
        }

        /**
         * Pushes a Node on the WorkList, ensuring no duplicates
         * If Node is null it will not be added.
         */
        public E push( E x ) {
            if( x==null ) return null;
            int idx = x._nid;
            if( !_on.get(idx) ) {
                _on.set(idx);
                if( _len==_es.length )
                    _es = Arrays.copyOf(_es,_len<<1);
                _es[_len++] = x;
                _totalWork++;
            }
            return x;
        }

        public void addAll( Ary<E> ary ) {
            for( E n : ary )
                push(n);
        }
        public void addAll( E[] es ) {
            for( E n : es )
                push(n);
        }


        /**
         * True if Node is on the WorkList
         */
        public boolean on( E x ) { return _on.get(x._nid); }

        /**
         * Removes a random Node from the WorkList; null if WorkList is empty
         */
        public E pop() {
            if( _len == 0 ) return null;
            int idx = _R.nextInt(_len);
            E x = (E)_es[idx];
            _es[idx] = _es[--_len]; // Compress array
            _on.clear(x._nid);
            return x;
        }

        //boolean isEmpty() { return _len==0; }
        //Node[] asAry() { return Arrays.copyOf(_es,_len); }
        //public void clear() {
        //    _len = 0;
        //    _on.clear();
        //    _R.setSeed(_seed);
        //    _totalWork = 0;
        //}
    }
}
