package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

// Runs 32-bit R5 code in an emulator
public abstract class TestRisc5 {

    public static EvalRisc5 build( String file, int arg, int spills ) throws IOException {
        return build("src/test/java/com/seaofnodes/simple/progs",file, arg, spills);
    }

    // Compile and run a simple program
    public static EvalRisc5 build( String dir, String file, int arg, int spills ) throws IOException {
        // Compile and export Simple
        String src = Files.readString(Path.of(dir+"/"+file+".smp"));
        CodeGen code = new CodeGen(src).driver("riscv", "SystemV",null);

        // Allocation quality not degraded
        int delta = spills>>3;
        if( delta==0 ) delta = 1;
        if( spills != -1 )
            assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);

        // Image
        byte[] image = new byte[1<<20]; // A megabyte (1024*1024 bytes)
        EvalRisc5 R5 = new EvalRisc5(image, 1<<16);
        // Code at offset 0
        System.arraycopy(code._encoding.bits(), 0, image, 0, code._encoding.bits().length);
        // Initial incoming int arg
        R5.regs[riscv.A0] = arg;
        // malloc memory starts at 64K and goes up
        return R5;
    }

}
