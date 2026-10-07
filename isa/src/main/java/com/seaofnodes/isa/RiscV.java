package com.seaofnodes.isa;

/** Instruction fields and concrete operand encodings; no compiler state. */
public final class RiscV {
    private RiscV() {}

    public static final int OP_LOAD    = 0b00_000_11;
    public static final int OP_LOADFP  = 0b00_001_11;
    public static final int OP_CUSTOM0 = 0b00_010_11;
    public static final int OP_IMM     = 0b00_100_11;
    public static final int OP_AUIPC   = 0b00_101_11;
    public static final int OP_STORE   = 0b01_000_11;
    public static final int OP_STOREFP = 0b01_001_11;
    public static final int OP_CUSTOM1 = 0b01_010_11;
    public static final int OP         = 0b01_100_11;
    public static final int OP_LUI     = 0b01_101_11;
    public static final int OP_CUSTOM2 = 0b10_010_11;
    public static final int OP_FP      = 0b10_100_11;
    public static final int OP_BRANCH  = 0b11_000_11;
    public static final int OP_JALR    = 0b11_001_11;
    public static final int OP_RESERVED= 0b11_010_11;
    public static final int OP_JAL     = 0b11_011_11;

    public enum RM {
        RNE,       // Round to Nearest, ties to Even
        RTZ,       // Round towards Zero
        RDN,       // Round Down
        RUP,       // Round Up
        DIRECT,    // Round to Nearest, ties to Max Magnitude
        RESERVED1, // Reserved for futue use
        RESERVED2, // Reserved for future use
        DYN,       // In instruction’s rm field, selects dynamic rounding mode; In Rounding Mode register, reserved
    }

    public static int r_type(int opcode, int rd, int func3, int rs1, int rs2, int func7) {
        assert 0 <= rs1 && rs1 < 32;
        assert 0 <= rs2 && rs2 < 32;
        assert 0 <= rd &&  rd  < 32;
        return (func7 << 25) | (rs2 << 20) | (rs1 << 15) | (func3 << 12) | (rd << 7) | opcode;
    }

    public static int u_type(int opcode, int rd, int imm20) {
        assert 0 <= rd && rd < 32;
        return (imm20 << 12) | (rd << 7) | opcode;
    }

    public static int j_type(int opcode, int rd, int delta) {
        assert -(1L<<20) <= delta && delta < (1L<<20);
        assert 0 <= rd && rd < 32;
        // Messy branch offset encoding
        // 31 30-21 20 19-12 11-7  6-0
        // 20 10- 1 11 19-12 rpc   JAL
        assert (delta&1)==0;    // Low bit is always zero, not encoded
        int imm10_01 = (delta>> 1) & 0x3FF;
        int imm11    = (delta>>11) &     1;
        int imm12_19 = (delta>>12) &  0xFF;
        int imm20    = (delta>>20) &     1;
        int bits = imm20<<19 | imm10_01 << 9 | imm11 << 8 | imm12_19;
        return bits << 12 | rd << 7 | opcode;
    }

    /** FCVT.L.D with RTZ saturates overflow; explicitly map either NaN to zero. */
    public static void floatToInteger(CodeSink out, int dst, int src) {
        out.add4(r_type(OP_FP,dst,1,src,0,0x71)); // FCLASS.D
        out.add4(i_type(OP_IMM,dst,7,dst,0x300)); // ANDI: NaN classification bits
        out.add4(b_type(OP_BRANCH,1,(short)dst,(short)0,12)); // BNE nan
        out.add4(r_type(OP_FP,dst,RM.RTZ.ordinal(),src,2,0x61)); // FCVT.L.D
        out.add4(j_type(OP_JAL,0,8));             // J done
        out.add4(i_type(OP_IMM,dst,0,0,0));       // nan: LI dst,0
    }

    public static int i_type(int opcode, int rd, int func3, int rs1, int imm12) {
        assert 0 <= rd  &&  rd  < 32;
        assert 0 <= rs1 &&  rs1 < 32;
        assert opcode >= 0 && func3 >=0 && imm12 >= 0; // Zero-extend by caller
        return  (imm12 << 20) | (rs1 << 15) | (func3 << 12) | (rd << 7) | opcode;
    }

    public static int s_type(int opcode, int func3, int rs1, int rs2, int imm12) {
        assert 0 <= rs1 &&  rs1 < 32;
        assert 0 <= rs2 &&  rs2 < 32;
        assert 0 <= func3;

        assert imm12 >= 0;      // Masked to high zero bits by caller
        int imm_lo = imm12 & 0x1F;
        int imm_hi = imm12 >> 5;
        return (imm_hi << 25) | (rs2 << 20) | (rs1 << 15) | (func3 << 12) | (imm_lo << 7) | opcode;
    }

    public static int b_type(int opcode, int func3, short rs1, short rs2, int delta) {
        assert 0 <= rs1 && rs1 < 32;
        assert 0 <= rs2 && rs2 < 32;
        assert -4*1024 <= delta && delta < 4*1024;
        assert (delta&1)==0;    // Low bit is always zero, not encoded
        // Messy branch offset encoding
        // 31 30 29 28 27 26 25 24-20 19-15 14-12 11 10  9  8  7  6-0
        // 12 10  9  8  7  6  5 SRC2  SRC1  FUNC3  4  3  2  1 11  OP
        int imm4_1 = (delta>> 1) & 0xF;
        int imm10_5= (delta>> 5) &0x3F;
        int imm11  = (delta>>11) &   1;
        int imm12  = (delta>>12) &   1;
        int imm5 = imm4_1<<1 | imm11;
        int imm7 = imm12<<6 | imm10_5;
        return (imm7 << 25 ) | (rs2 << 20) | (rs1 << 15) | (func3 << 12) | (imm5 << 7) | opcode;
    }

    static public int jumpop(String op) {
        return switch(op) {
        case "=="  -> 0x0;
        case "!="  -> 0x1;
        case "<"   -> 0x4;
        case ">="  -> 0x5;
        case "u<"  -> 0x6;
        case "u<=" -> 0x7;
        default  ->  throw new UnsupportedOperationException();
        };
    }

    static public int fsetop(String op) {
        return switch(op) {
        case "<"  -> 1;
        case "<=" -> 0;
        case "==" -> 2;
        default   -> throw new UnsupportedOperationException();
        };
    }
}
