# Build from this chapter directory; make libs/init prepares the offline cache.
USE_ISA := 1
EXTRA_TESTS := com.seaofnodes.simple.codegen.X86EncodingTest
include $(firstword $(wildcard ../build-support/java.mk build-support/java.mk))

# Native allocation statistics, introduced with register allocation.
.PHONY: spill-stats
spill-stats: $(test_classes)
	@mkdir -p build/objs
	@$(JVM) com.seaofnodes.simple.SpillStats
