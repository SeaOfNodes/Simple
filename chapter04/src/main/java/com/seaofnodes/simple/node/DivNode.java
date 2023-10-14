package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeBot;
import com.seaofnodes.simple.type.TypeInteger;

public class DivNode extends Node {
    public DivNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
    }

    @Override
    public String label() { return "Div"; }
  
    @Override
    StringBuilder _print(StringBuilder sb) {
        in(1)._print(sb.append("("));
        in(2)._print(sb.append("/"));
        return sb.append(")");
    }
  
    @Override
    public Type compute() {
        if (in(1)._type instanceof TypeInteger i0 &&
            in(2)._type instanceof TypeInteger i1) {
            if (i0.isConstant() && i1.isConstant()) {
                if (i1.value() == 0)
                    throw new IllegalArgumentException("Divide by zero");
                return TypeInteger.constant(i0.value()/i1.value());
            }
            return i0.meet(i1);
        }
        return TypeBot.BOTTOM;
    }

    @Override
    public Node idealize() { return null; }
}
