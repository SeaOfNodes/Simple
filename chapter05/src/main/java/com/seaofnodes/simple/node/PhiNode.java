package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeBot;

public class PhiNode extends Node {

    public PhiNode(Node... inputs) {
        super(inputs);
    }

    @Override
    public String label() {
        return "Phi";
    }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        sb.append("Phi(");
        for( Node in : _inputs )
            in._print0(sb).append(",");
        sb.setLength(sb.length()-1);
        sb.append(")");
        return sb;
    }

    @Override
    public Type compute() {
        return TypeBot.BOTTOM;
    }

    @Override
    public Node idealize() {
        // Remove a "junk" Phi: Phi(x,x) is just x
        if( in(1)==in(2) )
            return in(1);
        
        return null;
    }
}
