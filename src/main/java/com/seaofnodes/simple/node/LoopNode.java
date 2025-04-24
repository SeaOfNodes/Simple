package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeMem;
import java.util.BitSet;
import java.util.HashSet;

public class LoopNode extends RegionNode {
    public LoopNode( Parser.Lexer loc, Node entry ) { super(loc,null,entry,null); }
    public LoopNode( LoopNode loop ) { super(loop); }

    public CFGNode entry() { return cfg(1); }
    public CFGNode back () { return cfg(2); }

    @Override
    public String label() { return "Loop"; }

    @Override
    public Type compute() {
        if( inProgress() ) return Type.CONTROL;
        return entry()._type;
    }

    // Bypass Region idom, same as the default idom() using use in(1) instead of in(0)
    public int idepth() {
        return CodeGen.CODE.validIDepth(_idepth) ? _idepth : (_idepth=CodeGen.CODE.iDepthFrom(idom().idepth()));
    }
    // Bypass Region idom, same as the default idom() using use in(1) instead of in(0)
    @Override public CFGNode idom(Node dep) { return entry(); }

    // If this is an unreachable loop, it may not have an exit.  If it does not
    // (i.e., infinite loop), force an exit to make it reachable.
    public StopNode forceExit( FunNode fun, StopNode stop ) {
        // Walk the backedge, then immediate dominator tree util we hit this
        // Loop again.  If we ever hit a CProj from an If (as opposed to
        // directly on the If) we found our exit.
        CFGNode x = back();
        while( x != this ) {
            if( x instanceof CProjNode exit && exit.in(0) instanceof IfNode iff ) {
                CFGNode other = iff.cproj(1-exit._idx);
                if( other!=null && other._ltree != _ltree && nested(_ltree,other._ltree) )
                    return stop; // Found an exit, not an infinite loop
            }
            x = x.idom();
        }
        // Found a no-exit loop.  Insert an exit
        NeverNode iff = new NeverNode(back()); // Ideal never-branch
        CProjNode t = new CProjNode(iff,0,"True" ).init();
        CProjNode f = new CProjNode(iff,1,"False").init();
        setDef(2,t);            // True continues loop, False (never) exits loop
        ReturnNode ret = fun.ret();
        iff._ltree = t._ltree = _ltree;
        ret._ltree = f._ltree = stop._ltree;

        // Now fold control into the exit.  Might have 1 valid exit, or an
        // XCtrl or a bunch of prior NeverNode exits.
        Node top = new ConstantNode(Type.TOP).peephole();
        Node memout = new MemMergeNode(false);
        memout.addDef(f); // placeholder for control
        for( Node u : _outputs )
            if( u instanceof PhiNode phi && phi._type.isa(TypeMem.BOT) )
                memout.addDef(phi);

        Node ctrl = ret.ctrl(), mem = ret.mem(), expr = ret.expr();
        if( ctrl!=null && ctrl._type != Type.XCONTROL ) {
            // Perfect aligned exit?
            if( !(ctrl instanceof RegionNode r &&
                  mem  instanceof PhiNode pmem && pmem.region()==r &&
                  expr instanceof PhiNode prez && prez.region()==r ) ) {
                // Nope, insert an aligned exit layer
                RegionNode r = new RegionNode(_loc,null,ctrl).init();
                ctrl = r;  r._ltree = stop._ltree;
                mem  = new PhiNode(r,mem ).init();
                expr = new PhiNode(r,expr).init();
            }
            // Append new Never exit
            ctrl.addDef(f  );
            mem .addDef(memout);
            expr.addDef(top);
        } else {
            ctrl = f;
            mem  = memout;
            expr = top;
        }
        ret.setDef(0,ctrl);
        ret.setDef(1,mem );
        ret.setDef(2,expr);

        return stop;
    }

    private static boolean nested(LoopTree inner, LoopTree outer) {
        for( ; inner!=null; inner = inner._par )
            if( inner == outer )
                return true;
        return false;
    }

}
