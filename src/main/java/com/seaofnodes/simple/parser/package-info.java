/**
 * Handwritten recursive-descent parsing directly into Sea-of-Nodes IR.
 *
 * <p>{@link com.seaofnodes.simple.parser.Parser} owns the shared cursor and
 * scope. Package-local components implement the grammar families: statements,
 * expressions, declarations, types, functions, and structs. References handles
 * use-side name/field resolution; Allocations, ControlFlow, and Conversions
 * contain their shared IR construction. Lexer also supplies location snapshots.
 *
 * <p>See {@code chapter29/docs/29-grammar.md} for the production-to-method map.
 * Evaluation order and scope lifetimes are explicit: loop updates and declared
 * constructor bodies are the two deferred source fragments. There is no AST.
 */
package com.seaofnodes.simple.parser;
