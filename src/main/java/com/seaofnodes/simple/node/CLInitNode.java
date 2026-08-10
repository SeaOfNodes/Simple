package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.type.*;
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

    @Override public Node idealize() {
        if( CodeGen.CODE._phase!=CodeGen.Phase.Lower || bypass() || in(0)._type.isHigh() ) return null;
        if( CodeGen.CODE._midAssert ) return this;
        // Until entry arguments become arrays, ordinary initializers receive zero.
        return expand(CodeGen.CODE.con(TypeInteger.ZERO));
    }

    // Called only after cycle/type checking. The initialized arm skips the
    // call; both arms rejoin with whole memory and a known-true flag slice.
    private Node expand(Node arg) {
        FunNode target = CodeGen.CODE.link(cu());
        assert target!=null && !target.isDead();
        keep();
        Node ctrl = in(0);
        Node memory = mem();

        // A child body may assume its parent has finished. Establish that
        // requirement first, then test the child's flag in the resulting memory.
        CompUnit parent = cu()._par;
        if( parent!=null ) {
            Node ptr = CodeGen.CODE.con(TypeMemPtr.make((byte)2,parent._clz,true));
            Field flag = parent._clz.field("$init");
            Node off = CodeGen.CODE.con(parent._clz.offset(parent._clz.find("$init")));
            Node parentReady = CodeGen.CODE.add(new LoadNode(_loc,"$init",flag._alias,ctrl,memory,ptr,off).init());
            CLInitNode init = CodeGen.CODE.add(new CLInitNode(_loc,ctrl,memory,ptr,parentReady).init());
            ctrl = CodeGen.CODE.add(new CProjNode(init,0,ScopeNode.CTRL).init());
            memory = CodeGen.CODE.add(new ProjNode(init,1,ScopeNode.MEM0).init());
        }

        Node childOff = CodeGen.CODE.con(cu()._clz.offset(cu()._clz.find("$init")));
        // Read the child's flag after establishing its parent's memory effects.
        Node ready = CodeGen.CODE.add(new LoadNode(_loc,"$init",flag()._alias,ctrl,memory,ptr(),childOff).init());

        IfNode iff = CodeGen.CODE.add(new IfNode(ctrl,ready).init());
        Node done = CodeGen.CODE.add(new CProjNode(iff,0,"True").init());
        Node cold = CodeGen.CODE.add(new CProjNode(iff,1,"False").init());

        Node fptr = new FunPtrNode(target.sig(),CodeGen.CODE._start,target.ret()).peephole();
        CallNode call = CodeGen.CODE.add(new CallNode(_loc,cold,memory,ptr(),arg,fptr).init());
        CallEndNode end = new CallEndNode(call,TypeRPC.constant(CodeGen.CODE.rpc(_loc._cu._cname)));
        // This is a lowered call, after interprocedural analysis. Do not add
        // new parameter flows to a body already optimized by SCCP.
        CodeGen.CODE.add(end.init());
        Node called = CodeGen.CODE.add(new CProjNode(end,0,ScopeNode.CTRL).init());
        Node after = CodeGen.CODE.add(new ProjNode(end,1,ScopeNode.MEM0).init());
        RegionNode region = CodeGen.CODE.add(new RegionNode(_loc,null,done,called).init());
        Node merged = CodeGen.CODE.add(new BulkMemPhiNode(ScopeNode.MEM0,region,memory,after).init());

        // Replace the individual outputs; no tuple-pack node or second
        // representation of CLInit is needed while the projections collapse.
        for( Node use : _outputs.asAry() ) {
            if( use==null ) continue; // Our temporary keep.
            assert use instanceof Proj;
            int idx = ((Proj)use).idx();
            use.subsume(idx==0 ? region : idx==1 ? merged : CodeGen.CODE.con(((TypeTuple)_type)._types[2]));
        }
        CodeGen.CODE.invalidateIDepthCaches();
        return unkeep(); // Iter removes this now-unused node.
    }

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
