package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;
import java.util.HashMap;

// Upcast (join) the input to a t.  Used after guard test to lift an input.
// Can also be used to make a type-assertion if ctrl is null.
public class CastNode extends Node {
    public Type _t;
    public CastNode(Type t, Node ctrl, Node in) { super(ctrl, in); _t = t; }

    public CastNode(CastNode c) {
        super(c.in(0), c.in(1)); // Call parent constructor
        this._t = c._t;          // Copy the Type field
        setType(compute());      // Ensure type is recomputed
    }
    @Override public Tag serialTag() { return Tag.Cast; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        baos.packed2(types.get(_t)); // NPE if fails lookup
    }
    static Node make( BAOS bais, Type[] types)  { return new CastNode(types[bais.packed2()], null, null); }

    @Override public String label() { return "("+_t.str()+")"; }

    @Override
    public String uniqueName() { return "Cast_" + _nid; }

    @Override public boolean isPinned() { return true; }

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

        // If unconditional, it must collapse (or error)
        if( in(0)==null )
            return _t;
        // If conditional, return the join.  The guard test ensures
        // the join is correct.
        return t1.join(_t);
    }

    @Override
    public Node idealize() {
        return in(1)._type.isa(_t) ? in(1) : null;
    }

    @Override boolean _upgradeType( HashMap<String,Type> TYPES) {
        Type old = _t; _t = _t.upgradeType(TYPES);  return old != _t;
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
        if( in(1)._type != Type.BOTTOM )
            // No condition to test, so this must optimize away
            return Parser.error( "Type " + in(1)._type.str() + " is not of declared type " + _t.str(), null );
        // Only happens for un-initialized refs that must init.
        // Allow uses of initializing stores, but other uses represent a
        // partially initialized field, and should error
        boolean bad = false;
        StoreNode init=null;
        for( Node use : _outputs )
            if( (use instanceof StoreNode st && st._init && !((TypeMemPtr)st.ptr()._type)._obj._name.startsWith("class") ) )
                init = st;
            else bad = true;
        if( !bad ) return null;
        // Expecting an init store
        TypeMemPtr ptr = (TypeMemPtr)init.ptr()._type;
        throw Parser.error("'"+ptr._obj._name+"' is not fully initialized, field '" + init._name + "' is only partially set in the constructor", init._loc);
    }
}
