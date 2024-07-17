# Grammar for Chapter 12

```antlrv4
grammar SimpleLanguage;

program
    : statement+ EOF
    ;

statement
    : returnStatement
    | structDeclaration
    | declStatement
    | blockStatment
    | expressionStatement
    | ifStatement
    | whileStatement
    | breakStatement
    | continueStatment
    | metaStatement
    ;

PRIMTYPE
    : 'int'
    | 'flt'
    ;

field
    : PRIMTYPE IDENTIFIER ';'
    ;

fields
    : field+
    ;

structDeclaration
    : 'struct' IDENTIFIER '{' fields '}'
    ;

whileStatement
    : 'while' '(' expression ')' statement
    ;

breakStatement
    : 'break' ';'
    ;

continueStatement
    : 'continue' ';'
    ;

ifStatement
    : 'if' '(' expression ')' statement ('else' statement)?
    ;

metaStatement
    : '#showGraph' ';'
    ;

expressionStatement
    : IDENTIFIER '=' expression ';'
    | fieldExpression '=' expression ';'
    ;

blockStatement
    : '{' statement+ '}'
    ;

structName
    : IDENTIFIER
    ;

declStatement
    : PRIMTYPE IDENTIFIER '=' expression ';'
    | structName ('?')? IDENTIFIER '=' expression ';'
    ;

returnStatement
    : 'return' expression ';'
    ;

expression
    : comparisonExpression
    ;

comparisonExpression
    : additiveExpression (('==' | '!='| '>'| '<'| '>='| '<=') additiveExpression)*
    ;

additiveExpression
    : multiplicativeExpression (('+' | '-') multiplicativeExpression)*
    ;

multiplicativeExpression
    : unaryExpression (('*' | '/') unaryExpression)*
    ;

unaryExpression
    : ('-') unaryExpression
    | '!' unaryExpression
    | primaryExpression
    ;

newExpression
    : 'new' IDENTIFIER
    ;

fieldExpression
    : primaryExpresson '.' IDENTIFIER
    ;

primaryExpression
    : IDENTIFIER
    | INTEGER_LITERAL
    | 'true'
    | 'false'
    | 'null'
    | newExpression
    | '(' expression ')'
    | fieldExpression
    ;

INTEGER_LITERAL
    : [1-9][0-9]*
    | [0]
    ;

IDENTIFIER
    : NON_DIGIT (NON_DIGIT | DIGIT)*
    ;

NON_DIGIT: [a-zA-Z_];
DEC_DIGIT: [0-9];
```
