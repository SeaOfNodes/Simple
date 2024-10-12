package com.seaofnodes.simple;

import com.seaofnodes.simple.fuzzer.Fuzzer;
import java.util.Random;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Normal tests run chapter-local regression seeds. Open failures and exploratory
 * fuzzing are opt-in (run directly in the IDE or temporarily remove Ignore).
 * Seeds depend on this chapter's generator; do not copy them between chapters.
 */
public class FuzzerWrap {

    private static final long[] REGRESSION_SEEDS = {
    };

    private static final long[] OPEN_FAILING_SEEDS = {
    };

    @Test         public void fuzzPeepsRegression  () { fuzzPeepsSeeds(REGRESSION_SEEDS); }
    @Test @Ignore public void fuzzPeepsOpenFailures() { fuzzPeepsSeeds(OPEN_FAILING_SEEDS); }

    private static void fuzzPeepsSeeds(long... seeds) {
        var fuzzer = new Fuzzer();
        for (long seed : seeds)
            fuzzer.fuzzPeepsRegression(seed);
    }

    @Ignore
    @Test
    public void fuzzPeepsLarge() {
        var fuzzer = new Fuzzer();
        for (int i=0; i<1000000; i++)
            fuzzer.fuzzPeeps(i);
        assertTrue(fuzzer.noExceptions());
    }


    @Test @Ignore
    public void fuzzPeepsRandom() {
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
