package com.seaofnodes.simple.type;

/**
 * Common interface for memory types that need to provide aliasing info.
 */
public interface AliasSource {
    int alias();

    /**
     * Alias names - requirement is that they start with $ so we can use
     * them as special var names. We rely upon this naming convention to find all the
     * mem slices
     */
    default String aliasName() {
        // The $ prefix is allow use of this as a special var name
        //
        return "$Alias" + alias();
    }
}
