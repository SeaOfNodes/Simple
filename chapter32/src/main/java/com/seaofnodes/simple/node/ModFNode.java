package com.seaofnodes.simple.node;

import com.seaofnodes.isa.Arm64;
import com.seaofnodes.isa.RiscV;
import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.cpus.arm.arm;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.SB;

/** Floating remainder has no SSE/ARM/RISC-V instruction: emit a helper call. */
public class ModFNode extends MachConcreteNode implements MachNode {
    private RegMask _lhs, _rhs, _ret, _kills;
    public int _xslot;
    public ModFNode(ModNode mod) { super(mod); }
    @Override public String op() { return "modf"; }
    public void cacheRegs(CodeGen code) {
        TypeFunPtr sig=TypeFunPtr.make(false,new Type[]{TypeFloat.F64,TypeFloat.F64},TypeFloat.F64);
        _lhs=code._mach.callArgMask(sig,2,0,code._cCallingConv);
        _rhs=code._mach.callArgMask(sig,3,0,code._cCallingConv);
        _ret=code._mach.retMask(sig);
        RegMaskRW kills=code._cCallerSave.copy();
        if( code._mach instanceof arm ) kills.set(arm.FLAGS);
        int base=code._mach.regs().length+cfg0().fun()._maxArgSlot;
        _xslot=base+code._mach.maxArgSlot(sig,code._cCallingConv);
        for( int i=base; i<_xslot; i++ ) kills.set(i);
        _kills=kills;
    }
    @Override public RegMask regmap(int i) { return i==1 ? _lhs : i==2 ? _rhs : null; }
    @Override public RegMask outregmap() { return _ret; }
    @Override public RegMask killmap() { return _kills; }
    @Override public void encoding(Encoding enc) {
        enc.external(this,"simple_f64_mod");
        switch( machine().name() ) {
        case "x86_64_v2" -> X86.call(enc);
        case "arm" -> enc.add4(Arm64.b(Arm64.OP_CALL,0));
        case "riscv" -> {
            enc.add4(RiscV.u_type(RiscV.OP_AUIPC,riscv.T0,0));
            enc.add4(RiscV.i_type(RiscV.OP_JALR,riscv.RPC,0,riscv.T0,0));
        }
        default -> throw new IllegalStateException(machine().name());
        }
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" % ").p(code.reg(in(2)));
    }
    @Override public String comment() { return "call simple_f64_mod"; }
}
