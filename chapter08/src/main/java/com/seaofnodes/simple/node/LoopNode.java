package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

import java.util.BitSet;

public class LoopNode extends RegionNode {

    public LoopNode( Node entry, Node back) { super(null,entry,back); }

    Node entry() { return in(1); }
    Node back () { return in(2); }

    @Override
    public String label() { return "Loop"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append("Loop").append(_nid);
    }
    
    @Override
    public Type compute() {
        return inProgress() ? Type.CONTROL : super.compute();
    }
    
    @Override
    public Node idealize() {
        return inProgress() ? null : super.idealize();
    }

    @Override Node idom() { return entry(); }
    
    @Override public boolean inProgress() { return back()==null; }    
}
