# Chapter 13: References

## References

Structs can reference other structs; references can be mutually recursive.
Any reference field can allow null or not, same as a normal variable.

```java
struct Person {
  String name;       // No string type, yet
  int age;
  FamilyTree tree?;  // A family tree, or not
}
struct FamilyTree {
  Person father?;    // FamilyTree can reference Person
  Person mother?;
  Person []kids?;
}
```

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
