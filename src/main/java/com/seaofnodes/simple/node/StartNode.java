package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.*;
import java.util.HashSet;
import static com.seaofnodes.simple.Utils.TODO;

/** Start supplies control, whole memory, and the argument, in that order. */
public class StartNode extends LoopNode implements MultiNode {

    final Type _arg;

    public StartNode(Type arg) { super(null,null); _arg = arg; _type = compute(); }

    @Override public String label() { return "Start"; }

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p(label());
    }

    @Override public boolean isMultiHead() { return true; }
    @Override public boolean blockHead() { return true; }
    @Override public CFGNode cfg0() { return null; }

    // Get the one control following; error to call with more than one e.g. an
    // IfNode or other multi-way branch.  For Start, its "main"
    @Override public CFGNode uctrl() {
        // Find "main", its the start.
        CFGNode C = null;
        for( Node use : _outputs )
            if( use instanceof FunNode fun && fun.sig().isa(TypeFunPtr.MAIN) )
                { assert C==null; C = fun; }
        return C;
    }


    @Override public TypeTuple compute() {
        return TypeTuple.make(Type.CONTROL,TypeMem.BOT,_arg);
    }

    @Override public Node idealize() { return null; }

    // No immediate dominator, and idepth==0
    @Override public int idepth() { return 0; }
    @Override public CFGNode idom(Node dep) { return null; }

}
