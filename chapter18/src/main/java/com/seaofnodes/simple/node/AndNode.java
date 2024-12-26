package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.BitSet;

public class AndNode extends Node {
    public AndNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "And"; }

    @Override public String glabel() { return "&"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append("&"), visited);
        return sb.append(")");
    }

    @Override
    public Type compute() {
        Type t1 = in(1)._type, t2 = in(2)._type;
        if( t1.isHigh() || t2.isHigh() )
            return TypeInteger.TOP;
        if( t1 instanceof TypeInteger i0 &&
            t2 instanceof TypeInteger i1 ) {
            if( i0.isConstant() && i1.isConstant() )
                return TypeInteger.constant(i0.value()&i1.value());
            // Sharpen allowed bits if either value is narrowed
            long mask = i0.mask() & i1.mask();
            return mask < 0 ? TypeInteger.BOT : TypeInteger.make(0,mask);
        }
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Node rhs = in(2);
        Type t1 = lhs._type;
        Type t2 = rhs._type;

        // And of -1.  We do not check for (-1&x) because this will already
        // canonicalize to (x&-1)
        if( t2.isConstant() && t2 instanceof TypeInteger i && i.value()==-1 )
            return lhs;

        // Move constants to RHS: con*arg becomes arg*con
        if ( t1.isConstant() && !t2.isConstant() )
            return swap12();

        // Do we have ((x & (phi cons)) & con) ?
        // Do we have ((x & (phi cons)) & (phi cons)) ?
        // Push constant up through the phi: x & (phi con0&con0 con1&con1...)
        Node phicon = AddNode.phiCon(this,true);
        if( phicon!=null ) return phicon;

        return null;
    }
    @Override Node copy(Node lhs, Node rhs) { return new AndNode(lhs,rhs); }
    @Override public String err() {
        if( !(in(1)._type instanceof TypeInteger) ) return "Cannot '&' " + in(1)._type;
        if( !(in(2)._type instanceof TypeInteger) ) return "Cannot '&' " + in(2)._type;
        return null;
    }
}
