package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphEvent;
import com.seaofnodes.graph.GraphObserver;
import com.seaofnodes.graph.GraphSnapshot;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.Node;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.UUID;

/** Capture events and neighborhoods on the compiler thread. Transport is supplied by the viewer. */
public abstract class SimpleGraphObserver extends GraphObserver<Node> {
    private final CodeGen _code;
    private final SimpleGraphAdapter _graph = new SimpleGraphAdapter();
    private final String _comp = UUID.randomUUID().toString();
    private final ArrayList<Peep> _peeps = new ArrayList<>();
    private long _next, _step;
    private boolean _off;

    protected SimpleGraphObserver(CodeGen code) { _code = code; }

    protected abstract void frame(GraphSnapshot snap, int pos, GraphEvent evt) throws IOException;

    private static class Peep {
        final Node node;
        final long id, up;
        final String phase;
        final int pos;
        final BitSet near = new BitSet();
        GraphSnapshot pre;
        boolean sent;

        Peep(Node n, long id, long up, String phase, int pos) {
            node = n; this.id = id; this.up = up; this.phase = phase; this.pos = pos;
        }
    }

    @Override public void before(Node n) {
        if( _off ) return;
        var p = new Peep(n, ++_next, _peeps.isEmpty() ? 0 : _peeps.getLast().id,
                         _code._phase.name(), pos());
        _peeps.add(p);
        near(p, n);
        // Buffer until we know that this attempt (or a nested one) made progress.
        // Include n explicitly: parse-time nodes need not have any uses yet.
        p.pre = snap(n);
    }

    @Override public void after(Node n, Node repl, boolean applied) {
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
            Node rez = repl == null ? n : repl;
            emit(snap(rez.isDead() ? null : rez), pos(),
                 evt(p, applied ? GraphEvent.Kind.APPLY : GraphEvent.Kind.RETURN,
                     rez.isDead() ? 0 : rez._nid));
        }
        _peeps.removeLast();
    }

    @Override public void dep(Node n, Node def) {
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
        return new GraphEvent(kind, p.id, p.up, p.phase, p.node._nid, repl, p.near.stream().toArray());
    }

    private void add(Peep p, Node n) { if( n != null ) p.near.set(n._nid); }

    private void near(Peep p, Node n) {
        if( n == null ) return;
        add(p, n);
        for( Node def : n._inputs  ) add(p, def);
        for( Node use : n._outputs ) add(p, use);
        // These are wake-up dependents, not all nodes inspected by a rule.
        // Read before the compiler drains the list; never move/clear it here.
        for( int i = 0; i < n.nDeps(); i++ ) add(p, n.dep(i));
    }

    private int pos() { return _code._phase == CodeGen.Phase.Parse ? _code.P.pos() : -1; }

    private GraphSnapshot snap(Node extra) {
        var roots = new ArrayList<Node>();
        roots.add(_code._stop);
        if( _code._phase == CodeGen.Phase.Parse ) {
            roots.add(_code.P._scope);
            roots.addAll(_code.P._xScopes);
        }
        for( Peep p : _peeps ) if( !p.node.isDead() ) roots.add(p.node);
        roots.add(extra);
        return _graph.snap(_comp, 0, roots.toArray(Node[]::new));
    }

    private void emit(GraphSnapshot snap, int pos, GraphEvent evt) {
        if( _off ) return;
        // Assign steps on emission; attempts without progress do not consume frames.
        snap = new GraphSnapshot(snap.ver(), _comp, _step++, snap.roots(), snap.nodes());
        try {
            frame(snap, pos, evt);
        } catch( IOException e ) {
            // A disconnected display must not interrupt optimization.
            _off = true;
            _code._obs = null;
            System.err.println("Graph viewer disconnected: " + e.getMessage());
        }
    }
}
