package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import com.seaofnodes.simple.type.TypeTuple;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import static com.seaofnodes.simple.codegen.CodeGen.CODE;

public class FunNode extends RegionNode {

    // When set true, this Call/CallEnd/Fun/Return is being trivially inlined
    boolean _folding;

    private TypeFunPtr _sig;    // Initial signature
    private ReturnNode _ret;    // Return pointer

    public String _name;        // Debug name

    public int _approxUIDs;     // Approximate function size, used as a inlining heuristic

    public FunNode( Parser.Lexer loc, TypeFunPtr sig, String name, Node... nodes ) { super(loc,nodes); _name=name; _sig = sig; }
    public FunNode( FunNode fun ) {
        super( fun, fun==null ? null : fun._loc );
        if( fun!=null ) {
            _sig = fun.sig();
            _name = fun._name;
        } else {
            _sig = TypeFunPtr.BOT;
            _name = "";
        }
    }
    @Override public Tag serialTag() { return Tag.Fun; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        assert !_folding;
        baos.packed1(nIns());          // Number of linked calls
        baos.packed2(types.get(_sig)); // NPE if fails lookup
        baos.packed2(_name==null ? 0 : strs.get(_name));
    }
    static Node make( BAOS bais, String[] strs, Type[] types)  {
        Node[] ins = new Node[bais.packed1()];
        TypeFunPtr sig = (TypeFunPtr)types[bais.packed2()];
        String name = strs[bais.packed2()];
        return new FunNode(null,sig,name,ins);
    }

    @Override public String label() { return _name == null ? "$fun"+_sig.fidx() : _name; }

    // Find the one CFG user from Fun.  It's not always the Return, but always
    // the Return *is* a CFG user of Fun.
    @Override public CFGNode uctrl() {
        for( Node n : _outputs )
            if( n instanceof CFGNode cfg &&
                (cfg instanceof RegionNode || cfg.cfg0()==this) )
                return cfg;
        return null;
    }

    public ParmNode parm(int idx) {
        ParmNode pidx = null;
        for( Node n : _outputs )
            if( n instanceof ParmNode parm && parm._idx==idx )
                { assert pidx==null; pidx=parm; }
        return pidx;
    }
    public ParmNode rpc() { return parm(0); }

    // Cannot create the Return and Fun at the same time; one has to be first.
    // So setting the return requires a second step.
    public void setRet(ReturnNode ret) { _ret=ret; }
    public ReturnNode ret() { assert _ret!=null; return _ret; }

    // Signature can improve over time
    public TypeFunPtr sig() { return _sig; }
    public void setSig( TypeFunPtr sig ) {
        assert sig.isa(_sig);
        if( _sig != sig ) {
            CODE.add(this);
            _sig = sig;
            unlock();
        }
    }

    @Override boolean _upgradeType( HashMap<String,Type> TYPES) {
        Type old = _sig; _sig = (TypeFunPtr)_sig.upgradeType(TYPES);  return old != _sig;
    }

    public void setName( String name ) {
        if( _name==null ) _name=name;
        else _name += "."+name;
    }

    @Override
    public Type compute() {
        // Only dead if no callers after SCCP
        if( unknownCallers() )
            return Type.CONTROL;
        Type t = Type.XCONTROL;
        for (int i = 1; i < nIns(); i++) {
            // Since no unknown callers, we are not main and the Start input
            // will be a Tuple with XControl, so ignore it.  Need to be called
            // from someplace other than Start
            if( !(in(i) instanceof StartNode) )
                t = t.meet(in(i)._type);
        }
        return t;
    }

    @Override
    public Node idealize() {

        // Some linked path dies, except main never kills Start
        Node progress = deadPath(unknownCallers());
        if( progress!=null ) {
            if( nIns()==2 && in(1) instanceof CallNode call )
                CODE.add(call.cend()); // If no Start and one call, check for inline
            return progress;
        }

        // Upgrade inferred or user-written return type to actual
        if( _ret!=null && _ret._type instanceof TypeTuple tt && tt.ret() != _sig.ret() ) {
            setSig(_sig.makeFrom(tt.ret()));
            return this;
        }

        // Attempt to get rid of the unknown caller.
        // - Must be past Parser, which invites new calls "from whole cloth".
        // - Must have a private name
        // - Function pointer constant cannot have escape
        if( CODE._phase.ordinal() > CodeGen.Phase.Parse.ordinal() && in(1) instanceof StartNode ) {
            if( _name==null || _name.contains( "._" ) ) {
                // TODO: REALLY EXPENSIVE WAY TO FIND USES
                Node con = new ConstantNode(_sig).peephole();
                boolean escapes = false;
                for( Node use : con._outputs )
                    throw Utils.TODO();
                // If the function pointer does not escape, remove the unknown caller
                if( !escapes )
                    return removeDeadPath(1);
            }
        }


        // If no default/unknown caller, use the normal RegionNode ideal rules
        // to collapse
        if( unknownCallers() ) return null;

        // If folding (already inlined) down to a single input, become that input.
        // Cannot collapse if not-inlining, and might not inline if call site
        // calls multiple targets.
        if( _folding && nIns()==2 && !hasPhi() ) {
            CODE.add( CODE._stop ); // Stop will remove dead path
            CODE.add( _ret );       // Return will compute to TOP control
            return in(1); // Collapse if no Phis; 1-input Phis will collapse on their own
        }

        return null;
    }

    // Bypass Region idom, always assume depth == 1, one more than Start,
    // unless folding then just a ID on input#1
    @Override public int idepth() {
        return _folding ? super.idepth() : CodeGen.CODE.iDepthAt(1);
    }
    // Bypass Region idom, always assume idom is Start
    @Override public CFGNode idom(Node dep) { return _folding && nIns()==3 ? cfg(2) : (nIns()>1 ? cfg(1) : null); }

    // Always in-progress until we run out of unknown callers
    public boolean unknownCallers() { return nIns()>=2 && in(1) instanceof StartNode; }

    public boolean isModInit( ) {
        // The one top-level <clinit> with no internal dots:
        // "sys.<clinit>" is a module, but "sys.io.<clinit>" is not.
        return isClz() && _name.indexOf('.')==_name.lastIndexOf('.');
    }
    public boolean isInit( ) { return isInit(_name); }
    public boolean isClz ( ) { return _name!=null && _name.endsWith(".<clinit>"); }
    public static boolean isInit(String name ) { return name!=null && name.endsWith("init>"); }
    public static boolean isInstance(String name ) { return name!=null && name.endsWith(".<init>"); }

    // Function is public (callable from Start directly).
    public boolean isPublic( CodeGen code ) {
        // Never true for anonymous functions
        if( _name == null ) return false;
        // False if name starts with underscore, skipping any leading struct names.
        int idx = _name.lastIndexOf('.')+1;
        if( _name.charAt(idx)=='_' ) return false;
        return true;
    }

    @Override public boolean inProgress() { return unknownCallers(); }

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

    // Clone function body. Give function a new FIDX.
    FunNode copyBody() {
        // Clone the function body
        BitSet body = body();
        assert body.cardinality() < 100;
        // Walk the body, cloning
        IdentityHashMap<Node,Node> map = new IdentityHashMap<>();
        BitSet visit = CodeGen.CODE.visit();
        bodyCopy( visit, body, map, this );
        visit.clear();
        bodyEdge( visit, body, map, this );
        visit.clear();

        // Remove all callers - this is a private copy
        FunNode fun2 = (FunNode)map.get(this);
        while( fun2.nIns() > 1 )
            fun2.removeDeadPath(1);
        // New function/return cross-link each other
        ReturnNode ret2 = (ReturnNode)map.get(_ret);
        fun2._ret = ret2;
        ret2._fun = fun2;

        // Flip to a new FIDX to avoid confusion with the old one
        TypeFunPtr sig2 = _sig.makeFrom(CodeGen.CODE.nextFIDX());
        fun2._sig = sig2;

        return fun2;
    }

    // Clone small function
    private void bodyCopy( BitSet visit, BitSet body, IdentityHashMap<Node,Node> map, Node n ) {
        if( !body.get(n._nid) ) return; // Not part of the body
        if( visit.get(n._nid) ) return; // Been there, done that
        visit.set(n._nid);
        Node m = n.copy();
        CodeGen.CODE.add(m);
        map.put(n,m);
        m._type = n._type;
        for( Node x : n._outputs )
            bodyCopy(visit,body,map,x);
    }
    private void bodyEdge( BitSet visit, BitSet body, IdentityHashMap<Node,Node> map, Node n ) {
        if( !body.get(n._nid) ) return; // Not part of the body
        if( visit.get(n._nid) ) return; // Been there, done that
        visit.set(n._nid);
        Node m = map.get(n);
        for( Node e : n._inputs ) {
            Node x = e;
            if( e != null ) {
                x = map.get(e);
                if( x == null ) x = e;
            }
            m.addDef(x);
        }
        for( Node x : n._outputs )
            bodyEdge(visit,body,map,x);
    }


    // FunNodes must match signature (equivalent: no 2 FunNodes are ever GVN'able)
    @Override public boolean eq( Node n ) {
        return _sig == ((FunNode)n)._sig;
    }

    @Override public int hash() {
        return _sig.hashCode();
    }

    // ------------
    // MachNode specifics, shared across all CPUs

    // Function may call other functions?  X86 requires 16b aligned SP on
    // outbound calls, but not leaf.  Set during an unrelated RA scan.
    public boolean _hasCalls;

    // Maximum incoming argument slot, based on function type
    public int _maxArgSlot;

    // Frame adjust, including padding, set during early Encoding
    public int _frameAdjust;

    public String op() { return _frameAdjust==0 ? "entry" : "subi"; }
    public void postSelect(CodeGen code) {
        code.link(this);
        // Max slot seen past space reserved for incoming arguments
        _maxArgSlot = code._mach.maxArgSlot(sig());
    }
    public RegMask regmap(int i) { return null; }
    public RegMask outregmap() { return null; }
    public void encoding( Encoding enc ) { throw Utils.TODO();  }
    public void asm(CodeGen code, SB sb) {
        if( _frameAdjust!=0 )
            sb.p("rsp -= #").p(_frameAdjust);
    }

    public void computeFrameAdjust(CodeGen code, int maxReg) {
        // Max slot seen, or 0.
        int maxSlot = Math.max(maxReg - code._mach.regs().length,_maxArgSlot);
        // Frame adjust before rounding, can be negative for no spills
        int size = maxSlot - _maxArgSlot;
        _frameAdjust = size*8;
    }

    // Given a high register number and during encoding compute the stack
    // pointer offset.  Pre-RA we lack the fun._frameAdjust.  We also need a
    // FunNode.
    public int computeStackOffset(CodeGen code, int reg) {
        String[] regs = code._mach.regs();
        int stackSlot = reg - regs.length;
        int slotRotate = stackSlot < _maxArgSlot
            ? stackSlot + (_frameAdjust>>3)
            : stackSlot - _maxArgSlot;
        return slotRotate*8;
    }

    @Override public void gather( HashMap<String,Integer> strs ) {
        Serialize.gather(strs,_name);
    }
}
