package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import java.util.BitSet;
import java.util.ArrayDeque;

/** A lexical lifetime endpoint. This is a control use, not a machine operand.
 * Kept values may be raw addresses; Allocation follows them back to storage.
 * Retained in .simple and through inlining; erased before scheduling. */
public class KeepAliveNode extends CFGNode {
    public KeepAliveNode(Node ctrl, Node value) { super(ctrl,value); }
    @Override public Tag serialTag() { return Tag.KeepAlive; }
    @Override public Type compute() { return in(0)._type; }
    @Override public Node idealize() {
        Node value=in(1);
        if( value._type==TypeInteger.ZERO || value._type==Type.NIL || in(0)._type==Type.XCONTROL ) return in(0);
        if( in(0) instanceof KeepAliveNode prior && prior.in(1)==value && addDep(prior).nOuts()==1 ) return prior;
        // Pending parser edges count as uses too. Drop an expression only when
        // none of its parts has a use outside the fences.
        if( unused(value) ) return in(0);
        return null;
    }

    private boolean unused(Node value) {
        BitSet seen=new BitSet();
        Ary<Node> nodes=new Ary<>(Node.class);
        ArrayDeque<Node> work=new ArrayDeque<>();
        work.push(value);
        while( !work.isEmpty() ) {
            Node n=work.pop();
            if( n==null || n instanceof ConstantNode || n instanceof CFGNode || seen.get(n._nid) ) continue;
            seen.set(n._nid);
            nodes.add(n);
            // Only inspect the address expression, not the object's fields or
            // the allocation's memory. The pointer is the endpoint of tracing.
            if( n instanceof PtrToIntNode ) continue;
            if( n._type instanceof TypeMemPtr || n.isFunLeaf() ) return false;
            for( Node in : n._inputs ) if( in!=null ) work.push(in);
        }
        for( Node n : nodes ) for( Node use : addDep(n)._outputs )
            if( use==null || !(use instanceof KeepAliveNode) && !seen.get(use._nid) ) return false;
        return true;
    }

    /** Parser-only accumulation of lifetime obligations. A scalar token lets
     * ordinary Phis merge branches and loops; it is never evaluated as a number.
     * Both inputs survive: replacing a variable must not forget its old object. */
    public static class Values extends Node {
        public Values(Node prior, Node value) { super(null,prior,value); }
        @Override public Tag serialTag() { return Tag.KeepValues; }
        @Override public Type compute() { return TypeInteger.BOT; }
        @Override public Node idealize() {
            if( in(1)._type==TypeInteger.ZERO ) return in(2);
            if( in(2)._type==TypeInteger.ZERO || in(1)==in(2) ) return in(1);
            return null;
        }
    }

    public static void erase(StartNode start) {
        Ary<Node> nodes=new Ary<>(Node.class);
        BitSet candidates=new BitSet();
        start.walk(n -> {
            if( n instanceof KeepAliveNode || n instanceof Values ||
                n instanceof PhiNode && !(n instanceof ParmNode) ) {
                nodes.add(n);
                candidates.set(n._nid);
            }
            return null;
        });
        boolean hasFence=false;
        for( Node n : nodes ) if( n instanceof KeepAliveNode || n instanceof Values ) { hasFence=true; break; }
        if( !hasFence ) return;
        // GVN can share a pending Phi with an ordinary program Phi. Names do
        // not distinguish them. Preserve every Phi with a real use, and its
        // inputs; discard only the subgraph used exclusively by lifetime fences.
        BitSet live=new BitSet();
        ArrayDeque<Node> work=new ArrayDeque<>();
        for( Node n : nodes ) if( !(n instanceof KeepAliveNode) )
            for( Node use : n._outputs ) if( use==null || !candidates.get(use._nid) ) {
                work.push(n);
                break;
            }
        while( !work.isEmpty() ) {
            Node n=work.pop();
            if( live.get(n._nid) ) continue;
            live.set(n._nid);
            assert !(n instanceof Values) : "Lifetime token used as a program value";
            for( int i=1; i<n.nIns(); i++ )
                if( n.in(i)!=null && candidates.get(n.in(i)._nid) ) work.push(n.in(i));
        }
        // Loaded Eval2 bodies must not GVN into a different executing graph.
        ConstantNode zero=ConstantNode.raw(TypeInteger.ZERO);
        zero.setDef(0,start);
        zero.keep();
        for( Node n : nodes ) if( !n.isDead() && !live.get(n._nid) )
            n.subsume(n instanceof KeepAliveNode ? n.in(0) : zero);
        zero.unkill();
        CodeGen.CODE.invalidateIDepthCaches();
    }
}
