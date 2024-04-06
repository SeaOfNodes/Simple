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
    public boolean _nil;

    private TypeMemPtr(TypeStruct obj, boolean nil) {
        super(TMEMPTR);
        _obj = obj;
        _nil = nil;
    }
    public static TypeMemPtr make(TypeStruct obj, boolean nil) { return new TypeMemPtr(obj, nil).intern(); }
    public static TypeMemPtr make(TypeStruct obj) { return TypeMemPtr.make(obj, false); }

    public static TypeMemPtr BOT = make(TypeStruct.BOT,true);
    public static TypeMemPtr NULL= make(null,true);
    public static TypeMemPtr TEST= make(TypeStruct.TEST,false);
    public static void gather(ArrayList<Type> ts) { ts.add(NULL); ts.add(BOT); ts.add(TEST); }

    @Override
    protected Type xmeet(Type t) {
        TypeMemPtr that = (TypeMemPtr) t;
        return TypeMemPtr.make(xmeet(that._obj), _nil | that._nil);
    }
    // Meet (null,TS,MANY) vs (null,TS,MANY)
    private TypeStruct xmeet(TypeStruct obj) {
        if( _obj == obj ) return  obj; // If same, then same
        if( _obj == null) return  obj; // If either is null, take the other
        if(  obj == null) return _obj;
        return TypeStruct.BOT; // Must be unequal
    }


    @Override
    public Type dual() { return TypeMemPtr.make(dual(_obj), !_nil); }
    private TypeStruct dual(TypeStruct obj) {
        return obj==null ? TypeStruct.BOT : (obj==TypeStruct.BOT ? null : obj);
    }

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
        sb.append("*");
        if( _obj==null ) sb.append("void");
        else _obj._print(sb);
        return sb.append(_nil ? "?" : "");
    }

    @Override public String str() {
        if( this==NULL ) return "null";
        String s = _obj==null ? "void" : _obj._name;
        return "*"+s+(_nil ? "?" : "");
    }

}
