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

    private static final long[] REGRESSION_SEEDS = {
        375135762521757909L,   // bad LCA
        -148471672577312953L,  // bulk mem
        -5037182906211190034L, // dead Guard control
        -6359653295501938199L, // monotonicity, escape is dead
    };

    private static final long[] OPEN_FAILING_SEEDS = {
    };


    @Test         public void fuzzPeepsRegression  () { fuzzPeepsSeeds(  REGRESSION_SEEDS); }
    @Test @Ignore public void fuzzPeepsOpenFailures() { fuzzPeepsSeeds(OPEN_FAILING_SEEDS); }

    private static void fuzzPeepsSeeds(long... seeds) {
        var fuzzer = new Fuzzer();
        for (long seed : seeds)
            fuzzer.fuzzPeepsRegression(seed);
    }

    @Test
    public void fuzzPeepsRandom() {
        Random R = new Random(System.currentTimeMillis());
        var fuzzer = new Fuzzer();
        for (int i=0; i<100; i++)
            fuzzer.fuzzPeeps(R.nextLong());
        assertTrue(fuzzer.noExceptions());
    }


    @Ignore
    @Test
    public void fuzzPeepsLarge() {
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
