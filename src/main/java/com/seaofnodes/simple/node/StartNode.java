package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import static com.seaofnodes.simple.Utils.TODO;

/**
 * The Start node represents the start of the function.
 *
 * Start initially has 1 input (arg) from outside and the initial control.
 * In ch10 we also add mem aliases as structs get defined; each field in struct
 * adds a distinct alias to Start's tuple.
 */
public class StartNode extends LoopNode implements MultiNode {

    final Type _arg;

    public StartNode(Type arg) { super((Parser.Lexer)null,null); _arg = arg; _type = compute(); }
    public StartNode(StartNode start) { super(start); _arg = start==null ? null : start._arg; }

    @Override public String label() { return "Start"; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
      return sb.append(label());
    }

    @Override public boolean blockHead() { return true; }
    @Override public CFGNode cfg0() { return null; }

    // Get the one control following; error to call with more than one e.g. an
    // IfNode or other multi-way branch.  For Start, its "main"
    @Override public CFGNode uctrl() {
        // Find "main", its the start.
        CFGNode C = null;
        for( Node use : _outputs )
            if( use instanceof FunNode fun && fun.sig().isa(CodeGen.CODE._main) )
                { assert C==null; C = fun; }
        return C;
    }


    @Override public TypeTuple compute() {
        return TypeTuple.make(Type.CONTROL,TypeMem.TOP,_arg);
    }

    @Override public Node idealize() { return null; }

    // No immediate dominator, and idepth==0
    @Override public int idepth() { return CodeGen.CODE.iDepthAt(0); }
    @Override public CFGNode idom(Node dep) { return null; }

}
