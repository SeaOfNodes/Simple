package com.seaofnodes.simple.type;

import com.seaofnodes.simple.Utils;
import java.util.ArrayList;

/**
 * Represents a field in a struct. This is not a Type in the type system.
 */
public class Field extends Type {

    /**
     * Represents the starting alias ID - which is 2 because then it nicely
     * slots into Start's projections. Start already uses slots 0-1.
     */
    private static final int _RESET_ALIAS_ID = 2;

    /**
     * Alias ID generator - we start at 2 because START uses 0 and 1 slots,
     * by starting at 2, our alias ID is nicely mapped to a slot in Start.
     */
    private static int _ALIAS_ID = _RESET_ALIAS_ID;

    // The triple {structName,type,fieldName} uniquely identifies a field.

    // Struct name
    public final String _sname;
    // Type of the field
    public final Type _type;
    // Field name
    public final String _fname;

    // Convenience field: Owning TypeStruct
    public TypeStruct _obj;

    // Convenience field: unique alias number
    public final int _alias;

    private Field(String sname, Type type, String fname, int alias) {
        super(TFLD);
        _sname = sname;
        _type  = type;
        _fname = fname;
        _alias = alias;
    }
    // Make with existing alias
    private static Field make( String sname, Type type, String fname, int alias ) {
        return new Field(sname,type,fname,alias).intern();
    }
    // Make with new alias
    public static Field make( String sname, Type type, String fname ) {
        Field fld = make(sname,type,fname,_ALIAS_ID).intern();
        if( fld._alias == _ALIAS_ID )
            _ALIAS_ID++;
        return fld;
    }

    public static final Field TEST = make("test",TypeInteger.ZERO,"test");
    public static void gather(ArrayList<Type> ts) { ts.add(TEST); }

    @Override Field xmeet( Type that ) {
        Field fld = (Field)that; // Invariant
        assert _sname.equals(fld._sname);
        assert _fname.equals(fld._fname);
        return make(_sname,_type.meet(fld._type),_fname);
    }

    @Override
    public Field dual() { return make(_sname,_type.dual(),_fname,_alias); }

    public String aliasName() { return "$"+_alias; };

    @Override
    public Field glb() {
        return make(_sname,_type.glb(),_fname,_alias);
    }

    // Override in subclasses
    int hash() { return _sname.hashCode() ^ _type.hashCode() ^ _fname.hashCode(); }

    boolean eq(Type t) {
        Field f = (Field)t;
        return _sname.equals(f._sname) && _type==f._type && _fname.equals(f._fname);
    }


    @Override
    public StringBuilder _print( StringBuilder sb ) {
        return _type._print(sb.append(_fname).append(":"));
    }

    @Override public String str() { return _fname; }

    static void resetField() {
        _ALIAS_ID = _RESET_ALIAS_ID;
    }
}
