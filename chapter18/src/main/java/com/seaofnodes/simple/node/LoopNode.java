package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;
import com.seaofnodes.simple.type.TypeInteger;
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
    @Override public CFGNode idom(Node dep) { return entry(); }

    // If this is an unreachable loop, it may not have an exit.  If it does not
    // (i.e., infinite loop), force an exit to make it reachable.
    public StopNode forceExit( FunNode fun, StopNode stop ) {
        // Walk the backedge, then immediate dominator tree util we hit this
        // Loop again.  If we ever hit a CProj from an If (as opposed to
        // directly on the If) we found our exit.
        CFGNode x = back();
        while( x != this ) {
            if( x instanceof CProjNode exit && exit.in(0) instanceof IfNode iff ) {
                CFGNode other = iff.cproj(1-exit._idx);
                if( other.loopDepth() < loopDepth() )
                    return stop; // Found an exit, not an infinite loop
            }
            x = x.idom();
        }
        // Found a no-exit loop.  Insert an exit
        NeverNode iff = new NeverNode(back());
        for( Node use : _outputs )
            if( use instanceof PhiNode )
                iff.addDef(use);
        CProjNode t = new CProjNode(iff,0,"True" );
        CProjNode f = new CProjNode(iff,1,"False");
        setDef(2,f);

        // Now fold control into the exit
        if( fun!=null )
            throw Utils.TODO();

        // If not in a function, look for a last-level merge point;
        // make if not there.
        RegionNode ctrl = stop.ctrl() instanceof RegionNode r ? r : new RegionNode(null,stop.ctrl());
        PhiNode    mem  = stop.mem () instanceof PhiNode phi && phi.in(0)==ctrl ? phi : new PhiNode("",stop.mem ()._type,ctrl,stop.mem ());
        PhiNode    expr = stop.expr() instanceof PhiNode phi && phi.in(0)==ctrl ? phi : new PhiNode("",stop.expr()._type,ctrl,stop.expr());
        if( stop.ctrl()!=ctrl ) stop.setDef(0,ctrl);
        if( stop.mem ()!=mem  ) stop.setDef(1,mem );
        if( stop.expr()!=expr ) stop.setDef(2,expr);
        Node top = new ConstantNode(Type.TOP).peephole();
        ctrl.addDef(t  );
        mem .addDef(top);
        expr.addDef(top);
        return stop;
    }
}
