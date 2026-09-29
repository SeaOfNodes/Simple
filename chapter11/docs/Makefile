GV := $(wildcard *.gv)
SVG := $(patsubst %.gv,%.svg,$(GV))
.DELETE_ON_ERROR:

.PHONY: all
all: $(SVG)

%.svg: %.gv
	dot -Tsvg $< > $@
	@sed -i 's/\r$$//' $@

.PHONY: clean
clean:
	@rm -f $(SVG)
