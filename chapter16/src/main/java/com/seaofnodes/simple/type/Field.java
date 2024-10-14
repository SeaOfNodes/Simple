package com.seaofnodes.simple.type;

import com.seaofnodes.simple.Utils;
import java.util.ArrayList;

/**
 * Represents a field in a struct. This is not a Type in the type system.
 */
public class Field extends Type {

    // The pair {fieldName,type} uniquely identifies a field.

    // Field name
    public final String _fname;
    // Type of the field
    public final Type _type;
    // Unique memory alias, not sensibly part of a "type" but very convenient here.
    public final int _alias;
    // Field must be written to exactly once, no more, no less
    public final boolean _final;

    private Field(String fname, Type type, int alias, boolean xfinal ) {
        super(TFLD);
        _fname = fname;
        _type  = type;
        _alias = alias;
        _final = xfinal;
    }
    // Make with existing alias
    public static Field make( String fname, Type type, int alias, boolean xfinal ) {
        return new Field(fname,type,alias,xfinal).intern();
    }

    public static final Field TEST = make("test",TypeInteger.ZERO,-2,false);
    public static void gather(ArrayList<Type> ts) { ts.add(TEST); }

    @Override Field xmeet( Type that ) {
        Field fld = (Field)that; // Invariant
        assert _fname.equals(fld._fname);
        assert _alias==fld._alias;
        assert _final==fld._final;
        return make(_fname,_type.meet(fld._type),_alias,_final);
    }

    @Override
    public Field dual() { return make(_fname,_type.dual(),_alias,_final); }

    @Override
    public Field glb() {
        return make(_fname,_type.glb(),_alias,_final);
    }

    // Override in subclasses
    int hash() { return _fname.hashCode() ^ _type.hashCode() ^ _alias ^ (_final ? 1024 : 0); }

    boolean eq(Type t) {
        Field f = (Field)t;
        return _fname.equals(f._fname) && _type==f._type && _alias==f._alias && _final==f._final;
    }


    @Override
    public StringBuilder print( StringBuilder sb ) {
        return _type.print(sb.append(_final?"!":"").append(_fname).append(":").append(_alias).append(" : "));
    }

    @Override public String str() { return _fname; }
}
