# Chapter 2

In this chapter we extend the language grammar to include arithmetic operations such as addition, subtraction,
multiplication, division, and unary minus. This allows us to write statements such as:

```
return 1 + 2 * 3 + -5;
```

Here is the [complete language grammar](docs/02-grammar.md) for this chapter. 

## Nodes Pre Peephole Optimization

The following visual shows how the graph looks like pre-peephole optimization:

![Example Visual](./docs/02-pre-peephole-ex1.svg)

* Control nodes appear as square boxes
* Control edges are in bold red
* The edges from Start to Constants are shown in dotted lines as these are not true def-use edges