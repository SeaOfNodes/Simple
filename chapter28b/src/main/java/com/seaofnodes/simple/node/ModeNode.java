package com.seaofnodes.simple.node;

/**
 * A node whose numeric input family is selected once: unknown, integer, or
 * floating point.  Implementors remain ordinary Nodes; this interface only
 * centralizes the structural mode contract shared by arithmetic, comparisons,
 * and unary minus.
 */
public interface ModeNode {
    byte mode();
    Node setMode(byte mode);
}
