package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import java.io.IOException;

import com.seaofnodes.simple.node.cpus.arm.arm;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter22Test {

    @Test
    public void testJig() throws IOException {
        String src = """
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


    static int testCPUSize( String src, String cpu, String os, int spills ) {
        return testCPU(src,cpu,os,spills, "return 0;" )._encoding._bits.size();
    }

    // Should not fold away
    @Test public void testSextFail() throws IOException {
        String src = """
struct _Person { i32 age;};
_Person !p = new _Person;
p.age = (arg<<17)>>17;
return 0;
""";
        assertEquals(59, testCPUSize(src, "x86_64_v2","Win64",2 ));
        assertEquals(72, testCPUSize(src, "riscv","SystemV",4 ));
        assertEquals(68, testCPUSize(src, "arm","SystemV",4 ));

        // do assertEquals here
        EvalRisc5 R5 = TestRisc5.build( src, "sext_str_not_fold_away", 0, 4, false);
        int trap = R5.step(100);
        assertEquals(0,trap);

        EvalArm64 A5 = TestArm64.build("sext_str_not_fold_away", src, 0, 4, false);
        trap = A5.step(100);
        assertEquals(0,trap);

    }

    // Should not fold away
    @Test public void testSextFail2() throws IOException {
        String src = """
                struct _Person { i32 age;};
                _Person !p = new _Person;
                p.age = (arg<<48)>>48;
                return 0;
        """;

        EvalRisc5 R5 = TestRisc5.build( src, "sext_str_not_fold_away_2", 0, 4, false);
        int trap = R5.step(100);
        assertEquals(0,trap);

        EvalArm64 A5 = TestArm64.build("sext_str_not_fold_away_2", src, 0, 4, false);
        int trap_arm = A5.step(100);
        assertEquals(0,trap_arm);

        assertEquals(59, testCPUSize(src, "x86_64_v2","Win64",2 ));
        assertEquals(72, testCPUSize(src, "riscv","SystemV",4 ));
        assertEquals(68, testCPUSize(src, "arm","SystemV",4 ));

    }

    // Should fold away
    @Test public void testSextSuccess() throws IOException {
        String src = """
// Should fold away sign extend
struct _Person { i8 age;};
_Person !p = new _Person;
p.age = (arg<<48)>>48;
return 0;
""";

        EvalRisc5 R5 = TestRisc5.build( src, "sext_str_fold_away", 0, 5, false);
        int trap = R5.step(100);
        assertEquals(0,trap);

        EvalArm64 A5 = TestArm64.build("sext_str_fold_away", src, 0, 5, false);
        int trap_arm = A5.step(100);
        assertEquals(0,trap_arm);

        assertEquals(51, testCPUSize(src, "x86_64_v2","Win64",2 ));
        assertEquals(68, testCPUSize(src, "riscv","SystemV",5 ));
        assertEquals(64, testCPUSize(src, "arm","SystemV",5 ));
        // do assertEquals here
    }


    // Int now is changed to 4 bytes.
    @Test public void testPerson() throws IOException {
        String src =
"""
struct Person {
    i32 age;
};

val fcn = { Person?[] ps, int x ->
    if( ps[x] )
        ps[x].age++;
};
""";
        String person = "6\n";
        TestC.runC(src, "person", person, 0);

        // Memory layout starting at PS:
        int ps = 1<<16;         // Person array pointer starts at heap start
        // Person[3] = { len,pad,P0,P1,P2 }; // sizeof = 4*8
        // P0 = { age } // sizeof=8
        int p0 = ps+4*8+0*8;
        // P1 = { age } // sizeof=8
        int p1 = ps+4*8+1*8;
        // P2 = { age } // sizeof=8
        int p2 = ps+4*8+2*8;
        EvalRisc5 R5 = TestRisc5.build( src, "fcn", ps, 0, false);
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

        EvalArm64 A5 = TestArm64.build("fcn", src, ps, 0, false);
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
val x = 5; // aa.az; // Error to self-define forward ref
struct _A { _B? b; _C? c; i64 ax; val az = x*2; };
struct _B { _A? a; _C? c; f32 bx; val bz = x*3; };
struct _C { _A? a; _B? b; f64 cx; val cz = x*x; };
_A !aa = new _A{ ax=17; };
_B !bb = new _B{ bx=3.14; a = aa; };
_C !cc = new _C{ cx=2.73; a = aa; b = bb; };
aa.b = bb;
aa.c = cc;
bb.c = cc;
return cc.cz;
""";
        CodeGen code = new CodeGen(src).parse().opto().typeCheck();
        assertEquals("return 25;", code._stop.toString());
        assertEquals("25", Eval2.eval(code, 0));
    }

    @Test
    public void testHelloWorld() throws IOException {
        String src = """
// fd  buf len -> len
{  i32 i64 u32 -> u32 } write = "C";
// Write byte array to stdout
val p = { u8[~] str ->
    i64 ptr = str;  // cast array base to i64
    return write(1,ptr,str#);
};
p("Hello, World!");
return 0;
""";
        TestC.runSF(src,"helloWorld", "Hello, World!",2);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src, "helloWorld", 0, 5, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);
        assertEquals("Hello, World!",R5._stdout.toString());

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("helloWorld", src,0, 5, false);
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
val _sum = { i32[~] is ->  // final array
    int sum=0;
    for( int i=0; i<is#; i++ )
        sum += is[i];
    return sum;
};
return _sum(is);
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
        TestC.runSF(src,"echo", "",0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build( src, "echo", 0, 2, false);
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
