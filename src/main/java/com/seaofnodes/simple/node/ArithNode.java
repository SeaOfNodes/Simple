package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.BitSet;

public abstract class ArithNode extends Node {
    // Source location for late reported errors
    Parser.Lexer _loc;

    public ArithNode( Parser.Lexer loc, Node lhs, Node rhs) { super(null, lhs, rhs); _loc = loc; }
    @Override public String glabel() { return op(); }
    abstract String op();

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append(op()), visited);
        return sb.append(")");
    }

    abstract long doOp(long x, long y);
    abstract TypeInteger doOp(TypeInteger x, TypeInteger y);

    // Generic airthmetic op math: high returns high; low returns low; 2
    // constants fold; only 2 non-constants call specialized math.
    @Override
    public final TypeInteger compute() {
        Type t1 = in(1)._type, t2 = in(2)._type;
        if( t1.isHigh() || t2.isHigh() )
            return TypeInteger.TOP;
        if( t1 instanceof TypeInteger x &&
            t2 instanceof TypeInteger y )
            return x.isConstant() && y.isConstant()
                ? con(x,y)
                : doOp(x,y).makeWide( (byte)Math.max(x._widen,y._widen) );
        return TypeInteger.BOT;
    }

    private TypeInteger con( Type t1, Type t2 ) {
        return TypeInteger.constant( doOp( ((TypeInteger)t1).value(), ((TypeInteger)t2).value()) );
    }

    // Check for "op(Phi(con,x),Phi(con,y))" and push-up through the Phi.
    // Note that this is the exact reverse of Phi pulling a common op down
    // to reduce total op-count.  We don't get in an endless push-up
    // push-down peephole cycle because the constants all fold first.
    // Returns "Phi(op(con,con),op(x,y))".

    // This check is slightly different from the

    // Expected to be called after other ideal checks are done.
    @Override public Node idealize() {
        if( in(1) instanceof PhiNode lhs &&
            in(2) instanceof PhiNode rhs &&
            lhs.nIns() >= 2 && !lhs.inProgress() &&
            lhs.region()==rhs.region() && !(lhs.region() instanceof FunNode) &&
            lhs.nIns()>2 && // A 1-input Phi will collapse already
            // Disallow with self-looping phi; these will collapse
            (lhs.in(2)!=lhs && rhs.in(2)!=rhs) ) {
            // Profit check: only 1 instance of `this` will remain, all the
            // others will fold to constants.
            int cnt=0;
            for( int i=1; i<lhs.nIns(); i++ )
                if( isCon(lhs,rhs,i) )
                    cnt++;
            if( lhs.nIns()-1 - cnt <= 1 ) {
                // Profit!  Push up-phi and fold
                Node[] ns = new Node[lhs.nIns()];
                ns[0] = lhs.in(0);
                for( int i=1; i<lhs.nIns(); i++ ) {
                    ns[i] = isCon(lhs,rhs,i)
                        ? new ConstantNode(con(lhs.in(i)._type, rhs.in(i)._type)).peephole()
                        : copy(lhs.in(i), rhs.in(i)).peephole();
                }
                String label = lhs._label==rhs._label ? lhs._label : lhs._label + rhs._label;
                return new PhiNode(label,lhs._minType,ns).peephole();
            }
        }
        return null;
    }

    static boolean isCon( PhiNode lhs, PhiNode rhs, int i ) {
        return lhs.in(i)._type.isConstant() && rhs.in(i)._type.isConstant();
    }


    static boolean overflow( long x, long y ) {
        if( (x ^    y ) < 0 ) return false; // unequal signs, never overflow
        return (x ^ (x + y)) < 0; // sum has unequal signs, so overflow
    }

    @Override public Parser.ParseException err() {
        if( in(1)._type.isHigh() || in(2)._type.isHigh() ) return null;
        if( !(in(1)._type instanceof TypeInteger) ) return Parser.error("Cannot '"+op()+"' " + in(1)._type,_loc);
        if( !(in(2)._type instanceof TypeInteger) ) return Parser.error("Cannot '"+op()+"' " + in(2)._type,_loc);
        return null;
    }
}
