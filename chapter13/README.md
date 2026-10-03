# Chapter 13: References

[Previous: Chapter 12](../chapter12/README.md) |
[Next: Chapter 14](../chapter14/README.md)

# Table of Contents

1. [References](#references)
   - [Forward References](#forward-references)
   - [Self-referential references](#self-referential-references)
   - [Auto deepen forward ref types](#auto-deepen-forward-ref-types)

In this chapter, we extend structs to allow for forward and cyclic references.

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter13) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter12...linear-chapter13) it to the previous chapter.


## Forward References

A forward reference occurs when a type or identifier is used before its full
definition is encountered by the compiler. (incomplete types, mutual
references).  When forward-reference types are encountered, the compiler may
initially treat them as placeholders or "shallow" representations.

```java
struct Person {
    String name;       // No string type, yet
    int age;
    FamilyTree? tree;  // A family tree, or not
}
```

[edit: Show the TypeStruct mapping]

At the time of the `Person` struct definition, the `FamilyTree` struct is not
yet defined. This is a forward reference. To handle this, we assume the struct
will be defined later.

[edit: swap to code reference]

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

Reference fields start out as null, doing anything with them will result in a compile time error.

```java
struct Person {
    String name;
    int age;
    FamilyTree? tree;
}
Person p = new Person;
p.tree.left = null; // Accessing unknown field 'left' from 'null'
```

It turns out `p.tree` is `null`, so we can't access the field `left` from it.

When we are creating the struct, we set up the initial value to Null:

`newStruct`:

```java
private Node newStruct(TypeStruct obj) {
   Node n = new NewNode(TypeMemPtr.make(obj), ctrl()).peephole().keep();
   int alias = _aliases.get(obj._name);
   for( Field field : obj._fields ) {
       store(field._fname,alias,field._type,n,new ConstantNode(field._type.makeInit()).peephole(),true);
       alias++;
   }
   return n.unkeep();
}
```
Zooming in....

```java
// parsing =  FamilyTree? tree;
for( Field field : obj._fields ) {
    // field._type = *FamilyTree?
    // REMEMBER:
    // @Override public TypeMemPtr makeInit() { return NULLPTR; }
  store(field._fname,alias,field._type,n,new ConstantNode(field._type.makeInit()).peephole(),true);
  alias++;
}
```
In a case of a reference `field._type.makeInit()` will return  a `NULLPTR`, later this is what the peephole turns into when loading
the field. `p.tree`

---

```java
struct N { N next; int i; }
N n = new N;
return n.next;  // Value reports as null, despite field typed as not-null
```

As shown above, we explicitly allow not-null fields to be null initialized (as
of Chapter 13).  We can't allow null to be stored into a not-null field, but we
can allow a not-null field to be null (initially).  This will be corrected in a
later chapter.


### Self-referential references
Self-cyclic or self-referential types are types that include a reference to themselves as part of their definition.

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

Auto deepening refers to the process where the compiler expands the forward reference into its complete definition.
Here we have a simple linked list implementation, with the payload being a `int`.

```java
struct LLI { LLI? next; int i; }
LLI? head = null;
while( arg ) {
    LLI x = new LLI;
    x.next = head;
    x.i = arg;
    head = x;
    arg = arg-1;
}
if( !head ) return 0; 
LLI? next = head.next;
if( next==null ) return 1;
return next.i;
```

When `a.next` is encountered, we auto deepen the type so its fields are going to be set.

This is needed to resolve the stale type that the loop brings around.

```java
if( e instanceof TypeMemPtr tmp && tmp._obj._fields==null ) {
    e = tmp.make_from((TypeStruct) TYPES.get(tmp._obj._name));
}
```


---

Normal field syntax works:
e.g. `person.tree.father = new Person;` or
`person.tree.father.name = "Dad";`.

Reference fields all start out null, and can be tested as such.  However, a
non-null reference field can only be stored with a non-null ref.

```java
struct N { N next; int i; }
N n = new N;
return n.next.i;  // Value reports as null, despite field typed as not-null
```

And:

```java
struct N { N next; int i; }
N n = new N;
n.next = null; // Error, cannot store null into not-null field
return n.next;
```

Forward references must be specified later and field access is later checked.



## `LoadNode::idealize`
LoadNode first selects its alias through any MemMerge, then returns `st.val()`
when it finds a matching Store to the same object.
```java
        // Simple Load-after-Store on same address.
        if( mem() instanceof StoreNode st &&
            ptr() == st.ptr() && _alias == st._alias ) { // Same object and field
            assert _name.equals(st._name); // Equiv class aliasing is perfect
            return st.val(); // // <<-- {ConstantNode@1651} "null"
        }
```
After alias selection, the Load's `mem()` refers to that initializing Store.
In the next iteration, we will end up with:

```java
...
// expr {ConstantNode@1651} "null"
// ptr = expr._type
// ptr = TypeMemPtr@1510 "null"
// ptr._obj._name = $TOP
TypeStruct base = (TypeStruct)TYPES.get(ptr._obj._name);
int idx = base==null ? -1 : base.find(name); // <<-- null
if( idx == -1 ) {
  throw error("Accessing unknown field '" + name + "' from '" + ptr.str() + "'"); // throws here!!!
}
```
