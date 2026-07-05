# Cahier d'Analyse et de Conception — YowPainter Backend

Documentation LaTeX complète du projet backend YowPainter.

## Contenu

| Chapitre | Sujet |
|----------|-------|
| 1 | Introduction et stack technique |
| 2 | Contexte, acteurs, multi-tenancy |
| 3 | Besoins fonctionnels et non fonctionnels |
| 4 | Cas d'utilisation (diagramme + fiches détaillées) |
| 5 | Architecture hexagonale et packages |
| 6 | Classes métier (domaine) |
| 7 | Classes techniques (ports, adaptateurs, sécurité) |
| 8 | Diagrammes de séquence et machines d'états |
| 9 | Déploiement et infrastructure |
| 10 | Référence API REST et WebSocket |
| 11 | Annexes (glossaire, BDD, checklist prod) |

## Prérequis

- **TeX Live** (Linux/macOS) ou **MiKTeX** (Windows)
- Packages LaTeX : `babel-french`, `tikz`, `pgf`, `hyperref`, `geometry`, `booktabs`, `longtable`, `tcolorbox`, `listings`, `fancyhdr`, `microtype`

Installation rapide des packages manquants (MiKTeX) :
```powershell
mpm --install=babel-french
mpm --install=tikz
mpm --install=tcolorbox
```

## Compilation

### 1. Générer les diagrammes Mermaid (PNG)

```powershell
cd docs/latex
.\compile-diagrams.ps1
```

Les sources `.mmd` sont dans `diagrams/`. Les PNG générés sont utilisés par le rapport LaTeX.

### 2. Compiler le PDF

```powershell
pdflatex -interaction=nonstopmode cahier_analyse_conception.tex
pdflatex -interaction=nonstopmode cahier_analyse_conception.tex
```

Exécuter `pdflatex` **deux fois** pour la table des matières et la liste des figures.

Le PDF généré : `docs/latex/cahier_analyse_conception.pdf`

## Structure des fichiers

```
docs/latex/
├── cahier_analyse_conception.tex   # Fichier principal
├── compile-diagrams.ps1            # Mermaid → PNG
├── Makefile                        # Compilation automatisée
├── README.md                       # Ce fichier
├── diagrams/                       # Sources Mermaid + PNG générés
│   ├── *.mmd                       # Diagrammes source
│   ├── *.png                       # Images compilées (gitignore optionnel)
│   └── mermaid-config.json
└── chapters/
    ├── 01_introduction.tex
    ├── 02_contexte.tex
    ├── 03_besoins.tex
    ├── 04_cas_utilisation.tex
    ├── 05_architecture.tex
    ├── 06_classes_metier.tex
    ├── 07_classes_techniques.tex
    ├── 08_sequences.tex
    ├── 09_deploiement.tex
    ├── 10_api.tex
    └── 11_annexes.tex
```

## Mise à jour

Lors de l'ajout de nouveaux modules ou endpoints, mettre à jour les chapitres concernés :
- Nouveau module → chapitres 5, 6, 7, 10
- Nouveau workflow → chapitre 8
- Nouvelle variable d'env → chapitre 9
