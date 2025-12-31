package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.util.BAOS;
import java.io.IOException;

import static org.junit.Assert.*;

// Runs 32-bit R5 code in an emulator
public abstract class TestRisc5 {

    public static EvalRisc5 build( String src, String main, int arg, int spills, boolean print ) throws IOException {
        // Compile and export Simple
        CodeGen code = new CodeGen(src).driver("riscv", "SystemV",null,false);
        if( print ) { code.print_as_hex(); System.out.print(code.asm()); }

        // Allocation quality not degraded
        int delta = spills>>3;
        if( delta==0 ) delta = 1;
        if( spills != -1 )
            assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);

        // Image
        byte[] image = new byte[1<<20]; // A megabyte (1024*1024 bytes)
        EvalRisc5 R5 = new EvalRisc5(image, 1<<16);
        int start = 0;          // Code at offset 0
        int cpool = fill(code._encoding._bits ,image,start);
        int sdata = fill(code._encoding._cpool,image,cpool);
        int end   = fill(code._encoding._sdata,image,sdata);

        // Initial incoming int arg
        R5.regs[riscv.A0] = arg;
        // malloc memory starts at 64K and goes up

        // Look up starting PC or zero if not given
        if( main!=null )
            for( Node use : code._start._outputs )
                if( use instanceof FunNode fun && main.equals(fun._name) )
                    R5._pc = code._encoding._opStart[fun._nid];

        return R5;
    }

    // Fill bits into image; return end point rounded up.
    private static int fill( BAOS bits, byte[] image, int off) {
        System.arraycopy(bits.buf(), 0, image, off, bits.size());
        return (off+bits.size() + 15) & -16;
    }

}
