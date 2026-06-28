package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.IterPeeps;
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
    private static final Ary<CompUnit> WORK = new Ary<>(CompUnit.class);

    private static final Ary<CompUnit> NEEDS_LOAD = new Ary<>(CompUnit.class);

    // Return a compilation unit (cached or new).  If new, it does not necessarily
    // need to be compiled.
    public static CompUnit makeCUnit( CodeGen code, CompUnit par, String name ) throws IOException {
        return makeCUnit(code,par,name,null,false);
    }

    private static CompUnit makeCUnit( CodeGen code, CompUnit par, String name, File obj ) throws IOException {
        return makeCUnit(code,par,name,obj,obj!=null);
    }

    private static CompUnit makeCUnit( CodeGen code, CompUnit par, String name, File obj, boolean external ) throws IOException {
        // Slashed file name?  Move leading parts into parent CUs.
        int idx = name.indexOf("/");
        if( idx >= 0 ) {
            String head = name.substring(0,idx);
            CompUnit sub = makeCUnit(code,par,head,external ? externalObj(code,par,head) : null,external);
            return makeCUnit(code,sub,name.substring(idx+1).intern(),obj,external);
        }

        String fname = par==null ? name : (par._fname + "/" + name).intern();
        CompUnit cunit = code._compunits.get(fname);
        if( cunit != null ) return cunit;
        String cname = par==null ? name : (par._cname + "." + name).intern();
        code._compunits.put(fname, cunit = external ? new CompUnit(obj,par,fname,cname,name) : new CompUnit(par,fname,cname,name));
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
            CompUnit cu = makeCUnit( code, null, fname );
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
            CompUnit cu = WORK.pop();
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
        for( CompUnit cu : code._compunits.values() )
            if( cu._ext==null )
                cu._clz = (TypeStruct)Parser.TYPES.get(Parser.addClzPrefix(cu._cname));

        // Walk over all Nodes, and upgrade the internal constants to the
        // closed-over types.
        code._stop.walk( (Node n) -> {
                n.upgradeType(Parser.TYPES);
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
                // Adds sort by NIDs, which just got shuffled
                if( n instanceof AddNode add )
                    code.add(add);
                return null;
            } );

        // If we loaded Opto-typed code, skip first Iter
        if( !NEEDS_LOAD.isEmpty() )
            code._phase = CodeGen.Phase.Iter;
    }

    // Parse one Simple source code file.  Add all the FRefs produced to the worklist.
    private static void parseOne( CodeGen code, CompUnit cunit ) {
        cunit.setStart(code);
        // Parse a source file, return missing external references
        Ary<FRefNode> frefs = code.P.parse(cunit);

        // Find all missing external references, or complain.  This can trigger more parses.
        while( !frefs.isEmpty() ) {
            FRefNode fref = frefs.pop();
            // Symbol not found in source code (or we would not be here).
            // Search the module for the fref
            try {
                CompUnit xcunit = findCUnitModule(code,cunit,fref._name);
                if( xcunit == null )
                    xcunit = findCUnitExternalSimple(code,fref._name);
                if( xcunit != null ) {
                    // Replace the FRef with the discovered class name.
                    fref._name = Parser.addClzPrefix(xcunit._cname);
                    cunit.addDep(xcunit);
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
        return findCUnitExternalSimple(code,typeName);
    }

    // Load the public symbols for this CompUnit, and add dependent CompUnits to the NEEDS_LOAD list.
    private static void loadDeps(CodeGen code, CompUnit cunit) {
        // Load IR after parsing
        if( NEEDS_LOAD.find(cunit) != -1 )
            return;             // Already on the to-be-loaded list?
        try {
            NEEDS_LOAD.add(cunit);
            // Load dependent classes
            ElfReader elf = ElfReader.load(cunit._obj,cunit);
            elf.loadPublicSymbols();
            for( String fname : elf._deps ) {
                // Check CompUnit for being up to date
                CompUnit sub = makeCUnit(code,null,fname,null/*depObj(code,cunit,fname)*/);
                // If not seen before or out of date, parse the new nested cunit
                if( sub._clz == null && sub._par == null )
                    WORK.add(sub);
            }
        } catch( IOException ioe ) {
            throw new RuntimeException(ioe);
        }
    }

    private static File depObj(CodeGen code, CompUnit cunit, String fname) {
        if( cunit._smp != null )
            return null;
        ElfReader elf = code.findExternalSimple(Parser.addClzPrefix(fname.replace('/','.')));
        if( elf == null )
            throw new RuntimeException("Cannot find external Simple dependency '"+fname+"'");
        return elf._file;
    }

    private static File externalObj(CodeGen code, CompUnit par, String name) {
        String cname = par==null ? name : par._cname + "." + name;
        ElfReader elf = code.findExternalSimple(Parser.addClzPrefix(cname));
        return elf == null ? null : elf._file;
    }

    private static void loadCompUnit(CodeGen code, CompUnit cunit) {
        // Expecting a previously compiled object file
        assert cunit._obj != null;
        // Extract published symbols and put them in the internal tables;
        // cross-references will pick them up there.
        ElfReader elf = ElfReader.load(cunit._obj,cunit);
        cunit._clz = elf.loadSimple(code);
        StartNode lstart = (StartNode)elf._nodes.at(0);
        StopNode  lstop  = ( StopNode)elf._nodes.last();

        // Add all new loaded functions to the linker table
        for( Node n : lstop._inputs ) {
            StopCUNode  stop = (StopCUNode)n;
            StartCUNode start = stop.start();
            CompUnit cu = code._compunits.get(start._fname);
            cu._start = start;
            cu._stop  = stop ;

            for( Node ret : stop._inputs ) {
                FunNode fun = ((ReturnNode)ret).fun();
                code.link(fun);
            }
        }

        // Hook the new code into the existing code graph
        for( Node lstopcu : lstop._inputs )
            code._stop.addDef(lstopcu);
        lstart.subsume(code._start);

        // Add the new top-level loaded class
        Parser.TYPES.put(cunit._clz._name,cunit._clz);
        Field inst = cunit._clz.field(cunit._name);
        if( inst != null ) // Also the instance type, if a constructor exists
            Parser.TYPES.put(cunit._cname, ((TypeMem)((TypeFunPtr)inst._t)._ret)._t);
        Parser.resolveType(cunit._clz._name);
    }

    // Symbol not found in source code (or we would not be here).
    // Search the module for the fref
    public static CompUnit findCompUnitOrThrow(CodeGen code, CompUnit cu, String symbol) {
        CompUnit cu2 =  findCompUnit(code,cu,symbol);
        if( cu2 != null ) return cu2;
        throw new RuntimeException("Undefined name '" + symbol +"'");
    }

    public static CompUnit findCompUnit(CodeGen code, CompUnit cu, String symbol) {
        // Symbol not found in source code (or we would not be here).
        // Search the module for the fref
        try {
            CompUnit local = findCUnitModule(code,cu,symbol);
            if( local != null )
                return local;
            return findCUnitExternalSimple(code,symbol);
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
            if( sub._clz == null && WORK.find(sub) == -1 )
                WORK.add(sub);
            return sub;
        }

        // Look up the scope
        return findCUnitModule( code, cunit._par, symbol );
    }

    // Search external Simple objects only.  C linkage uses explicit "C"
    // declarations, which produce ExternNodes and are resolved by the linker.
    private static CompUnit findCUnitExternalSimple( CodeGen code, String symbol ) {
        String clzName = symbol.startsWith(Parser.CLZ) ? symbol : Parser.addClzPrefix(symbol);
        String cname = clzName.substring(Parser.CLZ.length());
        String fname = cname.replace('.','/');

        CompUnit cunit = code._compunits.get(fname);
        if( cunit != null )
            return cunit;

        ElfReader elf = code.findExternalSimple(clzName);
        if( elf == null )
            return null;

        try {
            cunit = makeCUnit(code,null,fname,elf._file);
        } catch( IOException ioe ) {
            throw new RuntimeException(ioe);
        }
        TypeStruct clz = (TypeStruct)Parser.TYPES.get(clzName);
        if( clz == null )
            Parser.TYPES.put(clzName,clz = TypeStruct.make(clzName,true));
        cunit._clz = clz;
        if( WORK.find(cunit) == -1 && NEEDS_LOAD.find(cunit) == -1 )
            WORK.add(cunit);
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
