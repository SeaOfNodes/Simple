# Chapter 13: References

In this chapter, we add references and structs.

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter13) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter12...linear-chapter13) it to the previous chapter.

## References

Structs can reference other structs; references can be mutually recursive.
Any reference field can allow null or not, same as a normal variable.

### Mutually recursive

### Forward References
A forward reference occurs when a type or identifier is used before its full definition is 
encountered by the compiler. (incomplete types, mutual references).
When forward-reference types are encountered, the compiler may initially treat them as placeholders or "shallow" representations.


Consider the following code:
```java
struct Person {
String name;       // No string type, yet
int age;
FamilyTree? tree;  // A family tree, or not
}
...
```
At the time of the `Person` struct definition, the `FamilyTree` struct is not yet defined. This is a forward reference. To handle this, we assume the struct will be defined later.
```java 
Type t = TYPES.get(tname);
   // Assume a forward-reference type
   if( t == null ) {
       int old2 = _lexer._position;
       String id = _lexer.matchId();
       if( id==null ) {
           _lexer._position = old;
           return null;
       }
       TYPES.put(tname,t = TypeStruct.make(tname)); // Assume forward ref
       _lexer._position = old2;
   }
```

---

### Self-referential references
Self-cyclic or self-referential types are types that include a reference to themsleves as part of their defintion.

Consider the following code:
```java 
struct N { N next; int i; }
N n = new N;
n.next = null; // <<-- Compile error, cannot store null into not-null field
return n.next;
```
The field `next` is of type `N`, which is the same type as the structure `N` itself.
This creates a circular reference. To resolve this, we auto deepen the forward ref type.


#### Auto deepen forward ref types
Auto deepening refers to the process where the compiler expends the forward reference into its complete defintion.

When a.next is encountered, we auto deepen the type so its fields are going to get set.


Fields that are not nullable cannot
Normal field syntax works:  
e.g. `person.tree.father = new Person;` or 
     `person.tree.father.name = "Dad";`.

Reference fields all start out null, and can be tested as such.  However, a
non-null reference field can only be stored with a non-null ref.

```java
struct N { N next; int i; }
N n = new N;
return n.next;  // Value reports as null, despite field typed as not-null
```

And:

```java
struct N { N next; int i; }
N n = new N;
n.next = null; // <<-- Compile error, cannot store null into not-null field
return n.next;
```

Forward references must be specified later and field access is later checked.