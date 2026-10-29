package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;

// Escapes from calls and unloaded bodies, shared by all compilation units.
// These include paths which never Return. Iter creates this after parsing;
// the outer Start holds it until Unlink. Saved objects contain the resulting
// StopCU types instead. Dependencies wake this node when contributors change;
// Stops need waking only when the combined result changes.
public class GlobalEscapeNode extends Node {
    public GlobalEscapeNode() { super(new Node[0]); _type = TypeMem.BOT; }
    @Override public String label() { return "GlobalEscape"; }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) { return p.p(label()); }
    @Override public Node idealize() { return null; }

    @Override public TypeMem compute() {
        TypeMem xmem = StopCUNode.priorEscapes(_type);
        for( CallNode call : CodeGen.CODE._calls )
            if( !call.isDead() ) {
                xmem = StopCUNode.publicBottom(xmem.escapes(call.exposed(this)),xmem);
                // FULL memory normalizes to public bits. Preserve precise
                // private arguments independently of that normalization.
                if( call._type==Type.CONTROL && call.exposesArguments(this) )
                    for( int i = 0; i<call.nargs(); i++ )
                        xmem = StopCUNode.publicBottom(xmem.escapes(addDepForwards(call.arg(i+2))._type),xmem);
            }
        for( FunNode fun : CodeGen.CODE._linker )
            if( fun!=null && fun.summary()!=null && !fun.summary().isDead() ) {
                xmem = StopCUNode.publicBottom(xmem.escapes(fun.summary().exposed(this)),xmem);
                // A reachable unloaded body can call these targets, including
                // private initializers. Its arguments are unavailable here.
                if( fun._type==Type.CONTROL )
                    for( int fidx : fun.summary()._classDependencyFIDXs )
                        if( fidx!=0 ) xmem = xmem.makeFrom(XInt.meet(xmem._escFs,XInt.make(fidx)),xmem._escAs);
            }
        return xmem;
    }
}
