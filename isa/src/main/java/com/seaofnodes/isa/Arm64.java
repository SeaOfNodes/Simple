package com.seaofnodes.isa;

/** Instruction fields and concrete operand encodings; no compiler state. */
public final class Arm64 {
    private Arm64() {}

    public static final int OP_ADD       = 0b10_001_011;
    public static final int OPF_ADD      = 0b00_011_110;
    public static final int OPF_OP_ADD   = 0b00_101_0;
    public static final int OPI_ADD      = 0b10_010_00100;
    public static final int OPI_SUB      = 0b11_010_00100;
    public static final int OP_UJMP      = 0b000101;
    public static final int OP_ADRP      = 0b10000;
    public static final int OP_ASR       = 0b1010;
    public static final int OPI_ASR      = 0b10_0100_1101;
    public static final int OP_LSL       = 0b1000;
    public static final int OPI_LSL       =0b1101001101;
    public static final int OP_LSR        = 0b1001;
    public static final int OPI_LSR       = 0b1101001101;
    public static final int OP_BRANCH    = 0b01_0101_00;
    public static final int OP_CALL      = 0b10_010_1;
    public static final int OP_CALLRARM  = 0b1101011000111111000000;
    public static final int OP_SUBS      = 0b1111000100;
    public static final int OP_CSET      = 0b10011010100;
    public static final int OP_CMP       = 0b11101011;
    public static final int OPI_CMP      = 0b1111000100;
    public static final int OP_DIV       = 0b10011010110;
    public static final int OPF_DIV      = 0b000110;
    public static final int OP_MUL       = 0b10011011000;
    public static final int OPF_MUL      =  0b10;
    public static final int OPF_ARM      = 0b01011100;
    public static final int OP_FLOAT_C   = 0b10011110;
    public static final int OP_XOR       = 0b11001010;
    public static final int OPI_XOR      = 0b110100100;
    public static final int OP_AND       = 0b10_0010_10;
    public static final int OPI_AND      = 0b10_0100_100;
    public static final int OP_OR        = 0b10101010;
    public static final int OPI_OR       = 0b101100100;
    public static final int OP_SUB       = 0b11001011;
    public static final int OPF_SUB      = 0b1110;
    public static final int OP_MOVK      = 0b111100101;
    public static final int OP_MOVN      = 0b100100101;
    public static final int OP_MOVZ      = 0b110100101;
    public static final int OP_RET       = 0b1101011001011111000000;
    public static final int OP_LOAD_R_64    = 0b11111000011;
    public static final int OP_LOAD_R_32    = 0b10111000011;
    public static final int OP_LOAD_R_16    = 0b01111000010;
    public static final int OP_LOAD_R_8     = 0b00111000011;
    public static final int OP_LOAD_IMM_64  = 0b1111100101;
    public static final int OP_LOAD_IMM_32  = 0b1011100101;
    public static final int OP_LOAD_IMM_16  = 0b0111100101;
    public static final int OP_LOAD_IMM_8   = 0b0011100101;
    public static final int OPF_LOAD_R_64    = 0b11111100011;
    public static final int OPF_LOAD_R_32    = 0b10111100011;
    public static final int OPF_LOAD_IMM_64  = 0b1111110101;
    public static final int OPF_LOAD_IMM_32  = 0b1011110101;
    public static final int OP_STORE_R_64    = 0b11111000001;
    public static final int OP_STORE_R_32    = 0b10111000001;
    public static final int OP_STORE_R_16    = 0b01111000001;
    public static final int OP_STORE_R_8     = 0b00111000001;
    public static final int OP_STORE_IMM_64  = 0b1111100100;
    public static final int OP_STORE_IMM_32  = 0b1011100100;
    public static final int OP_STORE_IMM_16  = 0b0111100100;
    public static final int OP_STORE_IMM_8   = 0b0011100100;
    public static final int OPF_STORE_R_64    = 0b11111100001;
    public static final int OPF_STORE_R_32    = 0b10111100001;
    public static final int OPF_STORE_IMM_32  = 0b1011110100;
    public static final int OPF_STORE_IMM_64  = 0b1111110100;
    public static final int OP_FMOV        = 0b10011110;
    public static final int OP_FMOV_REG    = 0b00011110;
    public static final int OP_MOV         = 0b10101010000;

    public enum OPTION {
        UXTB,
        UXTH,
        UXTX,
        SXTB,
        SXTH,
        SXTW,
        SXTX,
    }

    public enum STORE_LOAD_OPTION {
        UXTW,  // base+ u32 index [<< logsize]
        UXTX,  // base+ u64 index [<< logsize]
        SXTW,  // base+ s32 index [<< logsize]
        SXTX,  // base+ s64 index [<< logsize]
    }

    public enum COND {
        EQ,
        NE,
        CS,
        CC,
        MI,
        PL,
        VS,
        VC,
        HI,
        LS,
        GE,
        LT,
        GT,
        LE,
        AL,
        NV
    }

    static public int cset(int opcode, int rm, COND cond, int rn, int rd) {
        assert 0 <= rm && rm < 32;
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        // Is one of the standard conditions, excluding AL and NV, encoded in the "cond" field with its least significant bit inverted.
        int actualCond = cond.ordinal() ^ 1;
        return (opcode << 21) | (rm << 16) | (actualCond << 12) | (0b01 << 10) |  (rn << 5) | rd;
    }

    static public int cset(int opcode, COND cond, int rn, int rd) {
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        return (opcode << 16) | (cond.ordinal() << 12) | (rn << 5) | rd;
    }

    public static int imm_inst(int opcode, int imm12, int rn, int rd) {
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        assert opcode >=0 && imm12 >= 0; // Caller zeros high order bits
        return (opcode << 22) | (imm12 << 10) | (rn << 5) | rd;
    }

    public static int imm_shift(int opcode, int imm, int imms, int rn,  int rd)  {
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        return (opcode << 22) | (1 << 22) | (imm << 16) | (imms << 10) | (rn << 5) | rd;
    }

    public static int imm_inst_n(int opcode, int imm13, int rn, int rd) {
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        assert 0 <= imm13 && imm13 <= 0x1FFF;
        return (opcode << 23) | (imm13 << 10) | (rn << 5) | rd;
    }

    public static int imm_inst_l(int opcode, int imm12, int self) {
        int body = imm_inst(opcode, imm12&0xFFF, self, self);
        return body;
    }

    public static int r_reg(int opcode, int shift, int rm, int imm6, int rn, int rd) {
        assert 0 <= rm && rm < 32;
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        return (opcode << 24) | (shift << 21) | (rm << 16) | (imm6 << 10) | (rn << 5) | rd;
    }

    public static int shift_reg(int opcode, int rm, int op2, int rn, int rd) {
        assert 0 <= rn && rn < 32;
        assert 0 <= rm && rm < 32;
        assert 0 <= rd && rd < 32;
        return (opcode << 21) | (rm << 16) | (op2 << 10) | (rn << 5) | rd;
    }

    public static int madd(int opcode, int rm, int ra, int rn, int rd) {
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        assert 0 <= rm && rm < 32;
        return (opcode << 21) | (rm << 16) | (ra << 10) | (rn << 5) | rd;
    }

    public static int mov(int opcode, int shift, int imm16, int rd) {
        assert 0 <= rd && rd < 32;
        return (opcode << 23) | (shift << 21) | (imm16 << 5) | rd;
    }

    public static int mov_reg(int opcode, int src, int rd) {
        assert 0 <= rd && rd < 32;
        return (opcode  << 21) | (src << 16) | 0b11111 << 5 | rd;
    }

    public static int ret(int opcode) {
        return (opcode << 10) | (30 << 5);
    }

    public static int f_scalar(int opcode, int ftype, int rm, int op, int rn, int rd) {
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        assert 0 <= rm && rm < 32;
        return (opcode << 24) | (ftype << 22) | (1 << 21) | (rm << 16) | (op << 10) | (rn << 5) | rd;
    }

    public static int f_mov_reg(int opcode, int rn, int rd) {
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        return (opcode << 24) | (0b01100000010000 << 10) | (rn << 5) | rd;
    }

    public static int f_mov_general(int opcode, int ftype, int rmode, int opcode1, int rn, int rd) {
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        return (opcode << 24) |(ftype << 22) | (1 << 21) | (rmode << 19) | (opcode1 << 16) | (rn << 5) | rd;
    }

    public static int load_pc(int opcode, int offset, int rt) {
        assert -(1<<20) <= offset && offset < (1<<20);
        assert (offset&3)==0;
        offset = (offset>>2)&0x7FFFF;
        return (opcode << 24) | (offset << 5) | rt;
    }

    public static int adrp(int op, int imlo,int opcode, int imhi, int rd) {
        assert 0 <= rd && rd < 32;
        assert 0 <= imlo && imlo <= 0x3;     //  2 bits
        assert 0 <= imhi && imhi <= 0x7FFFF; // 19 bits
        return (op << 31) | (imlo << 29) |(opcode << 24) | (imhi << 5) | rd;
    }

    public static int indr_adr(int opcode, int off, STORE_LOAD_OPTION option, int s, int ptr, int rt) {
        assert 0 <= ptr && ptr < 32;
        assert 0 <= rt &&  rt  < 32;
        return (opcode << 21) | (off << 16) | (option.ordinal() << 13) | (s << 12) | (2 << 10) | (ptr << 5) | rt;
    }

    public static int load_str_imm(int opcode, int imm12, int ptr, int rt, int size) {
        assert 0 <= ptr && ptr < 32;
        assert 0 <= rt &&  rt  < 32;

        if(size == 8) imm12 = imm12 >> 3;
        if(size == 4) imm12 = imm12 >> 2;
        if(size == 2) imm12 = imm12 >> 1;
        // size == 1  imm12 = imm12
        return (opcode << 22) | ((imm12) << 10)  | (ptr << 5) | rt;
    }

    public static int f_convert(int opcode_1, int opcode_2, int opcode_3, int opcode_4,  int vd, int vm)  {
        return (opcode_1 << 28) | (opcode_2 << 24) | (opcode_3 << 20) | (opcode_4 << 16) |
                (vd << 12) | (0x01100010 << 4) | vm;
    }

    /** FCVTZS Xd,Dn: truncate, saturate overflow, and convert NaN to zero. */
    public static int floatToInteger(int rn, int rd) {
        assert 0<=rn && rn<32 && 0<=rd && rd<32;
        return 0x9E780000 | (rn<<5) | rd;
    }

    public static int float_cast(int opcode, int ftype, int rn, int rd) {
        assert 0 <= rd &&  rd < 32;
        assert 0 <= rn &&  rn  < 32;
        return (opcode << 24) | (ftype << 22) | (2176 << 10) | (rn << 5) | rd;
    }

    public static int f_cmp(int opcode, int ftype, int rm, int rn) {
        assert 0 <= rn && rn  < 32;
        assert 0 <= rm && rm  < 32;
        // Todo: |8 is not needed
        return (opcode  << 24) | (ftype << 21) | (rm << 16) | (8 << 10) | (rn << 5);
    }

    public static COND make_condition(String bop) { return make_condition(bop,false); }

    public static COND make_condition(String bop, boolean fp) {
        // One of {N,Z,C} and !V
        // OR { !N, !Z, C, V }
        return fp
            ? switch (bop) {    // FP uses unsigned encodings
            case "==" -> COND.EQ;  //  Z
            case "!=" -> COND.NE;  // !Z
            case "<"  -> COND.MI;  //        N
            case "<=" -> COND.LS;  //  Z || !C
            case ">=" -> COND.PL;  //       !N
            case ">"  -> COND.HI;  // !Z &&  C
            default   -> throw new UnsupportedOperationException();
        }
            : switch (bop) {
            case "==" -> COND.EQ;  //  Z
            case "!=" -> COND.NE;  // !Z
            case "<"  -> COND.LT;  //       N != V
            case "<=" -> COND.LE;  //  Z || N != V
            case ">=" -> COND.GE;  //       N == V
            case ">"  -> COND.GT;  // !Z && N == V
            default   -> throw new UnsupportedOperationException();
        };
    }

    public static int b_cond(int opcode, int delta, COND cond) {
        // 24-5 == 19bits offset range
        assert -(1<<20) <= delta && delta < (1<<20);
        assert (delta&3)==0;
        delta>>=2;
        delta &= (1L<<19)-1;    // Zero extend
        return (opcode << 24) | ((delta)<< 5) | cond.ordinal();
    }

    public static int cond_set(int opcode, int rm, COND cond, int rn, int rd) {
        assert 0 <= rd &&  rd < 32;
        assert 0 <= rn &&  rn  < 32;
        return (opcode << 21) | (rm << 16) | (cond.ordinal() << 12) | (rn << 5) | rd;
    }

    public static int blr(int opcode, int rd) {
        assert 0 <= rd && rd < 32;
        return opcode << 10 | rd << 5;
    }

    public static int b(int opcode, int delta) {
        assert -(1<<27) <= delta && delta < (1<<27);
        assert (delta&3)==0;
        delta>>=2;
        delta &= (1L<<26)-1;    // Zero extend
        return (opcode << 26) | delta;
    }

    public static int b_calloc(int opcode, int delta) {
        return b(opcode,delta);
    }

    public static long decodeImm12(int imm12) {
        int immr = (imm12 >> 6) & 0x3F;
        int imms = imm12 & 0x3F;
        int size;
        if ((imm12 & 0x1000) != 0) {
            size = 64;
        } else {
            size = 31-(imms >> 1);
            size |= size >> 1;
            size |= size >> 2;
            size |= size >> 4;
            size++;
            imms &= ~((32-size) << 1);
        }
        long val = (2L << imms)-1;
        while (size < 64) {
            val |= val << size;
            size <<= 1;
        }
        val = (val >>> immr) | val << (64-immr);
        return val;
    }

    public static int logicalImmediate(long val) {
        if (val == 0 || val == -1) return -1; // Special cases are not allowed
        int immr = 0;
        // Rotate until we have 0[...]1

        // Rotate until:
        // The number is negative (MSB is 1)
        // Or the LSB is not 1
        while (val < 0 || (val & 1)==0) {
            // circular rotation
            val = (val >>> 63) | (val << 1);
            immr++;
        }
        // Start by assuming that val might be made of two 32-bit chunks that repeat.
        int size = 32;
        long pattern = val;
        // Is upper half of pattern the same as the lower?
        while ((pattern & ((1L<<size)-1)) == (pattern >> size)) {
            // Then only take one half
            pattern >>= size;
            size >>= 1;
        }
        size <<= 1;
        int imms = Long.bitCount(pattern);
        // Pattern should now be zeros followed by ones 0000011111
        if (pattern != (1L<<imms)-1) return -1;
        imms--;
        if (size == 64) return 0x1000 | immr << 6 | imms;
        return (32-size)<<1 | immr << 6 | imms;
    }
    /** Materialize a 64-bit constant with MOVZ/MOVN and MOVK. */
    public static void constant(CodeSink out, int self, long x) {
        int nb0 = 0;
        int nb1 = 0;
        // Count number of 0000 and FFFF blocks
        for (int i=0; i<64; i+=16) {
            int block = (int)(x >> i) & 0xFFFF;
            if (block == 0) nb0++;
            if (block == 0xFFFF) nb1++;
        }
        int pattern;
        int op;
        if(nb0 >= nb1) {
            // More 0 blocks then F blocks, use movz
            pattern = 0;
            op = OP_MOVZ;
        } else {
            // More F blocks then 0 blocks, use movn
            pattern = 0xFFFF;
            op = OP_MOVN;
        }
        int invert = pattern;
        for (int i=0; i<4; i++) {
            int block = (int)x & 0xFFFF;
            x >>= 16;
            if (block != pattern) {
                out.add4(mov(op, i, block ^ invert, self));
                op = OP_MOVK;
                invert = 0;
            }
        }
        if (op != OP_MOVK) {
            // All blocks are the same, special case
            out.add4(mov(op, 0, 0, self));
        }
    }
}
