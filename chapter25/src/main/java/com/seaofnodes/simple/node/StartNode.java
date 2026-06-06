package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Utils;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;

/**
 * The Start node represents the external world calling, be it calling main or
 * just some linked caller calling.
 * <p>
 * Start initially has 1 input (arg) from outside and the initial control.
 * In ch10 we also add mem aliases as structs get defined; each field in struct
 * adds a distinct alias to Start's tuple.
 * <p>
 * By ch25, Start is the external world (or linker) calling with everything
 * that has escaped.  Start is NOT the FunNode called *main* (which can assume
 * no memory nor fidxs exist nor escaped).  Start gets escaped things from
 * Stop; together these close the Grand Cycle between the code we know and the
 * code we don't.
 */
public class StartNode extends LoopNode implements MultiNode {

    final Type _arg;

    public StartNode(StopNode stop, Type arg) { super(null,stop); _arg = arg;  }
    public StartNode(StartNode start) { super(start); _arg = start==null ? null : start._arg; }
    @Override public Tag serialTag() { return Tag.Start; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
      return sb.append(label());
    }

    @Override public CFGNode cfg0() { return null; }

    // Get the one control following; error to call with more than one e.g. an
    // IfNode or other multi-way branch.  For Start, it's the class init for
    // this compilation unit.
    @Override public CFGNode uctrl() {
        // Find module.<clinit>, it's the start.
        CFGNode C = null;
        for( Node use : _outputs )
            if( use instanceof FunNode fun && fun.isModInit() )
                { assert C==null; C = fun; }
        return C;
    }

    boolean escapedFIDX(int fidx) {
        if( !(_type instanceof TypeTuple tt) )
            return !_type.isHigh(); // TOP never escapes, BOTTOM always escapes
        TypeMem tmem = (TypeMem)tt._types[1];
        if( tmem == TypeMem.BOT ) return true;
        if( tmem == TypeMem.TOP ) return false;
        return XInt.bit(tmem._escFs,fidx);
    }

    @Override public TypeTuple compute() {
        TypeMem tmem;
        StopNode stop = (StopNode)in(1);
        if( !(stop._type instanceof TypeTuple tt) )
            tmem = stop._type.isHigh() ? TypeMem.TOP : TypeMem.BOT;
        else {
            tmem = (TypeMem)tt._types[1];
            if( tmem.isHigh() )
                tmem = TypeMem.make(1,Type.BOTTOM,false,false,tmem._escFs,tmem._escAs);
        }
        return TypeTuple.make(Type.CONTROL,tmem,_arg);
    }

    @Override public Node idealize() { return null; }

    // No immediate dominator, and idepth==0
    @Override public int idepth() { return CodeGen.CODE.iDepthAt(0); }
    @Override public CFGNode idom(Node dep) { return null; }

    @Override public boolean eq( Node n ) { return true; }
}
