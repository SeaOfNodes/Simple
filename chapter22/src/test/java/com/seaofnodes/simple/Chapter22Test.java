package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.seaofnodes.simple.node.cpus.arm.arm;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter22Test {

    @Test @Ignore
    public void testJig() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/jig.smp"));
        testCPU(src,"x86_64_v2", "Win64"  ,-1,null);
        testCPU(src,"riscv"    , "SystemV",-1,null);
        testCPU(src,"arm"      , "SystemV",-1,null);
    }

    static void testCPU( String src, String cpu, String os, int spills, String stop ) {
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.Encoding,cpu,os);
        int delta = spills>>3;
        if( delta==0 ) delta = 1;
        if( spills != -1 )
            assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);
        if( stop != null )
            assertEquals(stop, code._stop.toString());
    }


    // Store opt wipes out SEXT shifts
    @Test public void testSext() throws IOException {
        EvalRisc5 R5 = TestRisc5.build("sext_str_2", 0, 4, true);
        int trap = R5.step(100);
        assertEquals(0,trap);
        // do assertEquals here
    }

    @Test public void testStorePeep() throws IOException{
        String src = """ 
                i64 a = 123456789012345;
                i32 b = a<<32>>>32; // truncate high order bits
                return b;
        """;
        testCPU(src,"riscv"    , "SystemV",0,"return ( -2045911040 + #-135 );");
        EvalRisc5 R5 = TestRisc5.build("store_peep", 0, 0, true);
        int trap = R5.step(100);
        assertEquals(0,trap);
    }
    // Int now is changed to 4 bytes.
    @Test public void testPerson() throws IOException {
        String person = "6\n";
        //TestC.run("person", person, 0);

        // Memory layout starting at PS:
        int ps = 1<<16;         // Person array pointer starts at heap start
        // Person[3] = { len,pad,P0,P1,P2 }; // sizeof = 4*8
        // P0 = { age } // sizeof=8
        int p0 = ps+4*8+0*8;
        // P1 = { age } // sizeof=8
        int p1 = ps+4*8+1*8;
        // P2 = { age } // sizeof=8
        int p2 = ps+4*8+2*8;
        EvalRisc5 R5 = TestRisc5.build("person", ps, 0, false);
        R5.regs[riscv.A1] = 1;  // Index 1
        R5.st8(ps,3);           // Length
        R5.st8(ps+1*8,p0);
        R5.st8(ps+2*8,p1);
        R5.st8(ps+3*8,p2);
        R5.st8(p0, 5); // age= 5
        R5.st8(p1,17); // age=17
        R5.st8(p2,60); // age=60

        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals( 5+0,R5.ld8(p0));
        assertEquals(17+1,R5.ld8(p1));
        assertEquals(60+0,R5.ld8(p2));

        EvalArm64 A5 = TestArm64.build("person", ps, 0, false);
        A5.regs[arm.X1] = 1;  // Index 1
        A5.st8(ps, 3);
        A5.st8(ps+1*8,p0);
        A5.st8(ps+2*8,p1);
        A5.st8(ps+3*8,p2);
        A5.st8(p0, 5); // age= 5
        A5.st8(p1,17); // age=17
        A5.st8(p2,60); // age=60

        int trap_arm = A5.step(100);
        assertEquals(0,trap_arm);
        assertEquals( 5+0, A5.ld8(p0));
        assertEquals(17+1, A5.ld8(p1));
        assertEquals(60+0, A5.ld8(p2));
    }

    @Test
    public void testCoRecur() {
        String src = """
struct A { B? b; C? c; i64 ax; val az = x*2; };
struct B { A? a; C? c; f32 bx; val bz = x*3; };
struct C { A? a; B? b; f64 cx; val cz = x*x; };
A !aa = new A{ ax=17; };
B !bb = new B{ bx=3.14; a = aa; };
C !cc = new C{ cx=2.73; a = aa; b = bb; };
aa.b = bb;
aa.c = cc;
bb.c = cc;
val x = 5; // aa.az; // Error to self-define forward ref
return cc.cz;
""";
        CodeGen code = new CodeGen(src).parse().opto().typeCheck();
        assertEquals("return 25;", code._stop.toString());
        assertEquals("25", Eval2.eval(code, 0));
    }

    @Test
    public void testHelloWorld() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/helloWorld.smp"));
        TestC.run(src,TestC.CALL_CONVENTION,null,null,"build/objs/helloWorld","","Hello, World!",0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build("helloWorld", 0, 2, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("helloWorld", 0, 2, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
    }

    @Test
    public void testFinalArray() {
        String src = """
int N=4;
i32[] !is = new i32[N];
for( int i=0; i<N; i++ )
    is[i] = i*i;
val sum = { i32[~] is ->
    int sum=0;
    for( int i=0; i<is#; i++ )
        sum += is[i];
    return sum;
};
return sum(is);
""";
        CodeGen code = new CodeGen(src).parse().opto().typeCheck();
        assertEquals("return Phi(Loop,0,(Phi_sum+.[]));", code._stop.toString());
        assertEquals("14", Eval2.eval(code, 0));
    }


    @Test @Ignore
    public void testEcho() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/echo.smp"));
        TestC.run(src,TestC.CALL_CONVENTION,null,null,"build/objs/echo","","",0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build("echo", 0, 2, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("echo", 0, 2, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
    }


}
