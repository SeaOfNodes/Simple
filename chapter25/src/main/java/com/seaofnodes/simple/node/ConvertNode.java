package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;

import java.util.BitSet;
import java.util.HashMap;

// Convert a value to an authoritative destination type.  The destination is
// known structurally from a declaration; the source family can sharpen later.
public class ConvertNode extends Node {
    private Type _dst;

    public ConvertNode(Type dst, Node val) {
        super(null,val);
        _dst = dst;
    }

    public Type dst() { return _dst; }
    public Node val() { return in(1); }

    @Override public String label() { return "Convert_"+_dst.str(); }
    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return val()._print0(sb.append(label()).append("("),visited).append(")");
    }

    // The declaration fixes the result family even while the source is weak.
    @Override public Type compute() { return _dst; }

    @Override public Node idealize() {
        Type src = val()._type;
        if( src==Type.BOTTOM || src==Type.TOP ) return null;
        if( src.isa(_dst) ) return val();

        // Integer (and nil-as-zero) to floating point.
        if( (src instanceof TypeInteger || src==Type.NIL) && _dst instanceof TypeFloat )
            return new ToFloatNode(val());

        // Narrow integers produce the declared sign/zero extension.
        if( src instanceof TypeInteger && _dst instanceof TypeInteger dst ) {
            if( dst._min==0 )
                return new AndNode(null,val(),con(dst._max));
            int shift = Long.numberOfLeadingZeros(dst._max)-1;
            Node shf = con(shift);
            if( shf._type==TypeInteger.ZERO ) return val();
            return new SarNode(null,new ShlNode(null,val(),shf.keep()).peephole(),shf.unkeep());
        }

        // Narrow f64 to f32.
        if( src instanceof TypeFloat && _dst instanceof TypeFloat )
            return new RoundF32Node(val());

        // Wait for more source information, or report an error after Opto.
        return null;
    }

    @Override boolean _upgradeType(HashMap<String,Type> TYPES) {
        Type dst = _dst.upgradeType(TYPES);
        if( dst==_dst ) return false;
        unlock();
        _dst = dst;
        return true;
    }

    @Override public Parser.ParseException err() {
        return Parser.error("Type "+val()._type.str()+" cannot convert to "+_dst.str(),null);
    }

    @Override public boolean eq(Node n) { return _dst==((ConvertNode)n)._dst; }
    @Override int hash() { return _dst.hashCode(); }
}
