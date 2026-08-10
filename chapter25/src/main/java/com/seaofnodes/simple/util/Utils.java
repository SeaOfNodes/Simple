package com.seaofnodes.simple.util;

import java.util.ArrayList;

public class Utils {
    public static RuntimeException TODO() { return TODO("Not yet implemented"); }
    public static RuntimeException TODO(String msg) { return new RuntimeException(msg); }

    /**
     *  Fast, constant-time, element removal.  Does not preserve order
     *
     *  @param array ArrayList to modify
     *  @param i element to be removed
     *  @return element removed
     */
    public static <E> void del(ArrayList<E> array, int i) {
        E last = array.removeLast();
        if (i < array.size()) array.set(i, last);
    }

    /**
     * Search a list for an element by reference
     *
     * @param ary List to search in
     * @param x Object to be searched
     * @return >= 0 on success, -1 on failure
     */
    public static <E> int find( ArrayList<E> ary, E x ) {
        for( int i=0; i<ary.size(); i++ )
            if( ary.get(i)==x )
                return i;
        return -1;
    }
    /**
     * Search a list for an element by reference
     *
     * @param ary List to search in
     * @param x Object to be searched
     * @return >= 0 on success, -1 on failure
     */
    public static <E> int find( E[] ary, E x ) {
        for( int i=0; i<ary.length; i++ )
            if( ary[i]==x )
                return i;
        return -1;
    }

    // Rotate a long, nice for hashes
    public static long rot( long x, int n ) { return (x<<n) | (x>>>n); }

    // Fold a 64bit hash into 32 bits
    public static int fold( long x ) { return (int)((x>>32) ^ x); }
}
