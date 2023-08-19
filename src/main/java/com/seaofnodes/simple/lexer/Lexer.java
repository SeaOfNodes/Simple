/**
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Contributor(s):
 *
 * The Initial Developer of the Original Software is Cliff Click.
 *
 * This file is part of the Sea Of Nodes Simple Programming language
 * implementation. See https://github.com/SeaOfNodes/Simple
 *
 * The contents of this file are subject to the terms of the
 * Apache License Version 2 (the "APL"). You may not use this
 * file except in compliance with the License. A copy of the
 * APL may be obtained from:
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.seaofnodes.simple.lexer;

import com.seaofnodes.util.AryInt;
import com.seaofnodes.util.SB;

import java.text.NumberFormat;
import java.text.ParsePosition;

public class Lexer {
    private final String _srcName;    // Source for error messages; usually a file name
    private final byte[] _buf;
    private int _x;               // Parser index
    private int _lastNWS;         // Index of last non-white-space char
    private final AryInt _lines;  // char offset of each line

    private final NumberFormat _nf;
    private final ParsePosition _pp;
    private final String _str;

    public Lexer(String srcName, String source)
    {
        _srcName = srcName;
        _buf = source.getBytes();
        _x   = 0;

        // Set fields strictly for Java number parsing
        _nf = NumberFormat.getInstance();
        _nf.setGroupingUsed(false);
        _pp = new ParsePosition(0);
        _str = source;           // Keep a complete string copy for java number parsing
        _lines = new AryInt();//
        _lines.push(0);       // Line 0 at offset 0
    }

    // Skip WS, return true&skip if match, false & do not skip if miss.
    public boolean peek( char c ) { return peek1(skipWS(),c); }
    public boolean peek_noWS( char c ) { return peek1(_x >= _buf.length ? -1 : _buf[_x],c); }
    // Already skipped WS & have character;
    // return true & skip if a match, false& do not skip if a miss.
    public boolean peek1( byte c0, char c ) {
        assert c0==-1 || c0== _buf[_x];
        if( c0!=c ) return false;
        _x++;                       // Skip peeked character
        return true;
    }
    // Already skipped WS & have character;
    // return true&skip if match, false & do not skip if miss.
    public boolean peek2( byte c0, String s2 ) {
        if( c0 != s2.charAt(0) ) return false;
        if( _x+1 >= _buf.length || _buf[_x+1] != s2.charAt(1) ) return false;
        _x+=2;                      // Skip peeked characters
        return true;
    }
    public boolean peek( String s ) {
        if( !peek(s.charAt(0)) ) return false;
        if( !peek_noWS(s.charAt(1)) ) {  _x--; return false; }
        return true;
    }
    // Peek 'c' and NOT followed by 'no'
    public boolean peek_not( char c, char no ) {
        byte c0 = skipWS();
        if( c0 != c || (_x+1 < _buf.length && _buf[_x+1] == no) ) return false;
        _x++;
        return true;
    }
    // Match any of these, and return the match or null
    public String peek(String[] toks) {
        for( String tok : toks ) if( peek1(tok) ) return tok;
        return null;
    }
    public boolean peek1(String tok) {
        for( int i=0; i<tok.length(); i++ )
            if( _x+i >= _buf.length || _buf[_x+i] != tok.charAt(i) )
                return false;
        return true;
    }


    /** Advance parse pointer to the first non-whitespace character, and return
     *  that character, -1 otherwise.  */
    public byte skipWS() {
        int oldx = _x;
        while( _x < _buf.length ) {
            byte c = _buf[_x];
            if( c=='/' && _x+1 < _buf.length && _buf[_x+1]=='/' ) { skipEOL()  ; continue; }
            if( c=='/' && _x+1 < _buf.length && _buf[_x+1]=='*' ) { skipBlock(); continue; }
            if( c=='\n' && _x+1 > _lines.last() ) _lines.push(_x+1);
            if( !isWS(c) ) {
                if( oldx-1 > _lastNWS && !isWS(_buf[oldx-1]) ) _lastNWS = oldx-1;
                return c;
            }
            _x++;
        }
        return -1;
    }
    public void skipEOL  () { while( _x < _buf.length && _buf[_x] != '\n' ) _x++; }
    public void skipBlock() { throw new UnsupportedOperationException("Block comments not yet implemented"); }
    // Advance parse pointer to next WS.  Used for parser syntax error recovery.
    public void skipNonWS() {
        while( _x < _buf.length && !isWS(_buf[_x]) ) _x++;
    }

    /** Return true if `c` passes a test */
    public static boolean isWS    (byte c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r'; }
    public static boolean isAlpha0(byte c) { return ('a'<=c && c <= 'z') || ('A'<=c && c <= 'Z') || (c=='_'); }
    public static boolean isAlpha1(byte c) { return isAlpha0(c) || ('0'<=c && c <= '9'); }
    public static boolean isJava  (byte c) { return isAlpha1(c) || (c=='$') || (c=='.'); }
    public  static boolean isDigit (byte c) { return '0' <= c && c <= '9'; }

    public String token() { skipWS();  return token0(); }
    // Lexical tokens.  Any alpha, followed by any alphanumerics is a alpha-
    // token; alpha-tokens end with WS or any operator character.  Any collection
    // of the classic operator characters are a token, except that they will break
    // un-ambiguously.
    public String token0() {
        if( _x >= _buf.length ) return null;
        byte c=_buf[_x];  int x = _x;
        if( isOp0(c) || (c=='_' && _x+1 < _buf.length && isOp0(_buf[_x+1])) )
            while( _x < _buf.length && isOp1(_buf[_x]) ) _x++;
        else if( isAlpha0(c) )
            while( _x < _buf.length && isAlpha1(_buf[_x]) ) _x++;
        else return null; // Not a token; specifically excludes e.g. all bytes >= 128, or most bytes < 32
        if( (c==':' || c==',') && _x-x==1 ) // Disallow bare ':' as a token; ambiguous with ?: and type annotations; same for ','
        { _x=x; return null; } // Unwind, not a token
        if( c=='-' && _x-x>2 && _buf[x+1]=='>' ) // Disallow leading "->", confusing with function parameter list end; eg "not={x->!x}"
            _x=x+2;                                // Just return the "->"
        return new String(_buf,x,_x-x);
    }
    public boolean isOp(String s) {
        if( s==null || s.isEmpty() ) return false;
        byte c = (byte)s.charAt(0);
        if( !isOp0(c) && (c!='_' || !isOp0((byte)s.charAt(1))) ) return false;
        for( int i=1; i<s.length(); i++ )
            if( !isOp1((byte)s.charAt(i)) ) return false;
        return true;
    }

    // Parse a number; WS already skipped and sitting at a digit.  Relies on
    // Javas number parsing.
    public Number number() {
        _pp.setIndex(_x);
        Number n = _nf.parse(_str,_pp);
        _x = _pp.getIndex();
        if( n instanceof Long   ) {
            if( _buf[_x-1]=='.' ) _x--; // Java default parser allows "1." as an integer; pushback the '.'
        }
        return n;
    }

    public int field_number() {
        byte c = _buf[_x];
        if( !isDigit(c) ) return -1;
        _x++;
        int sum = c-'0';
        while( _x < _buf.length && isDigit(c=_buf[_x]) ) {
            _x++;
            sum = sum*10+c-'0';
        }
        return sum;
    }

    /** Parse a String; _x is at '"'.
     *  str  = [.\%]*               // String contents; \t\n\r\% standard escapes
     *  str  = %[num]?[.num]?fact   // Percent escape embeds a 'fact' in a string; "name=%name\n"
     */
    public String string() {
        int oldx = ++_x;
        byte c;
        while( (c=_buf[_x++]) != '"' ) {
            if( c=='%' ) throw new UnsupportedOperationException("Not implemented");
            if( c=='\\' ) throw new UnsupportedOperationException("Not implemented");
            if( _x == _buf.length ) return null;
        }
        return new String(_buf,oldx,_x-oldx-1).intern();
    }

    public static boolean isOp0(byte c) { return "!#$%*+-=<>^[]~/&|".indexOf(c) != -1; }
    public static boolean isOp1(byte c) { return isOp0(c) || ":?_{}".indexOf(c) != -1; }

    public String errLocMsg(String s) {
        if( s.charAt(0)=='\n' ) return s;
        // find line start
        int a=_x;
        while( a > 0 && _buf[a-1] != '\n' ) --a;
        if( _buf[a]=='\r' ) a++; // do not include leading \n or \n\r
        // find line end
        int b=_x;
        while( b < _buf.length && _buf[b] != '\n' ) b++;
        if( b < _buf.length ) b--; // do not include trailing \n or \n\r
        // Find line number.  Bin-search returns the insertion-point, which is the NEXT
        // line unless _x is exactly a line start.
        int line = _lines.binary_search(_x); // Find zero-based line insertion point
        if( line == _lines._len ||  _lines.at(line)>_x ) line--;
        // error message using 1-based line
        SB sb = new SB().p(_srcName).p(':').p(line+1).p(':').p(s).nl();
        sb.p(new String(_buf,a,b-a)).nl();
        int line_start = a;
        for( int i=line_start; i<_x; i++ )
            sb.p(' ');
        return sb.p('^').nl().toString();
    }

    @Override public String toString() { return new String(_buf,_x,_buf.length-_x); }
    @Override public boolean equals(Object loc) {
        if( this==loc ) return true;
        if( !(loc instanceof Lexer p) ) return false;
        return _x==p._x && _srcName.equals(p._srcName);
    }
    @Override public int hashCode() {
        return _srcName.hashCode()+_x;
    }
}
