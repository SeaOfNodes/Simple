package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.TypeInteger;

public class ModNode extends ArithNode {
    public ModNode(Node lhs, Node rhs) { super(null,lhs,rhs); }
    public ModNode(Node lhs, Node rhs, byte mode) { super(null,lhs,rhs,mode); }
    @Override public Tag serialTag() { return Tag.Mod; }
    @Override public String op() { return "%"; }
    @Override boolean allowFloat() { return true; }

    @Override long doOp(long x, long y) { return y==0 ? 0 : x%y; }
    @Override double doOp(double x, double y) { return x%y; }
    @Override TypeInteger doOp(TypeInteger x, TypeInteger y) { return TypeInteger.BOT; }
    @Override Node copy(Node lhs, Node rhs) { return new ModNode(lhs,rhs,_mode); }
}
