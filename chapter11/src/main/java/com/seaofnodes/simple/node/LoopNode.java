package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.Utils;
import java.util.BitSet;
import java.util.HashSet;

public class LoopNode extends RegionNode {
    public LoopNode( Node entry ) { super(null,entry,null); }

    public CFGNode entry() { return cfg(1); }
    public CFGNode back () { return cfg(2); }

    @Override
    public String label() { return "Loop"; }

    @Override
    public Type compute() {
        if( inProgress() ) return Type.CONTROL;
        return entry()._type;
    }

    @Override
    public Node idealize() {
        return inProgress() ? null : super.idealize();
    }

    // Bypass Region idom, same as the default idom() using use in(1) instead of in(0)
    @Override public int idepth() { return _idepth==0 ? (_idepth=idom().idepth()+1) : _idepth; }
    // Bypass Region idom, same as the default idom() using use in(1) instead of in(0)
    @Override CFGNode idom() { return entry(); }

    @Override int loopDepth() {
        if( _loopDepth!=0 ) return _loopDepth; // Was already set
        _loopDepth = entry()._loopDepth+1;     // Entry depth plus one
        // One-time tag loop exits
        for( CFGNode idom = back(); idom!=this; idom = idom.idom() ) {
            // Walk idom in loop, setting depth
            idom._loopDepth = _loopDepth;
            // Loop exit hits the CProj before the If, instead of jumping from
            // Region directly to If.
            if( idom instanceof CProjNode proj ) {
                assert proj.in(0) instanceof IfNode; // Loop exit test
                // Find the loop exit CProj, and set loop_depth
                for( Node use : proj.in(0)._outputs )
                    if( use instanceof CProjNode proj2 && proj2 != idom )
                        proj2._loopDepth = _loopDepth-1;
            }
        }
        return _loopDepth;
    }

    // If this is an unreachable loop, it may not have an exit.  If it does not
    // (i.e., infinite loop), force an exit to make it reachable.
    void forceExit( StopNode stop ) {
        // Walk the backedge, then immediate dominator tree util we hit this
        // Loop again.  If we ever hit a CProj from an If (as opposed to
        // directly on the If) we found our exit.
        CFGNode x = back();
        while( x != this ) {
            if( x instanceof CProjNode exit )
                return;         // Found an exit, not an infinite loop
            x = x.idom();
        }
        // Found a no-exit loop.  Insert an exit
        NeverNode iff = new NeverNode(back());
        CProjNode t = new CProjNode(iff,0,"True" );
        CProjNode f = new CProjNode(iff,1,"False");
        setDef(2,f);
        stop.addDef(new ReturnNode(t,new ConstantNode(TypeInteger.ZERO).peephole(),null));
        // All Phis are dead also
        for( int i=0; i<nOuts(); i++ )
            if( out(i) instanceof PhiNode phi ) {
                phi.setDef(2,null);
                i--;
            }
    }
}
