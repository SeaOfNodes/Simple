package com.seaofnodes.print;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import static com.seaofnodes.print.IRAdapter.Kind;

/** Shared diagnostic traversal. All ownership, ordering and visited state is local. */
public final class IRPrinter<N extends BaseNode<N>> {
    private final IRAdapter<N> _a;
    public IRPrinter(IRAdapter<N> adapter) { _a = adapter; }

    public record Unit<N>(String name, N start, N stop, ArrayList<N> functions) {}

    public String program(N start, N stop, ArrayList<Unit<N>> units) {
        StringBuilder sb=new StringBuilder(line(start));
        ArrayList<N> constants=new ArrayList<>();
        IdentityHashMap<N,Boolean> graph=new IdentityHashMap<>(), global=new IdentityHashMap<>();
        globals(start,graph,constants);
        for( N n : constants ) global.put(n,Boolean.TRUE);
        // GVN can share a floating expression of constants between functions.
        for( N n : sorted(graph) )
            if( !_a.global(n) && floatingGlobal(n,global) ) constants.add(n);
        constants.sort(Comparator.comparingInt(_a::id));
        for( N n : constants ) sb.append(line(n));
        ArrayList<Unit<N>> ordered=new ArrayList<>(units);
        ordered.sort(Comparator.comparing(u -> u.name()==null ? "" : u.name()));
        for( Unit<N> unit : ordered ) {
            if( unit.start()!=null ) {
                if( _a.dead(unit.start()) ) continue;
                sb.append("\n=== ").append(unit.name()).append(" ===\n").append(line(unit.start()));
                ArrayList<N> ps=new ArrayList<>();
                for( int i=0; i<_a.nOuts(unit.start()); i++ ) {
                    N p=_a.out(unit.start(),i);
                    if( _a.projection(p) ) ps.add(p);
                }
                ps.sort(projectionOrder());
                for( N p : ps ) sb.append(line(p));
            }
            ArrayList<N> funs=new ArrayList<>(unit.functions());
            funs.sort(Comparator.comparingInt(_a::id));
            for( N fun : funs ) if( !_a.dead(fun) ) sb.append(function(fun,global));
            sb.append(line(unit.stop()));
        }
        return sb.append(line(stop)).toString();
    }

    // Function-pointer constants can be attached to Returns rather than Start.
    private void globals(N n, IdentityHashMap<N,Boolean> seen, ArrayList<N> constants) {
        if( n==null || seen.put(n,Boolean.TRUE)!=null ) return;
        if( _a.global(n) && !_a.dead(n) ) constants.add(n);
        for( int i=0; i<_a.nIns(n); i++ ) globals(_a.in(n,i),seen,constants);
        for( int i=0; i<_a.nOuts(n); i++ ) globals(_a.out(n,i),seen,constants);
    }

    private boolean floatingGlobal(N n, IdentityHashMap<N,Boolean> global) {
        if( n==null ) return true;
        Boolean old=global.get(n);
        if( old!=null ) return old;
        global.put(n,Boolean.FALSE); // Stop incomplete data cycles.
        if( _a.dead(n) || _a.kind(n)!=Kind.DATA || _a.input0(n)!=null || _a.nIns(n)==0 ) return false;
        for( int i=1; i<_a.nIns(n); i++ )
            if( !floatingGlobal(_a.in(n,i),global) ) return false;
        global.put(n,Boolean.TRUE);
        return true;
    }

    public String line(N n) {
        if( n == null ) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("%4d %-7.7s ".formatted(_a.id(n),_a.label(n)));
        if( _a.dead(n) ) return sb.append("DEAD\n").toString();
        for( int i=0; i<_a.nIns(n); i++ ) {
            N def = _a.in(n,i);
            sb.append(def == null ? "____" : "%4d".formatted(_a.id(def))).append(_a.inputMark(n,def));
        }
        int cols = _a.inputColumns();
        for( int i=_a.nIns(n); i<cols; i++ ) sb.append("     ");
        sb.append(" [[  ");
        for( int i=0; i<_a.nOuts(n); i++ ) {
            N use = _a.out(n,i);
            sb.append(use == null ? "____ " : "%4d ".formatted(_a.id(use)));
        }
        for( int i=_a.nOuts(n); i<cols+2-Math.max(_a.nIns(n),cols); i++ ) sb.append("     ");
        return sb.append(" ]]  ").append(_a.type(n)).append('\n').toString();
    }

    /** A bounded backward slice, including the headers of boundary projections/Phis. */
    public String prettyPrint(N root, int depth) {
        IdentityHashMap<N,Boolean> nodes = new IdentityHashMap<>();
        ArrayDeque<N> todo = new ArrayDeque<>();
        if( root != null ) { nodes.put(root,Boolean.TRUE); todo.add(root); }
        for( int d=0; d<depth && !todo.isEmpty(); d++ ) {
            int count=todo.size();
            while( count-->0 ) {
                N n=todo.remove();
                for( int i=0; i<_a.nIns(n); i++ ) {
                    N def=_a.in(n,i);
                    if( def != null && nodes.put(def,Boolean.TRUE)==null ) todo.add(def);
                }
            }
        }
        for( N n : new ArrayList<>(nodes.keySet()) )
            if( _a.phi(n) || _a.projection(n) ) {
                N head=_a.input0(n);
                if( head != null ) nodes.put(head,Boolean.TRUE);
            }
        // Complete projection groups without extending ordinary dependencies.
        for( N n : new ArrayList<>(nodes.keySet()) )
            for( int i=0; i<_a.nOuts(n); i++ ) {
                N p=_a.out(n,i);
                if( _a.projection(p) && _a.input0(p)==n ) nodes.put(p,Boolean.TRUE);
            }
        return render(nodes);
    }

    /** Dump one function without traversing the caller/callee linkage. */
    public String function(N fun) {
        return function(fun,new IdentityHashMap<>());
    }

    private String function(N fun, IdentityHashMap<N,Boolean> global) {
        IdentityHashMap<N,Boolean> nodes = new IdentityHashMap<>();
        collect(fun,fun,nodes);
        nodes.keySet().removeIf(n -> Boolean.TRUE.equals(global.get(n)));
        // Floating expressions of constants need not have a forward path from
        // the function header. Include their definitions from the owned uses.
        for( N n : new ArrayList<>(nodes.keySet()) )
            // Parm alternatives are arguments in the callers, not local defs.
            if( _a.kind(n)!=Kind.PARM )
                for( int i=0; i<_a.nIns(n); i++ ) collectData(_a.in(n,i),nodes,global);
        return "\n--- "+_a.signature(fun)+"----------------------\n"+
            render(nodes)+"--- "+_a.functionName(fun)+" ----------------------\n";
    }

    private void collect(N n, N owner, IdentityHashMap<N,Boolean> seen) {
        if( n == null || _a.dead(n) || seen.containsKey(n) || _a.global(n) ) return;
        Kind k=_a.kind(n);
        if( (k==Kind.FUN && n!=owner) || (k==Kind.PARM && _a.input0(n)!=owner) ) return;
        if( k==Kind.START || k==Kind.STOP || k==Kind.UNIT ) return;
        if( k==Kind.CALL_END && !seen.containsKey(_a.input0(n)) ) return;
        seen.put(n,Boolean.TRUE);
        if( k!=Kind.RETURN )
            for( int i=0; i<_a.nOuts(n); i++ ) collect(_a.out(n,i),owner,seen);
    }

    private void collectData(N n, IdentityHashMap<N,Boolean> seen, IdentityHashMap<N,Boolean> global) {
        if( n==null || seen.containsKey(n) || _a.dead(n) || _a.global(n) || _a.control(n) ||
            Boolean.TRUE.equals(global.get(n)) ) return;
        N head=_a.input0(n);
        if( _a.control(head) && !seen.containsKey(head) ) return;
        seen.put(n,Boolean.TRUE);
        for( int i=0; i<_a.nIns(n); i++ ) collectData(_a.in(n,i),seen,global);
    }

    private ArrayList<N> sorted(IdentityHashMap<N,Boolean> nodes) {
        ArrayList<N> ns = new ArrayList<>(nodes.keySet());
        ns.sort(Comparator.comparingInt(_a::id));
        return ns;
    }

    /** Finish CFG RPO before placing data; data backedges cannot reorder blocks. */
    private String render(IdentityHashMap<N,Boolean> nodes) {
        ArrayList<N> ns=sorted(nodes), post=new ArrayList<>();
        IdentityHashMap<N,Boolean> seen=new IdentityHashMap<>();
        // Start from CFG roots, then cover disconnected/partially built components.
        for( N n : ns )
            if( _a.control(n) && !hasPred(n,nodes) ) controlPost(n,nodes,seen,post);
        for( N n : ns )
            if( _a.control(n) ) controlPost(n,nodes,seen,post);
        Collections.reverse(post);
        IdentityHashMap<N,Integer> blocks=new IdentityHashMap<>();
        ArrayList<ArrayList<N>> body=new ArrayList<>();
        for( int i=0; i<post.size(); i++ ) { blocks.put(post.get(i),i); body.add(new ArrayList<>()); }
        if( body.isEmpty() ) body.add(new ArrayList<>());
        for( N n : ns ) block(n,nodes,blocks);
        IdentityHashMap<N,Integer> placed=new IdentityHashMap<>();
        for( N n : ns )
            if( !_a.control(n) ) body.get(place(n,nodes,blocks,placed)).add(n);
        StringBuilder sb=new StringBuilder();
        IdentityHashMap<N,Boolean> emitted=new IdentityHashMap<>();
        for( int i=0; i<body.size(); i++ ) {
            if( i<post.size() ) {
                N cfg=post.get(i);
                if( !emitted.containsKey(cfg) ) sb.append('\n');
                for( N n : body.get(i) )
                    if( !_a.phi(n) && !_a.projection(n) && _a.input0(n)==null ) emit(n,nodes,emitted,sb);
                emitDefs(cfg,nodes,emitted,sb);
                emitLine(cfg,emitted,sb);
                projections(cfg,nodes,emitted,sb);
                // A Phi's backedge belongs in the body, never ahead of the Phi.
                for( N n : body.get(i) ) if( _a.phi(n) ) emitLine(n,emitted,sb);
            }
            for( N n : body.get(i) ) emit(n,nodes,emitted,sb);
        }
        // Include unanchored Phis and dead/incomplete nodes exactly once.
        for( N n : ns ) emitLine(n,emitted,sb);
        return sb.toString();
    }

    private boolean edge(N def, N use) {
        if( !_a.control(use) || _a.kind(use)==Kind.FUN || _a.kind(def)==Kind.RETURN ) return false;
        if( _a.region(use) || _a.kind(use)==Kind.STOP ) {
            for( int i=_a.kind(use)==Kind.STOP ? 0 : 1; i<_a.nIns(use); i++ )
                if( _a.in(use,i)==def ) return true;
            return false;
        }
        return _a.input0(use)==def;
    }
    private boolean hasPred(N n, IdentityHashMap<N,Boolean> nodes) {
        for( int i=0; i<_a.nIns(n); i++ ) {
            N def=_a.in(n,i);
            // The loop entry determines its root status, not its backedge.
            if( _a.kind(n)==Kind.LOOP && i>1 ) break;
            if( def!=null && nodes.containsKey(def) && _a.control(def) && edge(def,n) ) return true;
        }
        return false;
    }
    private void controlPost(N n, IdentityHashMap<N,Boolean> nodes,
                             IdentityHashMap<N,Boolean> seen, ArrayList<N> post) {
        if( seen.put(n,Boolean.TRUE)!=null ) return;
        ArrayList<N> uses=new ArrayList<>();
        for( int i=0; i<_a.nOuts(n); i++ ) {
            N use=_a.out(n,i);
            if( use!=null && nodes.containsKey(use) && edge(n,use) ) uses.add(use);
        }
        uses.sort(Comparator.comparingInt(_a::id));
        for( N use : uses ) controlPost(use,nodes,seen,post);
        post.add(n);
    }
    private int block(N n, IdentityHashMap<N,Boolean> nodes, IdentityHashMap<N,Integer> blocks) {
        Integer old=blocks.get(n);
        if( old!=null ) return old;
        if( n==null || !nodes.containsKey(n) || _a.kind(n)==Kind.CONSTANT ) return 0;
        blocks.put(n,0);
        int b=0;
        N head=_a.input0(n);
        if( _a.control(head) || _a.projection(n) ) b=block(head,nodes,blocks);
        else for( int i=0; i<_a.nIns(n); i++ ) b=Math.max(b,block(_a.in(n,i),nodes,blocks));
        blocks.put(n,b);
        return b;
    }
    private void emitDefs(N n, IdentityHashMap<N,Boolean> nodes, IdentityHashMap<N,Boolean> emitted,
                          StringBuilder sb) {
        for( int i=0; i<_a.nIns(n); i++ ) emit(_a.in(n,i),nodes,emitted,sb);
    }

    // Floating data follows its uses. A loop Phi consumes its backedge value
    // at the latch, not at the header of that (possibly enclosing) loop.
    private int place(N n, IdentityHashMap<N,Boolean> nodes, IdentityHashMap<N,Integer> blocks,
                      IdentityHashMap<N,Integer> placed) {
        Integer old=placed.get(n);
        if( old!=null ) return old;
        int early=block(n,nodes,blocks);
        placed.put(n,early); // Close data cycles without changing compiler state.
        if( _a.control(n) || _a.phi(n) || _a.projection(n) || _a.global(n) ||
            _a.input0(n)!=null ) return early;
        int late=Integer.MAX_VALUE;
        for( int i=0; i<_a.nOuts(n); i++ ) {
            N use=_a.out(n,i);
            if( use==null || !nodes.containsKey(use) || _a.kind(use)==Kind.PARM ) continue;
            if( _a.phi(use) && _a.input0(use)!=null && _a.kind(_a.input0(use))==Kind.LOOP ) {
                N loop=_a.input0(use);
                for( int j=1; j<_a.nIns(use); j++ )
                    if( _a.in(use,j)==n && j<_a.nIns(loop) ) {
                        N pred=_a.in(loop,j);
                        if( _a.projection(pred) ) pred=_a.input0(pred);
                        if( pred!=null && blocks.containsKey(pred) ) late=Math.min(late,blocks.get(pred));
                    }
            } else late=Math.min(late,place(use,nodes,blocks,placed));
        }
        int b=late==Integer.MAX_VALUE ? early : Math.max(early,late);
        placed.put(n,b);
        return b;
    }
    private void emit(N n, IdentityHashMap<N,Boolean> nodes, IdentityHashMap<N,Boolean> emitted,
                      StringBuilder sb) {
        if( n==null || emitted.containsKey(n) || !nodes.containsKey(n) || _a.control(n) || _a.phi(n) ) return;
        if( _a.projection(n) && nodes.containsKey(_a.input0(n)) ) {
            N head=_a.input0(n);
            if( !emitted.containsKey(head) ) emit(head,nodes,emitted,sb);
            if( emitted.containsKey(n) ) return;
        }
        emitted.put(n,Boolean.TRUE);
        emitDefs(n,nodes,emitted,sb);
        sb.append(line(n));
        projections(n,nodes,emitted,sb);
    }
    private void emitLine(N n, IdentityHashMap<N,Boolean> emitted, StringBuilder sb) {
        if( emitted.put(n,Boolean.TRUE)==null ) sb.append(line(n));
    }
    private void projections(N n, IdentityHashMap<N,Boolean> nodes, IdentityHashMap<N,Boolean> emitted,
                             StringBuilder sb) {
        ArrayList<N> ps=new ArrayList<>();
        for( int i=0; i<_a.nOuts(n); i++ ) {
            N p=_a.out(n,i);
            if( _a.projection(p) && nodes.containsKey(p) && _a.input0(p)==n ) ps.add(p);
        }
        ps.sort(projectionOrder());
        for( N p : ps ) emitLine(p,emitted,sb);
    }

    private Comparator<N> projectionOrder() {
        return Comparator.comparingInt(_a::index).thenComparingInt(_a::id);
    }

    /** Scheduling already determines order; inspect it without sorting compiler arrays. */
    public String scheduled(ArrayList<N> blocks) {
        StringBuilder sb=new StringBuilder();
        IdentityHashMap<N,Boolean> emitted=new IdentityHashMap<>();
        for( N block : blocks ) {
            if( block==null ) continue;
            sb.append('\n');
            emitLine(block,emitted,sb);
            for( int i=0; i<_a.nOuts(block); i++ ) {
                N use=_a.out(block,i);
                if( _a.phi(use) ) emitLine(use,emitted,sb);
            }
            for( int i=0; i<_a.nOuts(block); i++ ) {
                N use=_a.out(block,i);
                if( use!=null && !_a.control(use) ) {
                    emitLine(use,emitted,sb);
                    ArrayList<N> ps=new ArrayList<>();
                    for( int j=0; j<_a.nOuts(use); j++ ) {
                        N p=_a.out(use,j);
                        if( p!=null && _a.kind(p)==Kind.PROJ && _a.input0(p)==use ) ps.add(p);
                    }
                    ps.sort(projectionOrder());
                    for( N p : ps ) emitLine(p,emitted,sb);
                }
            }
        }
        return sb.toString();
    }
}
