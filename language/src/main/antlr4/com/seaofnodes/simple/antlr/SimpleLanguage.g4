grammar SimpleLanguage;

program
    : declaration* statement+ EOF
    ;

declaration
    : type IDENTIFIER ';'
    ;

type
    : 'int'
    ;

statement
    : 'if' '(' expression ')' statement
    | 'if' '(' expression ')' statement 'else' statement
    | 'while' '(' expression ')' statement
    | IDENTIFIER '=' expression ';'
    | '{' declaration* statement+ '}'
    | 'break' ';'
    ;

expression
    : primaryExpression                                             # PrimaryExpression_
    | ('-' | '!') expression                                        # UnaryExpression
    | expression ('*' | '/' ) expression                            # ArithmeticOrLogicalExpression
    | expression ('+' | '-') expression                             # ArithmeticOrLogicalExpression
    | expression ('==' | '!='| '>'| '<'| '>='| '<=') expression     # ComparisonExpression
    | expression '&&' expression                                    # BooleanExpression
    | expression '||' expression                                    # BooleanExpression
    ;

primaryExpression
    :
    | INTEGER_LITERAL
    | IDENTIFIER
    | '(' expression ')'
    ;

///////////////////////////////////////////////////////////////////

IDENTIFIER
    :   IDENTIFIER_NONDIGIT
        (   IDENTIFIER_NONDIGIT
        |   DEC_DIGIT
        )*
    ;

fragment
IDENTIFIER_NONDIGIT
    :   NON_DIGIT
    |   UNIVERSAL_CHARACTER_NAME
    ;

fragment
NON_DIGIT
    :   [a-zA-Z_]
    ;


fragment
UNIVERSAL_CHARACTER_NAME
    :   '\\u' HEX_QUAD
    |   '\\U' HEX_QUAD HEX_QUAD
    ;

fragment
HEX_QUAD
    :   HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT
    ;

// tokens char and string
CHAR_LITERAL
   : '\''
   (
      ~['\\\n\r\t]
      | QUOTE_ESCAPE
      | ASCII_ESCAPE
      | UNICODE_ESCAPE
   ) '\''
   ;

STRING_LITERAL
   : '"'
   (
      ~["]
      | QUOTE_ESCAPE
      | ASCII_ESCAPE
      | UNICODE_ESCAPE
      | ESC_NEWLINE
   )* '"'
   ;
RAW_STRING_LITERAL: 'r' RAW_STRING_CONTENT;
fragment RAW_STRING_CONTENT: '#' RAW_STRING_CONTENT '#' | '"' .*? '"';
BYTE_LITERAL: 'b\'' (. | QUOTE_ESCAPE | BYTE_ESCAPE) '\'';

BYTE_STRING_LITERAL: 'b"' (~["] | QUOTE_ESCAPE | BYTE_ESCAPE)* '"';
RAW_BYTE_STRING_LITERAL: 'br' RAW_STRING_CONTENT;
fragment ASCII_ESCAPE: '\\x' OCT_DIGIT HEX_DIGIT | COMMON_ESCAPE;
fragment BYTE_ESCAPE: '\\x' HEX_DIGIT HEX_DIGIT | COMMON_ESCAPE;
fragment COMMON_ESCAPE: '\\' [nrt\\0];
fragment UNICODE_ESCAPE
   : '\\u{' HEX_DIGIT HEX_DIGIT? HEX_DIGIT? HEX_DIGIT? HEX_DIGIT? HEX_DIGIT? '}'
   ;
fragment QUOTE_ESCAPE: '\\' ['"];
fragment ESC_NEWLINE: '\\' '\n';

// number
INTEGER_LITERAL
   :
   (
      DEC_LITERAL
      | BIN_LITERAL
      | OCT_LITERAL
      | HEX_LITERAL
   ) INTEGER_SUFFIX?
   ;
DEC_LITERAL: DEC_DIGIT (DEC_DIGIT | '_')*;
HEX_LITERAL: '0x' '_'* HEX_DIGIT (HEX_DIGIT | '_')*;
OCT_LITERAL: '0o' '_'* OCT_DIGIT (OCT_DIGIT | '_')*;
BIN_LITERAL: '0b' '_'* [01] [01_]*;
FLOAT_LITERAL
   : DEC_LITERAL
   (
      '.' DEC_LITERAL
   )? FLOAT_EXPONENT? FLOAT_SUFFIX?
   ;

fragment INTEGER_SUFFIX
   : 'u8'
   | 'u16'
   | 'u32'
   | 'u64'
   | 'u128'
   | 'usize'
   | 'i8'
   | 'i16'
   | 'i32'
   | 'i64'
   | 'i128'
   | 'isize'
   ;

fragment FLOAT_SUFFIX: 'f32' | 'f64';
fragment FLOAT_EXPONENT: [eE] [+-]? '_'* DEC_LITERAL;
fragment OCT_DIGIT: [0-7];
fragment DEC_DIGIT: [0-9];
fragment HEX_DIGIT: [0-9a-fA-F];


WHITESPACE
    :   [ \t]+
        -> skip
    ;

NEWLINE
    :   (   '\r' '\n'?
        |   '\n'
        )
        -> skip
    ;

LINE_COMMENT
    :   '//' ~[\r\n]*
        -> skip
    ;


ANY: . ;