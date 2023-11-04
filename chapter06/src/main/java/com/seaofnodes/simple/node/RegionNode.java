package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

public class RegionNode extends Node {
    public RegionNode(Node... inputs) { super(inputs); }

    @Override
    public String label() { return "Region"; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        return sb.append("Region").append(_nid);
    }

    @Override public boolean isCFG() { return true; }

    @Override
    public Type compute() {
        Type t = Type.XCONTROL;
        for (int i = 1; i < nIns(); i++)
            if (in(i) != null) t = t.meet(in(i)._type);
        return t;
    }

    @Override
    public Node idealize() {
        return null;
    }

    /**
     * If only 1 of the inputs is live then return it
     */
    public Node single_live_input() {
        Node live = null;
        for( int i=1; i<nIns(); i++ )
            if( !in(i)._type.isDeadCtrl() )
                if (live == null)
                    live = in(i);
                else
                    return null;
        return live;
    }

    public RegionNode keep() {
        addUse(null); // Add a dummy user
        return this;
    }

    public RegionNode unkeep() {
        delUse(null);
        return this;
    }
}
