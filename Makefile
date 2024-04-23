# Standalone compiler snapshot; shared rules also work in linearized checkouts.
include $(firstword $(wildcard ../build-support/early-chapter.mk build-support/early-chapter.mk))
test_cp += com.seaofnodes.simple.FuzzerWrap

.PHONY: fuzzer
fuzzer: $(test_classes)
	java -ea -cp "$(CLZDIR)/main$(SEP)$(CLZDIR)/test$(SEP)$(jars)" org.junit.runner.JUnitCore com.seaofnodes.simple.FuzzerWrap
