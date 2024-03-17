package com.seaofnodes.simple.type;

import java.util.Objects;

public class TypeMemPtr extends Type {

    TypeStruct _structType;
    boolean _maybeNull;

    public static TypeMemPtr NULLPTR = new TypeMemPtr(null).intern();

    public TypeMemPtr(TypeStruct structType) {
        super(TMEMPTR);
        _structType = structType;
        _maybeNull = structType == null;
    }

    public TypeMemPtr(TypeStruct structType, boolean maybeNull) {
        super(TMEMPTR);
        _structType = structType;
        _maybeNull = maybeNull;
    }

    public TypeStruct structType() { return _structType; }

    @Override
    public boolean isNull() { return _structType == null; }

    @Override
    public boolean maybeNull() { return _maybeNull; }

    @Override
    protected Type xmeet(Type t) {
        TypeMemPtr other = (TypeMemPtr) t;
        if (isNull() && !other.isNull()) return new TypeMemPtr(other._structType, true).intern();
        if (!isNull() && other.isNull()) return new TypeMemPtr(_structType, true).intern();
        if (_structType == other._structType) {
            if (other._maybeNull) return other;
            return this;
        }
        else throw new RuntimeException("Unexpected meet of type MemPtr");
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
        sb.append("ptr(" + _structType._name + ")");
        if (maybeNull()) sb.append("|null");
        return sb;
    }
}
