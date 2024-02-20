package com.seaofnodes.simple;

import com.seaofnodes.simple.fuzzer.Fuzzer;
import org.junit.Ignore;
import org.junit.Test;


import static org.junit.Assert.assertTrue;

/**
 * To use the fuzzer run the <code>fuzzPeeps</code> methods.
 * This can be done from IntelliJ Gui or remove the `@Ignore` annotation and start the test case from the command line
 * via <code>mvn clean test -Dtest=com.seaofnodes.simple.Fuzzer08Test#fuzzPeeps</code>
 */
public class Fuzzer09Test {

    @Test
    @Ignore
    public void fuzzPeeps() {
        var fuzzer = new Fuzzer();
        for (int i=0; i<100000; i++)
            fuzzer.fuzzPeeps(i);
        assertTrue(fuzzer.noExceptions());
    }

}
