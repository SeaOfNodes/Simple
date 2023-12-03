package com.seaofnodes.simple.fuzzer;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StopNode;

/**
 * Some utilities for the fuzzer.
 */
class FuzzerUtils {

    /**
     * Check that eiter one of the objects is null or that both are equal.
     */
    private static boolean equalOrNull(Object a, Object b) {
        if (a == null || b == null) return true;
        return a.equals(b);
    }

    /**
     * Check if both stack traces have the same first entry. Empty stack traces can happen with NPE optimization
     * and allow the other to be any stack trace.
     */
    private static boolean sameStackTraceOrigin(StackTraceElement[] a, StackTraceElement[] b) {
        if (a == null || a.length == 0 || b == null || b.length == 0) return true; // One or both stack traces are missing.
        return a[0].equals(b[0]);
    }

    /**
     * Checks if two exceptions are caused by the same problem.
     */
    public static boolean isExceptionFromSameCause(Throwable a, Throwable b) {
        if (a == null || b == null) return a == b; // Handle the null case
        if (a.getClass() != b.getClass()) return false; // Different classes
        if (!equalOrNull(a.getMessage(), b.getMessage())) return false; // Different messages. Null is allowed as wildcard
        var s1 = a.getStackTrace();
        var s2 = b.getStackTrace();
        if (!sameStackTraceOrigin(s1, s2)) return false; // Different stack trace origins
        if (!isExceptionFromSameCause(a.getCause(), b.getCause())) return false; // Different causes
        var o1 = a.getSuppressed();
        var o2 = b.getSuppressed();
        if (o1 == null || o2 == null || o1.length != o2.length) return o1 == o2; // Different suppression
        for (int i=0; i<o1.length; i++) {
            if (!isExceptionFromSameCause(o1[i], o2[i])) return false; // Different suppression
        }
        return true;
    }

    /**
     * Parse script with peepholes enabled or disabled
     */
    public static StopNode parse(String script, boolean runPeeps) {
        var parser = new Parser(script);
        Node._disablePeephole = !runPeeps;
        return parser.parse();
    }

}
