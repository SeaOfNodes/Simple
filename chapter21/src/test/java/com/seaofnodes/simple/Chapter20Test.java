package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.print.ASMPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter20Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen("return 0;");
        code.parse().opto().typeCheck();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  2));
    }

    static void testCPU( String src, String cpu, String os, int spills, String stop ) {
        CodeGen code = new CodeGen(src);
        code.driver(CodeGen.Phase.RegAlloc,cpu,os);
        int delta = spills>>3;
        if( delta==0 ) delta = 1;
        if( spills != -1 )
            assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);
        if( stop != null )
            assertEquals(stop, code._stop.toString());
    }

    private static void testAllCPUs( String src, int spills, String stop ) {
        testCPU(src,"x86_64_v2", "SystemV",spills,stop);
        testCPU(src,"riscv"    , "SystemV",spills,stop);
        testCPU(src,"arm"      , "SystemV",spills,stop);
    }

    @Test public void testAlloc0() {
        testCPU("return new u8[arg];","x86_64_v2", "SystemV",3,"return [u8];");
        testCPU("return new u8[arg];","riscv"    , "SystemV",5,"return [u8];");
        testCPU("return new u8[arg];","arm"      , "SystemV",5,"return [u8];");
    }

    @Test public void testBasic1() {
        String src = "return arg | 2;";
        testCPU(src,"x86_64_v2", "SystemV",1,"return (ori,mov(arg));");
        testCPU(src,"riscv"    , "SystemV",0,"return ( arg | #2 );");
        testCPU(src,"arm"      , "SystemV",0,"return (ori,arg);");
    }

    @Test
    public void testNewtonInteger() {
        String src =
"""
// Newtons approximation to the square root
val sqrt = { int x ->
    int guess = x;
    while( 1 ) {
        int next = (x/guess + guess)/2;
        if( next == guess ) return guess;
        guess = next;
    }
};
return sqrt(arg) + sqrt(arg+2);
""";
        testCPU(src,"x86_64_v2", "Win64"  ,23,null);
        testCPU(src,"riscv"    , "SystemV",19,null);
        testCPU(src,"arm"      , "SystemV",19,null);
    }

    @Test
    public void testNewtonFloat() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/newtonFloat.smp"))
            + "flt farg = arg; return test_sqrt(farg) + test_sqrt(farg+2.0);";
        testCPU(src,"x86_64_v2", "SystemV",39,null);
        testCPU(src,"riscv"    , "SystemV",17,null);
        testCPU(src,"arm"      , "SystemV",18,null);
    }

    @Test
    public void testAlloc2() {
        String src = "int[] !xs = new int[3]; xs[arg]=1; return xs[arg&1];";
        testCPU(src,"x86_64_v2", "SystemV",-1,"return .[];");
        testCPU(src,"riscv"    , "SystemV", 6,"return .[];");
        testCPU(src,"arm"      , "SystemV", 6,"return .[];");
    }



    @Test
    public void testArray1() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/array1.smp"));
        testCPU(src,"x86_64_v2", "SystemV",-1,"return .[];");
        testCPU(src,"riscv"    , "SystemV", 8,"return (add,.[],(mul,.[],1000));");
        testCPU(src,"arm"      , "SystemV", 5,"return (add,.[],(mul,.[],1000));");
    }


    @Test
    public void testString() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/stringHash.smp"));
        testCPU(src,"x86_64_v2", "SystemV", 9,null);
        testCPU(src,"riscv"    , "SystemV", 5,null);
        testCPU(src,"arm"      , "SystemV", 6,null);
    }

    @Test
    public void testCast() {
        String src = "struct Bar { int x; }; var b = arg ? new Bar;  return b ? b.x++ + b.x++ : -1;";
        testCPU(src,"x86_64_v2", "SystemV",2,null);
        testCPU(src,"riscv"    , "SystemV",2,null);
        testCPU(src,"arm"      , "SystemV",2,null);
    }

    @Test
    public void testFltArg() {
        String src = "return {int i, flt f, int j->return i+f+j;};";
        testAllCPUs(src,0,null);
    }

    @Test
    public void testFlags1() {
        String src = """
bool b1 = arg == 1;
bool b2 = arg == 2;
if (b2) if (b1) return 1;
if (b1) return 2;
return 0;
""";
        testCPU(src,"x86_64_v2", "SystemV",0,"return Phi(Region,1,2,0);");
        testCPU(src,"riscv"    , "SystemV",0,"return Phi(Region,1,2,0);");
        testCPU(src,"arm"      , "SystemV",0,"return Phi(Region,1,2,0);");
    }

    @Test
    public void testFlags2() {
        String src = """
bool b1 = arg == 1;
while (arg > 0) {
    arg--;
    if (b1) arg--;
}
return arg;
""";
        testCPU(src,"x86_64_v2", "SystemV",3,null);
        testCPU(src,"riscv"    , "SystemV",2,null);
        testCPU(src,"arm"      , "SystemV",2,null);
    }
}
