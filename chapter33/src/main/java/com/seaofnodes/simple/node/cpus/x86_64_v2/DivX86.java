package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

public class DivX86 extends MachConcreteNode implements MachNode {
    private final boolean _mod;
    DivX86( Node div ) { super(div); _mod=div instanceof ModNode; }
    @Override public String op() { return _mod ? "mod" : "div"; }
    @Override public RegMask regmap(int i) {
        return (i==1) ? x86_64_v2.RAX_MASK : x86_64_v2.DIV_MASK;
    }
    @Override public RegMask outregmap() { return _mod ? x86_64_v2.RDX_MASK : x86_64_v2.RAX_MASK; }
    @Override public RegMask killmap() {
        return new RegMask((1L<<(_mod ? x86_64_v2.RAX : x86_64_v2.RDX)) | (1L<<x86_64_v2.FLAGS));
    }

    @Override public void encoding( Encoding enc ) {
        int rhs=enc.reg(in(2)), dst=enc.reg(this);
        // IDIV faults on zero and MIN_VALUE/-1. Handle both before issuing it.
        X86.reg(enc,0x85,rhs,rhs); // TEST rhs,rhs
        enc.add1(0x0F).add1(0x84);
        int zero=enc._bits.size(); enc.add4(0); // JZ zero
        X86.imm(enc,0x81,7,rhs,-1); // CMP rhs,-1
        enc.add1(0x0F).add1(0x85);
        int divide=enc._bits.size(); enc.add4(0); // JNE divide
        if( _mod ) X86.reg(enc,0x33,dst,dst);
        else X86.unary(enc,0xF7,3,dst); // NEG wraps MIN_VALUE, like ideal division.
        enc.add1(0xE9);
        int minusOne=enc._bits.size(); enc.add4(0); // JMP done
        enc.patch4(divide,enc._bits.size()-divide-4);
        X86.divide(enc,rhs);
        enc.add1(0xE9);
        int done=enc._bits.size(); enc.add4(0); // JMP done
        enc.patch4(zero,enc._bits.size()-zero-4);
        X86.reg(enc,0x33,dst,dst);
        enc.patch4(minusOne,enc._bits.size()-minusOne-4);
        enc.patch4(done,enc._bits.size()-done-4);
    }
    // General form: "div  dst /= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(_mod ? " % " : " / ").p(code.reg(in(2)));
    }
    @Override public String comment() { return (_mod ? "kill rax" : "kill rdx")+", flags; zero and -1 guarded"; }
}
