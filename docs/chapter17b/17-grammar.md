# Chapter 17b grammar additions

Start with [Chapter 17a's declaration syntax](../../chapter17a/docs/17-grammar.md).
This chapter adds inference, assignment shortcuts, conditional expressions, and
`for` loops. Access and binding permissions keep the same meaning.

```text
inferredDeclaration = ("var" | "val") ("!" | "~")? identifier "=" assignment
assignment          = expression (assignmentOperator assignment)?
assignmentOperator  = "=" | "+=" | "-=" | "*=" | "/=" | "&=" | "|=" | "^="
                    | "<<=" | ">>=" | ">>>="
expression          = bitwise ("?" assignment (":" assignment)?)?
prefixUpdate        = ("++" | "--") identifier
postfixUpdate       = lvalue ("++" | "--")
forStatement        = "for" "(" declarationOrExpression? ";" assignment? ";"
                      assignment? ")" statement
```

Declarations can contain several comma-separated names. `var` permits
reassignment and `val` fixes the binding; both retain the initializer's reference
permissions. Postfix updates also support fields and array elements. Prefix
updates currently support local names. See the README for evaluation order.
