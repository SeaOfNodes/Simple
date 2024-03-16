package com.seaofnodes.simple.type;

/**
 * Common interface for memory types that need to provide aliasing info.
 */
public interface AliasSource {
    int alias();
    String aliasName();
}
