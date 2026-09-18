package com.seaofnodes.simple.print;

import com.seaofnodes.simple.node.*;
import java.util.*;

/** A debugger's ordering, independent of compiler scheduling and cached dominators. */
final class DebugSchedule {
    static Node input0(Node n) {
        return n==null || n._inputs==null || n._inputs.isEmpty() ? null : n._inputs.get(0);
    }

    static boolean head(Node n) { return n instanceof RegionNode || n instanceof MultiNode; }

    static boolean projection(Node n) { return n instanceof Proj; }

    static boolean cfg(Node n) { return n instanceof CFGNode; }

    static boolean gap(Node n) { return cfg(n) && !projection(n); }

    static boolean child(Node n) {
        Node h = input0(n);
        return (n instanceof PhiNode && h instanceof RegionNode) ||
               (projection(n) && h instanceof MultiNode);
    }

    private static Node group(Node n, IdentityHashMap<Node,Boolean> selected) {
        return child(n) && selected.containsKey(input0(n)) ? input0(n) : n;
    }

    private static int order(Node n) {
        if( n instanceof Proj proj ) return proj.idx();
        return n._nid;
    }

    // Use identity, not node equality (GVN), for all debugger bookkeeping.
    static ArrayList<Node> schedule(Node base, int depth) {
        ArrayList<Node> nodes = new ArrayList<>();
        IdentityHashMap<Node,Integer> distance = new IdentityHashMap<>();
        add(base,0,nodes,distance);
        for( int i=0; i<nodes.size(); i++ ) {
            Node n = nodes.get(i);
            int d = distance.get(n);
            if( n._inputs!=null && d<Math.max(0,depth) )
                for( Node def : n._inputs ) add(def,d+1,nodes,distance);
        }
        // Complete block headers at the depth boundary, without pulling in
        // another round of ordinary dependencies through sibling Phis.
        for( int i=0; i<nodes.size(); i++ ) {
            Node n = nodes.get(i);
            if( child(n) ) add(input0(n),depth,nodes,distance);
            if( head(n) && n._outputs!=null )
                for( Node use : n._outputs )
                    if( child(use) && input0(use)==n ) add(use,depth,nodes,distance);
        }

        return orderNodes(nodes);
    }

    // Function ownership follows raw uses, stopping at call/return linkages.
    static ArrayList<Node> function(FunNode fun) {
        ArrayList<Node> nodes = new ArrayList<>();
        IdentityHashMap<Node,Boolean> seen = new IdentityHashMap<>();
        nodes.add(fun);
        seen.put(fun,Boolean.TRUE);
        for( int i=0; i<nodes.size(); i++ ) {
            Node n = nodes.get(i);
            if( n instanceof ReturnNode || n._outputs==null ) continue;
            for( Node use : n._outputs ) {
                if( use==null || seen.containsKey(use) ) continue;
                if( use instanceof FunNode other && other!=fun ) continue;
                if( use instanceof ParmNode && input0(use)!=fun ) continue;
                if( use instanceof StartNode || use instanceof StartCUNode ||
                    use instanceof StopCUNode || use instanceof StopNode ) continue;
                if( use instanceof CallEndNode && input0(use)!=n ) continue;
                seen.put(use,Boolean.TRUE);
                nodes.add(use);
            }
        }
        return orderNodes(nodes);
    }

    static ArrayList<Node> orderNodes(ArrayList<Node> nodes) {
        IdentityHashMap<Node,Boolean> selected = new IdentityHashMap<>();
        for( Node n : nodes ) selected.put(n,Boolean.TRUE);
        IdentityHashMap<Node,ArrayList<Node>> members = new IdentityHashMap<>();
        ArrayList<Node> groups = new ArrayList<>();
        for( Node n : nodes ) {
            Node g = group(n,selected);
            if( !members.containsKey(g) ) {
                members.put(g,new ArrayList<>());
                groups.add(g);
            }
            if( n!=g ) members.get(g).add(n);
        }
        // Reconstruct uses from inputs: incomplete reverse edges must not
        // hide nodes. Ignore loop backedges for ordering, but still print them.
        IdentityHashMap<Node,ArrayList<Node>> uses = new IdentityHashMap<>();
        IdentityHashMap<Node,Boolean> incoming = new IdentityHashMap<>();
        for( Node g : groups ) uses.put(g,new ArrayList<>());
        for( Node n : nodes ) {
            if( n._inputs==null ) continue;
            Node to = group(n,selected);
            for( int i=0; i<n._inputs.size(); i++ ) {
                Node def = n._inputs.get(i);
                if( def==null || !selected.containsKey(def) ) continue;
                if( i>=2 && (n instanceof LoopNode ||
                             n instanceof PhiNode && input0(n) instanceof LoopNode) ) continue;
                if( n instanceof FunNode && def instanceof CallNode ||
                    n instanceof CallEndNode && def instanceof ReturnNode ||
                    n instanceof ParmNode && i>0 ) continue;
                Node from = group(def,selected);
                if( from!=to ) {
                    uses.get(from).add(to);
                    incoming.put(to,Boolean.TRUE);
                }
            }
        }
        Comparator<Node> cfgFirst = Comparator
            .comparingInt((Node n) -> cfg(n) ? 0 : 1).thenComparingInt(n -> n._nid);
        for( ArrayList<Node> us : uses.values() ) us.sort(cfgFirst);
        groups.sort(Comparator.comparingInt(n -> n._nid));
        IdentityHashMap<Node,Boolean> seen = new IdentityHashMap<>();
        ArrayList<Node> post = new ArrayList<>();
        for( Node g : groups )
            if( !incoming.containsKey(g) ) walk(g,uses,seen,post);
        // A broken graph may consist entirely of cycles, with no roots.
        for( Node g : groups ) walk(g,uses,seen,post);
        Collections.reverse(post);
        post=nestLoops(nodes,groups,selected,uses,post);
        ArrayList<Node> result = new ArrayList<>();
        for( Node g : post ) {
            result.add(g);
            sortChildren(members.get(g));
            result.addAll(members.get(g));
        }
        return result;
    }

    // Projection lists are short. Select the next idx directly, including
    // -1, sparse indices, and deterministic nid order for duplicate indices.
    static void sortChildren(ArrayList<Node> children) {
        for( int i=0; i<children.size(); i++ ) {
            int best=i;
            for( int j=i+1; j<children.size(); j++ ) {
                Node a=children.get(j), b=children.get(best);
                if( order(a)<order(b) || order(a)==order(b) && a._nid<b._nid ) best=j;
            }
            Collections.swap(children,i,best);
        }
    }

    static ArrayList<Node> children(Node head) {
        ArrayList<Node> children = new ArrayList<>();
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if( head!=null && head._outputs!=null ) for( Node use : head._outputs )
            if( child(use) && input0(use)==head && seen.add(use) ) children.add(use);
        sortChildren(children);
        return children;
    }

    // Printer-local natural loops. Never ask idom()/loopDepth() to build or
    // repair compiler metadata while inspecting a partly constructed graph.
    private static final class Region {
        final Node head;
        final Set<Node> cfg = Collections.newSetFromMap(new IdentityHashMap<>());
        Region parent;
        final ArrayList<Node> nodes = new ArrayList<>();
        ArrayList<Node> rpo;
        Region(Node head) { this.head = head; }
    }

    private record Use(Node node, int input) {}

    private static ArrayList<Node> predecessors(Node n) {
        ArrayList<Node> preds = new ArrayList<>();
        if( !cfg(n) || n._inputs==null || (n instanceof StartNode || n instanceof StopNode || n instanceof FunNode || n instanceof StartCUNode || n instanceof StopCUNode) ) return preds;
        if( n instanceof RegionNode ) {
            for( int i=1; i<n._inputs.size(); i++ )
                if( cfg(n._inputs.get(i)) ) preds.add(n._inputs.get(i));
        } else if( cfg(input0(n)) ) preds.add(input0(n));
        return preds;
    }

    private static Region common(Region a, Region b) {
        Set<Region> path = Collections.newSetFromMap(new IdentityHashMap<>());
        for( Region r=a; r!=null; r=r.parent ) path.add(r);
        for( Region r=b; r!=null; r=r.parent ) if( path.contains(r) ) return r;
        throw new AssertionError("disjoint printer regions");
    }

    private static Region enclosing(Node cfg, ArrayList<Region> loops, Region root) {
        Region best = root;
        for( Region loop : loops )
            if( loop.cfg.contains(cfg) && (best==root || loop.cfg.size()<best.cfg.size()) ) best=loop;
        return best;
    }

    // A nested loop is one vertex in its parent's RPO.
    private static Node unit(Node n, Region scope, IdentityHashMap<Node,Region> owner) {
        Region r = owner.get(n);
        if( r==scope ) return n;
        while( r!=null && r.parent!=scope ) r=r.parent;
        return r==null ? null : r.head;
    }

    private static ArrayList<Node> nestLoops(ArrayList<Node> nodes, ArrayList<Node> groups,
                                            IdentityHashMap<Node,Boolean> selected,
                                            IdentityHashMap<Node,ArrayList<Node>> uses,
                                            ArrayList<Node> post) {
        Region root = new Region(null);
        ArrayList<Region> loops = new ArrayList<>();
        for( Node n : groups ) {
            if( !(n instanceof LoopNode) || n instanceof StartNode ) continue;
            Region loop = new Region(n);
            loop.cfg.add(n);
            ArrayList<Node> work = new ArrayList<>();
            Node back = n._inputs==null || n._inputs.size()<3 ? null : n._inputs.get(2);
            Node entry = n._inputs==null || n._inputs.size()<2 ? null : n._inputs.get(1);
            if( cfg(back) && selected.containsKey(back) && loop.cfg.add(back) ) work.add(back);
            boolean closed = back==n;
            for( int i=0; i<work.size(); i++ )
                for( Node pred : predecessors(work.get(i)) ) {
                    if( pred==n ) closed=true;
                    if( pred!=entry && selected.containsKey(pred) && loop.cfg.add(pred) ) work.add(pred);
                }
            // An incomplete or malformed backedge is still a printable header.
            if( !closed ) { loop.cfg.clear(); loop.cfg.add(n); }
            loops.add(loop);
        }
        // The same hierarchy applies to node/depth slices which contain
        // several linked functions, as well as the whole-program view.
        IdentityHashMap<Node,ArrayList<Node>> cfgUses = new IdentityHashMap<>();
        for( Node n : nodes ) if( cfg(n) ) cfgUses.put(n,new ArrayList<>());
        for( Node n : nodes ) if( cfg(n) )
            for( Node pred : predecessors(n) )
                if( cfgUses.containsKey(pred) ) cfgUses.get(pred).add(n);
        for( Node n : groups ) if( n instanceof FunNode ) {
            Region fun = new Region(n);
            ArrayList<Node> work = new ArrayList<>();
            fun.cfg.add(n);
            work.add(n);
            for( int i=0; i<work.size(); i++ )
                for( Node use : cfgUses.get(work.get(i)) )
                    if( fun.cfg.add(use) ) work.add(use);
            loops.add(fun);
        }
        for( Node n : groups ) if( n instanceof StartCUNode ) {
            Region cu = new Region(n);
            cu.cfg.add(n);
            for( Region fun : loops )
                if( fun.head instanceof FunNode f && f._compunit!=null && f._compunit._start==n ) {
                    cu.cfg.addAll(fun.cfg);
                    if( selected.containsKey(f._compunit._stop) ) cu.cfg.add(f._compunit._stop);
                }
            loops.add(cu);
        }
        if( loops.isEmpty() ) return post;
        for( Region loop : loops ) {
            loop.parent=root;
            for( Region outer : loops )
                if( outer.cfg.size()>loop.cfg.size() && outer.cfg.containsAll(loop.cfg) &&
                    (loop.parent==root || outer.cfg.size()<loop.parent.cfg.size()) ) loop.parent=outer;
        }

        IdentityHashMap<Node,Region> owner = new IdentityHashMap<>();
        IdentityHashMap<Node,ArrayList<Use>> users = new IdentityHashMap<>();
        for( Node g : groups ) {
            users.put(g,new ArrayList<>());
            if( cfg(g) ) owner.put(g,enclosing(g,loops,root));
            else if( cfg(input0(g)) ) owner.put(g,enclosing(input0(g),loops,root));
            else if( g instanceof ConstantNode ) owner.put(g,root);
        }
        for( Node use : nodes ) if( use._inputs!=null )
            for( int i=0; i<use._inputs.size(); i++ ) {
                Node def = use._inputs.get(i);
                if( use instanceof ParmNode && i>0 ) continue; // Caller linkage, not callee data.
                if( def!=null && selected.containsKey(def) && group(def,selected)!=group(use,selected) )
                    users.get(group(def,selected)).add(new Use(use,i));
            }
        // Floating definitions follow the common enclosing region of their
        // uses. A loop Phi's entry value belongs outside that loop; its
        // backedge value belongs inside. Pinned nodes keep their actual control.
        boolean progress;
        do {
            progress=false;
            for( int i=post.size()-1; i>=0; i-- ) {
                Node g = post.get(i);
                if( owner.containsKey(g) ) continue;
                Region place=null;
                boolean ready=true;
                for( Use use : users.get(g) ) {
                    Region r=owner.get(group(use.node(),selected));
                    if( r==null ) { ready=false; break; }
                    if( use.node() instanceof PhiNode && input0(use.node()) instanceof LoopNode &&
                        r.head==input0(use.node()) && use.input()==1 ) r=r.parent;
                    place=place==null ? r : common(place,r);
                }
                if( ready ) { owner.put(g,place==null ? root : place); progress=true; }
            }
        } while( progress );
        // Unresolved data cycles do not prevent the rest of the graph printing.
        for( Node g : groups ) owner.putIfAbsent(g,root);
        for( Region loop : loops ) owner.put(loop.head,loop);
        for( Node g : groups ) owner.get(g).nodes.add(g);
        for( Region loop : loops ) loop.parent.nodes.add(loop.head);

        ArrayList<Region> regions = new ArrayList<>(loops);
        regions.add(root);
        for( Region scope : regions ) {
            IdentityHashMap<Node,ArrayList<Node>> edges = new IdentityHashMap<>();
            IdentityHashMap<Node,Boolean> incoming = new IdentityHashMap<>();
            for( Node n : scope.nodes ) edges.put(n,new ArrayList<>());
            for( Node from : groups ) {
                Node a=unit(from,scope,owner);
                if( a==null ) continue;
                for( Node to : uses.get(from) ) {
                    Node b=unit(to,scope,owner);
                    if( b!=null && a!=b ) { edges.get(a).add(b); incoming.put(b,Boolean.TRUE); }
                }
            }
            scope.nodes.sort(Comparator.comparingInt(n -> n._nid));
            for( ArrayList<Node> es : edges.values() )
                es.sort(Comparator.comparingInt((Node n) -> cfg(n) ? 0 : 1).thenComparingInt(n -> n._nid));
            IdentityHashMap<Node,Boolean> seen = new IdentityHashMap<>();
            scope.rpo = new ArrayList<>();
            if( scope.head!=null ) walk(scope.head,edges,seen,scope.rpo);
            for( Node n : scope.nodes ) if( !incoming.containsKey(n) ) walk(n,edges,seen,scope.rpo);
            for( Node n : scope.nodes ) walk(n,edges,seen,scope.rpo);
            Collections.reverse(scope.rpo);
            // Disconnected floating definitions still follow the loop header.
            if( scope.head!=null ) {
                scope.rpo.removeIf(n -> n==scope.head);
                scope.rpo.add(0,scope.head);
            }
        }
        // Expand nested loop vertices without recursive Java calls.
        IdentityHashMap<Node,Region> byHead = new IdentityHashMap<>();
        for( Region loop : loops ) byHead.put(loop.head,loop);
        ArrayList<Node> result = new ArrayList<>();
        ArrayDeque<Region> scopes = new ArrayDeque<>();
        ArrayDeque<Iterator<Node>> positions = new ArrayDeque<>();
        scopes.push(root);
        positions.push(root.rpo.iterator());
        while( !scopes.isEmpty() ) {
            if( !positions.peek().hasNext() ) { scopes.pop(); positions.pop(); continue; }
            Node n=positions.peek().next();
            Region nested=byHead.get(n);
            if( nested!=null && nested.parent==scopes.peek() ) {
                scopes.push(nested);
                positions.push(nested.rpo.iterator());
            } else result.add(n);
        }
        return result;
    }

    private static void add(Node n, int d, ArrayList<Node> nodes,
                            IdentityHashMap<Node,Integer> distance) {
        if( n!=null && !distance.containsKey(n) ) {
            distance.put(n,d);
            nodes.add(n);
        }
    }

    // An explicit DFS stack handles long chains as well as malformed cycles.
    private static void walk(Node root, IdentityHashMap<Node,ArrayList<Node>> uses,
                             IdentityHashMap<Node,Boolean> seen, ArrayList<Node> post) {
        if( seen.put(root,Boolean.TRUE)!=null ) return;
        ArrayDeque<Node> stack = new ArrayDeque<>();
        ArrayDeque<Iterator<Node>> edges = new ArrayDeque<>();
        stack.push(root);
        edges.push(uses.get(root).iterator());
        while( !stack.isEmpty() ) {
            if( edges.peek().hasNext() ) {
                Node use = edges.peek().next();
                if( seen.put(use,Boolean.TRUE)==null ) {
                    stack.push(use);
                    edges.push(uses.get(use).iterator());
                }
            } else {
                post.add(stack.pop());
                edges.pop();
            }
        }
    }
}
