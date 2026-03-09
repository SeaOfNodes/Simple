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

    public static void parseAll( CodeGen code, String srcName, String src ) {
        FRefNode fref = new FRefNode(srcName,src,null);
        Ary<FRefNode> frefs = new Ary<>(new FRefNode[]{fref});

        // Repeat as we find source files needing parsing
        while( (fref=frefs.pop()) != null ) {

            if( fref.isDead() || fref.nIns()>1 ) {
                //frefs.del(i--); // Remove from worklist
                //continue;
                throw Utils.TODO("untested");
            }

            // Attempt to find external name
            String xname = Parser.addClzPrefix(fref._name);
            ExternNode ext = code.findExternal(xname);
            String srcName = code.findSource(fref._name);

                // If  no src and  no ext, throw error.
                // If  no src and yes ext, resolve fref.
                // If yes src and  no ext, compile src into ext and resolve
                // If yes src and yes ext -
                //     - Check dependencies; compile or use
                if( srcName == null ) {
                    if( ext == null ) {
                        throw Parser.error("Undefined name '" + fref._name + "'",fref._loc);
                    } else {
                        // Resolve the FRefNode by plugging in its definition
                        fref.setDef(1, ext);
                        code.add(fref);
                        frefs.del(i--); // Remove from worklist
                        done = false;
                    }
                } else {
                    if( ext == null ) {
                        // TODO: Record dependency of srcName into current compile
                        throw Utils.TODO("Compile missing ELF");
                    } else {
                        throw Utils.TODO("Check dates and compile or link");
                    }
                }





            // Parse a single top-level file.
            code.P.parse(fref._srcName,fref._src,frefs);

            // Iteratively run peepholes.  Besides doing needed optimization
            // this might also kill some FRefs.
            code._stop.peephole();
            code._iter.iterate(code);

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
