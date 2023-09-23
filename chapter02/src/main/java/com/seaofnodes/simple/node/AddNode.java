package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.TypeInteger;

public class AddNode extends Node {
    public AddNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
    }

    @Override
    public String label() { return "Add"; }

    @Override
    StringBuilder _print(StringBuilder sb) {
        in(1)._print(sb.append("("));
        in(2)._print(sb.append("+"));
        return sb.append(")");
    }
  

    @Override
    public TypeInteger compute() {
        // Invariant: the inputs to Add are only TypeIntegers.
        
        // Add is only well-defined on TypeInteger, and if the inputs are NOT
        // TypeInteger, then our compiler is broken, and we should crash hard
        // now and debug the issue.
        TypeInteger lhs = (TypeInteger)in(1)._type;
        TypeInteger rhs = (TypeInteger)in(2)._type;
        long lo = lhs._lo + rhs._lo;
        long hi = lhs._hi + rhs._hi;
        return new TypeInteger(lo,hi);
    }

    @Override
    public Node idealize () {
        // TODO: add of 0
        return null;
    }
        
}
