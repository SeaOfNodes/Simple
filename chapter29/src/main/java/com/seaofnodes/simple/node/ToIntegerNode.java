package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeScalar;

// Truncate toward zero, saturating overflow; NaN converts to zero.
public class ToIntegerNode extends Node {
    public ToIntegerNode(Node lhs) { super(null,lhs); }
    @Override public Tag serialTag() { return Tag.ToInteger; }
    @Override public String label() { return "ToInteger"; }
    @Override protected String format() { return "(int)%1"; }

    @Override public Type compute() {
        Type t = in(1)._type;
        if( t.isHigh() ) return TypeInteger.TOP;
        if( t==Type.BOTTOM || t==TypeScalar.BOT ) return t;
        if( t instanceof TypeFloat tf && tf.isConstant() )
            return TypeInteger.constant((long)tf.value());
        return TypeInteger.BOT;
    }

    @Override public Node idealize() { return null; }
    @Override Node copy(Node lhs, Node rhs) { return new ToIntegerNode(lhs); }
}
