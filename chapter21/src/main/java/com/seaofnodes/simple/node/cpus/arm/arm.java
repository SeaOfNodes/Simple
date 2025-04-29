package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

public class arm extends Machine {
    public arm( CodeGen code ) {
        if( !"SystemV".equals(code._callingConv) )
            throw new IllegalArgumentException("Unknown calling convention "+code._callingConv);
    }

    // ARM64
    @Override public String name() { return "arm"; }

    // GPR(S)
    public static final int X0  =  0,  X1  =  1,  X2  =  2,  X3  =  3,  X4  =  4,  X5  =  5,  X6  =  6,  X7  =  7;
    public static final int X8  =  8,  X9  =  9,  X10 = 10,  X11 = 11,  X12 = 12,  X13 = 13,  X14 = 14,  X15 = 15;
    public static final int X16 = 16,  X17 = 17,  X18 = 18,  X19 = 19,  X20 = 20,  X21 = 21,  X22 = 22,  X23 = 23;
    public static final int X24 = 24,  X25 = 25,  X26 = 26,  X27 = 27,  X28 = 28,  X29 = 29,  X30 = 30,  RSP = 31;

    // Floating point registers
    public static final int D0  = 32,  D1  = 33,  D2  = 34,  D3  = 35,  D4  = 36,  D5  = 37,  D6  = 38,  D7  = 39;
    public static final int D8  = 40,  D9  = 41,  D10 = 42,  D11 = 43,  D12 = 44,  D13 = 45,  D14 = 46,  D15 = 47;
    public static final int D16 = 48,  D17 = 49,  D18 = 50,  D19 = 51,  D20 = 52,  D21 = 53,  D22 = 54,  D23 = 55;
    public static final int D24 = 56,  D25 = 57,  D26 = 58,  D27 = 59,  D28 = 60,  D29 = 61,  D30 = 62,  D31 = 63;

    static final int FLAGS = 64;
    static final int MAX_REG = 65;
    public static final int D_OFFSET = 32;

    static final String[] REGS = new String[] {
        "X0",  "X1",  "X2",  "X3",  "X4",  "X5",  "X6",  "X7",
        "X8",  "X9",  "X10", "X11", "X12", "X13", "X14", "X15",
        "X16", "X17", "X18", "X19", "X20", "X21", "X22", "X23",
        "X24", "X25", "X26", "X27", "X28", "X29", "RPC", "RSP",
        "D0",  "D1",  "D2",  "D3",  "D4",  "D5",  "D6",  "D7",
        "D8",  "D9",  "D10", "D11", "D12", "D13", "D14", "D15",
        "D16", "D17", "D18", "D19", "D20", "D21", "D22", "D23",
        "D24", "D25", "D26", "D27", "D28", "D29", "D30", "D31",
        "flags"
    };
    @Override public String[] regs() { return REGS; }

    // from (x0-x30)
    // General purpose register mask: pointers and ints, not floats
    static final long RD_BITS = 0xFFFFFFFFL;
    static final RegMask RMASK = new RegMask(RD_BITS);
    static final long WR_BITS = 0x7FFFFFFFL; // All the GPRs, not RSP
    static final RegMask WMASK = new RegMask(WR_BITS);

    // Float mask from(d0–d31)
    static final long FP_BITS = 0xFFFFFFFFL<<D0;
    static final RegMask DMASK = new RegMask(FP_BITS);

    // Load/store mask; both GPR and FPR
    static final RegMask MEM_MASK = new RegMask(WR_BITS | FP_BITS);

    static final RegMask SPLIT_MASK = new RegMask(WR_BITS | FP_BITS, -2L/*skip flags*/);

    static final RegMask FLAGS_MASK = new RegMask(FLAGS);

    // Arguments masks
    static final RegMask X0_MASK = new RegMask(X0);
    static final RegMask X1_MASK = new RegMask(X1);
    static final RegMask X2_MASK = new RegMask(X2);
    static final RegMask X3_MASK = new RegMask(X3);
    static final RegMask X4_MASK = new RegMask(X4);
    static final RegMask X5_MASK = new RegMask(X5);
    static final RegMask X6_MASK = new RegMask(X6);
    static final RegMask X7_MASK = new RegMask(X7);

    // Arguments(float) masks
    static final RegMask D0_MASK = new RegMask(D0);
    static final RegMask D1_MASK = new RegMask(D1);
    static final RegMask D2_MASK = new RegMask(D2);
    static final RegMask D3_MASK = new RegMask(D3);
    static final RegMask D4_MASK = new RegMask(D4);
    static final RegMask D5_MASK = new RegMask(D5);
    static final RegMask D6_MASK = new RegMask(D6);
    static final RegMask D7_MASK = new RegMask(D7);

    // major opcode: OP
    public static int OP_ADD       = 0b10_001_011;
    public static int OPF_ADD      = 0b00_011_110;
    public static int OPF_OP_ADD   = 0b00_101_0;
    public static int OPI_ADD      = 0b10_010_00100;

    public static int OPI_SUB      = 0b11_010_00100;
    public static int OP_UJMP      = 0b000101;

    public static int OP_ADRP      = 0b10000;

    public static int OP_ASR       = 0b1010;
    public static int OPI_ASR      = 0b10_0100_1101;

    public static int OP_LSL       = 0b1000;
    public static int OPI_LSL       =0b1101001101;

    public static int OP_LSR        = 0b1001;
    public static int OPI_LSR       = 0b1101001101;

    public static int OP_BRANCH    = 0b01_0101_00;
    public static int OP_CALL      = 0b10_010_1;

    public static int OP_CALLRARM  = 0b1101011000111111000000;

    public static int OP_SUBS      = 0b1111000100;
    public static int OP_CSET      = 0b10011010100;
    public static int OP_CMP       = 0b11101011;
    public static int OPI_CMP      = 0b1111000100;

    public static int OP_DIV       = 0b10011010110;
    public static int OPF_DIV      = 0b000110;

    public static int OP_MUL       = 0b10011011000;
    public static int OPF_MUL      =  0b10;

    public static int OPF_ARM      = 0b01011100;

    public static int OP_FLOAT_C   = 0b10011110;

    public static int OP_XOR       = 0b11001010;
    public static int OPI_XOR      = 0b110100100;

    public static int OP_AND       = 0b10_0010_10;
    public static int OPI_AND      = 0b10_0100_100;

    public static int OP_OR        = 0b10101010;
    public static int OPI_OR       = 0b101100100;

    public static int OP_SUB       = 0b11001011;
    public static int OPF_SUB      = 0b1110;

    public static int OP_MOVK      = 0b111100101;
    public static int OP_MOVN      = 0b100100101;
    public static int OP_MOVZ      = 0b110100101;

    public static int OP_RET       = 0b1101011001011111000000;
    // Store/load opcodes
    public static int OP_LOAD_R_64    = 0b11111000011;
    public static int OP_LOAD_R_32    = 0b10111000011;
    public static int OP_LOAD_R_16    = 0b01111000010;
    public static int OP_LOAD_R_8     = 0b00111000011;

    public static int OP_LOAD_IMM_64  = 0b1111100101;
    public static int OP_LOAD_IMM_32  = 0b1011100101;
    public static int OP_LOAD_IMM_16  = 0b0111100101;
    public static int OP_LOAD_IMM_8   = 0b0011100101;

    // load
    public static int OPF_LOAD_R_64    = 0b11111100011;
    public static int OPF_LOAD_R_32    = 0b10111100011;

    public static int OPF_LOAD_IMM_64  = 0b1111110101;
    public static int OPF_LOAD_IMM_32  = 0b1011110101;

    public static int OP_STORE_R_64    = 0b11111000001;
    public static int OP_STORE_R_32    = 0b10111000001;
    public static int OP_STORE_R_16    = 0b01111000001;
    public static int OP_STORE_R_8     = 0b00111000001;

    public static int OP_STORE_IMM_64  = 0b1111100100;
    public static int OP_STORE_IMM_32  = 0b1011100100;
    public static int OP_STORE_IMM_16  = 0b0111100100;
    public static int OP_STORE_IMM_8   = 0b0011100100;

    public static int OPF_STORE_R_64    = 0b11111100001;
    public static int OPF_STORE_R_32    = 0b10111100001;

    public static int OPF_STORE_IMM_32  = 0b1111110100;
    public static int OPF_STORE_IMM_64  = 0b1111110100;


    public static int OP_FMOV        = 0b10011110;
    public static int OP_FMOV_REG    = 0b00011110;
    // https://docsmirror.github.io/A64/2023-06/mov_orr_log_shift.html
    public static int OP_MOV         = 0b10101010000;
    // Calling convention; returns a machine-specific register
    // for incoming argument idx.
    // index 0 for control, 1 for memory, real args start at index 2
    static final RegMask[] CALLINMASK = new RegMask[] {
        X0_MASK,
        X1_MASK,
        X2_MASK,
        X3_MASK,
        X4_MASK,
        X5_MASK,
        X6_MASK,
        X7_MASK,
    };
    static final RegMask[] XMMS = new RegMask[] {
        D0_MASK,
        D1_MASK,
        D2_MASK,
        D3_MASK,
        D4_MASK,
        D5_MASK,
        D6_MASK,
        D7_MASK,
    };

    // ARM ENCODING
    public enum OPTION {
        UXTB,
        UXTH,
        UXTX,
        SXTB,
        SXTH,
        SXTW,
        SXTX,
    }

    static public int cset(int opcode, int rm, COND cond, int rn, int rd) {
        assert 0 <= rm && rm < 32;
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        return (opcode << 21) | (rm << 15) | (cond.ordinal() << 12) | (rn << 5) | rd;
    }

    static public int cset(int opcode, COND cond, int rn, int rd) {
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        return (opcode << 16) | (cond.ordinal() << 12) | (rn << 5) | rd;
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

    // True if signed 9-bit immediate
    private static boolean imm9(TypeInteger ti) {
        // 55 = 64-9
        return ti.isConstant() && ((ti.value()<<55)>>55) == ti.value();
    }
    // True if signed 12-bit immediate
    private static boolean imm12(TypeInteger ti) {
        // 52 = 64-12
        return ti.isConstant() && ((ti.value()<<52)>>52) == ti.value();
    }


    // Can we encode this in ARM's 12-bit LOGICAL immediate form?
    // Some combination of shifted bit-masks.
    private static int imm12Logical(TypeInteger ti) {
        if( !ti.isConstant() ) return -1;
        if( !ti.isConstant() ) return -1;
        long val = ti.value();
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

    // sh is encoded in opcode
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

    public static void imm_inst(Encoding enc, Node n,Node n2,  int opcode, int imm12) {
        short self = enc.reg(n);
        short reg1 = enc.reg(n2);

        int body = imm_inst(opcode, imm12&0xFFF, reg1, self);
        enc.add4(body);
    }

    public static void imm_inst_subs(Encoding enc, Node n,Node n2,  int opcode, int imm12) {
        short reg1 = enc.reg(n2);
        // 31 = 11111
        int body = imm_inst(opcode, imm12&0xFFF, reg1, 31);
        enc.add4(body);
    }

    // for cases where rs1 and dst are the same, eg add x0, x0, 1
    public static void imm_inst(Encoding enc, int opcode, int imm12, int self) {
        int body = imm_inst(opcode, imm12&0xFFF, self, self);
        enc.add4(body);
    }

    public static void imm_inst_n(Encoding enc, Node n, Node n2, int opcode, int imm13) {
        short self = enc.reg(n);
        short reg1 = enc.reg(n2);
        int body = imm_inst_n(opcode, imm13, reg1, self);
        enc.add4(body);
    }

    // nth bit comes from immediate and not opcode
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

    // for cases where rs1 and dst are the same, eg add x0, x0, 1
    public static int imm_inst_l(Encoding enc, Node n, int opcode, int imm12) {
        short self = enc.reg(n);
        short reg1 = enc.reg(n.in(1));
        int body = imm_inst(opcode, imm12&0xFFF, reg1, self);
        return body;
    }

    // for normal add, reg1, reg2 cases (reg-to-reg)
    // using shifted-reg form
    public static int r_reg(int opcode, int shift, int rm, int imm6, int rn, int rd) {
        assert 0 <= rm && rm < 32;
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        return (opcode << 24) | (shift << 21) | (rm << 16) | (imm6 << 10) | (rn << 5) | rd;
    }
    public static void r_reg(Encoding enc, Node n, int opcode) {
        short self = enc.reg(n);
        short reg1 = enc.reg(n.in(1));
        short reg2 = enc.reg(n.in(2));
        int body = r_reg(opcode, 0, reg2, 0,  reg1, self >= 32 ? reg1: self);
        enc.add4(body);
    }

    public static void r_reg_subs(Encoding enc, Node n, int opcode) {
        short reg1 = enc.reg(n.in(1));
        short reg2 = enc.reg(n.in(2));
        // 31 = 11111
        int body = r_reg(opcode, 0, reg2, 0,  reg1, 31);
        enc.add4(body);
    }

    public static int shift_reg(int opcode, int rm, int op2, int rn, int rd) {
        assert 0 <= rn && rn < 32;
        assert 0 <= rm && rm < 32;
        assert 0 <= rd && rd < 32;
        return (opcode << 21) | (rm << 16) | (op2 << 10) | (rn << 5) | rd;
    }
    public static void shift_reg(Encoding enc, Node n, int op2) {
        short self = enc.reg(n);
        short reg1 = enc.reg(n.in(1));
        short reg2 = enc.reg(n.in(2));
        int body = shift_reg(0b10011010110, reg2, op2, reg1, self);
        enc.add4(body);
    }

    //  MUL can be considered an alias for MADD with the third operand Ra being set to 0
    public static int madd(int opcode, int rm, int ra, int rn, int rd) {
        assert 0 <= rn && rn < 32;
        assert 0 <= rd && rd < 32;
        assert 0 <= rm && rm < 32;
        return (opcode << 21) | (rm << 16) | (ra << 10) | (rn << 5) | rd;
    }
    public static void madd(Encoding enc, Node n, int opcode, int ra) {
        short self = enc.reg(n);
        short reg1 = enc.reg(n.in(1));
        short reg2 = enc.reg(n.in(2));
        int body = madd(opcode, reg2, ra,  reg1, self);
        enc.add4(body);
    }

    // encodes movk, movn, and movz
    public static int mov(int opcode, int shift, int imm16, int rd) {
        assert 0 <= rd && rd < 32;
        return (opcode << 23) | (shift << 21) | (imm16 << 5) | rd;
    }

    public static int mov_reg(int opcode, int src, int rd) {
        assert 0 <= rd && rd < 32;
        return (opcode  << 21) | (src << 16) | 0b11111 << 5 | rd;
    }

    public static int ret(int opcode) {
        return (opcode << 10);
    }

    // FMOV (scalar, immediate)
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
    public static void f_scalar(Encoding enc, Node n, int op ) {
        short self = (short)(enc.reg(n)      -D_OFFSET);
        short reg1 = (short)(enc.reg(n.in(1))-D_OFFSET);
        short reg2 = (short)(enc.reg(n.in(2))-D_OFFSET);
        int body = f_scalar(0b00011110, 1, reg2, op,  reg1, self);
        enc.add4(body);
    }

    // share same encoding with LDR (literal, SIMD&FP) flavour
    public static int load_pc(int opcode, int offset, int rt) {
        offset>>=2;
        return (opcode << 24) | (offset << 5) | rt;
    }
    // int l
    public static int adrp(int op, int imlo,int opcode, int imhi, int rd) {
        assert 0 <= rd && rd < 32;
        return (op << 31) | (imlo << 29) |(opcode << 24) | (imhi << 5) | rd;
    }

    // [Rptr+Roff]
    public static int indr_adr(int opcode, int off, STORE_LOAD_OPTION option, int s, int ptr, int rt) {
        assert 0 <= ptr && ptr < 32;
        assert 0 <= rt &&  rt  < 32;
        return (opcode << 21) | (off << 16) | (option.ordinal() << 13) | (s << 12) | (2 << 10) | (ptr << 5) | rt;
    }
    // [Rptr+imm9]
    public static int load_str_imm(int opcode, int imm12, int ptr, int rt, int size) {
        assert 0 <= ptr && ptr < 32;
        assert 0 <= rt &&  rt  < 32;

        if(size == 8) imm12 = imm12 >> 3;
        if(size == 4) imm12 = imm12 >> 2;
        if(size == 2) imm12 = imm12 >> 1;
        // size == 1  imm12 = imm12
        return (opcode << 22) | ((imm12) << 10)  | (ptr << 5) | rt;
    }

    // encoding for vcvt, size is encoded in operand
    // <Qd>, <Qm>
    // F32.S32
    //encoded as op = 0b00, size = 0b10.
    // VCVT<c>.<Td>.<Tm> <Dd>, <Dm>
    // opcode is broken down into 4 pieces
    public static int f_convert(int opcode_1, int opcode_2, int opcode_3, int opcode_4,  int vd, int vm)  {
        return (opcode_1 << 28) | (opcode_2 << 24) | (opcode_3 << 20) | (opcode_4 << 16) |
                (vd << 12) | (0x01100010 << 4) | vm;
    }
    public static int float_cast(int opcode, int ftype, int rn, int rd) {
        assert 0 <= rd &&  rd < 32;
        assert 0 <= rn &&  rn  < 32;
        return (opcode << 24) | (ftype << 22) | (2176 << 10) | (rn << 5) | rd;
    }


    // ftype = 3
    public static int f_cmp(int opcode, int ftype, int rm, int rn) {
        assert 0 <= rn && rn  < 32;
        assert 0 <= rm && rm  < 32;
        // Todo: |8 is not needed
        return (opcode  << 24) | (ftype << 21) | (rm << 16) | (8 << 10) | (rn << 5);
    }
    public static void f_cmp(Encoding enc, Node n) {
        short reg1 = (short)(enc.reg(n.in(1))-D_OFFSET);
        short reg2 = (short)(enc.reg(n.in(2))-D_OFFSET);
        int body = f_cmp(0b00011110, 3, reg1,  reg2);
        enc.add4(body);
    }

    public static COND make_condition(String bop) {
        return switch (bop) {
        case "==" -> COND.EQ;
        case "!=" -> COND.NE;
        case "<"  -> COND.LT;
        case "<=" -> COND.LE;
        case ">=" -> COND.GE;
        case ">"  -> COND.GT;
        default   -> throw Utils.TODO();
        };
    }

    public static int b_cond(int opcode, int delta, COND cond) {
        // 24-5 == 19bits offset range
        assert -(1<<19) <= delta && delta < (1<<19);
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

    // Branch with Link to Register calls a subroutine at an address in a register, setting register X30 to PC+4.
    public static int blr(int opcode, int rd) {
        assert 0 <= rd && rd < 32;
        return opcode << 10 | rd << 5;
    }
    public static int b(int opcode, int delta) {
        assert -(1<<26) <= delta && delta < (1<<26);
        assert (delta&3)==0;
        delta>>=2;
        delta &= (1L<<26)-1;    // Zero extend
        return (opcode << 26) | delta;
    }
    // no aligned assert(assert (delta&3)==0;)
    public static int b_calloc(int opcode, int delta) {
        assert -(1<<26) <= delta && delta < (1<<26);
        delta>>=2;
        delta &= (1L<<26)-1;    // Zero extend
        return (opcode << 26) | delta;
    }

    @Override public RegMask callArgMask(TypeFunPtr tfp, int idx, int maxArgSlot ) { return callInMask(tfp,idx,maxArgSlot); }
    static RegMask callInMask(TypeFunPtr tfp, int idx, int maxArgSlot ) {
        if( idx==0 ) return CodeGen.CODE._rpcMask;
        if( idx==1 ) return null;
        // Count floats in signature up to index
        int fcnt=0;
        for( int i=2; i<idx; i++ )
            if( tfp.arg(i-2) instanceof TypeFloat)
                fcnt++;
        // Floats up to XMMS in XMM registers
        if( tfp.arg(idx-2) instanceof TypeFloat ) {
            if( fcnt < XMMS.length )
                return XMMS[fcnt];
        } else {
            RegMask[] cargs = CALLINMASK;
            if( idx-2-fcnt < cargs.length )
                return cargs[idx-2-fcnt];
        }
        throw Utils.TODO(); // Pass on stack slot
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

    // Return the max stack slot used by this signature, or 0
    @Override public short maxArgSlot( TypeFunPtr tfp ) {
        int icnt=0, fcnt=0;     // Count of ints, floats
        for( int i=0; i<tfp.nargs(); i++ ) {
            if( tfp.arg(i) instanceof TypeFloat ) fcnt++;
            else icnt++;
        }
        int nstk = Math.max(icnt-8,0)+Math.max(fcnt-8,0);
        return (short)nstk;
    }

    private static final long CALLEE_SAVE =
        1L<<X19 |
        1L<<X20 | 1L<<X21 | 1L<<X22 | 1L<<X23 |
        1L<<X24 | 1L<<X25 | 1L<<X26 | 1L<<X27 |
        1L<<X28 |
        1L<<D9  | 1L<<D10 | 1L<<D11 |
        1L<<D12 | 1L<<D13 | 1L<<D14 | 1L<<D15;
    static final long CALLER_SAVE = ~CALLEE_SAVE & ~(1L<<RSP);
    @Override public long callerSave() { return CALLER_SAVE; }
    @Override public long neverSave() { return 1L<<RSP; }
    @Override public RegMask retMask( TypeFunPtr tfp ) {
        return tfp.ret() instanceof TypeFloat ? D0_MASK : X0_MASK;
    }
    @Override public int rpc() { return X30; }

    // Create a split op; any register to any register, including stack slots
    @Override public SplitNode split(String kind, byte round, LRG lrg) {  return new SplitARM(kind,round);  }

    // Return a MachNode unconditional branch
    @Override public CFGNode jump() {
        return new UJmpARM();
    }

    // Instruction selection
    @Override public Node instSelect(Node n ) {
        return switch( n ) {
        case AddFNode addf  -> new AddFARM(addf);
        case AddNode add    -> add(add);
        case AndNode and    -> and(and);
        case BoolNode bool  -> cmp(bool);
        case CallNode call  -> call(call);
        case CastNode cast  -> new CastARM(cast);
        case CallEndNode cend -> new CallEndARM(cend);
        case CProjNode c    -> new CProjNode(c);
        case ConstantNode con -> con(con);
        case DivFNode divf  -> new DivFARM(divf);
        case DivNode div    -> new DivARM(div);
        case FunNode fun    -> new FunARM(fun);
        case IfNode iff     -> jmp(iff);
        case LoadNode ld    -> ld(ld);
        case MemMergeNode mem -> new MemMergeNode(mem);
        case MinusNode neg  -> new NegARM(neg);
        case MulFNode mulf  -> new MulFARM(mulf);
        case MulNode mul    -> new MulARM(mul);
        case NewNode nnn    -> new NewARM(nnn);
        case NotNode not    -> new NotARM(not);
        case OrNode or      -> or(or);
        case ParmNode parm  -> new ParmARM(parm);
        case PhiNode phi    -> new PhiNode(phi);
        case ProjNode prj   -> new ProjARM(prj);
        case ReadOnlyNode read  -> new ReadOnlyNode(read);
        case ReturnNode ret -> new RetARM(ret,ret.fun());
        case SarNode sar    -> asr(sar);
        case ShlNode shl    -> lsl(shl);
        case ShrNode shr    -> lsr(shr);
        case StartNode start -> new StartNode(start);
        case StopNode stop  -> new StopNode(stop);
        case StoreNode st   -> st(st);
        case SubFNode subf  -> new SubFARM(subf);
        case SubNode sub    -> sub(sub);
        case ToFloatNode tfn-> new I2F8ARM(tfn);
        case XorNode xor    -> xor(xor);

        case LoopNode loop  -> new LoopNode(loop);
        case RegionNode region-> new RegionNode(region);
        default -> throw Utils.TODO();
        };
    }

    private Node cmp(BoolNode bool){
        Node cmp = _cmp(bool);
        return new SetARM(cmp, IfNode.negate(bool.op()));
    }
    private Node _cmp(BoolNode bool) {
        if( bool.isFloat() )
            return new CmpFARM(bool);
        return bool.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti
                ? new CmpIARM(bool, (int)ti.value())
                : new CmpARM(bool);
    }

    private Node ld(LoadNode ld) {
        return new LoadARM(address(ld), ld.ptr(), idx, off);
    }

    private Node jmp(IfNode iff) {
        // If/Bool combos will match to a Cmp/Set which sets flags.
        // Most general arith ops will also set flags, which the Jmp needs directly.
        // Loads do not set the flags, and will need an explicit TEST
        String op = "!=";
        if( iff.in(1) instanceof BoolNode bool ) op = bool.op();
        else if( iff.in(1)==null ) op = "=="; // Never-node cutout
        else iff.setDef(1, new BoolNode.NE(iff.in(1), new ConstantNode(TypeInteger.ZERO)));
        return new BranchARM(iff, op);
    }

    private Node add(AddNode add) {
        return add.in(2) instanceof ConstantNode off && off._con instanceof TypeInteger ti && imm12(ti)
                ? new AddIARM(add, (int)ti.value())
                : new AddARM(add);
    }

    private Node sub(SubNode sub) {
        return sub.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti)
                ? new SubIARM(sub, (int)(ti.value()))
                : new SubARM(sub);
    }

    private Node con( ConstantNode con ) {
        if( !con._con.isConstant() ) return new ConstantNode( con ); // Default unknown caller inputs
        return switch( con._con ) {
            case TypeInteger ti  -> new IntARM(con);
            case TypeFloat   tf  -> new FloatARM(con);
            case TypeFunPtr  tfp -> new TFPARM(con);
            case TypeMemPtr tmp -> new ConstantNode(con);
            case TypeNil tn  -> throw Utils.TODO();
            // TOP, BOTTOM, XCtrl, Ctrl, etc.  Never any executable code.
            case Type t -> t==Type.NIL ? new IntARM(con) : new ConstantNode(con);
        };
    }

    private Node call(CallNode call){
        return call.fptr() instanceof ConstantNode con && con._con instanceof TypeFunPtr tfp
                ? new CallARM(call, tfp)
                : new CallRRARM(call);
    }

    private Node or(OrNode or) {
        int imm12;
        return or.in(2) instanceof ConstantNode off && off._con instanceof TypeInteger ti && (imm12 = imm12Logical(ti)) != -1
                ? new OrIARM(or, imm12)
                : new OrARM(or);
    }

    private Node xor(XorNode xor) {
        int imm12;
        return xor.in(2) instanceof ConstantNode off && off._con instanceof TypeInteger ti && (imm12 = imm12Logical(ti)) != -1
                ? new XorIARM(xor, imm12)
                : new XorARM(xor);
    }

    private Node and(AndNode and) {
        int imm12;
        return and.in(2) instanceof ConstantNode off && off._con instanceof TypeInteger ti && (imm12 = imm12Logical(ti)) != -1
                ? new AndIARM(and, imm12)
                : new AndARM(and);
    }

    private Node asr(SarNode asr) {
        return asr.in(2) instanceof ConstantNode off && off._con instanceof TypeInteger ti && ti.value() >= 0 && ti.value() < 63
                ? new AsrIARM(asr, (int)ti.value())
                : new AsrARM(asr);
    }

    private Node lsl(ShlNode lsl) {
        return lsl.in(2)  instanceof ConstantNode off && off._con instanceof TypeInteger ti && ti.value() >= 0 && ti.value() < 63
                ? new LslIARM(lsl, (int)ti.value())
                : new LslARM(lsl);
    }

    private Node lsr(ShrNode lsr) {
        return lsr.in(2)  instanceof ConstantNode off && off._con instanceof TypeInteger ti && ti.value() >= 0 && ti.value() < 63
                ? new LsrIARM(lsr, (int)ti.value())
                : new LsrARM(lsr);
    }


    private static int off;
    private static Node idx;
    private Node st(StoreNode st) {
        return new StoreARM(address(st),st.ptr(),idx,off,st.val());
    }

    // Gather addressing mode bits prior to constructing.  This is a builder
    // pattern, but saving the bits in a *local* *global* here to keep mess
    // contained.
    private <N extends MemOpNode> N address(N mop ) {
        off = 0;  // Reset
        idx = null;
        Node base = mop.ptr();
        // Skip/throw-away a ReadOnly, only used to typecheck
        if( base instanceof ReadOnlyNode read ) base = read.in(1);
        assert !(base instanceof AddNode) && base._type instanceof TypeMemPtr; // Base ptr always, not some derived
        if( mop.off() instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm9(ti) ) {
            off = (int)ti.value();
            assert off == ti.value(); // In 32-bit range
        } else {
            idx = mop.off();
        }
        return mop;
    }


}
