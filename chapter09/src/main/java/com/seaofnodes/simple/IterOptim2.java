package com.seaofnodes.simple;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StopNode;

import java.util.ArrayList;
import java.util.BitSet;

public class IterOptim2 {

    public static ArrayList<Node> getRPO(StopNode stop) {
        // First, a Breadth First Search at a fixed depth.
        IRPrinter.BFS bfs = new IRPrinter.BFS(stop,99);
        // Convert just that set to a post-order
        ArrayList<Node> rpos = new ArrayList<>();
        BitSet visit = new BitSet();
        for( int i=bfs._lim; i< bfs._bfs.size(); i++ )
            IRPrinter.postOrd( bfs._bfs.get(i), rpos, visit, bfs._bs);
        return rpos;
    }

    public StopNode iterate(StopNode stop, boolean show) {
        ArrayList<Node> rpos = getRPO(stop);
        for (int iter = 0; iter < 3; iter++) {
            boolean progress = false;
            for (int i = rpos.size() - 1; i >= 0; i--) {
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
            if (progress) rpos = getRPO(stop);
            else          break;
        }

        if( show )
            System.out.println(new GraphVisualizer().generateDotOutput(stop,null,null));
        return stop;
    }
}
