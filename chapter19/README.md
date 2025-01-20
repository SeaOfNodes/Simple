# Chapter 19: Instruction Selection and Portable Compilation

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter19) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter18...linear-chapter19) it to the previous chapter.

## Clean up CodeGen compile driver

More static globals move into the CodeGen object, which is passed around at all
the top level drivers.  Its not passed into the Node constructors, hence not
into the idealize() calls, which remains the majority of cases needing a static
global.  In short the compiler gets a lot more functional, and a big step
towards concurrent compilation.