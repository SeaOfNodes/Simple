package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;

// A branch-proven refinement to zero/null or non-zero/non-null.  The value
// family is deliberately taken from the input when it becomes known.
public class GuardNode extends Node {
    public final boolean _nonZero;

    public GuardNode(boolean nonZero, Node ctrl, Node val) {
        super(ctrl,val);
        _nonZero = nonZero;
    }

    public GuardNode(GuardNode guard) {
        super(guard);
        _nonZero = guard._nonZero;
    }

    @Override public Tag serialTag() { return Tag.Guard; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node,Integer> anodes) {
        baos.write(_nonZero ? 1 : 0);
    }
    static Node make(BAOS bais) { return new GuardNode(bais.read()!=0,null,null); }

    @Override public String label() { return _nonZero ? "(!=0)" : "(==0)"; }
    @Override public String uniqueName() { return "Guard_"+_nid; }
    @Override public boolean isPinned() { return true; }

    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return in(1)._print0(sb.append(label()),visited);
    }

    @Override public Type compute() {
        Type t = in(1)._type;
        if( t==Type.BOTTOM || t==Type.TOP ) return t;
        Type t2 = _nonZero ? t.nonZero() : t.makeZero();
        return t2==null ? t : t2; // No sane answer for e.g. not-zero of a zero
    }

    @Override public Node idealize() {
        if( in(1)._type==Type.BOTTOM || in(1)._type==Type.TOP ) return null;
        if( _type.isHighOrConst() && in(1)._type.isa(_type) )
            return in(1);
        return null;
    }

    @Override public boolean eq(Node n) { return _nonZero==((GuardNode)n)._nonZero; }
    @Override int hash() { return _nonZero ? 1 : 0; }
}
