package com.seaofnodes.print;

import java.util.ArrayList;

/** Machine facts for a listing, independent of traversal and column formatting. */
public abstract class AssemblyAdapter<N extends BaseNode<N>> {
    public final IRAdapter<N> ir;
    protected AssemblyAdapter(IRAdapter<N> ir) { this.ir=ir; }
    public abstract ArrayList<N> blocks();
    public abstract String op(N n);
    public abstract String operands(N n);
    public abstract String register(N n);
    public String comment(N n) { return null; }
    public String phiName(N n) { return ir.label(n); }
    public boolean hidden(N n) { return false; }
    public boolean labelBlock(N n) { return true; }
    public boolean postAlloc() { return false; }
    public boolean encoded() { return false; }
    public boolean prologue(N fun) { return false; }
    public int defaultSize() { return 4; }
    public boolean littleEndian() { return true; }
    public int offset(N n) { return -1; }
    public int size(N n) { return 1; }
    public byte[] bytes() { return null; }
    public ArrayList<ASMPrinter.Pool> pools(int codeEnd) { return new ArrayList<>(); }
}
