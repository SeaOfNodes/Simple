package com.seaofnodes.simple.print;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeMem;
import com.seaofnodes.simple.type.TypeRPC;

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
        if( fun._name != null ) sb.p(fun._name).p(" ");
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

    static private final int encWidth = 10;
    static private final int opWidth = 5;
    static private final int argWidth = 30;
    static int doBlock(int iadr, SB sb, CodeGen code, FunNode fun, int cfgidx) {
        CFGNode bb = code._cfg.at(cfgidx);
        if( bb != fun && !(bb instanceof IfNode) && !(bb instanceof CallEndNode) && !(bb instanceof CallNode)  && !(bb instanceof CProjNode && bb.in(0) instanceof CallEndNode ))
            sb.p(label(bb)).p(":").nl();
        if( bb instanceof CallNode ) return iadr;
        final boolean postAlloc = code._phase.ordinal() > CodeGen.Phase.RegAlloc.ordinal();
        final boolean postEncode= code._phase.ordinal() >=CodeGen.Phase.Encoding.ordinal();

        boolean once=false;
        for( Node n : bb.outs() ) {
            if( !(n instanceof PhiNode phi) ) continue;
            if( phi._type instanceof TypeMem || phi._type instanceof TypeRPC ) continue; // Nothing for the hidden ones
            // Post-RegAlloc phi prints all on one line
            if( postAlloc ) {
                if( !once ) { once=true; sb.fix(4," ").p(" ").fix(encWidth,"").p("  "); }
                sb.p(phi._label).p(':').p(code.reg(phi)).p(',');
            } else {
                // Pre-RegAlloc phi prints one line per
                sb.fix(4," ").p(" ").fix(encWidth,"").p("  ").fix(opWidth,phi._label).p(" ").p(code.reg(phi));
                if( phi.getClass() == PhiNode.class ) {
                    sb.p(" = phi( ");
                    for( int i=1; i<phi.nIns(); i++ )
                        sb.p("N").p(phi.in(i)._nid).p(",");
                    sb.unchar().p(" )");
                }
                sb.nl();
            }
        }
        if( once ) sb.unchar().nl();

        // All the non-phis
        for( int i=0; i<bb.nOuts(); i++ )
            if( !(bb.out(i) instanceof PhiNode) )
                iadr = doInst(iadr, sb,code, cfgidx, bb.out(i),postAlloc, postEncode );

        return iadr;
    }

    static int doInst( int iadr, SB sb, CodeGen code, int cfgidx, Node n, boolean postAlloc, boolean postEncode ) {
        if( n instanceof CProjNode ) return iadr;
        if( postAlloc && n instanceof CalleeSaveNode ) return iadr;
        if( postEncode && n instanceof ProjNode ) return iadr;
        if( n instanceof MemMergeNode ) return iadr;

        // All blocks ending in a Region will need to either fall into or jump
        // to this block.  Until the post-reg-alloc block layout cleanup, we
        // need to assume a jump.  There's no real hardware op here, yet.
        if( n instanceof RegionNode cfg && !(n instanceof FunNode) ) {
            if( postEncode ) return iadr; // All jumps inserted already
            while( cfgidx < code._cfg._len-1 ) {
                CFGNode next = code._cfg.at(++cfgidx);
                if( next == n ) return iadr; // Fall-through, no branch
                if( next.nOuts()>1 )
                    break;      // Has code in the block, need to jump around
                // No code in the block, can fall through it
            }
            sb.hex2(iadr++).p(" ").fix(encWidth,"??").p("  ").fix(opWidth,"JMP").p(" ").fix(argWidth,label(cfg)).nl();
            return iadr;
        }

        // ProjNodes following a multi (e.g. Call or New results),
        // get indent slightly and just print their index & node#
        if( n instanceof ProjNode proj ) {
            if( proj._type instanceof TypeMem ) return iadr; // Nothing for the hidden ones
            sb.fix(4," ").p(" ").fix(encWidth,"").p("    ").fix(opWidth,proj._label==null ? "---" : proj._label).p(" ").p(code.reg(n)).nl();
            return iadr;
        }

        // ADDR ENCODING  Op--- dst = src op src       // Comment
        // 1234 abcdefgh  ld4   RAX = [Rbase + off]    // Comment
        sb.hex2(iadr).p(" ");

        // Encoding
        int fatEncoding = 0;
        if( code._encoding != null ) {
            int size = code._encoding._opLen[n._nid];
            for( int i=0; i<Math.min(size,encWidth>>1); i++ )
                sb.hex1(code._encoding._bits.buf()[iadr++]);
            for( int i=size*2; i<encWidth; i++ )
                sb.p(" ");
            fatEncoding = size - (encWidth>>1); // Not-printed parts of encoding
        } else
            sb.fix(encWidth,"");
        sb.p("  ");

        // Op; generally "ld4" or "call"
        sb.fix(opWidth, n instanceof MachNode mach ? mach.op() : n.label());
        sb.p(" ");

        // General asm args
        if( n instanceof MachNode mach ) {
            int old = sb.len();
            mach.asm(code,sb);
            sb.fix(argWidth-(sb.len()-old),""); // Pad out

        } else if( !(n._type instanceof TypeMem) ) {
            // Room for some inputs
            sb.fix(5, n._nid+":" );
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
        String comment = n.comment();
        if( comment!=null ) sb.p("// ").p(comment);

        sb.nl();


        if( fatEncoding > 0 ) {
            sb.hex2(iadr).p(" ");
            for( int i=0; i<fatEncoding; i++ )
                sb.hex1(code._encoding._bits.buf()[iadr++]);
            sb.nl();
        }


        // MultiNodes are immediately followed by projection(s)
        if( !(n instanceof CFGNode) && n instanceof MultiNode ) {
            for( Node proj : n._outputs ) {
                assert proj instanceof ProjNode;
                doInst(iadr,sb,code, cfgidx, proj,postAlloc,postEncode);
            }
        }

        return iadr;
    }

    private static String label( CFGNode bb ) {
        return (bb instanceof LoopNode ? "LOOP" : "L")+bb._nid;
    }

}
