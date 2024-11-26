package com.seaofnodes.simple.node;
import java.util.*;
import static org.junit.Assert.*;

// Inspect raw fields: the regression must not itself use lazy accessors.
public class PrintTestSupport {
    public static void unchanged(Node root, Runnable print) throws Exception {
        var hash = Node.class.getDeclaredField("_hash");
        hash.setAccessible(true);
        var nodes = new ArrayList<Node>();
        var seen = new IdentityHashMap<Node,Boolean>();
        nodes.add(root);
        seen.put(root,true);
        for( int i=0; i<nodes.size(); i++ ) {
            Node n = nodes.get(i);
            for( Node def : n._inputs )
                if( def!=null && seen.put(def,true)==null ) nodes.add(def);
            for( Node use : n._outputs )
                if( use!=null && seen.put(use,true)==null ) nodes.add(use);
        }
        var ins = new IdentityHashMap<Node,Node[]>();
        var outs = new IdentityHashMap<Node,Node[]>();
        var hashes = new IdentityHashMap<Node,Integer>();
        var versions = new IdentityHashMap<Node,Integer>();
        var depths = new IdentityHashMap<Node,Integer>();
        for( Node n : nodes ) {
            ins.put(n,n._inputs.toArray(new Node[0]));
            outs.put(n,n._outputs.toArray(new Node[0]));
            hashes.put(n,hash.getInt(n));
            if( n instanceof CFGNode cfg ) {
                depths.put(n,(int)cfg._idepth);
                versions.put(n,(int)cfg._idepthVersion);
            }
        }
        print.run();
        for( Node n : nodes ) {
            assertEquals("hash of node "+n._nid,(int)hashes.get(n),hash.getInt(n));
            if( n instanceof CFGNode cfg ) {
                assertEquals("depth of node "+n._nid,(int)depths.get(n),cfg._idepth);
                assertEquals("depth version of node "+n._nid,(int)versions.get(n),cfg._idepthVersion);
            }
            assertEquals(ins.get(n).length,n.nIns());
            assertEquals(outs.get(n).length,n.nOuts());
            for( int i=0; i<n.nIns(); i++ ) assertSame(ins.get(n)[i],n.in(i));
            for( int i=0; i<n.nOuts(); i++ ) assertSame(outs.get(n)[i],n._outputs.get(i));
        }
    }
}
