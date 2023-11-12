package com.seaofnodes.simple;

public class Utils {
    public static RuntimeException TODO() { return TODO("Not yet implemented"); }
    public static RuntimeException TODO(String msg) { return new RuntimeException(msg); }
}