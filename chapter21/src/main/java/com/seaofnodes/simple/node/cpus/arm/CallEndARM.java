package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.CallEndNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeFunPtr;

public class CallEndARM extends CallEndNode implements MachNode {
    final TypeFunPtr _tfp;
    CallEndARM( CallEndNode cend ) {
        super(cend);
        _tfp = (TypeFunPtr)(cend.call().fptr()._type);
    }

    @Override public String op() { return "cend"; }
    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return null; }
    @Override public RegMask outregmap(int idx) { return idx == 2 ? arm.retMask(_tfp,2) : null; }
    @Override public RegMask killmap() { return arm.armCallerSave(); }
    @Override public void encoding( Encoding enc ) { }
    @Override public void asm(CodeGen code, SB sb) {  }
}
