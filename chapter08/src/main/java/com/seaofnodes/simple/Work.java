package com.seaofnodes.simple;

import java.util.BitSet;
import java.util.Random;
import java.util.function.IntSupplier;
import java.util.Arrays;

// Classic worklist, with a fast add/remove, dup removal, random pull.
public class Work<E extends IntSupplier> {

    private Object[] _es;
    private int _len;
    private final BitSet _on;   // Bit set if Node._nid is on worklist
    private final Random _R;

    Work() { this(123); }
    Work(long seed) {
        _es = new Object[1];
        _len=0;
        _on = new BitSet();
        _R = new Random(seed);
    }

    // Push on worklist, removing dups
    E push( E x ) {
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
    }
}
