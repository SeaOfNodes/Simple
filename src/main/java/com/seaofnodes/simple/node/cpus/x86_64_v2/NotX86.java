package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;

public class NotX86 extends MachConcreteNode implements MachNode {
    NotX86(NotNode not)  {super(not);}
    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.RMASK;  }

    @Override public int twoAddress( ) { return 0; }

    // NotNode(BoolNode) = Inverted BoolNode(1)
    // NotNode(NotNode(BoolNode)) = BoolNode(2)
    // anything else to test & setz(3)
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }
    @Override public void asm(CodeGen code, SB sb) { sb.p(code.reg(this)); }
    @Override public String op() { return "not"; }
}
