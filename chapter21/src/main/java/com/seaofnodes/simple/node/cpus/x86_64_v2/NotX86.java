package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.BoolNode;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.NotNode;
import com.seaofnodes.simple.node.MachNode;


public class NotX86 extends MachConcreteNode implements MachNode {
    NotX86(NotNode not)  {super(not);}
    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.RMASK;  }

    @Override public int twoAddress( ) { return 0; }

    // NotNode(BoolNode) = Inverted BoolNode(1)
    // NotNode(NotNode(BoolNode)) = BoolNode(2)
    // anything else to test & setz(3)
    @Override public void encoding( Encoding enc ) {
        int beforeSize = bytes.size();

        if(in(1) instanceof NotNode && in(1).in(1) instanceof BoolNode) {
                setDef(1, in(1).in(1));
        }

        else if(in(1) instanceof BoolNode) {
            // swap input of BoolNode invert it
            in(1).swap12();
        } else{
            // test & setz
            LRG not_rg = CodeGen.CODE._regAlloc.lrg(in(1));
            LRG out_rg = CodeGen.CODE._regAlloc.lrg(this);
            short reg1 = not_rg.get_reg();
            short out_reg = out_rg.get_reg();

            // test   rdi,rdi
            bytes.write(x86_64_v2.rex(reg1, reg1, 0));
            bytes.write(0x85);
            bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, reg1, reg1));

            // setz (sete dil)
            bytes.write(x86_64_v2.rex(out_reg, 0, 0));
            bytes.write(0x0F);
            bytes.write(0x94);

            bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, out_reg, 0));
            // REX.W + 0F B6 /r	MOVZX r64, r/m81 (sign extend)
            //  movzx  rdi,dil
            bytes.write(x86_64_v2.rex(out_reg, out_reg, 0));
            bytes.write(0x0F);
            bytes.write(0xB6);

            bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, out_reg, out_reg));
        }

        return bytes.size() - beforeSize;
    }
    @Override public void asm(CodeGen code, SB sb) { sb.p(code.reg(this)); }
    @Override public String op() { return "not"; }
}
