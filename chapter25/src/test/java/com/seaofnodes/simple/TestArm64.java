package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.cpus.arm.arm;
import com.seaofnodes.simple.util.BAOS;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

// Runs 32-bit ARM code in an emulator

public class TestArm64 {

    public static EvalArm64 build( String main, String src, int arg, int spills, boolean print ) throws IOException {
        // Compile and export Simple
        CodeGen code = new CodeGen(src).driver("arm", "SystemV",true,false);

        // Allocation quality not degraded
        int delta = spills>>3;
        if( delta == 0 ) delta =1;
        if( spills != -1 ) assertEquals("Expect spills:", spills, code._regAlloc._spillScaled, delta);

        // Image
        byte[] image = new byte[1<<20]; // A megabyte (1024*1024 bytes)
        EvalArm64 ARM = new EvalArm64(image, 1<<16);
        int start = 0;          // Code at offset 0
        int cpool = fill(code.compunit()._encoding._bits ,image,start);
        int sdata = fill(code.compunit()._encoding._cpool,image,cpool);
        int end   = fill(code.compunit()._encoding._sdata,image,sdata);

        // Initial incoming int arg
        ARM.regs[arm.X0] = arg;
        // malloc memory starts at 64K and goes up

        // Look up starting PC or zero if not given
        if( main!=null )
            for( Node use : code._start._outputs )
                if( use instanceof FunNode fun && main.equals(fun._name) )
                    ARM._pc = code.compunit()._encoding.opStart(fun);

        return ARM;
    }

    // Fill bits into image; return end point rounded up.
    private static int fill( BAOS bits, byte[] image, int off) {
        System.arraycopy(bits.buf(), 0, image, off, bits.size());
        return (off+bits.size() + 15) & -16;
    }
}
