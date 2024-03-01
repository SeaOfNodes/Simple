package com.seaofnodes.simple;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StopNode;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;

public class IterOptim2 {

    static void dfs(Node root, Consumer<Node> consumer, BitSet visited) {
        visited.set(root._nid);
        /* For each successor node */
        for (int i = 0; i < root.nOuts(); i++) {
            Node S = root.out(i);
            if (S == null)
                continue;
            if (!visited.get(S._nid))
                dfs(S, consumer, visited);
        }
        consumer.accept(root);
    }

    public static List<Node> rpo(Node root) {
        List<Node> nodes = new ArrayList<>();
        // note add below prepends
        dfs(root, (n)->nodes.add(0,n), new BitSet());
        return nodes;
    }

    public StopNode iterate(StopNode stopNode, Node startNode, boolean show) {
        int iter = 0;
        for (;;) {
            List<Node> rpos = rpo(startNode);
            boolean progress = false;
            for (int i = 0; i < rpos.size(); i++) {
                Node n = rpos.get(i);
                if (n.isDead()) continue;
                Node x = n.peepholeOpt();
                if (x != null) {
                    progress = true;
                    if (x != n) n.subsume(x);
                    if (x.isDead()) continue;
                    if (x._type == null) x._type = x.compute();
                }
            }
            if (!progress) break;
        }
        System.out.println("Completed in " + iter + " iterations");
        if( show )
            System.out.println(new GraphVisualizer().generateDotOutput(stopNode,null,null));
        return stopNode;
    }
}
