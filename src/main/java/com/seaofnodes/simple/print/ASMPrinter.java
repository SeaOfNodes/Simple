package com.seaofnodes.simple.print;

import com.seaofnodes.print.AssemblyAdapter;
import com.seaofnodes.print.ASMPrinter.Data;
import com.seaofnodes.print.ASMPrinter.Pool;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.ArrayList;
import com.seaofnodes.simple.codegen.Encoding;

/** Machine-specific facts; traversal, columns and byte display live in print/. */
public final class ASMPrinter extends AssemblyAdapter<Node> {
    private final CodeGen _code;
    private final Encoding _enc;
    private ASMPrinter(CodeGen code) {
        super(new IRPrinter());
        _code=code;
        _enc=code._encoding;
    }
    public static SB print(SB sb, CodeGen code) {
        return sb.p(new com.seaofnodes.print.ASMPrinter<>(new ASMPrinter(code)).print());
    }
    @Override public ArrayList<Node> blocks() {
        var blocks=new ArrayList<Node>();
        var cfg=_code._cfg;
        if( cfg!=null ) for( Node n : cfg ) blocks.add(n);
        return blocks;
    }
    @Override public String op(Node n) { return n instanceof MachNode m ? m.op() : n.label(); }
    @Override public String register(Node n) { return _code.reg(n); }
    @Override public String comment(Node n) { return n.comment(); }
    @Override public String phiName(Node n) { return ((PhiNode)n)._label; }
    @Override public String operands(Node n) {
        SB sb=new SB();
        if( n instanceof MachNode m ) m.asm(_code,sb);
        else if( !(n._type instanceof TypeMem) ) {
            sb.p(n._nid).p(": ");
            for( Node def : n._inputs ) sb.p(def==null ? "___" : Integer.toString(def._nid)).p(' ');
        }
        return sb.toString();
    }
    @Override public boolean labelBlock(Node n) {
        return !(n instanceof FunNode) && !(n instanceof IfNode) && !(n instanceof CallNode) &&
            !(n instanceof CallEndNode) && !(n instanceof CProjNode && ir.input0(n) instanceof CallEndNode);
    }
    @Override public boolean hidden(Node n) {
        if( n instanceof PhiNode || n instanceof ProjNode )
            if( n._type instanceof TypeMem || n._type instanceof TypeRPC ) return true;
        if( postAlloc() && n instanceof CalleeSaveNode ) return true;
        if( n instanceof MemMergeNode ) return true;
        return n.getClass()==ConstantNode.class;
    }
    @Override public boolean postAlloc() { return _code._phase.ordinal()>CodeGen.Phase.RegAlloc.ordinal(); }
    @Override public boolean encoded() { return _enc!=null && _code._phase.ordinal()>=CodeGen.Phase.Encoding.ordinal(); }
    @Override public int defaultSize() { return _code._mach==null ? 2 : _code._mach.defaultOpSize(); }
    @Override public boolean littleEndian() { return _code._asmLittle; }
    @Override public boolean prologue(Node n) { return ((FunNode)n)._frameAdjust!=0; }
    @Override public byte[] bytes() { return encoded() ? _enc._bits.buf() : null; }
    @Override public int offset(Node n) {
        return encoded() && _enc._opStart!=null && n._nid<_enc._opStart.length ? _enc._opStart[n._nid] : -1;
    }
    @Override public int size(Node n) {
        return encoded() && _enc._opLen!=null && n._nid<_enc._opLen.length ? _enc._opLen[n._nid]&255 : 0;
    }
    private static ArrayList<Data> entries(Iterable<Encoding.Relo> relos) {
        var data=new ArrayList<Data>();
        for( Encoding.Relo r : relos ) {
            int align=1<<r._align;
            int size=r._t instanceof TypeStruct ? (r._structSize+align-1)&-align : align;
            if( r._t instanceof TypeTuple t ) size=align*t._types.length;
            data.add(new Data(r._t,r._t.str(),r._align,size));
        }
        return data;
    }
    @Override public ArrayList<Pool> pools(int codeEnd) {
        var pools=new ArrayList<Pool>();
        if( !encoded() ) return pools;
        int base=(codeEnd+15)&-16;
        if( !_enc._bigCons.isEmpty() && base<_enc._bits.size() )
            pools.add(new Pool("Constant Pool",_enc._bits.buf(),base,base,entries(_enc._bigCons.values())));
        return pools;
    }
}
