# Chapter 10

In this chapter:

* We add user defined struct types in the Simple language.
* We introduce the concept of Memory, Memory Pointers and Aliasing.
* We add new nodes to represent operations on memory - such as creating new instance of a struct, storing and loading struct fields.

Here is the [complete language grammar](docs/10-grammar.md) for this chapter.

## Memory

Until now we have dealt with scalar values that fit a single machine register. We now introduce aggregate type `struct` that have reference semantics and thus do not work as register values, 
even if the struct would fit into a register. To support values of such a type, we need the concept of `Memory`.

The core ideas regarding `Memory` are:

* The program starts with a single `Memory` value that encapsulates the whole of available memory.
* Each `struct` type carves out this memory such as all instances of a particular field in a given struct goes into the same slice. We can identify this via "alias classes". Conceptually, we can think of an "alias class"
  as a unique integer ID assigned to each field in a struct, where distinct struct types have their own set of ids. For example:

Suppose we have declared two struct types like so:

```
struct Vector2D { int x; int y; }
struct Vector3D { int x; int y; int z; }
```

Then we can imagine assigning following alias class IDs

| Struct type | Field | Alias |
| ----------- | ----- | ----- |
| Vector2D    | x     | 1     |
| Vector2D    | y     | 2     |
| Vector3D    | x     | 3     |
| Vector3D    | y     | 4     |
| Vector3D    | z     | 5     |

So at this point we have sliced memory into 5 distinct alias classes.
Note that the `x` and `y` in `Vector2D` do not alias `x` and `y` in `Vector3D`.

In this chapter we do not have inheritance or sub-typing. But if we had subtyping and `Vector3D` 
was a subtype of `Vector2D` then `x` and `y` would alias and would be given the same alias class. 

## Extensions to Intermediate Representation

We add following new Node types to support memory operations:

| Node Name | Type | Description                      | Inputs                                                    | Value                                  |
|-----------|------|----------------------------------|-----------------------------------------------------------|----------------------------------------|
| New       | Mem  | Create ptr to new object         | Control, Struct type                                      | Ptr value                              |
| Store     | Mem  | Stores a value in a struct field | Memory slice (aliased by struct+field), Ptr, Field, Value | Memory slice (aliased by struct+field) |
| Load      | Mem  | Loads a value from a field       | Memory slice (aliased by struct+field), Ptr, Field        | Value loaded                           |
| Cast      | ?    | Upcasts a value                  | ?                                                         | ?                                      | 

* New takes the current control as an input so that it is pinned correctly in the control flow. Conceptually the control input is also a proxy for all memory that originates from the Start node.
* Above, "Ptr" refers to the base address of the allocated object. Within the context of a single program (function), each `Ptr` represents a distinct object in memory.
* A "Memory Slice" represents a slice of memory where all stores and loads alias. Different slices do not alias.

Additionally, the following Node types will be enhanced:

| Node Name | Type    | Changes                                                          |
|-----------|---------|------------------------------------------------------------------|
| Start     | Control | Start will produce the initial Memory projections, one per slice |
| Return    | Control | Will merge all memory slices                                     |

## Enhanced Type Lattice

The Type Lattice for Simple has a major revision in this chapter. 

![Graph1](./docs/lattice.svg)

Within the Type Lattice, we now have following domains:

* Control type - this represents control flow
* Integer type - Integer values
* Struct type (new) - Represents user defined struct types, a struct type is allowed to have members of Integer type only in this chapter
  * `$TOP` represents local Top for struct type; implies all we know about the type is that it is a struct but, we do not know if it is a specific struct, or all possible structs, etc.
  * `$BOT` represents local Bottom for struct type; implies that we know the value can definitely all possible struct types
* Pointer type (new) - Represents a pointer to a struct type
  * `Null` is a special pointer to non-existent memory object
  * We use the prefix `*` to mean pointer-to. Thus `*S1` means pointer to `S1`, `*$TOP` means pointer to `$TOP`.
  * `*void` is a synonym for `*$BOT` - i.e. it represents a Non-Null pointer to all possible struct types, akin to `void *` in C.
  * `?` suffix represents the union of a pointer to some type and `Null`.
  * `Null` pointer evaluates to False and non-Null pointers evaluate to True, as in C.
* Memory (new) - Represents memory and memory slices
  * `#n` - refers to a memory slice

We make use of following operations on the lattice.

* The `meet` operation takes two types and computes the greatest lower bound type.
  * Example, meet of `*S1` and `*S2` results in `*void`.
  * Meet of `*S1` and struct `Null` results in `*S1?`
  * Meet of `*void` and `Null` results in `*$BOT?`; bear in mind that `*void` is a synonym for `*$BOT`.
  * Meet of `1` and `2` results in `Int Bot`.
  * Meet of `#1` and `#2` results in `MEM#BOT`.
* The `join` operation takes two types and computes the least upper bound type.
  * Example, join of `*S1` and `*S2` results in `*$TOP`.
  * Join of `1` and `2` results in `Int Top`.
  * Join of `*void` and `Null` results in `*$TOP`.
* The `dual` operation can be described as follows:
  * Find the corresponding node of the lattice after you invert the lattice.
    * Thus, dual of `Top` is `Bot`.
    * dual of `*$TOP` is `*$BOT?`.
  * However, for structs, the dual is obtained by computing the dual of each struct member. Thus, dual of `*S1` is not `*S1?`.

A key change in the Sea of Nodes type computation is to ensure that values stay inside the domain after they are created. To support this, each domain within the lattice has a local Top and Bottom type.
The Parser now tracks the declared type of a variable; the actual type is tracked in the Sea of Nodes graph. 




## A simple example

Let us now see how we would represent following.

```java
struct Vector2D { int x; int y; }

Vector2D v = new Vector2D;
v.x = 1;
if (arg)
    v.y = 2;
else
    v.y = 3;
return v;
```

The graph from above will have the shape:

![Graph1](./docs/example1.svg)