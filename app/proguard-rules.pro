# Verso : R8 réduit les bibliothèques (Compose, Material, Backdrop), mais on garde le code de Verso
# tel quel (services déclarés au système, JSON lu à la main) et on n'obscurcit rien : les journaux
# d'erreur restent lisibles.
-dontobfuscate
-keep class fr.niakimbo.verso.** { *; }

# SDK Glyph de Nothing (liaison au service système, AIDL).
-keep class com.nothing.** { *; }
-keep interface com.nothing.** { *; }
-dontwarn com.nothing.**

# Backdrop (Liquid Glass) : shaders AGSL chargés par nom.
-keep class com.kyant.** { *; }
-dontwarn com.kyant.**
