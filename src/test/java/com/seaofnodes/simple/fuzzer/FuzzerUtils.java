package com.seaofnodes.simple.fuzzer;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StopNode;
import com.seaofnodes.simple.type.TypeInteger;
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
     * Get the val of a private field
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
     * Some problems might trigger debug output. Suppress this output with a null print stream during fuzzing.
     */
    public static final PrintStream NULL_PRINT_STREAM = new PrintStream(OutputStream.nullOutputStream());

    /**
     * Check that the messages are similar.
     */
    private static boolean similarMessages(String a, String b) {
        if (a == null || b == null) return true;
        a = a.replaceAll("'\\w+'", "'normalized'").replaceAll("variable \\w+", "variable normalized");
        b = b.replaceAll("'\\w+'", "'normalized'").replaceAll("variable \\w+", "variable normalized");
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
        if (!similarMessages(a.getMessage(), b.getMessage())) return false; // Different messages. Null is allowed as wildcard
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
    public static CodeGen parse(String script, long workListSeed) {
        var err = System.err;
        var out = System.out;
        try {
            System.setErr(NULL_PRINT_STREAM);
            System.setOut(NULL_PRINT_STREAM);
            var code = new CodeGen(script, TypeInteger.BOT, workListSeed);
            return code.driver(CodeGen.Phase.LocalSched);
        } finally {
            System.setErr(err);
            System.setOut(out);
        }
    }

}
