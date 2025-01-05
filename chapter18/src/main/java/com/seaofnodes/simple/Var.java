package com.seaofnodes.simple;

import com.seaofnodes.simple.node.FRefNode;
import com.seaofnodes.simple.type.*;


/**
 *  The tracked fields are now complex enough to deserve a array-of-structs layout
 */
public class Var {

    public final String _name;   // Declared name
    public int _idx;             // index in containing scope
    private Type _type;          // Declared type
    public boolean _final;       // Final field
    public Parser.Lexer _loc;    // Source location

    public Var(int idx, String name, Type type, boolean xfinal, Parser.Lexer loc) {
        _idx = idx;
        _name = name;
        _type = type;
        _final = xfinal;
        _loc = loc;
    }
    public Type type() {
        if( !_type.isFRef() ) return _type;
        // Update self to no longer use the forward ref type
        Type def = Parser.TYPES.get(((TypeMemPtr)_type)._obj._name);
        return (_type=_type.meet(def));
    }
    public Type lazyGLB() {
        Type t = type();
        return t instanceof TypeMemPtr ? t : t.glb();
    }

    // Forward reference variables (not types) can only be a bottom
    // function pointer.
    public boolean isFRef() { return type()== FRefNode.FREF_TYPE; }

    public boolean defFRef( Type type, boolean xfinal, Parser.Lexer loc ) {
        assert isFRef() && xfinal;
        _type = type;
        _final = true;
        _loc = loc;
        return true;
    }

    @Override public String toString() {
        return _type.toString()+(_final ? " ": " !")+_name;
    }
}
