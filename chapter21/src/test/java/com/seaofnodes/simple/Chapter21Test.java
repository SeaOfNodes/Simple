package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.print.ASMPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter21Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen("return 0;");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  2));
    }

    static void testCPU( String src, String cpu, String os, int spills, String stop ) {
        CodeGen code = new CodeGen(src);
        code.parse().opto().typeCheck().instSelect(cpu,os).GCM().localSched().regAlloc().encode();
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

    @Test
    public void testArray1() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/array1.smp"));
        testCPU(src,"x86_64_v2", "SystemV",-1,"return .[];");
        testCPU(src,"riscv"    , "SystemV", 7,"return (add,.[],(mul,.[],1000));");
        testCPU(src,"arm"      , "SystemV", 5,"return (add,.[],(mul,.[],1000));");
    }

    @Test
    public void testFib() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/fib.smp"));
        testCPU(src,"x86_64_v2", "SystemV",24,null);
        testCPU(src,"riscv"    , "SystemV",16,null);
        testCPU(src,"arm"      , "SystemV",16,null);
    }

    @Test
    public void testNewtonFloat() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/newtonFloat.smp"))
            + "flt farg = arg; return test_sqrt(farg) + test_sqrt(farg+2.0);";
        testCPU(src,"x86_64_v2", "SystemV",39,null);
        testCPU(src,"riscv"    , "SystemV",18,null);
        testCPU(src,"arm"      , "SystemV",19,null);
    }

    @Test public void testNewtonExport() throws IOException {
        String result = """
0  0.000000
1  1.000000
2  1.414214
3  1.732051
4  2.000000
5  2.236068
6  2.449490
7  2.645751
8  2.828427
9  3.000000
""";
        TestC.run("newtonFloat",result);
    }

    @Test
    public void testString() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/stringHash.smp"));
        testCPU(src,"x86_64_v2", "SystemV", 9,null);
        testCPU(src,"riscv"    , "SystemV", 5,null);
        testCPU(src,"arm"      , "SystemV", 6,null);
    }

    @Test public void testStringExport() throws IOException { TestC.run("stringHash"); }

    @Test public void testSieve() throws IOException {
        String primes = "25[2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, ]";
        TestC.run("sieve",primes);
    }

    @Test public void testFibExport() throws IOException {
        String fib = "[1, 1, 2, 3, 5, 8, 13, 21, 34, 55]";
        TestC.run("fib", fib);

        EvalRisc5 R5 = TestRisc5.build("fib", 8);
        int trap = R5.step(100);
        assertEquals(0,trap);
        // Return register A0 holds fib(8)==55
        assertEquals(55,R5.regs[riscv.A0]);
    }

    @Test public void testBrainFuck() throws IOException {
        String brain_fuck = "Hello World!\n";
        TestC.run("brain_fuck", brain_fuck);
    }

    @Test public void testPerson() throws IOException {
        String person = "6\n";
        TestC.run("person", person);

        // Memory layout starting at PS:
        int ps = 1<<16;         // Person array pointer
        // Person[3] = { len,pad,P0,P1,P2 }; // sizeof = 4*8
        // P0 = { age } // sizeof=8
        int p0 = ps+4*8+0*8;
        // P1 = { age } // sizeof=8
        int p1 = ps+4*8+1*8;
        // P2 = { age } // sizeof=8
        int p2 = ps+4*8+2*8;
        EvalRisc5 R5 = TestRisc5.build("person", ps);
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
    }

    @Test public void testArgCount() throws IOException {
        String arg_count = "191.000000\n";
        TestC.run("arg_count", arg_count);
    }
}
