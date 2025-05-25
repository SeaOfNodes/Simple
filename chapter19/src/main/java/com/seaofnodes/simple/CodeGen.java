 package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.x86_64_v2.MemOpX86;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;

public class CodeGen {
    // Last created CodeGen as a global; used all over to avoid passing about a
    // "context".
    public static CodeGen CODE;

    public enum Phase {
        Parse,                  // Parse ASCII text into Sea-of-Nodes IR
        Opto,                   // Run ideal optimizations
        TypeCheck,              // Last check for bad programs
        InstructionSelection,   // Convert to target hardware nodes
        Schedule,               // Global schedule (code motion) nodes
        LocalSched,             // Local schedule
        RegAlloc;               // Register allocation
    }
    public enum CallingConv {
        Win64,
        SystemV
    }
    public Phase _phase;

    // ---------------------------
    // Compilation source code
    public final String _src;
    // Compile-time known initial argument type
    public final TypeInteger _arg;
    public CallingConv _callingConv;

    // ---------------------------
    public CodeGen( String src ) { this(src, TypeInteger.BOT, 123L ); }
    public CodeGen( String src, TypeInteger arg, long workListSeed ) {
        CODE = this;
        _phase = null;
        _callingConv = null;
        _start = new StartNode(arg);
        _stop = new StopNode(src);
        _src = src;
        _arg = arg;
        _iter = new IterPeeps(workListSeed);
        P = new Parser(this,arg);
    }


    // ---------------------------
    /**
     *  A counter, for unique node id generation.  Starting with value 1, to
     *  avoid bugs confusing node ID 0 with uninitialized values.
     */
    private int _uid = 1;
    public int UID() { return _uid; }
    public int getUID() { return _uid++; }

    // Next available memory alias number
    private int _alias = 2; // 0 is for control, 1 for memory
    public  int getALIAS() { return _alias++; }


    // Popular visit bitset, declared here, so it gets reused all over
    public final BitSet _visit = new BitSet();
    public BitSet visit() { assert _visit.isEmpty(); return _visit; }

    // Start and stop; end points of the generated IR
    public StartNode _start;
    public StopNode  _stop;

    // Global Value Numbering.  Hash over opcode and inputs; hits in this table
    // are structurally equal.
    public final HashMap<Node,Node> _gvn = new HashMap<>();

    // Compute "function indices": FIDX.
    // Each new request at the same signature gets a new FIDX.
    private final HashMap<TypeTuple,Integer> FIDXS = new HashMap<>();
    public TypeFunPtr makeFun( TypeTuple sig, Type ret ) {
        Integer i = FIDXS.get(sig);
        int fidx = i==null ? 0 : i;
        FIDXS.put(sig,fidx+1);  // Track count per sig
        assert fidx<64;         // TODO: need a larger FIDX space
        return TypeFunPtr.make((byte)2,sig,ret, 1L<<fidx );
    }
    // Signature for MAIN
    public TypeFunPtr _main = makeFun(TypeTuple.MAIN,Type.BOTTOM);
    // Reverse from a constant function pointer to the IR function being called
    public FunNode link( TypeFunPtr tfp ) {
        assert tfp.isConstant();
        return _linker.get(tfp.makeFrom(Type.BOTTOM));
    }

    // Insert linker mapping from constant function signature to the function
    // being called.
    public void link(FunNode fun) {
        _linker.put(fun.sig().makeFrom(Type.BOTTOM),fun);
    }

    // "Linker" mapping from constant TypeFunPtrs to heads of function.  These
    // TFPs all have exact single fidxs and their return is wiped to BOTTOM (so
    // the return is not part of the match).
    private final HashMap<TypeFunPtr,FunNode> _linker = new HashMap<>();

    // Parser object
    public final Parser P;

    // Parse ASCII text into Sea-of-Nodes IR
    public CodeGen parse() {
        assert _phase == null;
        _phase = Phase.Parse;

        P.parse();
        JSViewer.show();
        return this;
    }


    // ---------------------------
    // Iterator peepholes.
    public final IterPeeps _iter;
    // Stop tracking deps while assertin
    public boolean _midAssert;
    // Statistics on peepholes
    public int _iter_cnt, _iter_nop_cnt;
    // Stat gathering
    public void iterCnt() { if( !_midAssert ) _iter_cnt++; }
    public void iterNop() { if( !_midAssert ) _iter_nop_cnt++; }

    // Run ideal optimizations
    public CodeGen opto() {
        assert _phase == Phase.Parse;
        _phase = Phase.Opto;

        // Pessimistic peephole optimization on a worklist
        _iter.iterate(this);

        // TODO:
        // Optimistic
        // TODO:
        // loop unroll, peel, RCE, etc
        return this;
    }
    public <N extends Node> N add( N n ) { return (N)_iter.add(n); }
    public void addAll( Ary<Node> ary ) { _iter.addAll(ary); }


    // ---------------------------
    // Last check for bad programs
    public CodeGen typeCheck() {
        // Demand phase Opto for cleaning up dead control flow at least,
        // required for the following GCM.
        assert _phase.ordinal() <= Phase.Opto.ordinal();
        _phase = Phase.TypeCheck;

        Parser.ParseException err = _stop.walk( Node::err );
        if( err != null )
            throw err;
        return this;
    }


    // ---------------------------
    // Code generation CPU target
    public Machine _mach;

    // Convert to target hardware nodes
    public CodeGen instSelect( String cpu, String callingConv ) {
        assert _phase.ordinal() <= Phase.TypeCheck.ordinal();
        _phase = Phase.InstructionSelection;

        _callingConv = switch(callingConv) {
        case "SystemV" -> CallingConv.SystemV;
        case "Win64"   -> CallingConv.Win64;
        default -> throw Utils.TODO(); // Unknown calling convention
        };

        // Look for CPU in fixed named place:
        //   com.seaofnodes.simple.node.cpus."cpu"."cpu.class"
        String clzFile = "com.seaofnodes.simple.node.cpus."+cpu+"."+cpu;
        try { _mach = ((Class<Machine>) Class.forName( clzFile )).getDeclaredConstructor().newInstance(); }
        catch( Exception e ) { throw new RuntimeException(e); }

        // Convert to machine ops
        _uid = 1;               // All new machine nodes reset numbering
        var map = new IdentityHashMap<Node,Node>();
        _instSelect( _stop, map );
        _stop  = ( StopNode)map.get(_stop );
        _start = (StartNode)map.get(_start);
        _instOuts(_stop,visit());
        _visit.clear();
        return this;
    }

    // Walk all ideal nodes, recursively mapping ideal to machine nodes, then
    // make a machine node for "this".
    private Node _instSelect( Node n, IdentityHashMap<Node,Node> map ) {
        if( n==null ) return null;
        Node x = map.get(n);
        if( x !=null ) return x; // Been there, done that

        // If n is a MachConcrete, then its part of a multi-node expansion.
        // It does not need instruction selection (already selected!)
        // but it does need its inputs walked.
        if( n instanceof MachConcreteNode || n instanceof MemOpX86) {
            for( int i=0; i < n.nIns(); i++ )
                n._inputs.set(i, _instSelect(n.in(i),map) );
            return n;
        }

        // Produce a machine node from n; map it to flag as done so stops cycles.
        map.put(n, x=_mach.instSelect(n) );
        // Walk machine op and replace inputs with mapped inputs
        for( int i=0; i < x.nIns(); i++ )
            x._inputs.set(i, _instSelect(x.in(i),map) );
        if( x instanceof MachNode mach )
            mach.postSelect();  // Post selection action

        // Updates forward edges only.
        n._outputs.clear();
        return x;
    }

    // Walk all machine Nodes, and set their output edges
    private void _instOuts( Node n, BitSet visit ) {
        if( visit.get(n._nid) ) return;
        visit.set(n._nid);
        for( Node in : n._inputs )
            if( in!=null ) {
                in._outputs.push(n);
                _instOuts(in,visit);
            }
    }


    // ---------------------------
    // Control Flow Graph in RPO order.
    Ary<CFGNode> _cfg = new Ary<>(CFGNode.class);

    // Global schedule (code motion) nodes
    public CodeGen GCM() { return GCM(false); }
    public CodeGen GCM( boolean show) {
        assert _phase.ordinal() <= Phase.InstructionSelection.ordinal();
        _phase = Phase.Schedule;

        // Build the loop tree, fix never-exit loops
        _start.buildLoopTree(_stop);
        GlobalCodeMotion.buildCFG(this);
        if( show )
            System.out.println(new GraphVisualizer().generateDotOutput(_stop,null,null));
        return this;
    }

    // ---------------------------
    // Local (basic block) scheduler phase, a classic list scheduler
    public CodeGen localSched() {
        assert _phase == Phase.Schedule;
        _phase = Phase.LocalSched;
        ListScheduler.sched(this);
        return this;
     }


    // ---------------------------
    public String reg(Node n) {
        if( _phase == Phase.RegAlloc ) throw Utils.TODO();
        return "N"+ n._nid;
    }


    // ---------------------------
    SB asm(SB sb) { return ASMPrinter.print(sb,this); }
    String asm() { return asm(new SB()).toString(); }


    // Testing shortcuts
    Node ctrl() { return _stop.ret().ctrl(); }
    Node expr() { return _stop.ret().expr(); }
    String print() { return _stop.print(); }

    // Debugging helper
    @Override public String toString() { return _stop.p(9999); }

    // Debugging helper
    public Node f(int idx) { return _stop.find(idx); }
}
