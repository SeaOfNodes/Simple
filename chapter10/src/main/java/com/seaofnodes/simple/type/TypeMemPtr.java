package com.seaofnodes.simple.type;

import java.util.ArrayList;

/**
 * Represents a Ptr. Ptrs are either null, or Ptr to a struct type or a union
 * of the two. Because Simple is a safe language we do not have an untyped Ptr
 * other than null.
 */
public class TypeMemPtr extends Type {
    // A TMP is pair (obj,nil)
    // where obj is one of
    //    (null,TypeStruct).
    // where nil is one of
    //    (true,false) meaning an explicit null is allowed or not

    public final TypeStruct _obj; // null, a TypeStruct, or sentinal TypeStruct.MANY
    public final boolean _nil;

    private TypeMemPtr(TypeStruct obj, boolean nil) {
        super(TMEMPTR);
        _obj = obj;
        _nil = nil;
    }
    public static TypeMemPtr make(TypeStruct obj, boolean nil) { return new TypeMemPtr(obj, nil).intern(); }
    public static TypeMemPtr make(TypeStruct obj) { return TypeMemPtr.make(obj, false); }

    public static TypeMemPtr BOT = make(TypeStruct.BOT,true);
    public static TypeMemPtr TOP = BOT.dual();
    public static TypeMemPtr NULL= make(TypeStruct.TOP,true);
    public static TypeMemPtr VOID= NULL.dual(); // A bottom mix of not-null ptrs
    public static TypeMemPtr TEST= make(TypeStruct.TEST,false);
    public static void gather(ArrayList<Type> ts) { ts.add(NULL); ts.add(BOT); ts.add(TEST); }

    @Override
    Type xmeet(Type t) {
        TypeMemPtr that = (TypeMemPtr) t;
        return TypeMemPtr.make((TypeStruct)_obj.meet(that._obj), _nil | that._nil);
    }

    @Override
    public TypeMemPtr dual() { return TypeMemPtr.make(_obj==null ? null : _obj.dual(), !_nil); }

    @Override
    public Type glb() {
        if( _obj==null ) return BOT;
        return make(_obj.glb(),true);
    }
    @Override public TypeMemPtr makeInit() { return NULL; }

    @Override
    int hash() { return (_obj==null ? 0xDEADBEEF : _obj.hashCode()) ^ (_nil ? 1024 : 0); }

    @Override
    boolean eq(Type t) {
        TypeMemPtr ptr = (TypeMemPtr)t; // Invariant
        return _obj == ptr._obj  && _nil == ptr._nil;
    }

    // [void,name,MANY]*[,?]
    @Override
    public StringBuilder _print(StringBuilder sb) {
        if( this==NULL ) return sb.append("null");
        if( this==VOID ) return sb.append("void*");
        return _obj._print(sb.append("*")).append(_nil ? "?" : "");
    }

    @Override public String str() {
        if( this==NULL ) return "null";
        if( this==VOID ) return "void*";
        return "*"+_obj.str()+(_nil ? "?" : "");
    }

}
