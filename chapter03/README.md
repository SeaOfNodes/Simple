# Chapter 3

In this chapter we extend the language grammar to allow variable declarations. This allows us to write:

```
int a=1; 
int b=2; 
int c=0; 
{ 
  int b=3; 
  c=a+b; 
} 
return c;
```

Here is the [complete language grammar](docs/03-grammar.md) for this chapter.

## Extensions to Intermediate Representation

In this chapter we do not add new nodes.

| Node Name  | Type    | Description                        | Inputs                                                           | Value                                                 |
|------------|---------|------------------------------------|------------------------------------------------------------------|-------------------------------------------------------|
| Start      | Control | Start of function                  | None                                                             | None for now as we do not have function arguments yet |
| Return     | Control | End of function                    | Predecessor control node, Data node value                        | Return value of the function                          |
| Constant   | Data    | Constants such as integer literals | None, however Start node is set as input to enable graph walking | Value of the constant                                 |
| Add        | Data    | Add two values                     | Two data nodes, values are added, order not important            | Result of the add operation                           |
| Sub        | Data    | Subtract a value from another      | Two data nodes, values are subtracted, order matters             | Result of the subtract                                |
| Mul        | Data    | Multiply two values                | Two data nodes, values are multiplied, order not important       | Result of the multiply                                |
| Div        | Data    | Divide a value by another          | Two data nodes, values are divided, order matters                | Result of the division                                |
| UnaryMinus | Data    | Negate a value                     | One data node, value is negated                                  | Result of the unary minus                             |

## Symbol Tables

To support variable declarations and lexical scopes, we introduce symbol tables in the parser.
A symbol table maps names to nodes.
We maintain a stack of symbol tables.
When a new scope is created, we push a new symbol table to the stack, when scope exits, the topmost symbol table is popped
from the stack.

Declaring a name adds it to the current symbol table. 
If a name is assigned to, then its mapping in the most recent symbol table is updated.
If a name is accessed, it's mapping is looked up in the symbol tables. The lookup goes up the stack of symbol tables.

