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
            if (i1._con == 0)
                throw new IllegalArgumentException("Divide by zero");
            return new TypeInteger(i0._con / i1._con);
        }
        return TypeBot.BOTTOM;
    }

    @Override
    public Node idealize() { return null; }
}
