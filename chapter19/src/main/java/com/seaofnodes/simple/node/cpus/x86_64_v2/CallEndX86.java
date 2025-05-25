package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.CodeGen;
import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.node.CallEndNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeFloat;
import com.seaofnodes.simple.type.TypeFunPtr;
import java.io.ByteArrayOutputStream;

public class CallEndX86 extends CallEndNode implements MachNode {
    final TypeFunPtr _tfp;
    CallEndX86( CallEndNode cend ) {
        super(cend);
        _tfp = (TypeFunPtr)cend.call().fptr()._type;
    }

    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { throw Utils.TODO(); }
    @Override public RegMask outregmap(int idx) {
        if( idx != 2 ) return null;
        return _tfp._ret instanceof TypeFloat ? x86_64_v2.RET_FMASK : x86_64_v2.RET_MASK;
    }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {  }

    @Override public String op() { return "cend"; }

}
