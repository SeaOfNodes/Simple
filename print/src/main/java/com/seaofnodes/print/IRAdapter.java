package com.seaofnodes.print;

/** Read-only facts about a chapter's graph. Needed only for structured IR dumps. */
public abstract class IRAdapter<N extends BaseNode<N>> {
    public enum Kind { DATA, CONSTANT, CTRL, START, STOP, REGION, LOOP, PHI, PARM,
                       MULTI, PROJ, CPROJ, FUN, RETURN, CALL, CALL_END, UNIT }
    public int id(N n) { return n._nid; }
    public String label(N n) { return n.label(); }
    public int nIns(N n) { return n.nIns(); }
    public N in(N n, int i) { return n.in(i); }
    public int nOuts(N n) { return n.nOuts(); }
    public N out(N n, int i) { return n.out(i); }
    public abstract Kind kind(N n);
    public boolean dead(N n) { return n.isDead(); }
    public String type(N n) { String t=n.typeName(); return t==null ? "" : t; }
    public int index(N n) { return id(n); }
    public String signature(N fun) { return label(fun); }
    public String functionName(N fun) { return label(fun); }
    public String inputMark(N n, N def) { return " "; }
    public boolean global(N n) { return kind(n)==Kind.CONSTANT; }
    public int inputColumns() { return 4; }

    public final N input0(N n) { return n == null || nIns(n) == 0 ? null : in(n,0); }
    public final boolean control(N n) {
        if( n == null ) return false;
        return switch(kind(n)) {
            case CTRL, START, STOP, REGION, LOOP, CPROJ, FUN, RETURN, CALL, CALL_END, UNIT -> true;
            default -> false;
        };
    }
    public final boolean phi(N n) { return n != null && (kind(n) == Kind.PHI || kind(n) == Kind.PARM); }
    public final boolean projection(N n) { return n != null && (kind(n) == Kind.PROJ || kind(n) == Kind.CPROJ); }
    public final boolean region(N n) { return n != null && (kind(n) == Kind.REGION || kind(n) == Kind.LOOP || kind(n) == Kind.FUN); }
}
