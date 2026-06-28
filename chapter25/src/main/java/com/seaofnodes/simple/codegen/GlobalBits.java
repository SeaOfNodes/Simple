package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.util.*;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Objects;


// This utility 2-way maps some *global* program feature to a local dense
// index, suitable for packing in BitSets.  The mapping takes a global value -
// always a {source file/class} name and an order number counting up per
// feature in the same file.  The dense local index can be different in
// different compilations.

// The global info allows loading the same remote class info from different
// paths and aligning them.  Example: Compiling A loads pre-compiled B and C,
// both of which load pre-compiled D.  The 2 different D loads unify via this
// global info.

// These mappings are all trivial identities when compiling one file; they
// only become complex when loading seperately compiled code.

public class GlobalBits {
    public static final int RESERVED = 2;
    private final HashMap<String,int[]> _clzOrder2Local = new HashMap<>();
    private final Ary<String> _local2Clz = new Ary<>(String.class);
    private final AryInt _local2Order = new AryInt();
    // Local dense index
    private int _local;

    GlobalBits() {
        _local = RESERVED;
        // Reserved null-class bits are global identities and are never
        // remapped.
        int[] xs = new int[RESERVED];
        xs[0] = RESERVED;       // Per-class next order counter
        for( int i=0; i<RESERVED; i++ ) {
            if( i > 0 ) xs[i] = i;
            _local2Clz  .setX(i,null);
            _local2Order.setX(i,i);
        }
        _clzOrder2Local.put(null,xs);
    }

    // Get a next local index for this clz
    int next( String clz ) { return next(clz,-1); }

    // Given a clz and order, return an existing local index or make a new
    // mapping and return that.
    int next(String clz, int order ) {
        // Get the per-clz Order->Local mapping
        int[] xs = _clzOrder2Local.get(clz);
        if( xs == null )
            xs = new int[]{RESERVED,1}; // Make, if it does not exist
        // Caller does not have an order in mind, just take the next one stored
        // in xs[0]
        if( order == -1 )
            order = xs[0]++;
        // Order exists from prior compilation, but not recorded locally
        else if( order >= xs[0] )
            xs[0] = order+1;
        // Grow as needed
        while( xs.length <= order )
            xs = Arrays.copyOf(xs,xs.length*2);
        // Have a local mapping already?
        if( xs[order] != 0 )
            return xs[order];
        int local = _local++;
        bind(local,clz,order);
        return local;        // The dense local index
    }

    // Bind a specific dense local index to {clz,order}.  Used by both fresh
    // allocation and deserialization, where local indexes must not compact
    // across holes because XInt bitsets are serialized against this space.
    private void bind( int local, String clz, int order ) {
        if( _local <= local )
            _local = local+1;
        _local2Clz  .setX(local,clz  );
        _local2Order.setX(local,order);

        // Inline-only fidx holes are represented as {null,0}.  They need the
        // reverse mapping above, but no forward map because xs[0] is the
        // per-class next-order counter.
        if( clz==null && order==0 )
            return;

        int[] xs = _clzOrder2Local.get(clz);
        if( xs == null )
            xs = new int[]{RESERVED,1};
        if( order >= xs[0] )
            xs[0] = order+1;
        while( xs.length <= order )
            xs = Arrays.copyOf(xs,xs.length*2);
        _clzOrder2Local.put(clz,xs);
        xs[order] = local;
    }

    // A file-local function that will inline, and never write to disk
    public int nextInline( ) {
        int local = _local++;
        _local2Clz  .setX(local,null);
        _local2Order.setX(local,0);
        return local;
    }
    // Get a next local index for the same clz as this old index
    public int next( int old ) { return next(_local2Clz.at(old));  }

    // Map a file-local dense index to this compilation unit's local dense index.
    public int map( GlobalBits fileBits, int fileLocal ) {
        if( fileLocal >= fileBits._local2Clz.size() )
            throw new ArrayIndexOutOfBoundsException(""+fileLocal+" >= "+fileBits._local2Clz.size());
        return findOrNext(fileBits._local2Clz.at(fileLocal), fileBits._local2Order.at(fileLocal));
    }

    public boolean isLocal( int local, String clz ) {
        return local < RESERVED || (local < _local2Clz.size() && Objects.equals(_local2Clz.at(local),clz));
    }

    public boolean hasLocal( int local ) {
        return local < _local2Clz.size();
    }

    public int local( GlobalBits bits, int local ) {
        return findOrNext(bits._local2Clz.at(local),bits._local2Order.at(local));
    }

    // Map a global {clz,order} to a local one; keep any existing mapping or
    // make a new one as needed.
    private int findOrNext(String clz, int order ) {
        // Reserved null-class identities are not represented in xs[order],
        // because xs[0] is the next-order counter.
        if( clz==null && order < _local2Clz.size() && _local2Order.at(order)==order && _local2Clz.at(order)==null )
            return order;
        int[] xs = _clzOrder2Local.get(clz);
        if( xs != null && order < xs.length ) {
            int local = xs[order];
            if( local < _local2Clz.size() && _local2Order.at(local)==order && Objects.equals(_local2Clz.at(local),clz) )
                return local;
        }
        return next(clz,order);
    }

    // Write out to BAOS enough bits to unwind the local back to global index
    void packed( BAOS baos, HashMap<String,Integer> strs ) {
        // Unwind a local index to the clz.
        baos.packed2(_local2Clz.size());
        for( int i=RESERVED; i<_local2Clz.size(); i++ ) {
            String clz = _local2Clz.at(i);
            baos.packed2(clz==null ? 0 : strs.get(clz));
            baos.packed2( _local2Order.at(i) );
        }
    }

    void gather( HashMap<String,Integer> strs ) {
        for( int i=RESERVED; i<_local2Clz.size(); i++ )
            Serialize.gather(strs,_local2Clz.at(i));
    }

    // Read in the BAIS bits needed to make a file-local -> global mapping
    static GlobalBits packed( BAOS bais, String[] strs ) {
        var gb = new GlobalBits();
        int n = bais.packed2();
        for( int i=RESERVED; i<n; i++ ) {
            int sx = bais.packed2();
            String clz = strs[sx];
            if( clz.isEmpty() ) clz = null;
            int order = bais.packed2();
            gb.bind(i,clz,order);
        }
        return gb;
    }

    @Override public String toString() {
        SB sb = new SB().p('[');
        for( int i=0; i<_local2Clz.size(); i++ ) {
            if( i > 0 ) sb.p(',');
            sb.p(i).p(" <-> ").p(_local2Clz.at(i)).p('#').p(_local2Order.at(i));
        }
        return sb.p(']').toString();
    }

}
