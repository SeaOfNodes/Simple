package com.seaofnodes.simple;

import com.seaofnodes.isa.eval.EvalRisc5;

import com.seaofnodes.isa.eval.EvalArm64;

import com.seaofnodes.simple.codegen.RegAllocTestSupport.CheckedCodeGen;
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

    @Test public void testNarrowCReturnsEmulated() {
        String[] types = {"i8","u8","i16","u16","i32","u32","int"};
        long[] values = {0xa5a5a5a500000000L,0xa5a5a5a50000007fL,0xa5a5a5a500000080L,
            0xa5a5a5a500007fffL,0xa5a5a5a500008000L,0xa5a5a5a57fffffffL,
            0xa5a5a5a580000000L,0xa5a5a5a5ffffffffL};
        for( String target : new String[]{"arm","riscv"} )
            for( int i=0; i<types.length; i++ ) {
                String src = "{"+types[i]+"} c_narrow=\"C\"; return c_narrow();";
                CodeGen code = new CheckedCodeGen(src).driver(CodeGen.Phase.Encoding,target,"SystemV");
                var enc = code._encoding;
                // Link the external call to a tiny independent native stub.
                var call = code._stop.walk(n -> n instanceof com.seaofnodes.simple.node.CallNode ? n : null);
                int start = enc._opStart[call._nid];
                ((com.seaofnodes.simple.codegen.RIPRelSize)call).patch(enc,start,enc._opLen[call._nid],0x1000-start);
                for( long value : values ) {
                    long expected = switch(i) {
                    case 0 -> (byte)value; case 1 -> value&255;
                    case 2 -> (short)value; case 3 -> value&65535;
                    case 4 -> (int)value; case 5 -> value&0xffffffffL;
                    default -> value;
                    };
                    byte[] image = new byte[1<<16];
                    byte[] bits = enc.bits();
                    System.arraycopy(bits,0,image,0,bits.length);
                    if( target.equals("arm") ) {
                        EvalArm64 cpu = new EvalArm64(image,1<<15);
                        // MOVZ X0,0x1100; LDR X0,[X0]; RET X30.
                        cpu._pc = enc._opStart[code.link(code._main)._nid];
                        cpu.st4(0x1000,0xd2822000); cpu.st4(0x1004,0xf9400000);
                        cpu.st4(0x1008,0xd65f03c0);
                        cpu.st8(0x1100,value);
                        assertEquals(0,cpu.step(1000));
                        assertEquals(0,cpu._pc);
                        assertEquals(types[i],expected,cpu.regs[0]);
                    } else {
                        EvalRisc5 cpu = new EvalRisc5(image,1<<15);
                        // AUIPC a0,0; LD a0,12(a0); RET.
                        cpu._pc = enc._opStart[code.link(code._main)._nid];
                        cpu.st4(0x1000,0x00000517); cpu.st4(0x1004,0x00c53503);
                        cpu.st4(0x1008,0x00008067);
                        // RV64 widens even u32 returns by sign-extending bit 31.
                        cpu.st8(0x100c,i==6 ? value : (int)expected);
                        assertEquals(0,cpu.step(1000));
                        assertEquals(0,cpu._pc);
                        assertEquals(types[i],expected,cpu.regs[riscv.A0]);
                    }
                }
            }
    }

    @Test public void testNarrowCReturns() throws IOException {
        String src = """
            {i8} c_i8="C"; {u8} c_u8="C";
            {i16} c_i16="C"; {u16} c_u16="C";
            {i32} c_i32="C"; {u32} c_u32="C";
            {int} c_i64="C";
            val test_i8={->return c_i8();};
            val test_u8={->return c_u8();};
            val test_i16={->return c_i16();};
            val test_u16={->return c_u16();};
            val test_i32={->return c_i32();};
            val test_u32={->return c_u32();};
            val test_i64={->return c_i64();};
            val test_cmp={->return c_i32()==-1;};
            """;
        for( String conv : new String[]{"SystemV","Win64"} )
            TestC.run(src,conv,com.seaofnodes.simple.type.TypeInteger.BOT,conv.equals("SystemV") ? "sysv_abi" : "ms_abi",
                      "src/test/java/com/seaofnodes/simple/progs/c_returns.c",
                      "build/objs/c_returns"+conv,"S","",-1);
    }



    @Test public void testReturnedFunctions() throws IOException {
        for( String src : new String[]{"return {->42;};", "val f={->42;}; return f;", "return sys.io.p;"} )
            for( String target : new String[]{"riscv","arm"} ) {
                CodeGen code = new CheckedCodeGen(src).driver(CodeGen.Phase.Encoding,target,"SystemV").exportELF(null);
                byte[] image = new byte[1<<16];
                byte[] bits = code._encoding.bits();
                System.arraycopy(bits,0,image,0,bits.length);
                boolean library = src.contains("sys.io.p");
                // u8[]: four-byte length followed by its bytes.
                image[0x2000]=2; image[0x2004]='o'; image[0x2005]='k';
                int entry = code._encoding._opStart[code.link(code._main)._nid];
                if( target.equals("riscv") ) {
                    EvalRisc5 cpu = new EvalRisc5(image,1<<15);
                    cpu._pc = entry;
                    assertEquals(0,cpu.step(1000));
                    assertEquals(0,cpu._pc);
                    cpu._pc = (int)cpu.regs[riscv.A0]; // Call the returned address.
                    cpu.regs[library ? riscv.A1 : riscv.A0] = 0x2000; // Methods also receive self.
                    assertEquals(0,cpu.step(1000));
                    assertEquals(0,cpu._pc);
                    if( library ) assertEquals("ok",cpu._stdout.toString());
                    else assertEquals(42,cpu.regs[riscv.A0]);
                } else {
                    EvalArm64 cpu = new EvalArm64(image,1<<15);
                    cpu._pc = entry;
                    assertEquals(0,cpu.step(1000));
                    assertEquals(0,cpu._pc);
                    cpu._pc = (int)cpu.regs[0];
                    cpu.regs[library ? 1 : 0] = 0x2000;
                    assertEquals(0,cpu.step(1000));
                    assertEquals(0,cpu._pc);
                    if( library ) assertEquals("ok",cpu._stdout.toString());
                    else assertEquals(42,cpu.regs[0]);
                }
            }
        CodeGen unused = new CodeGen("({->42;}); return 0;").parse().opto();
        assertEquals(1,unused._stop.nIns());
    }

    @Test public void testReturnedFunctionsNative() throws IOException {
        String src = "val factory={int x -> if(x) return {->42;}; return {->43;};}; val printer={->return sys.io.p;};";
        String obj = "build/objs/funptr.o";
        new CheckedCodeGen(src).driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,obj);
        assertEquals("ok",TestC.gcc(obj,"","src/test/java/com/seaofnodes/simple/progs/funptr.c",false,
                                   "build/objs/funptr"+(TestC.OS.startsWith("Windows") ? ".exe" : "")));
    }


    @Test public void testRiscvRightShifts() {
        // SRAI, SRLI, SRA, SRL: a0 = -32 shifted by 4.
        int[] ops = {0x40455513,0x00455513,0x40b55533,0x00b55533};
        for( int i=0; i<ops.length; i++ ) {
            EvalRisc5 r5 = new EvalRisc5(new byte[1<<16],1<<15);
            r5.st4(0,ops[i]);
            r5.regs[riscv.A0] = -32;
            r5.regs[riscv.A0+1] = 4;
            assertEquals(0,r5.step(1));
            assertEquals((i&1)==0 ? -2L : 0x0fff_ffff_ffff_fffeL,r5.regs[riscv.A0]);
        }
    }

    @Test public void testRiscvPointerRelocation() {
        CodeGen code = new CheckedCodeGen("return \"x\";").driver(CodeGen.Phase.Encoding,"riscv","SystemV");
        var enc = code._encoding;
        assertEquals(1,enc._bigCons.size());
        var ptr = (com.seaofnodes.simple.node.cpus.riscv.TMPRISC)enc._bigCons.keySet().iterator().next();
        int start = enc._opStart[ptr._nid];
        for( int delta : new int[]{0x7ff,0x800,0xfff,0x1000,0x1800,-1,-0x800,-0x801} ) {
            ptr.patch(enc,start,enc._opLen[ptr._nid],delta);
            // Place the program above zero so backward targets are valid addresses.
            byte[] image = new byte[1<<16];
            System.arraycopy(enc.bits(),0,image,0x4000,enc._bits.size());
            EvalRisc5 r5 = new EvalRisc5(image,1<<15);
            r5._pc = 0x4000;
            assertEquals(0,r5.step(100));
            assertEquals(0x4000+start+delta,r5.regs[riscv.A0]);
        }
    }

    @Test public void testSubZeroTypeError() {
        try {
            new CodeGen("return null-0;").parse().opto().typeCheck();
            fail("Subtraction must reject null even when the other operand is zero");
        } catch( Parser.ParseException e ) {
            assertEquals("Cannot '-' null",e.getMessage());
        }
    }

    @Test public void testInfiniteReturn() {
        String src = "struct S { int i; }; !S !s = new S; while(1) s.i++; return s.i;";
        testCPU(src,"x86_64_v2","SystemV",0,"return Top;");
        testCPU(src,"riscv","SystemV",0,"return Top;");
        testCPU(src,"arm","SystemV",0,"return Top;");
    }


    // Frozen Jig runs in Chapter23AllocTest; the revised input is in Chapter24AllocTest.
    @Test @Ignore
    public void testJig() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/jig.smp"));
        //String src = Files.readString(Path.of("docs/examples/BubbleSort.smp"));
        testCPU(src,"x86_64_v2", "Win64"  ,-1,null);
        testCPU(src,"riscv"    , "SystemV",-1,null);
        testCPU(src,"arm"      , "SystemV",-1,null);
    }

    static CodeGen testCPU( String src, String cpu, String os, int spills, String stop ) {
        CodeGen code = new CheckedCodeGen(src).driver(CodeGen.Phase.Encoding,cpu,os);
        SpillStats.record(code,"Chapter22",cpu,os);
        SpillStats.checkSpills(spills,code._regAlloc._spillScaled);
        if( stop != null )
            assertEquals(stop, code._stop.toString());
        return code;
    }


    static int testCPUSize( String src, String cpu, String os, int spills, String stop ) {
        return testCPU(src,cpu,os,spills,stop)._encoding._bits.size();
    }

    // Should not fold away
    @Test public void testSextFail() throws IOException {
        String src = """
struct Person { i32 age;};
!Person !p = new Person;
p.age = (arg<<17)>>17;
return 0;
""";
        assertEquals(46, testCPUSize(src, "x86_64_v2","Win64",2,"return 0;"));
        assertEquals(60, testCPUSize(src, "riscv","SystemV",4,"return 0;"));
        assertEquals(56, testCPUSize(src, "arm","SystemV",4,"return 0;"));

        // do assertEquals here
        EvalRisc5 R5 = TestRisc5.build("sext_str_not_fold_away", src,0, 4, false);
        int trap = R5.step(100);
        assertEquals(0,trap);

        EvalArm64 A5 = TestArm64.build("sext_str_not_fold_away", src, 0, 4, false);
        trap = A5.step(100);
        assertEquals(0,trap);

    }

    // Should not fold away
    @Test public void testSextFail2() throws IOException {
        String src = """

                struct Person { i32 age;};
                !Person !p = new Person;
                p.age = (arg<<48)>>48;
                return 0;
        """;

        EvalRisc5 R5 = TestRisc5.build("sext_str_not_fold_away_2", src, 0, 4, false);
        int trap = R5.step(100);
        assertEquals(0,trap);

        EvalArm64 A5 = TestArm64.build("sext_str_not_fold_away_2", src, 0, 4, false);
        int trap_arm = A5.step(100);
        assertEquals(0,trap_arm);

        assertEquals(46, testCPUSize(src, "x86_64_v2","Win64",2,"return 0;"));
        assertEquals(60, testCPUSize(src, "riscv","SystemV",4,"return 0;"));
        assertEquals(56, testCPUSize(src, "arm","SystemV",4,"return 0;"));

    }

    // Should fold away
    @Test public void testSextSuccess() throws IOException {
        String src = """
// Should fold away sign extend
struct Person { i8 age;};
!Person !p = new Person;
p.age = (arg<<48)>>48;
return 0;
       """;

        EvalRisc5 R5 = TestRisc5.build("sext_str_fold_away", src, 0, 5, false);
        int trap = R5.step(100);
        assertEquals(0,trap);

        EvalArm64 A5 = TestArm64.build("sext_str_fold_away", src, 0, 5, false);
        int trap_arm = A5.step(100);
        assertEquals(0,trap_arm);

        assertEquals(41, testCPUSize(src, "x86_64_v2","Win64",2,"return 0;"));
        assertEquals(56, testCPUSize(src, "riscv","SystemV",5,"return 0;"));
        assertEquals(52, testCPUSize(src, "arm","SystemV",5,"return 0;"));
        // do assertEquals here
    }


    // Int now is changed to 4 bytes.
    @Test public void testPerson() throws IOException {
        String src =
"""
struct Person {
    i32 age;
};

val fcn = { !Person?[] !ps, int x ->
    if( ps[x] )
        ps[x].age++;
};
""";
        String person = "6\n";
        TestC.run(src, "person", null, person, 0);

        // Memory layout starting at PS:
        int ps = 1<<16;         // Person array pointer starts at heap start
        // Person[3] = { len,pad,P0,P1,P2 }; // sizeof = 4*8
        // P0 = { age } // sizeof=8
        int p0 = ps+4*8+0*8;
        // P1 = { age } // sizeof=8
        int p1 = ps+4*8+1*8;
        // P2 = { age } // sizeof=8
        int p2 = ps+4*8+2*8;
        EvalRisc5 R5 = TestRisc5.build("person", src, ps, 0, false);
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

        EvalArm64 A5 = TestArm64.build("person", src, ps, 0, false);
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
struct A { !B? !b; !C? !c; i64 ax; val az = x*2; };
struct B { !A? !a; !C? !c; f32 bx; val bz = x*3; };
struct C { !A? !a; !B? !b; f64 cx; val cz = x*x; };
!A !aa = new A{ ax=17; };
!B !bb = new B{ bx=3.14; a = aa; };
!C !cc = new C{ cx=2.73; a = aa; b = bb; };
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
        String src =
"""
sys.io.p("Hello, World!");
return 0;
""";
        TestC.run(src,TestC.CALL_CONVENTION,null, null,null,"build/objs/helloWorld","","Hello, World!",0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build("helloWorld", src, 0, 2, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);
        assertEquals("Hello, World!",R5._stdout.toString());

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("helloWorld", src,0, 2, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
        assertEquals("Hello, World!",arm._stdout.toString());
    }

    @Test
    public void testFinalArray() {
        String src = """
int N=4;
i32[] !is = new i32[N];
for( int i=0; i<N; i++ )
    is[i] = i*i;
val sum = { i32[~] is ->  // final array
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
        String src =
"""
// Echo stdin to stdout.
return sys.io.p( sys.io.stdin() );
""";
        TestC.run(src,TestC.CALL_CONVENTION,null, null,null,"build/objs/echo","","",0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build("echo", src, 0, 2, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("echo", src, 0, 2, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
    }


}
