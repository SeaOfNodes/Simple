package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompoundAssignmentTest {
    private static final String[] OPS = {"&=", "|=", "^=", "<<=", ">>=", ">>>="};

    private static long apply(String op, long lhs, long rhs) {
        return switch (op) {
            case "&=" -> lhs & rhs;
            case "|=" -> lhs | rhs;
            case "^=" -> lhs ^ rhs;
            case "<<=" -> lhs << rhs;
            case ">>=" -> lhs >> rhs;
            case ">>>=" -> lhs >>> rhs;
            default -> throw new AssertionError(op);
        };
    }

    @Test public void testLocals() {
        for (String op : OPS)
            for (long arg : new long[]{-8, 0, 5, 64}) {
                long expected = apply(op, arg, 3);
                check("int x=arg; int r=(x " + op + " 3); return x*10+r;",
                      arg, expected*11);
            }
    }

    @Test public void testRhsOnce() {
        for (String op : OPS) {
            long expected = apply(op, 5, 1);
            check("int x=arg; int count=0; int r=(x " + op +
                  " (count=count+1)); return count*1000+x*10+r;", 5, 1000+expected*11);
        }
    }

    @Test public void testNarrowingAndChaining() {
        check("u8 x=255; x <<= 1; return x;", 0, 254);
        check("u8 x=255; x ^= 256; return x;", 0, 255);
        check("int x=1; int y=3; x |= y <<= 2; return x*100+y;", 0, 1312);
        check("int x=-8; x >>= 65; return x;", 0, -4);
        check("int x=-8; x >>>= 65; return x;", 0, Long.MAX_VALUE-3);
    }

    @Test public void testExistingOperators() {
        check("int x=8; x+=2; x-=1; x*=3; x/=9; return x;", 0, 3);
        check("int x=arg; int y=x++; int z=x--; return x*100+y*10+z;", 5, 556);
        check("return (arg << 2) + (arg >> 1) + (arg >>> 1);", 8, 40);
        check("return arg <= 8;", 8, 1);
        check("return arg >= 8;", 8, 1);
    }

    @Test public void testRejectedAssignments() {
        for (String op : OPS) {
            rejected("val x=3; x " + op + " 1; return x;");
            rejected("int x=3; x " + op + " 1.5; return x;");
        }
    }

    private static void rejected(String src) {
        try {
            check(src, 0, 0);
            fail("Accepted invalid assignment: " + src);
        } catch (RuntimeException e) {
            assertEquals("Must report a language error", RuntimeException.class, e.getClass());
            assertNotNull(e.getMessage());
            assertFalse("Must report a language error: " + e, e.getMessage().contains("Not yet implemented"));
        }
    }

    private static void check(String src, long arg, long expected) {
        var stop = new Parser(src).parse().iterate();
        assertEquals(src, Long.valueOf(expected), Evaluator.evaluate(stop, arg));
    }
}
