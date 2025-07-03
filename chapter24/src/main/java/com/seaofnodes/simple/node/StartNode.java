package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.util.Utils;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;

/**
 * The Start node represents the start of the function.
 * <p>
 * Start initially has 1 input (arg) from outside and the initial control.
 * In ch10 we also add mem aliases as structs get defined; each field in struct
 * adds a distinct alias to Start's tuple.
 */
public class StartNode extends CFGNode implements MultiNode {

    private boolean _inProgress;

    public StartNode() { super((Node)null); _inProgress = true; _type = compute(); }
    public StartNode(StartNode start) { super(start); }
    @Override public Tag serialTag() { return Tag.Start; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
      return sb.append(label());
    }

    @Override public CFGNode cfg0() { return null; }
    @Override public boolean blockHead() { return true; }

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

    public void notInProgress() { _inProgress=false; }
    public TypeFunPtr allEscapes() {
        return (TypeFunPtr) ((TypeTuple)_type)._types[2];
    }

    @Override public TypeTuple compute() {
        TypeFunPtr tfp ;
        if( _inProgress ) {
            tfp = CodeGen.CODE.allExports();
        } else {
            tfp = TypeFunPtr.MAIN0; // "main" fidx, open, no args
            for( Node n : _inputs )
                if( n instanceof CallNode call ) {
                    // All call args, minus the fcn ptr itself
                    for( int i=1; i<call.nIns()-1; i++ ) {
                        Type t = call.in(i)._type;
                        if( t instanceof TypeMem mem ) {
                            if( mem._t == Type.BOTTOM ) continue; // All the other escapes
                            else throw Utils.TODO();
                        } else if( t instanceof TypeMemPtr tmp ) {
                            // Escaping function via ptr
                            throw Utils.TODO();
                        } else if( t instanceof TypeFunPtr ) {
                            // Escaping function
                            throw Utils.TODO();
                        }
                    }
                }
            for( Node n : _outputs ) {
                if( n instanceof ConstantNode con && con._type instanceof TypeFunPtr ) {
                    for( Node use : con._outputs )
                        if( use instanceof ScopeNode scope )
                            // In-use by parser, so escapes
                            throw Utils.TODO();
                }
            }
        }

        return TypeTuple.make(Type.CONTROL,TypeMem.TOP,tfp);
    }

    @Override public Node idealize() { return null; }

    // No immediate dominator, and idepth==0
    @Override public int idepth() { return CodeGen.CODE.iDepthAt(0); }
    @Override public CFGNode idom(Node dep) { return null; }



}
