package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


// Parse a given file, and all its dependencies
public abstract class ParseAll {

    public static void parseAll( CodeGen code ) {

        // Repeat as we find source files needing parsing
        boolean done = false;
        while( !done ) {
            done = true;

            // Parse a single top-level file.

            // TODO: In a later phase, handle multiple files correctly.
            // For now, we only have one initial source.
            Ary<FRefNode> frefs = code.P.parse();

            code._stop.peephole();

            // Iteratively run peepholes.  Besides doing needed optimization
            // this might also kill some FRefs.
            code._iter.iterate(code);

            // Scan for unresolved forward references.
            for( int i=0; i<frefs._len; i++ ) {
                FRefNode fref = frefs.at(i);
                if( fref.isDead() || fref.nIns()>1 ) {
                    //frefs.del(i--); // Remove from worklist
                    //continue;
                    throw Utils.TODO("untested");
                }
                // Attempt to find external name
                String xname = Parser.addClzPrefix(fref._n._name);
                ExternNode ext = code.findExternal(xname);
                if( ext != null ) {
                    // Resolve the FRefNode by plugging in its definition
                    fref.setDef(1, ext);
                    code.add(fref);
                    frefs.del(i--); // Remove from worklist
                    done = false;
                }
            }

            for( FRefNode fref : frefs )
                throw Parser.error("Undefined name '" + fref._n._name + "'",fref._n._loc);
        }

        // Close over all recursive types, and upgrade TYPES
        Ary<TypeStruct> ary = new Ary<>(TypeStruct.class);
        for( Type t : Parser.TYPES.values() )
            if( t instanceof TypeStruct ts )
                ary.add(ts);
        TypeStruct[] tss = Type.closeOver(ary.asAry(),Parser.TYPES);
        for( TypeStruct ts : tss )
            Parser.TYPES.put(ts._name,ts);

        // Walk over all Nodes, and upgrade the internal constants to the
        // closed-over types.
        code._stop.walk( (Node n) -> {
                n.upgradeType(Parser.TYPES);
                if( n instanceof FunNode fun ) {
                    code.add(n); // Recheck after parse stops making new function calls
                    // Linked call sites might now inline, need to recheck
                    for( Node cend : fun.ret().outs() )
                        if( cend instanceof CallEndNode )
                            code.add(cend);
                }
                return null;
            } );

        // Gather top-level exported symbols
        for( Type t : Parser.TYPES.values() )
            if( t instanceof TypeStruct ts &&!ts.isAry() )
                code._publishSymbols.add(ts);

    }

}
