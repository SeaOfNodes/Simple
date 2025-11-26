package com.seaofnodes.simple;


import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.arm.arm;
import junit.framework.Assert;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

// Runs 32-bit ARM code in an emulator

public class TestArm64 {
    public static EvalArm64 build( String file, int arg, int spills, boolean print ) throws IOException {
        String dir = "src/test/java/com/seaofnodes/simple/progs";
        String src = Files.readString(Path.of(dir + "/"+file+".smp"));
        return build(dir,src, file, arg, spills, print);
    }

    public static EvalArm64 build( String name, String src, int arg, int spills, boolean print ) throws IOException {
        String dir = "src/test/java/com/seaofnodes/simple/progs";
        return build(dir, src ,name, arg, spills, print);
    }

    // Compile and run a simple program
    public static EvalArm64 build( String dir, String src, String file, int arg, int spills, boolean print ) throws IOException {
        // Compile and export Simple
        CodeGen code = new CodeGen(src).driver("arm", "SystemV",null);
        if( print ) { code.print_as_hex(); System.out.print(code.asm()); }

        // Allocation quality not degraded
        int delta = spills>>3;
        if( delta == 0 ) delta =1;
        if( spills != -1 ) assertEquals("Expect spills:", spills, code._regAlloc._spillScaled, delta);

        // Image
        byte[] image = new byte[1<<20]; // A megabyte (1024*1024 bytes)
        EvalArm64 ARM = new EvalArm64(image, 1<<16);
        // Code at offset 0
        System.arraycopy(code._encoding.bits(), 0, image, 0, code._encoding.bits().length);
        // Initial incoming int arg
        ARM.regs[arm.X0] = arg;
        // malloc memory starts at 64K and goes up
        return ARM;
    }
}
