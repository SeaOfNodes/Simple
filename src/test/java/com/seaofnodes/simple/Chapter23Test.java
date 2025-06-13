package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.RegAllocTestSupport.CheckedCodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;


import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter23Test {
    @Test public void testPopularUses() { com.seaofnodes.simple.codegen.RegAllocTestSupport.popularUses(); }

    @Test public void testDiagnosticTypeAccessors() throws Exception {
        new CodeGen("return 0;").parse();
        var tfp = TypeFunPtr.TEST;
        var con = new ConstantNode(tfp);
        var call = new CallNode(null,con,con,con);
        var field = Type.class.getDeclaredField("VISIT");
        field.setAccessible(true);
        var visit = (java.util.Map<Object,Type>)field.get(null);
        var marker = new Object();
        visit.put(marker,Type.BOTTOM);
        try {
            assertEquals(0,TypeInteger.ZERO.alignment());
            assertEquals(0,TypeInteger.ZERO.value());
            org.junit.Assert.assertTrue(tfp._isConstant());
            con.toString();
            call.name();
            con.p(1);
            org.junit.Assert.assertSame(Type.BOTTOM,visit.get(marker));
            assertEquals(1,visit.size());
        } finally { visit.clear(); }
    }


    @Test
    public void testOrRhsLoopLocal() throws IOException {
        checkOrRhsLoop("""
            struct S { int f; };
            int x = 0;
            int result = (arg & 1) || (new S {
                while (x < 3) x++;
                f = ++x;
            }).f;
            return x * 10 + result;
            """);
    }

    @Test
    public void testOrRhsLoopMemory() throws IOException {
        checkOrRhsLoop("""
            struct S { int f; };
            S !s = new S;
            int result = (arg & 1) || (new S {
                while (s.f < 3) s.f++;
                s.f++;
                f = s.f;
            }).f;
            return s.f * 10 + result;
            """);
    }

    private static void checkOrRhsLoop(String src) throws IOException {
        CodeGen code = new CheckedCodeGen(src).driver("riscv", "SystemV", null);
        SpillStats.record(code,"Chapter23","riscv","SystemV");
        for (int arg : new int[]{0, 1, 2, 3}) {
            byte[] image = new byte[1 << 20];
            System.arraycopy(code._encoding.bits(), 0, image, 0, code._encoding._bits.size());
            EvalRisc5 r5 = new EvalRisc5(image, 1 << 16);
            r5.regs[riscv.A0] = arg;
            assertEquals("Execution must finish", 0, r5.step(1000));
            // False LHS runs the loop and final increment, returning 4.
            // True LHS skips all RHS effects and returns the LHS value, 1.
            assertEquals("OR result and side effects for arg=" + arg,
                (arg & 1) == 0 ? 44 : 1, r5.regs[riscv.A0]);
        }
    }

    @Test
    public void testJig() throws IOException {
        String src =
"""
var apply = { i64 x, { i64 -> u32 } fcn ->
    return fcn(x);
}
""";
    }


    static CodeGen testCPU( String src, String cpu, String os, int spills, String stop ) {
        CodeGen code = new CheckedCodeGen(src).driver(CodeGen.Phase.Encoding,cpu,os);
        SpillStats.record(code,"Chapter23",cpu,os);
        SpillStats.checkSpills(spills,code._regAlloc._spillScaled);
        if( stop != null )
            assertEquals(stop, code._stop.toString());
        return code;
    }

    @Test
    public void testOr() throws IOException {
        String src =
                """
                int a = 1;
                int b = 0;
                
                if(a++ || b++ ) {
                    if(b == 0 && a == 2) {
                        sys.io.p("Or");
                    }
                } else{
                    sys.io.p("And");
                }
                return 0;
                """;

        TestC.runSF("or1", src, null, "Or", 0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build("or1", src, 0, 2, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);
        assertEquals("Or",R5._stdout.toString());

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("or1", src,0, 2, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
        assertEquals("Or",arm._stdout.toString());
    }

    @Test
    public void testAnd() throws IOException {
        String src =
                """
                int a = 1;
                int b = 1;
                
                if(a && b) {
                    sys.io.p("And");
                } else {
                    sys.io.p("Or");
                }
                return 0;
                """;
        TestC.runSF("and1", src, null, "And", 0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build("and1", src, 0, 2, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);
        assertEquals("And",R5._stdout.toString());

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("and1", src, 0, 2, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
        assertEquals("And",arm._stdout.toString());
    }

    @Test
    public void testAndPtr() throws IOException {
        // Todo: have one src here
        String src =
                """
                struct S { S? fld; };
                val ptr = arg == 1 ? null : new S{fld = arg==1 ? null : new S{fld = null;};};
                return ptr && ptr.fld ? "true" : "false";
                """;
        String src2 =
                """
                struct S { S? fld; };
                
                val ptr = arg == 1 ? null : new S{fld = arg==1 ? null : new S{fld = null;};};
                if( ptr && ptr.fld ) {
                  sys.io.p("true");
                } else {
                  sys.io.p("false");
                }
                
                return 0;
                """;
        CodeGen code = new CodeGen(src).parse().opto().typeCheck();
        assertEquals("[97-117][ 116,114,117,101]", Eval2.eval(code, 0));

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build("ptrand",src2, 1, 8, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);
        assertEquals("false",R5._stdout.toString());

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("ptrand", src2, 1, 8, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
        assertEquals("false",arm._stdout.toString());
    }

    // conditional side effect
    @Test
    public void testCondSideEffAnd() throws IOException {
        String src =
                """
                int a = 1;
                int b = 1;
                
                int x=1;
                int y=1;
                int z=0;
                
                int g = x++ && y++ && z++;
                
                if(x == 2 && y == 2 && z == 1 && g == 0) {
                    sys.io.p("Effected");
                } else {
                    sys.io.p("Not effected");
                }
                return 0;
                """;

        TestC.runSF("and2", src, null, "Effected", 0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build("and2", src, 0, 2, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);
        assertEquals("Effected",R5._stdout.toString());

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("and2", src, 0, 2, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
        assertEquals("Effected",arm._stdout.toString());
    }

    @Test
    public void testCondSideEffOr() throws IOException {
        String src =
                """
                int a = 1;
                int b = 1;
                
                int x = -1;
                int y=1;
                int z= -1;
                
                int g = x++ || y++ || z++;
                
                int switch = 0;
                if(x == 4 || y == 4 || z == 4) {
                    switch = -1;
                } else {
                    switch = 1;
                }
                int cd = 1;
                if((x == 0 && y == 1 && z==-1) && switch && g) {
                    sys.io.p("Effected");
                } else {
                    sys.io.p("Not effected");
                }
                return 0;
                """;
        TestC.runSF("or2", src, null, "Effected", 0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build("or2", src, 0, 2, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);
        assertEquals("Effected",R5._stdout.toString());

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("or2", src, 0, 2, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
        assertEquals("Effected",arm._stdout.toString());
    }

    // test it with function calls
    @Test
    public void testFuncCall() throws IOException {
        String src =
                """
                // -*- mode: java;  -*-
                int a = 1;
                int b = 1;
                
                var sq = { int x ->
                    x*x;
                };
                
                if(a && sq(0)) {
                    sys.io.p("And");
                } else {
                    sys.io.p("Or");
                }
                return 0;
                """;
        TestC.runSF("and3", src, null, "Or", 0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build("and3", src, 0, 2, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);
        assertEquals("Or",R5._stdout.toString());

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("and3", src, 0, 2, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
        assertEquals("Or",arm._stdout.toString());
    }


    @Test
    public void testFRefFields() {
        String src =
"""
// A hypothetical scanner class
struct Scan {
    int !x;
    u8[~] buf;
    // Skip whitespace
    val skip = { Scan s ->
        while( s.buf[s.x] <= ' ' )
            s.x++;
    };
    // Peek a character; if matched consume it, else false.
    val peek = { Scan s, u8 c ->
        skip(s);
        if( s.buf[s.x] != c ) return false;
        s.x++;
        return true;
    };
};
Scan !s = new Scan{ buf = "  q"; };
return Scan.peek(s,'q');
        """;

        CodeGen code = new CodeGen(src).parse().opto().typeCheck();
        assertEquals("return Phi(Region,0,1);", code._stop.toString());
        assertEquals("1", Eval2.eval(code, 0));
        testCPU(src,"x86_64_v2", "Win64"  ,17,null);
    };

    @Test
    public void testFRefFields2() {
        String src =
"""
// A hypothetical scanner class
struct Scan {
    int !x;
    u8[~] buf;
    // Skip whitespace
    val skip = { ->
        while( buf[x] <= ' ' )
            x++;
        return self;
    };
};
val s = new Scan{ buf = "  q"; };
return s.skip().x;
        """;

        try { new CodeGen(src).parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Argument #0 isa *Scan {i64 x; *[]u8 buf; { *Scan -> *Scan {i64 !x; *[]u8 buf; {21} skip; } #21} skip; }, but must be a *Scan {i64 !x; *[]u8 buf; ... }",e.getMessage()); }
    };


    @Test
    public void testMethod() {
        String src =
"""
// A hypothetical scanner class
struct Scan {
    int !x;
    u8[~] buf;
    // Skip whitespace
    val skip = { ->
        while( buf[x] <= ' ' )
            x++;
    };
    val require = { u8 ch ->
        skip();
        buf[x++]==ch;
    };
};
Scan !s = new Scan{ buf = "  [1,2]"; };
return s.require('[');
        """;

        CodeGen code = new CodeGen(src).parse().opto().typeCheck();
        assertEquals("return (.[]==91);", code._stop.toString());
        assertEquals("1", Eval2.eval(code, 0));
        testCPU(src,"x86_64_v2", "Win64"  ,16,null);
    };


}
