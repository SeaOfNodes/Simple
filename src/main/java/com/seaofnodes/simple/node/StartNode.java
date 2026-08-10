package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;

/** Unknown callers: control, exposed memory, and the entry argument.
 *  Stop feeds discovered addresses back here during SCCP. This closes the
 *  cycle between known code and unknown callers without a runtime loop.
 */
public class StartNode extends LoopNode implements MultiNode {

    final Type _arg;
    private TypeTuple _closed;

    public StartNode(Type arg) { super(null,null); _arg = arg; _type = compute(); }
    public StartNode(StartNode start) { super(start); _arg = start==null ? null : start._arg; _closed=start==null ? null : start._closed; }

    public void close() {
        _closed=(TypeTuple)_type;
        ProjNode mem=proj(1);
        if( mem!=null ) mem.subsume(new ConstantNode(mem._type).peephole());
        // Stop is a graph root even after the analysis feedback edge is gone.
        Node stop=in(1).keep();
        setDef(1,null);
        stop.unkeep();
    }

    @Override public String label() { return "Start"; }

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p(label());
    }

  @Override public CFGNode cfg0() { return null; }

    // Get the one control following; error to call with more than one e.g. an
    // IfNode or other multi-way branch.  For Start, its "main"
    @Override public CFGNode uctrl() {
        // Find "main", it's the start.
        CFGNode C = null;
        for( Node use : _outputs )
            if( use instanceof FunNode fun && fun.sig().isa(CodeGen.CODE._main) )
                { assert C==null; C = fun; }
        return C;
    }


    @Override public TypeTuple compute() {
        if( _closed!=null ) return _closed;
        TypeMem mem = in(1)==null || CodeGen.CODE._phase==null ||
            CodeGen.CODE._phase.ordinal()<CodeGen.Phase.Opto.ordinal() ? TypeMem.BOT :
            in(1)._type instanceof TypeMem m ? m : in(1)._type.isHigh() ? TypeMem.START : TypeMem.BOT;
        return TypeTuple.make(Type.CONTROL,mem,_arg);
    }

    public boolean escapedFIDX(int fidx) {
        if( !(_type instanceof TypeTuple tt) ) return !_type.isHigh();
        return XInt.bit(tt.mem()._escFs,fidx);
    }

    @Override public Node idealize() { return null; }

    // No immediate dominator, and idepth==0
    @Override public int idepth() { return 0; }
    @Override public CFGNode idom(Node dep) { return null; }

}
