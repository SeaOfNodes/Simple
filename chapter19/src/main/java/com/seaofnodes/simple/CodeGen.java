 package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;
import java.util.HashMap;

public class CodeGen {
    // Last created CodeGen as a global; used all over to avoid passing about a
    // "context".
    public static CodeGen CODE;

    public enum Phase {
        Parse, Opto, TypeCheck, Schedule, LocalSched, RegAlloc;
    }
    public Phase _phase;

    // Compilation source code
    public final String _src;
    // Compile-time known initial argument type
    public final TypeInteger _arg;

    /**
     * A counter, for unique node id generation.  Starting with value 1, to
     * avoid bugs confusing node ID 0 with uninitialized values.
     */
    private int _uid = 1;
    public int UID() { return _uid; }
    public int getUID() { return _uid++; }

    // Next available memory alias number
    private int _alias = 2; // 0 is for control, 1 for memory
    public  int getALIAS() { return _alias++; }


    // Popular visit bitset, declared here so it gets reused all over
    public final BitSet _wvisit = new BitSet();

    // Start and stop; end points of the generated IR
    public final StartNode _start;
    public final StopNode _stop;

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
    public TypeFunPtr _main = makeFun(TypeTuple.MAIN,Type.BOTTOM).setName("main");

    // "Linker" mapping from constant TypeFunPtrs to heads of function.  These
    // TFPs all have exact single fidxs and their return is wiped to BOTTOM (so
    // the return is not part of the match).
    private final HashMap<TypeFunPtr,FunNode> _linker = new HashMap<>();

    // Parser object
    public final Parser P;

    // Iterator peepholes.
    public final IterPeeps _iter;
    // Stop tracking deps while assertin
    public boolean _midAssert;
    // Statistics on peepholes
    public int _iter_cnt, _iter_nop_cnt;
    // Stat gathering
    public void iterCnt() { if( !_midAssert ) _iter_cnt++; }
    public void iterNop() { if( !_midAssert ) _iter_nop_cnt++; }


    public CodeGen( String src ) { this(src,TypeInteger.BOT, 123L ); }
    public CodeGen( String src, TypeInteger arg, long workListSeed ) {
        CODE = this;
        _phase = null;

        _start = new StartNode(arg);
        _stop = new StopNode(src);
        _src = src;
        _arg = arg;
        _iter = new IterPeeps(workListSeed);
        P = new Parser(this,arg);
    }

    public CodeGen parse() {
        assert _phase == null;
        _phase = Phase.Parse;

        P.parse();
        JSViewer.show();
        return this;
    }

    public CodeGen opto() {
        assert _phase == Phase.Parse;
        _phase = Phase.Opto;

        // Optimization
        _iter.iterate(this);

        // TODO:
        // Optimistic
        return this;
    }

    public CodeGen typeCheck() {
        // Demand phase Opto for cleaning up dead control flow at least,
        // required for the following GCM.  Note that peeps can be disabled,
        // but still the dead CFG will get cleaned.
        assert _phase == Phase.Opto;
        _phase = Phase.TypeCheck;

        // Type check
        Parser.ParseException err = _stop.walk( Node::err );
        if( err != null )
            throw err;
        return this;
    }


    public CodeGen GCM() { return GCM(false); }
    public CodeGen GCM( boolean show) {
        assert _phase == Phase.TypeCheck;
        _phase = Phase.Schedule;

        // Build the loop tree, fix never-exit loops
        _start.buildLoopTree(_stop);
        if( show )
            System.out.println(new GraphVisualizer().generateDotOutput(_stop,null,null));

        // TODO:
        // loop unroll, peel, RCE, etc
        GlobalCodeMotion.buildCFG(this);
        return this;
    }

    // Local (basic block) scheduler phase, a classic list scheduler
    public CodeGen localSched() {
        assert _phase == Phase.Schedule;
        _phase = Phase.LocalSched;
        ListScheduler.sched(this);
        return this;
     }

    // Reverse from a constant function pointer to the IR function being called
    public FunNode link( TypeFunPtr tfp ) {
        assert tfp.isConstant();
        return _linker.get(tfp.makeFrom(Type.BOTTOM));
    }

    public void link(FunNode fun) {
        _linker.put(fun.sig().makeFrom(Type.BOTTOM),fun);
    }


    public <N extends Node> N add( N n ) { return (N)_iter.add(n); }
    public void addAll( Ary<Node> ary ) { _iter.addAll(ary); }


    // Testing shortcuts
    Node ctrl() { return _stop.ret().ctrl(); }
    Node expr() { return _stop.ret().expr(); }
    String print() { return _stop.print(); }

    // Debugging helper
    @Override public String toString() {
        return _stop.p(9999);
    }

    // Debugging helper
    public Node f(int idx) { return _stop.find(idx); }
}
