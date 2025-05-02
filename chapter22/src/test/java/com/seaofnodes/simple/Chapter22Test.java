package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
