package com.seaofnodes.simple.sys;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


/** Default included namespaces for Simple.

    You can think of this as {@code #include <stdio.h>} for C or {@code import java.lang.*} for Java.
 */
public abstract class sys {

    public static final String SYS;

    static {
        try {
            SYS = Files.readString(Path.of("src/main/java/com/seaofnodes/simple/sys/sys.smp"));
        }
        catch( Exception e ) {
            throw new RuntimeException(e);
        }
    }

}
