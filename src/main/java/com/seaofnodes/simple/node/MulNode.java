package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.BitSet;

public class MulNode extends Node {
    public MulNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Mul"; }

    @Override public String glabel() { return "*"; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append("*"), visited);
        return sb.append(")");
    }

    @Override
    public Type compute() {
        Type t1 = in(1)._type, t2 = in(2)._type;
        if( t1.isHigh() || t2.isHigh() )
            return TypeInteger.TOP;
        if( t1 instanceof TypeInteger i1 &&
            t2 instanceof TypeInteger i2 ) {
            if( i1==TypeInteger.ZERO || i2==TypeInteger.ZERO)
                return TypeInteger.ZERO;
            if (i1.isConstant() && i2.isConstant())
                return TypeInteger.constant(i1.value()*i2.value());
        }
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Node rhs = in(2);
        Type t1 = lhs._type;
        Type t2 = rhs._type;

        // Move constants to RHS: con*arg becomes arg*con
        if ( t1.isConstant() && !t2.isConstant() )
            return swap12();

        // Multiply by constant
        if ( t2.isConstant() && t2 instanceof TypeInteger i ) {
            // Mul of 1.  We do not check for (1*x) because this will already
            // canonicalize to (x*1)
            long c = i.value();
            if( c==1 )  return lhs;
            if( c==0 )  return Parser.ZERO;
            // Mul by a power of 2, +/-1.  Bit patterns more complex than this
            // are unlikely to win on an X86 vs the normal "imul", and so
            // become machine-specific.
            if( (c & (c-1)) == 0 )
                return new ShlNode(null,lhs,con(Long.numberOfTrailingZeros(c)));
            // 2**n + 1, e.g. 9
            long d = c-1;
            if( (d & (d-1)) == 0 )
                return new AddNode(new ShlNode(null,lhs,con(Long.numberOfTrailingZeros(d))).peephole(),lhs);
            // 2**n - 1, e.g. 31
            long e = c+1;
            if( (e & (e-1)) == 0 )
                return new SubNode(new ShlNode(null,lhs,con(Long.numberOfTrailingZeros(e))).peephole(),lhs);

        }

        // Do we have ((x * (phi cons)) * con) ?
        // Do we have ((x * (phi cons)) * (phi cons)) ?
        // Push constant up through the phi: x * (phi con0*con0 con1*con1...)
        Node phicon = AddNode.phiCon(this,true);
        if( phicon!=null ) return phicon;

        return null;
    }
    @Override Node copy(Node lhs, Node rhs) { return new MulNode(lhs,rhs); }
    @Override Node copyF() { return new MulFNode(null,null); }
    @Override public Parser.ParseException err() {
        if( in(1)._type.isHigh() || in(2)._type.isHigh() ) return null;
        if( !(in(1)._type instanceof TypeInteger) ) return Parser.error("Cannot '"+label()+"' " + in(1)._type.glb(),null);
        if( !(in(2)._type instanceof TypeInteger) ) return Parser.error("Cannot '"+label()+"' " + in(2)._type.glb(),null);
        return null;
    }
}
