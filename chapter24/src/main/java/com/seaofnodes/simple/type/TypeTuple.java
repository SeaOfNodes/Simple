package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import com.seaofnodes.simple.util.BAOS;
import java.util.*;

public class TypeTuple extends Type {

    public Type[] _types;

    private static final Ary<TypeTuple> FREE = new Ary<>(TypeTuple.class);
    private TypeTuple(Type[] types) { super(TTUPLE); _types = types; }
    private TypeTuple init( Type[] types ) { _types = types; return this; }
    static TypeTuple malloc(Type[] types) {
        return FREE.isEmpty() ? new TypeTuple(types) : FREE.pop().init(types);
    }
    // All fields directly listed
    public static TypeTuple make(Type... types) {
        TypeTuple tt = malloc(types);
        TypeTuple t2 = tt.intern();
        return t2==tt ? tt : t2.free(tt);
    }
    @Override TypeTuple free(Type t) {
        TypeTuple tt = (TypeTuple)t;
        tt._types = null;
        tt._dual = null;
        tt._hash = 0;
        FREE.push(tt);
        return this;
    }
    @Override boolean isFree() { return _types==null; }


    public static final TypeTuple BOT = malloc(new Type[0]).intern();
    public static final TypeTuple TOP = BOT.dual();

    public static final TypeTuple TEST = make(TypeInteger.BOT,TypeMemPtr.TEST);
    public static final TypeTuple START= make(Type.CONTROL,TypeMem.TOP,TypeInteger.BOT);
    public static final TypeTuple RET  = make(Type.CONTROL,TypeMem.BOT,Type.BOTTOM);

    public static final TypeTuple IF_BOTH    = make(Type. CONTROL,Type. CONTROL);
    public static final TypeTuple IF_NEITHER = make(Type.XCONTROL,Type.XCONTROL);
    public static final TypeTuple IF_TRUE    = make(Type. CONTROL,Type.XCONTROL);
    public static final TypeTuple IF_FALSE   = make(Type.XCONTROL,Type. CONTROL);

    public static void gather(ArrayList<Type> ts) { ts.add(BOT); ts.add(TEST); ts.add(START); ts.add(IF_TRUE); }

    @Override
    Type xmeet(Type other) {
        TypeTuple that = (TypeTuple)other;     // contract from xmeet
        if( this==TOP ) return that;
        if( that==TOP ) return this;
        if( _types.length != that._types.length )
            return BOT;
        return make(TypeFunPtr.xmeet(_types,that._types));
    }

    @Override TypeTuple xdual() {
        if( _types.length==0 ) return malloc(null);
        return malloc(TypeFunPtr.xdual(_types));
    }

    @Override boolean _isConstant() {
        for( Type t : _types )
            if( !t._isConstant() )
                return false;
        return true;
    }
    @Override boolean _isFinal() { throw Utils.TODO(); }

    @Override TypeMemPtr _makeRO() { throw Utils.TODO(); }

    @Override public int log_size() { throw Utils.TODO(); }
    @Override public int alignment() {
        assert isConstant();
        int align = 0;
        for( Type t : _types )
            align = Math.max(align,t.alignment());
        return align;
    }

    public Type ret() { assert _types.length==3; return _types[2]; }

    @Override public int nkids() { return _types.length; }
    @Override public Type at( int idx ) { return _types[idx]; }
    @Override public void set( int idx, Type t ) { _types[idx] = t; }

    // Reserve tags for tuples with 2,3,generic
    @Override int TAGOFF() { return 4; }
    @Override public void packedT( BAOS baos, HashMap<String,Integer> strs, HashMap<Integer,Integer> aliases ) {
        if( _types.length==2 ) baos.write(TAGOFFS[_type] + 0);
        else if( _types.length==3 ) baos.write(TAGOFFS[_type] + 1);
        else if( _types.length==4 ) baos.write(TAGOFFS[_type] + 2);
        // 2 or generic
        else throw Utils.TODO();
    }
    static TypeTuple packedT( int tag, BAOS bais ) {
        if( tag==0 ) return malloc(new Type[2]);
        if( tag==1 ) return malloc(new Type[3]);
        if( tag==2 ) return malloc(new Type[4]);
        throw Utils.TODO();
    }

    @Override SB _print(SB sb, BitSet visit, boolean html ) {
        if( this==TOP ) return sb.p("[TOP]");
        if( this==BOT ) return sb.p("[BOT]");
        sb.p("[  ");
        for( Type t : _types )
            t.print(sb,visit,html).p(", ");
        return sb.unchar(2).p("]");
    }

    @Override public String str() { return print(new SB(),new BitSet(),false).toString(); }

    @Override
    int hash() {
        int sum = 0;
        if( _types!=null ) for( Type type : _types ) sum ^= type.hashCode();
        return sum;
    }

    @Override
    boolean eq( Type t ) {
        TypeTuple that = (TypeTuple)t; // Contract
        if( _types==null && that._types==null ) return true;
        if( _types==null || that._types==null ) return false;
        if( _types.length != that._types.length ) return false;
        for( int i=0; i<_types.length; i++ )
            if( _types[i]!=that._types[i] )
                return false;
        return true;
    }

}
