package com.seaofnodes.simple.type;

import com.seaofnodes.simple.Utils;
import java.util.ArrayList;

/**
 * Represents an array type
 */
public class TypeArray extends Type {

    // An Array has a type and a size; the size is any integer type.
    // The type can be any scalar type, so not e.g. TypeMem.
    public final Type _type;
    public final TypeInteger _len;

    private TypeArray(Type type, TypeInteger len) {
        super(Type.TARRAY);
        _type = type;
        _len = len;
    }

    // All fields directly listed
    public static TypeArray make(Type type, TypeInteger len) { return new TypeArray(type,len).intern(); }

    public static final TypeArray BOT = make(Type.BOTTOM,TypeInteger.BOT);
    public static final TypeArray FLTS = make(TypeFloat.BOT,TypeInteger.U32);

    public static void gather(ArrayList<Type> ts) { ts.add(BOT); ts.add(FLTS); }

    @Override
    Type xmeet(Type t) {
        TypeArray that = (TypeArray) t;
        Type type = _type.meet(that._type);
        TypeInteger len = (TypeInteger)_len.meet(that._len);
        return make(type,len);
    }

    @Override
    public TypeArray dual() {
        return make(_type.dual(),_len.dual());
    }

    // Keeps the same array, but lower-bounds the type not length
    @Override
    public TypeArray glb() {
        return make(_type.glb(),_len);
    }

    @Override
    boolean eq(Type t) {
        TypeArray ts = (TypeArray)t; // Invariant
        return _type==ts._type && _len==ts._len;
    }

    @Override
    int hash() {
        return Utils.fold(Utils.rot(_type.hashCode(),17) ^ _len.hashCode());
    }

    @Override
    public StringBuilder print(StringBuilder sb) {
        _type.print(sb).append("[");
        _len .print(sb).append("]");
        return sb;
    }

    @Override public String str() {
        return _type.str() + "[" + _len.str() + "]";
    }
}
