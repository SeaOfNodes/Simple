package com.seaofnodes.simple;

import java.io.IOException;
import java.nio.file.*;
import org.junit.Test;

// Revised Chapter 20 examples introduced with native encoding.
public class Chapter21AllocTest {
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
        Chapter21Test.testCPU(src,"x86_64_v2", "Win64"  ,48,null);
        Chapter21Test.testCPU(src,"riscv"    , "SystemV",19,null);
        Chapter21Test.testCPU(src,"arm"      , "SystemV",19,null);
    }

    @Test
    public void testNewtonFloat() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/newtonFloat.smp"))
            + "flt farg = arg; return test_sqrt(farg) + test_sqrt(farg+2.0);";
        Chapter21Test.testCPU(src,"x86_64_v2", "SystemV",39,null);
        Chapter21Test.testCPU(src,"riscv"    , "SystemV",17,null);
        Chapter21Test.testCPU(src,"arm"      , "SystemV",18,null);
    }

    @Test
    public void testArray1() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/array1.smp"));
        Chapter21Test.testCPU(src,"x86_64_v2", "SystemV",-1,"return .[];");
        Chapter21Test.testCPU(src,"riscv"    , "SystemV", 8,"return (add,.[],(mul,.[],1000));");
        Chapter21Test.testCPU(src,"arm"      , "SystemV", 5,"return (add,.[],(mul,.[],1000));");
    }


    @Test
    public void testString() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/stringHash21.smp"));
        Chapter21Test.testCPU(src,"x86_64_v2", "SystemV", 9,null);
        Chapter21Test.testCPU(src,"riscv"    , "SystemV", 3,null);
        Chapter21Test.testCPU(src,"arm"      , "SystemV", 3,null);
    }

}
