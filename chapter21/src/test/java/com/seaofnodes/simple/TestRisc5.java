package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import junit.framework.Assert;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

// Runs 32-bit R5 code in an emulator
public abstract class TestRisc5 {

    public static EvalRisc5 build( String file, int arg ) throws IOException {
        return build("src/test/java/com/seaofnodes/simple/progs",file, arg);
    }

    // Compile and run a simple program
    public static EvalRisc5 build( String dir, String file, int arg ) throws IOException {
        // Compile and export Simple
        String src = Files.readString(Path.of(dir+"/"+file+".smp"));
        CodeGen code = new CodeGen(src).parse().opto().typeCheck().instSelect( "riscv", "SystemV").GCM().localSched().regAlloc().encode();

        // Image
        byte[] image = new byte[1<<20]; // A megabyte
        EvalRisc5 R5 = new EvalRisc5(image);
        // Code at offset 0
        System.arraycopy(code._encoding.bits(), 0, image, 0, code._encoding.bits().length);
        // Program start
        R5._pc = 0;
        // Stack starts at 64K and goes down (runs into code)
        R5.regs[riscv.SP] = 1<<16;
        // Initial incoming int arg
        R5.regs[riscv.A0] = arg;
        // malloc memory starts at 64K and goes up
        return R5;
    }

}
