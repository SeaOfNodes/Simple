package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

public class LoopRegionNode extends RegionNode {

    private boolean _inProgress = true;

    public LoopRegionNode(Node... inputs) {
        super(inputs);
    }

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
    Node idom() {
        if (_inProgress)
            return null;
        return super.idom();
    }

    @Override
    public boolean inProgress() {
        return _inProgress;
    }
}
