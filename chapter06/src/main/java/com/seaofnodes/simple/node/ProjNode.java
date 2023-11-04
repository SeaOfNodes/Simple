package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeTuple;

public class ProjNode extends Node {

    // Which slice of the incoming multi-part value
    public final int _idx;

    // Debugging label
    public final String _label;
    
    public ProjNode(MultiNode ctrl, int idx, String label) {
        super(ctrl);
        _idx = idx;
        _label = label;
    }

    @Override
    public String label() { return _label; }

    @Override
    StringBuilder _print1(StringBuilder sb) { return sb.append(_label); }

    @Override public boolean isCFG() { return _idx==0 || ctrl() instanceof IfNode; }

    public MultiNode ctrl() { return (MultiNode)in(0); }

    @Override
    public Type compute() {
        Type t = ctrl()._type;
        return getType(t);
    }

    private Type getType(Type t) {
        return t instanceof TypeTuple tt ? tt._types[_idx] : Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        if (getType(ctrl()._type).isDeadCtrl()) return ConstantNode.DEAD_CTRL;
        if (ctrl() instanceof IfNode n) {
            if (n._type instanceof TypeTuple tt) {
                // Get the other Proj node type
                Type t = tt._types[_idx==0?1:0];
                // If the other one is dead, then If has only 1 branch
                if (t.isDeadCtrl()) {
                    // Other value is dead, so return the control's parent control
                    // Since parent is IfNode, we really need its parent
                    return ctrl().in(0).in(0);
                }
            }
        }
        return null;
    }
}
