package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.print.ASMPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;
import static com.seaofnodes.simple.Main.PORTS;
import static org.junit.Assert.*;

public class Chapter21Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen("return 0;");
        code.parse().opto().typeCheck();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  2));
    }

    static void testCPU( String src, String cpu, String os, int spills, String stop ) {
        CodeGen code = new CodeGen(src);
        code.parse().opto().typeCheck().instSelect(PORTS,cpu,os).GCM().localSched().regAlloc().encode();
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
            + "flt farg = arg; return sqrt(farg) + sqrt(farg+2.0);";
        testCPU(src,"x86_64_v2", "SystemV",23,null);
        testCPU(src,"riscv"    , "SystemV",18,null);
        testCPU(src,"arm"      , "SystemV",19,null);
    }

    @Test
    public void testString() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/stringHash.smp"));
        testCPU(src,"x86_64_v2", "SystemV",18,null);
        testCPU(src,"riscv"    , "SystemV",18,null);
        testCPU(src,"arm"      , "SystemV",17,null);
    }

    @Test
    public void testArray1() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/array1.smp"));
        testCPU(src,"x86_64_v2", "SystemV",7,"return .[];");
        testCPU(src,"riscv"    , "SystemV",7,"return (add,.[],(mul,.[],1000));");
        testCPU(src,"arm"      , "SystemV",5,"return (add,.[],(mul,.[],1000));");
    }


    @Test
    public void testExport() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/newtonFloat.smp"))
            + "flt farg = arg; return sqrt(farg) + sqrt(farg+2.0);";
        CodeGen code = new CodeGen(src);
        code.parse().opto().typeCheck().instSelect(PORTS, "x86_64_v2", "SystemV").GCM().localSched().regAlloc().encode().exportELF("build/objs/newton.o");
    }


    // Read test case from disk.
    // TODO: exportELF not handling external calls yet
    @Ignore @Test
    public void testPersons() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/Person.smp"));
        CodeGen code = new CodeGen(src);
        code.parse().opto().typeCheck().instSelect(PORTS, "x86_64_v2", "SystemV").GCM().localSched().regAlloc().encode().exportELF("build/objs/Persons.o");
        //testCPU(src,"x86_64_v2", "Win64"  ,3,null);
        //testCPU(src,"riscv"    , "SystemV",1,null);
        //testCPU(src,"arm"      , "SystemV",1,null);
    }

}
