package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.util.*;

abstract public class Serialize {

    static void serialize( CodeGen code ) {

        for( CompUnit ref : code._compunits.values() ) {
            if( ref._clz==null ) continue; // Not writing this one
            // Get all Nodes in a sane order
            Ary<Node> nodes = nodeOrder(code,ref);

            // Array of dependent file names
            String[] deps = null;
            if( ref._deps != null && ref._deps._len>0 ) {
                deps = new String[ref._deps._len];
                for( int i=0; i<deps.length; i++ )
                    deps[i] = ref._deps.at(i)._fname;
            }

            // Compress into bytes
            BAOS baos = write(nodes, ref._clz, deps);

            // --- Expensive bijection assert
            if( false ) {
                // Inflate into POJOs; renumbers everything
                ElfReader elf = new ElfReader(new BAOS(baos.toByteArray()));
                readAll(elf,ref, false);
                BAOS baos2 = write(elf._nodes,elf._clz,elf._deps);

                // Bi-jection
                for( int i=0; i<baos.size(); i++ )
                    assert baos.buf()[i]==baos2.buf()[i];
                assert baos.size()==baos2.size();
            }
            // --- Expensive bijection assert

            // Record serialized IR for later ELF writing
            ref._serial = baos;
        }
    }

    // --------------------------------------------------
    static BAOS write(Ary<Node> nodes, TypeStruct clz, String[] depobjs /*other CodeGen inputs, #aliases, #fidxs*/) {
        // Initialize the mapping from bits/tags to types
        Type.TAGOFFS();

        BAOS baos = new BAOS();
        // A - Print a header
        baos.write('C').write('0').write('D').write('E');

        // Count unique Types
        var types = new HashMap<Type,Integer>();
        // Count published types *first*, so can align with published strings
        types.put(Type.TOP,0);
        types.put( clz, types.size() );
        // Have to visit the innards specially, since they did not get the
        // normal recursive visit when first touching a type, because the
        // published types are packed early to align with their string names so
        // lookup in ElfReader can be fast.
        for( int i=0; i<clz.nkids(); i++ )
            clz.at(i).gather(types);
        // Gather types from all sources
        for( Node n : nodes ) {
            n._type.gather(types);
            if( n instanceof  FunNode fun ) fun.sig().gather(types);
            if( n instanceof TypeNode con ) con._con .gather(types);
        }
        // Radix sort by Type ID#
        Type[] atypes = new Type[types.size()];
        for( Type t : types.keySet() ) {
            atypes[types.get(t)] = t;
        }

        // Count unique aliases; they should all be in Types
        var aliases = new HashMap<Integer,Integer>();
        aliases.put(0,0);       // There is no alias 0, just a placeholder
        aliases.put(1,1);       // Always alias#1 maps to #1 for ALL MEM
        for( Type t : atypes ) {
            if( t instanceof Field fld   ) gather(aliases,fld._alias);
            if( t instanceof TypeMem mem ) gather(aliases,mem._alias);
        }

        // Count all unique Strings
        var strs = new HashMap<String,Integer>();
        strs.put("",0);         // Null string is always 0
        // Find and count *published* Strings first
        gather(strs,clz._name);
        // Find and count dependent object file string names
        if( depobjs != null )
            for( String fname : depobjs )
                gather(strs,fname);
        // Count strings from all types
        for( Type t : atypes ) {
            if( t instanceof Field fld     ) gather(strs,fld._fname);
            if( t instanceof TypeStruct ts ) gather(strs,ts . _name);
        }
        // Count strings from nodes
        for( Node n : nodes )
            n.gather(strs);
        // Radix sort by string ID#
        String[] astrs = new String[strs.size()];
        for( String s : strs.keySet() )
            astrs[strs.get(s)] = s;

        // Check that published strings and types align
        assert astrs[1] == ((TypeStruct)atypes[1])._name;
        if( depobjs != null )
            for( int i=0; i<depobjs.length; i++ )
                assert astrs[1+i+1] == depobjs[i];


        // B - Write unique strings
        baos.packed4(depobjs==null ? 0 : depobjs.length);
        baos.packed4(strs.size());
        // Write in ID# order
        for( String s : astrs )
            baos.packedS(s);

        // C - Write unique Types
        baos.packed4(atypes.length);
        // Write Types in ID# order, no children
        for( Type t : atypes )
            t.packed(baos,strs,aliases);
        // Write Types in ID# order, only child IDs
        for( Type t : atypes ) {
            int nkids = t.nkids();
            for( int i=0; i<nkids; i++ )
                baos.packed4(types.get(t.at(i)));
        }


        // D - Write the nodes.  Since this is expected to be the bulk of the
        // data, we might want to explore various options.

        // How many nodes?
        baos.packed4(nodes._len);

        // Radix sort by Node ID#, new compressed NIDs
        IdentityHashMap<Node,Integer> anodes = new IdentityHashMap<>();
        for( Node n : nodes )
            anodes.put(n,anodes.size()+1); // +1 bias, reserve 0 for null

        // First cut: 1 byte opcode, optional per-node info, includes variable nIns
        for( Node n : nodes ) {
            baos.packed1(n.serialTag().ordinal());
            baos.packed2(types.get(n._type));
            n.packed(baos,strs,types,aliases);
        }
        // Write out the input indices packed
        for( Node n : nodes ) {
            for( int i=0; i<n.nIns(); i++ )
                baos.packed2(n.in(i)==null ? 0 : anodes.get(n.in(i)));
            if( n instanceof FunNode fun )
                baos.packed2(anodes.get(fun.ret()));
        }

        return baos;
    }
    public static <T> void gather( HashMap<T,Integer> strs, T s) {
        if( s!=null && !strs.containsKey(s) )
            strs.put(s,strs.size());
    }

    // Returned array of *public* strings only.
    static void read_public_strs(ElfReader elf) {
        BAOS bais = elf._bais;
        Type.TAGOFFS();

        // A - Read a header
        if( bais.read()!='C' || bais.read()!='0' || bais.read()!='D' || bais.read()!='E' )
            throw new IllegalArgumentException("Missing magic word");

        // B - Packed read of #strings, then strings
        int ndependents = bais.packed4(); // Number of dependent object files
        int nstrs = bais.packed4();       // Total number of strings
        String[] strs = new String[nstrs];
        for( int i=0; i<nstrs; i++ )
            strs[i] = bais.packedS();
        elf._strs = strs;
        
        // Collect dependent file names
        elf._deps = new String[ndependents];
        for( int i=0; i<ndependents; i++ )
            elf._deps[i] = strs[1+i+1];
    }

    // --------------------------------------------------
    static void readAll( ElfReader elf, CompUnit cu, boolean remapFIDXs ) {
        BAOS bais = elf._bais;
        // Initialize the mapping from bits/tags to types
        Type.TAGOFFS();

        // A - Read a header
        if( bais.read()!='C' || bais.read()!='0' || bais.read()!='D' || bais.read()!='E' )
            throw new IllegalArgumentException("Missing magic word");

        // B - Packed read of #strings, then strings
        int ndependents = bais.packed4(); // Number of dependent object files
        int nstrs = bais.packed4();       // Total number of strings
        String[] strs = new String[nstrs];
        for( int i=0; i<nstrs; i++ )
            strs[i] = bais.packedS();
        elf._strs = strs;

        // Collect dependent file names
        elf._deps = new String[ndependents];
        for( int i=0; i<ndependents; i++ )
            elf._deps[i] = strs[1+i+1];

        // C - Packed read of #types, then types
        int ntypes = bais.packed4();
        // Map deserialized aliases to local aliases
        AryInt aliases = new AryInt();
        AryInt fidxs = new AryInt();
        Type[] types = Type.packed(bais,strs,aliases,fidxs,ntypes, Parser.TYPES, CodeGen.CODE._alias, CodeGen.CODE._fidx, remapFIDXs);
        // Also passed aliases used in aliases.at(0)
        CodeGen.CODE._alias = aliases.at(0);
        CodeGen.CODE._fidx  = fidxs  .at(0);
        aliases.set(0,0);
        // Check and collect first published symbols map to TypeStructs
        elf._clz = (TypeStruct)types[1/*skip zero*/];
        assert elf._clz._name==strs[1/*skip null ptr*/];

        // D - Read the nodes

        // Number of nodes
        int num = bais.packed4();
        // Packed array of nodes
        Ary<Node> nodes = new Ary<>(Node.class);
        for( int i=0; i<num; i++ ) {
            Node.Tag tag = Node.Tag.VALS[bais.packed1()];
            Type t = types[bais.packed2()];
            Node n = tag.make(bais,strs,types,aliases);
            n._type = t;
            nodes.push(n);
        }

        // Node edges
        for( Node n : nodes ) {
            int len = n.nIns();
            for( int i=0; i<len; i++ ) {
                int idx = bais.packed2();
                n.setDef(i,idx==0 ? null : nodes.at(idx-1));
            }
            // A little extra for FunNodes
            if( n instanceof FunNode fun ) {
                // Return is hooked like an edge
                ReturnNode ret = (ReturnNode)nodes.at(bais.packed2()-1);
                fun.setRet(ret);
                ret._fun = fun;
                fun._compunit = cu;
                if( remapFIDXs ) {
                    // Set the code._linker and compunit fields
                    int fidx = fun.sig().fidx();
                    assert CodeGen.CODE._linker.atX(fidx)==null;
                    CodeGen.CODE._linker.setX(fidx,fun);
                }
            }
        }

        assert check(nodes);
        elf._nodes = nodes;
    }

    private static boolean check( Ary<Node> nodes ) {
        for( Node n : nodes )
            if( n._type != n.compute() )
                return false;
        return true;
    }

    // --------------------------------------------------
    public static Ary<Node> nodeOrder( CodeGen code, CompUnit cu ) {
        assert code._start._ltree != null; // Uses loop tree
        BitSet visit = code.visit();
        Ary<Node> nodes = new Ary<>(Node.class);
        // Walk all listed functions, in this single compilation unit
        for( FunNode fun : code._linker )
            if( fun!=null && !fun.isDead() && fun._compunit == cu )
                _funWalk(fun,nodes,visit);

        // Just constants used by the listed functions
        Ary<Node> cons = new Ary<>(Node.class);
        cons.add(code._start);
        for( Node n : code._start._outputs ) {
            if( n instanceof ConstantNode con && !visit.get(con._nid) ) {
                for( Node use : con.outs() ) {
                    // Special case for Cast:BOT which is basically a double-indirect constant
                    if( use instanceof CastNode cast && cast.in(0)==null )
                        for( Node useuse : cast.outs() )
                            conUsed(cast,useuse,visit,cons);
                    // Constant used by somebody, so flag for output
                    conUsed(con,use,visit,cons);
                }
            }
        }
        cons.addAll(nodes);
        cons.add(cu._stop);
        visit.clear();
        return cons;
    }

    private static void conUsed( Node con, Node use, BitSet visit, Ary<Node> cons ) {
        if( use == null || !visit.get(use._nid) || visit.get(con._nid) ) return;
        cons.add(con);
        visit.set(con._nid);
    }

    // --------------------------------------------------
    public static Ary<Node> nodeOrder( CodeGen code ) {
        assert code._start._ltree != null; // Uses loop tree
        Ary<Node> nodes = new Ary<>(Node.class);
        BitSet visit = code.visit();
        nodes.add(code._start);

        // All the global constants
        for( Node n : code._start._outputs )
            if( !(n instanceof FunNode) ) {
                visit.set(n._nid); nodes.add(n);
                // Special for Cast:BOT
                for( Node cast : n._outputs )
                    if( cast!=null && cast.nIns()==2 && cast.in(0)==null )
                        { visit.set(cast._nid); nodes.add(cast); }
            }

        // All the functions, including internal ones
        for( FunNode fun : code._linker )
            if( fun!=null && !fun.isDead() )
                _funWalk(fun,nodes,visit);

        nodes.add(code._stop);
        visit.clear();
        return nodes;
    }

    // Walk and print whole functions at a time, in CG order
    private static void _funWalk( FunNode fun, Ary<Node> rpo, BitSet visit ) {
        int start = rpo._len;
        _funRPO(fun,rpo,visit);
        int len = rpo._len - start;
        for( int i=0; i< len>>1; i++ )
            rpo.swap(start+i,start+len-1-i);
    }

    // Walk and gather RPO nodes.
    private static void _funRPO(Node n, Ary<Node> rpo, BitSet visit ) {
        if( visit.get(n._nid) ) return; // Been there, done that
        visit.set(n._nid);              // Stop recursion
        // Walk outputs ordered
        if( n instanceof CFGNode ncfg ) {
            // If nodes walk outer loops before inner, so they hit the
            // Return first, so Return is at the bottom of the RPO.
            if( n instanceof IfNode iff ) {
                CProjNode c0 = iff.cproj(0);
                CProjNode c1 = iff.cproj(1);
                if( c0._ltree.depth() > c1._ltree.depth() )
                    { c0 = c1; c1 = iff.cproj(0); }
                _funRPO(c0,rpo,visit);
                _funRPO(c1,rpo,visit);
            } else {
                // CFG to CFG before CFG to data
                for( Node use : n._outputs )
                    if( use instanceof CFGNode usecfg && !ncfg.skip(usecfg) ) // Do not walk from Call to Fun
                        _funRPO(use,rpo,visit);
            }
            // Walk CFG to data eventually
            for( Node use : n._outputs )
                if( !(use instanceof CFGNode) )
                    _funRPO(use,rpo,visit);
        } else {
            // Do not walk from a non-CFG to a CFG; CFGs walk to CFGs to
            // preserve CFG order.  Do not walk *into* Phis; wait for the
            // Region to walk the Phis (otherwise you visit some of the
            // Phis ahead of others forcing the Phis to be spread around).
            for( Node use : n._outputs ) {
                if( use!=null && !(use instanceof CFGNode) && !(use instanceof PhiNode) )
                    _funRPO(use,rpo,visit);
            }
        }

        // If parent is a Multi, do not add (yet).
        // If self   is a Multi, add self and children.
        if( multiChild(n) || n instanceof CallEndNode ) {
            // nothing; delay until Multi-head or CallNode
        } else if( n instanceof MultiNode ) {
            // Now lump all multi/projections together
            printMulti(n,rpo);
        } else if( n instanceof CallNode call ) {
            printMulti(call.cend(),rpo);
            rpo.add(call);
        } else if( n instanceof RegionNode ) {
            // Now lump all Phis together
            int old = rpo._len;
            for( Node use : n._outputs )
                if( use instanceof PhiNode )
                    rpo.add(use);
            // Sort by label?  Want Phis before control outputs
            Arrays.sort( rpo._es, old, rpo._len, (x,y) -> y.label().compareTo(x.label()) );
            rpo.add(n);
        } else if( n instanceof StopNode ) {
            // nothing
        } else {
            rpo.add(n);         // Post-order add
        }
    }

    private static boolean multiChild(Node n) {
        return n instanceof Proj || n instanceof PhiNode;
    }

    private static void printMulti(Node n, Ary<Node> rpo) {
        // Now lump all multi/projections together
        for( Node use : n._outputs )
            rpo.add(use);
        // Sort by projection order
        Arrays.sort( rpo._es,rpo._len-n.nOuts(),rpo._len, (x,y) -> ((Proj)y).idx() - ((Proj)x).idx() );
        rpo.add(n);
    }

}
