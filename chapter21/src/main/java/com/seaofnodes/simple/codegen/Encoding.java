package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;


/**
 *  Instruction encodings
 *
 *  This class holds the encoding bits, plus the relocation information needed
 *  to move the code to a non-zero offset, or write to an external file (ELF).
 *
 *  There are also a bunch of generic utilities for managing bits and bytes
 *  common in all encodings.
 */
public class Encoding {

    // Top-level program graph structure
    final CodeGen _code;

    // Instruction bytes.  The registers are encoded already.  Relocatable data
    // is in a fixed format depending on the kind of relocation.

    // - RIP-relative offsets into the same chunk are just encoded with their
    //   check relative offsets; no relocation info is required.

    // - Fixed address targets in the same encoding will be correct for a zero
    //   base; moving the entire encoding requires adding the new base address.

    // - RIP-relative to external chunks have a zero offset; the matching
    //   relocation info will be used to patch the correct value.

    final ByteArrayOutputStream _bits;


    Encoding( CodeGen code ) {
        _code = code;
        _bits = new ByteArrayOutputStream();
    }


    void encode() {
        // Basic block layout.  Now that RegAlloc is finished, no more spill
        // code will appear.  We can change our BB layout from RPO to something
        // that minimizes actual branches, takes advantage of fall-through
        // edges, and tries to help simple branch predictions: back branches
        // are predicted taken, forward not-taken.




        // Write bits in order
        for( CFGNode bb : _code._cfg )
            for( Node n : bb._outputs )
                if( n instanceof MachNode mach )
                    throw Utils.TODO();
    }

}
