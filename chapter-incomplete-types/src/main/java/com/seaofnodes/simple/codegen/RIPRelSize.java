package com.seaofnodes.simple.codegen;

// This Node rip-relative encodings has sizes that vary by delta
public interface RIPRelSize {
    // delta is the distance from the opcode *start* to the target start.  Each
    // hardware target adjusts as needed, X86 measures from the opcode *end*
    // and small jumps are 2 bytes, so they'll need to subtract 2.
    byte encSize(int delta);

    // Patch full encoding in.  Used for both short and long forms.  delta
    // again is from the opcode *start*.
    void patch( Encoding enc, int opStart, int opLen, int delta );

}
