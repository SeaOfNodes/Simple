package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import java.util.*;

// Track class initializers - the code just after this point expects the named
// class to be initialized.  If this code path dies, then the dependency dies
// also - dead code does not force class init.
//
public class CLInitNode extends CFGNode implements MultiNode {
    public Parser.Lexer _loc;          // For error reporting

    // Inputs: control, whole memory, class pointer, loaded $init flag
    // Results: control, whole memory, known-true flag alias memory.
    // For field references, the ptr might be the dst clz ptr or not.
    public CLInitNode(Parser.Lexer loc, Node ctrl, Node mem, Node ptr, Node ready) {
        super(ctrl,mem,ptr,ready);
        _loc = loc;
    }

    CLInitNode(BAOS bais, String[] strs) { super(null,null,null,null); _loc=Parser.Lexer.unpack(bais,strs); }
    @Override public Tag serialTag() { return Tag.CLInit; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node, Integer> anodes ) {
        Parser.Lexer.packed(_loc,baos,strs);
    }
    @Override public void gather(HashMap<String,Integer> strs) { Parser.Lexer.gather(_loc,strs); }

    public CLInitNode(CLInitNode init) {
        super(init);
        _loc = init._loc;
    }

    public Node mem() { return in(1); }
    public Node ptr() { return in(2); }
    public Node ready() { return in(3); }

    // The field/alias can be unresolved until forward references are loaded.
    public Field flag() {
        return ptr()._type instanceof TypeMemPtr tmp && Parser.startsClzPrefix(tmp._obj._name)
            ? tmp._obj.field("$init") : null;
    }
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

    private boolean instance() {
        return ptr()._type==Type.NIL ||
            (ptr()._type instanceof TypeMemPtr tmp && tmp._obj!=TypeStruct.BOT &&
             !tmp._obj.isHigh() && !tmp._obj._open && !tmp._obj._fref &&
             !Parser.startsClzPrefix(tmp._obj._name));
    }

    public boolean bypass() { return instance() || ready()._type==TypeInteger.TRUE; }

    @Override public Type compute() {
        if( in(0)._type.isHigh() || ptr()._type.isHigh() )
            return TypeTuple.make(Type.XCONTROL,TypeMem.TOP,TypeMem.TOP);
        if( instance() )
            return TypeTuple.make(in(0)._type,mem()._type,mem()._type);
        Field flag = flag();
        Type slice = flag==null ? TypeMem.BOT : TypeMem.make(flag._alias,TypeInteger.TRUE);
        // The initializer may write arbitrary public memory. Only its flag
        // has a guaranteed value on the normal continuation.
        Type bulk = ready()._type.isHigh() ? TypeMem.TOP :
            ready()._type==TypeInteger.TRUE ? mem()._type : TypeMem.BOT;
        return TypeTuple.make(in(0)._type,bulk,slice);
    }

    @Override public Node pcopy(int idx, Node dep) {
        dep.addDep(ptr());
        dep.addDep(ready());
        if( instance() ) return idx==0 ? in(0) : mem();
        if( ready()._type!=TypeInteger.TRUE ) return null;
        if( idx==0 ) return in(0);
        if( idx==1 ) return mem();
        // Already established on the incoming path; this is a memory fact,
        // not a new store to the class object.
        return CodeGen.CODE.con(((TypeTuple)_type)._types[2]);
    }

    @Override public Node idealize() { return null; }

    // Resolve a precise flag slice even when the parser did not yet know the
    // class/alias and could only install the whole-memory projection.
    static Node slice(Node mem, int alias, Node dep) {
        if( !(mem instanceof ProjNode proj && proj._idx==1 && proj.in(0) instanceof CLInitNode init) )
            return mem;
        dep.addDep(init.ptr());
        Field flag = init.flag();
        if( flag==null || flag._alias!=alias ) return mem;
        return new ProjNode(init,2,Parser.memName(alias)).peephole();
    }
}
