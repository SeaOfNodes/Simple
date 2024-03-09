# Chapter 10

In this chapter:

* We add user defined struct types in the Simple language.
* We introduce the concept of Memory and Aliasing.
* We add new nodes to represent operations on memory - such as creating new instance of a struct, storing and loading struct fields.

Here is the [complete language grammar](docs/10-grammar.md) for this chapter.

## Memory

Until now we have dealt with scalar values that fit a single machine register. We now introduce aggregate type `struct` have reference semantics and thus do not work well with registers, 
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

We add following new Node types sto support memory operations

| Node Name | Type    | Description                        | Inputs                                                           | Value                                                 |
|-----------|---------|------------------------------------|------------------------------------------------------------------|-------------------------------------------------------|
| New       | Mem     | Create ptr to new object           | Memory, Struct type                                              | Ptr value                                             |
| Store     | Mem     | Stores a value in a struct field   | Memory (aliased by struct+field), Ptr, Field, Value              | Memory (aliased by struct+field)                      |
| Load      | Mem     | Loads a value from a field         | Memory (aliased by struct+field), Ptr, Field                     | Value loaded                                          |

Additional following Node types will be enhanced:

| Node Name | Type    | Changes                                          |
|-----------|---------|--------------------------------------------------|
| Start     | Control | Start will produce the initial Memory projection |
| Phi       | Data    | Will allow merging Store and Load ops            |

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

