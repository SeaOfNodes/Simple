package com.seaofnodes.simple;

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
    public static <E> E del(ArrayList<E> array, int i) {
        if ( i >= 0 && i < array.size() ) {
            E tmp = array.get(i);
            E last = array.removeLast();
            if (i < array.size()) array.set(i, last);
            return tmp;
        }
        return null;
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
     * Represents the starting alias ID - which is 2 because then it nicely
     * slots into Start's projections. Start already uses slots 0-1.
     */
    static final int _RESET_ALIAS_ID = 2;

    /**
     * Alias ID generator - we start at 2 because START uses 0 and 1 slots,
     * by starting at 2, our alias ID is nicely mapped to a slot in Start.
     */
    static int _ALIAS_ID = _RESET_ALIAS_ID;

    /**
     * Resets the alias IDs for new parse
     */
    public static void resetAliasId() { _ALIAS_ID = _RESET_ALIAS_ID; }
    public static int nextAliasId() { return _ALIAS_ID++; }

}