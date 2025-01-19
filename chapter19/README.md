# Chapter 19: Instruction Selection and Portable Compilation


## Clean up CodeGen compile driver

More static globals move into the CodeGen object, which is passed around at all
the top level drivers.  Its not passed into the Node constructors, hence not
into the idealize() calls, which remains the majority of cases needing a static
global.  In short the compiler gets a lot more functional, and a big step
towards concurrent compilation.