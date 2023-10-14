package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;

public class AddNode extends Node {
    public AddNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
        // Do an initial type computation
        _type = compute();
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
    public Type compute() {
        if( in(1)._type instanceof TypeInteger i0 &&
            in(2)._type instanceof TypeInteger i1 ) {
            if (i0.isConstant() && i1.isConstant())
                return TypeInteger.constant(i0.value()+i1.value());
            return i0.meet(i1);
        }
        return TypeBot.BOTTOM;
    }

    private Node add(Node lhs, Type t1, Type t2) {
        TypeInteger i1 = (TypeInteger)t1;
        TypeInteger i2 = (TypeInteger)t2;
        AddNode newAdd = new AddNode(lhs, new ConstantNode(TypeInteger.constant(i1.value() + i2.value())));
        kill();
        return newAdd;
    }

    @Override
    public Node idealize () {
        Node lhs = in(1);
        Node rhs = in(2);
        Type lhsType = lhs._type;
        Type rhsType = rhs._type;

        // Do we have (con1 + arg) + con2?
        if (rhsType.isConstant() && lhs instanceof AddNode) {
            Node rhs2 = lhs.in(2);
            Type lhs2Type = lhs.in(1)._type;
            Type rhs2Type = rhs2._type;
            if (lhs2Type.isConstant() && !rhs2Type.isConstant()) {
                return add(rhs2, lhs2Type, rhsType);
            }
        }
        // or con1 + (arg + con2)?
        else if (lhsType.isConstant() && rhs instanceof AddNode) {
            Node lhs2 = rhs.in(1);
            Type lhs2Type = lhs2._type;
            Type rhs2Type = rhs.in(2)._type;
            if (!lhs2Type.isConstant() && rhs2Type.isConstant()) {
                return add(lhs2, lhsType, rhs2Type);
            }
        }
        return null;
    }
        
}
