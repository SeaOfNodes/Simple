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
    private final HashMap<String,int[]> _clzOrder2Local = new HashMap<>();
    private final Ary<String> _local2Clz = new Ary<>(String.class);
    private final AryInt _local2Order = new AryInt();
    // Local dense index
    private int _local;

    GlobalBits( int init ) { _local = init; }

    // Get a next local index for this clz
    int next( String clz ) { return next(clz,-1); }

    // Record the next local index for this clz
    private int next(String clz, int order ) {
        // Get the per-clz Order->Local mapping
        int[] xs = _clzOrder2Local.get(clz);
        if( xs == null ) xs = new int[]{1}; // Make, if it does not exist
        // Caller does not have an order in mind, just take the next one stored
        // in xs[0]
        if( order == -1 )
            order = xs[0]++;
        // Grow as needed
        while( xs.length <= order )
            xs = Arrays.copyOf(xs,xs.length*2);
        // Exterd mapping {clz,Order} -> Local
        _clzOrder2Local.put(clz,xs);
        xs[order] = _local;
        // Save reverse mapping
        _local2Clz  .setX(_local,clz  );
        _local2Order.setX(_local,order);
        return _local++;        // The dense local index
    }


    // Get a next local index for the same clz as this old index
    public int next( int old ) { return next(_local2Clz.at(old));  }

    // Map a file-local dense index to this compilation unit's local dense index.
    public int map( GlobalBits fileBits, int fileLocal ) {
        if( fileLocal >= fileBits._local2Clz.size() )
            throw new ArrayIndexOutOfBoundsException(""+fileLocal+" >= "+fileBits._local2Clz.size());
        return findOrNext(fileBits._local2Clz.at(fileLocal), fileBits._local2Order.at(fileLocal));
    }

    // Map a global {clz,order} to a local one; keep any existing mapping or
    // make a new one as needed.
    private int findOrNext(String clz, int order ) {
        int[] xs = _clzOrder2Local.get(clz);
        if( xs != null && order < xs.length ) {
            int local = xs[order];
            if( local < _local2Clz.size() && _local2Order.at(local)==order && Objects.equals(_local2Clz.at(local),clz) )
                return local;
        }
        return next(clz,order);
    }

    // Write out to BAOS enough bits to unwind the local back to global index
    void packed( BAOS baos, HashMap<String,Integer> strs, int i ) {
        // Unwind a local index to the clz.
        baos.packed2(_local2Clz.size());
        for( ; i<_local2Clz.size(); i++ ) {
            String clz = _local2Clz.at(i);
            baos.packed2(clz==null ? 0 : strs.get(clz));
            baos.packed2( _local2Order.at(i) );
        }
    }

    void gather( HashMap<String,Integer> strs, int i ) {
        for( ; i<_local2Clz.size(); i++ )
            Serialize.gather(strs,_local2Clz.at(i));
    }

    // Read in the BAIS bits needed to make a file-local -> global mapping
    static GlobalBits packed( BAOS bais, String[] strs, int i) {
        var gb = new GlobalBits(i);
        int n = bais.packed2();
        for( ; i<n; i++ ) {
            int sx = bais.packed2();
            String clz = strs[sx];
            if( clz.isEmpty() ) clz = null;
            int order = bais.packed2();
            gb.next(clz,order);
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
