package com.seaofnodes.graph;

/** Optional, compilation-owned observer. Callbacks read the IR without changing it. */
public abstract class GraphObserver<N> {
    public abstract void before(N node);

    /**
     * repl is null for no progress. applied means substitution and cleanup are
     * complete; otherwise repl is being returned for the caller to attach.
     * Calls nest when a rewrite invokes another peephole.
     */
    public abstract void after(N node, N repl, boolean applied);

    /** A completed compiler phase. */
    public abstract void phase(String phase);

    /** A rule consulted a dependency, even if already registered or adjacent. */
    public void dep(N node, N def) {}
}
