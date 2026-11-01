package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Stack;

/** Source-unit entry point and shared cursor, scope, and IR utilities.
 * Grammar productions live in the package-local components below. */
public class Parser {
    final Statements statements = new Statements(this);
    final ControlFlow controlflow = new ControlFlow(this);
    final Expressions expressions = new Expressions(this);
    final Declarations declarations = new Declarations(this);
    final Types types = new Types(this);
    final Functions functions = new Functions(this);
    final Structs structs = new Structs(this);
    final Allocations allocations = new Allocations(this);
    final References references = new References(this);
    final Conversions conversions = new Conversions(this);

    // Compile driver
    public final CodeGen _code;

    // The Lexer.  Thin wrapper over a byte[] buffer with a cursor.
    Lexer _lexer;

    // Source file for compilation
    public CompUnit _ref;

    // Current classname being parsed
    String _nestedType;
    /**
     * Current ScopeNode - ScopeNodes change as we parse code, but at any point of time
     * there is one current ScopeNode. The reason the current ScopeNode can change is to do with how
     * we handle branching. See {@link Statements#parseIf()}.
     * <p>
     * Each ScopeNode contains a stack of lexical scopes, each scope is a symbol table that binds
     * variable names to Nodes.  The top of this stack represents current scope.
     * <p>
     * We keep a list of all ScopeNodes so that we can show them in graphs.
     * @see Statements#parseIf()
     * @see #_xScopes
     */
    public ScopeNode _scope;

    /**
     * List of keywords disallowed as identifiers
     */
    public static final HashSet<String> KEYWORDS = new HashSet<>(){{
            add("bool");
            add("break");
            add("byte");
            add("continue");
            add("else");
            add("f32");
            add("f64");
            add("false");
            add("flt");
            add("i16");
            add("i32");
            add("i64");
            add("i8");
            add("if");
            add("int");
            add("new");
            add("null");
            add("return");
            add("struct");
            add("true");
            add("u1");
            add("u16");
            add("u32");
            add("u8");
            add("while");
        }};

    /**
     * We clone ScopeNodes when control flows branch; it is useful to have
     * a list of all active ScopeNodes for purposes of visualization of the SoN graph
     */
    public final Stack<ScopeNode> _xScopes = new Stack<>();
    // Layouts needed by field accesses in this source file.  Resolve after
    // parsing, since a later local struct declaration can satisfy the use.
    public final LinkedHashSet<String> _requiredTypes = new LinkedHashSet<>();

    // Mapping from a type name to a Type.  The string name matches
    // `type.str()` call.
    public static final HashMap<String, Type> TYPES = new HashMap<>();
    public static final HashMap<String, Type> INIT_TYPES =
        new HashMap<>() {{
            put("bool",TypeInteger.U1 );
            put("byte",TypeInteger.U8 );
            put("f32" ,TypeFloat  .F32);
            put("f64" ,TypeFloat  .F64);
            put("flt" ,TypeFloat  .F64);
            put("i16" ,TypeInteger.I16);
            put("i32" ,TypeInteger.I32);
            put("i64" ,TypeInteger.BOT);
            put("i8"  ,TypeInteger.I8 );
            put("int" ,TypeInteger.BOT);
            put("u1"  ,TypeInteger.U1 );
            put("u16" ,TypeInteger.U16);
            put("u32" ,TypeInteger.U32);
            put("u8"  ,TypeInteger.U8 );
            put("val" ,Type.TOP);    // Marker type, indicates type inference
            put("var" ,Type.BOTTOM); // Marker type, indicates type inference
        }};

    public static class ParseException extends RuntimeException {
        public final Lexer _loc;
        ParseException( String msg, Lexer loc ) { super(msg);  _loc = loc; }
    }

    public Parser(CodeGen code ) {
        _code = code;
    }

    public Ary<FRefNode> parse( CompUnit ref ) {
        assert _scope == null && statements._breakScope == null && statements._continueScope == null && functions._returnScope == null;
        assert references._frefs == null;
        _lexer = new Lexer(ref);
        references._frefs = new Ary<>(FRefNode.class);
        _requiredTypes.clear();
        // Starting Scope has control, memory, initial arguments
        _scope = new ScopeNode();
        _scope.define(ScopeNode.CTRL, Type.CONTROL   , false, null, _lexer);
        _scope.define(ScopeNode.MEM0, TypeMem.BOT    , false, null, _lexer);
        // Track active scopes for Graph display
        _xScopes.push(_scope);
        ctrl(_code.XCTRL);
        mem(con(TypeMem.BOT));

        // File-level struct declaration
        _ref = ref;
        _nestedType = ref._cname;
        String typeName = TypeStruct.addClzPrefix(ref._cname);

        // Parse whole file, which is a struct-level class initializer
        structs.parseInitializer( true, typeName );

        if( !_lexer.isEOF() ) throw _errorSyntax("unexpected");

        Ary<FRefNode> frefs = references.resolve();

        // The class being published
        ref._clz = (TypeStruct)TYPES.get(typeName);

        // Clean up and reset
        _xScopes.pop();
        _scope.kill();
        _scope = null;
        references._frefs = null;

        return frefs;
    }

    // Cursor and token helpers

    // Return true and skip if "syntax" is next in the stream.
    boolean match (String syntax) { return _lexer.match (syntax); }
    // Match must be "exact", not be followed by more id letters
    boolean matchx(String syntax) { return _lexer.matchx(syntax); }
    boolean matchOp(char c0 ) { return _lexer.matchOp(c0);  }
    boolean matchOpx(char c0, char c1) { return _lexer.matchOpx(c0,c1);  }
    // Skip whitespace, but leave the next token unread.
    boolean peek(char ch) { return _lexer.peek(ch); }

    public int pos() { return _lexer._position; }
    int pos(int pos) {
        int old = _lexer._position;
        _lexer._position = pos;
        return old;
    }
    Type posT(int pos) { _lexer._position = pos; return null; }
    // Source code location
    Lexer loc() { return new Lexer(_lexer); }

    // Require and return an identifier
    String requireId() {
        String id = _lexer.matchId();
        if (id != null && !KEYWORDS.contains(id) ) return id.intern();
        throw error("Expected an identifier, found '"+id+"'");
    }

    String matchId() {
        int old = pos();
        String id = _lexer.matchId();
        if( id==null ) return null;
        if( !KEYWORDS.contains(id) ) return id;
        pos(old);
        return null;
    }

    // Require an exact match
    Parser require(String syntax) { require(null, syntax); return this; }
    <N> N require(N n, String syntax) {
        if (match(syntax)) return n;
        throw errorSyntax(syntax);
    }

    // Scope, control, and memory

    // Read and set CTRL
    Node ctrl() { return _scope.ctrl(); }
    <N extends Node> N ctrl(N n) { return _scope.ctrl(n); }
    // Read and set Memory
    Node mem() { return _scope.mem(1); }
    void mem(Node mem) { _scope.mem(mem); }

    // Ordinary parsing carries one whole-memory value.  Alias partitioning is
    // graph optimization, not parser state.
    void storeMem( Node st, int alias, Node prior ) {
        mem(alias==1 ? st : mergeAlias(prior,alias,st));
    }

    void classInit(Lexer site, Node ptr) {
        // Ordinary load machinery resolves the flag alias and offset when
        // the receiver is still a forward reference.
        Field flag = ptr._type instanceof TypeMemPtr tmp ? tmp._obj.field("$init") : null;
        int alias = flag==null ? 1 : flag._alias;
        Node ready = new LoadNode(site,"$init",alias,null,mem(),ptr,fldoff(ptr,"$init")).peephole();
        CLInitNode init = new CLInitNode(site,ctrl(),mem(),ptr,ready).peephole().keep();
        ctrl(new CProjNode(init,0,ScopeNode.CTRL).peephole());
        Node bulk = new ProjNode(init,1,ScopeNode.MEM0).peephole().keep();
        flag = init.flag();
        mem(flag==null ? bulk : mergeAlias(bulk,flag._alias,
            new ProjNode(init,2,ScopeNode.memName(flag._alias)).peephole()));
        bulk.unkeep();
        init.unkeep();
    }

    // IR construction using the current parser context

    ConstantNode con( long con ) { return _code.con(con); }
    ConstantNode con( Type t   ) { return _code.con(t  ); }

    // A semantic graph partition representing the complete memory after a
    // precise alias update.  ScopeNode treats this as one opaque value.
    Node mergeAlias(Node bulk, int alias, Node precise) {
        assert bulk != null : "Missing bulk memory for alias #"+alias;
        MemMergeNode merge = new MemMergeNode(null,bulk);
        merge.alias(alias,precise);
        return peep(merge);
    }
    // Field offset for an unknown type
    public Node fldoff( Node n, String name ) {
        if( n._type instanceof TypeMemPtr ptr && ptr._obj._fref )
            _requiredTypes.add(ptr._obj._name);
        return new FldOffNode(n,name,loc()).peephole();
    }
    public Node peep( Node n ) {
        // Peephole, then improve with lexically scoped guards
        return _scope.upcastGuard(n.peephole());
    }

    // Diagnostics

    ParseException errorSyntax(String syntax) { return _errorSyntax("expected `"+syntax+"`");  }
    private ParseException _errorSyntax(String msg) {
        return error("Syntax error, "+msg+" but found `" + _lexer.getAnyNextToken()+"`");
    }
    ParseException error(String msg) { return error(msg,_lexer); }
    public static ParseException error(String msg, Lexer loc) { return new ParseException(msg,loc); }

    // Debugging

    @Override
    public String toString() { return _lexer==null ? "" : _lexer.toString(); }

    // Debugging utility to find a Node by index
    public Node f(int nid) { return _code.f(nid); }
}
