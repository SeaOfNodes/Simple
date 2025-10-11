package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.BitSet;

public class MulNode extends ArithNode {
    public MulNode(Node lhs, Node rhs) { super(null, lhs, rhs); }
    @Override public Tag serialTag() { return Tag.Mul; }

    @Override public String op() { return "*"; }

    @Override long doOp( long x, long y ) { return x * y; }
    @Override TypeInteger doOp(TypeInteger x, TypeInteger y) {
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

        return super.idealize();
    }
    @Override Node copy(Node lhs, Node rhs) { return new MulNode(lhs,rhs); }
    @Override Node copyF() { return new MulFNode(null,null); }
}
