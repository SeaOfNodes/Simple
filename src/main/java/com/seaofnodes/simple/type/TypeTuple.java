package com.seaofnodes.simple.type;

import com.seaofnodes.simple.SB;
import java.util.ArrayList;

public class TypeTuple extends Type {

    public final Type[] _types;

    private TypeTuple(Type[] types) { super(TTUPLE); _types = types; }
    public  static TypeTuple make(Type... types) { return new TypeTuple(types).intern(); }

    public static final TypeTuple BOT = new TypeTuple(new Type[0]).intern();
    public static final TypeTuple TOP = new TypeTuple(null).intern();

    public static final TypeTuple TEST = make(TypeInteger.BOT,TypeMemPtr.TEST);
    public static final TypeTuple START= make(Type.CONTROL,TypeMem.TOP,TypeInteger.BOT);
    public static final TypeTuple MAIN = make(TypeInteger.BOT);
    public static final TypeTuple RET  = make(Type.CONTROL,TypeMem.BOT,Type.BOTTOM);
    public static final TypeTuple CALLOC = make(TypeInteger.BOT,TypeInteger.BOT);

    public static final TypeTuple IF_BOTH    = make(new Type[]{Type. CONTROL,Type. CONTROL});
    public static final TypeTuple IF_NEITHER = make(new Type[]{Type.XCONTROL,Type.XCONTROL});
    public static final TypeTuple IF_TRUE    = make(new Type[]{Type. CONTROL,Type.XCONTROL});
    public static final TypeTuple IF_FALSE   = make(new Type[]{Type.XCONTROL,Type. CONTROL});

    public  static void gather(ArrayList<Type> ts) { ts.add(BOT); ts.add(TEST); ts.add(START); ts.add(MAIN); ts.add(IF_TRUE); }

    @Override
    Type xmeet(Type other) {
        TypeTuple tt = (TypeTuple)other;     // contract from xmeet
        if( this==TOP ) return other;
        if( tt  ==TOP ) return this ;
        if( _types.length != tt._types.length )
            return BOT;
        Type[] ts = new Type[_types.length];
        for( int i=0; i<_types.length; i++ )
            ts[i] = _types[i].meet(tt._types[i]);
        return make(ts);
    }

    @Override public TypeTuple dual() {
        if( this==TOP ) return BOT;
        if( this==BOT ) return TOP;
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

    @Override public boolean isConstant() {
        for( Type t : _types )
            if( !t.isConstant() )
                return false;
        return true;
    }

    @Override public int log_size() {
        assert isConstant();
        int log_size = 0;
        for( Type t : _types )
            log_size = Math.max(log_size,t.log_size());
        return log_size;
    }

    public Type ret() { assert _types.length==3; return _types[2]; }

    @Override public String str() { return print(new SB()).toString(); }

    @Override public SB print(SB sb) {
        if( this==TOP ) return sb.p("[TOP]");
        if( this==BOT ) return sb.p("[BOT]");
        sb.p("[  ");
        for( Type t : _types )
            t.print(sb).p(", ");
        return sb.unchar(2).p("]");
    }
    @Override public SB gprint(SB sb) {
        if( this==TOP ) return sb.p("[TOP]");
        if( this==BOT ) return sb.p("[BOT]");
        sb.p("[  ");
        for( Type t : _types )
            t.gprint(sb).p(", ");
        return sb.unchar(2).p("]");
    }

    @Override
    int hash() {
        int sum = 0;
        if( _types!=null ) for( Type type : _types ) sum ^= type.hashCode();
        return sum;
    }

    @Override
    boolean eq( Type t ) {
        TypeTuple tt = (TypeTuple)t; // Contract
        if( _types==null && tt._types==null ) return true;
        if( _types==null || tt._types==null ) return false;
        if( _types.length != tt._types.length ) return false;
        for( int i=0; i<_types.length; i++ )
            if( _types[i]!=tt._types[i] )
                return false;
        return true;
    }


}
