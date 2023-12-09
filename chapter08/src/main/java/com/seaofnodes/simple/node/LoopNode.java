package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

import java.util.BitSet;

public class LoopNode extends RegionNode {

    public LoopNode( String label, Node entry, Node back) { super(label, null,entry,back); }

    Node entry() { return in(1); }
    Node back () { return in(2); }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(label()).append(_nid);
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
