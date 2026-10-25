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
- Deux canaux d'affichage :
  - **App** (`setAppMatrixFrame`) : ne tient que tant que l'app est au premier plan, et **n'importe quel Glyph Toy passe devant**.
  - **Glyph Toy** (`setMatrixFrame`) : prioritaire, reste affiché app fermée. Sur le (4a) Pro, seuls les Toys « Always-on » existent (*Réglages › Glyph Interface › Flip to Glyph › Always-on Glyph Toy*), un seul à la fois, et le système envoie `EVENT_AOD` environ une fois par minute.
- Clé SDK : `NothingKey = test` suffit pour un usage perso (vérifié : pas besoin du mode debug ADB). Une vraie clé (GDKsupport@nothing.tech) ne servirait qu'à publier.
- Méthode non documentée du SDK : `setGlyphMatrixTimeout(boolean)`, testable depuis le Diagnostic.

## 3. V1 — Switch rapide + widget

### 3.1 Bibliothèque de modèles
- Une liste de modèles avec un aperçu dot-matrix 13×13 qui reproduit la forme ronde de la vraie matrice.
- Deux types de modèles : **statique** (une seule image) et **animé** (plusieurs images + durée de chaque image).
- 90 modèles intégrés (étape 6), rangés par rayon : Effets, Météo, Icônes, Émotions, Persos (originaux, aucun personnage protégé), Formes, Fêtes, Texte, Infos. Puces en haut de la bibliothèque : Tous, ★, puis chaque rayon. Dans « Tous », aperçus fixes (sauf le modèle actif) pour ne pas faire tourner 90 animations à la fois.
- Tap sur un modèle → il s'affiche **immédiatement** sur la matrice.
- Un bouton « Éteindre la matrice ».
- Favoris : ☆/★ sur chaque carte (`FavorisStore`), dans l'ordre d'ajout. L'ordre réglable vient à l'étape 6.

### 3.1 bis Versions et réglages de chaque modèle
- Appui long sur une carte (ou bouton ⚙) → fiche du modèle : aperçu en direct, choix de la **version** (V1, V2… : quand un modèle est amélioré, l'ancienne version reste disponible) et **réglages**.
- Réglages communs à tous les modèles : luminosité, vitesse de l'animation, rotation, miroir, inverser.
- Réglages propres aux modèles calculés dans l'app (`generateur/`) : pluie (V1 et V2 : densité, averses, vent, longueur et types de gouttes, éclaboussures, orage), cœur (rythme, double battement, style), onde, chargement, horloge (aiguilles ou chiffres, cadran, 12/24 h).
- Chaque réglage a un titre, sa valeur lisible et une phrase qui explique ce qu'il change.
- Tout est enregistré tout de suite (`ReglagesStore`) et appliqué en direct sur la matrice, Glyph Toy compris.

### 3.2 Widget écran d'accueil
- Quatre formats dans la liste des widgets, tous redimensionnables : **Carré** 2×2 (matrice, nom, ‹ › ☰), **Rond** (la matrice seule, ☰ discret), **Barre** 4×1 (matrice, nom, ‹ › ☰), **Mini** 1×1 (matrice seule).
- Tap sur la matrice : au choix dans les réglages du widget — **Choisir** (défaut : ouvre la petite fenêtre de choix), **Suivant** (favori suivant, ou toute la bibliothèque sans favori), **On / Off** (éteint, puis rallume le dernier modèle), **Verso** (ouvre l'app). ‹ › = précédent / suivant.
- Section « Widgets » dans l'app : chaque widget posé avec un accès direct à ses réglages.
- ☰ (masqué quand le tap ouvre déjà la fenêtre) → petite fenêtre par-dessus l'écran d'accueil (`ChoixRapideActivity`) : onglets Favoris / Tous, tap = affiché, plus Éteindre et Ouvrir Verso. Android ne laisse pas un widget capter l'appui long (il sert au lanceur), d'où le bouton.
- Écran de réglage à la pose, puis via appui long › Paramètres du widget (`ConfigWidgetActivity`), avec le vrai widget en aperçu : thème sombre/clair, fond plein/transparent, points ronds/carrés, taille des points, LEDs blanches (noires en clair) ou rouges, animé/fixe, nom, boutons, action du tap. Un style par widget (`StylesWidgetStore`).
- **Rendu** (`RenduWidget`) : trois images carrées empilées — disque « LED éteinte », image du modèle en ALPHA_8 45×45 (3 px par LED, teintée « LED allumée »), masque percé d'un trou par LED (teinté couleur du disque). Les calques sont générés par `outils/gen_masques_widget.py`.
- **Animation** : toutes les images sont envoyées au lanceur dans un `ViewFlipper`. C'est le lanceur qui anime, Verso n'a pas besoin de tourner, et le ViewFlipper se met en pause dès que le widget n'est plus visible (app ouverte, écran éteint). Un seul intervalle par ViewFlipper : une image longue est répétée. Budget de 380 Ko d'images par widget (l'échange système → lanceur est limité à ~500 Ko) : au-delà, une image sur deux, durées cumulées (la pluie passe ainsi de 70 à 140 ms par image). Le modèle « Compteur test » (Diagnostic) sert à vérifier la pause.
- **Horloge** : une image, renvoyée à chaque minute pile par une alarme RTC qui ne réveille pas le téléphone (`USE_EXACT_ALARM`) ; écran éteint, elle attend le réveil.
- Mises à jour (`WidgetMaj`) quand le modèle actif, ses réglages ou le style d'un widget changent ; un widget inchangé n'est pas renvoyé.

### 3.3 Glyph Toy « Verso » (affichage permanent)
- Verso déclare un **Glyph Toy AOD** (`VersoToyService`). Une fois choisi dans les réglages Glyph (bouton dans l'app), il affiche le modèle actif **app fermée**, et le système le relance tout seul après un redémarrage.
- Le modèle actif est mémorisé (`ModeleStore`) ; le Toy le relit à chaque liaison.
- Le `MatrixController` est unique et bascule de canal : Toy quand le système a lié le service, app sinon.
- Le `ModelePlayer` joue les animations / l'horloge tant que l'app est à l'écran ou que le Toy est lié, et redessine à chaque `EVENT_AOD`.
- Journal du Toy gardé sur le téléphone (liaisons, AOD, écran allumé/éteint, nombre d'images envoyées) pour savoir ce qui se passe app fermée.
- Le système délie parfois le Toy tout seul (constaté le 25/10 à 01:44, rien touché) : au déliement, l'état du téléphone est noté (écran, économie de batterie, Ne pas déranger, batterie, AOD) et la cause probable s'affiche dans l'app ; s'il n'est pas relancé 2 min après, une notification « La matrice ne s'affiche plus » ouvre directement le choix du Glyph Toy (`SurveillanceToy`). Verso ne peut pas se re-choisir lui-même (réglage système protégé).
- ~~Foreground service~~ : abandonné, il ne passe pas devant un Toy.

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

Champ optionnel `categorie` (rayon de la bibliothèque). Les modèles intégrés sont dans `app/src/main/assets/modeles/` (ordre = nom de fichier, numéros sur 3 chiffres) et se régénèrent avec `outils/gen_modeles.py` (les 10 d'origine) et `outils/gen_modeles_pack.py` (le pack de l'étape 6). Un fichier invalide est ignoré et signalé dans Diagnostic.

Le même format servira à l'éditeur, aux modèles dynamiques et à Trame.

## 4. Feuille de route après la V1 (dans cet ordre)

1. **Modèles dynamiques avancés** : minuteur, visualiseur musique, notifications, météo réelle.
2. **Modèles automatiques** : changement de modèle selon des règles (heure, musique en cours, notification d'une app précise).
3. **Connexion Trame** : afficher la créature Trame au dos du téléphone. Pas urgent.
4. **Intégration NCOS.**

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
4. Glyph Toy AOD : affichage app fermée, horloge réelle, animations, restauration au redémarrage (le système relance le Toy). Tester si les animations tournent écran éteint (journal du Toy + expérience « wake lock »).
5. Widget écran d'accueil (+ favoris simples).
6. Plein de modèles (90) + bibliothèque par rayons.
7. Créateur de modèles : éditeur dessin complet (statique et animé : crayon, gomme, 9 luminosités, remplissage, ligne/cercle, symétrie, décaler/tourner, images + durée, pelure d'oignon, aperçu live sur la matrice, import/export JSON) + modèles dynamiques (forme à préciser : blocs ou formule).
8. Ordre des favoris + finitions UI.
