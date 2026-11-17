package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.parser.Parser;
import com.seaofnodes.simple.parser.Lexer;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.print.ExprPrinter;

/** The source new boundary. Own fields have been constructed, but an incomplete
 * actual argument keeps this result incomplete. Unlike a callee's merged Return,
 * this transfer describes one allocation site. The partial-only form gives
 * constructor self and ^ formals their restricted body view even after
 * inlining substitutes complete actual arguments. Erased after source checks. */
public class ConstructNode extends Node {
    public final Lexer _loc;
    private final boolean _explicit;
    public final boolean _partial;
    public ConstructNode(Node value, Ary<Node> args) {
        super(null,value,null);
        _loc = null;
        _explicit = false;
        _partial = false;
        for( Node arg : args ) addDef(arg);
    }
    // Inside the allocator: keep the final private memory with the result,
    // so inlining cannot discard the constructor-completion check.
    public ConstructNode(Node value, Node mem, Lexer loc, boolean explicit) {
        super(null,value,mem);
        _loc = loc;
        _explicit = explicit;
        _partial = false;
    }
    // The constructor body sees self and ^ formals through this restricted view.
    public ConstructNode(Node value) {
        super(null,value,null);
        _loc = null;
        _explicit = false;
        _partial = true;
    }
    @Override public String label() { return "Construct"; }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) { return p.p("construct(").n(in(1)).p(")"); }
    @Override public Type compute() {
        Type t = in(1)._type;
        if( in(2)!=null && in(2)._type.isHigh() ) return Type.TOP;
        if( !(t instanceof TypeMemPtr ptr) ) return t;
        boolean partial = _partial;
        for( int i = 3; i<nIns(); i++ ) partial |= TypeMemPtr.mayBePartial(in(i)._type);
        return ptr.withPartial(partial);
    }
    @Override public Node idealize() {
        // Restricting an already restricted view is redundant, including after
        // inlining. A partial Parm type alone can later sharpen to complete.
        if( _partial && partialView(in(1)) ) return in(1);
        // Wait for Opto to install closed field types.  Afterwards, a proven
        // identity with initialized fields is good, and can be removed.
        // Otherwise we're keeping this Node until we error report.
        return !_partial && CodeGen.CODE._phase.ordinal()>=CodeGen.Phase.Opto.ordinal() && _type==in(1)._type && err()==null ? in(1) : null;
    }
    static boolean partialView(Node n) {
        return n instanceof InitViewNode || n instanceof ConstructNode con && con._partial;
    }
    @Override public Parser.ParseException err() {
        if( in(2)==null || in(2)._type.isHigh() || !(in(1)._type instanceof TypeMemPtr ptr) ) return null;
        Type mem = in(2)._type;
        for( Field fld : ptr._obj._fields ) {
            if( fld._extern ) continue;
            Field actual = mem instanceof TypeMem tm && tm._t instanceof TypeStruct ts ? ts.field(fld._fname) : null;
            if( actual==null || actual._t==TypeScalar.BOT || actual._t==Type.BOTTOM )
                return Parser.error("'"+ptr._obj._name+"' is not fully initialized, field '"+fld._fname+
                    (_explicit ? "' is only partially set in the constructor" : "' needs to be set in a constructor"),_loc);
        }
        return null;
    }
    @Override public boolean eq(Node n) { return _explicit==((ConstructNode)n)._explicit && _partial==((ConstructNode)n)._partial; }
    @Override int hash() { return (_explicit ? 1 : 0) | (_partial ? 2 : 0); }
}
