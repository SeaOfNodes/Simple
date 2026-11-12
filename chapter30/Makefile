# Build from this chapter directory; make libs/init prepares the offline cache.
USE_ISA := 1
CUSTOM_TESTS := 1
CUSTOM_RELEASE := 1
DEFAULT_TARGETS := release tests
include $(firstword $(wildcard ../build-support/java.mk build-support/java.mk))

# These tests do not rely on the stdlib
test_alone := com.seaofnodes.simple.BrainFuckTest com.seaofnodes.simple.MergeSortTest com.seaofnodes.simple.TypeTest
test_raw_nums0 := 01 02 03 04 05 06 07 08 09 10 12 13 14Float 14 15 16 17a 17b 18 19 20 21
test_raw_nums1 := 22 23 24
# These tests DO rely on the stdlib
test_raw_sys   := 25

# Convert chapter numbers to test files
test_raw0    := $(patsubst %,com.seaofnodes.simple.Chapter%Test,$(test_raw_nums0))
test_raw1    := $(patsubst %,com.seaofnodes.simple.Chapter%Test,$(test_raw_nums1))
test_sys     := $(patsubst %,com.seaofnodes.simple.Chapter%Test,$(test_raw_sys)  )
# All the 'src/test/java' files ending in 'Test'
test_cp0     := $(patsubst $(TST)/$(SIMPLE)/%.java,com.seaofnodes.simple.%,$(wildcard $(TST)/$(SIMPLE)/*Test.java))
# Minus raw0, raw1, alone tests
test_cp1     := $(filter-out $(test_raw0) ,$(test_cp0))
test_cp2     := $(filter-out $(test_raw1) ,$(test_cp1))
test_cp3     := $(filter-out $(test_alone),$(test_cp2))
test_cp4     := $(filter-out com.seaofnodes.simple.Chapter25Test,$(test_cp3))
test_cp      := $(filter-out $(test_sys)  ,$(test_cp4))

# Default Simple .o target is build/objs which is where all the test .o's get
# dumped.  The default sys search needs to NOT include all the dumped test
# files; move it down into lib/
OBJS:= build/objs
SYS_SMPS:= $(wildcard src/main/smp/*.smp src/main/smp/*/*.smp src/main/smp/*/*/*.smp)
CPU_PORT:= x86_64_v2
CALL_CONV:= $(if $(filter Windows_NT,$(OS)),win64,SystemV)
CPU_ABI:= $(CPU_PORT)_$(CALL_CONV)
RELEASE:= build/release
TEST_SYS:= $(OBJS)/lib_$(CPU_ABI)/sys.o
RELEASE_SYS:= $(RELEASE)/$(CPU_ABI)/sys.o
RELEASE_CRT:= $(RELEASE)/$(CPU_ABI)/simple_crt.o
RELEASE_ARGS:= $(RELEASE)/$(CPU_ABI)/simple_args.o
C_CALL_CONV:= $(if $(filter win64,$(CALL_CONV)),ms_abi,sysv_abi)
GAME_DIR:= build/examples/$(CPU_ABI)
TICTACTOE:= $(GAME_DIR)/TicTacToe$(if $(filter Windows_NT,$(OS)),.exe,)

# Build a runnable game without starting it. Native outputs are ABI-specific;
# WSL can use CLZDIR=build/classes-linux to keep its Java build separate too.
.PHONY: tictactoe
tictactoe: $(TICTACTOE)

$(TICTACTOE): docs/examples/TicTacToe.smp $(CLZDIR)/test/.ttag $(RELEASE_SYS) $(RELEASE_CRT) Makefile
	@echo "building " $@
	@$(JVM) com.seaofnodes.simple.Simple --link --cpu $(CPU_PORT) --abi $(CALL_CONV) -L $(dir $(RELEASE_SYS)) -o $(GAME_DIR)/TicTacToe.o $<

$(TEST_SYS):	$(CLZDIR)/main/.mtag $(simple_driver) $(SYS_SMPS) | $(CLZDIR)/test/.ttag
	@echo "simping " $@
	@$(JVM) com.seaofnodes.simple.Simple --cpu $(CPU_PORT) --abi $(CALL_CONV) --norun -o $@ src/main/smp/sys.smp

$(RELEASE_SYS):	$(OBJS)/lib_$(CPU_ABI)/sys.o
	@echo "copying " $@
	@[ -d $(dir $@) ] || mkdir -p $(dir $@)
	@cp $< $@
	@cp -r $(dir $<)sys $(dir $@)

$(RELEASE_ARGS): src/main/smp/sys/rt/simple_args.smp $(CLZDIR)/main/.mtag $(simple_driver) Makefile | $(CLZDIR)/test/.ttag
	@echo "simping " $@
	@$(JVM) com.seaofnodes.simple.Simple --cpu $(CPU_PORT) --abi $(CALL_CONV) --norun -o $@ $<

$(RELEASE_CRT): src/main/c/simple_crt.c $(RELEASE_ARGS) Makefile
	@echo "compiling " $@
	@[ -d $(dir $@) ] || mkdir -p $(dir $@)
	@gcc -c -O2 -DCALL_CONV=$(C_CALL_CONV) -o $@ $<

#######################################################
# Base launch line for JVM tests

# Tests that do NOT need sys.smp
tests_raw0:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag $(RELEASE_CRT)
	@echo "testing " $(test_raw0)
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(JVM) org.junit.runner.JUnitCore $(test_raw0)

tests_raw1:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag $(RELEASE_CRT)
	@echo "testing " $(test_raw1)
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(JVM) org.junit.runner.JUnitCore $(test_raw1)

# Larger regressions, including BrainFuck's standard-library buffer variant.
tests_alone: $(main_classes) $(test_classes) $(RELEASE_SYS) $(RELEASE_CRT)
	@echo "testing " $(test_alone)
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(JVM) org.junit.runner.JUnitCore $(test_alone)

# Tests that DO need sys.smp
tests_sys: $(main_classes) $(test_classes) $(RELEASE_SYS) $(RELEASE_CRT)
	@echo "testing " $(test_sys)
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(JVM) org.junit.runner.JUnitCore $(test_sys)

# One test method (or the whole class when METHOD is omitted).
CLASS ?= Chapter25Test
METHOD ?=
.PHONY: test
test: $(main_classes) $(test_classes) $(RELEASE_SYS) $(RELEASE_CRT)
	@$(JVM) com.seaofnodes.simple.TestRunner "$(CLASS)" "$(METHOD)"

tests:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag tests_raw0 tests_raw1 tests_alone tests_sys fuzzer
	@echo "All tests"
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(JVM) org.junit.runner.JUnitCore $(test_cp)


# Build a Simple jar and release stdlib
release:	build/release/Simple.jar $(RELEASE_SYS) $(RELEASE_CRT)

# Build a Simple jar
build/release/Simple.jar:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag
	@echo "jarring " $@ " because " $?
	@[ -d $(dir $@) ] || mkdir -p $(dir $@)
	@printf "Main-Class: com.seaofnodes.simple.Simple\nClass-Path: ../../lib/junit-4.12.jar ../../lib/hamcrest-core-1.3.jar\n\n" > build/release/MANIFEST.MF
	@jar cfm $@ build/release/MANIFEST.MF -C $(CLZDIR)/main . -C $(CLZDIR)/test . -C $(SRC)/$(SIMPLE) . -C $(TST)/$(SIMPLE) .



.PHONY: spill-stats
spill-stats: $(test_classes) $(TEST_SYS) $(RELEASE_SYS) $(RELEASE_CRT)
	@$(JVM) com.seaofnodes.simple.SpillStats
