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
- `GlyphMatrixManager.getInstance()` est un singleton pour tout le processus : une seule connexion partagée, initialisée une fois. **Jamais `unInit()`** : constaté le 25/10 (v0.1.30), après `unInit()` un nouvel `init()` ne reçoit plus aucune réponse du service Glyph, et la matrice reste morte jusqu'au redémarrage de Verso.
- **Matrice figée — cause trouvée** (25/10) : `register()` doit être appelé **après** la liaison du Toy par le système. Si Verso était déjà enregistré avant (processus lancé par une mise à jour, l'app ou le démarrage, ou Toy délié puis relié), le SDK accepte les images (~550-730/min dans le journal, aucune erreur) mais la matrice reste figée, même app ouverte. v0.1.28 et v0.1.35 (simple `connect()` à la liaison) : figées ; v0.1.33 (réenregistrement à la liaison) : fonctionnait. Donc à chaque liaison du Toy : `relancer()` = `register()` sans `unInit()`. Même symptôme à l'extinction de l'écran (téléphone retourné : images envoyées ~550/min, matrice figée, repart au rallumage) : `register()` aussi à l'extinction, au rallumage et à chaque `EVENT_AOD` (v0.1.39). Bouton de secours « ↻ Matrice figée ? Redémarrer Verso » sous la grande matrice.
- **Veille du processeur** : téléphone face cachée, l'écran s'éteint et le processeur peut se mettre en veille au milieu d'un appel au SDK. Ne jamais mesurer la durée d'un appel avec l'heure murale (`currentTimeMillis`), et ne jamais couper la connexion parce qu'un appel « dure » : en v0.1.33, ça figeait la matrice 5 s après avoir retourné le téléphone.
- Si le service Glyph ne répond plus 2 fois de suite, la liaison est considérée perdue : bandeau « Redémarrer Verso » dans l'app ; app fermée, et seulement si la matrice avait marché depuis le lancement du processus (pas de boucle quand Glyph Interface est coupée), redémarrage automatique (au plus 1×/10 min, noté dans le journal du Toy) ; le système relance alors le Toy tout seul.
- Le contrôleur n'empile jamais les images (seule la dernière commande attend). Un chien de garde note dans le journal un appel au SDK de plus de 4 s (hors veille), sans rien couper. Bandeau « La matrice ne répond pas » dans l'app quand la connexion est en erreur.
- Deux canaux d'affichage :
  - **App** (`setAppMatrixFrame`) : ne tient que tant que l'app est au premier plan, et **n'importe quel Glyph Toy passe devant**.
  - **Glyph Toy** (`setMatrixFrame`) : prioritaire, reste affiché app fermée. Sur le (4a) Pro, seuls les Toys « Always-on » existent (*Réglages › Glyph Interface › Flip to Glyph › Always-on Glyph Toy*), un seul à la fois, et le système envoie `EVENT_AOD` environ une fois par minute.
- Clé SDK : `NothingKey = test` suffit pour un usage perso (vérifié : pas besoin du mode debug ADB). Une vraie clé (GDKsupport@nothing.tech) ne servirait qu'à publier.
- Méthode non documentée du SDK : `setGlyphMatrixTimeout(boolean)`, testable depuis le Diagnostic.

## 3. V1 — Switch rapide + widget

### 3.1 Bibliothèque de modèles
- Une liste de modèles avec un aperçu dot-matrix 13×13 qui reproduit la forme ronde de la vraie matrice.
- Deux types de modèles : **statique** (une seule image) et **animé** (plusieurs images + durée de chaque image).
- 90 modèles intégrés (étape 6), rangés par rayon : Effets, Météo, Icônes, Émotions, Persos (originaux, aucun personnage protégé), Formes, Fêtes, Texte, Infos. Puces en haut de la bibliothèque : Tous, ★, puis chaque rayon. Tous les aperçus sont animés, partout (bibliothèque, fenêtre ☰), mais seulement quand la carte est à l'écran : on peut faire défiler 90 modèles sans ralentir l'app.
- Tap sur un modèle → il s'affiche **immédiatement** sur la matrice.
- Un bouton « Éteindre la matrice » ; matrice éteinte, il devient « Rallumer « X » » (dernier modèle éteint, depuis l'app, le widget ou la fenêtre ☰). Toucher la grande matrice ouvre les réglages du modèle affiché.
- Favoris : ☆/★ sur chaque carte (`FavorisStore`) ; un nouveau favori arrive à la fin. **Ordre réglable** (étape 8) : dans le rayon ★, appui long sur une carte puis glisser (les autres cartes se décalent, petite vibration à chaque changement de place, ⚙ ouvre toujours les réglages). Cet ordre est celui du widget (‹ ›, tap « Suivant ») et de la fenêtre ☰.

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
- ☰ (masqué quand le tap ouvre déjà la fenêtre) → petite fenêtre par-dessus l'écran d'accueil (`ChoixRapideActivity`) : onglets Favoris / ✎ Mes (s'il y a des créations) / Tous, tap = affiché, plus Éteindre et Ouvrir Verso. Android ne laisse pas un widget capter l'appui long (il sert au lanceur), d'où le bouton.
- Écran de réglage à la pose, puis via appui long › Paramètres du widget (`ConfigWidgetActivity`), avec le vrai widget en aperçu : thème sombre/clair, fond plein/transparent, points ronds/carrés, taille des points, LEDs blanches (noires en clair) ou rouges, animé/fixe, nom, boutons, action du tap. Un style par widget (`StylesWidgetStore`).
- **Rendu** (`RenduWidget`) : trois images carrées empilées — disque « LED éteinte », image du modèle en ALPHA_8 45×45 (3 px par LED, teintée « LED allumée »), masque percé d'un trou par LED (teinté couleur du disque). Les calques sont générés par `outils/gen_masques_widget.py`.
- **Animation** : toutes les images sont envoyées au lanceur dans un `ViewFlipper`. C'est le lanceur qui anime, Verso n'a pas besoin de tourner, et le ViewFlipper se met en pause dès que le widget n'est plus visible (app ouverte, écran éteint). Un seul intervalle par ViewFlipper : une image longue est répétée. Budget de 380 Ko d'images par widget (l'échange système → lanceur est limité à ~500 Ko) : au-delà, une image sur deux, durées cumulées (la pluie passe ainsi de 70 à 140 ms par image). Le modèle « Compteur test » (Diagnostic) sert à vérifier la pause.
- **Horloge** : une image, renvoyée à chaque minute pile par une alarme RTC qui ne réveille pas le téléphone (`USE_EXACT_ALARM`) ; écran éteint, elle attend le réveil.
- Mises à jour (`WidgetMaj`) quand le modèle actif, ses réglages ou le style d'un widget changent ; un widget inchangé n'est pas renvoyé.

### 3.3 Glyph Toy « Verso » (affichage permanent)
- Verso déclare un **Glyph Toy AOD** (`VersoToyService`). Une fois choisi dans les réglages Glyph (bouton dans l'app), il affiche le modèle actif **app fermée**, et le système le relance tout seul après un redémarrage.
- Le modèle actif est mémorisé (`ModeleStore`) ; le Toy le relit à chaque liaison.
- Le `MatrixController` est unique et bascule de canal : Toy quand le système a lié le service, app sinon.
- **Priorités (schéma officiel du SDK, Phone (4a) Pro)** : de haut en bas, lampe Glyph, aperçus, identité de l'appelant, notifications, **affichage d'une app (`setAppMatrixFrame`)**, Flip to Glyph, indicateur de volume, NFC, notifications Essential, et tout en bas le **Toy AOD**. La couche app passe donc devant le Toy et garde sa dernière image jusqu'à `closeAppMatrix()`, même Verso arrêté. Verso la ferme à la bascule vers le canal Toy et à chaque enregistrement en canal Toy (correctif du 25/10 au soir). Avant ce correctif : chaque démarrage de Verso (MAJ, redémarrage, tuile Glyph off/on avec l'app ouverte) laissait une image de Pluie dans la couche app, et la matrice restait figée dessus jusqu'à la tuile Glyph off/on.
- **Écran éteint** : l'animation continue, processeur toujours gardé éveillé pendant l'animation (réglage fixe, plus d'interrupteur). `setGlyphMatrixTimeout` n'est plus jamais appelé (délai du système). Les essais « 1 i/s » puis « pause écran éteint » (v0.1.43 et v0.1.45) ont été annulés le 25/10 : les gels écran éteint venaient probablement de la couche app coincée.
- Le `ModelePlayer` joue les animations / l'horloge tant que l'app est à l'écran ou que le Toy est lié, et redessine à chaque `EVENT_AOD`.
- Journal du Toy gardé sur le téléphone (liaisons, AOD, écran allumé/éteint, nombre d'images envoyées) pour savoir ce qui se passe app fermée.
- Démarrage du téléphone / mise à jour de Verso (`DemarrageReceiver`) : si le Toy n'est toujours pas lié 60 s après, essai d'affichage par le canal app (noté dans le journal). L'essai est annulé dès que le Toy se lie, et écarté si le Toy a été lié dans les 2 min avant l'événement ou si le canal est déjà Toy (bug de l'étape 8 : il se lançait juste après une liaison).
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

Le même format sert à l'éditeur (§3.5) et servira à Trame. Champ optionnel `dynamique` : `horloge` (modèle intégré calculé) ou `blocs` (§3.5).

### 3.5 Créateur de modèles (étape 7, refait en version simple le 25/10)
Au-dessus de la bibliothèque : **✎ Créer un dessin** et **Importer**. Les créations sont rangées dans le rayon **Mes modèles** (puce dédiée, marquées ✎) et marchent partout comme les modèles intégrés : tap, réglages communs, favoris, widget, fenêtre ☰, Glyph Toy.

**Stockage** (`MesModelesStore`) : un fichier JSON par modèle dans `files/mes_modeles/`, id `perso-<date de création en base 36>`. Fiche d'une création (appui long / ⚙) : **Modifier**, **Partager** (JSON), **Fichier .json**, **Supprimer** (avec confirmation). Fiche d'un modèle intégré : **Dessiner à partir de ce modèle** (copie, l'original ne change pas). **Importer** : un fichier .json ou le presse-papiers ; chaque modèle importé reçoit un nouvel id.

**Éditeur dessin** (`CreateurActivity` › `EditeurDessin`) — un seul écran, pensé pour être compris sans explication :
- En haut : ←, le nom (tap = renommer ; demandé à l'enregistrement s'il est vide), **✓ Enregistrer**.
- La grande matrice : on dessine au doigt. Indice discret en dessous (« Dessine au doigt… »).
- **Crayon**, **Gomme**, **Remplir** + **5 luminosités** (niveaux 1, 3, 5, 7, 9).
- **Bande d'images** : tap = choisir l'image, **＋** = nouvelle image (copie de l'image affichée).
- **▶ Lire**, **↶ Annuler**, **↷ Rétablir**, **⋯ Plus**.
- Menu **⋯** : formes (ligne, cercle, disque), symétries ↔ ↕, image affichée (décaler, tourner, retourner, effacer, négatif, avancer / reculer, supprimer), vitesse de l'animation (une durée pour toutes les images : très lente 500 ms → très rapide 40 ms), pelure d'oignon, « Voir le dessin au dos du téléphone ».
- Aperçu sur la vraie matrice **seulement tant que l'éditeur est à l'écran** : dès qu'il passe en arrière-plan ou se ferme (`onPause`), la matrice reprend le modèle actif.

**Plus tard** (option « dynamiques tout faits ») : choisir un modèle dynamique prêt à l'emploi (heure + secondes, heure + batterie, texte défilant…) avec 2-3 réglages, à la place des blocs.

**Anciens modèles par blocs** (v0.1.26 à 0.1.28, éditeur retiré le 25/10) : ils restent jouables et partageables, mais ne se modifient plus. Format conservé pour référence :

Rendu `MoteurBlocs` : des blocs posés sur la matrice, chacun avec position `x`,`y`, taille `l`×`h` (en LEDs) et luminosité `niveau` 1-9. Les blocs sont dessinés dans l'ordre de la liste ; une LED garde la valeur la plus lumineuse. Le contenu d'un bloc est centré dans son rectangle et coupé à ses bords.

| Type (`type`) | Options (`options`) |
|---|---|
| `heure` | `style` chiffres / aiguilles · `afficher` hhmm (2 lignes, 7×11) / hh / mm · `format` 24 / 12 · `cadran` oui / non |
| `date` | `format` jour / jourmois (2 lignes) / semaine (LU…DI) |
| `batterie` | `style` anneau / barre / chiffre · `piste` oui / non |
| `secondes` | `style` anneau / point · `piste` oui / non |
| `texte` | `texte` (60 caractères, police 3×5, accents retirés, ♥ ★) · `vitesse` lent / normal / rapide · `taille` petite / grande (×2). Un texte qui tient reste immobile, centré. |
| `dessin` | `modele` (id d'un modèle : sa première image) · `ajuster` reduire / decouper |
| `effet` | `modele` (id d'un modèle animé) · `ajuster` · `vitesse` 0.5 / 1 / 2 |
| `minuteur` | `cible` HH:MM — minutes restantes avant cette heure (H puis MM sur 2 lignes au-delà d'une heure si le bloc fait 11 de haut) |

Un bloc `dessin` / `effet` reprend un modèle intégré ou un dessin créé (avec ses réglages), jamais un autre modèle par blocs. Un bloc introuvable ou mal réglé est simplement ignoré.

**Rendu en direct** : comme l'horloge, dans `ModelePlayer` (app ouverte ou Glyph Toy lié) et dans les aperçus. Cadence = la plus rapide demandée par les blocs (`Modele.periodeMs`) : texte qui défile 60-150 ms, effet = sa plus courte image, secondes 1 s ; sinon une image à chaque minute pile. Le widget reçoit une image, remise à jour chaque minute (comme l'horloge).

Format JSON d'un modèle par blocs :
```json
{
  "id": "perso-mh3k2x1a",
  "nom": "Mon cadran",
  "categorie": "Mes modèles",
  "type": "statique",
  "dynamique": "blocs",
  "blocs": [
    { "type": "secondes", "x": 0, "y": 0, "l": 13, "h": 13, "niveau": 4 },
    { "type": "heure", "x": 1, "y": 1, "l": 11, "h": 11, "niveau": 9,
      "options": { "style": "aiguilles", "cadran": "non" } }
  ],
  "frames": [ { "pixels": "<aperçu à 10:10, image de secours>", "dureeMs": 1000 } ]
}
```
Valeurs absentes = défauts ci-dessus ; un bloc qui déborde est ramené dans la grille.

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
7. Créateur de modèles : éditeur dessin (statique et animé), import/export JSON (§3.5). Refait en version simple le 25/10 ; le créateur par blocs a été retiré (trop compliqué).
8. Ordre des favoris (glisser-déposer) + finitions : correctif de l'essai « canal app » au démarrage, bouton Rallumer, tap sur la grande matrice = réglages, vibrations, onglet « Mes » dans la fenêtre ☰. **V1 complète.**
