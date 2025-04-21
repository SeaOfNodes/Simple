package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.SB;

// Convert a narrow C return register to Simple's full-width integer value.
public class ExtendX86 extends MachConcreteNode {
    final int _bits;
    final boolean _signed;
    ExtendX86(ProjNode prj, TypeInteger ret) {
        super(prj);
        _inputs.set(0,null);
        _inputs.add(new ProjX86(prj));
        _bits = 8 << ret.log_size();
        _signed = ret._min < 0;
    }
    @Override public String op() { return (_signed ? "sext" : "zext")+_bits; }
    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    @Override public void encoding(Encoding enc) {
        X86.extend(enc,_bits,_signed,enc.reg(this),enc.reg(in(1)));
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1)));
    }
}
