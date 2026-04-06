package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.codegen.CodeGen;
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

import static com.seaofnodes.simple.codegen.CodeGen.CODE;

// Parse a given file, and all its dependencies
public abstract class ParseAll {

    // Map from fully qualified slashed name (based from module root) to an
    // Compilation Unit.
    private static final HashMap<String,CompUnit> CUNITMAP = new HashMap<>();

    // Worklist of symbols to search and files to parse
    private static final Ary<CompUnit> WORK = new Ary<>(CompUnit.class);

    public static void reset() { CUNITMAP.clear(); WORK.clear(); }

    // Return a compilation unit (cached or new).  If new, it does not neccesarily
    // need to be compiled.
    public static CompUnit makeCUnit( CompUnit par, String name ) throws IOException {
        return makeCUnit(par,name,false);
    }

    // Discovered source file; if reference already exists return null and no
    // further work is needed.  If absent, go ahead and make and return a nested
    // compilation unit, which will need to be compiled in the same compilation unit.
    public static CompUnit makeCUnitIfAbsent( CompUnit par, String name ) throws IOException {
        return makeCUnit(par,name,true);
    }

    private static CompUnit makeCUnit( CompUnit par, String name, boolean absent ) throws IOException {
        String fname = par==null ? name : par._fname + "/" + name;
        CompUnit cunit = CUNITMAP.get(fname);
        if( cunit != null ) return absent ? null : cunit;
        String cname = par==null ? name : par._cname + "." + name;
        CUNITMAP.put(fname, cunit = new CompUnit(par,fname,cname,name));
        return cunit;
    }

    // Parse a top-level string, used for e.g. testing
    static void parseSource( String src ) {
        CompUnit cunit = new CompUnit(src);
        CUNITMAP.put(cunit._fname,cunit);
        parseAll(cunit);
    }

    // module_root/.../src.smp is source file name.
    static void parsePath(String fname) {
        // Initial split of top-level file name
        int lastSlash = fname.lastIndexOf('/');
        String fpath = lastSlash == -1 ? null  : fname.substring(0, lastSlash);
        String name  = lastSlash == -1 ? fname : fname.substring(lastSlash + 1);

        if( fpath!=null )
            throw new RuntimeException("need to build up a compilation-unit-path");
        CompUnit cunit;
        try {
            cunit = makeCUnit(null,name);
        } catch( IOException ioe ) {
            throw new RuntimeException(ioe);
        }

        // A first compilation unit to prime the pump
        parseAll(cunit);
    }

    private static void parseAll(CompUnit cunit) {
        WORK.add(cunit);
        // Work through all unparsed compilation units
        while( !WORK.isEmpty() )
            parseOne( WORK.pop() );

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
        CODE._stop.walk( (Node n) -> {
                n.upgradeType(Parser.TYPES);
                if( n instanceof FunNode fun ) {
                    CODE.add(n); // Recheck after parse stops making new function calls
                    // Linked call sites might now inline, need to recheck
                    for( Node cend : fun.ret().outs() )
                        if( cend instanceof CallEndNode )
                            CODE.add(cend);
                }
                return null;
            } );
    }

    // Parse one Simple source code file.  Add all the FRefs produced to the worklist.
    private static void parseOne( CompUnit cunit ) {
        // Nothing to do
        if( cunit._src==null || cunit._src.isEmpty() )
            return;

        // Parse a source file, return missing external references
        Ary<FRefNode> frefs = CODE.P.parse(cunit);

        // Iteratively run peepholes.  Besides doing needed optimization
        // this might also kill some FRefs.
        CODE._stop.peephole();
        CODE._iter.iterate(CODE);

        // Find all missing external references, or complain.  This can trigger more parses.
        while( !frefs.isEmpty() ) {
            FRefNode fref = frefs.pop();
            // Symbol not found in source code (or we would not be here).
            // Search the module for the fref
            try {
                CompUnit xcunit = findCUnitModule(cunit,fref._name);
                if( xcunit != null ) {
                    // Replace the FRef with the discovered class pointer.
                    // Class name:
                    String clzName = Parser.addClzPrefix(xcunit._cname);
                    // Class pointer:
                    TypeMemPtr clz = TypeMemPtr.make(TypeStruct.make(clzName,true));
                    // Class pointer constant:
                    ConstantNode clzCon = CODE.con(clz);
                    // Replace and optimize
                    fref.addDef(clzCon);
                    CODE.add(fref);

                    // Module search failed, now try external paths
                } else if( (xcunit = findCUnitExternal( fref._name ) ) != null ) {
                    // fref.addDef(xcunit.extern);
                    throw Utils.TODO();
                } else {
                    throw new RuntimeException("Undefined name '" + fref._name +"'");
                }
            } catch( IOException ioe ) {
                throw new RuntimeException(ioe);
            }
        }
    }


    // Find the symbol in module, starting from cunit.  This searches up the nested
    // class chain and sideways 1 step, so that any prefix of the class chain can
    // be skipped as a shortcut.  Symbol can have dotted names, which then go
    // down into classes.  e.g.
    //   A/X/Y exists.
    //   A/B contains X.Y, which is a shortcut for A.X.Y

    // However if A/B contains "Y", the search would be for B.Y then A.Y both of
    // which do not exist.
    private static CompUnit findCUnitModule( CompUnit cunit, String symbol ) throws IOException {
        if( cunit == null )       // Ran off top of module scope?
            return null;        // Failed search

        // Check self
        if( cunit._name.equals(symbol) )
            return cunit;

        // If symbol is itself nested (has embedded dots), move the leading part
        // over to the CompUnit path.  So e.g.:
        //   CompUnit{A.B}, Symbol="X.Y"
        // becomes
        //   CompUnit{A.B.X}, Symbol="Y"
        int dotIdx = symbol.indexOf(".");
        if( dotIdx != -1 ) {
            String pre = symbol.substring(0,dotIdx);
            String rest = symbol.substring(dotIdx+1,symbol.length());
            String fname = cunit._fname + "/" + pre;
            File f = new File( CODE._modDir + "/" + fname + ".smp" );
            if( f.exists() ) {
                CompUnit sub = makeCUnit(cunit,pre);
                // Note that "sub" itself is not put on the to-be-parsed-list.  We're
                // referring to a child class and do not need to parse the middles.
                CompUnit dcunit = findCUnitModule(sub,rest);
                if( dcunit != null )
                    return dcunit; // Nested dot search found cunit
            }
        }

        // Look in mod / fname / name / symbol.smp
        String fname = cunit._fname + "/" + symbol;
        File f = new File( CODE._modDir + "/" + fname + ".smp" );
        if( f.exists() ) {
            CompUnit sub = makeCUnitIfAbsent(cunit,symbol);
            // If not seen before or out of date, parse the new nested cunit
            if( sub!=null )
                WORK.add(sub);
            return sub;
        }

        // Look up the scope
        return findCUnitModule( cunit._par, symbol );
    }

    // Search external references only.  This should go deep on well known
    // container classes (tar, zip) and needs check for all the symbols in the
    // file, not just the file name (which is only checked here).
    private static CompUnit findCUnitExternal( String symbol ) {
        if( symbol.contains( "." ) )
            throw new RuntimeException("Need to handle nested external symbols");

        // Already found external symbol.  Expect lots of lookups on common symbols
        // like malloc/stdin/write
        CompUnit cunit = CUNITMAP.get(symbol);
        if( cunit != null )
            return cunit;

        // For now only look in file names, recursively in directories.
        // TODO: search tar files, zip files, contents of .obj files.
        ExternNode ext = CODE.findExternal(symbol);
        if( ext == null )
            return null;             // Failed to find in extern libraries
        // Add external symbol mapping
        CUNITMAP.put( symbol, cunit = new CompUnit(ext,symbol) );
        return cunit;
    }

    //private static File extSearch( File f, String symbol ) {
    //    File x;
    //    if( f.isDirectory() ) {
    //        for( File f2 : f.listFiles() )
    //            if( (x = extSearch(f2, symbol)) != null )
    //                return x;
    //    } else if( f.getName().equals(symbol+".obj") )
    //        return f;
    //    else if( f.getName().endsWith(".tar") )
    //        throw new RuntimeException("search tar for name");
    //    return null;
    //}

}
