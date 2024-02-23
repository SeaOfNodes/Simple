package com.seaofnodes.simple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;
import java.util.function.IntSupplier;

// Classic worklist, with a fast add/remove, dup removal, random pull.
public class Work<E extends IntSupplier> {

    private Object[] _es;
    private int _len;
    private final BitSet _on;   // Bit set if Node._nid is on worklist
    private final Random _R;
    private final long _seed;

    Work() { this(123); }
    Work(long seed) {
        _es = new Object[1];
        _len=0;
        _on = new BitSet();
        _seed = seed;
        _R = new Random();
        _R.setSeed(_seed);
    }

    // Push on worklist, removing dups
    public E push( E x ) {
        if( x==null ) return null;
        int idx = x.getAsInt();
        if( !_on.get(idx) ) {
            _on.set(idx);
            if( _len==_es.length )
                _es = Arrays.copyOf(_es,_len<<1);
            _es[_len++] = x;
        }
        return x;
    }

    public void addAll( ArrayList<E> ary ) {
        for( E n : ary )
            push(n);
    }
    
    // True if on the worklist
    boolean on( E x ) { return _on.get(x.getAsInt()); }

    // Remove random element; null if empty
    E pop() {
        if( _len == 0 ) return null;
        int idx = _R.nextInt(_len);
        E x = (E)_es[idx];
        _es[idx] = _es[--_len]; // Compress array
        _on.clear(x.getAsInt());
        return x;
    }

    public void clear() {
        _len = 0;
        _on.clear();
        _R.setSeed(_seed);
    }
}
