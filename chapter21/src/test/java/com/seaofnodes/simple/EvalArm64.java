package com.seaofnodes.simple;

public class EvalArm64 {
    // Memory image, always 0-based
    public final byte[] _buf;

    // GPRs
    final long[] regs;
    // FRs
    final double[] fregs;

    int _pc;

    // Start of free memory for allocation
    int _heap;

    // Cycle counters
    int _cycle;

    EvalArm64(byte[] buf, int stackSize) {
        _buf = buf;
        regs  = new long[32];
        fregs = new double[32];
        // Stack grows down, heap grows up
        regs[2/*RSP*/] = _heap = stackSize;
        _pc = 0;
    }

    public int step(int maxops) {
        int trap = 0;
        long rval = 0;
        double frval = 0;
        int pc = _pc;
        int cycle = _cycle;
        boolean is_f = false;
        outer:
        for(int icount = 0; icount < maxops; icount++) {
            int ir = 0;
            rval = 0;
            cycle++;

            if( pc >= _buf.length ) {
                trap = 1 + 1;  // Handle access violation on instruction read.
                break;
            }

            if( (pc & 3)!=0 ) {
                trap = 1;  // Handle PC-misaligned access
                break;
            }

            // Load instruction from image buffer at PC
            //ir = ld4s( pc );

            //int rdid = ir & 0x1f;
            //switch(ir & )
        }
        if(trap != 0) {
            return trap;
        }
        _cycle = _cycle;
        _pc = pc;
        return 0;
    }


}
