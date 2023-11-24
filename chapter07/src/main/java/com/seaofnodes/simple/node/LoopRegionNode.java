package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

public class LoopRegionNode extends RegionNode {

    public LoopRegionNode(Node entry, Node back) {
        super(null,entry,back);
    }

    Node entry() { return in(1); }
    Node back () { return in(2); }
    void setBack(Node n) { set_def(2, n); }
  
    @Override
    public Type compute() {
        if (inProgress())
            return Type.CONTROL;
        return super.compute();
    }

    @Override
    public Node idealize() {
        if (inProgress())
            return null;
        return super.idealize();
    }

    public Node finish(Node back) {
        assert inProgress();
        setBack(back);
        return this;
    }

    @Override
    Node idom() { return entry(); }

    @Override
    public boolean inProgress() {
        return back() == null;
    }

    @Override
    public String label() {
        return "Loop";
    }
}
