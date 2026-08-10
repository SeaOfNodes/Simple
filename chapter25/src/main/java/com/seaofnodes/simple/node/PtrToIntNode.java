package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeMemPtr;

import java.util.BitSet;

/**
 * Reinterpret a non-null managed pointer as its raw integer address.
 *
 * This node changes representation families but emits no code.  In particular,
 * it is not a {@link CheckCastNode}: no control-dependent proof can turn a
 * pointer into an integer.  Array-to-C-pointer conversion adds the array body
 * offset separately.
 */
public class PtrToIntNode extends Node {
    public PtrToIntNode(Node ptr) { super(null,ptr); }
    public PtrToIntNode(PtrToIntNode ptr) { super(ptr); }

    @Override public Tag serialTag() { return Tag.PtrToInt; }
    @Override public String glabel() { return "(i64*)"; }
    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return in(1)._print0(sb.append("(i64*)"),visited);
    }

    @Override public Type compute() {
        return in(1)._type.isHigh() ? TypeInteger.TOP : TypeInteger.BOT;
    }

    @Override public Node idealize() { return null; }

    @Override public Parser.ParseException err() {
        Type t = in(1)._type;
        if( t instanceof TypeMemPtr ptr )
            return ptr.notNull() ? null : Parser.error("Cannot convert a nullable pointer to an integer",null);
        return Parser.error("Cannot convert "+t.str()+" to an integer pointer",null);
    }
}
