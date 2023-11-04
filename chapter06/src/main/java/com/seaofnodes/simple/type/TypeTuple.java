package com.seaofnodes.simple.type;

public class TypeTuple extends Type {
  
    public final Type[] _types;

    public TypeTuple(Type... _types) {
        super(TTUPLE);
        this._types = _types;
    }

    @Override
    public Type xmeet(Type other) {
        throw new UnsupportedOperationException("Meet on Tuple Type not yet implemented");
    }

    @Override
    public StringBuilder _print(StringBuilder sb) {
        return sb;
    }

    public static final TypeTuple IF_BOTH_REACHABLE   = new TypeTuple(new Type[]{Type.CONTROL, Type.CONTROL});
    public static final TypeTuple IF_BOTH_UNREACHABLE = new TypeTuple(new Type[]{Type.XCONTROL,Type.XCONTROL});
    public static final TypeTuple IF_TRUE_REACHABLE   = new TypeTuple(new Type[]{Type.CONTROL, Type.XCONTROL});
    public static final TypeTuple IF_FALSE_REACHABLE  = new TypeTuple(new Type[]{Type.XCONTROL,Type.CONTROL});

}
