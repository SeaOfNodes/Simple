package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.util.SB;

public class CallEndMach extends CallEndNode implements MachNode {
    public CallEndMach( CallEndNode n ) { super(n); }
    // MachNode specifics, shared across all CPUs
    public int _xslot;
    private RegMask _retMask;
    private RegMask _kills;
    public void cacheRegs(CodeGen code) {
        // Return mask depends on TFP (either GPR or FPR)
        _retMask = code._mach.retMask(call().tfp());
        // Kill mask is all caller-saves, and any mirror stack slots for args
        // in registers.
        RegMaskRW kills = code._callerSave.copy();
        // Start of stack slots
        int maxReg = code._mach.regs().length;
        // Incoming function arg slots, all low numbered in the RA
        int fslot = fun()._maxArgSlot;
        // Killed slots for this calls outgoing args
        int xslot = code._mach.maxArgSlot(call().tfp());
        _xslot = (maxReg+fslot)+xslot;
        for( int i=0; i<xslot; i++ )
            kills.set((maxReg+fslot)+i);
        _kills = kills;
    }
    public String op() { return "cend"; }
    public RegMask regmap(int i) { return null; }
    public RegMask outregmap() { return null; }
    public RegMask outregmap(int idx) { return idx==2  ? _retMask : null; }
    public RegMask killmap() { return _kills; }
    public void encoding( Encoding enc ) { }
    public void asm(CodeGen code, SB sb) {  }
}
