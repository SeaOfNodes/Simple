package com.seaofnodes.simple.type;

public class TypeTuple extends Type {

    public final Type[] _types;

    public TypeTuple(Type[] _types) {
        this._types = _types;
    }

    @Override
    public Type meet(Type other) {
        throw new UnsupportedOperationException("Meet on Tuple Type not yet implemented");
    }

    @Override
    public StringBuilder _print(StringBuilder sb) {
        return sb;
    }
}
