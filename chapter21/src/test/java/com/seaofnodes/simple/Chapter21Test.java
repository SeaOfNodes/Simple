package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
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
        assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);
        if( stop != null )
            assertEquals(stop, code._stop.toString());
    }

    private static void testAllCPUs( String src, int spills, String stop ) {
        testCPU(src,"x86_64_v2", "SystemV",spills,stop);
        testCPU(src,"riscv"    , "SystemV",spills,stop);
        testCPU(src,"arm"      , "SystemV",spills,stop);
    }


    @Test public void testBasic1() {
        String src = "return arg | 2;";
        testCPU(src,"x86_64_v2", "SystemV",1,"return (ori,mov(arg));");
        testCPU(src,"riscv"    , "SystemV",0,"return ( arg | #2 );");
        testCPU(src,"arm"      , "SystemV",0,"return (ori,arg);");
    }

    @Test
    public void testNewtonFloat() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/newtonFloat.smp"))
            + "flt farg = arg; return test_sqrt(farg) + test_sqrt(farg+2.0);";
        testCPU(src,"x86_64_v2", "SystemV",39,null);
        testCPU(src,"riscv"    , "SystemV",18,null);
        testCPU(src,"arm"      , "SystemV",19,null);
    }

    @Test public void testNewtonExport() throws Exception {
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
        testCPU(src,"x86_64_v2", "SystemV", 1,null);
        testCPU(src,"riscv"    , "SystemV", 5,null);
        testCPU(src,"arm"      , "SystemV", 6,null);
    }

    @Test
    public void testStringExport() throws Exception { TestC.run("stringHash"); }

    @Test
    public void testArray1() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/array1.smp"));
        testCPU(src,"x86_64_v2", "SystemV",7,"return .[];");
        testCPU(src,"riscv"    , "SystemV",7,"return (add,.[],(mul,.[],1000));");
        testCPU(src,"arm"      , "SystemV",5,"return (add,.[],(mul,.[],1000));");
    }

    @Test
    public void testFib() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/fib.smp"));
        testCPU(src,"x86_64_v2", "SystemV",24,null);
        testCPU(src,"riscv"    , "SystemV",16,null);
        testCPU(src,"arm"      , "SystemV",16,null);
    }


    // Read test case from disk.
    // TODO: exportELF not handling external calls yet
    @Ignore @Test
    public void testPersons() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/Person.smp"));
        CodeGen code = new CodeGen(src);
        code.parse().opto().typeCheck().instSelect( "x86_64_v2", "SystemV").GCM().localSched().regAlloc().encode().exportELF("build/objs/Persons.o");
        //testCPU(src,"x86_64_v2", "Win64"  ,3,null);
        //testCPU(src,"riscv"    , "SystemV",1,null);
        //testCPU(src,"arm"      , "SystemV",1,null);
    }

}
