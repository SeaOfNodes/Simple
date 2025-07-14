package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.GlobalCodeMotion;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.print.IRPrinter;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.Utils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter24Test {

    @Test
    public void testBubbles() throws IOException {
        String src = Files.readString( Path.of("docs/examples/BubbleSort.smp"));
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.LoopTree);
    }

    @Test @Ignore
    public void testSys() throws IOException {
        // Produce build/objs/sys.o
        CodeGen code =
            new CodeGen("src/main/smp", // ROOT
                        "build/objs/",  // Where objs go
                        new Ary<>(String.class){{add("sys.smp");}},
                        new Ary<>(String.class){{add(com.seaofnodes.simple.sys.SYS);}},
                        123L,true).
            driver(CodeGen.CPU_PORT,CodeGen.CALL_CONVENTION);
    }


    @Test
    public void testExport0() throws IOException {
        // "struct foo" in file "foo.smp" is exported
        String src_foo = "struct foo { val x = 3.14; val _y = 123; };";
        CodeGen code_foo =
            new CodeGen("",            // ROOT of project
                        "build/objs/", // OBJS
                        new Ary<>(String.class){{add("foo.smp");}},
                        new Ary<>(String.class){{add( src_foo );}},
                        123L/*seed*/, true)
            .driver(CodeGen.CPU_PORT,CodeGen.CALL_CONVENTION);

        // top-level default "main" uses foo
        String src_main = "return new foo.x";
        CodeGen code_main =
            new CodeGen("",            // ROOT of project
                        "build/objs/", // OBJS
                        new Ary<>(String.class){{add("main.smp");}},
                        new Ary<>(String.class){{add( src_main );}},
                        123L/*seed*/, true)
            .driver(CodeGen.CPU_PORT,CodeGen.CALL_CONVENTION);


        // TODO: assert var foo is exported
        throw Utils.TODO();
    }


    @Test
    public void demoPrint() {
        String src =
"""
val c = { int x ->
    int sum=0;
    for( int i=0; i<x; i++ ) sum += i;
    sum;
};
val b = { int y -> c(y) * c(y+1); };
val a = { int z -> b(z) * b(z+5); };
return a(3);
""";
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.LoopTree);
        String rez = IRPrinter.prettyPrint(code);
    }


    @Test
    public void testOr() throws IOException {
        TestC.run("or1","Or",0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build("or1", 0, 2, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);
        assertEquals("Or",R5._stdout.toString());

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("or1", 0, 2, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
        assertEquals("Or",arm._stdout.toString());
    }


    @Test
    public void testAnd() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/and1.smp"));
        TestC.run(src,"And",0);

        // Evaluate on RISC5 emulator
        EvalRisc5 R5 = TestRisc5.build("and1", 0, 2, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals(0,R5.regs[riscv.A0]);
        assertEquals("And",R5._stdout.toString());

        // Evaluate on ARM emulator
        EvalArm64 arm = TestArm64.build("and1", 0, 2, false);
        trap = arm.step(100);
        assertEquals(0,trap);
        assertEquals(0,arm.regs[0]);
        assertEquals("And",arm._stdout.toString());
    }


    @Test @Ignore
    public void sieveOfEratosthenes() {
        String src =
"""
var ary = new bool[arg], primes = new int[arg];
var nprimes=0, p=0;
// Find primes while p^2 < arg
for( p=2; p*p < arg; p++ ) {
    // skip marked non-primes
    while( ary[p] ) p++;
    // p is now a prime
    primes[nprimes++] = p;
    // Mark out the rest non-primes
    for( int i = p + p; i < ary#; i += p )
        ary[i] = true;
}
// Now just collect the remaining primes, no more marking
for( ; p < arg; p++ )
    if( !ary[p] )
        primes[nprimes++] = p;
// Copy/shrink the result array
var !rez = new int[nprimes];
for( int j=0; j<nprimes; j++ )
    rez[j] = primes[j];
return rez;
""";
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.LoopTree);
        String rez = IRPrinter.prettyPrint(code);
    }
}
