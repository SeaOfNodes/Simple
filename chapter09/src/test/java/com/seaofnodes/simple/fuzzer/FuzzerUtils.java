package com.seaofnodes.simple.fuzzer;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StopNode;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.BitSet;

/**
 * Some utilities for the fuzzer.
 */
class FuzzerUtils {

    /**
     * Get the value of a private field
     */
    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object obj, String name) throws NoSuchFieldException, IllegalAccessException {
        return (T) (obj instanceof Class<?> clz ? getField(clz, name).get(null) : getField(obj.getClass(), name).get(obj));
    }

    /**
     * Get access to a private field
     */
    public static Field getField(Class<?> clz, String name) throws NoSuchFieldException {
        var field = clz.getDeclaredField(name);
        try {
            field.setAccessible(true);
        } catch (Throwable e) {
            // Intentionally left empty
        }
        return field;
    }

    /**
     * Try to rethrow an exception or create a runtime exception and throw it.
     */
    public static RuntimeException rethrow(Throwable e) {
        if (e instanceof RuntimeException re) return re;
        if (e instanceof Error er) throw er;
        return new RuntimeException(e);
    }

    /**
     * Copy of Node.WVISIST
     * Used to clear as exceptions might happen in the walk and left this bitset not cleared
     */
    private static final BitSet NodeWalkVisit;

    /**
     * Write access to Iterate.MID_ASSERT
     * Exception could happen mid assert and left this at true. Used to reset to false prior to parsing.
     */
    private static final MethodHandle set_MID_ASSERT;

    /**
     * Some problems might trigger debug output. Suppress this output with a null print stream during fuzzing.
     */
    public static final PrintStream NULL_PRINT_STREAM = new PrintStream(OutputStream.nullOutputStream());

    static {
        try {
            NodeWalkVisit = getFieldValue(Node.class, "WVISIT");
            set_MID_ASSERT = MethodHandles.lookup().unreflectSetter(getField( IterPeeps.class, "MID_ASSERT"));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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
        var err = System.err;
        var out = System.out;
        try {
            System.setErr(NULL_PRINT_STREAM);
            System.setOut(NULL_PRINT_STREAM);
            try {
                set_MID_ASSERT.invokeExact(false);
            } catch (Throwable e) {
                throw rethrow(e);
            }
            var parser = new Parser(script);
            Node._disablePeephole = !runPeeps;
            var stop = parser.parse();
            return runPeeps ? stop.iterate() : stop;
        } finally {
            NodeWalkVisit.clear();
            System.setErr(err);
            System.setOut(out);
        }
    }

}
