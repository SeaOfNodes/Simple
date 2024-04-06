package com.seaofnodes.simple.type;

import com.seaofnodes.simple.Utils;
import java.util.ArrayList;

/**
 * Represents a field in a struct. This is not a Type in the type system.
 */
public class Field extends Type {

    private static int UNIQUE_ALIAS=1;

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
        Field fld = make(sname,type,fname,UNIQUE_ALIAS).intern();
        if( fld._alias == UNIQUE_ALIAS )
            UNIQUE_ALIAS++;
        return fld;
    }

    public static final Field TEST = make("test",TypeInteger.ZERO,"test");
    public static void gather(ArrayList<Type> ts) { ts.add(TEST); }

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
}
