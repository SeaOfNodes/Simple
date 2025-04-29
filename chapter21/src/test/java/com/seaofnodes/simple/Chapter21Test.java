package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.arm.arm;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter21Test {

    @Test
    public void testJig() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/jig.smp"));
        CodeGen code = new CodeGen(src).driver("x86_64_v2","Win64","build/objs/jigS.o");
        //testCPU(src,"x86_64_v2", "Win64"  ,-1,null);
        //testCPU(src,"riscv"    , "SystemV",-1,null);
        //testCPU(src,"arm"      , "SystemV",-1,null);
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


    @Test public void testBasic1() {
        String src = "return arg | 2;";
        testCPU(src,"x86_64_v2", "SystemV",1,"return (ori,mov(arg));");
        testCPU(src,"riscv"    , "SystemV",0,"return ( arg | #2 );");
        testCPU(src,"arm"      , "SystemV",0,"return (ori,arg);");
    }

    @Test public void testInfinite() {
        String src = "struct S { int i; }; S !s = new S; while(1) s.i++;";
        testCPU(src,"x86_64_v2", "SystemV",0,"return Top;");
        testCPU(src,"riscv"    , "SystemV",2,"return Top;");
        testCPU(src,"arm"      , "SystemV",2,"return Top;");
    }

    @Test
    public void testArray1() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/array1.smp"));
        testCPU(src,"x86_64_v2", "SystemV",-1,"return .[];");
        testCPU(src,"riscv"    , "SystemV", 7,"return (add,.[],(mul,.[],1000));");
        testCPU(src,"arm"      , "SystemV", 5,"return (add,.[],(mul,.[],1000));");
    }

    @Test
    public void testAntiDeps1() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/antiDep1.smp"));
        testCPU(src,"x86_64_v2", "SystemV", 7,"return mov(mov(S));");
        testCPU(src,"riscv"    , "SystemV",10,"return mov(mov(S));");
        testCPU(src,"arm"      , "SystemV",10,"return mov(mov(S));");
    }

    @Test
    public void testString() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/stringHash.smp"));
        //testCPU(src,"x86_64_v2", "SystemV", 9,null);
        testCPU(src,"riscv"    , "SystemV", 5,null);
        testCPU(src,"arm"      , "SystemV", 6,null);
    }

    @Test public void testStringExport() throws IOException {
        TestC.run("stringHash", 9);
    }

    @Test public void testLoop2() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/loop2.smp"));
        testCPU(src,"x86_64_v2", "Win64"  ,0,"return (inc,Phi(Loop,0,inc));");
        testCPU(src,"riscv"    , "SystemV",0,"return ( Phi(Loop,0,addi) + #1 );");
        testCPU(src,"arm"      , "SystemV",0,"return (inc,Phi(Loop,0,inc));");
    }

    @Test public void testNewtonExport() throws IOException {
        String result = """
0  0.000000   (0)
1  1.000000   (0)
2  1.414214   (2.22045e-16)
3  1.732051   (0)
4  2.000000   (0)
5  2.236068   (0)
6  2.449490   (0)
7  2.645751   (0)
8  2.828427   (4.44089e-16)
9  3.000000   (0)
""";
        TestC.run("newtonFloat",result,34);

        EvalRisc5 R5 = TestRisc5.build("newtonFloat", 0, 10, false);
        R5.fregs[riscv.FA0 - riscv.F_OFFSET] = 3.0;
        int trap_r5 = R5.step(1000);
        assertEquals(0,trap_r5);
        // Return register A0 holds fib(8)==55
        assertEquals(1.732051,R5.fregs[riscv.FA0 - riscv.F_OFFSET], 0.00001);

        // arm
        EvalArm64 A5 = TestArm64.build("newtonFloat", 0, 10, false);
        A5.fregs[arm.D0 - arm.D_OFFSET] = 3.0;
        int trap_arm = A5.step(1000);
        assertEquals(0,trap_arm);
        assertEquals(1.732051, A5.fregs[arm.D0 - arm.D_OFFSET], 0.00001);
    }


    @Test public void testSieve() throws IOException {
        // The primes
        int[] primes = new int[]  { 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, };
        SB sb = new SB().p(primes.length).p("[");
        for( int prime : primes )
            sb.p(prime).p(", ");
        String sprimes = sb.p("]").toString();

        // Compile, link against native C; expect the above string of primes to be printed out by C
        TestC.run("sieve",sprimes, 257);

        // Evaluate on RISC5 emulator; expect return of an array of primes in
        // the simulated heap.
        EvalRisc5 R5 = TestRisc5.build("sieve", 100, 160, false);
        int trap = R5.step(10000);
        assertEquals(0,trap);
        // Return register A0 holds sieve(100)
        int ary = (int)R5.regs[riscv.A0];
        // Memory layout starting at ary(length,pad, prime1, primt2, prime3, prime4)
        assertEquals(primes.length, R5.ld4s(ary));
        for( int i=0; i<primes.length; i++ )
            assertEquals(primes[i], R5.ld4s(ary + 4 + i*4));

        // Evaluate on ARM5 emulator; expect return of an array of primes in
        // the simulated heap.
        EvalArm64 A5 = TestArm64.build("sieve", 100, 160, false);
        int trap_arm = A5.step(10000);
        assertEquals(0, trap_arm);
        int ary_arm = (int)A5.regs[arm.X0];
        // Memory layout starting at ary(length,pad, prime1, primt2, prime3, prime4)
        assertEquals(primes.length, A5.ld4s(ary_arm));
        for( int i = 0; i<primes.length; i++ )
            assertEquals(primes[i], A5.ld4s(ary_arm + 4 + i * 4));
    }

    @Test public void testFibExport() throws IOException {
        String fib = "[1, 1, 2, 3, 5, 8, 13, 21, 34, 55]";
        TestC.run("fib", fib, 24);

        EvalRisc5 R5 = TestRisc5.build("fib", 9, 16, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        // Return register A0 holds fib(8)==55
        assertEquals(55,R5.regs[riscv.A0]);

        // arm
        EvalArm64 A5 = TestArm64.build("fib", 9, 16, false);
        int trap_arm = A5.step(100);
        assertEquals(0,trap_arm);
        // Return register X0 holds fib(8)==55
        assertEquals(55, A5.regs[arm.X0]);
    }

    @Test public void testPerson() throws IOException {
        String person = "6\n";
        TestC.run("person", person, 0);

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
        R5.st4(ps,3);           // Length
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
        A5.st4(ps, 3);
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

    @Test public void testArgCount() throws IOException {
        // Test passes more args than registers in Sys5, which is far far more
        // than what Win64 allows - so Win64 gets a lot more spills here.
        String arg_count = "191.000000\n";
        TestC.run("arg_count", arg_count,
                  TestC.CALL_CONVENTION.equals("Win64") ? 42 : 15);


        EvalRisc5 R5 = TestRisc5.build("no_stack_arg_count", 0, 0, false);

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
        EvalArm64 A5 = TestArm64.build("no_stack_arg_count", 0, 0, false);

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
}
