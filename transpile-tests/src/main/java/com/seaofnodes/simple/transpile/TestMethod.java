package com.seaofnodes.simple.transpile;

import java.util.ArrayList;

public class TestMethod {
    public String name;
    public String parserInput;
    public Arg parserArg;
    // if this is set you can ignore the rest
    public String parseErrorMessage;
    public boolean disablePeephole;
    public boolean showAfterParse;
    public boolean iterate; // "opto"
    public boolean showAfterIterate;
    public boolean irPrinter;
    public boolean irPrinterLLVM;
    public String assertStopEquals;
    public boolean assertStopRetCtrlIsProj;
    public boolean assertStopRetCtrlIsCProj;
    public boolean assertStopRetCtrlIsRegion;
    public final ArrayList<Evaluation> evaluations = new ArrayList<>();

    // chapter18
    public boolean assertCtrlIsFun;
    public final ArrayList<Eval2> evaluations2 = new ArrayList<>();
    public boolean typeCheck;
    public boolean gcm;
    public boolean localSched;

    public sealed interface Arg {
        record IntBot() implements Arg {
        }

        record IntTop() implements Arg {
        }

        record IntConstant(long value) implements Arg {
        }
    }

    /**
     * result is a literal value
     **/
    public record Evaluation(Object result, Long parameter, boolean stringify) {
    }

    public record Eval2(String result, long arg, Integer timeout) {
    }
}
