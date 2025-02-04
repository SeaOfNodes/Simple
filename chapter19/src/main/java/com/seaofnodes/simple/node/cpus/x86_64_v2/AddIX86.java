package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class AddIX86 extends MachConcreteNode implements MachNode {
    final TypeInteger _ti;
    AddIX86( Node add, TypeInteger ti ) { super(add); _inputs.pop(); _ti = ti; }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.WMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form: "addi  dst += #imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" + #");
        _ti.print(sb);
    }

    @Override public String op() {
        return _ti.value() == 1  ? "inc" : (_ti.value() == -1 ? "dec" : "addi");
    }
}
