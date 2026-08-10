package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EscapeTest {
    private static final String POWER = """
        val power = { int base, int exponent ->
            if (exponent <= 0) return 1;
            int smaller=power(base,exponent-1);
            return base * smaller;
        };
        """;
    private static final String SUM = """
        val sumPowers = { int n, {int int -> int} f ->
            if (n <= 0) return 0;
            return f(2,n) + sumPowers(n-1,f);
        };
        """;
    private static ParmNode base(CodeGen code) {
        ParmNode parm=code._start.walk(n -> n instanceof ParmNode p && p._label.equals("base") ? p : null);
        assertNotNull(parm);
        return parm;
    }
    private static CodeGen analyze(String src, long seed) {
        return new CodeGen(src,TypeInteger.BOT,seed,true).driver(CodeGen.Phase.Opto);
    }

    @Test public void testKnownRecursiveCallback() {
        String src=POWER+SUM+"return sumPowers(arg,power);";
        for( long seed : new long[]{2,13,123} ) {
            CodeGen code=analyze(src,seed);
            ParmNode base=base(code);
            assertSame(TypeInteger.constant(2),base._type);
            assertFalse(base.fun().unknownCallers());
            assertNotNull(code._start.walk(n -> n instanceof CallNode c && "power".equals(c.name()) ? c : null));
            code.driver(CodeGen.Phase.TypeCheck);
            assertEquals("14",Eval2.eval(code,3));
        }
    }

    @Test public void testReturnedAddresses() {
        // Returning the address, or a chain of objects containing it, restores
        // arbitrary callers even when every visible call passes base=2.
        String holder="struct Holder { {int int -> int} f; }; val h=new Holder { f=power; };";
        for( String tail : new String[]{
            "return power;",
            holder+"return h;",
            holder+"struct Wrap { Holder child; }; return new Wrap { child=h; };",
            "struct Cell { {int int -> int}? !f; }; val h=new Cell; while(arg--) h.f=power; return h;"
        } ) {
            CodeGen code=analyze(POWER+SUM+"sumPowers(arg,power);"+tail,123);
            ParmNode base=base(code);
            assertSame(TypeInteger.BOT,base._type);
            assertTrue(base.fun().unknownCallers());
            assertTrue(code._start.escapedFIDX(base.fun().sig().fidx()));
            code.driver(CodeGen.Phase.TypeCheck);
        }
    }

    @Test public void testNativeExposure() {
        for( String tail : new String[]{
            "retain(power); return sumPowers(arg,power);",
            "if(arg) { retain(power); while(true) {} } return sumPowers(arg,power);"
        } ) {
            String src=POWER+SUM+"{ {int int -> int} -> int } retain=\"C\";"+tail;
            CodeGen code=analyze(src,123);
            ParmNode base=base(code);
            assertSame(TypeInteger.BOT,base._type);
            assertTrue(base.fun().unknownCallers());
            code.driver(CodeGen.Phase.TypeCheck);
        }
        // An external caller can inspect a field written through its argument,
        // even though the exposed function itself returns only an integer.
        String src="struct Holder { {int int -> int}? !f; };"+POWER+
            "val expose={!Holder h -> h.f=power; return 0;}; return expose;";
        CodeGen code=analyze(src,123);
        assertTrue(base(code).fun().unknownCallers());
        code.driver(CodeGen.Phase.TypeCheck);
    }
}
