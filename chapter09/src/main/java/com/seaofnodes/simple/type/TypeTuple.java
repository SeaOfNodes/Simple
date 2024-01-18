package com.seaofnodes.simple.type;

public class TypeTuple extends Type {
  
    public final Type[] _types;

    private TypeTuple(Type[] types) { super(TTUPLE); _types = types; }
    public static TypeTuple make(Type... types) { return new TypeTuple(types).intern(); }

    @Override
    public Type xmeet(Type other) {
        TypeTuple tt = (TypeTuple)other;     // contract from xmeet
        assert _types.length == tt._types.length;
        Type[] ts = new Type[_types.length];
        for( int i=0; i<_types.length; i++ )
            ts[i] = _types[i].meet(tt._types[i]);
        return make(ts);
    }

    @Override
    public StringBuilder _print(StringBuilder sb) {
        sb.append("[");
        for( Type t : _types )
            t._print(sb).append(",");
        sb.setLength(sb.length()-1);
        sb.append("]");
        return sb;
    }

    public static final TypeTuple IF_BOTH    = make(new Type[]{Type. CONTROL,Type. CONTROL});
    public static final TypeTuple IF_NEITHER = make(new Type[]{Type.XCONTROL,Type.XCONTROL});
    public static final TypeTuple IF_TRUE    = make(new Type[]{Type. CONTROL,Type.XCONTROL});
    public static final TypeTuple IF_FALSE   = make(new Type[]{Type.XCONTROL,Type. CONTROL});

    @Override
    int hash() {
        int sum = 0;
        for( Type type : _types ) sum ^= type.hashCode();
        return sum;
    }
    
    @Override
    boolean eq( Type t ) {
        TypeTuple tt = (TypeTuple)t; // Contract
        if( _types.length != tt._types.length ) return false;
        for( int i=0; i<_types.length; i++ )
            if( _types[i]!=tt._types[i] )
                return false;
        return true;
    }


}
