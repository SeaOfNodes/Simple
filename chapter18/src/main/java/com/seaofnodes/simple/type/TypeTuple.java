package com.seaofnodes.simple.type;

import java.util.ArrayList;

public class TypeTuple extends Type {

    public final Type[] _types;

    private TypeTuple(Type[] types) { super(TTUPLE); _types = types; }
    public  static TypeTuple make(Type... types) { return new TypeTuple(types).intern(); }

    private static final TypeTuple TEST = make(TypeInteger.BOT,TypeMemPtr.TEST);
    public  static final TypeTuple START = make(Type.CONTROL,TypeMem.TOP,TypeInteger.BOT);
    public  static final TypeTuple SIGTEST = make(TypeInteger.BOT,TypeInteger.BOT);
    public  static final TypeTuple SIGBOT = make(Type.BOTTOM);
    public  static final TypeTuple SIGTOP = SIGBOT.dual();
    public  static void gather(ArrayList<Type> ts) {  ts.add(TEST); ts.add(START); ts.add(SIGTEST); }

    @Override
    Type xmeet(Type other) {
        TypeTuple tt = (TypeTuple)other;     // contract from xmeet
        if( this==SIGTOP ) return other;
        if( tt  ==SIGTOP ) return this ;
        if( _types.length != tt._types.length )
            return SIGBOT;
        Type[] ts = new Type[_types.length];
        for( int i=0; i<_types.length; i++ )
            ts[i] = _types[i].meet(tt._types[i]);
        return make(ts);
    }

    @Override public TypeTuple dual() {
        Type[] ts = new Type[_types.length];
        for( int i=0; i<_types.length; i++ )
            ts[i] = _types[i].dual();
        return make(ts);
    }

    @Override
    public Type glb() {
        Type[] ts = new Type[_types.length];
        for( int i=0; i<_types.length; i++ )
            ts[i] = _types[i].glb();
        return make(ts);
    }

    @Override public StringBuilder print(StringBuilder sb) {
        sb.append("[  ");
        for( Type t : _types )
            t.print(sb).append(", ");
        sb.setLength(sb.length()-2);
        sb.append("]");
        return sb;
    }

    @Override public String str() {
        StringBuilder sb = new StringBuilder();
        sb.append("[  ");
        for( Type t : _types )
            sb.append(t.str()).append(", ");
        sb.setLength(sb.length()-2);
        return sb.append("]").toString();
    }

    /**
     * Display Type name in a format that's good for IR printer
     */
    @Override
    public StringBuilder typeName(StringBuilder sb) {
        sb.append("[");
        for( Type t : _types )
            t.typeName(sb).append(",");
        sb.setLength(sb.length()-1);
        return sb.append("]");
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
