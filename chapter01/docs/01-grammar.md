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
    : [0-9]+
    ;    
```