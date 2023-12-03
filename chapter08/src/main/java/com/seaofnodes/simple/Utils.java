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
        E tmp = array.get(i);
        E last = array.removeLast();
        if (i < array.size()) array.set(i, last);
        return tmp;
    }


}