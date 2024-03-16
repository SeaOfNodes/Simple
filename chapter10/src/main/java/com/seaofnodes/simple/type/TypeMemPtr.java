package com.seaofnodes.simple.type;

public class TypeMemPtr extends Type {

    TypeStruct _structType;

    public TypeMemPtr(TypeStruct structType) {
        super(TMEMPTR);
        _structType = structType;
    }

    public TypeStruct structType() { return _structType; }

    @Override
    public boolean isNull() { return _structType == null; }

    @Override
    int hash() { return _structType != null ? _structType.hash() : TMEMPTR; }

    @Override
    boolean eq(Type t) {
        // a ptr is equal to itself
        if (this == t) return true;
        if (isNull() && t instanceof TypeMemPtr ptr)
            return ptr.isNull();
        return false;
    }

    @Override
    public StringBuilder _print(StringBuilder sb) {
        return sb.append("ptr(" + _structType._name + ")");
    }
}
