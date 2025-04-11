
This is Dadal's reply from U-tube, recreated here (permission pending) so its
both easier to find and not lost if U-tube changes their mind.

The original link I copied from:

https://www.youtube.com/watch?v=iNWAgSAEPAg&lc=Ugwn_YpruALAauwRLqt4AaABAg.AGbLSQ5tlmFAGcQm0uZyZO

The whole replace chain:

https://github.com/SeaOfNodes/Simple/blob/main/ASimpleReply/ASimpleReplyChain.md



On medium sized functions, everything tends to be inlined in V8, but on larger
functions it tends to be more complicated. We've tried before to increase the
inclining budget, but this means reducing compile time, which means delaying
the time until the optimized code starts being executed, which in turns
regresses whatever benchmarks we're using to measure how fast V8 is. One
difference with Java here might be 1- how slow unoptimized JS code is (after
all, V8 bytecodes are ultra-generic, assuming any types for inputs, and having
to check a ton of various cases) and 2- how fast-running JS applications are
compared to Java (not in the sense that "JS is faster" but in the sense that
"JS applications probably stop running at least one order of magnitude earlier
than Java ones")
