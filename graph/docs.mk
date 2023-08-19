# Included from a chapter's docs/Makefile.
# A viewer snapshot takes precedence over a legacy DOT source of the same name.
GRAPH_DOCS := ../../graph
PYTHON ?= python3
DOC_BROWSER ?= chromium
JSON := $(wildcard *.json)
VIEW_SVG := $(JSON:.json=.svg)
GV := $(filter-out $(JSON:.json=.gv),$(wildcard *.gv))
SVG := $(VIEW_SVG) $(GV:.gv=.svg)
.DELETE_ON_ERROR:

.PHONY: all
all: $(SVG)

$(VIEW_SVG): %.svg: %.json $(GRAPH_DOCS)/render_docs.py $(wildcard $(GRAPH_DOCS)/web/*.js) $(wildcard $(GRAPH_DOCS)/web/*.css)
	$(PYTHON) $(GRAPH_DOCS)/render_docs.py --browser $(DOC_BROWSER) $<

$(GV:.gv=.svg): %.svg: %.gv
	dot -Tsvg $< > $@
	@sed -i 's/\r$$//' $@

.PHONY: clean
clean:
	@rm -f $(SVG)
