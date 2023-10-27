package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;

public class AddNode extends Node {
    public AddNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
    }

    @Override
    public String label() { return "Add"; }

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
            if (i0.isConstant() && i1.isConstant())
                return TypeInteger.constant(i0.value()+i1.value());
            return i0.meet(i1);
        }
        return TypeBot.BOTTOM;
    }

    @Override
    public Node idealize () {
        Node lhs = in(1);
        Node rhs = in(2);
        Type t1 = lhs._type;
        Type t2 = rhs._type;

        // Add of 0.  We do not check for (0+x) because this will already
        // canonicalize to (x+0)
        if ( t2.isConstant() && t2 instanceof TypeInteger i && i.value()==0 )
            return lhs;
        
        // Move constants to RHS: con+arg becomes arg+con
        if ( t1.isConstant() && !t2.isConstant() ) {
            Node tmp = in(1);   // Swap inputs without letting either input go dead during the swap
            _inputs.set(1,in(2));
            _inputs.set(2,tmp);
            return this;
        }
        
        // Goal: a left-spine set of adds, with constants on the rhs (which then fold).
        
        // Do we have (x + con1) + con2 ?
        // Rotate to   x +(con1  + con2)
        // The constants will fold on the next peephole.
        if (t2.isConstant() && lhs instanceof AddNode && lhs.in(2)._type.isConstant() )
            return new AddNode(lhs.in(1),new AddNode(lhs.in(2),rhs).peephole());

        // Do we have  x + (y + con) ?
        // Swap to    (x + y) + con
        if (rhs instanceof AddNode add && add.in(2)._type.isConstant() )
          return new AddNode(new AddNode(lhs,add.in(1)).peephole(), add.in(2));

        // Add of same to a multiply by 2
        if( lhs==rhs )
            return new MulNode(lhs,new ConstantNode(TypeInteger.constant(2)).peephole());
        
        return null;
    }
        
}
