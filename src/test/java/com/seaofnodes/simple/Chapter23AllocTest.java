package com.seaofnodes.simple;

import java.io.IOException;
import java.nio.file.*;
import org.junit.Test;

// Revised or newly enabled allocation examples in Chapter 23.
public class Chapter23AllocTest {
    @Test
    public void testString() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/stringHash.smp"));
        Chapter23Test.testCPU(src,"x86_64_v2", "SystemV", 9,null);
        Chapter23Test.testCPU(src,"riscv"    , "SystemV", 3,null);
        Chapter23Test.testCPU(src,"arm"      , "SystemV", 3,null);
    }

    @Test
    public void testJig21() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/jig.smp"));
        Chapter23Test.testCPU(src,"x86_64_v2", "Win64"  ,-1,null);
        Chapter23Test.testCPU(src,"riscv"    , "SystemV",-1,null);
        Chapter23Test.testCPU(src,"arm"      , "SystemV",-1,null);
    }

    @Test
    public void testJig22() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/jig.smp"));
        //String src = Files.readString(Path.of("docs/examples/BubbleSort.smp"));
        Chapter23Test.testCPU(src,"x86_64_v2", "Win64"  ,-1,null);
        Chapter23Test.testCPU(src,"riscv"    , "SystemV",-1,null);
        Chapter23Test.testCPU(src,"arm"      , "SystemV",-1,null);
    }

}
