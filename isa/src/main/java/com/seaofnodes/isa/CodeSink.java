package com.seaofnodes.isa;

/** Instruction bytes go straight into the chapter's code buffer. */
public interface CodeSink {
    CodeSink add1(int value);
    default CodeSink add2(int value) { return add1(value).add1(value>>8); }
    default CodeSink add4(int value) { return add2(value).add2(value>>16); }
    default CodeSink add8(long value) { return add4((int)value).add4((int)(value>>32)); }
}
