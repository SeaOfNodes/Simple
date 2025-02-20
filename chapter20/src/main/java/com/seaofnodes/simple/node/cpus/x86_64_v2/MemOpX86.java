package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.io.ByteArrayOutputStream;
import java.lang.StringBuilder;
import java.util.BitSet;

// Generic X86 memory operand base.
// inputs:
//   ctrl     - will appear for possibly RCE (i.e. array ops)
//   mem      - memory dependence edge
//   base     - Basic object pointer
//   idx/null - Scaled index offset, or null if none
//   val/null - Value to store, as part of a op-to-mem or op-from-mem.  Null for loads, or if an immediate is being used
// Constants:
//   offset   - offset added to base.  Can be zero.
//   scale    - scale on index; only 0,1,2,4,8 allowed, 0 is only when index is null
//   imm      - immediate value to store or op-to-mem, and only when val is null
public abstract class MemOpX86 extends MemOpNode implements MachNode {
    final int _off;             // Limit 32 bits
    final int _scale;           // Limit 0,1,2,3
    final int _imm;             // Limit 32 bits
    final char _sz = (char)('0'+(1<<_declaredType.log_size()));
    MemOpX86( Node op, MemOpNode mop, Node base, Node idx, int off, int scale, int imm ) {
        super(op,mop);
        assert base._type instanceof TypeMemPtr && !(base instanceof AddNode);
        assert (idx==null && scale==0) || (idx!=null && 0<= scale && scale<=3);

        // Copy memory parts from eg the LoadNode over the opcode, e.g. an Add
        if( op != mop ) {
            _inputs.set(0,mop.in(0)); // Control from mem op
            _inputs.set(1,mop.in(1)); // Memory  from mem op
            _inputs.set(2,base);      // Base handed in
        }

        assert ptr() == base;
        _inputs.setX(3,idx);
        _off = off;
        _scale = scale;
        _imm = imm;
    }

    // Store-based flavors have a value edge
    MemOpX86( Node op, MemOpNode mop, Node base, Node idx, int off, int scale, int imm, Node val ) {
        this(op,mop,base,idx,off,scale,imm);
        _inputs.setX(4,val);
    }

    Node idx() { return in(3); }
    Node val() { return in(4); } // Only for stores, including op-to-memory

    @Override public  StringBuilder _printMach(StringBuilder sb, BitSet visited) {
        return sb.append(".").append(_name);
    }

    @Override public String label() { return op(); }
    @Override public Type compute() { throw Utils.TODO(); }
    @Override public Node idealize() { throw Utils.TODO(); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) {
        if( i==1 ) return null;            // Memory
        if( i==2 ) return x86_64_v2.RMASK; // base
        if( i==3 ) return x86_64_v2.RMASK; // index
        if( i==4 ) return x86_64_v2.RMASK; // value
        throw Utils.TODO();
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { throw Utils.TODO(); }

    @Override public int encoding(ByteArrayOutputStream bytes) {
        switch (this) {
            case LoadX86 loadX86 -> {
                // REX.W + 8B /r	MOV r64, r/m64
                short reg = -1;
                // might be reduntant, it should be always provided
                LRG load_rg = CodeGen.CODE._regAlloc.lrg(this);
                if(load_rg != null) reg = load_rg.get_reg();

                int beforeSize = bytes.size();

                LRG base_rg = CodeGen.CODE._regAlloc.lrg(in(2));
                LRG idx_rg = CodeGen.CODE._regAlloc.lrg(in(3));

                short base_reg = base_rg.get_reg();
                short idx_re = -1;
                if(idx_rg != null) idx_re = idx_rg.get_reg();

                bytes.write(x86_64_v2.rex(reg, idx_re, base_reg));

                bytes.write(0x8B); // opcode


                // rsp is hard-coded here(0x04)
                // includes modrm internally
                x86_64_v2.sibAdr(_scale, idx_re, base_reg, _off, reg, bytes);

                return bytes.size() - beforeSize;
            }
            case StoreX86 storeX86 -> {
                // REX.W + C7 /0 id	MOV r/m64, imm32
                short reg = -1;
                LRG store_rg = CodeGen.CODE._regAlloc.lrg(in(4));
                if(store_rg != null) reg = store_rg.get_reg();

                int beforeSize = bytes.size();

                LRG base_rg = CodeGen.CODE._regAlloc.lrg(in(2));
                LRG idx_rg = CodeGen.CODE._regAlloc.lrg(in(3));

                short base_reg = base_rg.get_reg();
                short idx_re = -1;
                if(idx_rg != null) idx_re = idx_rg.get_reg();

                bytes.write(x86_64_v2.rex(reg, idx_re, base_reg));
                bytes.write(0xC7);  // opcode

                x86_64_v2.sibAdr(_scale, idx_re, base_reg, _off, reg, bytes);
                x86_64_v2.imm(_imm, 32, bytes);

                return bytes.size() - beforeSize;
            }
            case MemAddX86 memAddX86 -> {
                // add something to memory
                //REX.W + 01 /r | REX.W + 81 /0 id
                // ADD [mem], imm32/reg
                boolean im_form = false;
                short reg = -1;
                LRG mem_rg = CodeGen.CODE._regAlloc.lrg(this);
                if(mem_rg != null) reg =  mem_rg.get_reg();

                int beforeSize = bytes.size();

                LRG base_rg = CodeGen.CODE._regAlloc.lrg(in(2));
                LRG idx_rg = CodeGen.CODE._regAlloc.lrg(in(3));

                short base_reg = base_rg.get_reg();
                short idx_re = -1;
                if(idx_rg != null) idx_re = idx_rg.get_reg();

                bytes.write(x86_64_v2.rex(reg, idx_re, base_reg));
                if(in(4) != null) {
                    // val and not immediate
                    // opcode
                    bytes.write(0x81);
                } else {
                    im_form = true;
                    bytes.write(0x01);
                }


                if(im_form) {
                    reg = 0;
                }

                // includes modrm
                x86_64_v2.sibAdr(_scale, idx_re, base_reg, _off, reg, bytes);
                if(im_form) {
                    x86_64_v2.imm(_imm, 32, bytes);
                }
                return bytes.size() - beforeSize;
            }
            case CmpMemX86 cmpMemX86 -> {
                // REX.W + 81 /7 id	CMP r/m64, imm32 | REX.W + 39 /r	CMP r/m64,r64
                // CMP [mem], imm32
                boolean im_form = false;
                short reg = -1;
                LRG mem_rg = CodeGen.CODE._regAlloc.lrg(this);
                if(mem_rg != null) reg = mem_rg.get_reg();

                int beforeSize = bytes.size();


                LRG base_rg = CodeGen.CODE._regAlloc.lrg(in(2));
                LRG idx_rg = CodeGen.CODE._regAlloc.lrg(in(3));

                short base_reg = base_rg.get_reg();
                short idx_re = -1;
                if(idx_rg != null) idx_re = idx_rg.get_reg();


                bytes.write(x86_64_v2.rex(reg, idx_re, base_reg));
                if(in(4) != null) {
                    // val and not immediate
                    // opcode
                    bytes.write(0x81);
                } else {
                    im_form = true;
                    bytes.write(0x39);
                }

                if(im_form) {
                    reg = 7;
                }

                // includes modrm
                x86_64_v2.sibAdr(_scale, idx_re, base_reg, _off, reg, bytes);
                if(im_form) {
                    x86_64_v2.imm(_imm, 32, bytes);
                }
                return bytes.size() - beforeSize;
            }
            case AddFMemX86 addFMemX86 -> {
                //  addsd xmm0, DWORD PTR [rdi+0xc]
                short reg = -1;
                LRG mem_rg = CodeGen.CODE._regAlloc.lrg(this);
                if(mem_rg != null) reg = mem_rg.get_reg();

                int beforeSize = bytes.size();

//                bytes.write(x86_64_v2.rex(0, reg - x86_64_v2.FLOAT_OFFSET));

                // F opcode
                bytes.write(0xF2);

                bytes.write(0x0F);
                bytes.write(0x58);

                LRG base_rg = CodeGen.CODE._regAlloc.lrg(in(2));
                LRG idx_rg = CodeGen.CODE._regAlloc.lrg(in(3));

                short base_reg = base_rg.get_reg();
                short idx_re = -1;
                if(idx_rg != null) idx_re = idx_rg.get_reg();

                x86_64_v2.sibAdr(_scale, idx_re, base_reg, _off, reg, bytes);

                return bytes.size() - beforeSize;
            }
            case AddMemX86 addmemX86 -> {
                // add something to register from memory
                //  add   eax,DWORD PTR [rdi+0xc]
                // REX.W + 03 /r	ADD r64, r/m64
                short reg = -1;
                LRG mem_rg = CodeGen.CODE._regAlloc.lrg(this);
                if(mem_rg != null) reg = mem_rg.get_reg();

                int beforeSize = bytes.size();


                LRG base_rg = CodeGen.CODE._regAlloc.lrg(in(2));
                LRG idx_rg = CodeGen.CODE._regAlloc.lrg(in(3));

                short base_reg = base_rg.get_reg();
                short idx_re = -1;
                if(idx_rg != null) idx_re = idx_rg.get_reg();

                bytes.write(x86_64_v2.rex(reg, idx_re, base_reg));
                // opcode
                bytes.write(0x03);

                x86_64_v2.sibAdr(_scale, idx_re, base_reg, _off, reg, bytes);

                return bytes.size() - beforeSize;
            }
            default -> {
            }
        }
        throw Utils.TODO();
    }


    // "[base + idx<<2 + 12]"
    SB asm_address(CodeGen code, SB sb) {
        sb.p("[").p(code.reg(ptr()));
        if( idx() != null ) {
            sb.p("+").p(code.reg(idx()));
            if( _scale != 0 )
                sb.p("<<").p(_scale);
        }
        if( _off != 0 )
            sb.p("+").p(_off);
        return sb.p("]");
    }

}
