package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StoreNode;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;
import com.seaofnodes.simple.util.SB;

public class StoreX86 extends MemOpX86 {
    StoreX86( StoreNode st, Node base, Node idx, int off, int scale, int imm, Node val ) {
        super(st,st, base, idx, off, scale, imm, val);
    }
    @Override public String op() { return "st"+_sz; }
    @Override protected ExprPrinter<Node> _printMach(ExprPrinter<Node> p) {
        Node val = val();
        p.p(".").p(_name).p("=");
        if( val==null ) p.p(_imm);
        else p.n(val);
        return p.p(";");
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) {
        short ptr=enc.reg(ptr()), idx=enc.reg(idx()), src=enc.reg(val());
        if( src==-1 ) X86.storeImm(enc,Integer.numberOfTrailingZeros(_sz-'0'),_imm,ptr,idx,_off,_scale);
        else encVal(enc,_con,ptr,idx,src,_off,_scale);
    }

    // Non-immediate encoding
    static void encVal( Encoding enc, Type decl, short ptr, short idx, short src, int off, int scale ) {
        boolean xmm=src>=x86_64_v2.XMM_OFFSET;
        X86.store(enc,decl.log_size(),xmm,
                  xmm ? src-x86_64_v2.XMM_OFFSET : src,ptr,idx,off,scale);
    }


    // General form: "stN  [base + idx<<2 + 12],val"
    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb).p(",");
        if( val()==null ) sb.p("#").p(_imm);
        else sb.p(code.reg(val()));
    }
}
