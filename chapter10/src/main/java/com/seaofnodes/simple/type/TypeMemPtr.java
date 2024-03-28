package com.seaofnodes.simple.type;

import java.util.Objects;

public class TypeMemPtr extends Type {

    TypeStruct _structType;
    boolean _maybeNull;

    public static TypeMemPtr NULLPTR = new TypeMemPtr(null).intern();

    private TypeMemPtr(TypeStruct structType) {
        super(TMEMPTR);
        _structType = structType;
        _maybeNull = structType == null;
    }

    private TypeMemPtr(TypeStruct structType, boolean maybeNull) {
        super(TMEMPTR);
        _structType = structType;
        _maybeNull = maybeNull;
    }

    public static TypeMemPtr make(TypeStruct structType) { return new TypeMemPtr(structType).intern(); }
    public static TypeMemPtr make(TypeStruct structType, boolean maybeNull) { return new TypeMemPtr(structType, maybeNull).intern(); }

    public TypeStruct structType() { return _structType; }

    @Override
    public boolean isNull() { return _structType == null; }

    @Override
    public boolean maybeNull() { return _maybeNull; }

    @Override
    protected Type xmeet(Type t) {
        TypeMemPtr other = (TypeMemPtr) t;
        if (isNull() && !other.isNull()) return TypeMemPtr.make(other._structType, true);
        if (!isNull() && other.isNull()) return TypeMemPtr.make(_structType, true);
        if (_structType == other._structType) {
            if (other._maybeNull) return other;
            return this;
        }
        else throw new RuntimeException("Unexpected meet of type MemPtr");
    }

    @Override
    public Type widen() {
        if (!isNull()) return TypeMemPtr.make(_structType, true);
        return this;
    }

    @Override
    int hash() { return Objects.hash(_structType != null ? _structType.hash() : TMEMPTR, _maybeNull); }

    @Override
    boolean eq(Type t) {
        // a ptr is equal to itself
        if (this == t) return true;
        if (t instanceof TypeMemPtr ptr) {
            if (_structType != ptr._structType) return false;
            if (isNull()) return ptr.isNull();
            if (maybeNull()) return ptr.maybeNull();
        }
        return false;
    }

    @Override
    public StringBuilder _print(StringBuilder sb) {
        if (isNull()) return sb.append("null");
        sb.append("Ptr(" + _structType._name + ")");
        if (maybeNull()) sb.append("|null");
        return sb;
    }
}
