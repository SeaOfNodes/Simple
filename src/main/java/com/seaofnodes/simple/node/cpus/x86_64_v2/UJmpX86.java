package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;

// unconditional jump
public class UJmpX86 extends CFGNode implements MachNode, RIPRelSize {
    UJmpX86() { }
    @Override public String op() { return "jmp"; }
    @Override public String label() { return op(); }
    @Override protected String format() { return "jmp "; }
    @Override public RegMask regmap(int i) {return null; }
    @Override public RegMask outregmap() { return null; }
    @Override public Type compute() { throw Utils.TODO(); }
    @Override public Node idealize() { throw Utils.TODO(); }
    @Override public void encoding( Encoding enc ) {
        enc.jump(this,uctrl());
        X86.branch(enc,-1);
    }

    // Delta is from opcode start, but X86 measures from the end of the 2-byte encoding
    @Override public byte encSize(int delta) {
        return X86.branchSize(delta,false);
    }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        X86.patchBranch(enc.bits(),opStart,opLen,delta,-1);
    }

    @Override public void asm(CodeGen code, SB sb) {
        CFGNode target = uctrl();
        //assert target.nOuts() > 1; // Should optimize jmp to empty targets
        sb.p(label(target));
    }
}
