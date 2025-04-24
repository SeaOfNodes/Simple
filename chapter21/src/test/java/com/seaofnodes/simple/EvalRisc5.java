 package com.seaofnodes.simple;

/** Simple RISC5 CPU Emulator
 *
 * Original works credit Charles Lohr.
 * This is a complete rewrite in Java.
 *
 * Load code into memory and go!
 */

import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.node.cpus.riscv.riscv;

import java.util.Arrays;

 public class EvalRisc5 {

    // Memory image, always 0-based
    public final byte[] _buf;

    // GPRs
    final long[] regs;
    // FRs
    final double[] fregs;
    // PC
    int _pc;

    // Start of free memory for allocation
    int _heap;

    // Cycle counters
    int _cycle;

    EvalRisc5( byte[] buf, int stackSize ) {
        _buf  = buf;
        regs  = new long[32];
        fregs = new double[32];
        // Stack grows down, heap grows up
        regs[riscv.RSP] = _heap = stackSize;
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
    public void st4(int x, int  val) {st2(x,val); st2(x+2,val>>16); }
    public void st8(int x, long val) { st4(x,(int)val); st2(x+4,(int)(val>>32)); }

    // Note: only a few bits are used.  (Machine = 3, User = 0)
    // Bits 0..1 = privilege.
    // Bit 2 = WFI (Wait for interrupt)
    // Bit 3+ = Load/Store reservation LSBs.
    int _extraflags;

    public int step( int maxops ) {
        int trap = 0;
        long rval = 0;
        double frval = 0;
        int pc = _pc;
        int cycle = _cycle;
        boolean is_f = false;
        outer:
        for( int icount = 0; icount < maxops; icount++ ) {
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
            int rdid = (ir >> 7) & 0x1f;

            switch( ir & 0x7f ) {
            case 0x37: // LUI (0b0110111)
                rval = ( ir & 0xfffff000 );
                break;
            case 0x17: // AUIPC (0b0010111)
                rval = pc + ( ir & 0xfffff000 );
                break;
            case 0x6F: { // JAL (0b1101111)
                int reladdy = ((ir & 0x80000000)>>11) | ((ir & 0x7fe00000)>>20) | ((ir & 0x00100000)>>9) | ((ir&0x000ff000));
                if( (reladdy & 0x00100000)!=0 ) reladdy |= 0xffe00000; // Sign extension.
                rval = pc + 4;
                pc = pc + reladdy - 4;
                break;
            }
            case 0x67: { // JALR (0b1100111)
                int imm_se = ir >> 20; // Sign extend 12 bits
                rval = pc + 4;
                pc = ((int)regs[ (ir >> 15) & 0x1f ] + imm_se) & ~1;
                // Return from top-level ; exit sim
                if( pc==0 ) {
                    if( rdid!=0 ) regs[rdid] = rval; // Write PC into register before exit
                    break outer;
                }
                // Inline CALLOC effect
                if( pc == Encoding.SENTINAL_CALLOC ) {
                    int size = (int)(regs[10]*regs[11]);
                    regs[10] = _heap; // Return next free address
                    // Pre-zeroed; epsilon (null) collector, never recycles memory so always zero
                    // Arrays.fill(_buf,_heap,_heap+size, (byte) 0 );
                    _heap += size;
                    pc = (int)(rval - 4); // Unwind PC, as-if returned from calloc
                } else
                    pc -= 4;
                break;
            }
            case 0x63: { // Branch (0b1100011)
                int immm4 = ((ir & 0xf00)>>7) | ((ir & 0x7e000000)>>20) | ((ir & 0x80) << 4) | ((ir >> 31)<<12);
                if( (immm4 & 0x1000)!=0 ) immm4 |= 0xffffe000;
                long rs1 = regs[(ir >> 15) & 0x1f];
                long rs2 = regs[(ir >> 20) & 0x1f];
                immm4 = pc + immm4 - 4;
                rdid = 0;
                if( switch( (ir >> 12) & 0x7 ) {
                    case 0 -> rs1 == rs2;
                    case 1 -> rs1 != rs2;
                    case 4 -> rs1 <  rs2;
                    case 5 -> rs1 >= rs2;
                    case 6 -> Long.compareUnsigned(rs1,rs2) <  0;  //BLTU
                    case 7 -> Long.compareUnsigned(rs1,rs2) >= 0;  //BGEU
                    default -> { trap = (2+1); yield false; }
                    } )   pc = immm4;
                break;
            }
            case 0x03: { // Load (0b0000011)
                int rs1 = (int)regs[(ir >> 15) & 0x1f]; // Address chop to 32b
                int imm = ir >> 20;
                int imm_se = imm | (( (imm & 0x800)!=0 ) ? 0xfffff000 : 0 );
                int rsval = rs1 + imm_se;

                if( rsval >= _buf.length-3 ) {
                    trap = (5+1);
                    rval = rsval;
                } else {
                    switch( ( ir >> 12 ) & 0x7 ) {
                        //LB, LH, LW, LBU, LHU
                    case 0: rval = ld1s( rsval ); break;
                    case 1: rval = ld2s( rsval ); break;
                    case 2: rval = ld4s( rsval ); break;
                    case 3: rval = ld8 ( rsval ); break;
                    case 4: rval = ld1z( rsval ); break;
                    case 5: rval = ld2z( rsval ); break;
                    case 6: rval = ld4s( rsval ); break;
                    default: trap = (2+1);
                    }
                }
                break;
            }
            // floats
            case 0x7: {
                is_f = true;
                int rs1 = (int)regs[(ir >> 15) & 0x1f]; // Address chop to 32b
                int imm = ir >> 20;
                int imm_se = imm | (( (imm & 0x800)!=0 ) ? 0xfffff000 : 0 );
                int rsval = rs1 + imm_se;

                if( rsval >= _buf.length-3 ) {
                    trap = (5+1);
                    rval = rsval;
                } else {
                    switch( ( ir >> 12 ) & 0x7 ) {
                    case 2: frval = ld4f( rsval ); break;
                    case 3: frval = ld8f( rsval ); break;
                    default: trap = (2+1);
                    }
                }
                break;
            }

            case 0x23: { // Store 0b0100011
                int  rs1 = (int)regs[(ir >> 15) & 0x1f]; // Address chop to 32b
                long rs2 = regs[(ir >> 20) & 0x1f];
                int addy = ( ( ir >> 7 ) & 0x1f ) | ( ( ir & 0xfe000000 ) >> 20 );
                if( (addy & 0x800)!=0 ) addy |= 0xfffff000;
                addy += rs1;
                rdid = 0;
                if( addy >= _buf.length-3 ) {
                    trap = (7+1); // Store access fault.
                    rval = addy;
                } else {
                    switch( ( ir >> 12 ) & 0x7 ) { //SB, SH, SW
                    case 0: st1( addy, (int)rs2 ); break;
                    case 1: st2( addy, (int)rs2 ); break;
                    case 2: st4( addy, (int)rs2 ); break;
                    case 3: st8( addy,      rs2 ); break;
                    default: trap = (2+1);
                    }
                }
                break;
            }
            case 0x13:   // Op-immediate 0b0010011
            case 0x33: { // Op           0b0110011
                int imm = ir >> 20;
                imm = imm | (( (imm & 0x800)!=0 ) ? 0xfffff000 : 0);
                long rs1 = regs[(ir >> 15) & 0x1f];
                boolean is_reg = (ir & 0x20)!=0;
                long rs2 = is_reg ? regs[imm & 0x1f] : imm;

                // Insert the detection here
                int rs1id = (ir >> 15) & 0x1f;
                int funct3 = (ir >> 12) & 0x7;

                if( is_reg && ( ir & 0x02000000 )!=0 ) {
                    switch( (ir>>12)&7 ) { //0x02000000 = RV32M
                    case 0: rval = rs1 * rs2; break; // MUL
                    case 1: rval = (int)((long)((int)rs1) * (long)((int)rs2)) >> 32; break; // MULH
                    case 2: rval = (int)((long)((int)rs1) * (long)rs2) >> 32; break; // MULHSU
                    case 3: rval = (int)((long)rs1 * (long)rs2) >> 32; break; // MULHU
                    case 4: if( rs2 == 0 ) rval = -1; else rval = ((int)rs1 == Integer.MIN_VALUE && (int)rs2 == -1) ? rs1 : ((int)rs1 / (int)rs2); break; // DIV
                    case 5: if( rs2 == 0 ) rval = 0xffffffff; else rval = rs1 / rs2; break; // DIVU
                    case 6: if( rs2 == 0 ) rval = rs1; else rval = ((int)rs1 == Integer.MIN_VALUE && (int)rs2 == -1) ? 0 : ((int)((int)rs1 % (int)rs2)); break; // REM
                    case 7: if( rs2 == 0 ) rval = rs1; else rval = rs1 % rs2; break; // REMU
                    }
                } else {
                    rval = switch( (ir >> 12) & 7 ) { // These could be either op-immediate or op commands.  Be careful.
                    case 0 -> (is_reg && (ir & 0x40000000) != 0) ? (rs1 - rs2) : (rs1 + rs2);
                    case 1 -> rs1 << (rs2 & 0x1F);
                    case 2 -> (rs1 < rs2) ? 1 : 0;
                    case 3 -> (rs1 < rs2) ? 1 : 0;
                    case 4 -> rs1 ^ rs2;
                    case 5 -> (ir & 0x40000000) != 0 ? (((int) rs1) >> (rs2 & 0x1F)) : (rs1 >> (rs2 & 0x1F));
                    case 6 -> rs1 | rs2;
                    case 7 -> rs1 & rs2;
                    default -> rval;
                  };
                }
                break;
            }
            case 0x0f:      // 0b0001111
                rdid = 0;   // fencetype = (ir >> 12) & 0b111; We ignore fences in this impl.
                break;
            case 0x73: // Zifencei+Zicsr  (0b1110011)
                trap = (2+1); // Note micrrop 0b100 == undefined.
                break;

            case 0x2f: { // RV32A (0b00101111)
                long rs1 = regs[(ir >> 15) & 0x1f];
                long rs2 = regs[(ir >> 20) & 0x1f];
                int irmid = ( ir>>27 ) & 0x1f;

                // We don't implement load/store from UART or CLNT with RV32A here.
                if( rs1 >= _buf.length-3 ) {
                    trap = (7+1); //Store/AMO access fault
                    rval = rs1;
                } else {
                    rval = ld4s( (int)rs1 );

                    // Referenced a little bit of https://github.com/franzflasch/riscv_em/blob/master/src/core/core.c
                    boolean dowrite = true;
                    switch( irmid )                                            {
                    case 2: //LR.W (0b00010)
                        dowrite = false;
                        _extraflags = (_extraflags & 0x07) | (int)(rs1<<3);
                        break;
                    case 3: { //SC.W (0b00011) (Make sure we have a slot, and, it's valid)
                        boolean xflag = _extraflags >> 3 != ( rs1 & 0x1fffffff );
                        rval = xflag ? 1 : 0;  // Validate that our reservation slot is OK.
                        dowrite = !xflag; // Only write if slot is valid.
                        break;
                    }
                    case  0: rs2 += rval; break; // AMOADD.W  (0b00000)
                    case  1:              break; // AMOSWAP.W (0b00001)
                    case  4: rs2 ^= rval; break; // AMOXOR.W  (0b00100)
                    case  8: rs2 |= rval; break; // AMOOR.W   (0b01000)
                    case 12: rs2 &= rval; break; // AMOAND.W  (0b01100)
                    case 16: rs2 = Math.min( rs2, rval ); break; //AMOMIN.W (0b10000)
                    case 20: rs2 = Math.max( rs2, rval ); break; //AMOMAX.W (0b10100)
                    case 24: rs2 = Math.min( rs2, rval ); break; //AMOMINU.W (0b11000)
                    case 28: rs2 = Math.max( rs2, rval ); break; //AMOMAXU.W (0b11100)
                    default: trap = (2+1); dowrite = false; break; //Not supported.
                    }
                    if( dowrite ) st4( (int)rs1, (int)rs2 );
                }
                break;
            }

            case 0x53: {
                is_f = true;
                int rs1  = (ir >> 15) & 0x1F;
                int rs2  = (ir >> 20) & 0x1F;
                double lhs = fregs[rs1];
                double rhs = fregs[rs2];
                int func5 = (ir >> 27) & 0x1F;
                int func2 = (ir >> 25) & 0x3;
                if( func2 != 1 ) throw Utils.TODO(); // 1==double
                switch( func5 ) {
                case  0:  frval = lhs + rhs;  break;  // fadd.d
                case  1:  frval = lhs - rhs;  break;  // fsub.d
                case  2:  frval = lhs * rhs;  break;  // fmul.d
                case  3:  frval = lhs / rhs;  break;  // fdiv.d
                case  5:  frval = Math.min(lhs,rhs); break; // fmin.d, also used for move
                case 20:
                    is_f = false; // Output into a GPR
                    rval = switch( (ir >> 12) & 7 ) {
                    case 0 -> lhs <= rhs ? 1 : 0;
                    case 1 -> lhs == rhs ? 1 : 0;
                    case 2 -> lhs <  rhs ? 1 : 0;
                    default -> throw Utils.TODO();
                    };
                    break;
                case 26:  frval = (double)regs[rs1]; break; // fcvt.w.d
                default:
                    trap  = (2+1); // Fault: Invalid opcode.
                    throw Utils.TODO();
                }
                break;
            }

            default: trap = (2+1); // Fault: Invalid opcode.
            }

            // If there was a trap, do NOT allow register writeback.
            if( trap!=0 ) {
                _pc = pc;
                break;
            }

            if( rdid!=0 || is_f) {
                if(is_f) {fregs[rdid] = frval; is_f = false;} // Write back register.
                else regs[rdid] = rval; // Write back register.
            }

            pc += 4;
        }

        // Handle traps and interrupts.
        if( trap!=0 )
            return trap;

        _cycle = cycle;
        _pc = pc;
        return 0;
    }
}
