package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

import java.lang.StringBuilder;
import java.util.BitSet;


public abstract class MemOpARM extends MemOpNode implements MachNode {
    final int _off;             // Limit 9 bits sized, or (13 bits<<logsize) unsigned
    final int _imm;             // Limit ? bits
    final char _sz = (char)('0'+(1<<_declaredType.log_size()));
    MemOpARM(MemOpNode mop, Node ptr, Node idx, int off, int imm) {
        super(mop,mop);
        assert ptr._type instanceof TypeMemPtr && !(ptr instanceof AddNode);
        assert ptr() == ptr;
        _inputs.setX(2, ptr);
        _inputs.setX(3, idx);
        _off = off;
        _imm = imm;
    }

    // Store-based flavors have a value edge
    MemOpARM( MemOpNode mop, Node ptr, Node idx, int off, int imm, Node val ) {
        this(mop,ptr,idx,off,imm);
        _inputs.setX(4,val);
    }

    @Override public String label() { return op();}
    Node val() { return in(4); } // Only for stores

    @Override public  StringBuilder _printMach(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override public Type compute() { throw Utils.TODO(); }
    @Override public Node idealize() { throw Utils.TODO(); }

    int size() {
        if( _declaredType == TypeFloat.F32 ) return 4;
        if( _declaredType == TypeFloat.F64 ) return 8;
        if( _declaredType == TypeInteger.I8 || _declaredType == TypeInteger.U8 || _declaredType == TypeInteger.BOOL) return 1;
        if( _declaredType == TypeInteger.I16 || _declaredType == TypeInteger.U16 ) return 2;
        if( _declaredType == TypeInteger.I32 || _declaredType == TypeInteger.U32 || _declaredType instanceof TypeMemPtr) return 4;
        if( _declaredType == TypeInteger.BOT ) return 8;
        throw Utils.TODO();
    }

    int imm_op() {
        // stores
        if(this instanceof StoreARM) {
            if(_declaredType == TypeFloat.F32) return arm.OPF_STORE_IMM_32;
            if(_declaredType == TypeFloat.F64) return arm.OPF_STORE_IMM_64;
            if(_declaredType == TypeInteger. I8 || _declaredType == TypeInteger.U8  || _declaredType == TypeInteger.BOOL) return arm.OP_STORE_IMM_8;
            if( _declaredType == TypeInteger.I16 || _declaredType == TypeInteger.U16 ) return arm.OP_STORE_IMM_16;
            if( _declaredType == TypeInteger.I32 || _declaredType == TypeInteger.U32 || _declaredType instanceof TypeMemPtr) return arm.OP_STORE_IMM_32;
            if( _declaredType == TypeInteger.BOT ) return arm.OP_STORE_IMM_64;
            throw Utils.TODO();
        }
        // loads
        if(_declaredType == TypeFloat.F32) return arm.OPF_LOAD_IMM_32;
        if(_declaredType == TypeFloat.F64) return arm.OPF_LOAD_IMM_64;
        if(_declaredType == TypeInteger. I8 || _declaredType == TypeInteger.U8  || _declaredType == TypeInteger.BOOL) return arm.OP_LOAD_IMM_8;
        if( _declaredType == TypeInteger.I16 || _declaredType == TypeInteger.U16 ) return arm.OP_LOAD_IMM_16;
        if( _declaredType == TypeInteger.I32 || _declaredType == TypeInteger.U32 || _declaredType instanceof TypeMemPtr) return arm.OP_LOAD_IMM_32;
        if( _declaredType == TypeInteger.BOT ) return arm.OP_LOAD_IMM_64;
        throw Utils.TODO();
    }
    int reg_op() {
        // stores
        if(this instanceof StoreARM) {
            if(_declaredType == TypeFloat.F32) return arm.OPF_STORE_R_32;
            if( _declaredType == TypeInteger. I8 || _declaredType == TypeInteger.U8  || _declaredType == TypeInteger.BOOL) return arm.OP_STORE_R_8; //   SB
            if( _declaredType == TypeInteger.I16 || _declaredType == TypeInteger.U16 ) return arm.OP_STORE_R_16; // SH
            if(_declaredType == TypeFloat.F64) return arm.OPF_STORE_R_64;
            if( _declaredType == TypeInteger.I32 || _declaredType == TypeInteger.U32 || _declaredType instanceof TypeMemPtr) return arm.OP_STORE_R_32;
            if( _declaredType == TypeInteger.BOT ) return arm.OP_STORE_R_64;
            throw Utils.TODO();
        }
        // loads
        if(_declaredType == TypeInteger.I8) return arm.OP_LOAD_R_8;
        if(_declaredType == TypeInteger.I16) return arm.OP_LOAD_R_16;
        if(_declaredType == TypeInteger.U8) return arm.OP_LOAD_R_8;
        if(_declaredType == TypeInteger.BOOL) return arm.OP_LOAD_R_8;
        if(_declaredType == TypeInteger.U16) return arm.OP_LOAD_R_16;

        if(_declaredType == TypeFloat.F32) return arm.OPF_LOAD_R_32;
        if(_declaredType == TypeFloat.F64) return arm.OPF_LOAD_R_64;
        if( _declaredType == TypeInteger.I32 || _declaredType == TypeInteger.U32 || _declaredType instanceof TypeMemPtr) return arm.OP_LOAD_R_32;
        if( _declaredType == TypeInteger.BOT ) return arm.OP_LOAD_R_64;
        throw Utils.TODO();
    }
    // Wider mask to store both GPRs and FPRs
    @Override public RegMask regmap(int i) {
        // 0 - ctrl
        if( i==1 ) return null; // memory
        if( i==2 ) return arm.RMASK; // ptr/base
        if( i==3 ) return arm.RMASK; // off/index
        if( i==4 ) return arm.RMASK; // value
        return null; // Anti-dependence
    }

    // Shared encoding for loads and stores(int and float)
    public void ldst_encode( Encoding enc, int opcode_imm, int opcode_reg, Node xval, int size) {
        short ptr = enc.reg(ptr());
        short off = enc.reg(off());
        short val = enc.reg(xval);
        int body = off() == null
            ? arm.load_str_imm(opcode_imm, _off, ptr, val, size)
            : arm.indr_adr(opcode_reg, off, arm.STORE_LOAD_OPTION.SXTX, 0, ptr, val);
        enc.add4(body);
    }

    SB asm_address(CodeGen code, SB sb) {
        sb.p("[").p(code.reg(ptr())).p("+");
        if( off() != null ) sb.p(code.reg(off()));
        else sb.p(_off);
        return sb.p("]");
    }
}
