# Makefile — Compilation du CAC YowPainter Backend

MAIN = cahier_analyse_conception
LATEX = pdflatex
LATEX_FLAGS = -interaction=nonstopmode -halt-on-error
MMDC = npx --yes @mermaid-js/mermaid-cli
MMD_CONFIG = diagrams/mermaid-config.json

.PHONY: all diagrams clean view

all: diagrams $(MAIN).pdf

diagrams:
	@powershell -ExecutionPolicy Bypass -File compile-diagrams.ps1 2>/dev/null || \
	for f in diagrams/*.mmd; do \
	  $(MMDC) -c $(MMD_CONFIG) -i $$f -o $${f%.mmd}.png -b white -w 1400 -s 2; \
	done

$(MAIN).pdf: $(MAIN).tex chapters/*.tex diagrams/*.png
	$(LATEX) $(LATEX_FLAGS) $(MAIN).tex
	$(LATEX) $(LATEX_FLAGS) $(MAIN).tex

clean:
	rm -f $(MAIN).aux $(MAIN).log $(MAIN).toc $(MAIN).lof $(MAIN).lot $(MAIN).out

view: $(MAIN).pdf
	@echo "PDF généré : $(MAIN).pdf"
