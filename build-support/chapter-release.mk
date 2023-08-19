# Jar packaging for chapters predating the existing release target.
.PHONY: release
release: build/release/simple.jar

build/release/simple.jar: $(main_classes) $(test_classes)
	@mkdir -p build/release
	jar cf $@ -C $(CLZDIR)/main . -C $(CLZDIR)/test .
