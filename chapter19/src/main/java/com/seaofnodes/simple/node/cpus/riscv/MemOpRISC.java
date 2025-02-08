package com.seaofnodes.simple.node.cpus.riscv;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.x86_64_v2.x86_64_v2;
import com.seaofnodes.simple.type.*;
import java.io.ByteArrayOutputStream;
import java.lang.StringBuilder;
import java.util.BitSet;

// Generic risc memory operand base.
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

public class MemOpRISC extends MemOpNode implements MachNode{
    final int _off;             // Limit 32 bits
    final int _scale;           // Limit 0,1,2,3
    final int _imm;             // Limit 32 bits
    final char _sz = (char)('0'+(1<<_declaredType.log_size()));
    MemOpRISC( Node op, MemOpNode mop, Node base, Node idx, int off, int scale, int imm ) {
        super(op, mop);
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
    MemOpRISC(Node op, MemOpNode mop, Node base, Node idx, int off, int scale, int imm, Node val) {
        this(op, mop, base, idx, off, scale, imm);
        _inputs.setX(4, val);
    }
    Node idx() { return in(3); }
    Node val() { return in(4); }

    @Override public  StringBuilder _printMach(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override public String label() { return op(); }
    @Override public Type compute() { throw Utils.TODO(); }
    @Override public Node idealize() { throw Utils.TODO(); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) {
        if( i==1 ) return RegMask.EMPTY;    // Memory
        if( i==2 ) return riscv.RMASK;  // base
        if( i==3 ) return riscv.RMASK;  // index
        if( i==4 ) return riscv.RMASK;  // value
        throw Utils.TODO();
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { throw Utils.TODO(); }

    @Override public int encoding(ByteArrayOutputStream bytes) { throw Utils.TODO(); }


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

