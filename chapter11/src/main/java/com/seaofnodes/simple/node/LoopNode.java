package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.Utils;
import java.util.BitSet;
import java.util.HashSet;

public class LoopNode extends RegionNode {
    public LoopNode( Node entry ) { super(null,entry,null); }

    CFGNode entry() { return cfg(1); }
    CFGNode back () { return cfg(2); }

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
    @Override int idepth() { return _idepth==0 ? (_idepth=idom().idepth()+1) : _idepth; }
    // Bypass Region idom, same as the default idom() using use in(1) instead of in(0)
    @Override CFGNode idom() { return entry(); }

    // Backwards walk setting loop_depth and looking for unreachable code
    @Override int _walkUnreach( HashSet<CFGNode> unreach ) {
        _loop_depth = entry().walkUnreach(unreach) + 1;
        back().walkUnreach(unreach);
        return _loop_depth;
    }

    // If this is an unreachable loop, it may not have an exit.  If it does not
    // (i.e., infinite loop), force an exit to make it reachable.
    void forceExit( StopNode stop ) {
        assert _loop_depth==0;  // Was unreachable
        // Walk the backedge, then immediate dominator tree util we hit this
        // Loop again.  If we ever hit a CProj from an If (as opposed to
        // directly on the If) we found our exit.
        CFGNode x = back();
        while( x != this ) {
            if( x instanceof CProjNode exit ) break;
            x = x.idom();
        }
        // Found a no-exit loop.  Insert an exit
        if( x==this ) {
            NeverNode iff = new NeverNode(back());
            CProjNode t = new CProjNode(iff,0,"True" );
            CProjNode f = new CProjNode(iff,1,"False");
            setDef(2,f);
            stop.addDef(new ReturnNode(t,new ConstantNode(TypeInteger.ZERO),null));
        }
    }
}
