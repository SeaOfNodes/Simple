package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.util.SB;

public class FunX86 extends FunNode implements MachNode {
    FunX86( FunNode fun ) { super(fun); }
    @Override public void postSelect(CodeGen code) {
        super.postSelect(code);
        // One more for RPC pre-pushed; X86 special
        _maxArgSlot++;
    }

    @Override public void computeFrameAdjust(CodeGen code, int maxReg) {
        super.computeFrameAdjust(code,maxReg);
        // Alignment, but off by RPC
        if( _hasCalls )         // If non-leaf, pad to 16b
            _frameAdjust = ((_frameAdjust+8+8) & -16)-8;
    }

    @Override public void encoding( Encoding enc ) {
        int sz = _frameAdjust;
        if( sz == 0 ) return; // Skip if no frame adjust
        X86.imm(enc,0x81,5,x86_64_v2.RSP,sz);
    }

}
