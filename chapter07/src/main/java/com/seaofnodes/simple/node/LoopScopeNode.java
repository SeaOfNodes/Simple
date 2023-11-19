package com.seaofnodes.simple.node;

import java.util.HashSet;
import java.util.Set;

/**
 * Special handling for Scope used inside the loop body
 */
public class LoopScopeNode extends ScopeNode {

    /**
     * The scope of the loop before we entered the loop
     */
    private ScopeNode _head;
    /**
     * The loop region
     */
    private RegionNode _region;

    /**
     * Names we see referenced in the loop body that
     * we need to create phis for
     */
    private Set<String> _names = new HashSet<>();

    public LoopScopeNode(ScopeNode head, RegionNode region) {
        _head = head;
        _region = region;
    }

    private void addPhiIfNeeded(String name) {
        if (_names.contains(name))
            return;
        _names.add(name);
        Node body = super.lookup(name);
        Node head = _head.lookup(name);
        assert body != null && head != null;
        // The phi's second value is not set here
        // We update this in finish() method below
        Node phi = new PhiNode(name, _region, head, null);
        _head.update(name, phi);
        super.update(name, phi);
    }

    @Override
    public Node lookup(String name) {
        if (CTRL.equals(name))
            // CTRL is never a phi
            return super.lookup(name);
        addPhiIfNeeded(name);
        return super.lookup(name);
    }

    /**
     * Called once the loop body has been parsed
     */
    public void finish() {
        // For each name we created phi for, add the second input node
        // this is the value that was set in the body of the loop
        for (String name: _names) {
            PhiNode phi = (PhiNode) _head.lookup(name);
            phi.set_def(2, lookup(name));
        }
    }
}
