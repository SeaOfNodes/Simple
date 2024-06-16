package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

import java.util.BitSet;

public class LoopNode extends RegionNode {

    public LoopNode( Node entry ) { super(null,entry,null); }

    Node entry() { return in(1); }
    Node back () { return in(2); }

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
    @Override Node idom() { return entry(); }
}
