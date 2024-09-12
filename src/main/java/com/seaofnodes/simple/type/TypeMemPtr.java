package com.seaofnodes.simple.type;

import java.util.ArrayList;

/**
 * Represents a Pointer to memory.
 *
 * Null is generic pointer to non-existent memory.
 * *void is a non-Null pointer to all possible refs, both structs and arrays.
 * Pointers can be to specific struct and array types, or a union with Null.
 * The distinguished *$BOT ptr represents union of *void and Null.
 * The distinguished *$TOP ptr represents the dual of *$BOT.
 */
public class TypeMemPtr extends Type {
    // A TypeMemPtr is pair (obj,nil)
    // where obj is a TypeStruct, possibly TypeStruct.BOT
    // where nil is an explicit null is allowed or not

    // Examples:
    // (Person,false) - a not-nil Person
    // (Person,true ) - a Person or a nil
    // (BOT   ,false) - a not-nil void* (unspecified struct)
    // (TOP   ,true ) - a nil

    public final TypeStruct _obj;
    public final boolean _nil;

    private TypeMemPtr(TypeStruct obj, boolean nil) {
        super(TMEMPTR);
        assert obj!=null;
        _obj = obj;
        _nil = nil;
    }
    public static TypeMemPtr make(TypeStruct obj, boolean nil) { return new TypeMemPtr(obj, nil).intern(); }
    public static TypeMemPtr make(TypeStruct obj) { return make(obj, false); }
    public TypeMemPtr makeFrom(TypeStruct obj) { return make(obj, _nil); }

    // An abstract pointer, pointing to either a Struct or an Array.
    // Can also be null or not.
    public static TypeMemPtr BOT = make(TypeStruct.BOT,true);
    public static TypeMemPtr TOP = BOT.dual();
    // An abstract null (can be Struct or Array) or not-null C void*
    public static TypeMemPtr NULLPTR = make(TypeStruct.TOP,true);
    public static TypeMemPtr VOIDPTR = NULLPTR.dual(); // A bottom mix of not-null ptrs, like C's void* but not null

    public static TypeMemPtr TEST= make(TypeStruct.TEST,false);
    public static void gather(ArrayList<Type> ts) { ts.add(NULLPTR); ts.add(BOT); ts.add(TEST); }

    @Override
    Type xmeet(Type t) {
        TypeMemPtr that = (TypeMemPtr) t;
        return TypeMemPtr.make((TypeStruct)_obj.meet(that._obj), _nil | that._nil);
    }

    @Override
    public TypeMemPtr dual() { return TypeMemPtr.make(_obj.dual(), !_nil); }

    @Override
    public TypeMemPtr glb() { return make(_obj.glb(),true); }
    @Override public TypeMemPtr makeInit() { return NULLPTR; }
    @Override public Type nonZero() { return VOIDPTR; }

    @Override public boolean isHigh() { return this==TOP; }

    @Override public int log_size() { return 2; } // (1<<2)==4-byte pointers

    @Override
    int hash() { return _obj.hashCode() ^ (_nil ? 1024 : 0); }

    @Override
    boolean eq(Type t) {
        TypeMemPtr ptr = (TypeMemPtr)t; // Invariant
        return _obj == ptr._obj  && _nil == ptr._nil;
    }

    // [void,name,MANY]*[,?]
    @Override
    public StringBuilder print(StringBuilder sb) {
        if( this== NULLPTR) return sb.append("null");
        if( this== VOIDPTR) return sb.append("*void");
        return _obj.print(sb.append("*")).append(_nil ? "?" : "");
    }

    @Override public String str() {
        if( this== NULLPTR) return "null";
        if( this== VOIDPTR) return "*void";
        return "*"+_obj.str()+(_nil ? "?" : "");
    }

}
