#!/usr/bin/env python3
"""Notes de la Release GitHub : l'entrée la plus récente du journal (app/src/main/assets/journal.json).

Première ligne = « à quoi sert la mise à jour » : l'app l'affiche sur le bouton « Mettre à jour ».
"""
import json
import sys

chemin = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/journal.json"
try:
    e = json.load(open(chemin, encoding="utf-8"))[0]
except Exception as err:  # jamais bloquer la Release pour le journal
    print(f"Build automatique. (journal illisible : {err})")
    sys.exit(0)
print(e["sert"])
print()
print(f"## {e['titre']}")
for s in e.get("sections", []):
    print()
    print(f"### {s['titre']}")
    for p in s.get("points", []):
        print(f"- {p}")
if e.get("tester"):
    print()
    print("### À tester")
    for p in e["tester"]:
        print(f"- {p}")
