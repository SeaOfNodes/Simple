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

    @Test
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


    // Int now is changed to 4 bytes.
    @Test public void testPerson() throws IOException {
        String person = "6\n";
        // Todo: need to fix it and switch to 4 byte pointers
        TestC.run("person", person, 0);

        // Memory layout starting at PS:
        int ps = 1<<16;         // Person array pointer starts at heap start
        // Person[3] = { len,pad,P0,P1,P2 }; // sizeof = 4*8
        // P0 = { age } // sizeof=4
        int p0 = ps+4*4+0*4;
        // P1 = { age } // sizeof=4
        int p1 = ps+4*4+1*4;
        // P2 = { age } // sizeof=4
        int p2 = ps+4*4+2*4;
        EvalRisc5 R5 = TestRisc5.build("person", ps, 0, false);
        R5.regs[riscv.A1] = 1;  // Index 1
        R5.st4(ps,3);           // Length
        R5.st4(ps+1*4,p0);
        R5.st4(ps+2*4,p1);
        R5.st4(ps+3*4,p2);
        R5.st4(p0, 5); // age= 5
        R5.st4(p1,17); // age=17
        R5.st4(p2,60); // age=60

        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals( 5+0,R5.ld4s(p0));
        assertEquals(17+1,R5.ld4s(p1));
        assertEquals(60+0,R5.ld4s(p2));

        EvalArm64 A5 = TestArm64.build("person", ps, 0, false);
        A5.regs[arm.X1] = 1;  // Index 1
        A5.st4(ps, 3);
        A5.st4(ps+1*4,p0);
        A5.st4(ps+2*4,p1);
        A5.st4(ps+3*4,p2);
        A5.st4(p0, 5); // age= 5
        A5.st4(p1,17); // age=17
        A5.st4(p2,60); // age=60

        int trap_arm = A5.step(100);
        assertEquals(0,trap_arm);
        assertEquals( 5+0, A5.ld4s(p0));
        assertEquals(17+1, A5.ld4s(p1));
        assertEquals(60+0, A5.ld4s(p2));
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

    @Test @Ignore
    public void testHelloWorld() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/helloWorld.smp"));
        TestC.run(src,TestC.CALL_CONVENTION,null,null,"build/objs/helloWorld","","Hello, World!",13);
    }


}
