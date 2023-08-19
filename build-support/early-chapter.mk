# Compatibility for older/linearized Makefiles; all chapters use the same rules.
include $(dir $(lastword $(MAKEFILE_LIST)))java.mk
