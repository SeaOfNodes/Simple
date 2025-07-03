package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.print.IRPrinter;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;

/**
 *  CallEnd
 */
public class CallEndNode extends CFGNode implements MultiNode {

    // When set true, this Call/CallEnd/Fun/Return is being trivially inlined
    private boolean _folding;
    public final TypeRPC _rpc;

    public CallEndNode(CallNode call) { super(new Node[]{call}); _rpc = TypeRPC.constant(CodeGen.CODE.getRPC()); }
    public CallEndNode(CallEndNode cend) { super(cend); _rpc = cend._rpc; }
    @Override public Tag serialTag() { return Tag.CallEnd; }
    @Override public String label() { return _folding ? "FOLD_CEND" : "CallEnd"; }
    public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        baos.packed1(nIns());
        // Linked CallEnds depend on Return types which depend on CallEnds;
        // break the cycle
        baos.packed2(types.get(_type));
    }
    static Node make( BAOS bais, Type[] types )  {
        Node cend = new CallEndNode((CallNode)null);
        cend.setDefX(bais.packed1()-1,null);
        cend._type = types[bais.packed2()];
        return cend;
    }

    @Override public boolean blockHead() { return true; }

    public CallNode call() { return (CallNode)in(0); }

    @Override public CFGNode idom(Node dep) {
        // Folding the idom is the one inlining Return
        return _folding ? cfg(1) : super.idom(dep);
    }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("cend( ");
        sb.append( in(0) instanceof CallNode ? "Call, " : "----, ");
        for( int i=1; i<nIns()-1; i++ )
            in(i)._print0(sb,visited).append(",");
        sb.setLength(sb.length()-1);
        return sb.append(")");
    }

    @Override
    public Type compute() {
        if( !(in(0) instanceof CallNode call) )
            return TypeTuple.RET.dual();
        Type ret = Type.BOTTOM;
        if( addDep(call.fptr())._type instanceof TypeFunPtr tfp ) {
            ret = tfp.ret();
            // Here, if I can figure out I've found *all* callers, then I can meet
            // across the linked returns and join with the function return type.
            if( tfp.isConstant() && nIns()>1 ) {
                assert nIns()==2;     // Linked exactly once for a constant
                TypeTuple ttret = (TypeTuple)in(1)._type;
                ret = ret.join(ttret.ret()); // Return type
                return TypeTuple.make(call._type,ttret.mem(),ret);
            }
        }
        return TypeTuple.make(call._type,TypeMem.BOT,ret);
    }

    @Override
    public Node idealize() {

        // Not already inlined; linked exactly once; well-formed
        if( !_folding && nIns()==2 && in(0) instanceof CallNode call ) {
            Node fptr = call.fptr();
            if( fptr instanceof ConstantNode && // We have an immediate call
                // Function is being called, and its not-null
                fptr._type instanceof TypeFunPtr tfp && tfp.notNull() &&
                // Arguments are correct
                call.err()==null ) {

                // Trivial inlining: call site calls a single function; single function
                // is only called by this call site.
                Node progress = trivialInline(call);
                if( progress != null )
                    return progress;

                progress = smallInline(call);
                if( progress != null )
                    return progress;

            } else {
                addDep(fptr);
            }
        }

        return null;
    }

    // -----------------------------
    private Node trivialInline( CallNode call ) {
        Node fptr = call.fptr();
        if( fptr.nOuts() > 1 ) { // Only user is this call
            // Function ptr has multiple users (so maybe multiple call sites)
            addDep(fptr);
            return null;
        }


        ReturnNode ret = (ReturnNode)in(1);
        FunNode fun = ret.fun();
        // Expecting Start, and the Call, and not exported
        if( fun.nIns()!=3 || fun.isExported() ) {
            addDep(fun);
            addDep(fptr);
            return null;
        }
        assert fun.in(1) instanceof StartNode && fun.in(2)==call;

        // Disallow self-recursive inlining (loop unrolling by another name)
        CFGNode idom = call;
        while( !(idom instanceof FunNode) && idom!=null )
            idom = idom.idom();
        if( idom == fun || idom == null ) {
            addDep(fun);
            addDep(fptr);
            return null;
        }

        // Trivial inline: rewrite
        _folding = true;
        // Rewrite Fun so the normal RegionNode ideal collapses
        fun._folding = true;
        fun.setDef(1,Parser.XCTRL); // No default/unknown StartNode caller
        fun.setDef(2,call.ctrl());  // Bypass the Call;
        fun.ret().setDef(3,null);   // Return is folding also
        CodeGen.CODE.addAll(fun._outputs);
        // Inlining immediately blows all cache idepth fields past the inline point.
        // Bump the global version number invalidating them en-masse.
        CodeGen.CODE.invalidateIDepthCaches();
        return this;
    }

    // -----------------------------

    // Better inlining - fold up small (but repeated) loop bodies.
    // Actually, in the name of better testing, always fold up constructors.
    //
    private Node smallInline( CallNode call ) {
        ReturnNode ret = (ReturnNode)in(1);
        FunNode fun = ret.fun();
        assert fun.rpc() instanceof ParmNode parm && parm.cfg0()==fun;
        BitSet visit = new BitSet();
        // No control flow
        if( ret.ctrl() != fun ||
            !isTrivial( visit, fun, ret.mem() ) ||
            !isTrivial( visit, fun, ret.expr() ) )
            { addDep(ret); return null; }

        // Going to inline!

        // Map the function edges to the pre-call info
        IdentityHashMap<Node, Node> map = new IdentityHashMap<>();
        map.put(fun,call.ctrl());
        for( Node use : fun._outputs )
            if( use instanceof ParmNode parm )
                map.put(parm,call.arg(parm._idx));
        fun.setDef(fun._inputs.find(call),Parser.XCTRL);
        CodeGen.CODE.add(fun);

        ReturnNode ret2 = (ReturnNode)cloneTrivial(map, ret);
        ret2._fun=null;
        setDef(1,ret2);
        _folding = call._folding = true;
        return this;
    }

    // For small inlining, most values must be "trivial" - small, well-behaved,
    // limited.  Allow function parms, constants or Load/Stores from "trivial"
    // arguments

    private static boolean isTrivial( Node n ) {
        return switch(n) {
        case MemMergeNode mem -> true;
        case MemOpNode mem -> true;
        case ArithNode arith -> true;
        case CastNode cast -> true;
        case ReadOnlyNode ro -> true;
        default -> false;
        };
    }

    private static boolean isTrivial( BitSet visit, FunNode fun, Node n ) {
        if( visit.get(n._nid) ) return true;
        visit.set(n._nid);
        if( n instanceof ConstantNode )
            return true;
        if( n instanceof ParmNode parm )
            return parm.cfg0()==fun;
        if( isTrivial(n) ) {
            for( Node n2 : n._inputs )
                if( n2 != null && !isTrivial( visit, fun, n2 ) )
                    return false;
            return true;
        }
        return true;
    }

    // Clone the trivial function body
    private Node cloneTrivial( IdentityHashMap<Node, Node> map, Node n ) {
        Node m = map.get(n);
        if( m!=null ) return m;
        // Constants no cloning
        if( n instanceof ConstantNode con ) return con;
        assert n instanceof ReturnNode || isTrivial(n);
        // Clone private copy
        m = n.copy();
        for( int i=0; i<n.nIns(); i++ )
            m.setDefX(i, n.in(i)==null ? null : cloneTrivial(map,n.in(i)));
        return m.peephole();
    }


    // -----------------------------
    // CallEnd is collapsing, and C/ProjNodes fold up
    @Override public Node pcopy(int idx) {
        return _folding ? in(1).in(idx) : null;
    }
}
