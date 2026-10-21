# SDK Glyph (non redistribuable)

Dépose ici `glyph-matrix-sdk-2.0.aar` (récupéré sur
https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).

- Le fichier est ignoré par git (`*.aar`) : il ne doit **jamais** être commité.
- `app/build.gradle.kts` ajoute automatiquement la dépendance si le fichier est présent ;
  sans lui, le build continue normalement.
- En CI, le secret GitHub `GLYPH_SDK_AAR_BASE64` (AAR encodé en base64) est décodé ici :

      base64 -w0 glyph-matrix-sdk-2.0.aar   # à coller dans le secret
