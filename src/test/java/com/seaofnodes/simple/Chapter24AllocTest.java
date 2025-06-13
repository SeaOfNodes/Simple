package com.seaofnodes.simple;

import java.io.IOException;
import java.nio.file.*;
import com.seaofnodes.simple.node.cpus.arm.arm;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import org.junit.Test;
import static org.junit.Assert.*;

// Revised inherited programs belong to the chapter which changed their inputs.
public class Chapter24AllocTest {
    @Test
    public void testNewtonInteger() {
        String src =
"""
// Newtons approximation to the square root
val sqrt = { int x ->
    int guess = x;
    while( 1 ) {
        int next = (x/guess + guess)/2;
        if( next == guess ) return guess;
        guess = next;
    }
};
int cast_int = arg+2;
return sqrt(arg) + sqrt(cast_int);
""";
        Chapter24Test.testCPU(src,"x86_64_v2", "Win64"  ,48,null);
        Chapter24Test.testCPU(src,"riscv"    , "SystemV",19,null);
        Chapter24Test.testCPU(src,"arm"      , "SystemV",19,null);
    }

    @Test
    public void testJig21() throws IOException {
        String src =
"""
struct s0 {
    bool v1;
    i16 v2;
    int v3;
    i8 v4;
    byte v5;
};
while(new s0.v3)
    while(new s0.v5<<new s0.v4) {}
if(0) {
    if(0) {
        flt !P5ZUD4=new s0.v2;
    }
    while(0) {}
}
return new s0.v1;
""";
        Chapter24Test.testCPU(src,"x86_64_v2", "Win64"  ,-1,null);
        Chapter24Test.testCPU(src,"riscv"    , "SystemV",-1,null);
        Chapter24Test.testCPU(src,"arm"      , "SystemV",-1,null);
    }

    @Test public void testStackArguments() throws IOException {
        // Test passes more args than registers in Sys5, which is far far more
        // than what Win64 allows - so Win64 gets a lot more spills here.
        String src =
"""
val addAll = { int i0, flt f1, int i2, flt f3, int i4, flt f5, int i6, flt f7, int x8, flt f9, int i10, flt f11, int i12, flt f13, int i14, flt f15, int x16, flt f17 int x18, flt f19 ->
    return
    i0 + f1+ i2+ f3+ i4+ f5+ i6+ f7+ x8 +f9 +
    i10+f11+i12+f13+i14+f15+x16+f17+x18+f19 ;
};
""";
        String arg_count = "191.000000\n";

        TestC.run(src, "arg_count", null, arg_count, TestC.CALL_CONVENTION.equals("Win64") ? 32 : 9);

        EvalRisc5 R5 = TestRisc5.build("no_stack_arg_count", src, 0, 4, false);

        // Todo: handle stack(imaginary stack in emulator)
        // pass in float arguments
        R5.fregs[riscv.FA0 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA1 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA2 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA3 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA4 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA5 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA6 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA7 - riscv.F_OFFSET] = 1.1;

        // a0 is passed in arg
        R5.regs[riscv.A1] = 2;
        R5.regs[riscv.A2] = 2;
        R5.regs[riscv.A3] = 2;
        R5.regs[riscv.A4] = 2;
        R5.regs[riscv.A5] = 2;
        R5.regs[riscv.A6] = 2;
        R5.regs[riscv.A7] = 2;

        int trap = R5.step(100);
        assertEquals(0,trap);

        double result = R5.fregs[riscv.FA0 - riscv.F_OFFSET];

        assertEquals(22.8, result, 0.00001);

        // arm
        EvalArm64 A5 = TestArm64.build("no_stack_arg_count", src, 0, 4, false);

        A5.fregs[arm.D0 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D1 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D2 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D3 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D4 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D5 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D6 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D7 - arm.D_OFFSET] = 1.1;

        A5.regs[arm.X1]  = 2;
        A5.regs[arm.X2]  = 2;
        A5.regs[arm.X3]  = 2;
        A5.regs[arm.X4]  = 2;
        A5.regs[arm.X5]  = 2;
        A5.regs[arm.X6]  = 2;
        A5.regs[arm.X7]  = 2;

        int trap_arm = A5.step(100);
        assertEquals(0,trap_arm);

        double result1 = A5.fregs[arm.D0 - arm.D_OFFSET];
        assertEquals(22.8, result1, 0.00001);
    }

    @Test
    public void testJig22() throws IOException {
        String src =
"""
val fib = {int n ->
    int temp=0;
    int f1=1;
    int f2=1;
    int i=n;
    while( i>1 ){
        temp = f1+f2;
        f1=f2;
        f2=temp;
        i=i-1;
    }
    return f2;
};

fib(10);
""";
        Chapter24Test.testCPU(src,"x86_64_v2", "Win64"  ,-1,null);
        Chapter24Test.testCPU(src,"riscv"    , "SystemV",-1,null);
        Chapter24Test.testCPU(src,"arm"      , "SystemV",-1,null);
    }

    @Test
    public void testBubbleSort() throws IOException {
        //String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/jig.smp"));
        String src = Files.readString(Path.of("docs/examples/BubbleSort.smp"));
        Chapter24Test.testCPU(src,"x86_64_v2", "Win64"  ,-1,null);
        Chapter24Test.testCPU(src,"riscv"    , "SystemV",-1,null);
        Chapter24Test.testCPU(src,"arm"      , "SystemV",-1,null);
    }
}
