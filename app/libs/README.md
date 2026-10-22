# SDK Glyph (non redistribuable)

Dépose ici `glyph-matrix-sdk-2.0.aar` (récupéré sur
https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).

- Le fichier est ignoré par git (`*.aar`) : il ne doit **jamais** être commité.
- Avec l'AAR, `app/build.gradle.kts` ajoute la dépendance et compile la vraie
  implémentation (`app/src/glyph`). Sans lui, le build compile une version factice
  (`app/src/glyphStub`) : l'app tourne, mais n'allume rien.

## En CI

L'AAR encodé en base64 fait ~78 Ko, au-delà de la limite de 48 Ko d'un secret GitHub.
Il est donc coupé en deux secrets, recollés par le workflow :

- `GLYPH_SDK_AAR_BASE64_1` : première moitié
- `GLYPH_SDK_AAR_BASE64_2` : seconde moitié

Pour les régénérer :

    base64 -w0 glyph-matrix-sdk-2.0.aar > aar.b64
    split -n 2 -d aar.b64 part_     # part_00 → _1, part_01 → _2

## Sur le téléphone

Le SDK ne marche qu'avec le **mode debug Glyph** activé (il expire après 48 h) :

    adb shell settings put global nt_glyph_interface_debug_enable 1
