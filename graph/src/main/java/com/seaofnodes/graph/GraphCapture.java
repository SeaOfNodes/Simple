package com.seaofnodes.graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.UUID;

/** Shared compilation lifecycle, capture, event nesting and neighborhoods. */
public abstract class GraphCapture<N> extends GraphObserver<N> {

    private final GraphAdapter<N> _graph;
    private String _comp;
    private GraphSocket _server;
    private final ArrayList<Peep> _peeps = new ArrayList<>();
    private long _next, _step;
    private boolean _off = true;

    protected GraphCapture(GraphAdapter<N> graph) { _graph = graph; }

    // Create the chapter's compiler, attach this observer, and run its phases.
    protected abstract void compile(String src);
    // The remaining hooks only inspect context, except detach which clears it.
    protected abstract String phase();
    protected abstract int pos();
    protected abstract void roots(ArrayList<N> roots);
    protected abstract void detach();

    final void run(String src, GraphSocket server) {
        _comp = UUID.randomUUID().toString();
        _next = _step = 0;
        _server = server;
        _off = false;
        try {
            compile(src);
        } finally {
            _off = true;
            try {
                detach();
            } finally {
                _peeps.clear();
                _server = null;
            }
        }
    }

    private class Peep {
        final N node;
        final long id, up;
        final String phase;
        final int pos;
        final BitSet near = new BitSet();
        GraphSnapshot pre;
        boolean sent;

        Peep(N n, long id, long up, String phase, int pos) {
            node = n; this.id = id; this.up = up; this.phase = phase; this.pos = pos;
        }
    }

    @Override public void before(N n) {
        if( _off ) return;
        var p = new Peep(n, ++_next, _peeps.isEmpty() ? 0 : _peeps.getLast().id,
                         phase(), pos());
        _peeps.add(p);
        near(p, n);
        // Buffer until we know that this attempt (or a nested one) made progress.
        // Include n explicitly: parse-time nodes need not have any uses yet.
        p.pre = snap(n);
    }

    @Override public void after(N n, N repl, boolean applied) {
        if( _off ) return;
        Peep p = _peeps.getLast();
        assert p.node == n;
        near(p, n);
        near(p, repl);
        for( Peep up : _peeps ) up.near.or(p.near);
        if( repl != null || p.sent ) {
            // Emit ancestors first so a nested rewrite always has its context.
            for( Peep up : _peeps ) {
                if( up.sent ) continue;
                emit(up.pre, up.pos, evt(up, GraphEvent.Kind.BEFORE, 0));
                if( _off ) return;
                up.pre = null;
                up.sent = true;
            }
            N rez = repl == null ? n : repl;
            emit(snap(_graph.dead(rez) ? null : rez), pos(),
                 evt(p, applied ? GraphEvent.Kind.APPLY : GraphEvent.Kind.RETURN,
                     _graph.dead(rez) ? 0 : _graph.id(rez)));
        }
        _peeps.removeLast();
    }

    @Override public void dep(N n, N def) {
        if( _off || _peeps.isEmpty() ) return;
        Peep p = _peeps.getLast();
        add(p, n);
        add(p, def);
    }

    @Override public void phase(String phase) {
        if( _off ) return;
        emit(snap(null), pos(), new GraphEvent(GraphEvent.Kind.PHASE, 0, 0, phase, 0, 0, new int[0]));
    }

    private GraphEvent evt(Peep p, GraphEvent.Kind kind, int repl) {
        return new GraphEvent(kind, p.id, p.up, p.phase, _graph.id(p.node), repl, p.near.stream().toArray());
    }

    private void add(Peep p, N n) { if( n != null ) p.near.set(_graph.id(n)); }

    private void near(Peep p, N n) {
        if( n == null ) return;
        add(p, n);
        for( int i = 0; i < _graph.nIns(n); i++ ) add(p, _graph.in(n, i));
        for( int i = 0; i < _graph.nOuts(n); i++ ) add(p, _graph.out(n, i));
        // These are wake-up dependents, not all nodes inspected by a rule.
        // Read before the compiler drains the list; never move/clear it here.
        for( int i = 0; i < _graph.nDeps(n); i++ ) add(p, _graph.dep(n, i));
    }


    private GraphSnapshot snap(N extra) {
        var roots = new ArrayList<N>();
        roots(roots);
        for( Peep p : _peeps ) if( !_graph.dead(p.node) ) roots.add(p.node);
        roots.add(extra);
        return _graph.snap(_comp, 0, roots);
    }

    private void emit(GraphSnapshot snap, int pos, GraphEvent evt) {
        if( _off ) return;
        // Assign steps on emission; attempts without progress do not consume frames.
        snap = new GraphSnapshot(snap.ver(), _comp, _step++, snap.roots(), snap.nodes());
        try {
            _server.put(GraphJson.frame(snap, pos, evt));
        } catch( IOException e ) {
            // A disconnected display must not interrupt optimization.
            _off = true;
            System.err.println("Graph viewer disconnected: " + e.getMessage());
        }
    }
}
