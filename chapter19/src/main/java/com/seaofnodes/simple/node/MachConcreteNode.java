package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.Utils;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;

// Generic machine-specific class, has a few Node implementations that have to
// exist (abstract) but are not useful past the optimizer.
public abstract class MachConcreteNode extends Node implements MachNode{
    private static final Node[] CTRL = new Node[1];

    public boolean _single;
    public Node _debug;
    public MachConcreteNode(Node node) { super(node); }
    public MachConcreteNode(Node[]nodes) { super(nodes); }
    public MachConcreteNode(MachConcreteNode mach) { super(CTRL); mach._single = _single; }

    @Override public String label() { return op(); }
    @Override public Type compute () { throw Utils.TODO(); }
    @Override public Node idealize() { throw Utils.TODO(); }

    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        // Pop it now and not before GCM
        Node debug = _inputs.last();
        if(!_single) {
            _inputs.pop();
        }
        sb.append("(").append(op()).append(",");
        for( int i=1; i<nIns(); i++ )
            (in(i)==null ? sb.append(" ---") :  in(i)._print0(sb, visited)).append(",");
        sb.setLength(sb.length()-1);
        if(!_single) {
            _inputs.push(debug);
        }

        return sb.append(")");
    }

}
