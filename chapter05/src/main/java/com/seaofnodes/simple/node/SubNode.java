package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeBot;
import com.seaofnodes.simple.type.TypeInteger;

public class SubNode extends Node {
    public SubNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Sub"; }

    @Override public String glabel() { return "-"; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        in(1)._print0(sb.append("("));
        in(2)._print0(sb.append("-"));
        return sb.append(")");
    }
  
    @Override
    public Type compute() {
        if (in(1)._type instanceof TypeInteger i0 &&
            in(2)._type instanceof TypeInteger i1) {
            if (i0.isConstant() && i1.isConstant())
                return TypeInteger.constant(i0.value()-i1.value());
            return i0.meet(i1);
        }
        return TypeBot.BOTTOM;
    }

    @Override
    public Node idealize() {
        // Sub of same is 0
        if( in(1)==in(2) )
            return new ConstantNode(TypeInteger.constant(0));

        return null;
    }

}
