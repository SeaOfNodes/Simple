package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;
import java.util.*;

// Track class initializers - the code just after this point expects the named
// class to be initialized.  If this code path dies, then the dependency dies
// also - dead code does not force class init.
//
public class CLInitNode extends CFGNode {
    public Parser.Lexer _loc;          // For error reporting

    // For field references, the ptr might be the dst clz ptr or not.
    public CLInitNode(Parser.Lexer loc, Node ctrl, Node ptr) {
        super(ctrl,ptr);
        _loc = loc;
    }

    CLInitNode(BAOS bais) { super(null,null); }
    @Override public Tag serialTag() { return Tag.CLInit; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node, Integer> anodes ) {
    }

    public CLInitNode(CLInitNode init) {
        super(init);
        _loc = init._loc;
    }

    Node ptr() { return in(1); }
    @Override public String label() { return "CLInit"; }

    // Best effort dst compunit, based on ptr - or null
    public CompUnit cu() {
        if( !(ptr()._type instanceof TypeMemPtr ptr && Parser.startsClzPrefix(ptr._obj._name)) )
            return null;
        // Convert "class:sys.ary" into "sys/ary" and lookup CompUnit
        String fname = ptr._obj._name.substring(6).replace(".","/");
        return CodeGen.CODE._compunits.get(fname);
    }

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        FunNode fun = fun();
        CompUnit cu = cu();
        return p.p("CLInit ").p(fun._name).p(" -> ").p(cu==null ? "---" : cu._cname);
    }

    // Block head is Start, Region, CProj, but not e.g. If, Return, Stop
    public boolean blockHead() { return true; }

    @Override public Type compute() { return in(0)._type; }
    @Override public Node idealize() {
        // A receiver discovered to be an ordinary instance needs no class init.
        if( ptr()._type instanceof TypeMemPtr ptr && ptr._obj!=TypeStruct.BOT &&
            !ptr._obj.isHigh() && !Parser.startsClzPrefix(ptr._obj._name) )
            return in(0);       // Normal instance, no class init
        if( ptr()._type == Type.NIL || ptr()._type.isHigh() )
            return in(0);
        return null;
    }
}
