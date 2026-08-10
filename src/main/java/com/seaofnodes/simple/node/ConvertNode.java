package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;

import java.util.HashMap;

// Convert the incoming value; actual conversion depends on the _dst type.
// Convert ints to floats, ptrs to ints, nullable to not-null, etc.  Once the
// input type is known precisely enough, the idealize() call triggers and
// replaces the Convert with a concrete conversion (e.g. toFloatNode).
//
// Convert has no runtime dynamic behavior; it must *always* get replaced with
// some concrete behavior (or its an error).
public class ConvertNode extends Node {
    private Type _dst;

    public ConvertNode(Type dst, Node val) {
        super(null,val);
        // Folding this conversion would replace the Return-linked FunPtrNode
        // with an unhooked ConstantNode.
        assert !(dst instanceof TypeFunPtr && val instanceof FunPtrNode);
        _dst = dst;
    }

    public Type dst() { return _dst; }
    public Node val() { return in(1); }

    @Override public String label() { return "Convert_"+_dst.str(); }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p(label()).open().n(val()).close();
    }


    // Not much to do here, if the input type is not sharp enough to fold into
    // the proper conversion, its not sharp enough to do much else.
    @Override public Type compute() {
        Type src = val()._type;
        if( src.isHigh() ) return Type.TOP;
        if( !(_dst instanceof TypeMemPtr) && src.isa(_dst) ) return src;
        return _dst;
    }

    @Override public Node idealize() {
        Type src = val()._type;
        if( src==Type.BOTTOM || src==Type.TOP ) return null;
        if( (src.isa(_dst) && src.accessISA(_dst)) ) return val();

        // Managed arrays are represented by an object pointer, while C wants
        // the address of the first element.  The representation cast itself is
        // zero-code; the Add skips the array header.
        if( _dst==TypeInteger.BOT && src instanceof TypeMemPtr tmp && tmp._obj.isAry() )
            return new AddNode(new PtrToIntNode(val()).peephole(),Parser.off(tmp._obj,"[]"));

        // Integer (and nil-as-zero) to floating point.
        if( (src instanceof TypeInteger || src==Type.NIL) && _dst instanceof TypeFloat )
            return new ToFloatNode(val());

        // Convert to i64 first, then apply the destination's integer width.
        if( src instanceof TypeFloat && _dst instanceof TypeInteger )
            return new ConvertNode(_dst,new ToIntegerNode(val()).peephole());

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
        if( val()._type==Type.BOTTOM ||
            val()._type instanceof TypeNil src && src.nullable() && _dst instanceof TypeNil dst && dst.notNull() ) {
            Node out = out(0);
            if( out instanceof PhiNode phi ) out = phi.out(0);
            if( out instanceof StoreNode st )
                return Parser.error( "'" + ((TypeMemPtr)st.ptr()._type)._obj._name + "' is not fully initialized, field '" + st._name + "' is only partially set in the constructor", null );
        }
        return Parser.error("Type "+val()._type.str()+" is not of declared type "+_dst.str(),null);
    }

    @Override public boolean eq(Node n) { return _dst==((ConvertNode)n)._dst; }
    @Override int hash() { return _dst.hashCode(); }
}
