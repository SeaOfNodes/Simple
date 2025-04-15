package com.seaofnodes.simple;


import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.arm.arm;
import junit.framework.Assert;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

// Runs 32-bit ARM code in an emulator

public class TestArm64 {
    public static EvalArm64 build(String file, int arg) throws IOException {
        return  build("src/test/java/com/seaofnodes/simple/progs",file, arg);
    }

    // Compile and run a simple program
    public static EvalArm64 build(String dir, String file, int arg) throws IOException {
        // Compile and export Simple
        String src = Files.readString(Path.of(dir+"/"+file+".smp"));
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.RegAlloc,"arm", "SystemV").encode(true);

        // Image
        byte[] image = new byte[1<<20]; // A megabyte (1024*1024 bytes)
        EvalArm64 ARM = new EvalArm64(image, 1<<16);
        // Code at offset 0
        System.arraycopy(code._encoding.bits(), 0, image, 0, code._encoding.bits().length);
        // Initial incoming int arg
        ARM.regs[arm.D0] = arg;
        // malloc memory starts at 64K and goes up
        return ARM;
    }
}
