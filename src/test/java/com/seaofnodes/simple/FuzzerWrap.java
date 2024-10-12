package com.seaofnodes.simple;

import com.seaofnodes.simple.fuzzer.Fuzzer;
import java.util.Random;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * To use the fuzzer run the <code>fuzzPeeps</code> methods.
 * This can be done from IntelliJ Gui or remove the `@Ignore` annotation and start the test case from the command line
 * via <code>mvn clean test -Dtest=com.seaofnodes.simple.Fuzzer08Test#fuzzPeeps</code>
 */
public class FuzzerWrap {

    @Ignore
    @Test
    public void fuzzPeeps() {
        var fuzzer = new Fuzzer();
        for (int i=0; i<1000000; i++)
            fuzzer.fuzzPeeps(i);
        assertTrue(fuzzer.noExceptions());
    }


    @Test
    public void fuzzPeepsSmall() {
        Random R = new Random(System.currentTimeMillis());
        var fuzzer = new Fuzzer();
        for (int i=0; i<100; i++)
            fuzzer.fuzzPeeps(R.nextLong());
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
