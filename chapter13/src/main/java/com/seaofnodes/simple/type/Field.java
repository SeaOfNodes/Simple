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

    private Field(String fname, Type type ) {
        super(TFLD);
        _fname = fname;
        _type  = type;
    }
    // Make with existing alias
    public static Field make( String fname, Type type ) {
        return new Field(fname,type).intern();
    }

    public static final Field TEST = make("test",TypeInteger.ZERO);
    public static void gather(ArrayList<Type> ts) { ts.add(TEST); }

    @Override Field xmeet( Type that ) {
        Field fld = (Field)that; // Invariant
        assert _fname.equals(fld._fname);
        return make(_fname,_type.meet(fld._type));
    }

    @Override
    public Field dual() { return make(_fname,_type.dual()); }

    @Override
    public Field glb() {
        return make(_fname,_type.glb());
    }

    // Override in subclasses
    int hash() { return _fname.hashCode() ^ _type.hashCode(); }

    boolean eq(Type t) {
        Field f = (Field)t;
        return _fname.equals(f._fname) && _type==f._type;
    }


    @Override
    public StringBuilder _print( StringBuilder sb ) {
        return _type._print(sb.append(_fname).append(":"));
    }

    @Override public String str() { return _fname; }
}
