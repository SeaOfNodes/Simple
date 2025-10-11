package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeMemPtr;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;

// Upcast (join) the input to a t.  Used after guard test to lift an input.
// Can also be used to make a type-assertion if ctrl is null.
public class CastNode extends Node {
    public Type _t;
    public CastNode(Type t, Node ctrl, Node in) {
        super(ctrl, in);
        _t = t;
        setType(compute());
    }

    public CastNode(CastNode c) {
        super(c.in(0), c.in(1)); // Call parent constructor
        this._t = c._t;          // Copy the Type field
        setType(compute());      // Ensure type is recomputed
    }

    @Override public String label() { return "("+_t.str()+")"; }

    @Override
    public String uniqueName() { return "Cast_" + _nid; }

    @Override public boolean isPinned() { return true; }
    @Override public boolean isConst() { return true; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return in(1)._print0(sb.append(label()), visited);
    }

    @Override
    public Type compute() {
        // Cast array to int
        Type t1 = in(1)._type;
        if( _t == TypeInteger.BOT && t1 instanceof TypeMemPtr tmp && tmp._obj.isAry() )
            return _t;
        // Freeze if the join is high but both inputs are low
        Type tj = t1.join(_t);
        if( tj.isHigh() && !t1.isHigh() )
            return _type==null ? _t : _type;

        return tj;
    }

    @Override
    public Node idealize() {
        return in(1)._type.isa(_t) ? in(1) : null;
    }

    @Override
    public boolean eq(Node n) {
        CastNode cast = (CastNode)n; // Contract
        return _t==cast._t;
    }

    @Override
    int hash() { return _t.hashCode(); }

    @Override
    public Parser.ParseException err() {
        // Has a condition to test, so OK
        if( in(0) != null ) return null;
        // No condition to test, so this must optimize away
        return Parser.error( "Type " + in(1)._type.str() + " is not of declared type " + _t.str(), null );
    }
}
