/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Contributor(s):
 *
 * The Initial Developer of the Original Software is
 * The Ecstasy Project (https://github.com/xtclang/).
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

package com.seaofnodes.simple.lexer.ecstasy;


import java.util.HashMap;
import java.util.Map;

import static com.seaofnodes.util.Handy.appendChar;
import static com.seaofnodes.util.Handy.appendString;


/**
 * Representation of a language token.
 */
public class Token
        implements Cloneable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an XTC token.
     *
     * @param lStartPos  starting position in the Source (inclusive)
     * @param lEndPos    ending position in the Source (exclusive)
     * @param id         identity of the token
     */
    public Token(long lStartPos, long lEndPos, Id id)
        {
        this(lStartPos, lEndPos, id, null);
        }

    /**
     * Construct an XTC token.
     *
     * @param lStartPos  starting position in the Source (inclusive)
     * @param lEndPos    ending position in the Source (exclusive)
     * @param id         identity of the token
     * @param oValue     value of the token (if it is a literal)
     */
    public Token(long lStartPos, long lEndPos, Id id, Object oValue)
        {
        m_lStartPos = lStartPos;
        m_lEndPos   = lEndPos;
        m_id        = id;
        m_oValue    = oValue;
        }

    /**
     * Record whitespace information onto the token.
     *
     * @param fWhitespaceBefore
     * @param fWhitespaceAfter
     */
    public void noteWhitespace(boolean fWhitespaceBefore, boolean fWhitespaceAfter)
        {
        m_fLeadingWhitespace  = fWhitespaceBefore;
        m_fTrailingWhitespace = fWhitespaceAfter;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Determine the starting position in the source at which this token occurs.
     *
     * @return the Source position of the token
     */
    public long getStartPosition()
        {
        return m_lStartPos;
        }

    /**
     * Determine the ending position (exclusive) in the source for this token.
     *
     * @return the Source position of the end of the token
     */
    public long getEndPosition()
        {
        return m_lEndPos;
        }

    /**
     * Determine if this token follows whitespace in the source.
     *
     * @return true iff this token follows whitespace
     */
    public boolean hasLeadingWhitespace()
        {
        return m_fLeadingWhitespace;
        }

    /**
     * Determine if this token precedes whitespace in the source.
     *
     * @return true iff this token is followed by whitespace
     */
    public boolean hasTrailingWhitespace()
        {
        return m_fTrailingWhitespace;
        }

    /**
     * Determine the identity of the token.
     *
     * @return the identity of the token
     */
    public Id getId()
        {
        return m_id;
        }

    /**
     * Determine the value of the token.
     *
     * @return the value of the token, or null
     */
    public Object getValue()
        {
        return m_oValue;
        }

    /**
     * Helper to get the value as text, if there is a value, otherwise to get the token id's text.
     * Use only for debugging or when the token is known to be a String.
     *
     * @return the String form of the token
     */
    public String getValueText()
        {
        return m_oValue == null ? m_id.TEXT : m_oValue.toString();
        }

    /**
     * @return true iff this is an identifier is a "special name"
     */
    public boolean isSpecial()
        {
        if (m_id == Id.IDENTIFIER)
            {
            Id keywordId = Id.valueByContextSensitiveText((String) getValue());
            return keywordId != null && keywordId.Special;
            }

        return false;
        }

    /**
     * @return true iff this is an identifier is a context-sensitive name
     */
    public boolean isContextSensitive()
        {
        return m_id == Id.IDENTIFIER && Id.valueByContextSensitiveText((String) getValue()) != null;
        }

    public boolean isComment()
        {
        return m_id == Id.ENC_COMMENT || m_id == Id.EOL_COMMENT;
        }

    /**
     * If the token is an identifier that is also a context sensitive keyword, obtain that keyword.
     *
     * @return a keyword token that represents the same text as this identifier token
     */
    public Token convertToKeyword()
        {
        if (m_id != Id.IDENTIFIER)
            {
            throw new IllegalStateException("not an identifier! (" + this + ")");
            }

        Id id = Id.valueByContextSensitiveText((String) getValue());
        if (id == null)
            {
            throw new IllegalStateException("missing context sensitive keyword for: " + getValue());
            }

        return new Token(m_lStartPos, m_lEndPos, id, m_oValue);
        }

    /**
     * If the token is a context sensitive keyword that was an identifier, obtain the identifier.
     *
     * @return a token that is not a context sensitive keyword
     */
    public Token desensitize()
        {
        return m_id.ContextSensitive ? new Token(m_lStartPos, m_lEndPos, Id.IDENTIFIER, m_oValue) : this;
        }

    /**
     * Refine the token identity to a more specific identity.
     *
     * @param id  the refined identity
     */
    Token refine(Id id)
        {
        m_id = id;
        return this;
        }

    /**
     * Allow a token to be "peeled off" the front of this token, if possible. This mutates this
     * token, such that it is what remains after the returned token was peeled off.
     *
     * @param id      the token to peel off of this token
     * @param source  the source from which this token was created, which is necessary in order to
     *                calculate the exact position between any peeled-off token and this token
     *
     * @return the requested token that was peeled off of this token, if possible, otherwise null
     */
    public Token peel(Id id, Source source)
        {
        Id newId = null;

        if (id == Id.COMP_GT)
            {
            switch (m_id)
                {
                default:
                    return null;

                case USHR_ASN:
                    newId = Id.SHR_ASN;
                    break;

                case USHR:
                    newId = Id.SHR;
                    break;

                case SHR_ASN:
                    newId = Id.COMP_GTEQ;
                    break;

                case SHR:
                    newId = Id.COMP_GT;
                    break;

                case COMP_GTEQ:
                    newId = Id.ASN;
                    break;
                }
            }
        else if (id == Id.COMP_LT)
            {
            switch (m_id)
                {
                default:
                    return null;

                case SHL_ASN:
                    newId = Id.COMP_LTEQ;
                    break;

                case SHL:
                    newId = Id.COMP_LT;
                    break;

                // note: there are no legitimate use cases for peeling "<" off of "<=" or "<=>"
                // case COMP_LTEQ:
                //     newId = Id.ASN;
                //     break;
                }
            }

        if (newId == null)
            {
            return null;
            }

        // get the location of "this" token
        long start  = m_lStartPos;

        // get the location of the end of the new peeled token / start of this token (adjusted)
        long current = source.getPosition();
        source.setPosition(start);
        source.next();
        long middle = source.getPosition();
        source.setPosition(current);

        // adjust this token
        m_lStartPos = middle;
        m_id        = newId;

        // return the new token
        return new Token(start, middle, id);
        }

    /**
     * Allow a token to be "un-peeled off" from the front of that following token, if possible.
     *
     * @param that  the token following this token
     *
     * @return the new token, or null if the two tokens cannot be annealed
     */
    public Token anneal(Token that)
        {
        if (m_id == Id.COMP_LT && this.m_lEndPos == that.m_lStartPos)
            {
            switch (that.m_id)
                {
                case COMP_LTEQ:
                    return new Token(this.m_lStartPos, that.m_lEndPos, Id.SHL_ASN);

                case COMP_LT:
                    return new Token(this.m_lStartPos, that.m_lEndPos, Id.SHL);
                }
            }

        return null;
        }

    /**
     * Obtain the actual string of characters from the source code for this token.
     *
     * @param source  the source code
     *
     * @return the string of characters corresponding to this token, extracted from the source
     */
    public String getString(Source source)
        {
        return source.toString(m_lStartPos, m_lEndPos);
        }

    /**
     * Helper to log an error related to this Token.
     *
     * @param severity   the severity level of the error; one of {@link Severity#INFO},
     *                   {@link Severity#WARNING}, {@link Severity#ERROR}, or {@link Severity#FATAL}
     * @param sCode      the error code that identifies the error message
     * @param aoParam    the parameters for the error message; may be null
     *
     * @return true to attempt to abort the process that reported the error, or false to attempt to
     *         continue the process
     */
    public void log(ErrorListener errs, Source source, Severity severity, String sCode, Object... aoParam)
        {
        if (aoParam == null || aoParam.length == 0)
            {
            aoParam = new Object[] {source == null ? toString() : getString(source)};
            }

        errs.log(severity, sCode, aoParam, source,
                source == null ? 0L : getStartPosition(), source == null ? 0L : getEndPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    public String toDebugString()
        {
        return "[" +
                Source.calculateLine(m_lStartPos) +
                "," +
                Source.calculateOffset(m_lStartPos) +
                " - " +
                Source.calculateLine(m_lEndPos) +
                "," +
                Source.calculateOffset(m_lEndPos) +
                "] " + this;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        switch (m_id)
            {
            case LIT_CHAR:
                {
                sb.append('\'');
                appendChar(sb, (Character) m_oValue);
                sb.append('\'');
                }
                break;

            case LIT_STRING:
                sb.append('\"');
                appendString(sb, (String) m_oValue);
                sb.append('\"');
                break;

            case LIT_INT:
            case LIT_DEC:
            case LIT_FLOAT:
                sb.append(m_oValue);
                break;

            case LIT_BIT:
            case LIT_NIBBLE:
            case LIT_INT8:
            case LIT_INT16:
            case LIT_INT32:
            case LIT_INT64:
            case LIT_INT128:
            case LIT_INTN:
            case LIT_UINT8:
            case LIT_UINT16:
            case LIT_UINT32:
            case LIT_UINT64:
            case LIT_UINT128:
            case LIT_UINTN:
            case LIT_DEC32:
            case LIT_DEC64:
            case LIT_DEC128:
            case LIT_DECN:
            case LIT_FLOAT16:
            case LIT_FLOAT32:
            case LIT_FLOAT64:
            case LIT_FLOAT128:
            case LIT_FLOATN:
            case LIT_BFLOAT16:
            case TYPE_INT:
            case TYPE_FLOAT:
            case TYPE_CHAR:
                sb.append(m_id.TEXT)
                  .append(':')
                  .append(m_oValue);
                break;

            case IDENTIFIER:
                sb.append("name:")
                  .append(m_oValue);
                break;

            case ENC_COMMENT:
                {
                String sComment = (String) m_oValue;
                if (sComment.length() > 47)
                    {
                    sComment = sComment.substring(0, 44) + "...";
                    }
                appendString(sb.append("/*"), sComment).append("*/");
                }
                break;

            case EOL_COMMENT:
                {
                String sComment = (String) m_oValue;
                if (sComment.length() > 50)
                    {
                    sComment = sComment.substring(0, 47) + "...";
                    }
                appendString(sb.append("//"), sComment);
                }
                break;

            default:
                sb.append(m_id.TEXT);
                break;
            }

        return sb.toString();
        }

    public Token clone()
        {
        try
            {
            return (Token) super.clone();
            }
        catch (CloneNotSupportedException e)
            {
            throw new IllegalStateException(e);
            }
        }


    // ----- Token identities ----------------------------------------------------------------------

    /**
     * Token Identity.
     */
    public enum Id
        {
        COLON        (":"              ),
        SEMICOLON    (";"              ),
        COMMA        (","              ),
        DOT          ("."              ),
        I_RANGE_I    (".."             ),   // inclusive range
        E_RANGE_I    (">.."            ),   // exclusive start
        I_RANGE_E    ("..<"            ),   // exclusive end
        E_RANGE_E    (">..<"           ),   // exclusive range
        DIR_CUR      ("./"             ),
        DIR_PARENT   ("../"            ),
        STR_FILE     ("$"              ),
        BIN_FILE     ("#"              ),
        AT           ("@"              ),
        COND         ("?"              ),
        L_PAREN      ("("              ),
        ASYNC_PAREN  ("^("             ),
        R_PAREN      (")"              ),
        L_CURLY      ("{"              ),
        R_CURLY      ("}"              ),
        L_SQUARE     ("["              ),
        R_SQUARE     ("]"              ),
        ADD          ("+"              ),
        SUB          ("-"              ),
        MUL          ("*"              ),
        DIV          ("/"              ),
        MOD          ("%"              ),
        DIVREM       ("/%"             ),
        SHL          ("<<"             ),
        SHR          (">>"             ),
        USHR         (">>>"            ),
        BIT_AND      ("&"              ),
        BIT_OR       ("|"              ),
        BIT_XOR      ("^"              ),
        BIT_NOT      ("~"              ),
        NOT          ("!"              ),
        COND_AND     ("&&"             ),
        COND_XOR     ("^^"             ),
        COND_OR      ("||"             ),
        COND_ELSE    ("?:"             ),
        ASN          ("="              ),
        ADD_ASN      ("+="             ),
        SUB_ASN      ("-="             ),
        MUL_ASN      ("*="             ),
        DIV_ASN      ("/="             ),
        MOD_ASN      ("%="             ),
        SHL_ASN      ("<<="            ),
        SHR_ASN      (">>="            ),
        USHR_ASN     (">>>="           ),
        BIT_AND_ASN  ("&="             ),
        BIT_OR_ASN   ("|="             ),
        BIT_XOR_ASN  ("^="             ),
        COND_AND_ASN ("&&="            ),
        COND_OR_ASN  ("||="            ),
        COND_ASN     (":="             ),
        COND_NN_ASN  ("?="             ),       // NN -> Not Null
        COND_ELSE_ASN("?:="            ),
        COMP_EQ      ("=="             ),
        COMP_NEQ     ("!="             ),
        COMP_ORD     ("<=>"            ),
        COMP_LT      ("<"              ),
        COMP_LTEQ    ("<="             ),
        COMP_GT      (">"              ),
        COMP_GTEQ    (">="             ),
        INC          ("++"             ),
        DEC          ("--"             ),
        LAMBDA       ("->"             ),
        ASN_EXPR     ("<-"             ),
        ANY          ("_"              ),
        ALLOW        ("allow"          , true),
        AS           ("as"             ),
        ASSERT       ("assert"         ),
        ASSERT_RND   ("assert:rnd"     ),
        ASSERT_ARG   ("assert:arg"     ),
        ASSERT_BOUNDS("assert:bounds"  ),
        ASSERT_TODO  ("assert:TODO"    ),
        ASSERT_ONCE  ("assert:once"    ),
        ASSERT_TEST  ("assert:test"    ),
        ASSERT_DBG   ("assert:debug"   ),
        AVOID        ("avoid"          , true),
        BREAK        ("break"          ),
        CASE         ("case"           ),
        CATCH        ("catch"          ),
        CLASS        ("class"          ),
        CONDITIONAL  ("conditional"    ),
        CONST        ("const"          ),
        CONSTRUCT    ("construct"      ),
        CONTINUE     ("continue"       ),
        DEFAULT      ("default"        ),
        DELEGATES    ("delegates"      , true),
        DESIRED      ("desired"        , true),
        DO           ("do"             ),
        ELSE         ("else"           ),
        EMBEDDED     ("embedded"       , true),
        ENUM         ("enum"           ),
        EXTENDS      ("extends"        , true),
        FINALLY      ("finally"        ),
        FOR          ("for"            ),
        FUNCTION     ("function"       ),
        IF           ("if"             ),
        IMMUTABLE    ("immutable"      ),
        IMPLEMENTS   ("implements"     , true),
        IMPORT       ("import"         ),
        INCORPORATES ("incorporates"   , true),
        INJECT       ("inject"         , true),
        INTERFACE    ("interface"      ),
        INTO         ("into"           , true),
        IS           ("is"             ),
        MIXIN        ("mixin"          ),
        MODULE       ("module"         ),
        NEW          ("new"            ),
        OPTIONAL     ("optional"       , true),
        OUTER        ("outer"          , true, true),
        PACKAGE      ("package"        ),
        PREFER       ("prefer"         , true),
        PRIVATE      ("private"        ),
        PROTECTED    ("protected"      ),
        PUBLIC       ("public"         ),
        REQUIRED     ("required"       , true),
        RETURN       ("return"         ),
        SERVICE      ("service"        ),
        STATIC       ("static"         ),
        STRUCT       ("struct"         ),
        SUPER        ("super"          , true, true),
        SWITCH       ("switch"         ),
        THIS         ("this"           , true, true),
        THIS_CLASS   ("this:class"     , true, true),
        THIS_MODULE  ("this:module"    , true, true),
        THIS_PRI     ("this:private"   , true, true),
        THIS_PRO     ("this:protected" , true, true),
        THIS_PUB     ("this:public"    , true, true),
        THIS_SERV    ("this:service"   , true, true),
        THIS_STRUCT  ("this:struct"    , true, true),
        THIS_TARGET  ("this:target"    , true, true),
        THROW        ("throw"          ),
        TODO         ("TODO"           ),
        TRY          ("try"            ),
        TYPEDEF      ("typedef"        ),
        USING        ("using"          ),
        VAL          ("val"            , true),
        VAR          ("var"            , true),
        VOID         ("void"           ),
        WHILE        ("while"          ),
        IDENTIFIER   (null             ),
        EOL_COMMENT  (null             ),
        ENC_COMMENT  (null             ),

        // Simple language type keywords
        TYPE_INT     ("int"            , false, true),
        TYPE_FLOAT   ("float"          , false, true),
        TYPE_CHAR    ("char"           , false, true),
        // End Simple language type keywords

        LIT_BIT      (null             ),
        LIT_NIBBLE   (null             ),
        LIT_CHAR     (null             ),
        LIT_STRING   (null             ),
        LIT_BINSTR   (null             ),
        LIT_INT      (null             ),               // integer literal
        LIT_INTA     ("Int"            , true, true),   // automatically sized integer
        LIT_INT8     ("Int8"           , true, true),
        LIT_INT16    ("Int16"          , true, true),
        LIT_INT32    ("Int32"          , true, true),
        LIT_INT64    ("Int64"          , true, true),
        LIT_INT128   ("Int128"         , true, true),
        LIT_INTN     ("IntN"           , true, true),
        LIT_UINT8    ("UInt8"          , true, true),
        LIT_UINTA    ("UInt"           , true, true),   // automatically sized unsigned integer
        LIT_UINT16   ("UInt16"         , true, true),
        LIT_UINT32   ("UInt32"         , true, true),
        LIT_UINT64   ("UInt64"         , true, true),
        LIT_UINT128  ("UInt128"        , true, true),
        LIT_UINTN    ("UIntN"          , true, true),
        LIT_DEC      (null             ),               // decimal floating point literal
        LIT_DECA     ("Dec"            , true, true),   // automatically sized decimal
        LIT_DEC32    ("Dec32"          , true, true),
        LIT_DEC64    ("Dec64"          , true, true),
        LIT_DEC128   ("Dec128"         , true, true),
        LIT_DECN     ("DecN"           , true, true),
        LIT_FLOAT    (null             ),               // binary floating point literal
        LIT_FLOAT8E4 ("Float8e4"       , true, true),
        LIT_FLOAT8E5 ("Float8e5"       , true, true),
        LIT_BFLOAT16 ("BFloat16"       , true, true),
        LIT_FLOAT16  ("Float16"        , true, true),
        LIT_FLOAT32  ("Float32"        , true, true),
        LIT_FLOAT64  ("Float64"        , true, true),
        LIT_FLOAT128 ("Float128"       , true, true),
        LIT_FLOATN   ("FloatN"         , true, true),
        LIT_DATE     (null             ),
        LIT_TIMEOFDAY(null             ),
        LIT_TIME     (null             ),
        LIT_TIMEZONE (null             ),
        LIT_DURATION (null             ),
        LIT_VERSION  (null             ),
        LIT_PATH     (null             ),               // generated by the Parser, not by the Lexer
        TEMPLATE     ("{...}"          ),               // not a real token
        EOF          ("EOF"            ),               // end of token stream
        ENUM_VAL     ("enum-value"     );               // not a real token

        /**
         * Constructor.
         *
         * @param sText  a textual representation of the token, or null
         */
        Id(final String sText)
            {
            this(sText, false);
            }

        /**
         * Constructor.
         *
         * @param sText  a textual representation of the token, or null
         */
        Id(final String sText, boolean fContextSensitive)
            {
            this(sText, fContextSensitive, false);
            }

        Id(final String sText, boolean fContextSensitive, boolean fSpecial)
            {
            TEXT = sText;
            this.ContextSensitive = fContextSensitive;
            this.Special          = fSpecial;
            }

        @Override
        public String toString()
            {
            return TEXT == null ? super.toString() : '\"' + TEXT + '"';
            }

        /**
         * Look up an Id enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Format enum for the specified ordinal
         */
        public static Id valueOf(int i)
            {
            return IDs[i];
            }

        /**
         * Look up an Id enum by its {@link #TEXT}.
         *
         * @param sText  the textual representation of the Id
         *
         * @return an instance of Id, or null if there is no matching
         *         {@link #TEXT}
         */
        public static Id valueByText(String sText)
            {
            return KEYWORDS.get(sText);
            }

        /**
         * Look up an Id enum by its {@link #TEXT}, including context-sensitive keywords.
         *
         * @param sText  the textual representation of the Id
         *
         * @return an instance of Id, or null if there is no matching
         *         {@link #TEXT}
         */
        public static Id valueByContextSensitiveText(String sText)
            {
            return ALL_KEYWORDS.get(sText);
            }

        /**
         * Look up an Id enum by its {@link #TEXT}, and if it is one of the keywords that has both
         * a normal and one-or-more suffixed forms, then return the Id of the normal form.
         *
         * @param sText  the possible keyword
         *
         * @return the Id of the "normal form" of the keyword, iff suffixed forms also exist
         */
        public static Id valueByPrefix(String sText)
            {
            return PREFIXES.get(sText);
            }

        /**
         * All of the Format enums.
         */
        private static final Id[] IDs = Id.values();

        /**
         * String representations of tokens that have constant representations, excluding context-
         * sensitive keywords.
         */
        private static final Map<String, Id> KEYWORDS = new HashMap<>();
        /**
         * String representations of all tokens that have constant representations.
         */
        private static final Map<String, Id> ALL_KEYWORDS = new HashMap<>();

        /**
         * String representations of tokens that have both "normal" and "suffixed" representations.
         */
        private static final Map<String, Id> PREFIXES = new HashMap<>();

        static
            {
            for (Id id : IDs)
                {
                String sText = id.TEXT;
                if (sText != null && sText.length() > 0)
                    {
                    char ch = sText.charAt(0);
                    if (ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch == '_')
                        {
                        ALL_KEYWORDS.put(sText, id);

                        if (!id.ContextSensitive)
                            {
                            KEYWORDS.put(sText, id);
                            }

                        int ofColon = sText.indexOf(':');
                        if (ofColon > 0)
                            {
                            Id prefix = ALL_KEYWORDS.get(sText.substring(0, ofColon));
                            if (prefix != null)
                                {
                                PREFIXES.put(prefix.TEXT, prefix);
                                }
                            }
                        }
                    }
                }
            }

        /**
         * A textual representation of the token, if it has a constant textual representation;
         * otherwise null.
         */
        final public String TEXT;

        /**
         * True if the token is context-sensitive, i.e. if it is not always a reserved word.
         */
        final boolean ContextSensitive;

        /**
         * True if the token is a special name.
         */
        final boolean Special;
        }


    // ----- data members --------------------------------------------------------------------------

    /**
     * Starting position (inclusive) in the source of this token.
     */
    private long m_lStartPos;

    /**
     * Ending position (exclusive) in the source of this token.
     */
    private final long m_lEndPos;

    /**
     * Identifier of the token.
     */
    private Id m_id;

    /**
     * Value of the Token (if it is a literal).
     */
    private final Object m_oValue;

    /**
     * Each token knows if it follows whitespace.
     */
    private boolean m_fLeadingWhitespace;

    /**
     * Each token knows if it has whitespace following.
     */
    private boolean m_fTrailingWhitespace;
    }
