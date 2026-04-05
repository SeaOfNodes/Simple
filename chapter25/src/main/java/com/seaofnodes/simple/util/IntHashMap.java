package com.seaofnodes.simple.util;

import java.util.Arrays;

// Int-Int Hash Map
public class IntHashMap {
    private int[] _es;
    private int _size;
    private final int _sentinel;

    // Pick a sentinel value; not allowed for keys or values.  Sentinel is
    // returned on a miss.  Writing sentinel counts as a remove.
    public IntHashMap(int sentinel) {
        _sentinel = sentinel;
        _es = new int[4];
        Arrays.fill(_es,sentinel);
    }

    public int size() { return _size; }
    public void clear() {
        _size = 0;
        Arrays.fill(_es,_sentinel);
    }

    private int cap() { return _es.length>>1; }

    public int put( int key, int val ) {
        int slot = probe(key);
        int old = _es[(slot<<1)+1];
        if( old == _sentinel && val != _sentinel ) _size++;
        if( old != _sentinel && val == _sentinel ) _size--;
        // Check cap
        if( _size > ((cap()>>1) + (cap()>>2)) ) {
            grow();
            slot = probe(key);
        }

        _es[ slot<<1   ] = key;
        _es[(slot<<1)+1] = val;
        return old;
    }

    public int get( int key ) {
        int slot = probe(key);
        return _es[(slot<<1)+1];
    }

    int probe( int key ) {
        int mask = cap()-1;
        int slot = key & mask;
        while( _es[slot<<1] != _sentinel && _es[slot<<1] != key )
            slot = (slot + (key|1)) & mask;
        return slot;
    }

    // Double underlying array size
    private void grow() {
        int[] old = _es;
        _es = new int[_es.length<<1];
        Arrays.fill(_es,_sentinel);
        for( int i=0; i<old.length; i+=2 )
            if( old[i] != _sentinel )
                put(old[i],old[i+1]);
    }
}
