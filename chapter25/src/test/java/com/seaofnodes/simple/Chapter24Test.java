package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter24Test {

    @Test
    public void testJig() throws IOException {
        String src = "int v0=0;  v0 = arg && 0== 0 !=0;";
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

    // test it with function calls
    @Test
    public void testStack1() throws IOException {
        String src ="""
int something_specific = 11;
return (10 < something_specific < 20)
    ? 0  // Expected
    : 1; // Error
""";
        TestC.runSF("stacked_r_1", src, null, "", 0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src, "stacked_r_1", 0, 0, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("stacked_r_1", src, 0, 0, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
    }

    @Test
    public void testStack3() throws IOException {
        String src ="""
var sq_noInline = { int x ->
    x*x;
};
int score = 4;
return (sq_noInline(2) <= score < sq_noInline(3))
    ? 0  // Expected
    : 1; // Error
""";
        TestC.runSF("stacked_r_3", src, null, "", 3);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src, "stacked_r_3", 0, 5, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("stacked_r_3", src, 0, 5, false);
        int trap_1 = arm.step(100);
        assertEquals(0,trap_1);
        assertEquals(0,arm.regs[0]);
    }

    @Test
    public void testStack4() throws IOException {
        String src ="""
int something_specific = 9;
return (10 < something_specific < 20)
    ? 1   // Error
    : 0;  // Expected
""";

        TestC.runSF("stacked_r_4", src, null, "", 0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src, "stacked_r_4", 0, 0, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("stacked_r_4", src, 0, 0, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
    }

    @Test
    public void testStack6() {
        try { new CodeGen("return 0 < arg > 1;" ).parse(); fail(); }
        catch( RuntimeException e ) { assertEquals("Mixing relational directions in a chained relational test",e.getMessage()); }
    }

    @Test
    public void testStack9() throws IOException {
        String src  = "var stack9 = { int x ->  (x < 1) == 1; };";
        String src2 = "return (0 < 1) == 1;";
        TestC.run(src, "stacked_r_9x", null, "1", 0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src2, "stacked_r_9", 0, 0, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(1,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("stacked_r_9", src2, 0, 0, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(1,arm.regs[0]);
    }

    @Test
    public void testStack10() throws IOException {
        String src ="var stack10 = { int x -> (x < 1) == (1>x);};";
        String src2 ="return (0 < 1) == (1>0);";
        TestC.run(src, "stacked_r_10x", null, "1",0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src2, "stacked_r_10", 0, 0, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(1,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("stacked_r_10", src2,0, 0, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(1,arm.regs[0]);
    }

    @Test
    public void testStack11() throws IOException {
        String src  = "var stack11 = { int x ->  0 < x < x+1 < 4;}; ";
        String src2 = "return 0 < arg < arg+1 < 4;";
        TestC.run(src, "stacked_r_11x", null, "1", 2);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src2, "stacked_r_11", 1, 0, false);
        R5.regs[riscv.A1] = 1; // Arg in A1, Test.class in A0
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(1,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("stacked_r_11", src2, 1, 0, false);
        arm.regs[1] = 1; // Arg in r1, Test.class in r0
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(1,arm.regs[0]);
    }

    // shows that it is parsed as
    // ((0 != x) != 2)
    @Test
    public void testStack12() throws IOException {
        String src  = "var stack12 = { int x -> 0 != x != 2; };";
        String src2 = "return 0 != arg != 2;";
        TestC.run(src, "stacked_r_12x", null, "1",0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src2, "stacked_r_12", 1, 0, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(1,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("stacked_r_12", src2, 1, 0, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(1,arm.regs[0]);
    }


    // shows that it is parsed as
    // ((0 == x) == 1)
    @Test
    public void testStack13() throws IOException {
        String src  = "var stack13 = { int x -> 0 == x == 1; };";
        String src2 = "return 0 == arg == 1;";
        TestC.run(src, "stacked_r_13x", null, "1",0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src2, "stacked_r_13", 1, 0, false);
        R5.regs[riscv.A1] = 1; // Arg in A1, Test.class in A0
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(1,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("stacked_r_13", src2, 1, 0, false);
        arm.regs[1] = 1; // Arg in r1, Test.class in r0
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(1,arm.regs[0]);
    }

    @Test
    public void testSCCP0() {
        String src =
"""
int x = 1;
int cnt = 5;
while (cnt) {
  cnt -= 1;
  if (x == 0) {
    x += 1;
  }
}
return x;
""";
        CodeGen code = new CodeGen(src).parse().opto().typeCheck();
        assertEquals("return 1;", code._stop.toString());
    }

    @Test
    public void testSCCP1() {
        String src =
"""
int x = 1;
int cnt = 5;
while( cnt-- )
  if( x == 1 )
    x = 2-x;
return x;
""";
        CodeGen code = new CodeGen(src).parse().opto().typeCheck();
        assertEquals("return 1;", code._stop.toString());
    }

}
