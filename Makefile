# Build from this chapter directory; make libs/init prepares the offline cache.
USE_ISA := 1
include $(firstword $(wildcard ../build-support/java.mk build-support/java.mk))

# Native allocation statistics, introduced with register allocation.
.PHONY: spill-stats
spill-stats: $(test_classes)
	@mkdir -p build/objs
	@$(JVM) com.seaofnodes.simple.SpillStats

# The ABI shim gives standalone native tests the same entry contract as clients.
CPU_PORT := x86_64_v2
CALL_CONV := $(if $(filter Windows_NT,$(OS)),win64,SystemV)
CPU_ABI := $(CPU_PORT)_$(CALL_CONV)
RELEASE := build/release
RELEASE_CRT := $(RELEASE)/$(CPU_ABI)/simple_crt.o
C_CALL_CONV := $(if $(filter win64,$(CALL_CONV)),ms_abi,sysv_abi)
$(RELEASE_CRT): src/main/c/simple_crt.c Makefile
	@mkdir -p $(dir $@)
	@gcc -c -O2 -DCALL_CONV=$(C_CALL_CONV) -o $@ $<
tests release spill-stats: $(RELEASE_CRT)
