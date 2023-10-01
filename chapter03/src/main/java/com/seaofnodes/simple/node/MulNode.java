package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeBot;
import com.seaofnodes.simple.type.TypeInteger;

public class MulNode extends Node {
    public MulNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
    }
  
    @Override
    public String label() { return "Mul"; }
  
    @Override
    StringBuilder _print(StringBuilder sb) {
        in(1)._print(sb.append("("));
        in(2)._print(sb.append("*"));
        return sb.append(")");
    }
  
    @Override
    public Type compute() {
        if (in(1)._type instanceof TypeInteger i0 &&
                in(2)._type instanceof TypeInteger i1)
            return new TypeInteger(i0._con * i1._con);
        return TypeBot.BOTTOM;
    }

    @Override
    public Node idealize() { return null; }
}
