package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.codegen.Iter;
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
    // Results: control, whole memory, known-true flag alias memory, call control.
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
            return TypeTuple.make(Type.XCONTROL,TypeMem.TOP,TypeMem.TOP,Type.XCONTROL);
        if( instance() )
            return TypeTuple.make(in(0)._type,mem()._type,mem()._type,Type.XCONTROL);
        Field flag = flag();
        Type slice = flag==null ? TypeMem.BOT : TypeMem.make(flag._alias,TypeInteger.TRUE);
        // The initializer may write arbitrary public memory. Only its flag
        // has a guaranteed value on the normal continuation.
        Type bulk = ready()._type.isHigh() ? TypeMem.TOP :
            ready()._type==TypeInteger.TRUE ? mem()._type : TypeMem.BOT;
        Type call = ready()._type.isHigh() || ready()._type==TypeInteger.TRUE ? Type.XCONTROL : in(0)._type;
        return TypeTuple.make(in(0)._type,bulk,slice,call);
    }

    @Override public Node pcopy(int idx, Node dep) {
        if( idx==3 ) return null; // Analysis call edge, removed before scheduling.
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
        if( CodeGen.CODE._phase.ordinal()<CodeGen.Phase.Opto.ordinal() ) return null;
        if( CodeGen.CODE._phase.ordinal()<CodeGen.Phase.Lower.ordinal() ) return link() ? this : null;
        if( CodeGen.CODE._phase!=CodeGen.Phase.Lower || bypass() || in(0)._type.isHigh() ) return null;
        if( CodeGen.CODE._midAssert ) return this;
        // Until entry arguments become arrays, ordinary initializers receive zero.
        return expand(CodeGen.CODE.con(TypeInteger.ZERO));
    }

    // Before lowering, expose the conditional call to interprocedural analysis.
    // No Return edge is needed: CLInit already conservatively models its effects.
    public boolean link() {
        if( in(0)._type.isHigh() || ptr()._type.isHigh() || ready()._type.isHigh() || bypass() ) return false;
        FunNode target = CodeGen.CODE.link(cu());
        if( target==null ) return false; // Forward class reference during parsing.
        assert !target.isDead() : "dead initializer "+target._name;
        CProjNode call = cproj(3);
        if( call!=null && target._inputs.find(call)!=-1 ) return false;
        if( CodeGen.CODE._midAssert ) return true;
        if( call==null ) call = CodeGen.CODE.add(new CProjNode(this,3,"InitCall").init());
        CodeGen.CODE.keepInCompUnit(target);
        CodeGen.CODE.add(target).addDef(call);
        for( Node use : target._outputs )
            if( use instanceof ParmNode parm ) {
                Node arg = switch( parm._idx ) {
                case 0 -> CodeGen.CODE.con(TypeRPC.BOT);
                case 1 -> mem();
                case 2 -> ptr();
                case 3 -> CodeGen.CODE.con(TypeInteger.ZERO);
                default -> throw new IllegalStateException("initializer parameter");
                };
                CodeGen.CODE.add(parm).addDef(arg);
            }
        if( CodeGen.CODE._phase.ordinal()>CodeGen.Phase.Opto.ordinal() ) {
            boolean repair = !target.compute().isa(target._type);
            for( Node use : target._outputs )
                if( use instanceof ParmNode && !use.compute().isa(use._type) ) repair = true;
            if( repair ) Iter.repair(CodeGen.CODE);
        }
        return true;
    }

    public void unlink() {
        CProjNode call = cproj(3);
        if( call==null ) return;
        keep();
        for( Node use : call._outputs.asAry() )
            if( use instanceof FunNode target ) {
                CodeGen.CODE.add(target);
                target.removeDeadPath(target._inputs.find(call));
            }
        call.isKill();
        unkeep();
    }

    // Called only after cycle/type checking. The initialized arm skips the
    // call; both arms rejoin with whole memory and a known-true flag slice.
    private Node expand(Node arg) {
        FunNode target = CodeGen.CODE.link(cu());
        assert target!=null && !target.isDead();
        keep();
        Node ctrl = in(0);
        Node memory = mem();

        // The callee establishes its parent before setting its own flag.
        IfNode iff = CodeGen.CODE.add(new IfNode(ctrl,ready()).init());
        Node done = CodeGen.CODE.add(new CProjNode(iff,0,"True").init());
        Node cold = CodeGen.CODE.add(new CProjNode(iff,1,"False").init());

        Node fptr = new FunPtrNode(target.sig(),CodeGen.CODE._start,target.ret()).peephole();
        CallNode call = CodeGen.CODE.add(new CallNode(_loc,cold,memory,ptr(),arg,fptr).init());
        CallEndNode end = new CallEndNode(call,TypeRPC.constant(CodeGen.CODE.rpc(_loc._cu._cname)));
        CodeGen.CODE.add(end.init());
        // Replace the analysis edge with the ordinary call. Its arguments
        // are the same ones already analyzed before lowering.
        call.link(target);
        unlink();
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
