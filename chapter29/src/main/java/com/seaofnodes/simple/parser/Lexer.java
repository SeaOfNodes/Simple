package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.codegen.Serialize;
import com.seaofnodes.simple.parser.Parser.ParseException;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import java.util.HashMap;

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
            else if( _position+2 < _input.length &&
                     _input[_position  ] == '/' &&
                     _input[_position+1] == '/') {
                inc(); inc();
                while( !isEOF() && _input[_position] != '\n' )
                    inc();
            } else if( _position+2 < _input.length &&
                     _input[_position  ] == '/' &&
                     _input[_position+1] == '*') {
                // Skip /*comment*/
                while( !isEOF() && !(_input[_position-1] == '*' && _input[_position] == '/'))
                    inc();
                inc();
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
        int old = _position;
        int len = isLongOrDouble();
        boolean f32 = peek()=='f';
        if( f32 ) nextChar();
        int n = Math.abs(len);
        if( f32 )
            return TypeFloat.constant(Float.parseFloat(new String(_input,old,n)));
        if( len > 0 ) {
            if( len > 1 && _input[old]=='0' )
                throw Parser.error("Syntax error: integer values cannot start with '0'",this);
            long i = Long.parseLong(new String(_input,old,len));
            return TypeInteger.constant(i);
        }
        return TypeFloat.constant(Double.parseDouble(new String(_input,old,n)));
    }
    private String parseNumberString() {
        int old = _position;
        int len = Math.abs(isLongOrDouble());
        if( peek()=='f' ) {
            nextChar();
            len++;
        }
        return new String(_input,old,len);
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

    String parseString() {
        if( !peek('"') ) return null;
        inc();
        StringBuilder sb = new StringBuilder();
        while( !isEOF() ) {
            char c = nextChar();
            if( c=='"' ) return sb.toString();
            if( c!='\\' ) {
                sb.append(c);
                continue;
            }
            if( isEOF() )
                throw error("Unclosed string");
            sb.append(escapedChar("string"));
        }
        throw error("Unclosed string");
    }

    private char escapedChar(String kind) {
        char esc = nextChar();
        return switch( esc ) {
        case '0'  -> '\u0000';
        case 'n'  -> '\n';
        case 't'  -> '\t';
        case 'r'  -> '\r';
        case '\\' -> '\\';
        case '"'  -> '"';
        case '\'' -> '\'';
        default -> throw error("Unknown "+kind+" escape \\\\"+esc);
        };
    }

    char parseChar() {
        if( isEOF() ) throw error("Unclosed character");
        char c = nextChar();
        if( c=='\\' ) {
            if( isEOF() ) throw error("Unclosed character");
            c = escapedChar("character");
        }
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
            nextChar();
            if( ch==open ) depth++;
            else if( ch==close ) depth--;
            else if( ch=='"' || ch=='\'' ) {
                char quote = ch;
                while( !isEOF() ) {
                    ch = nextChar();
                    if( ch=='\\' ) nextChar();
                    else if( ch==quote ) break;
                }
            }
        }
    }

}
