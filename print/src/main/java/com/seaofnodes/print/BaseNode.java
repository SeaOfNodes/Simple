package com.seaofnodes.print;

/** Common read-only node view for debug printing and graph capture. */
public abstract class BaseNode<N extends BaseNode<N>> {
    // Allocated by the compiler. Copies receive a new ID from that same allocator.
    public int _nid;
    protected BaseNode(int nid) { _nid=nid; }

    public abstract N in(int i);
    public abstract int nIns();
    public abstract N out(int i);
    public abstract int nOuts();
    public int nDeps() { return 0; }
    public N dep(int i) { throw new IndexOutOfBoundsException(i); }

    public String label() { return getClass().getSimpleName(); }
    public String typeName() { return null; }
    public boolean isDead() { return false; }
    public boolean isCFG() { return false; }
    public String glabel() { return label(); }
    public String comment() { return null; }
    public final String uniqueName() { return label()+_nid; }

    // %0, %1, ... name input slots; %% prints a literal percent sign.
    protected String format() { return null; }
    protected ExprPrinter<N> _print1(ExprPrinter<N> p) {
        String fmt=format();
        return fmt==null ? p.p(label()) : p.prt(self(),fmt);
    }
    // Short constants return null to expand their value on each appearance.
    protected String repeatName() { return label(); }

    @SuppressWarnings("unchecked")
    private N self() { return (N)this; }
    public final String print() { return ExprPrinter.print(self()); }
    @Override public final String toString() { return print(); }
}
