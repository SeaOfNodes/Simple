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
        for( Node n : nodes ) if( n.kind()==Kind.FUN || n.kind()==Kind.LOOP || n.label().equals("If") ) { any=true; break; }
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
        var parts = new ArrayList<Node>();
        BitSet[] body = new BitSet[len];
        for( Node n : nodes ) if( n.kind()==Kind.LOOP ) {
            int id = n.id(), back = def(n,2);
            BitSet set = body[id] = new BitSet();
            set.set(id);
            parts.add(n);
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
        // Look for a closed If/Region diamond. Reachable joins are considered
        // nearest first; a candidate must have no other CFG entry or exit.
        for( Node n : nodes ) if( n.kind()==Kind.CTRL && n.label().equals("If") && uses[n.id()].length==2 ) {
            BitSet seen = new BitSet();
            head=tail=0;
            work[tail++]=n.id(); seen.set(n.id());
            while( head<tail ) {
                int id=work[head++];
                if( ns[id].kind()==Kind.REGION ) {
                    BitSet set=diamond(n.id(),id,ns,preds,uses);
                    if( set!=null ) { body[n.id()]=set; parts.add(n); break; }
                }
                for( int use : uses[id] ) if( !seen.get(use) ) {
                    seen.set(use); work[tail++]=use;
                }
            }
        }
        // Outer regions first. Only nested or disjoint sets can form boxes;
        // overlapping loop/diamond candidates keep the already accepted group.
        parts.sort((a,b) -> Integer.compare(body[b.id()].cardinality(),body[a.id()].cardinality()));
        int[] par = new int[len], home = new int[len];
        Arrays.fill(home,-1);
        for( Node n : nodes ) if( cfg(n) ) home[n.id()] = fun[n.id()];
        var accepted = new ArrayList<Node>();
        BitSet gids = new BitSet();
        for( Node n : parts ) {
            int id=n.id();
            boolean fits=true;
            for( Node p : accepted ) if( body[id].intersects(body[p.id()]) ) {
                BitSet rest=(BitSet)body[id].clone();
                rest.andNot(body[p.id()]);
                if( !rest.isEmpty() || body[id].get(p.id()) ) { fits=false; break; }
            }
            if( !fits ) continue;
            accepted.add(n);
            gids.set(id);
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
        // Compute input and output ownership independently, so a floating chain
        // between two regions cannot pull itself into either end. Null slots and
        // parser/lifetime associations do not nominate a region. Phi uses refer
        // to the Phi itself, not to its incoming CFG path.
        BitSet fixed = new BitSet();
        for( Node n : nodes ) if( home[n.id()]>=0 ) fixed.set(n.id());
        int[] ins=home.clone(), outs=home.clone();
        boolean changed;
        do {
            changed=false;
            for( Node n : nodes ) for( Edge e : n.edges() ) {
                int id=e.def();
                if( id==0 || ns[id]==null || e.role()==Role.ASSOC ) continue;
                if( !fixed.get(n.id()) && ins[id]>=0 ) {
                    int next=lca(ins[n.id()],ins[id],par);
                    if( next!=ins[n.id()] ) { ins[n.id()]=next; changed=true; }
                }
                if( !fixed.get(id) && outs[n.id()]>=0 ) {
                    int next=lca(outs[id],outs[n.id()],par);
                    if( next!=outs[id] ) { outs[id]=next; changed=true; }
                }
            }
        } while( changed );
        for( Node n : nodes ) if( !fixed.get(n.id()) ) {
            home[n.id()]=owner(ins[n.id()],outs[n.id()],par);
        }
        // Unattached floating users may have no directional anchor. Check the
        // actual neighbors too, widening unsupported claims until they settle.
        // Only moving outward avoids self-supporting cycles of floating nodes.
        do {
            changed=false;
            Arrays.fill(ins,-1); Arrays.fill(outs,-1);
            for( Node n : nodes ) for( Edge e : n.edges() ) if( e.def()!=0 && e.role()!=Role.ASSOC ) {
                ins[n.id()]=lca(ins[n.id()],home[e.def()],par);
                outs[e.def()]=lca(outs[e.def()],home[n.id()],par);
            }
            for( Node n : nodes ) if( !fixed.get(n.id()) ) {
                int id=n.id(), next=lca(home[id],owner(ins[id],outs[id],par),par);
                if( next!=home[id] ) { home[id]=next; changed=true; }
            }
        } while( changed );
        // Projections and their MultiNode are one display box.
        for( Node n : nodes ) if( n.proj()!=null && n.proj().par()!=0 )
            home[n.id()]=home[n.proj().par()];
        for( Node n : nodes ) if( n.kind()==Kind.FUN || gids.get(n.id()) ) {
            int id=n.id(), size=0;
            for( Node m : nodes ) if( home[m.id()]==id ) size++;
            int[] ids = new int[size];
            size=0;
            for( Node m : nodes ) if( home[m.id()]==id ) ids[size++]=m.id();
            result.add(new Group(id,par[id],ids));
        }
        return result;
    }

    private static BitSet diamond(int entry, int exit, Node[] ns, int[][] preds, int[][] uses) {
        BitSet set=new BitSet();
        int[] todo=new int[ns.length];
        int head=0, tail=0;
        todo[tail++]=entry; set.set(entry);
        while( head<tail ) {
            int id=todo[head++];
            if( id==exit ) continue;
            if( uses[id].length==0 || ns[id].kind()==Kind.FUN ||
                ns[id].kind()==Kind.START || ns[id].kind()==Kind.UNIT ) return null;
            for( int use : uses[id] ) if( !set.get(use) ) { set.set(use); todo[tail++]=use; }
        }
        if( !set.get(exit) ) return null;
        for( int id=set.nextSetBit(0); id>=0; id=set.nextSetBit(id+1) ) if( id!=entry )
            for( int p : preds[id] ) if( !set.get(p) ) return null;
        // Reject trapped cycles as well as paths to another exit.
        BitSet reaches=new BitSet();
        head=tail=0;
        todo[tail++]=exit; reaches.set(exit);
        while( head<tail ) for( int p : preds[todo[head++]] )
            if( set.get(p) && !reaches.get(p) ) { reaches.set(p); todo[tail++]=p; }
        return reaches.equals(set) ? set : null;
    }

    private static int lca(int a, int b, int[] par) {
        if( a<0 ) return b;
        for( int x=a; x!=0; x=par[x] )
            for( int y=b; y!=0; y=par[y] ) if( x==y ) return x;
        return 0;
    }

    private static int owner(int a, int b, int[] par) {
        a=Math.max(0,a); b=Math.max(0,b);
        if( a==0 ) return b;
        if( b==0 || a==b ) return a;
        // Sibling nominations leave the node between the regions. Nested
        // nominations agree on the enclosing region (e.g. a function and loop).
        return lca(a,b,par);
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
