package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.RegAllocTestSupport.CheckedCodeGen;
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
    @Test public void testArmFloatMemory() throws IOException {
        for( String type : new String[]{"f32","f64"} ) {
            String src = type+"[] !a=new "+type+"[3]; a[arg]=1.25; return a[1];";
            CodeGen code = new CodeGen(src).driver("arm","SystemV",null);
            byte[] image = new byte[1<<20];
            byte[] bits = code._encoding.bits();
            System.arraycopy(bits,0,image,0,bits.length);
            EvalArm64 cpu = new EvalArm64(image,1<<16);
            cpu.regs[arm.X0]=1;
            assertEquals(0,cpu.step(1000));
            assertEquals(1.25,cpu.fregs[0],0);
        }
        // Independently exercise both SIMD widths and addressing modes. Keep
        // the GPR value different so accidentally reading that bank is visible.
        for( boolean wide : new boolean[]{false,true} )
            for( boolean indexed : new boolean[]{false,true} ) {
                EvalArm64 cpu = new EvalArm64(new byte[512],256);
                cpu.regs[1]=128; cpu.regs[2]=16; cpu.regs[3]=99;
                cpu.fregs[3]=-13.25;
                int store = (wide ? 0xFD000000 : 0xBD000000) | ((16/(wide?8:4))<<10) | (1<<5) | 3;
                if( indexed ) store=(wide ? 0xFC226823 : 0xBC226823); // STR D3/S3,[X1,X2]
                cpu.st4(0,store);
                cpu.st4(4,store | (1<<22)); // LDR to the same SIMD register.
                assertEquals(0,cpu.step(1));
                assertEquals(-13.25,wide ? cpu.ld8f(144) : cpu.ld4f(144),0);
                cpu.fregs[3]=0;
                assertEquals(0,cpu.step(1));
                assertEquals(-13.25,cpu.fregs[3],0);
            }
    }


    @Test public void testArmCallInstructions() {
        EvalArm64 cpu = new EvalArm64(new byte[512],256);
        for( int delta : new int[]{4,32,-4} ) {
            cpu._pc = 128;
            for( int i=0; i<32; i++ ) cpu.regs[i]=1000+i;
            cpu.st4(128,0x94000000 | ((delta>>2)&0x03FFFFFF)); // BL
            assertEquals(0,cpu.step(1));
            assertEquals(128+delta,cpu._pc);
            for( int i=0; i<32; i++ ) assertEquals(i==30 ? 132 : 1000+i,cpu.regs[i]);
        }
        for( int rn : new int[]{9,30} )
            for( boolean call : new boolean[]{false,true} ) {
                cpu._pc=4;
                cpu.regs[rn]=64;
                cpu.st4(4,(call ? 0xD63F0000 : 0xD65F0000) | (rn<<5)); // BLR / RET
                assertEquals(0,cpu.step(1));
                assertEquals(64,cpu._pc);
                if( call ) assertEquals(8,cpu.regs[30]);
            }
    }

    @Test public void testArmImmediateArithmetic() {
        EvalArm64 cpu = new EvalArm64(new byte[16],16);
        // Unsigned imm12, including bit 11 and the optional 12-bit shift.
        for( int[] test : new int[][]{{0x91200020,2148},{0xD1200020,-1948},
                                     {0x91400420,4196},{0xD1400420,-3996}} ) {
            cpu._pc=0; cpu.regs[1]=100;
            cpu.N=true; cpu.Z=false; cpu.C=true; cpu.V=true;
            cpu.st4(0,test[0]);
            assertEquals(0,cpu.step(1));
            assertEquals(test[1],cpu.regs[0]);
            assertTrue(cpu.N); assertFalse(cpu.Z); assertTrue(cpu.C); assertTrue(cpu.V);
        }
    }

    @Test public void testArmCallsAndFrames() throws IOException {
        String src = """
            val sum = { int n ->
                if( n<=0 ) return 1;
                return sum(n-1)+n;
            };
            val run = { int n -> return sum(n)+sum(n+1); };
            """;
        assertTrue("Ordinary calls must survive optimization",checkArmCalls(src,"run",4,27)>0);
    }

    private static int checkArmCalls(String src, String entryName, int arg, int result) throws IOException {
        CodeGen code = new CodeGen(src).driver("arm","SystemV",null);
        byte[] image = new byte[1<<20];
        System.arraycopy(code._encoding.bits(),0,image,0,code._encoding.bits().length);
        EvalArm64 cpu = new EvalArm64(image,1<<16);
        boolean entry=false;
        int frames=0, calls=0;
        for( var bb : code._cfg ) {
            if( bb instanceof com.seaofnodes.simple.node.FunNode fun ) {
                int off = code._encoding._opStart[fun._nid];
                if( entryName.equals(fun._name) ) { cpu._pc=off; entry=true; }
                int frame=fun._frameAdjust;
                assertEquals(0,frame&15);
                if( frame>0 ) {
                    frames++;
                    assertEquals(0xD10003FF | (frame<<10),cpu.ld4s(off)); // SUB SP,SP,#bytes
                }
            }
            if( bb instanceof com.seaofnodes.simple.node.ReturnNode ret ) {
                int off=code._encoding._opStart[ret._nid];
                int frame=ret.fun()._frameAdjust;
                if( frame>0 ) {
                    assertEquals(0x910003FF | (frame<<10),cpu.ld4s(off)); // ADD SP,SP,#bytes
                    off+=4;
                }
                assertEquals(0xD65F03C0,cpu.ld4s(off)); // RET X30
            }
            if( bb instanceof com.seaofnodes.simple.node.cpus.arm.CallARM ) calls++;
        }
        assertTrue("Must execute the program entry",entry);
        assertTrue("Exercise stack frames",frames>0);
        for( int i=19; i<=29; i++ ) cpu.regs[i]=1000+i;
        cpu.regs[0]=arg;
        assertEquals(0,cpu.step(10000));
        assertEquals("Must return to the harness",0,cpu._pc);
        assertEquals(result,cpu.regs[0]);
        assertEquals(1<<16,cpu.regs[31]);
        for( int i=19; i<=29; i++ ) assertEquals(1000+i,cpu.regs[i]);
        return calls;
    }


    @Test public void testArmSubtractRegisters() {
        EvalArm64 cpu = new EvalArm64(new byte[16],16);
        cpu.st4(0,0xCB020020); // SUB X0,X1,X2, no shift or flag update.
        for( long[] pair : new long[][]{{7,3},{3,7},{Long.MIN_VALUE,1},{1L<<40,3}} ) {
            cpu._pc = 0;
            cpu.regs[1] = pair[0]; cpu.regs[2] = pair[1];
            cpu.N=true; cpu.Z=false; cpu.C=true; cpu.V=true;
            assertEquals(0,cpu.step(1));
            assertEquals(pair[0]-pair[1],cpu.regs[0]);
            assertTrue(cpu.N); assertFalse(cpu.Z); assertTrue(cpu.C); assertTrue(cpu.V);
        }
    }


    @Test public void testNarrowStores() throws IOException {
        for( String type : new String[]{"i8","u8","i16","u16"} ) {
            String src = "struct S { "+type+" x; }; S !s = new S; s.x = arg; return 0;";
            CodeGen code = new CodeGen(src).driver("riscv","SystemV",null);
            int stores=0;
            for( var bb : code._cfg )
                for( var node : bb.outs() )
                    if( node instanceof com.seaofnodes.simple.node.cpus.riscv.StoreRISC st ) {
                        stores++;
                        assertEquals("Byte/short stores have no FP encoding",-1,st.regmap(4).nextReg((short)31));
                    }
            assertEquals(1,stores);
            byte[] image = new byte[1<<20];
            byte[] bits = code._encoding.bits();
            System.arraycopy(bits,0,image,0,bits.length);
            EvalRisc5 cpu = new EvalRisc5(image,1<<16);
            cpu.regs[riscv.A0] = 0x8765;
            assertEquals(0,cpu.step(100));
            assertEquals(type.endsWith("8") ? 0x65 : 0x8765,
                         type.endsWith("8") ? cpu.ld1z(1<<16) : cpu.ld2z(1<<16));
            code = new CodeGen(src).driver(CodeGen.Phase.Encoding,"x86_64_v2",TestC.CALL_CONVENTION);
            stores=0;
            for( var bb : code._cfg )
                for( var node : bb.outs() )
                    if( node instanceof com.seaofnodes.simple.node.cpus.x86_64_v2.StoreX86 st ) {
                        stores++;
                        assertEquals("Byte/short stores have no XMM encoding",-1,st.regmap(4).nextReg((short)15));
                    }
            assertEquals(1,stores);
        }
    }

    @Test public void testNativeExitStatus() throws IOException {
        Path source = Path.of("build/objs/nativeExit.c");
        Files.createDirectories(source.getParent());
        Files.writeString(source,"int main() { return 7; }\n");
        try {
            TestC.gcc(source.toString(),null,null,false,
                      "build/objs/nativeExit"+(TestC.OS.startsWith("Windows") ? ".exe" : ""));
        } catch( AssertionError error ) {
            assertTrue(error.getMessage(),error.getMessage().contains("Program exit status"));
            return;
        }
        fail("A failing native program must fail the test, even with empty stdout");
    }

    @Test public void testCoalescing() { com.seaofnodes.simple.codegen.RegAllocTestSupport.coalescing(); }
    @Test public void testRisc64BitStore() {
        byte[] mem = new byte[24];
        EvalRisc5 cpu = new EvalRisc5(mem,mem.length);
        check64BitStore(mem,cpu::st8,cpu::ld8);
    }

    @Test public void testArm64BitStore() {
        byte[] mem = new byte[24];
        EvalArm64 cpu = new EvalArm64(mem,mem.length);
        check64BitStore(mem,cpu::st8,cpu::ld8);
    }

    // Check bytes independently of ld8, including overwrite and adjacent memory.
    private static void check64BitStore(byte[] mem,
                                       java.util.function.BiConsumer<Integer,Long> store,
                                       java.util.function.IntToLongFunction load) {
        java.util.Arrays.fill(mem,(byte)0xA5);
        for( long value : new long[]{0x0123456789ABCDEFL,0xFEDCBA9876543210L,
                                     -1L,0L,Long.MIN_VALUE,Long.MAX_VALUE} ) {
            store.accept(8,value);
            for( int i=0; i<mem.length; i++ ) {
                int expected = i>=8 && i<16 ? (int)(value >>> (8*(i-8)))&0xFF : 0xA5;
                assertEquals("byte "+i,expected,mem[i]&0xFF);
            }
            assertEquals(value,load.applyAsLong(8));
        }
    }


    @Test @Ignore
    public void testJig() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/jig.smp"));
        testCPU(src,"x86_64_v2", "Win64"  ,-1,null);
        testCPU(src,"riscv"    , "SystemV",-1,null);
        testCPU(src,"arm"      , "SystemV",-1,null);
    }

    static void testCPU( String src, String cpu, String os, int spills, String stop ) {
        CodeGen code = new CheckedCodeGen(src).driver(CodeGen.Phase.Encoding,cpu,os);
        SpillStats.record(code,"Chapter21",cpu,os);
        SpillStats.checkSpills(spills,code._regAlloc._spillScaled);
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
        testCPU(src,"x86_64_v2", "SystemV", 9,null);
        testCPU(src,"riscv"    , "SystemV", 3,null);
        testCPU(src,"arm"      , "SystemV", 3,null);
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
        TestC.run("sieve",sprimes, 178);

        // Evaluate on RISC5 emulator; expect return of an array of primes in
        // the simulated heap.
        EvalRisc5 R5 = TestRisc5.build("sieve", 100, 89, false);
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
        EvalArm64 A5 = TestArm64.build("sieve", 100, 93, false);
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
        TestC.run("person21", person, 0);

        // Memory layout starting at PS:
        int ps = 1<<16;         // Person array pointer starts at heap start
        // Person[3] = { len,pad,P0,P1,P2 }; // sizeof = 4*8
        // P0 = { age } // sizeof=8
        int p0 = ps+4*8+0*8;
        // P1 = { age } // sizeof=8
        int p1 = ps+4*8+1*8;
        // P2 = { age } // sizeof=8
        int p2 = ps+4*8+2*8;
        EvalRisc5 R5 = TestRisc5.build("person21", ps, 0, false);
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

        EvalArm64 A5 = TestArm64.build("person21", ps, 0, false);
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

    @Test public void testArgCount() throws IOException {
        // Test passes more args than registers in Sys5, which is far far more
        // than what Win64 allows - so Win64 gets a lot more spills here.
        String arg_count = "191.000000\n";
        TestC.run("arg_count", arg_count,
                  TestC.CALL_CONVENTION.equals("Win64") ? 32 : 9);


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
