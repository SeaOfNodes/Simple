# Grammar for Chapter 1

```antlrv4
grammar SimpleLanguage;

program
    : statement EOF
    ;

statement
    : 'return' expression ';'
    ;

expression
    : primaryExpression
    ;

primaryExpression
    : INTEGER_LITERAL
    ;

INTEGER_LITERAL
    : [1-9][0-9]*
    | [0]
    ;
```