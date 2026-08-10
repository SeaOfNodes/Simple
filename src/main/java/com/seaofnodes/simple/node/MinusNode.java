package com.seaofnodes.simple.node;


import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;
import java.util.HashMap;
import java.util.IdentityHashMap;

public class MinusNode extends Node implements ModeNode {
    // Mode is 0 for unknown, 1 for int, 2 for flt
    byte _mode;

    public MinusNode(Node in) { super(null, in); }
    public MinusNode(Node in, byte mode) { super(null, in); _mode = mode; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node,Integer> anodes) {
        baos.packed1(_mode);
    }
    @Override public Tag serialTag() { return Tag.Minus; }

    @Override public byte mode() { return _mode; }
    @Override public Node setMode(byte mode) {
        assert _mode==0 && (mode==1 || mode==2);
        unlock();               // _mode participates in GVN hash and equality
        _mode = mode;
        return this;
    }

    @Override protected String format() { return "(-%1)"; }

    @Override
    public Type compute() {
        Type t = in(1)._type;
        // Constant array payloads lie below their element ranges in the lattice.
        if( t instanceof TypeConAry ary ) t = ary.elem();
        if( t==null )
            return Type.TOP;
        if( t.isHigh() )
            return _mode==0 ? Type.TOP :
                _mode==1 ? TypeInteger.TOP :
                t instanceof TypeFloat ? t : TypeFloat.F64.dual();
        if( t instanceof TypeInteger i0 ) {
            if( _mode==2 ) return TypeFloat.F64;
            if( i0._min != Long.MIN_VALUE && i0._max != Long.MIN_VALUE )
                return TypeInteger.make(-i0._max,-i0._min,i0._widen);
            return TypeInteger.BOT;
        }
        if( t instanceof TypeFloat f ) {
            if( _mode==1 ) return TypeInteger.BOT;
            return f.isConstant() ? TypeFloat.constant(-f.value()) : f;
        }
        // No numeric family is known. This must be below both integer and
        // float results, in either analysis direction and with any mode.
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        // -(-x) is x
        if( in(1) instanceof MinusNode minus && addDep(minus.in(1))._type.isa(compute()) )
            return minus.in(1);

        // Can we decide int vs flt?
        if( _mode==0 ) {
            byte mode = (byte)(
                in(1)._type instanceof TypeInteger ? 1 :
                in(1)._type instanceof TypeFloat   ? 2 : 0);
            if( mode!=0 ) return setMode(mode).init();
        }

        return null;
    }
    @Override public Parser.ParseException err() {
        return ArithNode.nodeErr(this,null,"-",_mode,true);
    }
    @Override public boolean eq( Node n ) { return _mode==((MinusNode)n)._mode; }
    @Override public int hash() { return _mode; }
}
