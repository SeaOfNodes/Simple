package com.seaofnodes.graph;

import com.seaofnodes.graph.GraphSnapshot.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

/** Read-only display ownership, computed from detached snapshots, never compiler passes. */
final class GraphGroups {
    static ArrayList<Group> build(ArrayList<Node> nodes) {
        var result = new ArrayList<Group>();
        boolean any=false;
        for( Node n : nodes ) if( n.kind()==Kind.FUN || n.kind()==Kind.LOOP ) { any=true; break; }
        if( !any ) return result;
        int len = nodes.getLast().id()+1;
        Node[] ns = new Node[len];
        for( Node n : nodes ) ns[n.id()] = n;
        int[][] preds = new int[len][];
        int[] cnt = new int[len];
        for( Node n : nodes ) {
            int[] ps = new int[n.edges().size()];
            int size=0;
            for( Edge e : n.edges() )
                if( pred(n,e) && cfg(ns[e.def()]) ) {
                    ps[size++] = e.def();
                    cnt[e.def()]++;
                }
            preds[n.id()] = Arrays.copyOf(ps,size);
        }
        int[][] uses = new int[len][];
        for( Node n : nodes ) uses[n.id()] = new int[cnt[n.id()]];
        Arrays.fill(cnt,0);
        for( Node n : nodes ) for( int p : preds[n.id()] ) uses[p][cnt[p]++] = n.id();

        // Function calls/returns never carry ownership into another function.
        int[] fun = new int[len], work = new int[len];
        int head=0, tail=0;
        for( Node n : nodes ) if( n.kind()==Kind.FUN ) {
            fun[n.id()] = n.id();
            work[tail++] = n.id();
        }
        while( head<tail ) {
            int id = work[head++];
            for( int use : uses[id] ) if( fun[use]==0 ) {
                fun[use] = fun[id];
                work[tail++] = use;
            }
        }

        // Natural loops: walk backwards from the backedge, stopping at the
        // header. An unfinished loop has only its header until it closes.
        var loops = new ArrayList<Node>();
        BitSet[] body = new BitSet[len];
        for( Node n : nodes ) if( n.kind()==Kind.LOOP ) {
            int id = n.id(), back = def(n,2);
            BitSet set = body[id] = new BitSet();
            set.set(id);
            loops.add(n);
            if( back==0 || ns[back]==null ) continue;
            head=tail=0;
            if( back!=id ) { set.set(back); work[tail++]=back; }
            boolean closed=false, outside=false;
            while( head<tail ) {
                int cur = work[head++];
                if( fun[cur]!=fun[id] || ns[cur].kind()==Kind.FUN ||
                    ns[cur].kind()==Kind.START || ns[cur].kind()==Kind.UNIT ) { outside=true; break; }
                for( int p : preds[cur] ) {
                    if( p==id ) closed=true;
                    if( !set.get(p) ) { set.set(p); work[tail++]=p; }
                }
            }
            if( outside || !closed ) { set.clear(); set.set(id); }
        }
        // Outer loops first. Simple's structured CFG makes these sets nested
        // or disjoint.
        loops.sort((a,b) -> Integer.compare(body[b.id()].cardinality(),body[a.id()].cardinality()));
        int[] par = new int[len], home = new int[len];
        Arrays.fill(home,-1);
        for( Node n : nodes ) if( cfg(n) ) home[n.id()] = fun[n.id()];
        for( Node n : loops ) {
            int id=n.id();
            par[id]=home[id];
            for( int i=body[id].nextSetBit(0); i>=0; i=body[id].nextSetBit(i+1) ) home[i]=id;
        }
        for( Node n : nodes ) {
            int id=n.id();
            if( n.kind()==Kind.SCOPE || n.kind()==Kind.STOP ) home[id]=0;
            if( !cfg(n) && n.proj()==null && n.kind()!=Kind.PHI &&
                n.edges().stream().noneMatch(e -> e.def()!=0 && e.role()!=Role.ASSOC) ) home[id]=0;
            int anchor = n.proj()!=null ? n.proj().par() : def(n,0);
            if( !cfg(n) && n.kind()!=Kind.SCOPE && n.kind()!=Kind.STOP &&
                anchor!=0 && ns[anchor]!=null && cfg(ns[anchor]) )
                home[id]=home[anchor];
        }
        // Floating values belong to the common enclosing group of their uses.
        // A Phi's input is used on the corresponding predecessor, not at the
        // header (especially the entry input of a loop Phi).
        BitSet fixed = new BitSet();
        for( Node n : nodes ) if( home[n.id()]>=0 ) fixed.set(n.id());
        boolean changed;
        do {
            changed=false;
            for( Node n : nodes ) for( Edge e : n.edges() ) {
                int id=e.def();
                if( id==0 || ns[id]==null || fixed.get(id) || e.role()==Role.ASSOC ) continue;
                int site=n.id();
                if( n.kind()==Kind.PHI && e.idx()>0 ) {
                    Node reg=ns[def(n,0)];
                    site=reg==null ? 0 : def(reg,e.idx());
                }
                int grp=site==0 ? 0 : home[site];
                if( grp<0 ) continue;
                int next=lca(home[id],grp,par);
                if( next!=home[id] ) { home[id]=next; changed=true; }
            }
        } while( changed );
        // Projections and their MultiNode are one display box.
        for( Node n : nodes ) if( n.proj()!=null && n.proj().par()!=0 )
            home[n.id()]=home[n.proj().par()];
        for( Node n : nodes ) if( n.kind()==Kind.FUN || n.kind()==Kind.LOOP ) {
            int id=n.id(), size=0;
            for( Node m : nodes ) if( home[m.id()]==id ) size++;
            int[] ids = new int[size];
            size=0;
            for( Node m : nodes ) if( home[m.id()]==id ) ids[size++]=m.id();
            result.add(new Group(id,par[id],ids));
        }
        return result;
    }

    private static int lca(int a, int b, int[] par) {
        if( a<0 ) return b;
        for( int x=a; x!=0; x=par[x] )
            for( int y=b; y!=0; y=par[y] ) if( x==y ) return x;
        return 0;
    }

    private static int def(Node n, int idx) {
        for( Edge e : n.edges() ) if( e.idx()==idx ) return e.def();
        return 0;
    }

    private static boolean pred(Node n, Edge e) {
        if( e.def()==0 || e.role()!=Role.CTRL ) return false;
        return switch( n.kind() ) {
            case REGION, LOOP -> e.idx()>0;
            case CTRL -> e.idx()==0;
            default -> false;
        };
    }

    private static boolean cfg(Node n) {
        return n!=null && switch( n.kind() ) {
            case CTRL, REGION, LOOP, FUN, START, UNIT -> true;
            default -> false;
        };
    }
}
