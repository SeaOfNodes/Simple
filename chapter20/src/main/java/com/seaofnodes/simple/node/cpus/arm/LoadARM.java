package com.seaofnodes.simple.node.cpus.arm;


import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.LoadNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.cpus.x86_64_v2.MemOpX86;

public class LoadARM extends MemOpARM{
    LoadARM(LoadNode ld) {}
//    @Override public String op() { return "ld"+_sz; }
//
//    // Register mask allowed as a result.  0 for no register.
//    @Override public RegMask outregmap() { return arm.WMASK; }
//
//
//    // General form: "ldN  dst,[base + idx<<2 + 12]"
//    @Override public void asm(CodeGen code, SB sb) {
//        sb.p(code.reg(this)).p(",");
//        asm_address(code,sb);
//    }
}
