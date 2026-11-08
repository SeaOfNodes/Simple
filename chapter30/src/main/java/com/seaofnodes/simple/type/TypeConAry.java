package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Represents a constant array of primitives
 */
public abstract class TypeConAry<A> extends TypeScalar {
    boolean _any;
    byte _widen;
    // One of byte or int array
    public final A _ary;

    TypeConAry( boolean any, byte widen, A ary ) { super(TCONARY); _any = any; _widen = widen; _ary = ary; }
    public static void gather(ArrayList<Type> ts) {
        ts.add(TypeConAryB.make(""));
        ts.add(TypeConAryB.ABC);
        ts.add(TypeConAryB.ABCD);
        ts.add(TypeConAryI.I123);
    }
    // Fresh, uninterned type sharing this array; xdual must not intern.
    abstract TypeConAry<A> _make(boolean any, byte widen);

    @Override TypeConAry<A> xdual() { return _make( !_any, (byte)(3-_widen) ); }

    @Override Type xmeet(Type t) {
        TypeConAry ary = (TypeConAry)t; // Invariant
        // Same base array but different?
        if( _equals(ary) )
            // Return array with larger widen
            return _widen >= ary._widen ? this : ary;
        // Unrelated constant arrays, falls to some int range
        return elem().meet(ary.elem());
    }

    Type ymeet( TypeInteger ti ) {
        // Empty arrays have no observed elements: use the storage bounds.
        // Check both endpoints just as the loop checks a nonempty body;
        // retaining high integer inputs here preserves lattice duality.
        if( len()==0 ) {
            TypeInteger bound=emptyElem();
            if( !ti.isa(TypeInteger.make(bound._min,bound._min,ti._widen)) ||
                !ti.isa(TypeInteger.make(bound._max,bound._max,ti._widen)) )
                return ti.meet(bound);
        }
        // if i can isa *each* element, then maybe can keep.
        // no good to i.isa(elem()) because fails the dual
        for( int j=0; j<len(); j++ )
            if( !ti.isa(TypeInteger.make(at8(j),at8(j),ti._widen)) )
                return ti.meet(elem());
        byte widen = (byte)Math.max(_widen,ti._widen);
        return widen==_widen ? this : _make(_any,widen).intern();
    }

    private boolean _equals(TypeConAry ary) {
        int len = len();
        if( len != ary.len() ) return false;
        for( int i=0; i<len; i++ )
            if( at8(i) != ary.at8(i) )
                return false;
        return true;
    }

    @Override public boolean isHigh() { return this==TOP; }
    @Override boolean _isConstant() { return true; }
    @Override Type _makeStorage() { return this; }

    // Meet-over-elements type
    public Type elem() {
        if( _ary==null )
            return _any ? Type.TOP : BOTTOM;
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
        return log_size()==0 ? TypeInteger.make(0,255,_widen)
            : TypeInteger.make(Integer.MIN_VALUE,Integer.MAX_VALUE,_widen);
    }
    public abstract long at8(int idx);
    public abstract int len();
    @Override public abstract int log_size();
    public void write( BAOS baos ) { throw Utils.TODO("Should not reach here: abstract constant array cannot be written"); }

    // Reserve tags for u8 array
    @Override int TAGOFF() { return 1; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs ) {
        assert log_size()==0 && !_any;
        baos.write(TAGOFFS[TCONARY]+0);
        baos.packed4(len());
        baos.write((byte[])_ary);
    }
    static TypeConAry packed( int tag, BAOS bais ) {
        return TypeConAryB.make(bais.read(new byte[bais.packed4()]));
    }
}
