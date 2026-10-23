# VERSO — Spécification

App Android perso pour piloter la **Glyph Matrix** du Nothing Phone (4a) Pro.
Remplace les apps du Play Store (bugs, features payantes, réglages compliqués) par un outil simple et rapide.

## 1. Principe

- **Verso est la seule app qui parle à la matrice.** Les autres apps (Trame, NCOS plus tard) ne touchent jamais au SDK : elles demandent à Verso d'afficher quelque chose.
- But n°1 : **changer de modèle en un tap**, depuis l'app ou un widget.
- Univers visuel Nothing : noir / blanc / rouge, rendu dot-matrix, interface en français.

## 2. Matériel visé (contraintes)

| Élément | Phone (4a) Pro |
|---|---|
| Identifiant SDK | `Glyph.DEVICE_25111p` |
| Matrice | 13×13, **circulaire** (les coins n'existent pas) |
| Glyph Touch | Non |
| Glyph Toys | AOD uniquement |

- SDK officiel : https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
- Le fichier `glyph-matrix-sdk-2.0.aar` **n'est pas redistribuable** : il ne va pas dans le dépôt (voir §7).
- `GlyphMatrixManager.getInstance()` est un singleton pour tout le processus : une seule connexion partagée, et aucun composant ne doit appeler `unInit()` dans son coin.
- Affichage piloté par l'app : `setAppMatrixFrame`.
- **À vérifier en premier** : le mode debug Glyph expire au bout de 48 h. Il faut tester si Verso reste utilisable au quotidien sans le réactiver, et sinon prévoir la demande d'une clé API à Nothing.

## 3. V1 — Switch rapide + widget

### 3.1 Bibliothèque de modèles
- Une liste de modèles avec un aperçu dot-matrix 13×13 qui reproduit la forme ronde de la vraie matrice.
- Deux types de modèles : **statique** (une seule image) et **animé** (plusieurs images + durée de chaque image).
- Une dizaine de modèles intégrés pour commencer : horloge simple, cœur, smiley, étoile, onde, pluie, logo NC, etc.
- Tap sur un modèle → il s'affiche **immédiatement** sur la matrice.
- Un bouton « Éteindre la matrice ».
- Favoris, avec un ordre réglable.

### 3.2 Widget écran d'accueil
- Montre le modèle actif avec son aperçu dot-matrix.
- Tap → passe au favori suivant.
- Appui long ou petit bouton → ouvre la liste des favoris pour en choisir un directement.

### 3.3 Service d'affichage
- Un **foreground service** garde la main sur la matrice et joue les animations.
- Le dernier modèle choisi est restauré automatiquement au redémarrage du téléphone.
- Toute la logique d'affichage passe par une seule classe, par exemple `MatrixController`.

### 3.4 Format d'un modèle
Fichier JSON (une base Room est possible si ça devient plus simple) :
```json
{
  "id": "coeur",
  "nom": "Cœur",
  "type": "anime",
  "frames": [
    { "pixels": "<169 valeurs de luminosité 0-255, 13×13>", "dureeMs": 120 }
  ]
}
```
Chaque image s'écrit au choix avec `pixels` (169 valeurs 0-255, tableau ou texte) ou avec `dessin` (13 lignes de 13 caractères : `.` éteint, `1`…`9` luminosité croissante, `X` plein), plus lisible à la main. L'export se fait toujours en `pixels`.

Les modèles intégrés sont dans `app/src/main/assets/modeles/` (ordre = nom de fichier) et se régénèrent avec `outils/gen_modeles.py`. Un fichier invalide est ignoré et signalé dans Diagnostic.

Le même format servira à l'éditeur, aux modèles dynamiques et à Trame.

## 4. Feuille de route après la V1 (dans cet ordre)

1. **Éditeur pixel 13×13** : dessin au doigt, aperçu en direct sur la matrice, plusieurs images avec un réglage du timing, import et export en JSON.
2. **Modèles dynamiques** : horloge, minuteur, visualiseur musique, notifications.
3. **Modèles automatiques** : changement de modèle selon des règles (heure, musique en cours, notification d'une app précise).
4. **Connexion Trame** : afficher la créature Trame au dos du téléphone. Pas urgent.
5. **Intégration NCOS.**

## 5. Interface pour les autres apps (préparer dès la V1)

- Prévoir dès le départ un point d'entrée que les autres apps peuvent appeler : un service lié ou un broadcast, à choisir pendant le développement.
- Il est protégé par une **permission de niveau `signature`**. Seules les apps signées avec ta clé (Trame, NCOS) peuvent l'utiliser.
- Actions prévues : `afficher(modèle)`, `afficherFrame(pixels)`, `rendreLaMain()`.
- Un système de **priorité** décide quel affichage passe devant : choix manuel > Trame > automatique.

## 6. Stack

- Kotlin + Jetpack Compose, minSdk 34 (le SDK Glyph demande Android 14 ou plus).
- applicationId : `fr.niakimbo.verso`
- Dépôt GitHub public : `Niakimbo22/Verso` (minutes CI illimitées ; aucun secret ni AAR commité)

## 7. Build et mises à jour

- Même flux que Trame : push sur `main` → GitHub Actions build l'APK debug → publication en GitHub Release → mise à jour automatique sur le téléphone. **Aucune installation d'APK à la main.**
- L'AAR du SDK n'est pas dans le dépôt : la CI le recolle depuis deux secrets GitHub (`GLYPH_SDK_AAR_BASE64_1` et `_2`, le base64 dépassant la limite de 48 Ko d'un secret). Sans eux, le build compile un contrôleur factice.
- Prévoir dès le début un keystore debug stable et un versionCode qui ne redescend jamais, pour éviter le bug de désinstallation rencontré sur Trame.

## 8. Étapes de développement V1

1. Squelette Compose + CI + récupération de l'AAR dans la CI.
2. `MatrixController` + test : afficher un pixel, puis une image, sur le vrai téléphone. Vérifier la question des 48 h du mode debug.
3. Format des modèles + bibliothèque intégrée + écran liste avec aperçus. Les animations tournent dans l'app tant qu'elle est ouverte (`ModelePlayer`) ; l'écran de test devient « Diagnostic » (repliable).
4. Foreground service + animations + restauration au redémarrage.
5. Widget écran d'accueil.
6. Favoris + finitions UI.
