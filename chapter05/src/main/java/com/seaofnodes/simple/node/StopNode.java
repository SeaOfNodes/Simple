package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

public class StopNode extends Node {
    public StopNode(Node... inputs) {
        super(inputs);
    }

    @Override
    public String label() {
        return "Stop";
    }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        if( ret()!=null ) return ret()._print0(sb);
        sb.append("Stop[ ");
        for( Node ret : _inputs )
            ret._print0(sb).append(" ");
        return sb.append("]");
    }

    @Override public boolean isCFG() { return true; }

    // If a single Return, return it.
    // Otherwise, null because ambiguous.
    public ReturnNode ret() {
        return nIns()==1 ? (ReturnNode)in(0) : null;
    }
    
    @Override
    public Type compute() {
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        return null;
    }

    public Node addReturn(Node node) {
        return add_def(node);
    }

}
