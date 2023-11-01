package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;

public class AddNode extends Node {
    public AddNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Add"; }
    
    @Override public String glabel() { return "+"; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        in(1)._print0(sb.append("("));
        in(2)._print0(sb.append("+"));
        return sb.append(")");
    }
  

    @Override
    public Type compute() {
        if( in(1)._type instanceof TypeInteger i0 &&
            in(2)._type instanceof TypeInteger i1 ) {
            return TypeInteger.constant(i0.value()+i1.value());
        }
        return TypeBot.BOTTOM;
    }

    @Override
    public Node idealize () {
        // TODO: add of 0
        return null;
    }
        
}
