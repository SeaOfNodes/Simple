package com.seaofnodes.simple.type;

public class TypeTuple extends Type {
  
    public final Type[] _types;

    public TypeTuple(Type... _types) {
        super(TTUPLE);
        this._types = _types;
    }

    @Override
    public Type meet(Type other) {
        throw new UnsupportedOperationException("Meet on Tuple Type not yet implemented");
    }

    @Override
    public StringBuilder _print(StringBuilder sb) {
        sb.append("[ ");
        for( Type t : _types )
            t._print(sb).append(",");
        sb.setLength(sb.length()-1);
        sb.append("]");
        return sb;
    }
}
