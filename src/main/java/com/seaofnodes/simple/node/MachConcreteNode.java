package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.Utils;
import java.io.ByteArrayOutputStream;

// Generic machine-specific class, has a few Node implementations that have to
// exist (abstract) but are not useful past the optimizer.
public abstract class MachConcreteNode extends Node implements MachNode{

    public MachConcreteNode(Node node) { super(node); }
    public MachConcreteNode(Node[]nodes) { super(nodes); }

    @Override public String label() { return op(); }
    @Override public Type compute () { throw Utils.TODO(); }
    @Override public Node idealize() { throw Utils.TODO(); }

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        p.open().p(op()).p(",");
        for( int i=1; i<nIns(); i++ )
            (in(i)==null ? p.p(" ---") :  p.n(in(i))).p(",");
        return p.unchar(',').close();
    }

}
