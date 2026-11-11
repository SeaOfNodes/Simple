package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.codegen.Serialize;
import com.seaofnodes.simple.codegen.SourceError;
import com.seaofnodes.simple.parser.Parser.ParseException;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import java.util.HashMap;
import java.io.ByteArrayOutputStream;

// Lexer provides low level access to the raw file bytes, peeks and matches
// short strings, parses numbers, skips comments and whitespace, tracks
// line numbers, allows the parse position to be saved and restored, and
// serves as a location indicator for errors.

public class Lexer {

    // Input buffer; an array of text bytes read from a file or a string
    private final byte[] _input;
    // Original source, retained across inlining.
    public final CompUnit _cu;
    // Tracks current position in input buffer
    int _position = 0;

    //
    private int _line_number = 1;
    // Start of current line
    private int _line_start = 0;

    /** True when this location precedes {@code that} in the same source. */
    public boolean before(Lexer that) {
        return that != null && (_cu!=null ? _cu==that._cu : _input==that._input) && _position < that._position;
    }

    public String position() { return _line_number+":"+(_position-_line_start+1); }
    public int pos() { return _position; }

    public String diagnostic(String message) {
        return _cu==null ? SourceError.format("<input>",_input,_position,message)
            : SourceError.format(_cu,_position,message);
    }

    /**
     * Record the source text for lexing
     */
    public Lexer(String source) {
        this(source.getBytes());
    }

    /**
     * Direct from disk file source
     */
    public Lexer(byte[] buf) {
        _input = buf;
        _cu = null;
    }

    Lexer(CompUnit cu) {
        _input = cu._src.getBytes();
        _cu = cu;
    }

    // Imported location: source text is only needed when reporting an error.
    Lexer(CompUnit cu, int pos) {
        _input = null;
        _cu = cu;
        _position = pos;
    }

    public static void gather(Lexer loc, HashMap<String,Integer> strs) {
        if( loc==null || loc._cu==null ) return;
        Serialize.gather(strs,loc._cu._cname);
        String file = loc._cu.sourceFile();
        if( file!=null ) Serialize.gather(strs,file);
    }

    public static void packed(Lexer loc, BAOS out, HashMap<String,Integer> strs) {
        if( loc==null || loc._cu==null ) { out.packed4(0); return; }
        out.packed4(loc._position+1);
        out.packed4(strs.get(loc._cu._cname));
        String file = loc._cu.sourceFile();
        out.packed4(file==null ? 0 : strs.get(file));
    }

    public static Lexer unpack(BAOS in, String[] strs) {
        int pos = in.packed4();
        if( pos==0 ) return null;
        String owner = strs[in.packed4()];
        String file = strs[in.packed4()];
        CompUnit cu = CodeGen.CODE._compunits.get(owner.replace('.','/'));
        assert cu!=null : owner;
        if( !file.isEmpty() ) cu._sourceFile = file;
        return new Lexer(cu,pos-1);
    }

    /**
     *  Copy a lexer from a lexer
     */
    Lexer(Lexer l) {
        _input = l._input;
        _cu = l._cu;
        _position = l._position;
        _line_number = l._line_number;
        _line_start = l._line_start;
    }

    // Very handy in the debugger, shows the unparsed program
    @Override
    public String toString() {
        return _input==null ? _cu._cname+"@"+_position : new String(_input, _position, _input.length - _position);
    }

    // True if at EOF
    boolean isEOF() {
        return _position >= _input.length;
    }

    // Peek next character, or report EOF
    char peek() {
        return isEOF() ? Character.MAX_VALUE   // Special value that causes parsing to terminate
                : (char) _input[_position];
    }
    // Just crash if misused
    public byte peek(int off) { return _input[_position+off]; }

    private void inc() {
        if( _position++ < _input.length && _input[_position-1] == '\n' ) {
            _line_number++;
            _line_start = _position;
        }
    }
    // Does not honor LF, so caller must roll back position on a LF
    private char nextChar() {
        char ch = peek();
        inc();
        return ch;
    }

    // True if a white space
    private boolean isWhiteSpace() {
        return peek() <= ' '; // Includes all the use space, tab, newline, CR
    }

    /**
     * Return the next non-white-space character
     */
    void skipWhiteSpace() {
        while( true ) {
            if( isWhiteSpace() ) inc();
            // Skip // to end of line
            else if( _position+1 < _input.length &&
                     _input[_position  ] == '/' &&
                     _input[_position+1] == '/') {
                inc(); inc();
                while( !isEOF() && _input[_position] != '\n' )
                    inc();
            } else if( _position+1 < _input.length &&
                     _input[_position  ] == '/' &&
                     _input[_position+1] == '*') {
                Lexer start=new Lexer(this);
                inc(); inc();
                while( !isEOF() && !(peek()=='*' && _position+1<_input.length && peek(1)=='/') )
                    inc();
                if( isEOF() ) throw Parser.error("Unclosed block comment",start);
                inc(); inc();
            } else break;
        }
    }

    // Return true, if we find "syntax" after skipping white space; also
    // then advance the cursor past syntax.
    // Return false otherwise, and do not advance the cursor.
    boolean match(String syntax) {
        assert syntax.indexOf('\n')==-1; // No newlines in match
        skipWhiteSpace();
        int len = syntax.length();
        if (_position + len > _input.length) return false;
        for (int i = 0; i < len; i++)
            if ((char) _input[_position + i] != syntax.charAt(i))
                return false;
        _position += len;
        return true;
    }

    // Match must be exact and not followed by more ID characters.
    // Prevents identifier "ifxy" from matching an "if" statement.
    boolean matchx(String syntax) {
        if( !match(syntax) ) return false;
        if( !isIdLetter(peek()) ) return true;
        _position -= syntax.length();
        return false;
    }
    // Match this char, and the next char must be different.
    // Handles '&&' vs '&'
    boolean matchOp( char c0 ) {
        skipWhiteSpace();
        if( _position+1 >= _input.length || _input[_position]!=c0 || _input[_position+1]==c0 )
            return false;
        inc();
        return true;
    }
    // Match these two characters in a row
    boolean matchOpx(char c0, char c1) {
        skipWhiteSpace();
        if( _position+1 >= _input.length || _input[_position]!=c0 || _input[_position+1]==c1 )
            return false;
        inc();
        return true;
    }

    boolean peek(char ch) {
        skipWhiteSpace();
        return peek()==ch;
    }

    boolean peekIsId() {
        skipWhiteSpace();
        return isIdStart(peek());
    }

    // Return an identifier or null
    String matchId() {
        return peekIsId() ? parseId() : null;
    }

    // Used for errors
    String getAnyNextToken() {
        if (isEOF()) return "EOF";
        if (isIdStart(peek())) return parseId();
        if (isNumber(peek())) return parseNumberString();
        if (isPunctuation(peek())) return parsePunctuation();
        return String.valueOf(peek());
    }

    boolean isNumber(char ch) {return Character.isDigit(ch);}

    // Return a constant Type, either TypeInteger or TypeFloat
    Type parseNumber() {
        Lexer start=new Lexer(this);
        if( isHex() ) return parseInteger(parseNumberString(),16,2,start);
        int old = _position;
        int len = isLongOrDouble();
        boolean f32 = peek()=='f';
        if( f32 ) nextChar();
        int n = Math.abs(len);
        String literal=new String(_input,old,n);
        try {
            if( f32 )
                return TypeFloat.constant(Float.parseFloat(literal));
            if( len > 0 ) {
                if( len > 1 && _input[old]=='0' )
                    return parseInteger(literal,8,0,start);
                long i = Long.parseLong(literal);
                return TypeInteger.constant(i);
            }
            return TypeFloat.constant(Double.parseDouble(literal));
        } catch( NumberFormatException ex ) {
            throw Parser.error(len>0 && !f32 ? "Integer literal is outside the signed 64-bit range"
                : "Invalid floating-point literal '"+literal+(f32 ? "f" : "")+"'",start);
        }
    }
    private String parseNumberString() {
        int old = _position;
        if( isHex() ) {
            _position += 2;
            while( isIdLetter(peek()) ) inc();
            return new String(_input,old,_position-old);
        }
        int len = Math.abs(isLongOrDouble());
        if( peek()=='f' ) {
            nextChar();
            len++;
        }
        return new String(_input,old,len);
    }

    private boolean isHex() {
        return peek()=='0' && _position+1<_input.length && (peek(1)=='x' || peek(1)=='X');
    }

    private TypeInteger parseInteger(String literal, int radix, int prefix, Lexer start) {
        String kind = radix==16 ? "Hexadecimal" : "Octal";
        if( literal.length()==prefix )
            throw Parser.error(kind+" integer literal requires at least one digit",start);
        for( int i=prefix; i<literal.length(); i++ )
            if( Character.digit(literal.charAt(i),radix)<0 )
                throw Parser.error("Invalid "+kind.toLowerCase()+" integer literal '"+literal+"'",start);
        try {
            return TypeInteger.constant(Long.parseUnsignedLong(literal.substring(prefix),radix));
        } catch( NumberFormatException ex ) {
            throw Parser.error(kind+" integer literal exceeds 64 bits",start);
        }
    }

    // Return +len that ends a long
    // Return -len that ends a double
    private int isLongOrDouble() {
        int old = _position;
        char c;
        while( Character.isDigit(c = nextChar()) ) ;
        if( !(c=='e' || c=='.') )
            return --_position - old;
        if( peek() == '-' ) nextChar();
        while( Character.isDigit(c = nextChar()) || c=='e' || c=='.' ) ;
        return -(--_position - old);
    }

    // First letter of an identifier
    private boolean isIdStart(char ch) {
        return Character.isAlphabetic(ch) || ch == '_';
    }

    // All characters of an identifier, e.g. "_x123"
    private boolean isIdLetter(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private String parseId() {
        int start = _position;
        while (isIdLetter(nextChar())) ;
        return new String(_input, start, --_position - start).intern();
    }

    //
    private boolean isPunctuation(char ch) {
        return "=;[]<>()+-/*&|^".indexOf(ch) != -1;
    }

    private String parsePunctuation() {
        int start = _position;
        return new String(_input, start, 1);
    }

    // Next oper= character, or 0.
    // As a convenience, mark "++" as a char 1 and "--" as char -1 (65535)
    // Distinct tags for the multi-character compound operators.
    static final char SHL = 2, SAR = 3, SHR = 4;

    // ch is +/- 1, means oper++ or oper-- means postfix.
    static boolean postfix(char ch) {
        return (byte)ch== 1 || (byte)ch== -1;
    }

    public char matchOperAssign() {
        skipWhiteSpace();
        if( match("<<=" ) ) return SHL;
        if( match(">>>=") ) return SHR;
        if( match(">>=" ) ) return SAR;
        if( _position+2 >= _input.length ) return 0;
        char ch0 = (char)_input[_position];
        if( "+-/*&|^".indexOf(ch0) == -1 ) return 0;
        char ch1 = (char)_input[_position+1];
        if(               ch1 == '=' ) { _position += 2; return ch0; }
        if( isIdLetter((char)_input[_position+2]) ) return 0;
        if( ch0 == '+' && ch1 == '+' ) { _position += 2; return (char) 1; }
        if( ch0 == '-' && ch1 == '-' ) { _position += 2; return (char)-1; }
        return 0;
    }

    record StringPart(byte[] text, boolean end) {}

    // Opening quote has been consumed. Leave a format's '%' unread so both
    // the expression parser and deferred-body scanner use the same grammar.
    StringPart stringPart(Lexer start) {
        ByteArrayOutputStream sb = new ByteArrayOutputStream();
        while( !isEOF() ) {
            char c = nextChar();
            if( c=='"' ) return new StringPart(sb.toByteArray(),true);
            if( c=='%' ) {
                if( peek()=='%' ) { inc(); sb.write('%'); continue; }
                _position--;
                return new StringPart(sb.toByteArray(),false);
            }
            if( c!='\\' ) {
                sb.write(c);
                continue;
            }
            if( isEOF() )
                throw Parser.error("Unclosed string",start);
            sb.write(escapedChar("string"));
        }
        throw Parser.error("Unclosed string",start);
    }

    record Format(int kind, int flags, int width, int precision, Lexer site) {}

    Format stringFormat() {
        Lexer site = new Lexer(this);
        assert peek()=='%';
        inc();
        if( peek()=='(' ) { inc(); return new Format(0,0,0,-1,site); }
        int flags=0, bit;
        while( (bit="-+ #0".indexOf(peek()))>=0 ) {
            int mask=1<<bit;
            if( (flags&mask)!=0 ) throw Parser.error("Repeated format flag",site);
            flags |= mask;
            inc();
        }
        int width = formatNumber(site);
        int precision = -1;
        if( peek()=='.' ) {
            inc();
            precision = formatNumber(site);
        }
        char kind=nextChar();
        if( "diuoxXfFeEgGaAcsb".indexOf(kind)<0 )
            throw Parser.error("Expected a string format: %(expression), %d(expression), or %%",site);
        boolean number="diuoxXfFeEgGaA".indexOf(kind)>=0;
        if( !number && (flags&~1)!=0 || "uoxX".indexOf(kind)>=0 && (flags&6)!=0 ||
            "diu".indexOf(kind)>=0 && (flags&8)!=0 )
            throw Parser.error("Invalid flags for %"+kind,site);
        if( precision>=0 && (kind=='c' || kind=='b') )
            throw Parser.error("Precision is not supported for %"+kind,site);
        if( peek()!='(' ) throw Parser.error("Expected '(' after string format %"+kind,site);
        inc();
        return new Format(kind,flags,width,precision,site);
    }

    private int formatNumber(Lexer site) {
        long n=0;
        while( peek()>='0' && peek()<='9' ) {
            n=n*10+nextChar()-'0';
            if( n>Integer.MAX_VALUE ) throw Parser.error("String format width or precision is too large",site);
        }
        return (int)n;
    }

    private char escapedChar(String kind) {
        Lexer start=new Lexer(this);
        start._position--; // The backslash introducing the escape.
        char esc = nextChar();
        return switch( esc ) {
        case '0'  -> '\u0000';
        case 'n'  -> '\n';
        case 't'  -> '\t';
        case 'r'  -> '\r';
        case '\\' -> '\\';
        case '"'  -> '"';
        case '\'' -> '\'';
        case '%'  -> '%';
        default -> throw Parser.error("Unknown "+kind+" escape \\"+esc,start);
        };
    }

    char parseChar() {
        Lexer start=new Lexer(this);
        start._position--; // Opening quote was consumed by the caller.
        if( isEOF() || peek()=='\n' || peek()=='\r' ) throw Parser.error("Unclosed character literal",start);
        if( peek()=='\'' ) throw Parser.error("Empty character literal",start);
        char c = nextChar();
        if( c=='\\' ) {
            if( isEOF() ) throw Parser.error("Unclosed character literal",start);
            c = escapedChar("character");
        }
        if( isEOF() || peek()=='\n' || peek()=='\r' ) throw Parser.error("Unclosed character literal",start);
        if( peek()!='\'' ) throw Parser.error("Character literal must contain exactly one byte",start);
        inc();
        return c;
    }

    private ParseException error(String msg) { return Parser.error(msg,this); }

    // Scan a deferred fragment, leaving its unmatched closing delimiter unread.
    // Comments, quoted text, and escapes do not affect the nesting depth.
    void skipTo(char open, char close, String what) {
        int depth = 0;
        while( true ) {
            skipWhiteSpace();
            if( isEOF() ) throw error("Unclosed "+what);
            char ch = peek();
            if( ch==close && depth==0 ) return;
            Lexer site=ch=='"' ? new Lexer(this) : null;
            nextChar();
            if( ch==open ) depth++;
            else if( ch==close ) depth--;
            else if( ch=='"' ) {
                while( !stringPart(site).end() ) {
                    stringFormat();
                    skipTo('(',')',"string interpolation");
                    nextChar();
                }
            } else if( ch=='\'' ) {
                parseChar();
            }
        }
    }

}
