package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphAdapter;
import com.seaofnodes.graph.GraphSnapshot.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeMem;

/** Chapter 25's view of the IR, including partially constructed parse-time graphs. */
public class SimpleGraphAdapter extends GraphAdapter<Node> {

    @Override protected boolean show(Node n) {
        if( n != CodeGen.CODE.ZERO && n != CodeGen.CODE.XCTRL ) return true;
        for( int i=0; i<n.nOuts(); i++ )
            if( n.out(i) != null ) return true;
        return false; // The parser's keep is not a graph use.
    }

    @Override protected String edgeName(Node n, int i) {
        return n instanceof ScopeNode scope && i < scope._vars.size()
                ? scope.var(i)._name : null;
    }
    @Override protected int edgeJump(Node n, int i) {
        return n instanceof CallEndNode && i>0 && n.in(i) instanceof ReturnNode ret
                ? ref(ret.fun()) : 0;
    }
    @Override protected int projectionIndex(Node n) { return n instanceof Proj p ? p.idx() : -1; }

    @Override protected boolean folding(Node n) {
        if( n instanceof FunNode fun ) return fun.folding();
        if( n instanceof ReturnNode ret ) return ret.fun().folding();
        if( n instanceof CallEndNode cend ) return cend.folding();
        if( n instanceof CallNode )
            for( int i=0; i<n.nOuts(); i++ )
                if( n.out(i) instanceof CallEndNode cend && cend.folding() ) return true;
        return false;
    }

    @Override protected Kind kind(Node n) {
        if( n instanceof StopNode ) return Kind.STOP;
        if( n instanceof ScopeNode ) return Kind.SCOPE;
        if( n instanceof StartCUNode ) return Kind.UNIT;
        if( n instanceof FunNode ) return Kind.FUN;
        // Start inherits Loop for whole-program analysis, but is not a source loop.
        if( n instanceof StartNode ) return Kind.START;
        if( n instanceof LoopNode ) return Kind.LOOP;
        if( n instanceof RegionNode ) return Kind.REGION;
        if( n instanceof PhiNode ) return Kind.PHI;
        if( n instanceof CFGNode ) return Kind.CTRL;
        return isMem(n) ? Kind.MEM : Kind.DATA;
    }

    @Override protected Role role(Node n, int i) {
        if( n instanceof ScopeNode || n instanceof ConstantNode || n instanceof FunPtrNode ||
            n instanceof CtrlNode || n instanceof XCtrlNode )
            return Role.ASSOC;
        if( n instanceof PhiNode )
            return i == 0 ? Role.ASSOC : isMem(n) ? Role.MEM : Role.DATA;
        if( n instanceof Proj )
            return n instanceof CFGNode ? Role.CTRL : isMem(n) ? Role.MEM : Role.DATA;
        // StartCU inherits Region, but slot 0 really connects it to Start.
        if( n instanceof StartCUNode && i == 0 ) return Role.CTRL;
        if( n instanceof RegionNode && i == 0 ) return Role.ASSOC;
        // Preserve known slot roles even when an input is not attached/typed yet.
        if( i == 0 || n instanceof RegionNode || n instanceof StopNode ) return Role.CTRL;
        if( n instanceof MemMergeNode ||
            (i == 1 && (n instanceof MemOpNode || n instanceof ReturnNode || n instanceof CallNode)) ||
            (n instanceof EscapeNode && i >= 2) ) return Role.MEM;
        Node def = n.in(i);
        if( def instanceof CFGNode ) return Role.CTRL;
        if( def != null && isMem(def) ) return Role.MEM;
        return Role.DATA;
    }

    private boolean isMem(Node n) {
        // These Phi subclasses are memory even before their first type computation.
        return n instanceof MemPhiNode || n instanceof BulkMemPhiNode || n.isMem() || n._type instanceof TypeMem;
    }
}
