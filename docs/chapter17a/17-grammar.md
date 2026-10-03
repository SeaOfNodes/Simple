# Chapter 17a grammar additions

The expression and statement forms are those of Chapter 16. Declarations add
independent access and binding modifiers:

```text
declaration = type binding ("," binding)* ";"
binding     = ("!" | "~")? identifier ("=" expression)?
type        = ("!" | "~")? name suffix*
suffix      = "?" | "[]" | "[~]"
```

A prefix qualifier requires a struct reference. It qualifies the named base,
not enclosing arrays. Each bracket pair qualifies its own array layer. A binding
modifier applies only to the following name. See the README for defaults and
constructor initialization rules. `var`, `val`, increments, compound assignments,
conditional expressions, and `for` loops are introduced in Chapter 17b.
