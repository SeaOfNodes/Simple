package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.type.Type;

public class CalleeSaveNode extends ProjNode implements MachNode {
    final RegMask _mask;
    public CalleeSaveNode(FunNode fun, int reg, String label) {
        super(fun,reg,label);
        fun._outputs.pop();
        int i=0;
        while( fun.out(i) instanceof PhiNode )  i++;
        fun._outputs.insert(this,i);
        _mask = new RegMask(reg);
    }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return _mask; }
    @Override public void encoding( Encoding enc ) { }

    @Override public Type compute() { return Type.BOTTOM; }
    @Override public Node idealize() { return null; }
}
