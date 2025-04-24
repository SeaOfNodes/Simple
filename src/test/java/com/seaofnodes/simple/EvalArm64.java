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
    int SENTINEL_CALLOC = -4;
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
    public int  ld2z(int x) {
        return ld1z(x) | ld1z(x+1)<< 8; }
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
            // [31:26]
            int opcode2 = (ir >> 26) & 0x3F;

            switch(opcode2) {
                case 5: {
                    //b
                    int imm26 = ir << 6 >> 6;

                    pc = pc + (imm26 * 4) - 4;
                    // won't hit pc += 4 at the bottom because continue
                    // do it manually
                    pc += 4;
                    rdid = -1;
                    continue ;
                }
                case 0x25: {
                    // bl (just calloc)
                    rval = pc + 4;
                    regs[arm.X30] = rval;
                    if(pc + 4 == 0) {
                        regs[rdid] = rval;
                        break outer;
                    }
                    int imm26 = (ir & 0x03FFFFFF);
                    int imm = (imm26 << 6) >> 6;
                    pc = pc + (imm << 2) ;
                    if (pc == SENTINEL_CALLOC) {
                        long size = regs[arm.X0]*regs[arm.X1];
                        regs[arm.X0] = _heap;
                        _heap += (int)size;
                        // unwind pc
                        pc = (int)rval;
                    }
                    continue;
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
                case 0x38: {
                    // STRB/LDRB(register)
                    int opc = ir >> 21 & 0x7;
                    int rn = (ir >> 5) & 0x1F;
                    int rm = (ir >> 16) & 0x1F;
                    // full opcode
                    opcode1 = (ir >> 21) & 0x7FF;
                    switch(opc) {
                        case 0x1: {
                            // store
                            if(opcode1 == arm.OP_STORE_R_8) {
                                st1((int)regs[rn] + (int)regs[rm], (int)regs[rdid]);
                            }else {
                                trap = (2+1);
                            }
                            rdid = -1;
                            break;
                        }
                        case 0x3: {
                            // load
                            if(opcode1 == arm.OP_LOAD_R_8) {
                                rval = ld1s((int)regs[rn] + (int)regs[rm]);
                            } else {
                                trap = (2+1);
                            }
                            break;
                        }
                        default: trap = (2+1);
                    }
                    break;
                }
                case 0x39: {
                    // STRB/LDRB(immediate)
                    // str/ldr(immediate)
                    // rn
                    int base = ir >> 5 & 0x1F;
                    int imm = (ir >> 10) & 0xFFF;
                    rval = regs[base] + imm;
                    int opc = (ir >> 22) & 0x3;
                    // full opcode
                    opcode1 = (ir >> 22) & 0x3FF;

                    switch(opc) {
                        case 0x0: {
                            // store
                            if(opcode1 == arm.OP_STORE_IMM_8) {
                                st1((int)rval, (int)regs[rdid]);
                            } else trap = (2+1);
                            rdid = -1;

                            break;
                        }
                        case 0x1: {
                            // LDR (immediate)
                            if(opcode1 == arm.OP_LOAD_IMM_8) {
                                rval = ld1s((int)rval);
                            } else trap = (2+1);
                            rdid = -1;
                            break;
                        }
                        default: trap = (2+1);
                    }
                    break;
                }
                case 0x78: {
                    // STRH/LDRH(register)
                    int opc = ir >> 21 & 0x7;
                    int rn = (ir >> 5) & 0x1F;
                    int rm = (ir >> 16) & 0x1F;
                    // full opcode
                    opcode1 = (ir >> 21) & 0x7FF;
                    switch(opc) {
                        case 0x1: {
                            // store
                            if(opcode1 == arm.OP_STORE_R_16) {
                                st1((int)regs[rn] + (int)regs[rm], (int)regs[rdid]);
                            }else {
                                trap = (2+1);
                            }
                            rdid = -1;
                            break;
                        }
                        case 0x3: {
                            // load
                            if(opcode1 == arm.OP_LOAD_R_16) {
                                rval = ld1s((int)regs[rn] + (int)regs[rm]);
                            } else {
                                trap = (2+1);
                            }
                            break;
                        }
                        default: trap = (2+1);
                    }
                    break;
                }

                case 0x79: {
                    // STRH/LDRH(immediate)
                    int base = ir >> 5 & 0x1F;
                    int imm = (ir >> 10) & 0xFFF;
                    imm *= 2;
                    rval = regs[base] + imm;
                    int opc = (ir >> 22) & 0x3;
                    // full opcode
                    opcode1 = (ir >> 22) & 0x3FF;

                    switch(opc) {
                        case 0x0: {
                            // store
                            if(opcode1 == arm.OP_STORE_IMM_16) {
                                st2((int)rval, (int)regs[rdid]);
                            } else trap = (2+1);
                            rdid = -1;

                            break;
                        }
                        case 0x1: {
                            // LDR (immediate)
                            if(opcode1 == arm.OP_LOAD_IMM_16) {
                                rval = ld2s((int)rval);
                            } else trap = (2+1);
                            rdid = -1;
                            break;
                        }
                        default: trap = (2+1);
                    }
                    break;
                }


                case 0x5c: {
                    // ldr
                    is_f = true;
                    // address = delta
                    int address = ir << 8 >> 13;
                    address *= 4;
                    if(address >= _buf.length-3) trap = (5 + 1);
                    frval = ld8f(pc + address);
                    break;
                }
                case 0xB9: {
                    // str/ldr(immediate) 32 bits
                    int base = ir >> 5 & 0x1F;
                    int imm = (ir >> 10) & 0xFFF;
                    imm *= 4;

                    rval = regs[base] + imm;
                    int opc = (ir >> 22) & 0x3;

                    opcode1 = (ir >> 22) & 0x3FF;
                    switch(opc) {
                        case 0x0: {
                            // store
                            if(opcode1 == arm.OP_STORE_IMM_32) {
                                st4((int)rval, (int)regs[rdid]);
                            } else trap = (2+1);

                            rdid = -1;
                            break;
                        }
                        case 0x1: {
                            // LDR (immediate)
                            if(opcode1 == arm.OP_LOAD_IMM_32) {
                                rval = ld4s((int) rval);
                            } else trap = (2+1);
                            break;
                        }
                        default: trap = (2+1);
                    }
                    break;
                }
                case 0xF9: {
                    // str/ldr(immediate) 64 bits
                    int base = ir >> 5 & 0x1F;
                    int imm = (ir >> 10) & 0xFFF;

                    imm *= 8;

                    rval = regs[base] + imm;
                    int opc = (ir >> 22) & 0x3;
                    // full opcode
                    opcode1 = (ir >> 22) & 0x3FF;
                    switch(opc) {
                        case 0x0: {
                            // store
                            if(opcode1 == arm.OP_STORE_IMM_64) {
                                st8((int)rval, regs[rdid]);
                            }  else trap = (2+1);

                            rdid = -1;
                            break;
                        }
                        case 0x1: {
                            // LDR (immediate)
                            if(opcode1 == arm.OP_LOAD_IMM_64) {
                                rval = ld8((int)rval);
                            } else trap = (2+1);
                            break;
                        }
                        default: trap = (2+1);
                    }
                    break;
                }
                case 0xf8: {
                    // LDR/STR (register + index) 64 bits
                    int opc = ir >> 21 & 0x7;
                    int rn = (ir >> 5) & 0x1F;
                    int rm = (ir >> 16) & 0x1F;
                    // full opcode
                    opcode1 = (ir >> 21) & 0x7FF;
                    switch(opc) {
                        case 0x1: {
                            if(opcode1 == arm.OP_STORE_R_64) {
                                st8((int)regs[rn] + (int)regs[rm], regs[rdid]);
                            }else {
                                trap = (2+1);
                            }
                            // store(expected to store the length here)

                            rdid = -1;
                            break;
                        }
                        case 0x3: {
                            // load
                            if(opcode1 == arm.OP_LOAD_R_64) {
                                rval = ld8((int) (regs[rn] + regs[rm]));
                            } else {
                                trap = (2+1);
                            }
                            break;
                        }
                        default: trap = (2+1);
                    }

                    break;
                }

                case 0xB8: {
                    // LDR/STR (register + index) 32 bits
                    int opc = ir >> 21 & 0x7;
                    int rn = (ir >> 5) & 0x1F;
                    int rm = (ir >> 16) & 0x1F;
                    // full opcode
                    opcode1 = (ir >> 21) & 0x7FF;
                    switch(opc) {
                        case 0x1: {
                            if(opcode1 == arm.OP_STORE_R_32) {
                                st4((int)regs[rn] + (int)regs[rm], (int)regs[rdid]);
                            }else {
                                trap = (2+1);
                            }
                            // store(expected to store the length here)

                            rdid = -1;
                            break;
                        }
                        case 0x3: {
                            // load
                            if( opcode1 == arm.OP_LOAD_R_32) {
                                rval = ld4s((int) (regs[rn] + regs[rm]));
                            } else {
                                trap = (2+1);
                            }
                            break;
                        }
                        default: trap = (2+1);
                    }

                    break;
                }

                case 0xFC: {
                    // LDR/STR (register + index) float 64 bits
                    int opc = ir >> 21 & 0x7;
                    int rn = (ir >> 5) & 0x1F;
                    int rm = (ir >> 16) & 0x1F;
                    is_f = true;
                    // full opcode
                    opcode1 = (ir >> 21) & 0x7FF;
                    switch(opc) {
                        case 0x1: {
                            if(opcode1 == arm.OPF_STORE_R_64) {
                                st8((int)regs[rn] + (int)regs[rm], (int)regs[rdid]);
                            }else {
                                trap = (2+1);
                            }
                            // store(expected to store the length here)

                            rdid = -1;
                            break;
                        }
                        case 0x3: {
                            // load
                            if(opcode1 == arm.OPF_LOAD_R_64) {
                                frval = ld8f((int) (regs[rn] + regs[rm]));
                            } else {
                                trap = (2+1);
                            }
                            break;
                        }
                        default: trap = (2+1);
                    }

                    break;
                }

                case 0xBC: {
                    // LDR/STR (register + index) float 32 bits
                    int opc = ir >> 21 & 0x7;
                    int rn = (ir >> 5) & 0x1F;
                    int rm = (ir >> 16) & 0x1F;
                    is_f = true;
                    // full opcode
                    opcode1 = (ir >> 21) & 0x7FF;
                    switch(opc) {
                        case 0x1: {
                            if(opcode1 == arm.OPF_STORE_R_32) {
                                st4((int)regs[rn] + (int)regs[rm], (int)regs[rdid]);
                            }else {
                                trap = (2+1);
                            }
                            // store(expected to store the length here)

                            rdid = -1;
                            break;
                        }
                        case 0x3: {
                            // load
                            if(opcode1 == arm.OPF_LOAD_R_32) {
                                frval = ld4f((int) (regs[rn] + regs[rm]));
                            } else {
                                trap = (2+1);
                            }
                            break;
                        }
                        default: trap = (2+1);
                    }

                    break;
                }

                case 0xBD: {
                    // LDR/STR(immediate) 32 bits floats
                    int base = ir >> 5 & 0x1F;
                    int imm = (ir >> 10) & 0xFFF;

                    imm *= 4;
                    is_f = true;
                    rval = regs[base] + imm;
                    int opc = (ir >> 22) & 0x3;
                    // full opcode
                    opcode1 = (ir >> 22) & 0x3FF;
                    switch(opc) {
                        case 0x0: {
                            // store
                            if(opcode1 == arm.OPF_STORE_IMM_32) {
                                st4((int)rval, (int)regs[rdid]);
                            }  else trap = (2+1);

                            rdid = -1;
                            break;
                        }
                        case 0x1: {
                            // LDR (immediate)
                            if(opcode1 == arm.OPF_LOAD_IMM_32) {
                                frval = ld4s((int)rval);
                            } else trap = (2+1);
                            break;
                        }
                        default: trap = (2+1);
                    }
                    break;
                }
                case 0xFD: {
                    // LDR/STR(immediate) 64 bits floats
                    int base = ir >> 5 & 0x1F;
                    int imm = (ir >> 10) & 0xFFF;

                    imm *= 4;
                    is_f = true;
                    rval = regs[base] + imm;
                    int opc = (ir >> 22) & 0x3;
                    // full opcode
                    opcode1 = (ir >> 22) & 0x3FF;
                    switch(opc) {
                        case 0x0: {
                            // store
                            if(opcode1 == arm.OPF_STORE_IMM_64) {
                                st8((int)rval, regs[rdid]);
                            }  else trap = (2+1);

                            rdid = -1;
                            break;
                        }
                        case 0x1: {
                            // LDR (immediate)
                            if(opcode1 == arm.OPF_LOAD_IMM_64) {
                                frval = ld8((int)rval);
                            } else trap = (2+1);
                            break;
                        }
                        default: trap = (2+1);
                    }
                    break;
                }
                case 0x9b: {
                    // mul
                    int rn = (ir >> 5) & 0x1F;
                    int rm = (ir >> 16) & 0x1F;
                    rval = regs[rn] * regs[rm];
                    break;
                }
                case 0x54: {
                    // b.cond
                    int imm19 = ir << 8 >> 13;
                    imm19 = pc + (imm19 * 4) - 4;
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
                case 0x9a: {
                    // conditional select(csel)
                    int rm = (ir >> 16) & 0x1F;
                    switch((ir >> 12) & 0xF) {
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
                        default: {
                            rval =  regs[(ir >> 5) & 0x1F];
                        }
                    }
                    break;
                }
                case 0xeb: {
                    // subs(shifted register)
                    int rn = (ir >> 5)  & 0x1F;
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
                    boolean sign_rhs = (immediate >>> 63) != 0;
                    boolean sign_res = (rval >>> 63) != 0;
                    V = (sign_lhs != sign_rhs) && (sign_res != sign_lhs);
                    rdid = -1;
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
                    // only hit it twice
                    rval = regs[rn] + immediate;
                    break;
                }
                case 0xD3: {
                    // lsl(immediate)
                    int imm = (ir >> 16) & 0x3F;
                    int rn = (ir >> 5) & 0x1F;
                    rval     = regs[rn] << (64 - imm);
                    break;
                }
                case 0x93: {
                    // asr(immediate)
                    int imm = (ir >> 16) & 0x3F;
                    int rn = (ir >> 5) & 0x1F;
                    rval     = regs[rn] >> (imm);
                    break;
                }
                case 0x92: {
                    // and(immediate)
                    int imm12 = (ir >> 10) & 0x1FFF;
                    long immediate = arm.decodeImm12(imm12);
                    int rn = (ir >> 5) & 0x1F;
                    rval = regs[rn] & immediate;
                    break;
                }
                case 0xAA: {
                    // MOV(register)
                    int rm = (ir >> 16) & 0x1F;
                    rval = regs[rm];
                    break;
                }
                case 0xD6: {
                    rdid = -1;
                    if(ld4s(pc + 4) == 0) {
                        break outer;
                    }
                }
                // bitwise
                case 0x8A: {
                    // AND
                    int rn = (ir >> 5) & 0x1F;
                    int rm = (ir >> 16) & 0x1F;
                    rval = regs[rn] & regs[rm];
                    break;
                }
                // floats
                case 0x1E: {
                    is_f = true;
                    int rs1 = (ir >> 5) & 0x1F;
                    int rs2 = (ir >> 16) & 0x1F;

                    int op = (ir >> 10) & 0x3F;
                    switch(op) {
                        case 16: frval = fregs[rs1]; break; // fmov
                        case 10:frval = fregs[rs1] + fregs[rs2]; break; // fadd

                        case 6: if (fregs[rs2] == 0) frval = 0; else frval = fregs[rs1] / fregs[rs2]; break; // fdiv
                        case 8: {
                            // fcmp
                            double lhs  = fregs[rs1];
                            double rhs = fregs[rs2];

                            Z = (lhs == rhs);
                            N = (lhs < rhs);
                            C = lhs >= rhs;
                            V = Double.isNaN(lhs) || Double.isNaN(rhs);
                            rdid = -1;

                            break;
                        }
                        case 2: frval = fregs[rs1] * fregs[rs2]; break; // fmul
                        case 14: frval = fregs[rs1] - fregs[rs2]; break; // fsub
                        default: trap = (2+1);
                    }
                    break;
                }
                case 0x9e: {
                    // scvtf
                    is_f = true;
                    int rs1 = (ir >> 5) & 0x1F;
                    frval = (double)regs[rs1];
                    break;
                }
                default: trap = (2+1);
            }
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
