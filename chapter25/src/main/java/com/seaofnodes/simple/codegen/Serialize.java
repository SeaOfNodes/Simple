package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.util.*;

abstract public class Serialize {

    static void serialize( CodeGen code ) {

        // Get all Nodes in a sane order
        Ary<Node> nodes = nodeOrder(code);

        // Array of dependent file names
        HashSet<String> xdeps = new HashSet<>();
        for( CompUnit cu : code._compunits.values() ) {
            if( cu._deps != null )
                for( CompUnit cudep : cu._deps )
                    xdeps.add(cudep._fname);
        }
        String[] deps = xdeps.toArray(new String[xdeps.size()]);

        // Top-level clazz being reported
        String topName = code._srcName == null ? "Test" : code._srcName;
        CompUnit top = code._compunits.get(topName);
        TypeStruct clz = top._clz;

        // Compress into bytes
        BAOS baos = write(nodes, clz, deps, code._aliases, code._fidxs, code._rpcs, code._externFunc);

        // --- Expensive bijection assert
        if( false ) {
            // Inflate into POJOs; renumbers everything
            ElfReader elf = new ElfReader(new BAOS(baos.toByteArray()));
            readAll(code,elf, code._aliases, code._fidxs, code._rpcs);
            BAOS baos2 = write(elf._nodes,elf._clz,elf._deps, code._aliases, code._fidxs, code._rpcs, code._externFunc);

            // Bi-jection
            for( int i=0; i<baos.size(); i++ )
                assert baos.buf()[i]==baos2.buf()[i] : "bijection mismatch at "+i+": "+(baos.buf()[i]&0xFF)+" != "+(baos2.buf()[i]&0xFF);
            assert baos.size()==baos2.size() : "bijection size mismatch: "+baos.size()+" != "+baos2.size();
        }
        // --- Expensive bijection assert

        // Record serialized IR for later ELF writing
        code._serial = baos;
    }

    // --------------------------------------------------
    static BAOS write(Ary<Node> nodes, TypeStruct clz, String[] depobjs, GlobalBits aliases, GlobalBits fidxs, GlobalBits rpcs, HashMap<Integer,String> externFunc) {
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
            if( n instanceof CallEndNode cend ) cend._rpc.gather(types);
            if( n instanceof  FunNode fun ) fun.sig().gather(types);
            if( n instanceof TypeNode con ) con._con .gather(types);
        }
        // Radix sort by Type ID#
        Type[] atypes = new Type[types.size()];
        for( Type t : types.keySet() )
            atypes[types.get(t)] = t;

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
        // Count strings used by local->global bit mappings.
        aliases.gather(strs);
        fidxs  .gather(strs);
        rpcs   .gather(strs);
        for( String extern : externFunc.values() )
            gather(strs,extern);
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
        // Write in ID# order; the first depobjs.length of these are dependent strings
        for( String s : astrs )
            baos.packedS(s);

        // B.2 - Write the local->global mappings
        aliases.packed(baos,strs);
        fidxs  .packed(baos,strs);
        rpcs   .packed(baos,strs);
        ArrayList<Integer> externFidxs = new ArrayList<>(externFunc.keySet());
        Collections.sort(externFidxs);
        baos.packed2(externFidxs.size());
        for( int fidx : externFidxs ) {
            baos.packed2(fidx);
            baos.packed2(strs.get(externFunc.get(fidx)));
        }

        // C - Write unique Types
        baos.packed4(atypes.length);
        // Write Types in ID# order, no children
        for( Type t : atypes )
            t.packed(baos,strs);
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
            n.packed(baos,strs,types,anodes);
        }
        // Write out the input indices packed.
        for( Node n : nodes ) {
            for( int j=0; j<n.nIns(); j++ ) {
                Node def = n.in(j);
                Integer idx = def==null ? Integer.valueOf(0) : anodes.get(def);
                assert idx != null : "Missing serialized input for "+n+" input "+j+" def "+def;
                baos.packed2(idx);
            }
            if( n instanceof FunNode fun )
                baos.packed2(anodes.get(fun.ret()));
        }

        return baos;
    }
    public static <T> void gather( HashMap<T,Integer> strs, T s) {
        if( s!=null && !strs.containsKey(s) )
            strs.put(s,strs.size());
    }
    public static void gather( AryInt is, int s) {
        if( is.atX(s)==0 ) {
            int cnt=0;
            for( int i=1; i<is._len; i++ )
                if( is._es[i] != 0 )
                    cnt++;
            is.setX(s,cnt+1);
        }
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
    static void readAll( CodeGen code, ElfReader elf, GlobalBits aliases, GlobalBits fidxs, GlobalBits rpcs ) {
        BAOS bais = elf._bais;
        // Initialize the mapping from bits/tags to types
        Type.TAGOFFS();

        // If not read the strings already, read them now
        String[] strs = elf._strs;
        if( strs == null ) {
            // A - Read a header
            if( bais.read() != 'C' || bais.read() != '0' || bais.read() != 'D' || bais.read() != 'E' )
                throw new IllegalArgumentException( "Missing magic word" );

            // B - Packed read of #strings, then strings
            int ndependents = bais.packed4(); // Number of dependent object files
            int nstrs = bais.packed4();       // Total number of strings
            elf._strs = strs = new String[nstrs];
            for( int i = 0; i < nstrs; i++ )
                strs[i] = bais.packedS();

            // Collect dependent file names
            elf._deps = new String[ndependents];
            for( int i = 0; i < ndependents; i++ )
                elf._deps[i] = strs[1 + i + 1];
        }

        // B.2 - Read the local->global mappings.  Further down we will map
        // Elf file-local -> global, then global -> CodeGen.CODE local
        GlobalBits fileAliases = GlobalBits.packed(bais,strs);
        GlobalBits fileFidxs   = GlobalBits.packed(bais,strs);
        GlobalBits fileRpcs    = GlobalBits.packed(bais,strs);
        int nexterns = bais.packed2();
        for( int i=0; i<nexterns; i++ ) {
            int fileFidx = bais.packed2();
            String extern = strs[bais.packed2()];
            int fidx = fileFidx < GlobalBits.RESERVED ? fileFidx : fidxs.map(fileFidxs,fileFidx);
            String old = code.externFunc(fidx);
            if( old == null )
                code.externFunc(fidx,extern);
            else
                assert old.equals(extern);
        }


        // C - Packed read of #types, then types
        int ntypes = bais.packed4();
        Type[] types = Type.packed(bais,strs,ntypes, Parser.TYPES,
                                   fileAliases,aliases,
                                   fileFidxs  ,fidxs,
                                   fileRpcs   ,rpcs);

        // aliases.set(0,0);
        // Check and collect first published symbols map to TypeStructs
        elf._clz = (TypeStruct)types[1/*skip zero*/];
        assert elf._clz._name==strs[1/*skip null ptr*/];

        // D - Read the nodes

        // Number of nodes
        int num = bais.packed4();
        // Packed array of nodes
        Ary<Node> nodes = new Ary<>(Node.class);
        CompUnit cu = null;
        for( int i=0; i<num; i++ ) {
            Node.Tag tag = Node.Tag.VALS[bais.packed1()];
            Type t = types[bais.packed2()];
            Node n = tag.make(bais,strs,types,fileAliases,aliases);
            n._type = t;
            nodes.push(n);
            // A little extra for FunNodes
            if( n instanceof StartCUNode startcu ) {
                cu = code._compunits.get(startcu._fname==null ? (code._srcName==null ? "Test" : code._srcName) : startcu._fname);
                assert cu != null;
            }
            if( n instanceof StopCUNode stopcu ) {
                cu = null;
            }
            if( n instanceof FunNode fun ) {
                assert cu != null;
                fun._compunit = cu;
            }
            if( n instanceof ExternNode ext && ext._con instanceof TypeFunPtr tfp && tfp.isConstant() ) {
                String old = code.externFunc(tfp.fidx());
                if( old == null )
                    code.externFunc(tfp.fidx(), ext._extern);
                else
                    assert old.equals(ext._extern);
            }
        }

        // Node edges
        for( Node n : nodes ) {
            int len = n.nIns();
            for( int i=0; i<len; i++ ) {
                int idx = bais.packed2();
                n.setDef(i,idx==0 ? null : nodes.at(idx-1));
                // Call's output[0] is always the CallEnd
                if( n instanceof CallEndNode cend && i==0 ) {
                    var outs = cend.call()._outputs;
                    outs.swap(0,outs._len-1);
                }
            }
            // A little extra for FunNodes
            if( n instanceof FunNode fun ) {
                // Return is hooked like an edge
                ReturnNode ret = (ReturnNode)nodes.at(bais.packed2()-1);
                fun.setRet(ret);
                ret._fun = fun;
            }
        }
        // Add edge from Start to Stop
        StartNode start = (StartNode)nodes.at(0);
        StopNode stop = (StopNode)nodes.last();
        start.setDef(1,stop);

        assert check(code,nodes);
        elf._nodes = nodes;
    }

    // Check loaded types and nodes are consistent
    private static boolean check( CodeGen code, Ary<Node> nodes ) {
        // If loading code instead of parsing, the types will be valid
        // post-parse - e.g. nodes are not "in progress" so Regions and Phis
        // can just do meet-over-inputs, hence the Phase change before asserting
        boolean good = true;
        CodeGen.Phase phase = code._phase;
        code._phase = CodeGen.Phase.Opto;
        for( Node n : nodes ) {
            Type nval = n.compute();
            if( nval != n._type ) {
                System.err.println("Node "+n+" read in as "+n._type+" but computes as "+nval);
                good = false;
            }
        }
        code._phase = phase;
        return good;
    }

    // --------------------------------------------------
    public static Ary<Node> nodeOrder( CodeGen code, CompUnit cu ) {
        assert code._start._ltree != null; // Uses loop tree
        BitSet visit = code.visit();
        Ary<Node> nodes = new Ary<>(Node.class);
        visit.set(code._stop._nid);
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
        // StartCU and memory projection
        cons.add(cu._start);
        for( Node n : cu._start._outputs )
            if( n instanceof ProjNode ) cons.add(n);
        // All the innards of functions
        cons.addAll(nodes);
        // StopCU, Stop
        cons.add(cu._stop);
        cons.add(code._stop);
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
        for( Node n : code._start._outputs ) {
            if( n instanceof ConstantNode ) {
                visit.set( n._nid );
                nodes.add( n );
                // Special for Cast:BOT
                for( Node cast : n._outputs )
                    if( cast != null && cast.nIns() == 2 && cast.in( 0 ) == null ) {
                        visit.set( cast._nid );
                        nodes.add( cast );
                    }
            }
        }

        // All the CompUnits
        for( CompUnit cu : code._compunits.values() ) {
            // Whole CompUnits have no users, went dead
            if( cu._start != null && !cu._start.isDead() ) {
                nodes.add(cu._start);
                // Memory proj
                nodes.add(cu._start.proj(1));
                // All the functions, including internal ones, in comp-unit order
                for( FunNode fun : code._linker )
                    if( fun!=null && !fun.isDead() && fun._compunit == cu )
                        _funWalk(fun,nodes,visit);
                nodes.add(cu._stop);
            }
        }

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
            } else if( n instanceof StopNode ) {
                // nothing
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
        if( !(n instanceof StartNode) )
            Arrays.sort( rpo._es,rpo._len-n.nOuts(),rpo._len, (x,y) -> ((Proj)y).idx() - ((Proj)x).idx() );
        rpo.add(n);
    }

}
