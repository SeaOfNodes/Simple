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
    // Unique memory alias
    public final int _alias;
    // Type of the field
    public final Type _type;

    private Field(String fname, int alias, Type type ) {
        super(TFLD);
        _fname = fname;
        _alias = alias;
        _type  = type;
    }
    // Make with existing alias
    public static Field make( String fname, int alias, Type type ) {
        return new Field(fname,alias,type).intern();
    }

    public static final Field TEST = make("test",-2,TypeInteger.ZERO);
    public static void gather(ArrayList<Type> ts) { ts.add(TEST); }

    @Override Field xmeet( Type that ) {
        Field fld = (Field)that; // Invariant
        assert _fname.equals(fld._fname);
        assert _alias==fld._alias;
        return make(_fname,_alias,_type.meet(fld._type));
    }

    @Override
    public Field dual() { return make(_fname,_alias,_type.dual()); }

    @Override
    public Field glb() {
        return make(_fname,_alias,_type.glb());
    }

    // Override in subclasses
    int hash() { return _fname.hashCode() ^ _type.hashCode() ^ _alias; }

    boolean eq(Type t) {
        Field f = (Field)t;
        return _fname.equals(f._fname) && _alias==f._alias && _type==f._type;
    }


    @Override
    public StringBuilder print( StringBuilder sb ) {
        return _type.print(sb.append(_fname).append(":").append(_alias).append(" : "));
    }

    @Override public String str() { return _fname; }
}
