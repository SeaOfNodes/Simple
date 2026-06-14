package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

// Parse a given file, and all its dependencies
public abstract class ParseAll {

    // Worklist of symbols to search and files to parse
    private static final HashSet<CompUnit> WORK = new HashSet<>();

    private static final Ary<CompUnit> NEEDS_LOAD = new Ary<>(CompUnit.class);

    // Return a compilation unit (cached or new).  If new, it does not necessarily
    // need to be compiled.
    public static CompUnit makeCUnit( CodeGen code, CompUnit par, String name ) throws IOException {
        String fname = par==null ? name : par._fname + "/" + name;
        CompUnit cunit = code._compunits.get(fname);
        if( cunit != null ) return cunit;
        String cname = par==null ? name : (par._cname + "." + name).intern();
        code._compunits.put(fname, cunit = new CompUnit(par,fname,cname,name));
        return cunit;
    }

    // Parse a top-level string, used for e.g. testing
    static void parseSource( CodeGen code, String src ) {
        parseSource(code,"Test",src);
    }

    // Parse a top-level string with an explicit compilation-unit name.
    static void parseSource( CodeGen code, String fname, String src ) {
        CompUnit cunit = new CompUnit(fname,src);
        code._compunits.put(cunit._fname,cunit);
        parseAll(code,cunit);
    }

    // module_root/.../src.smp is source file name.
    static void parsePath(CodeGen code, String fname) {
        try {
            // Split and build the parent-path down to the source file.
            String[] ss = fname.split("/");
            CompUnit cu = null;
            for( String s : ss )
                cu = makeCUnit( code, cu, s );
            // Compile this CompUnit
            parseAll(code,cu);
        } catch( IOException ioe ) {
            throw new RuntimeException(ioe);
        }
    }

    private static void parseAll(CodeGen code, CompUnit cunit) {
        WORK.clear();           // Cleanup from prior tests
        NEEDS_LOAD.clear();
        // Prime the pump with the first CompUnit
        WORK.add(cunit);
        // Work through all unparsed compilation units and parse
        while( !WORK.isEmpty() ) {
            CompUnit cu = WORK.iterator().next();
            WORK.remove(cu);
            // No source code?  Means we do not need to parse, but instead need to load the symbols as-if we parsed
            if( cu._src==null || cu._src.isEmpty() )
                loadDeps(code,cu); // Load recursive dependents
            else
                parseOne( code, cu ); // Parse
        }

        // Load all pre-compiled CompUnits
        for( CompUnit cu : NEEDS_LOAD )
            loadCompUnit(code,cu);

        // Many types will have been declared cyclically either internally or
        // across multiple compilation units.

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
                // If function types have sharpened, we need to revisit inline
                // decisions.
                if( n instanceof FunNode fun ) {
                    code.add(n); // Recheck after parse stops making new function calls
                    // Linked call sites might now inline, need to recheck
                    for( Node cend : fun.ret().outs() )
                        if( cend instanceof CallEndNode )
                            code.add(cend);
                }
                if( n instanceof FRefNode fref ) {
                    TypeStruct clz = (TypeStruct)Parser.TYPES.get(fref._name);
                    if( clz == null )
                        return null;
                    // Class pointer:
                    TypeMemPtr clzptr = TypeMemPtr.make((byte)2,clz,true);
                    // Class pointer constant:
                    ConstantNode clzCon = code.con(clzptr);
                    // Replace and optimize
                    fref.addDef(clzCon);
                    code.add(fref);
                }
                return null;
            } );
    }

    // Parse one Simple source code file.  Add all the FRefs produced to the worklist.
    private static void parseOne( CodeGen code, CompUnit cunit ) {
        // Parse a source file, return missing external references
        Ary<FRefNode> frefs = code.P.parse(cunit);

        // Find all missing external references, or complain.  This can trigger more parses.
        while( !frefs.isEmpty() ) {
            FRefNode fref = frefs.pop();
            // Symbol not found in source code (or we would not be here).
            // Search the module for the fref
            try {
                CompUnit xcunit = findCUnitModule(code,cunit,fref._name);
                if( xcunit != null ) {
                    // Replace the FRef with the discovered class name.
                    fref._name = Parser.addClzPrefix(xcunit._cname);
                    cunit.addDep(xcunit);

                    // Module search failed, now try external paths
                } else if( (xcunit = findCUnitExternal( code, fref._name ) ) != null ) {
                    // fref.addDef(xcunit.extern);
                    throw Utils.TODO();
                } else {
                    throw new RuntimeException("Undefined name '" + fref._name +"'");
                }
            } catch( IOException ioe ) {
                throw new RuntimeException(ioe);
            }
        }
        addRequiredTypes(code,cunit);
    }

    private static void addRequiredTypes(CodeGen code, CompUnit cunit) {
        for( String typeName : Parser.REQUIRED_TYPES ) {
            if( !Parser.UNRESOLVED_TYPES.contains(typeName) )
                continue;
            try {
                CompUnit xcunit = findRequiredType(code,cunit,typeName);
                if( xcunit != null )
                    cunit.addDep(xcunit);
            } catch( IOException ioe ) {
                throw new RuntimeException(ioe);
            }
        }
    }

    private static CompUnit findRequiredType(CodeGen code, CompUnit cunit, String typeName) throws IOException {
        for( CompUnit cu = cunit; cu != null; cu = cu._par ) {
            String symbol = typeName.startsWith(cu._cname+".")
                ? typeName.substring(cu._cname.length()+1)
                : typeName;
            CompUnit xcunit = findCUnitModule(code,cu,symbol);
            if( xcunit != null )
                return xcunit;
        }
        return null;
    }

    private static void loadDeps(CodeGen code, CompUnit cunit) {
        // Load IR after parsing
        if( NEEDS_LOAD.find(cunit) != -1 )
            return;             // Already on the to-be-loaded list?
        try {
            NEEDS_LOAD.add(cunit);
            // Load dependent classes
            ElfReader elf = ElfReader.load(cunit._obj,cunit);
            elf.loadPublicSymbols();
            for( String symbol : elf._deps ) {
                // Check CompUnit for being up to date
                CompUnit sub = makeCUnit(code,null,symbol);
                // If not seen before or out of date, parse the new nested cunit
                if( sub._clz == null )
                    WORK.add(sub);
            }
        } catch( IOException ioe ) {
            throw new RuntimeException(ioe);
        }
    }

    private static void loadCompUnit(CodeGen code, CompUnit cunit) {
        // Expecting a previously compiled object file
        assert cunit._obj != null;
        // Extract published symbols and put them in the internal tables;
        // cross-references will pick them up there.
        ElfReader elf = ElfReader.load(cunit._obj,cunit);
        cunit._clz = elf.loadSimple(code);
        StartNode lStart = (StartNode)elf._nodes.at(0);
        StopNode  lStop  = ( StopNode)elf._nodes.last();

        // Loaded fidxs have already been remapped into this CodeGen's local
        // namespace.  Publish the function heads so Opto can resolve escaped
        // function pointers without scanning the whole loaded graph.
        for( Node use : lStart._outputs )
            if( use instanceof FunNode fun )
                code.link(fun);

        // The global Stop owns each loaded CompUnit Stop; the global Start
        // replaces the loaded Start as the single external-world boundary.
        cunit._stop = lStop;
        code._stop.addDef(cunit._stop);
        code.add(code._stop);

        //code._start._type = code._start._type.join(lStart._type);
        //lStart.subsume(code._start);
        code.add(code._start);

        // Loaded CompUnits carry post-Opto types.  Keep those strong facts and
        // let the next global analysis reconcile them with parsed code.

        // Add the new top-level loaded class
        assert !Parser.TYPES.containsKey(cunit._clz._name);
        Parser.TYPES.put(cunit._clz._name,cunit._clz);
        Parser.resolveType(cunit._clz._name);
    }

    // With great sadness, we throw away the loaded optimistic types so we
    // can link into the existing pessimistic types without blowing the
    // monotone invariant.
    private static void pessimizeLoaded(CodeGen code, Node seed) {
        Ary<Node> work = new Ary<>(Node.class);
        BitSet on = new BitSet();
        // Seed the boundary neighborhood even if Start itself is already
        // stable; its loaded users can still carry optimistic serialized types.
        pessPush(work,on,seed);
        for( Node def : seed._inputs  ) pessPush(work,on,def);
        for( Node use : seed._outputs ) pessPush(work,on,use);
        Node n;
        while( (n=work.pop()) != null ) {
            on.clear(n._nid);
            assert !n.isDead();
            Type old = n._type;
            Type type = n.compute();
            if( old == type ) continue;
            n._type = type;
            code.add(n);
            for( Node def : n._inputs  ) pessPush(work,on,def);
            for( Node use : n._outputs ) pessPush(work,on,use);
        }
    }

    private static void pessPush(Ary<Node> work, BitSet on, Node n) {
        if( n != null && !on.get(n._nid) ) {
            on.set(n._nid);
            work.push(n);
        }
    }


    public static CompUnit findCompUnit(CodeGen code, CompUnit cu, String symbol) {
        // Symbol not found in source code (or we would not be here).
        // Search the module for the fref
        try {
            CompUnit local = findCUnitModule(code,cu,symbol);
            if( local != null )
                return local;
            // Module search failed, now try external paths
            CompUnit ext = findCUnitExternal( code, symbol );
            if( ext != null )
               return ext;

            throw new RuntimeException("Undefined name '" + symbol +"'");
        } catch( IOException ioe ) {
            throw new RuntimeException(ioe);
        }
    }

    // Find the symbol in module, starting from cunit.  This searches up the nested
    // class chain and sideways 1 step, so that any prefix of the class chain can
    // be skipped as a shortcut.  Symbol can have dotted names, which then go
    // down into classes.  e.g.
    //   A/X/Y exists.
    //   A/B contains X.Y, which is a shortcut for A.X.Y

    // However if A/B contains "Y", the search would be for A.B.Y then A.Y both of
    // which do not exist.
    private static CompUnit findCUnitModule( CodeGen code, CompUnit cunit, String symbol ) throws IOException {
        if( cunit == null )     // Ran off top of module scope?
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
            String rest = symbol.substring(dotIdx+1 );
            String fname = cunit._fname + "/" + pre;
            File f = new File( code._modDir + "/" + fname + ".smp" );
            if( f.exists() ) {
                CompUnit sub = makeCUnit(code,cunit,pre);
                // Note that "sub" itself is not put on the to-be-parsed-list.  We're
                // referring to a child class and do not need to parse the middles.
                CompUnit dcunit = findCUnitModule(code,sub,rest);
                if( dcunit != null )
                    return dcunit; // Nested dot search found cunit
            }
        }

        // Look in mod / fname / name / symbol.smp
        String fname = cunit._fname + "/" + symbol;
        File f = new File( code._modDir + "/" + fname + ".smp" );
        if( f.exists() ) {
            CompUnit sub = makeCUnit(code,cunit,symbol);
            // If not seen before or out of date, parse the new nested cunit
            if( sub._clz == null )
                WORK.add(sub);
            return sub;
        }

        // Look up the scope
        return findCUnitModule( code, cunit._par, symbol );
    }






    // Search external references only.  This should go deep on well known
    // container classes (tar, zip) and needs check for all the symbols in the
    // file, not just the file name (which is only checked here).
    private static CompUnit findCUnitExternal( CodeGen code, String symbol ) {
        if( symbol.contains( "." ) )
            throw new RuntimeException("Need to handle nested external symbols");

        // Already found external symbol.  Expect lots of lookups on common symbols
        // like malloc/stdin/write
        CompUnit cunit = code._compunits.get(symbol);
        if( cunit != null )
            return cunit;

        // For now only look in file names, recursively in directories.
        // TODO: search tar files, zip files, contents of .obj files.
        ExternNode ext = code.findExternal(symbol);
        if( ext == null )
            return null;             // Failed to find in extern libraries
        // Add external symbol mapping
        code._compunits.put( symbol, cunit = new CompUnit(ext,symbol) );
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
