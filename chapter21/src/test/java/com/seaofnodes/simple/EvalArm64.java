package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.node.cpus.arm.arm;

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
        regs[arm.RSP] = _heap = stackSize;
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
        outer:
        for(int icount = 0; icount < maxops; icount++) {
            int ir = 0;
            rval = 0;
            frval = 0;
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
            // for the opcode we just match for bits[31:24];
            switch( opcode1 ) {
            case 0x14:  case 0x15:  case 0x16:  case 0x17:
                pc += ((ir << 6 >> 6) * 4)-4;
                rdid = -1;
                break;

            case 0x1E: {        // floats
                is_f = true;
                int rs1 = (ir >> 5) & 0x1F;
                int rs2 = (ir >> 16) & 0x1F;

                int op = (ir >> 10) & 0x3F;
                switch(op) {
                case 2: frval = fregs[rs1] * fregs[rs2]; break; // fmul
                case 6: frval = fregs[rs1] / fregs[rs2]; break; // fdiv
                case 8: {  // fcmp
                    double lhs = fregs[rs1];
                    double rhs = fregs[rs2];
                    Z = (lhs == rhs);
                    N = (lhs <  rhs);
                    C = (lhs >= rhs);
                    V = Double.isNaN(lhs) || Double.isNaN(rhs);
                    rdid = -1;
                    break;
                }
                case 10: frval = fregs[rs1] + fregs[rs2]; break; // fadd
                case 14: frval = fregs[rs1] - fregs[rs2]; break; // fsub
                case 16: frval = fregs[rs1]; break; // fmov
                default: trap = (2+1);
                }
                break;
            }

            case 0x54: {
                // b.cond
                int imm19 = ir << 8 >> 13;
                imm19 = pc + (imm19 * 4) - 4;
                rdid = -1;

                boolean bra = switch(ir & 0xF) {
                case 0x0 ->  Z; // eq
                case 0x1 -> !Z; // ne
                case 0x2 ->  C; // cs
                case 0x3 -> !C;
                case 0x4 ->  N; // mi
                case 0x5 -> !N; // pl
                case 0x6 ->  V; // vs
                case 0x7 -> !V; // vc
                case 0x8 ->  C && !Z; // hi
                case 0x9 -> !C ||  Z; // ls
                case 0xA -> N == V;   // ge
                case 0xB -> N != V;   // lt
                case 0xC -> !Z && N == V; // gt
                case 0xD -> Z || N != V; // le
                case 0xE -> true; // always executed(al)
                default  -> throw Utils.TODO(); // trap=3; break;
                };
                if( bra ) pc=imm19;
                break;
            }
            case 0x5C: {
                // ldr
                is_f = true;
                // address = delta
                int address = ir << 8 >> 13;
                address *= 4;
                if(address >= _buf.length-3) trap = (5 + 1);
                frval = ld8f(pc + address);
                break;
            }

            case 0x8A: {        // AND
                int rn = (ir >>  5) & 0x1F;
                int rm = (ir >> 16) & 0x1F;
                rval = regs[rn] & regs[rm];
                break;
            }
            case 0x8B: {        // add(shifted register)
                int rn = (ir >>  5) & 0x1F;
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
            case 0x92: {
                // and(immediate)
                int rn = (ir >> 5) & 0x1F;
                int immediate = (ir >> 10) & 0x1FFF;
                long imm = arm.decodeImm12(immediate);
                rval = regs[rn] & imm;
                break;
            }
            case 0x93: {
                // asr(immediate)
                int rn = (ir >> 5) & 0x1F;
                int immediate = ir << 10 >> 20;
                rval = regs[rn] >> immediate;
                break;
            }
            case 0x94:  case 0x95:  case 0x96:  case 0x97: { // bl
                rval = pc + 4;
                regs[arm.X30] = rval;
                if(pc + 4 == 0) {
                    regs[rdid] = rval;
                    break outer;
                }
                int imm26 = (ir & 0x03FFFFFF);
                int imm = (imm26 << 6) >> 6; // Sign extend
                pc += (imm << 2); // times 4

                if( pc == Encoding.SENTINAL_CALLOC ) {
                    regs[arm.X0] = _heap;
                    long size = regs[arm.X0]*regs[arm.X1];
                    _heap += (int)size;
                    pc = (int)rval;  // unwind pc; calloc is complete and returns
                }
                rdid = -1;
                pc -= 4;
                break;
            }
            case 0x9A: {        // conditional select(csel)
                int rm = (ir >> 16) & 0x1F;
                switch( (ir >> 12) & 0xF ) {
                case 0x0: if(Z)  rval = regs[rm]; break; // eq
                case 0x1: if(!Z) rval = regs[rm]; break; // ne
                case 0x2: if(C)  rval = regs[rm]; break; // cs
                case 0x3: if(!C) rval = regs[rm]; break;
                case 0x4: if(N) rval  =  regs[rm]; break; // mi
                case 0x5: if(!N) rval = regs[rm]; break; // pl
                case 0x6: if(V) rval = regs[rm]; break; // vs
                case 0x7: if(!V) rval = regs[rm]; break; // vc
                case 0x8: if(C && !Z) rval = regs[rm]; break; // hi
                case 0x9: if(!C || Z) rval = regs[rm]; break; // ls
                case 0xA: if(N == V) rval = regs[rm]; break; // ge
                case 0xB: if(N != V) rval = regs[rm]; break; // lt
                case 0xC: if(!Z && N == V) rval  = regs[rm]; break; // gt
                case 0xD: if(Z || N != V) rval = regs[rm]; break; // le
                case 0xE: rval = regs[rm]; break; // always executed(al)
                case 0xF: rval =  regs[(ir >> 5) & 0x1F];
                }
                break;
            }
            case 0x9B: {        // mul
                int rn = (ir >>  5) & 0x1F;
                int rm = (ir >> 16) & 0x1F;
                rval = regs[rn] * regs[rm];
                break;
            }
            case 0x9E: {
                // scvtf
                is_f = true;
                int rs1 = (ir >> 5) & 0x1F;
                frval = (double)regs[rs1];
                break;
            }
            case 0xAA: {
                // MOV(register)
                int rm = (ir >> 16) & 0x1F;
                rval = regs[rm];
                break;
            }
            case 0xD3: {
                // lsl
                int immr = ir << 10 >> 26;
                rval     = 64 - immr;
                break;
            }
            case 0xD2: { // movz
                rval = (ir >> 5) & 0x7FFF;
                break;
            }
            case 0xD6: {
                rdid = -1;
                if(ld4s(pc + 4) == 0) {
                    break outer;
                }
                break;
            }
            case 0xEB: {
                // subs(shifted register)
                int rn = (ir >> 5) &  0x1F;
                int rm = (ir >> 16) & 0x1F;
                long lhs = regs[rn];
                long rhs = regs[rm];
                rval = lhs - rhs;

                // set flags as a side effect
                Z = (rval == 0);
                N = (rval < 0);
                C = lhs >= rhs;

                boolean sign_lhs = (lhs >>> 63) != 0;
                boolean sign_rhs = (rhs >>> 63) != 0;
                boolean sign_res = (rval >>> 63) != 0;
                V = (sign_lhs != sign_rhs) && (sign_res != sign_lhs);
                rdid = -1;
                break;
            }
            case 0xF1: {
                // subs(immediate)
                int rn = (ir >> 5) & 0x1F;
                int immediate = ir << 10 >> 20;
                long lhs = regs[rn];

                rval = lhs - immediate;

                // set flags as a side effect
                Z = (rval == 0);
                N = (rval < 0);
                C = lhs >= immediate;

                boolean sign_lhs = (lhs >>> 63) != 0;
                boolean sign_rhs = (immediate >>> 31) != 0;
                boolean sign_res = (rval >>> 63) != 0;
                V = (sign_lhs != sign_rhs) && (sign_res != sign_lhs);
                rdid = -1;
                break;
            }
            case 0xF8: { // ld1/st1 [reg+reg]
                int base = ir >>  5 & 0x1F;
                int idx  = ir >> 10 & 0x1F;
                int addr = (int)regs[base+idx];
                if( (ir <<8 >> 30)== 1 )  rval = _buf[addr];       // ld1
                else { _buf[addr] = (byte)regs[rdid]; rdid = -1; } // st1
                break;
            }
            case 0xF9: { // ld1/st1 [reg+imm]
                //    cond  op
                // 0b 1111 010X  // Load/store word and unsigned byte
                // 0b 1111 0110  // Load/store word and unsigned byte
                // 0b 1111 0111  // media
                int base = ir >> 5 & 0x1F;
                int imm12 = (ir >> 10) & 0xFFF;
                int addr = (int)(regs[base] + imm12);
                if( (ir <<8 >> 30)== 1 )  rval = _buf[addr];       // ld1
                else { _buf[addr] = (byte)regs[rdid]; rdid = -1; } // st1
                break;
            }

            default: trap = (2+1);
            }

            if( trap != 0 ) { _pc = pc; break; }
            if( rdid != -1 ) {
                if( is_f ) { fregs[rdid] = frval; is_f = false; }
                else          regs[rdid] =  rval;
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
