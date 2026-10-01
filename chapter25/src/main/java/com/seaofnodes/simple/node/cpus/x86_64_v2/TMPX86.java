package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeMemPtr;
import com.seaofnodes.simple.util.SB;

public class TMPX86 extends ConstantNode implements MachNode, RIPRelSize{
    private byte _opLen;
    final String _ext;
    TMPX86( ConstantNode con, String ext ) { super(con); _ext = ext; }
    @Override public String op() { return "ldp"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    @Override public boolean isClone() { return true; }
    @Override public Node copy() { return new TMPX86(this,_ext); }

    @Override public void encoding( Encoding enc ) {
        short dst = enc.reg(this);
        // 0 or 1 for REX depending on the dst.
        _opLen = X86.rip(enc,0,0x8D,true,dst);
        if( _ext!=null ) enc.externalData(this,_ext);
        else enc.largeConstant(this,((TypeMemPtr)_con)._obj, _opLen-4, 2/*ELF encoding PC32*/);
    }

    // Delta is from opcode start
    @Override public byte encSize(int delta) { return _opLen; }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        enc.patch4(opStart + opLen - 4, delta - opLen);
    }

    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" = #"));
    }
    @Override public boolean eq(Node n) { return this==n; }
}
