package com.seaofnodes.simple.node;

public class LoopScopeNode extends ScopeNode {

    private ScopeNode _head;
    private RegionNode _region;

    public LoopScopeNode(ScopeNode head, RegionNode region) {
        _head = head;
        _region = region;
    }

    @Override
    public Node ctrl() {
        return super.ctrl();
    }

    @Override
    public Node ctrl(Node n) {
        return super.ctrl(n);
    }

    @Override
    public Node update(String name, Node n) {
        if (n == null)
            return define(name, n);
        Node phi = new PhiNode(name, _region, n, _head.lookup(name));
        _head.update(name, phi);
        return super.update(name, phi);
    }

    @Override
    public Node lookup(String name) {
        if (CTRL.equals(name))
            return super.lookup(name);
        return update(name, super.lookup(name));
    }
}
