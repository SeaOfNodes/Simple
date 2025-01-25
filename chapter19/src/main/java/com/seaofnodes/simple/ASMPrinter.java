package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.type.TypeFunPtr;

public abstract class ASMPrinter {

    public static SB print(SB sb, CodeGen code) {
        if( code._cfg==null )
            return sb.p("Need _cfg set, run after GCM");

        // instruction address
        int iadr = 0;
        for( int i=0; i<code._cfg._len; i++ )
            if( code._cfg.at(i) instanceof FunNode fun )
                iadr = print(iadr,sb,code,fun,i);
        return sb;
    }

    private static int print(int iadr, SB sb, CodeGen code, FunNode fun, int cfgidx) {
        // Function header
        sb.nl().p("---");
        fun.sig().print(sb);
        sb.p("---------------------------").nl();


        while( !(code._cfg.at(cfgidx) instanceof ReturnNode) )
            iadr = doBlock(iadr,sb,code,fun,cfgidx++);

        // Function separator
        sb.p("---");
        fun.sig().print(sb);
        sb.p("---------------------------").nl();
        return iadr;
    }

    static int doBlock(int iadr, SB sb, CodeGen code, FunNode fun, int cfgidx) {
        CFGNode bb = code._cfg.at(cfgidx);
        if( bb != fun )
            sb.p("L").p(bb._nid).p(":").nl();

        for( Node n : bb._outputs )
            iadr = doInst(iadr, sb,code,fun,bb,n);

        return iadr;
    }

    static int doInst(int iadr, SB sb, CodeGen code, FunNode fun, CFGNode bb, Node n) {
        if( n instanceof PhiNode ) return iadr;

        // ADDR ENCODING  Op--- dst = src op src       // Comment
        // 1234 abcdefgh  ld4   RAX = [Rbase + off]    // Comment
        sb.hex4(iadr);
        sb.p(" ");

        // Encoding
        final int encWidth = 8;
        int size = 1;           // TODO: Fake encoding size
        iadr += size;
        sb.fix(encWidth,"??");
        sb.p("  ");

        // Op; generally "ld4" or "call"
        final int opWidth = 5;
        sb.fix(opWidth, n instanceof MachNode mach ? mach.op() : n.label());
        sb.p(" ");

        // General asm args
        final int argWidth = 20;
        if( n instanceof MachNode mach ) {
            int old = sb.len();
            mach.asm(code,sb);
            sb.fix(argWidth-(sb.len()-old),""); // Pad out

        } else {
            // Room for some inputs
            int off = 0;
            for( int i=0; i<n.nIns(); i++ ) {
                sb.fix(5, n.in(i) == null ? "___" : String.valueOf(n.in(i)._nid));
                off += 5;
                if( off >= argWidth ) break;
            }
            if( off < argWidth ) sb.fix(argWidth-off,"");
        }
        sb.p(" ");

        // Comment

        sb.nl();
        return iadr;
    }
}
