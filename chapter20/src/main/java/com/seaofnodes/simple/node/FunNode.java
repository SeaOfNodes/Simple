package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import java.util.BitSet;
import static com.seaofnodes.simple.codegen.CodeGen.CODE;

public class FunNode extends RegionNode {

    // When set true, this Call/CallEnd/Fun/Return is being trivially inlined
    boolean _folding;

    private TypeFunPtr _sig;    // Initial signature
    private ReturnNode _ret;    // Return pointer

    public String _name;        // Debug name

    public FunNode( Parser.Lexer loc, TypeFunPtr sig, Node... nodes ) { super(loc,nodes); _sig = sig; }
    public FunNode( FunNode fun ) { super( fun, fun._loc ); _sig = fun.sig(); _name = fun._name; }

    @Override
    public String label() { return _name == null ? "$fun"+_sig.fidx() : _name; }

    // Find the one CFG user from Fun.  It's not always the Return, but always
    // the Return *is* a CFG user of Fun.
    @Override public CFGNode uctrl() {
        for( Node n : _outputs )
            if( n instanceof CFGNode cfg &&
                (cfg instanceof RegionNode || cfg.cfg0()==this) )
                return cfg;
        return null;
    }

    public ParmNode rpc() {
        ParmNode rpc = null;
        for( Node n : _outputs )
            if( n instanceof ParmNode parm && parm._idx==0 )
                { assert rpc==null; rpc=parm; }
        return rpc;
    }

    // Cannot create the Return and Fun at the same time; one has to be first.
    // So setting the return requires a second step.
    public void setRet(ReturnNode ret) { _ret=ret; }
    ReturnNode ret() { assert _ret!=null; return _ret; }

    // Signature can improve over time
    public TypeFunPtr sig() { return _sig; }
    void setSig( TypeFunPtr sig ) {
        assert sig.isa(_sig);
        if( _sig != sig ) {
            CODE.add(this);
            _sig = sig;
        }
    }

    @Override
    public Type compute() {
        // Only dead if no callers after SCCP
        return Type.CONTROL;
    }

    @Override
    public Node idealize() {

        // Some linked path dies
        Node progress = deadPath();
        if( progress!=null ) {
            if( nIns()==3 && in(2) instanceof CallNode call )
                CODE.add(call.cend()); // If Start and one call, check for inline
            return progress;
        }

        // When can we assume no callers?  Or no other callers (except main)?
        // In a partial compilation, we assume Start gets access to any/all
        // top-level public structures and recursively what they point to.
        // This in turn is valid arguments to every callable function.
        //
        // In a total compilation, we can start from Start and keep things
        // more contained.

        // If no default/unknown caller, use the normal RegionNode ideal rules
        // to collapse
        if( unknownCallers() ) return null;

        // If down to a single input, become that input
        if( nIns()==2 && !hasPhi() ) {
            CODE.add( CODE._stop ); // Stop will remove dead path
            CODE.add( _ret );       // Return will compute to TOP control
            return in(1); // Collapse if no Phis; 1-input Phis will collapse on their own
        }

        return null;
    }

    // Bypass Region idom, always assume depth == 1, one more than Start
    @Override public int idepth() { return (_idepth=1); }
    // Bypass Region idom, always assume idom is Start
    @Override public CFGNode idom(Node dep) { return cfg(1); }

    // Always in-progress until we run out of unknown callers
    public boolean unknownCallers() { return in(1) instanceof StartNode; }

    @Override public boolean inProgress() { return unknownCallers(); }

    // Add a new function exit point.
    public void addReturn(Node ctrl, Node mem, Node rez) {  _ret.addReturn(ctrl,mem,rez);  }

    // Build the function body
    public BitSet body() {

        // Reverse up (stop to start) CFG only, collect bitmap.
        BitSet cfgs = new BitSet();
        cfgs.set(_nid);
        walkUp(ret(),cfgs );

        // Top down (start to stop) all flavors.  CFG limit to bitmap.
        // If data use bottoms out in wrong CFG, returns false - but tries all outputs.
        // If any output hits an in-CFG use (e.g. phi), then keep node.
        BitSet body = new BitSet();
        walkDown(this, cfgs, body, new BitSet());
        return body;
    }

    private static void walkUp(CFGNode n, BitSet cfgs) {
        if( cfgs.get(n._nid) ) return;
        cfgs.set(n._nid);
        if( n instanceof RegionNode r )
            for( int i=1; i<n.nIns(); i++ )
                walkUp(n.cfg(i),cfgs);
        else walkUp(n.cfg0(),cfgs);
    }

    private static boolean walkDown( Node n, BitSet cfgs, BitSet body, BitSet visit ) {
        if( visit.get(n._nid) ) return body.get(n._nid);
        visit.set(n._nid);

        if( n instanceof CFGNode && !cfgs.get(n._nid) )
            return false;
        if( n.in(0)!=null && !cfgs.get(n.in(0)._nid) )
            return false;
        boolean in = n.in(0)!=null || n instanceof CFGNode;
        for( Node use : n._outputs )
            in |= walkDown(use,cfgs,body,visit);
        if( in ) body.set(n._nid);
        return in;
    }
}
