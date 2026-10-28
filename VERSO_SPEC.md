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
- **Matrice figée — vraie cause** (résolu le 25/10, v0.1.47 à 0.1.51) : la couche « app » restait ouverte devant le Toy (voir §3.3, priorités). Correctif : `closeAppMatrix()` au passage au canal Toy. Les `register()` à la liaison, à l'extinction, au rallumage et à chaque `EVENT_AOD` (v0.1.33 à 0.1.39) étaient une **fausse piste** ; ils restent dans le code, inoffensifs, à ne pas présenter comme la cause. Bouton de secours « ↻ Matrice figée ? Redémarrer Verso » sous la grande matrice ; déblocage manuel : tuile Glyph off/on.
- **Veille du processeur** : téléphone face cachée, l'écran s'éteint et le processeur peut se mettre en veille au milieu d'un appel au SDK. Ne jamais mesurer la durée d'un appel avec l'heure murale (`currentTimeMillis`), et ne jamais couper la connexion parce qu'un appel « dure » : en v0.1.33, ça figeait la matrice 5 s après avoir retourné le téléphone.
- Si le service Glyph ne répond plus 2 fois de suite, la liaison est considérée perdue : bandeau « Redémarrer Verso » dans l'app ; app fermée, et seulement si la matrice avait marché depuis le lancement du processus (pas de boucle quand Glyph Interface est coupée), redémarrage automatique (au plus 1×/10 min, noté dans le journal du Toy) ; le système relance alors le Toy tout seul.
- Le contrôleur n'empile jamais les images (seule la dernière commande attend). Un chien de garde note dans le journal un appel au SDK de plus de 4 s (hors veille), sans rien couper. Bandeau « La matrice ne répond pas » dans l'app quand la connexion est en erreur.
- Deux canaux d'affichage :
  - **App** (`setAppMatrixFrame`) : passe **devant** le Glyph Toy (schéma officiel des priorités, §3.3) et garde sa dernière image jusqu'à `closeAppMatrix()`, même Verso arrêté. À fermer dès qu'on passe au Toy.
  - **Glyph Toy** (`setMatrixFrame`) : prioritaire, reste affiché app fermée. Sur le (4a) Pro, seuls les Toys « Always-on » existent (*Réglages › Glyph Interface › Flip to Glyph › Always-on Glyph Toy*), un seul à la fois, et le système envoie `EVENT_AOD` environ une fois par minute.
- Clé SDK : `NothingKey = test` suffit pour un usage perso (vérifié : pas besoin du mode debug ADB). Une vraie clé (GDKsupport@nothing.tech) ne servirait qu'à publier.
- Méthode non documentée du SDK : `setGlyphMatrixTimeout(boolean)`, testable depuis le Diagnostic.

## 3. V1 — Switch rapide + widget

### 3.1 Bibliothèque de modèles
- Une liste de modèles avec un aperçu dot-matrix 13×13 qui reproduit la forme ronde de la vraie matrice.
- Deux types de modèles : **statique** (une seule image) et **animé** (plusieurs images + durée de chaque image).
- 90 modèles intégrés (étape 6), rangés par rayon : Effets, Météo, Icônes, Émotions, Persos (originaux, aucun personnage protégé), Formes, Fêtes, Texte, Infos. La bibliothèque complète n'est **plus affichée en permanence** (26/10) : carte « ▦ Tous les modèles » sur l'écran principal → **feuille de verre** qui monte du bas (façon iOS, §3.9). Puces en haut de la feuille : 🔍, Tous, ★, ◷ Récents, Mes modèles, puis chaque rayon ; « Tous » est rangé par rayons avec un titre par rayon. Grille **paresseuse** (`LazyVerticalGrid`) : seules les cartes à l'écran existent et s'animent.
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

### 3.2 bis Galerie de widgets (refaite le 26/10)
Première version (« Mes widgets », blocs à placer, v0.1.65) jugée pas intuitive par Nico, avec un vrai bug : un widget réglé en 4 × 2 se posait en 2 × 2. Refaite **façon galerie de l'iPhone**, sans blocs à placer.

- Écran principal › **WIDGETS** › **＋ Ajouter un widget** → liste des **genres** avec aperçu : **Matrice**, **Horloge**, **Tout-en-un**, **Batterie**, **Message**.
- Page d'un genre : les **tailles défilent** au doigt (aperçu réel, à la vraie proportion, sur un faux fond d'écran coloré), puis **Style** (Verre / Sombre / Clair / Sans fond), **Couleur** (blanc ou rouge Nothing), texte pour Message, et **＋ Ajouter le widget**.
- **8 tailles**, chacune un widget à part pour le lanceur (`TailleW` → `Verso1x1`, `Verso2x1`, `WidgetPersoProvider` (2 × 2), `Verso3x2`, `Verso4x1`, `Verso4x2`, `Verso4x3`, `Verso4x4`, avec `targetCellWidth/Height`) : un 4 × 2 se pose vraiment en 4 × 2. « Ajouter le widget » appelle `requestPinAppWidget` sur le fournisseur de la bonne taille ; le réglage attend dans `WidgetsPersoStore` (jeton) et `PoseRecepteur` le donne au widget posé.
- Chaque genre a un contenu pensé pour chaque taille (`Genre.contenu`) : par ex. Matrice 4 × 2 = matrice + nom + règle auto + ‹ ⏻ › ; Horloge 4 × 4 = grande heure, date, grande matrice.
- Posé depuis la liste des widgets du lanceur : la galerie s'ouvre avec la taille imposée (`ConfigWidgetPersoActivity`) ; sans réglage, le widget montre la Matrice de sa taille. Dans l'app, la liste « Sur ton écran d'accueil » permet de **modifier** chaque widget posé (contenu et style ; la taille reste celle posée).
- Rendu : `WidgetVerso.plan()` → `RenduPerso` (blocs internes : matrice S/M/L/XL, `TextClock` pour l'heure et la date, batterie, nom, règle auto, texte, boutons). Mises à jour par `WidgetMaj` (modèle, minute, batterie, règle auto, état du Glyph).
- **Verre** dans un widget : le lanceur ne permet pas de flouter le fond d'écran ; `widget_fond_verre` imite le Liquid Glass (voile en dégradé, reflet en haut, ombre intérieure en bas, liseré lumineux).
- **Réglages (27/10, demande de Nico : « pas assez personnalisable, surtout pour Glyph »)**, en trois cartes sous l'aperçu :
  - *Apparence* : style, couleur des LEDs et du texte principal (blanc / noir en clair, rouge Nothing, ambre, bleu glace, vert, rose), texte pour Message.
  - *Matrice* : modèle **Suit le dos** ou **📌 Épinglé** (choisi dans une rangée d'aperçus ; le widget montre toujours ce modèle, horloge comprise, remise à jour chaque minute), points ronds / carrés, LEDs éteintes visibles / cachées, animée / image fixe, **au toucher** : fenêtre de choix, suivant, ⏻ On / Off, ouvrir Verso, **afficher ce modèle au dos** (modèle épinglé = raccourci ; `WidgetActions.AFFICHER`, une intention par modèle).
  - *Afficher* : nom du modèle, boutons ‹ ⏻ ›, règle auto (seulement si la taille les a), pastille **État du Glyph** (`Type.GLYPH` : ○ Glyph éteint en rouge, ● Au dos · Toy actif / ● Glyph allumé en vert, ○ Matrice éteinte ; mis à jour à chaque liaison du Toy et changement du Glyph).
  - JSON relu sans perte : l'ancien `rouge` devient la couleur rouge.
- **Fenêtre de pose du lanceur** : `requestPinAppWidget` reçoit le vrai widget réglé en `EXTRA_APPWIDGET_PREVIEW` (avant : l'aperçu fixe « 12:34 · VERSO PERSO » de `widget_perso_apercu`). **Liste des widgets du lanceur** (Android 15+) : `setWidgetPreview` envoie le vrai rendu Matrice de chaque taille, ~8 s après le démarrage, une fois par version et au plus une fois par jour (API limitée à ~2 appels / heure).
- **Boutons ‹ ⏻ ›** : icônes vectorielles (`ic_w_precedent`, `ic_w_marche`, `ic_w_lecture`, `ic_w_suivant`, Material, Apache 2.0) teintées par `setColorFilter`. La police du lanceur n'a pas le caractère « ⏻ » : il s'affichait en boîte « NO GLYPH ».

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

### 3.6 Règles automatiques (étape 9)
Le modèle change tout seul selon la situation. Carte **Automatique** sur l'écran principal (état, interrupteur général) → `AutomatiqueActivity` :
- **Liste** : « En ce moment » (modèle affiché animé + règle en cours), mes règles (appui long + glisser = ordre = priorité, interrupteur par règle), **idées** en un tap (défilement horizontal), bouton flottant **＋ Nouvelle règle**.
- **Nouvelle règle** (28/10) : elle commence **vide**, sur l'écran « On commence par quoi ? » (le catalogue des conditions) ; retour = abandon.
- **Éditeur** : en haut une **phrase** qui résume la règle (« Si de 22:00 à 07:00 et en charge, afficher Lune. ») avec l'aperçu animé et « ● ce serait le cas maintenant / ○ pas le cas ». Puis **SI…** (une carte par condition, ses réglages juste dessous, ● vraie maintenant), « Toutes (ET) / Au moins une (OU) » dès 2 conditions, **＋ Ajouter une condition** (catalogue par familles). Puis **ALORS…** : Modèle / Hasard / Défiler / Éteindre. **▸ Plus d'options** : nom, revenir au modèle d'avant.

Déclencheurs (22, aucun menu caché : chaque réglage est un bouton) :

| Famille | Déclencheur | Réglages | Source / permission |
|---|---|---|---|
| Temps | Horaire | de… à…, jours | alarme exacte aux bornes |
| Temps | À une heure (moment) | heure, jours | alarme exacte |
| Temps | Au réveil (moment) | fenêtre (5h-12h) | 1er `USER_PRESENT` du jour |
| Temps | Nuit / jour | nuit ou jour, décalage ±1 h, ville | calcul NOAA hors ligne (`Soleil`), ville cherchée une fois avec le géocodeur du téléphone |
| Temps | Une date | jour + mois, chaque année | minuit |
| Temps | Avant l'alarme | 15 min… 2 h | `AlarmManager.nextAlarmClock` (app Horloge) |
| Batterie | En charge | tout / câble / sans fil | `BATTERY_CHANGED` |
| Batterie | Batterie faible | 10-30 % (hors charge) | idem |
| Batterie | Batterie pleine | 80-100 % (en charge) | idem |
| Batterie | Économie | — | `POWER_SAVE_MODE_CHANGED` |
| Téléphone | Verrouillé / déverrouillé | — | écran allumé / éteint / déverrouillé |
| Téléphone | Ne pas déranger | — | `INTERRUPTION_FILTER_CHANGED` |
| Téléphone | Silencieux / vibreur | les deux / l'un | `RINGER_MODE_CHANGED` |
| Téléphone | Mode avion | — | `AIRPLANE_MODE_CHANGED` |
| Téléphone | Lampe | — | `CameraManager.TorchCallback` |
| Connexions | Casque | n'importe lequel / un précis | sorties audio, sans permission |
| Connexions | Bluetooth (montre, voiture…) | n'importe lequel / un associé | `ACL_CONNECTED/DISCONNECTED` (récepteur du manifeste, liste gardée dans `verso_auto`) ; **permission « Appareils à proximité »** demandée dans l'éditeur |
| Connexions | Réseau | Wi-Fi / données / hors ligne | `ACCESS_NETWORK_STATE` (sans le nom du Wi-Fi) |
| Apps | Notification (**flash**) | apps (toutes ou cochées), durée 3 / 5 / 10 / 30 s | `EcouteNotifs` (NotificationListenerService) ; **« Accès aux notifications »** |
| Apps | Musique | apps (n'importe laquelle ou cochées) | sessions média (`MediaSessionManager.getActiveSessions`, même accès) |
| Apps | App ouverte | une ou plusieurs apps (au moins une) | `AppPremierPlan` : service d'accessibilité « Verso · Glyph et apps » (`TYPE_WINDOW_STATE_CHANGED`, vraie activité seulement, menu déroulant et claviers ignorés) ; repli **« Accès aux données d'utilisation »** (lecture toutes les secondes, écran allumé, seulement si une règle l'utilise) |
| Temps | Météo | soleil / nuages / pluie / neige / orage (plusieurs) | `Meteo` (Open-Meteo), mesure de plus de 3 h = inconnue |

**Notification = règle « flash »** (partie 2, 28/10) : ni « pendant » ni « moment ». À chaque nouvelle notification d'une app acceptée, la première règle flash de la liste (avec « toutes », ses autres conditions doivent être vraies, moments exclus) affiche son modèle pendant la durée choisie, puis **fondu** : le dessin s'éteint en 480 ms, le modèle d'avant revient en 480 ms (8 images chacun) et reprend sa boucle (`ModelePlayer.flash`). Rien n'est mémorisé (ni modèle actif, ni mémoire des règles). Pas de flash si la matrice est éteinte, ni si personne ne peut la voir (app fermée et Toy pas lié : jamais de couche app). Un choix à la main, une règle ou un autre flash l'interrompt. Actions possibles : Modèle ou Hasard. Notifications ignorées : celles de Verso, permanentes / service au premier plan, résumés de groupe, « silencieuses » (`isAmbient`), mises à jour (`FLAG_ONLY_ALERT_ONCE`, ou même clé < 3 s). Verso ne lit que le paquet de l'app.

**App ouverte** (partie 3, 28/10) : vraie tant qu'une des apps est au premier plan, téléphone déverrouillé. Une nouvelle règle « App ouverte » est ajoutée **en tête** (elle doit gagner tant que l'app est à l'écran). À la sortie, le modèle d'avant revient (comportement normal d'une règle « pendant »). Le service d'accessibilité du bouton Glyph (§3.8) n'est plus limité au menu déroulant : il reçoit les changements de fenêtre de toutes les apps (jamais leur contenu) ; il s'appelle désormais **« Verso · Glyph et apps »**.

**Météo** (`auto/Meteo.kt`) : `api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code,is_day` (gratuit, sans clé), ville de « Nuit / jour » ou Meylan par défaut. Code WMO → ciel : 0-1 soleil, 2-3 / 45-48 nuages, 51-67 / 80-82 pluie, 71-77 / 85-86 neige, 95-99 orage. Mesure gardée sur le téléphone (`verso_meteo`), redemandée si elle a plus de 30 min : quand le modèle Météo réelle est dessiné, ou par l'alarme des règles si une condition Météo existe (le récepteur attend alors 10 s). Un changement de ciel réévalue les règles.

**Idées « App ouverte »** : l'app est cherchée sur le téléphone (nom contenant météo / weather ; Spotify, YouTube Music, Deezer… ; appareil photo par défaut) ; pas trouvée = l'éditeur s'ouvre directement sur le choix des apps.

**Demande d'accès aux notifications** (`auto/DemandeAcces.kt`, 28/10) : fenêtre à l'ouverture de Verso (écran principal et Automatique) tant qu'une règle active en a besoin (Notification, Musique, App ouverte, ou modèle de son) et que l'accès manque ; bouton = interrupteur de Verso directement ; disparaît au retour si accordé ; « Plus tard » = jusqu'au prochain lancement. Android n'offre pas de fenêtre système pour cet accès. Sans l'accès, un son qui joue (« ? ») compte pour les apps de musique d'une règle App ouverte (`Evaluation.estAppMusique`).

**Choix des apps** (`auto/ChoixApps.kt`) : apps du lanceur (`<queries>` MAIN/LAUNCHER), icône + nom, recherche sans accents, cochées en haut ; rien de coché = toutes. Carte d'autorisation avec bouton direct (`ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS`) et rappel « 🔒 Grisé ? Fiche de Verso › ⋮ › Autoriser les paramètres restreints » (app hors Play Store) ; elle disparaît dès que c'est accordé.

Actions : **Modèle** ; **Hasard** (un favori au hasard, ou toute la bibliothèque sans favori) ; **Défiler** (les favoris dans l'ordre ★ toutes les 5-60 min, tant que la règle dure, par alarme) ; **Éteindre**.

**Comportement** (`Evaluation` + `Decision`, 20 tests dans `ReglesTest`) :
- **Priorité** : la règle « pendant » la plus haute dans la liste gagne, **sauf le son en TOP 1** (28/10, Nico : « quand c'est le son, c'est primordial ») : une règle vraie avec une condition Musique, ou qui affiche Égaliseur / Musique en direct (`Evaluation.estSon`), passe devant toutes les autres.
- **Son détecté** : une app de « App ouverte » qui joue du son compte comme ouverte, même en arrière-plan. Avec l'accès aux notifications, seules les sessions média comptent (qui joue vraiment). Sans lui, `AudioManager.isMusicActive` (relu à chaque `AudioPlaybackCallback`, sans autorisation) dit qu'un son joue (app inconnue « ? » : suffit pour Musique « n'importe quelle app ») et réévalue les règles à chaque début / fin de son. `isMusicActive` reste vrai quelques secondes après l'arrêt sans nouveau rappel → règle Deezer → Égaliseur bloquée : il est relu 1,5 s et 4 s après chaque rappel (corrigé PR #47). Musique arrêtée : nouvelle évaluation 2,5 s après.
- Une règle s'applique **quand la gagnante change** (elle commence, une autre passe devant, ou son action est modifiée). Tant que la gagnante ne change pas, rien n'est imposé.
- **Choix à la main** (app, widget, ☰) : il tient jusqu'au prochain changement automatique (« option A »). Pendant une règle, c'est lui qui revient à la fin.
- **Fin d'une règle** : retour au modèle d'avant (ou au dernier choix à la main) ; option pour garder.
- **Moment** (réveil, heure précise) : une fois par jour par règle ; avec « toutes (ET) », les autres conditions doivent être vraies à ce moment. Le modèle devient celui à remettre après une règle en cours.
- Une alarme exacte (`AlarmeRegles`, réveille le téléphone) tombe au prochain instant où une règle peut changer : bornes de plage, heure précise, lever / coucher ± décalage, minuit, alarme − X min, défilement.
- La mémoire du moteur (règle en cours, modèle à remettre, moments du jour, défilement) est dans `verso_auto` : après un redémarrage du processus, rien ne se redéclenche si la situation n'a pas changé.
- Le moteur ne touche jamais la matrice lui-même : `ModelePlayer.appliquerAuto`. App fermée et Toy pas lié, il **mémorise seulement** (le Toy reprend à sa liaison) : il n'ouvre jamais la couche app de la matrice app fermée (§3.3).
- Chaque changement est noté dans le journal du Toy (« Auto (raison) : « Nuit » → lune »).
- ⚠️ L'« Horaire du coucher » de Glyph Interface éteint la matrice : une règle de nuit pendant cet horaire ne se voit pas.

Format JSON d'une règle (`RegleStore`, ordre de la liste = priorité ; l'ancien format `quand` + `modele` de la partie 1 est relu) :
```json
{ "id": "regle-mh3k2x1a", "nom": "Dodo en charge", "active": true, "revenir": true, "toutes": true,
  "si": [ { "type": "plage", "debut": 1380, "fin": 420, "jours": [1,2,3,4,5,6,7] }, { "type": "charge" } ],
  "alors": { "type": "eteindre" } }
```
Types `si` : `notif` (`apps` = [{`pkg`, `nom`}], vide = toutes ; `secondes`), `musique` (`apps`), `app` (`apps`), `meteo` (`ciels` : soleil / nuages / pluie / neige / orage), `plage` (`debut`, `fin` en minutes, `jours` 1 = lundi), `aheure` (`minute`, `jours`), `reveil` (`debut`, `fin`), `soleil` (`nuit`, `decalage`), `date` (`jour`, `mois`), `alarme` (`minutes`), `charge` (`source` cable/sansfil), `batterie` (`seuil`), `pleine` (`seuil`), `economie`, `ecran` (`verrouille`), `dnd`, `sonnerie` (`mode` silencieux/vibreur), `avion`, `lampe`, `casque` (`appareil`), `bluetooth` (`appareil`), `reseau` (`reseau` wifi/donnees/aucun). `alors` : `modele` (+ `modele`), `hasard`, `defiler` (+ `minutes`), `eteindre`.

Le Wi-Fi par nom est **abandonné** (28/10, Nico : peu utile, coûte la localisation « Toujours »).

### 3.6 bis Modèles live (étape 9, partie 3)
Rayon **Live**, en tête de la bibliothèque (`generateur/Live.kt`) : **Horloge** (quitte Infos), **Météo réelle**, **Musique en direct**, **Égaliseur** (quitte Effets), tout ce qui suit le vrai monde. Modèles dynamiques (comme l'horloge) qui se dessinent avec des données réelles ; marchent partout (tap, favoris, widget à la minute, règles).
- **Météo réelle** (`meteo-live`, une image toutes les 90 ms) : icône animée reprise de la bibliothèque selon le ciel (soleil / lune la nuit, éclaircie, nuage, pluie V1, neige-nuage, orage) et température en chiffres 3×5 (« 12° », « -5° », « -12 »). Réglage *Afficher* : les deux à tour de rôle (5 s icône, 3 s température), icône, température. Pas encore de mesure : « -- ».
- **Musique en direct** (`musique-live`, 100 ms) : 5 barres d'égaliseur pendant la lecture, ▮▮ en pause, et **anneau d'avancement** du titre sur le bord de la matrice (réglage). Rien ne joue : la note de la bibliothèque, à moitié éteinte. Demande l'accès aux notifications (sessions média).
- **Égaliseur** (`egaliseur`, même id que l'ancien : favoris et règles gardés) : V1 = l'animation d'origine ; **V2 (défaut)** = vrai son, dessin de l'original (« il ressemble à rien », 28/10) : *Grosses* (4 barres de 2 LEDs, défaut), *Fines* (6), *Centre* (6, haut et bas) ; lignes 2 à 10, dégradé base douce → sommet à fond, crête qui reste 300 ms puis retombe d'une ligne / 120 ms ; 50 ms. Sans mesure possible : l'animation V1 ; mesure active mais silence : ligne de points au repos.
  - **Égaliseur réel** (28/10, `auto/Spectre.kt`, refait le même soir : 9 bandes log de 40 Hz à 14 kHz ; si la sortie du téléphone (session 0) reste muette 3 s ou est refusée, essai sur la session annoncée par l'app de musique via `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` ; état lisible `Spectre.diag` : ⏸ / 🎤 / ❌ / 🔇 / ✅, affiché dans la fiche du modèle et noté dans le journal du Toy) : `Visualizer` sur la sortie audio (session 0), FFT ~20 fois par seconde, 5 bandes (30-150, 150-450, 450-1 400, 1 400-4 000, 4 000-12 000 Hz), échelle log, gain automatique par bande, montée immédiate et descente douce. Demande l'autorisation **Micro** (`RECORD_AUDIO`, exigée par Android pour le Visualizer ; le micro n'est jamais enregistré) et `MODIFY_AUDIO_SETTINGS`. Carte « 🎤 Égaliseur sur la vraie musique » dans la fiche du modèle et sous le modèle d'une règle. Capture lancée au premier dessin, arrêtée 3 s après le dernier. Pas de mesure récente ou son muet (> 1,5 s) → l'animation reprend. ⚠️ À vérifier : Android peut rendre la capture muette quand Verso n'est pas à l'écran.

### 3.7 Liquid Glass (interface)
Vrai verre façon iOS, avec la bibliothèque **Backdrop** (`io.github.kyant0:backdrop:1.0.1`, Kyant, Apache 2.0) : réfraction des bords (shader AGSL `lens`, avec profondeur), flou, vibrance, reflet (`Highlight`) et ombre. Code dans `ui/verre/Verre.kt` :
- **Verre flottant** (28/10, « au plus fluide mais je veux du style ») : tout ce qui est dans `dessus` (mini-lecteur, barres du haut, boutons flottants, menu ☰) ne floute plus que le **fond**, plus le contenu qui défile dessous. Avant, tout l'écran était enregistré dans un calque à chaque image et chaque verre du contenu y était recalculé une 2ᵉ fois. Style compensé (`LocalFlottant`) : verre fumé plus dense (noir 34 %), lumière en dégradé du haut, lueur rouge au bas, liseré clair. Le banc (« ancien moteur ») garde l'ancien calque pour comparer.
- `EcranVerre` : fond (noir, lueurs rouge Nothing / blanche / rouge sombre qui suivent le défilement — et dérivent à 20 images/s si « Fond animé » est activé —, grille de points façon matrice) + contenu en verre sur ce fond + éléments flottants (`dessus`) en verre sur le fond **et** le contenu qui défile dessous.
- `Modifier.verre`, `PanneauVerre`, `BoutonVerre` (principal = teinté rouge), `PuceVerre`, `RondVerre`, effet d'appui qui enfonce le verre (ressort + vibration).
- Réglages (26/10, renforcés à la demande de Nico) : flou léger (7 dp) pour un verre plus transparent, réfraction forte des bords (22 dp) avec irisation (`chromaticAberration`, retirée des petites puces où elle ne se voit pas), reflet plus marqué, lueurs du fond plus vives.
- **Fond** : les lueurs suivent le défilement mais ne dérivent plus toutes seules ; interrupteur **Plus › Fond animé** (coupé par défaut) pour remettre la dérive à 20 i/s.
- Partout sur l'écran principal (disque de la grande matrice, boutons, puces des rayons, statut du Toy, cartes Automatique et Widgets) et dans Automatique (barres et boutons flottants).
- **Mini-lecteur** (`ui/MiniLecteur.kt`) : barre de verre flottante en bas de l'écran principal, visible **dès que la grande matrice sort de l'écran et quand la bibliothèque est ouverte** (comme le mini-lecteur de Musique sur iPhone) : matrice en direct, nom du modèle, règle auto en cours ou minuteur de veille, ‹ › et **glisser à gauche / droite** (favoris ou toute la bibliothèque, comme le widget), ⏻ / ▶ (éteindre / rallumer le dernier). Toucher = remonter à la grande matrice. **Appui long** = panneau de verre : luminosité du modèle affiché (enregistrée dans ses réglages), minuteur de veille 15 / 30 / 60 min (alarme exacte `MinuteurVeille`, marche app fermée), 🎲 un favori au hasard.
- **Puces des rayons** (`ui/FiltreBibliotheque.kt`) : fixes en haut de la feuille Bibliothèque (la barre collante de l'écran principal n'a plus lieu d'être). Puce 🔍 = recherche par nom ou rayon, sans accents ni majuscules, dans toute la bibliothèque. Rayon **◷ Récents** : les 12 derniers modèles affichés (`RecentsStore`, à la main ou par une règle).
- **Tuile des réglages rapides** « Verso » (`rapide/TuileVerso`) : sous-titre = modèle en cours ; tap = modèle suivant (rallume si éteinte) ; appui long = ouvre Verso.
- **Raccourcis de l'icône** (appui long sur l'icône, `res/xml/raccourcis.xml` → `RaccourciActivity` invisible) : Modèle suivant, Au hasard, Éteindre / rallumer, Automatique.
- Toutes ces actions passent par `ModelePlayer.jouer / eteindre` (`rapide/ActionsRapides`), comme un tap dans l'app : pour les règles automatiques, ce sont des choix à la main.
- **Pas** de verre sur les 90 cartes de modèles (trop lourd à 90) : fond noir translucide qui laisse passer les lueurs. Les tuiles de la rangée « Accès rapide » (une dizaine à l'écran) sont, elles, en verre.
- **Appui façon iOS 26** (`PressionVerre`, 26/10) sur tous les boutons, puces, cartes et tuiles de verre : le verre gonfle un peu, s'étire vers le doigt (élastique) et une lueur suit le doigt (shader AGSL). Aucun évènement consommé : clic et défilement inchangés ; si le doigt part en défilement, l'effet se relâche.
- **Feuille de verre** (bibliothèque) : verre plein écran très flouté posé sur l'écran principal ; il **exporte** son rendu (`exportedBackdrop` de Backdrop) pour que les puces et boutons posés dessus réfractent la feuille. Couches de `EcranVerre` : fond → contenu → feuille → éléments flottants.
- Sans fond disponible (fenêtres qui ne sont pas dans un `EcranVerre`), `verre` retombe sur un panneau sombre classique.
- Compose foundation/ui passent en 1.9.5 (exigé par Backdrop) ; Kotlin reste en 2.1.20.

### 3.8 Le vrai bouton Glyph (26/10)
*Depuis le 28/10, le service s'appelle « Verso · Glyph et apps » et sert aussi à la condition « App ouverte » (§3.6).*

Demande de Nico : quand la Glyph Interface est coupée (tuile « Glyph » du menu déroulant), ⏻ dans Verso ne servait à rien. Il veut que ⏻ appuie **réellement** sur ce bouton.

**Analyse** : Nothing ne donne aucun moyen aux apps d'allumer / éteindre la Glyph Interface. Le SDK ne fait qu'afficher (`setMatrixFrame`, `setAppMatrixFrame`, `closeAppMatrix`), et le réglage système est protégé (`WRITE_SECURE_SETTINGS`, ADB seulement, abandonné par Nico).

**Solution** : service d'accessibilité **« Verso · bouton Glyph »** (`glyph/BoutonGlyph`, `res/xml/bouton_glyph.xml`, limité à `com.android.systemui`). Il ne réagit à aucun évènement de lui-même ; quand Verso le lui demande, il :
1. ouvre le menu déroulant (`GLOBAL_ACTION_QUICK_SETTINGS`) ;
2. cherche la tuile dont le nom commence par « Glyph » (pas la lampe / torche, ni le Toy, ni la tuile Verso), en faisant défiler les pages de tuiles si besoin (~2,5 s max) ;
3. lit son état (case cochée, ou « Activé / Désactivé ») et n'appuie que s'il faut changer ;
4. relit l'état, puis referme le menu (`GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE`). Chaque appui est noté dans le journal du Toy.

**Branchements** (`rapide/ActionsRapides`) : ⏻ partout (bouton de l'app, mini-lecteur, widget On/Off, fenêtre ☰, tuile Verso, raccourci de l'icône, fin du minuteur de veille) = **éteindre le modèle ET la Glyph Interface**, ou **rallumer la Glyph Interface puis le dernier modèle**. Si le Glyph est connu éteint, ⏻ rallume même si un modèle est choisi. Les règles automatiques « Éteindre » ne touchent **pas** au Glyph système (sinon elles couperaient aussi les notifications Glyph sans prévenir).

**État connu** : lu à chaque appui ; « allumé » quand le Toy de Verso est lié (le système ne le lie que Glyph allumé) ; « inconnu » quand le Toy est délié (coupé à la main ?).

**Interface** : carte sous la grande matrice. Si le service n'est pas activé, elle donne un guide en 3 étapes : fiche de Verso › ⋮ › « Autoriser les paramètres restreints » (app installée hors Play Store), puis Accessibilité › « Verso · bouton Glyph ». Une fois activé, elle montre l'état (● allumé / éteint) et un bouton Allumer / Éteindre. Sans le service, ⏻ fait comme avant (modèle seulement).

**Limites** : le menu déroulant s'ouvre et se ferme en un éclair ; si Nothing renomme ou déplace la tuile dans une mise à jour, il faudra adapter la recherche. La logique de la matrice (`closeAppMatrix` au passage au Toy) est inchangée.

### 3.9 Écran principal et performance (26/10)
Demande de Nico : l'app rame un peu ; les 90 modèles prennent toute la place ; écran plus lisible et plus interactif **sans supprimer aucune option** ; preuves mesurées, pas de « c'est plus fluide ».

**Organisation** (`ui/VersoScreen.kt`, `ui/Accueil.kt`) :
1. En-tête : « Verso », version et état des mises à jour (bouton « Mettre à jour » seulement s'il y en a une).
2. **La matrice** (disque de verre ; tap = réglages du modèle), nom, règle auto ou minuteur ; **‹ ⏻ ›** ; puces **☆ Favori**, **⚙ Réglages**, **◐ Options** (luminosité, minuteur de veille, 🎲 au hasard — les mêmes que l'appui long sur le mini-lecteur).
3. **Deux pastilles d'état** : Glyph Toy (vert / ambre / rouge) et bouton Glyph. Toucher = détails (carte du Toy + carte du vrai bouton Glyph avec son guide d'activation). Les détails du Toy s'ouvrent tout seuls s'il y a un problème. La carte « La matrice ne répond pas » reste toujours visible quand il le faut.
4. **Accès rapide** : ★ Favoris / ◷ Récents en une rangée de tuiles de verre qui défile (tap = afficher, appui long = réglages), « Ordre › / Tout voir › » ; carte **▦ Tous les modèles** → feuille Bibliothèque.
5. **Automatique** (inchangé).
6. **Réglages** repliés : **Widgets** (galerie + widgets posés + anciens widgets) et **Plus** (Fond animé, « ↻ Matrice figée ? Redémarrer Verso », Diagnostic avec le banc de fluidité).

**Feuille Bibliothèque** (`FeuilleBibliotheque`) : monte du bas avec un ressort, poignée à tirer vers le bas, ✕ ou retour Android pour fermer. Dedans : rayons, 🔍, ✎ Créer un dessin / ⇩ Importer, réordonner ★ (appui long + glisser), ⚙ et appui long = fiche du modèle. Rayon et recherche gardés entre deux ouvertures.

**Causes de lenteur trouvées dans le code (v0.1.67)** et correctifs :
| Cause | Effet | Correctif |
|---|---|---|
| Fond redessiné 20×/s (≈ 1 200 cercles) | chaque verre de l'écran recalcule flou + réfraction 20×/s, même sans toucher | fond fixe au repos (parallaxe gardée), grille en une seule commande `drawPoints`, option Fond animé |
| Image de la matrice lue tout en haut de la bibliothèque | les 90 cartes recomposées à chaque image (~14×/s avec Pluie) | `DotMatrix({ … })` lit l'image **au dessin** : seule la matrice se redessine |
| 90 cartes composées d'un coup + 90 modèles calculés à l'ouverture | ouverture lente, défilement lourd | grille paresseuse + modèles calculés à l'affichage (`CacheModeles`) |
| Aperçus animés qui recomposent la carte entière | texte, ★, ⚙ recalculés à chaque image | `rememberApercu` + dessin seul (aussi fiche du modèle, fenêtre ☰, Automatique) |
| Lambdas de `Modifier.verre` neuves à chaque recomposition | Backdrop compare ses paramètres et reconstruit flou + shader à chaque fois | lambdas mémorisées |
| Calques englobants (`layerBackdrop`) qui redessinent tout | tout l'écran rejoué pour chaque calque | fond et contenu dans leur propre calque, réutilisés |
| Barre collante recalculée à chaque pixel de défilement | recompositions pendant le défilement | supprimée (puces fixes dans la feuille) |
| Écran principal animé derrière la feuille | flou plein écran recalculé à chaque image | grande matrice et tuiles figées tant que la feuille est ouverte (`LocalFige`) |

**Banc de fluidité** (Plus › Diagnostic › FLUIDITÉ, `ui/Perf.kt`) : ~35 s sans toucher, le même parcours deux fois — **ancien moteur** (`ModePerf.ancien` reproduit les causes ci-dessus : fond animé point par point, effets du verre recréés, grille non paresseuse, recompositions à chaque image) puis **nouveau**. Trois scènes : repos 3 s, défilement de l'écran principal, défilement de la bibliothèque (90). Mesure avec l'API officielle `Window.addOnFrameMetricsAvailableListener` : nombre d'images réellement dessinées (et par seconde), durée médiane et 95 % d'une image, temps GPU médian, % d'images **en retard** (au-delà de l'échéance `FrameMetrics.DEADLINE`), plus le temps d'ouverture de la bibliothèque et de l'app (lancement à froid). **Écran allumé** pendant la mesure (`FLAG_KEEP_SCREEN_ON`, retiré à la fin ou à l'annulation : avant, la mise en veille coupait le banc). **Complet** (~35 s) ou **Rapide** (~15 s), progression + ✕ Annuler (aussi en haut de l'écran). Résultats en barres (verdict, une carte par scène : % en retard et images/s, ancien vs nouveau), rapport copiable. **Optimisation automatique** d'après le nouveau moteur : repos > 25 i/s avec fond animé → fond coupé ; écran principal > 8 % / > 20 % en retard → verre Équilibré (sans irisation) / Léger (flou et réfraction ×0,6, sans ombre) ; bibliothèque > 8 % → aperçus fixes (le modèle affiché reste animé) ; tout < 3 % → on remonte d'un cran. Chaque changement est listé, avec ↩️ Annuler et 🔁 Re-mesurer. Réglages manuels dans le Diagnostic (`ReglagesUi.qualiteVerre`, `apercusAnimes`). Le rapport est gardé (relisible après coup). Limite : l'« ancien » est reproduit dans la nouvelle mise en page (la bibliothèque y est dans la feuille), pas la v0.1.67 à l'identique.

### 3.10 Menu latéral ☰ (27/10)
Bouton ☰ (verre) à gauche de « Verso ». Panneau de verre (304 dp) posé sur tout l'écran (couche `dessus`), qui glisse depuis la gauche avec un ressort ; voile noir qui s'assombrit avec ; chaque ligne arrive à son tour (fondu + glissé, décalé de 28 ms). Fermer : pousser le panneau vers la gauche, toucher à côté, retour Android. En-tête : la matrice en direct, version, modèle affiché.
Entrées (raccourcis, rien n'y est caché qui ne soit aussi sur l'écran) : Accueil, Tous les modèles, Favoris, Récents, Mes dessins, Créer un dessin · Règles automatiques · Ajouter un widget, Mes widgets · Bouton Glyph (ouvre les détails des pastilles), Choisir le Glyph Toy · Nouveautés, Journal des mises à jour, Diagnostic et fluidité, Matrice figée ? Redémarrer · interrupteur Fond animé.

### 3.11 Journal des mises à jour (27/10)
`app/src/main/assets/journal.json`, la plus récente en premier : `pr`, `version` (null pour l'entrée de tête), `date`, `titre`, **`sert`** (une phrase « Cette mise à jour sert à … »), `sections` (titre + points : tous les détails), `tester` (facultatif).
- **Règle pour chaque PR** : ajouter son entrée en tête avec `"version": null` et remplir la version de l'entrée précédente (= numéro de la Release publiée). Une entrée de tête sans version = la version installée (`BuildConfig.VERSION_NAME`).
- **Menu ☰ › Journal** : feuille de verre, une carte par version (puce de version, date, titre, phrase « sert à » toujours visible, détails dépliables).
- **Nouveautés** : au premier lancement d'une nouvelle version, la feuille « Nouveautés » s'ouvre une fois (préférence `nouveautes_vues`), avec « Compris » et « Tout le journal ».
- **Lisible d'un coup d'œil** (demande de Nico) : `sert` ≤ 1 phrase courte ; `essentiel` = 2 à 5 points très courts qui commencent par un emoji (toujours visibles pour la version actuelle) ; `sections` = détails, repliés par défaut, eux aussi courts avec emoji.
- **Pas de « ⏻ »** dans les textes : la police du téléphone ne l'a pas (boîte vide). Icône `ic_w_marche` ou 🔌.
- **Release GitHub** : notes = l'entrée de tête (`outils/notes_release.py`, 1re ligne = phrase « sert à »). Le bouton « Mettre à jour » de l'app affiche cette phrase (`Release.sert`).

> **Égaliseur réel, réglage du 28/10 (v0.1.96)** : chaque bande est comparée à sa moyenne récente (~1,5 s) : son régulier ≈ 30 % de hauteur, coup (basse, batterie) jusqu'à ~75 %, montée 65 % / descente 20 % par mesure. **Widgets** : le lanceur ne fait que défiler des images toutes prêtes → un modèle de son y est remplacé par l'animation de l'égaliseur d'origine (`Catalogue.resoudreWidget`).

### 3.12 Chrono Glyph (28/10)
Nico : « Nothing le propose déjà mais c'est sous-coté ». `rapide/Chrono` (état en heure murale dans `verso_chrono` : survit au processus et au redémarrage) + modèle **Chrono** (`chrono`, rayon Live, `generateur/ChronoLive`) : MM en haut / SS en bas (HH / MM après 1 h), point qui fait le tour du bord en 1 min (réglage), chiffres qui clignotent en pause. ▶ = démarre et affiche Chrono au dos (choix à la main ; le modèle d'avant est retenu) ; ⏸ = pause ; ↺ = zéro et retour au modèle d'avant. Commandes : widget de la galerie **Chrono** (`Type.CHRONO` = `Chronometer` des RemoteViews, le lanceur fait tourner le temps ; `Type.BOUTONS_CHRONO` = ▶/⏸, ↺ via `WidgetActions.CHRONO / CHRONO_ZERO`), et la fiche du modèle.

### 3.14 Égaliseur : réglage « Mouvement » (28/10)
Nico : « fixe sans musique, animé avec, personnalisable ». Réglage `mouvement` (V1 et V2) : **Au son** (défaut) = barres au repos sans musique, animées avec ; **Toujours** = barres inventées en continu ; **Jamais** = repos. « Musique en cours » = `Musique.joue` (session média ou `AudioManager.isMusicActive`, retombe après 1,2 s). Widgets : `EgaliseurLive.pourWidget` = 1 image ou boucle de 24 images (2,4 s), redessinée quand `Musique.joue` change (signature des widgets). Le widget ne suit pas le rythme exact (le lanceur ne fait que boucler des images) ; au dos, la vraie mesure ([[Spectre]]) ou, sans mesure, des barres inventées.

### 3.13 Taille de l'APK (28/10)
Les MAJ étaient de plus en plus longues : APK 22 Mo (Compose non réduit). **R8** activé (`isMinifyEnabled`, `isShrinkResources`) → ~4 Mo ; `proguard-rules.pro` garde tout `fr.niakimbo.verso.**`, le SDK Glyph (`com.nothing.**`) et Backdrop, sans obscurcir (journaux lisibles). Téléchargement : barre de progression rafraîchie au plus 1 fois par %, `Accept-Encoding: identity`.

## 4. Feuille de route après la V1 (dans cet ordre)

1. **Modèles dynamiques avancés** : minuteur, texte défilant tout fait… (visualiseur musique, notifications et météo réelle faits à l'étape 9).
2. **Modèles automatiques** : en cours (§3.6, étape 9).
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
9. Règles automatiques (§3.6) + Liquid Glass (§3.7). Fait : moteur, 20 déclencheurs, ET/OU, actions, éditeur en phrase ; partie 2 (28/10) : notifications (flash + fondu) et musique ; partie 3 (28/10) : app ouverte, météo, modèles live (§3.6 bis). Wi-Fi par nom abandonné.
