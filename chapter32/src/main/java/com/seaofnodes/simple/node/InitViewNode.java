package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;

/** A program-point view of self. Memory proves which slots may be read;
 * their values still come from ordinary Loads. Erased after TypeCheck. */
public class InitViewNode extends TypeNode {
    public InitViewNode(TypeMemPtr declared, Node self, Node mem) { super(declared,null,self,mem); }
    @Override public String label() { return "InitView"; }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) { return p.p("initView(").n(in(1)).p(")"); }
    @Override public Type compute() {
        Type ptr = in(1)._type, mem = in(2)._type;
        if( ptr.isHigh() || mem.isHigh() ) return Type.TOP;
        if( !(ptr instanceof TypeMemPtr p) ) return ptr;
        TypeStruct state = mem instanceof TypeMem m && m._one && m._t instanceof TypeStruct ts
            ? ts : p._obj.makeInit();
        // An earlier alias can have fewer initialized slots. The declaration
        // supplies field types; this program point's memory supplies the proof.
        return p.makeFrom(((TypeMemPtr)_con)._obj).withPartial(true).initialized(state);
    }
    @Override public Node idealize() {
        // Closed declarations and settled memory must prove this view redundant.
        // Keep a structural partial restriction: a Parm's current partial type
        // alone is not enough, since inlining can substitute a complete caller.
        return CodeGen.CODE._phase.ordinal()>=CodeGen.Phase.Opto.ordinal() &&
            _type==in(1)._type && ConstructNode.partialView(in(1)) ? in(1) : null;
    }
}
