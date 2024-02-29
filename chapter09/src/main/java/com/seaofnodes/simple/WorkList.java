package com.seaofnodes.simple;

import com.seaofnodes.simple.node.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;

/**
 * Classic WorkList, with a fast add/remove, dup removal, random pull.
 * The Node's nid is used to check membership in the worklist.
 */
public class WorkList<E extends Node> {

    private Node[] _es;
    private int _len;
    private final BitSet _on;   // Bit set if Node._nid is on WorkList
    private final Random _R;    // For randomizing pull from the WorkList
    private final long _seed;

    WorkList() { this(123); }
    WorkList(long seed) {
        _es = new Node[1];
        _len=0;
        _on = new BitSet();
        _seed = seed;
        _R = new Random();
        _R.setSeed(_seed);
    }

    /**
     * Pushes a Node on the WorkList, ensuring no duplicates
     * If Node is null it will not be added.
     */
    public E push( E x ) {
        if( x==null ) return null;
        int idx = x._nid;
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

    /**
     * True if Node is on the WorkList
     */
    boolean on( E x ) { return _on.get(x._nid); }

    /**
     * Removes a random Node from the WorkList; null if WorkList is empty
     */
    E pop() {
        if( _len == 0 ) return null;
        int idx = _R.nextInt(_len);
        E x = (E)_es[idx];
        _es[idx] = _es[--_len]; // Compress array
        _on.clear(x._nid);
        return x;
    }

    public void clear() {
        _len = 0;
        _on.clear();
        _R.setSeed(_seed);
    }
}
