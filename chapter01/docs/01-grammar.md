# Grammar for Chapter 1

```antlrv4
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
    | expression ('*' | '/' ) expression                            # ArithmeticOrLogicalExpression
    | expression ('+' | '-') expression                             # ArithmeticOrLogicalExpression
    ;

primaryExpression
    :
    | INTEGER_LITERAL
    | IDENTIFIER
    | '(' expression ')'
    ;
```