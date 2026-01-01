package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter23Test {

    @Test @Ignore
    public void testJig() throws IOException {
        //String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/jig.smp"));
        String src = Files.readString(Path.of("docs/examples/BubbleSort.smp"));
        testCPU(src,"x86_64_v2", "Win64"  ,-1,null);
        testCPU(src,"riscv"    , "SystemV",-1,null);
        testCPU(src,"arm"      , "SystemV",-1,null);
    }


    static CodeGen testCPU( String src, String cpu, String os, int spills, String stop ) {
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.Encoding,cpu,os);
        int delta = spills>>3;
        if( delta==0 ) delta = 1;
        if( spills != -1 )
            assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);
        if( stop != null )
            assertEquals(stop, code._stop.toString());
        return code;
    }

    @Test
    public void testOr() throws IOException {
        String src = """
int a = 1;
int b = 0;
int rez = 2;

if( a++ || b++ ) {
    if( b == 0 && a == 2 )
        rez = 0; // Expected answer
} else {
    rez = 1;
}
return rez;
""";

        TestC.runSF("or1", src, null, "", 0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src, "or1", 0, 0, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("or1", src,0, 0, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
    }

    @Test
    public void testAnd() throws IOException {
        String src = """
int a = 1;
int b = 1;
return a && b ? 0 : 1;  // Expected answer 0
""";
        TestC.runSF("and1", src, null, "", 0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src, "and1", 0, 0, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("and1", src, 0, 0, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
    }

    @Test
    public void testAndPtr() throws IOException {
        // Todo: have one src here
        String src = """
struct S { S? fld; };
val ptr = arg == 1 ? null : new S{fld = arg==1 ? null : new S{fld = null;};};
return ptr && ptr.fld ? "true" : "false";
""";
        CodeGen code = new CodeGen(src).parse().opto().typeCheck();
        assertEquals("[97-117][ 116,114,117,101]", Eval2.eval(code, 0));

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src, "ptrand", 1, 8, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        int adr = (int)R5.regs[riscv.A0]; // Returns a Simple *u8[~] string
        assertEquals(4,R5.ld4s(adr)); // String length
        assertEquals(0x65757274,R5.ld4s(adr+4)); // "true"

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("ptrand", src, 1, 8, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        int adr2 = (int)arm.regs[0]; // Returns a Simple *u8[~] string
        assertEquals(4,arm.ld4s(adr2)); // String length
        assertEquals(0x65757274,arm.ld4s(adr2+4)); // "true"
    }

    // conditional side effect
    @Test
    public void testCondSideEffAnd() throws IOException {
        String src = """
int x=1;
int y=1;
int z=0;

int g = x++ && y++ && z++;
return x == 2 && y == 2 && z == 1 && g == 0
    ? 0  // Effected, Expected
    : 1; // Error, not effected
""";

        TestC.runSF("and2", src, null, "", 0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src, "and2", 0, 0, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("and2", src, 0, 0, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
    }

    @Test
    public void testCondSideEffOr() throws IOException {
        String src = """
int x = -1;
int y =  1;
int z = -1;
int g = x++ || y++ || z++;
int switch = (x == 4 || y == 4 || z == 4) ? -1 : 1;
return (x == 0 && y == 1 && z==-1) && switch && g
    ? 0   // Effected, expected
    : 1;  // Error
""";
        TestC.runSF("or2", src, null, "", 0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src, "or2", 0, 0, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("or2", src, 0, 0, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
    }

    // test it with function calls
    @Test
    public void testFuncCall() throws IOException {
        String src = """
int a = 1;
int b = 1;
var sq_noInline = { int x ->
    x*x;
};
return (a && sq_noInline(0))
    ? 1  // Error
    : 0; // Expected
""";
        TestC.runSF("and3", src, null, "", 0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src, "and3", 0, 2, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("and3", src, 0, 2, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
    }


    @Test
    public void testFRefFields() {
        String src = """
// A hypothetical scanner class
struct _Scan {
    int !x;
    u8[~] buf;
    // Skip whitespace
    val skip = { ->
        while( buf[x] <= ' ' )
            x++;
    };
    // Peek a character; if matched consume it, else false.
    val peek = { u8 c ->
        skip();
        if( buf[x] != c ) return false;
        x++;
        return true;
    };
};
_Scan !_s = new _Scan{ buf = "  q"; };
return _s.peek('q');
""";

        CodeGen code = new CodeGen(src).parse().opto().typeCheck();
        assertEquals("Stop[ return 0; return Phi(Region,0,1); return #2; ]", code._stop.toString());
        assertEquals("1", Eval2.eval(code, 0));
        testCPU(src,"x86_64_v2", "Win64"  ,20,null);
    };

    @Test
    public void testFRefFields2() {
        String src ="""
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
        catch( Exception e ) {
            // Bad error message, but basically requires a mutable 'x' field.
            assertEquals("Argument #0 isa *Test.Scan {i64 x; *[]u8 buf; { *Test.Scan -> *Test.Scan {i64 !x; *[]u8 buf; {2} skip; } #2} skip; }, but must be a *Test.Scan {i64 !x; *[]u8 buf; { *Test.Scan -> *Test.Scan #2} skip; }",e.getMessage());
        }
    };


    @Test
    public void testMethod() {
        String src = """
// A hypothetical scanner class
struct _Scan {
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
_Scan !_s = new _Scan{ buf = "  [1,2]"; };
return _s.require('[');
""";

        CodeGen code = new CodeGen(src).parse().opto().typeCheck();
        assertEquals("Stop[ return 0; return (Parm_ch(require,u8)==.[]); return (.[]==91); ]", code._stop.toString());
        assertEquals("1", Eval2.eval(code, 0));
        testCPU(src,"x86_64_v2", "Win64", 32, null);
    };


}
