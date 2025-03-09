package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.riscv.riscv;

import java.io.ByteArrayOutputStream;


// Jump on flags, uses flags
public class BranchARM extends IfNode implements MachNode{
    final String _bop;
    BranchARM(IfNode iff, String bop ) {
        super(iff);
        _bop = bop;
    }

    @Override public String label() { return op(); }

    @Override public void postSelect() {
        Node set = in(1);
        Node cmp = set.in(1);
        // Bypass an expected Set and just reference the cmp directly
        if( set instanceof SetARM)
            _inputs.set(1,cmp);
        else
            throw Utils.TODO();
    }

    @Override public RegMask regmap(int i) { assert i==1; return arm.FLAGS_MASK; }
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // Assuming that condition flags are already set
        // These flags are set by comparison
        // Todo: implement more efficient solution with subs
        // no need for regs because it uses flags

        arm.COND cond = switch (_bop) {
            case "==" -> arm.COND.EQ;
            case "<" -> arm.COND.LE;
            case "<=" -> arm.COND.LT;
            default -> null;
        };
        int beforeSize = bytes.size();
        assert cond != null;
        int body = arm.b_cond(84, 0, cond);

        arm.push_4_bytes(body, bytes);
        return bytes.size() - beforeSize;


    }

    @Override public void asm(CodeGen code, SB sb) {
        String src = code.reg(in(1));
        if( src!="flags" )  sb.p(src);
    }

    @Override public String op() { return "j"+_bop; }
    @Override public String comment() {
        return "L"+cproj(1)._nid+", L"+cproj(0)._nid;
    }
}
