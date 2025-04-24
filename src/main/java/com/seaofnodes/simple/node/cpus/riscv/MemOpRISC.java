package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;

public abstract class MemOpRISC extends MemOpNode implements MachNode {
    final int _off;             // Limit 12 bits
    final char _sz;
    MemOpRISC(MemOpNode mop, Node base, int off, Node val) {
        super(mop,mop);
        assert base._type instanceof TypeMemPtr;
        _inputs.setX(2, base ); // Base can be an Add, is no longer raw object base
        _inputs.setX(3, null);  // Never an index
        _inputs.setX(4, val );
        _off = off;
        _sz = (char)('0'+(1<<_declaredType.log_size()));
    }

    @Override public String label() { return op(); }
    Node val() { return in(4); } // Only for stores

    @Override public StringBuilder _printMach(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override public Type compute() { throw Utils.TODO(); }
    @Override public Node idealize() { throw Utils.TODO(); }

    // func3 is based on load/store size and extend
    int func3() {
        int func3 = -1;
        // no unsigned flavour for store, so both signed and unsigned trigger the same
        if(this instanceof StoreRISC) {
            if( _declaredType == TypeInteger. I8 || _declaredType == TypeInteger.U8  || _declaredType == TypeInteger.BOOL) func3=0; //   SB
            if( _declaredType == TypeInteger.I16 || _declaredType == TypeInteger.U16 ) func3=1; // SH
            if( _declaredType == TypeInteger.I32 || _declaredType == TypeInteger.U32 || _declaredType instanceof TypeMemPtr) func3=2; //  SW
            if( _declaredType == TypeInteger.BOT ) func3=3; //   SD
            if( func3 == -1 ) throw Utils.TODO();
            return func3;
        }
        if( _declaredType == TypeInteger. I8 ) func3=0; // LB
        if( _declaredType == TypeInteger.I16 ) func3=1; // LH
        if( _declaredType == TypeInteger.I32 ) func3=2; // LW
        if( _declaredType == TypeInteger.BOT ) func3=3; // LD
        if( _declaredType == TypeInteger. U8 ) func3=4; // LBU
        if( _declaredType == TypeInteger.BOOL) func3=4; // LBU
        if( _declaredType == TypeInteger.U16 ) func3=5; // LHU
        if( _declaredType == TypeInteger.U32 ) func3=6; // LWU

        // float
        if(_declaredType == TypeFloat.F32) func3 = 2; // fLW   fSW
        if(_declaredType == TypeFloat.F64) func3 = 3; // fLD   fSD
        if( _declaredType instanceof TypeMemPtr ) func3=3; // 8 byte pointers
        if( func3 == -1 ) throw Utils.TODO();
        return func3;
    }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) {
        // 0 - ctrl
        // 1 - memory
        if( i==2 ) return riscv.RMASK;    // base
        // 2 - index
        if( i==4 ) return riscv.MEM_MASK; // value
        throw Utils.TODO();
    }

    SB asm_address(CodeGen code, SB sb) {
        sb.p("[").p(code.reg(ptr())).p("+");
        return sb.p(_off).p("]");
    }
}
