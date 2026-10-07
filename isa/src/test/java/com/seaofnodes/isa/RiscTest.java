package com.seaofnodes.isa;

import com.seaofnodes.isa.eval.EvalArm64;
import com.seaofnodes.isa.eval.EvalRisc5;

/** Fixed instruction words plus execution checks of the shared test emulators. */
public class RiscTest {
    static void word(int expected, int actual) {
        assert expected==actual : String.format("Expected %08x, got %08x",expected,actual);
    }
    public static void main(String[] args) {
        word(0x9E780020,Arm64.floatToInteger(1,0)); // FCVTZS x0,d1
        for( double value : new double[]{0.0,-0.0,6.92,-6.92,0.99,-0.99,
                0x1p63,Math.nextDown(0x1p63),-0x1p63,Math.nextDown(-0x1p63),
                Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NaN,
                Double.longBitsToDouble(0xFFF0000000000001L)} ) {
            var a=new EvalArm64(new byte[1024],512);
            a.st4(4,0x9E780020);
            a._pc=4;
            a.fregs[1]=value;
            a.step(1);
            assert a.regs[0]==(long)value : "ARM conversion: "+value;

            var bytes=new X86Test.Bytes();
            RiscV.floatToInteger(bytes,10,1);
            var r=new EvalRisc5(new byte[1024],512);
            System.arraycopy(bytes.toByteArray(),0,r._buf,4,bytes.size());
            r._pc=4;
            r.fregs[1]=value;
            // NaN takes four instructions; a finite value takes five.
            r.step(Double.isNaN(value) ? 4 : 5);
            assert r.regs[10]==(long)value : "RISC-V conversion: "+value;
        }
        word(0x8B020020,Arm64.r_reg(Arm64.OP_ADD,0,2,0,1,0)); // add x0,x1,x2
        word(0x9100A420,Arm64.imm_inst(Arm64.OPI_ADD,41,1,0));
        word(0xD2800540,Arm64.mov(Arm64.OP_MOVZ,0,42,0));
        word(0xF9400BE0,Arm64.load_str_imm(Arm64.OP_LOAD_IMM_64,16,31,0,8));
        word(0xF9000BE0,Arm64.load_str_imm(Arm64.OP_STORE_IMM_64,16,31,0,8));
        word(0xD65F03C0,Arm64.ret(Arm64.OP_RET));
        word(0x17FFFFFF,Arm64.b(Arm64.OP_UJMP,-4));
        word(0x16000000,Arm64.b(Arm64.OP_UJMP,-(1<<27)));
        word(0x54000040,Arm64.b_cond(Arm64.OP_BRANCH,8,Arm64.COND.EQ));
        word(0x54800001,Arm64.b_cond(Arm64.OP_BRANCH,-(1<<20),Arm64.COND.NE));
        word(0x5CFFFFE0,Arm64.load_pc(Arm64.OPF_ARM,-4,0));

        word(0x002081B3,RiscV.r_type(RiscV.OP,3,0,1,2,0)); // add x3,x1,x2
        word(0x02A00513,RiscV.i_type(RiscV.OP_IMM,10,0,0,42));
        word(0x123451B7,RiscV.u_type(RiscV.OP_LUI,3,0x12345));
        word(0x00B13823,RiscV.s_type(RiscV.OP_STORE,3,2,11,16));
        word(0x00208463,RiscV.b_type(RiscV.OP_BRANCH,0,(short)1,(short)2,8));
        word(0xFE208EE3,RiscV.b_type(RiscV.OP_BRANCH,0,(short)1,(short)2,-4));
        word(0x008000EF,RiscV.j_type(RiscV.OP_JAL,1,8));
        word(0xFFDFF0EF,RiscV.j_type(RiscV.OP_JAL,1,-4));
        word(0x000800EF,RiscV.j_type(RiscV.OP_JAL,1,1<<19));
        word(0x800000EF,RiscV.j_type(RiscV.OP_JAL,1,-(1<<20)));

        // Constants exercise MOVN, MOVZ and multiple MOVK lanes, including all 0/1.
        for( long x : new long[]{0,-1,42,0xFFFF0000FFFFL,0x1122334455667788L,Long.MIN_VALUE} ) {
            var bytes=new X86Test.Bytes();
            Arm64.constant(bytes,0,x);
            byte[] image=new byte[1024];
            System.arraycopy(bytes.toByteArray(),0,image,4,bytes.size());
            var arm=new EvalArm64(image,512);
            arm._pc=4;
            arm.step(bytes.size()/4);
            assert arm.regs[0]==x : String.format("Constant %016x became %016x",x,arm.regs[0]);
        }
        // Raw instructions make these emulator checks independent of the encoder.
        var arm=new EvalArm64(new byte[1024],512);
        arm.st4(4,0xD2800540); // mov x0,#42
        arm._pc=4;
        arm.step(1);
        assert arm.regs[0]==42;
        var risc=new EvalRisc5(new byte[1024],512);
        risc.st4(4,0x02A00513); // addi a0,zero,42
        risc._pc=4;
        risc.step(1);
        assert risc.regs[10]==42;
        System.out.println("Shared ARM/RISC-V encoding and evaluator tests passed");
    }
}
