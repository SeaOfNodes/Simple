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
    // External Reference.
    private static final HashMap<String,ExtRef> REFMAP = new HashMap<>();

    // Worklist of symbols to search and files to parse
    private static final Ary<ExtRef> WORK = new Ary<>(ExtRef.class);


    // Return a reference (cached or new).  If new, it does not neccesarily
    // need to be compiled.
    public static ExtRef makeRef( ExtRef par, String name ) throws IOException {
        return makeRef(par,name,false);
    }

    // Discovered source file; if reference already exists return null and no
    // further work is needed.  If absent, go ahead and make and return a new
    // reference, which will need to be compiled in the same compilation unit.
    public static ExtRef makeRefIfAbsent( ExtRef par, String name ) throws IOException {
        return makeRef(par,name,true);
    }

    private static ExtRef makeRef( ExtRef par, String name, boolean absent ) throws IOException {
        String fname = par==null ? name : par._fname + "/" + name;
        ExtRef ref = REFMAP.get(fname);
        if( ref != null ) return absent ? null : ref;
        String cname = par==null ? name : par._cname + "." + name;
        REFMAP.put(fname, ref = new ExtRef(par,fname,cname,name));
        return ref;
    }

    // External References.  These are mirrored over a file system tree (so
    // form a tree), both the source tree and build/object tree.
    public static class ExtRef {
        final ExtRef _par;          // Parent, e.g. A/B
        final String _fname;        // Full slashed file name starting from module root, e.g. A/B/C
        final public String _cname; // Full dotted class name starting from module root, e.g. A.B.C
        final String _name;         // Base name, e.g. C
        final ExternNode _ext;      // Found in external libs; e.g. libc
        final File _smp;            // Simple source file, e.g. module_root/A/B/C.smp
        final File _obj;            // ELF output file, e.g. build/A/B/C.o
        final public String _src;   // Source code, from file or test case

        Ary<TypeStruct> _clzs;  // List of classes exported into the ELF output
        Ary<FunNode   > _funs;  // List of function entry points exported
        BAOS _serial;           // Serialized IR for this ELF file

        // Discovered source file.  Source is loaded if required to parse, and null
        // otherwise.
        private ExtRef( ExtRef par, String fname, String cname, String name ) throws IOException {
            _par  = par;
            _fname= fname;
            _cname= cname;
            _name = name;
            _ext  = null;
            _smp  = new File( CODE.  _modDir + "/" + fname + ".smp");
            _obj  = new File( CODE._buildDir + "/" + fname + ".o"  );
            _src  = !_obj.exists() || _smp.lastModified() > _obj.lastModified()
                ? new String(Files.readAllBytes(_smp.toPath()))
                : null;
        }

        // Test case.  No source file.  Source code passed in as a String.  Fake
        // class name.
        ExtRef( String src ) {
            _par  = null;
            _name = "Test";
            _fname= _name;
            _cname= _name;
            _ext  = null;
            _smp  = null;
            _obj  = null;
            _src  = src;
        }

        // External ExtRef, pulled in from an external library like libc
        ExtRef( ExternNode ext, String symbol ) {
            _par  = null;
            _name = symbol;
            _fname= _name;
            _cname= null;
            _ext  = ext;
            _smp  = null;
            _obj  = null;
            _src  = null;
        }

        // Add a public class name.  You always get the self-name.  If the
        // source code has a nested class, you can get more.
        public void addClass( TypeStruct clz ) {
            if( _clzs==null ) _clzs = new Ary<>(TypeStruct.class);
            _clzs.add(clz);
        }
        public void addFunction( FunNode fun ) {
            if( _funs==null ) _funs = new Ary<>(FunNode.class);
            _funs.add(fun);
        }

        public void replaceAllFuns( IdentityHashMap<Node,Node> map ) {
            for( int i=0; i<_funs._len; i++ )
                _funs._es[i] = (FunNode)map.get(_funs._es[i]);
        }
        public void replaceAllClzs() {
            for( int i=0; i<_clzs._len; i++ )
                _clzs.set(i, (TypeStruct)Parser.TYPES.get(_clzs.at(i)._name));
        }

        @Override public String toString() {
            String x = "{"+_cname;
            if( _smp!=null && _smp.exists()) x += ",hasSMP";
            if( _obj!=null && _obj.exists()) x += ",hasObj";
            if( _src!=null   ) x += ",hasSrc";
            if( _ext != null ) x += ",isExt";
            x += "}";
            return x;
        }
    }

    // Parse a top-level string, used for e.g. testing
    static void parseSource( String src ) {
        ExtRef ref = new ExtRef(src);
        REFMAP.put(ref._fname,ref);
        parseAll(ref);
    }

    // module_root/.../src.smp is source file name.
    static void parsePath(String fname) {
        // Initial split of top-level file name
        int lastSlash = fname.lastIndexOf('/');
        String fpath = lastSlash == -1 ? null  : fname.substring(0, lastSlash);
        String name  = lastSlash == -1 ? fname : fname.substring(lastSlash + 1);

        if( fpath!=null )
            throw new RuntimeException("need to build up a ref-path");
        ExtRef ref;
        try {
            ref = makeRef(null,name);
        } catch( IOException ioe ) {
            throw new RuntimeException(ioe);
        }

        // A first Ref to prime the pump
        parseAll(ref);
    }

    private static void parseAll(ExtRef ref) {
        WORK.add(ref);
        // Work through all unparsed Refs
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
    private static void parseOne( ExtRef ref ) {
        // Nothing to do
        if( ref._src==null || ref._src.isEmpty() )
            return;

        // Parse a source file, return missing external references.
        Ary<FRefNode> frefs = CODE.P.parse(ref);

        // Iteratively run peepholes.  Besides doing needed optimization
        // this might also kill some FRefs.
        CODE._stop.peephole();
        CODE._iter.iterate(CODE);

        // Find all missing refs, or complain.  This can trigger more parses.
        while( !frefs.isEmpty() ) {
            FRefNode fref = frefs.pop();
            // Symbol not found in ref source code (or we would not be here).
            // Search the module for the fref
            try {
                ExtRef xref = findRefModule(ref,fref._name);
                if( xref != null ) {
                    // Replace the FRef with the discovered class pointer.
                    // Class name:
                    String clzName = Parser.addClzPrefix(xref._cname);
                    // Class pointer:
                    TypeMemPtr clz = TypeMemPtr.make(TypeStruct.make(clzName,true));
                    // Class pointer constant:
                    ConstantNode clzCon = CODE.con(clz);
                    // Replace and optimize
                    fref.addDef(clzCon);
                    CODE.add(fref);

                    // Module search failed, now try external paths
                } else if( (xref = findRefExternal( fref._name ) ) != null ) {
                    // fref.addDef(xref.extern);
                    throw Utils.TODO();
                } else {
                    throw new RuntimeException("Undefined name '" + fref._name +"'");
                }
            } catch( IOException ioe ) {
                throw new RuntimeException(ioe);
            }
        }
    }


    // Find the symbol in module, starting from ref.  This searches up the nested
    // class chain and sideways 1 step, so that any prefix of the class chain can
    // be skipped as a shortcut.  Symbol can have dotted names, which then go
    // down into classes.  e.g.
    //   A/X/Y exists.
    //   A/B contains X.Y, which is a shortcut for A.X.Y

    // However if A/B contains "Y", the search would be for B.Y then A.Y both of
    // which do not exist.
    private static ExtRef findRefModule( ExtRef ref, String symbol ) throws IOException {
        if( ref == null )       // Ran off top of module scope?
            return null;        // Failed search

        // Check self
        if( ref._name.equals(symbol) )
            return ref;

        // If symbol is itself nested (has embedded dots), move the leading part
        // over to the ExtRef path.  So e.g.:
        //   ExtRef{A.B}, Symbol="X.Y"
        // becomes
        //   ExtRef{A.B.X}, Symbol="Y"
        int dotIdx = symbol.indexOf(".");
        if( dotIdx != -1 ) {
            String pre = symbol.substring(0,dotIdx);
            String rest = symbol.substring(dotIdx+1,symbol.length());
            String fname = ref._fname + "/" + pre;
            File f = new File( CODE._modDir + "/" + fname + ".smp" );
            if( f.exists() ) {
                ExtRef sub = makeRef(ref,pre);
                // Note that "sub" itself is not put on the to-be-parsed-list.  We're
                // referring to a child class and do not need to parse the middles.
                ExtRef dref = findRefModule(sub,rest);
                if( dref != null )
                    return dref; // Nested dot search found ref
            }
        }

        // Look in mod / fname / name / symbol.smp
        String fname = ref._fname + "/" + symbol;
        File f = new File( CODE._modDir + "/" + fname + ".smp" );
        if( f.exists() ) {
            ExtRef sub = makeRefIfAbsent(ref,symbol);
            // If not seen before or out of date, parse the new nested ref
            if( sub!=null )
                WORK.add(sub);
            return sub;
        }

        // Look up the scope
        return findRefModule( ref._par, symbol );
    }

    // Search external references only.  This should go deep on well known
    // container classes (tar, zip) and needs check for all the symbols in the
    // file, not just the file name (which is only checked here).
    private static ExtRef findRefExternal( String symbol ) {
        if( symbol.contains( "." ) )
            throw new RuntimeException("Need to handle nested external symbols");

        // Already found external symbol.  Expect lots of lookups on common symbols
        // like malloc/stdin/write
        ExtRef ref = REFMAP.get(symbol);
        if( ref != null )
            return ref;

        // For now only look in file names, recursively in directories.
        // TODO: search tar files, zip files, contents of .obj files.
        ExternNode ext = CODE.findExternal(symbol);
        if( ext == null )
            return null;             // Failed to find in extern libraries
        // Add external symbol mapping
        REFMAP.put( symbol, ref = new ExtRef(ext,symbol) );
        return ref;
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
