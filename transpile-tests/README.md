# Transpile Tests

This module may be helpful if you want to create an implementation of Simple in another language.
It can parse the JUnit tests to automatically translate most of the boilerplate.
Some manual tweaks will still be required though, because only common patterns are detected
(e.g. 01: testSimpleProgram, testZero; 04: testVarArg; 08: testRegress5; 09: testMeet, ...)

## Usage

Just call `JUnitParser.parseRepository` to get a list of parsed test classes for each chapter,
and implement a pretty printer for your favourite language.

For an example you can refer to `printers.RustPrinter`,
which produces tests similar to the ones used by
[Simple-Rust](https://github.com/SeaOfNodes/Simple-Rust).
