package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

public class LoopRegionNode extends RegionNode {

    private boolean _inProgress = true;

    public LoopRegionNode(Node entry, Node back) {
        super(null,entry,back);
    }

    Node entry() { return in(1); }
    Node back () { return in(2); }
  
    @Override
    public Type compute() {
        if (_inProgress)
            return Type.CONTROL;
        return super.compute();
    }

    @Override
    public Node idealize() {
        if (_inProgress)
            return null;
        return super.idealize();
    }

    public Node finish() {
        _inProgress = false;
        return this;
    }

    @Override
    Node idom() { return entry(); }

    @Override
    public boolean inProgress() {
        return _inProgress;
    }
}
