package com.seaofnodes.simple;

import com.seaofnodes.simple.fuzzer.Fuzzer;
import org.junit.Test;
import org.junit.Ignore;


import static org.junit.Assert.assertTrue;

/**
 * To use the fuzzer run the <code>fuzzPeeps</code> methods.
 * This can be done from IntelliJ Gui or remove the `@Ignore` annotation and start the test case from the command line
 * via <code>mvn clean test -Dtest=com.seaofnodes.simple.FuzzerWrap#fuzzPeeps</code>
 */
public class FuzzerWrap {

    @Test
    @Ignore
    public void fuzzPeeps() {
        var fuzzer = new Fuzzer();
        for (int i=0; i<1000000; i++)
            fuzzer.fuzzPeeps(i);
        assertTrue(fuzzer.noExceptions());
    }


    @Test
    @Ignore
    public void fuzzPeepTiming() {
        var fuzzer = new Fuzzer();
        int max_nid=0;
        for (int i=0; i<1000000; i++)
            max_nid = fuzzer.fuzzPeepTiming(i, max_nid);
    }
}
