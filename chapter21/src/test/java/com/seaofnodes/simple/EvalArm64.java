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

    // flags
    boolean N = false;
    boolean Z = false;
    boolean C = false;
    boolean V = false;

    EvalArm64(byte[] buf, int stackSize) {
        _buf = buf;
        regs  = new long[32];
        fregs = new double[32];
        // Stack grows down, heap grows up
        regs[2/*RSP*/] = _heap = stackSize;
        _pc = 0;
    }

    // Little endian
    public int  ld1s(int x) { return _buf[x  ]     ; }
    public int  ld1z(int x) { return _buf[x  ]&0xFF; }
    public int  ld2s(int x) { return ld1z(x) | ld1s(x+1)<< 8; }
    public int  ld2z(int x) { return ld1z(x) | ld1z(x+1)<< 8; }
    public int  ld4s(int x) { return ld2z(x) | ld2s(x+2)<<16; }
    public long ld8 (int x) { return (long)ld4s(x)&0xFFFFFFFFL | ((long)ld4s(x+4))<<32; }


    public float  ld4f(int x) { return Float.intBitsToFloat(ld4s(x)); }
    public double ld8f(int x) { return Double.longBitsToDouble(ld8(x)); }

    public void st1(int x, int  val) { _buf[x] = (byte)val; }
    public void st2(int x, int  val) { st1(x,val); st1(x+1,val>> 8); }
    public void st4(int x, int  val) { st2(x,val); st2(x+2,val>>16); }
    public void st8(int x, long val) { st4(x,(int)val); st2(x+4,(int)(val>>32)); }


    public int step(int maxops) {
        int trap = 0;
        long rval = 0;
        double frval = 0;
        int pc = _pc;
        int cycle = _cycle;
        boolean is_f = false;

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
            ir = ld4s( pc );

            int rdid = ir & 0x1f;
            // [31:24]
            int opcode1 = (ir >> 24) & 0xFF;
            // [31:26]
            int opcode2 = (ir >> 26) & 0x3F;
            switch(opcode2) {
                case 5: {
                    //b
                    int imm26 = ir << 6 >> 6;

                    pc = pc + imm26 - 4;
                    rdid = -1;
                    continue ;
                }
            }
            // for the opcode we just match for bits[31:24];
            switch(opcode1) {
                case 0xD2: {
                    // movz
                    // get immediate
                    rval = (ir >> 5) & 0x7FFF;
                    break;
                }
                case 0x54: {
                    // b.cond
                    int imm19 = ir << 8 >> 13;
                    imm19 = pc + imm19 - 4;
                    rdid = -1;

                    switch(ir & 0xF) {
                        case 0x0: if(Z)  pc = imm19; break; // eq
                        case 0x1: if(!Z) pc = imm19; break; // ne
                        case 0x2: if(C)  pc = imm19; break; // cs
                        case 0x3: if(!C) pc = imm19; break;
                        case 0x4: if(N) pc = imm19; break; // mi
                        case 0x5: if(!N) pc = imm19; break; // pl
                        case 0x6: if(V) pc = imm19; break; // vs
                        case 0x7: if(!V) pc = imm19; break; // vc
                        case 0x8: if(C && !Z) pc = imm19; break; // hi
                        case 0x9: if(!C || Z) pc = imm19; break; // ls
                        case 0xA: if(N == V) pc = imm19; break; // ge
                        case 0xB: if(N != V) pc = imm19; break; // lt
                        case 0xC: if(!Z && N == V) pc = imm19; break; // gt
                        case 0xD: if(Z || N != V) pc = imm19; break; // le
                        case 0xE: pc = imm19; break; // always executed(al)
                        default: trap = (2+1);
                    }
                    break;
                }
                case 0xeb: {
                    // subs(shifted register)
                    int rn = (ir >> 5) &  0x1F;
                    int rm = (ir >> 16) & 0x1F;
                    int lhs = (int)regs[rn];
                    int rhs = (int)regs[rm];
                    rval = lhs - rhs;

                    // set flags as a side effect
                    Z = (rval == 0);
                    N = (rval < 0);
                    C = lhs >= rhs;

                    int sign_lhs = (lhs >> 31) & 1;
                    int sign_rhs = (rhs >> 31) & 1;
                    long sign_res = (rval >> 31) & 1;
                    V = (sign_lhs != sign_rhs) && (sign_res != sign_lhs);
                    break;
                }
                case 0x8B: {
                    // add(shifted register)
                    int rn = (ir >> 5)  & 0x1F;
                    int rm = (ir >> 16) & 0x1F;
                    long lhs = regs[rn];
                    long rhs = regs[rm];
                    rval = lhs + rhs;
                    break;
                }
                case 0x91: {
                    // add(immediate)
                    int rn = (ir >> 5) & 0x1F;
                    int immediate = ir << 10 >> 20;
                    rval = regs[rn] + immediate;
                    break;
                }
                case 0xAA: {
                    // MOV(register)
                    int rm = (ir >> 16) & 0x1F;
                    rval = regs[rm];
                    break;
                }
                default: trap = (2+1);
            }
            set:
            if(trap != 0) {
                _pc = pc;
                break;
            }
            if(rdid != -1) {
                if(is_f) {fregs[rdid] = frval; is_f = false;}
                else regs[rdid] = rval;
            }

            pc += 4;
        }
        // Handle traps and interrupts.
        if(trap != 0) {
            return trap;
        }
        _cycle = cycle;
        _pc = pc;
        return 0;
    }


}
