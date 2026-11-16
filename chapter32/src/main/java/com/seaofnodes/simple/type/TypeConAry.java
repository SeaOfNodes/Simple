package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.BAOS;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Constant integer contents with an explicit storage width and signedness.
 */
public final class TypeConAry extends TypeScalar {
    boolean _any;
    byte _widen;
    private final TypeInteger _storage;
    private final long[] _ary;

    private TypeConAry(boolean any, byte widen, TypeInteger storage, long[] ary) {
        super(TCONARY);
        _any=any; _widen=widen; _storage=storage; _ary=ary;
    }
    static final TypeConAry ABC=make("abc");
    static final TypeConAry ABCD=make("abcd");
    public static void gather(ArrayList<Type> ts) {
        ts.add(make(""));
        ts.add(ABC);
        ts.add(ABCD);
        ts.add(make(TypeInteger.I32,new long[]{1,2,3}));
        ts.add(make(TypeInteger.U16,new long[]{0,65535}));
        ts.add(make(TypeInteger.BOT,new long[]{Long.MIN_VALUE,Long.MAX_VALUE}));
        for( TypeInteger storage : STORAGE ) {
            ts.add(make(storage,new long[0]));
            ts.add(make(storage,new long[]{0,1}));
        }
    }
    // Fresh, uninterned type sharing this array; xdual must not intern.
    private TypeConAry _make(boolean any, byte widen) {
        return new TypeConAry(any,widen,_storage,_ary);
    }

    @Override TypeConAry xdual() { return _make( !_any, (byte)(3-_widen) ); }

    @Override Type xmeet(Type t) {
        TypeConAry ary = (TypeConAry)t; // Invariant
        if( len()==0 ) return emptyMeet(ary);
        if( ary.len()==0 ) return ary.emptyMeet(this);
        // Same base array but different?
        if( _equals(ary) )
            // Return array with larger widen
            return _widen >= ary._widen ? this : ary;
        // Unrelated constant arrays, falls to some int range
        return elem().meet(ary.elem());
    }

    Type ymeet( TypeInteger ti ) {
        if( len()==0 ) return emptyMeet(ti);
        // if i can isa *each* element, then maybe can keep.
        // no good to i.isa(elem()) because fails the dual
        for( int j=0; j<len(); j++ )
            if( !ti.isa(TypeInteger.make(at8(j),at8(j),ti._widen)) )
                return ti.meet(elem());
        byte widen = (byte)Math.max(_widen,ti._widen);
        return widen==_widen ? this : _make(_any,widen).intern();
    }

    private boolean _equals(TypeConAry ary) {
        return _storage==ary._storage && (len()!=0 || _any==ary._any) && Arrays.equals(_ary,ary._ary);
    }
    @Override boolean eq(Type t) { return t instanceof TypeConAry ary && _widen==ary._widen && _equals(ary); }
    @Override int hash() { return Arrays.hashCode(_ary)^_storage.hashCode()^(len()==0 && _any ? 1231 : 0); }
    @Override public String str() {
        boolean text=_storage==TypeInteger.U8;
        for( long value : _ary )
            if( value<32 || value>126 ) { text=false; break; }
        if( !text ) {
            String range=elem().str();
            return range.startsWith("[") ? range : "["+range+"]";
        }
        byte[] bytes = new byte[len()];
        for( int i=0; i<bytes.length; i++ ) bytes[i]=(byte)_ary[i];
        return "[\""+new String(bytes)+"\"]";
    }

    @Override public boolean isHigh() { return this==TOP; }
    @Override boolean _isConstant() { return true; }
    @Override Type _makeStorage() { return this; }

    // Meet-over-elements type
    public Type elem() {
        int len = len();
        long min = Long.MAX_VALUE;
        if( len==0 ) return emptyElem();
        long max = Long.MIN_VALUE;
        for( int i=0; i<len; i++ ) {
            min = Math.min(min,at8(i));
            max = Math.max(max,at8(i));
        }
        return TypeInteger.make(min,max,_widen);
    }
    private TypeInteger emptyElem() {
        TypeInteger storage=storageType();
        return _any ? TypeInteger.make(storage._min,storage._max,_widen)
                    : TypeInteger.make(storage._max,storage._min,_widen);
    }
    // Keep the empty payload distinct from its element bound. It sits just
    // above the storage dual; its own dual sits just below the storage bound.
    // This makes it a subtype of every constant array within those bounds,
    // while preserving meet/join symmetry with ordinary integer ranges.
    private Type emptyMeet(Type t) {
        Type bound=emptyElem();
        if( t.isa(bound) && (_any || t!=bound) ) return this;
        return bound.meet(t);
    }
    public TypeInteger storageType() { return _storage; }
    public long at8(int idx) { return _ary[idx]; }
    public int len() { return _ary.length; }
    @Override public int log_size() { return _storage.log_size(); }
    public void write( BAOS baos ) {
        for( int i=0; i<len(); i++ ) {
            long value=at8(i);
            for( int b=0; b<(1<<log_size()); b++ ) baos.write((byte)(value >>> (8*b)));
        }
    }

    /** Canonical contents, independent of the node which discovered them. */
    public static TypeConAry make(TypeInteger storage, long[] values) {
        long[] copy=values.clone();
        int shift=64-(8<<storage.log_size());
        for( int i=0; i<copy.length; i++ )
            copy[i]=storage._min<0 ? (copy[i]<<shift)>>shift : copy[i]&storage._max;
        return new TypeConAry(false,(byte)0,storage,copy).intern();
    }
    public static TypeConAry make(byte[] bytes) {
        long[] values=new long[bytes.length];
        for( int i=0; i<bytes.length; i++ ) values[i]=bytes[i]&255;
        return new TypeConAry(false,(byte)0,TypeInteger.U8,values).intern();
    }
    public static TypeConAry make(String s) { return make(s.getBytes()); }

    // Keep the existing byte-array tag and encoding. An impossible Java array
    // length escapes to a typed payload, without renumbering later type tags.
    private static final int TYPED = -1;
    private static final TypeInteger[] STORAGE={TypeInteger.BOOL,TypeInteger.I8,TypeInteger.U8,
        TypeInteger.I16,TypeInteger.U16,TypeInteger.I32,TypeInteger.U32,TypeInteger.BOT};

    // Reserve tags for u8 array
    @Override int TAGOFF() { return 1; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs ) {
        baos.write(TAGOFFS[TCONARY]+0);
        if( storageType()!=TypeInteger.U8 || _any || _widen!=0 ) {
            baos.packed4(TYPED);
            int kind=0;
            while( kind<STORAGE.length && STORAGE[kind]!=storageType() ) kind++;
            assert kind<STORAGE.length;
            baos.packed1(kind);
            baos.packed1((_any ? 4 : 0)|_widen);
        }
        baos.packed4(len());
        write(baos);
    }
    static TypeConAry packed( int tag, BAOS bais ) {
        int len=bais.packed4();
        if( len!=TYPED ) return make(bais.read(new byte[len]));
        TypeInteger storage=STORAGE[bais.packed1()];
        int flags=bais.packed1();
        long[] values=new long[bais.packed4()];
        int size=1<<storage.log_size();
        for( int i=0; i<values.length; i++ ) {
            long value=0;
            for( int b=0; b<size; b++ ) value|=(long)bais.read() << (8*b);
            if( storage._min<0 && size<8 ) value=(value << (64-size*8)) >> (64-size*8);
            values[i]=value;
        }
        TypeConAry ary=make(storage,values);
        return flags==0 ? ary : ary._make((flags&4)!=0,(byte)(flags&3)).intern();
    }
}
