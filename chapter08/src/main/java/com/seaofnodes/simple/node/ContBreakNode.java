package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

import java.util.BitSet;

public class ContBreakNode extends RegionNode {

    boolean _inProgress = true;

    public ContBreakNode(String label, Node... inputs) {
        super(label, inputs);
    }

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

    @Override Node idom() { return null; }

    @Override public boolean inProgress() { return _inProgress; }

    public void finish() {
        _inProgress = false;
    }
}
