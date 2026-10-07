# Love Letter · Cour

Application Android native en **Kotlin / Jetpack Compose / Material 3**, intégralement en français et jouable hors ligne. Un joueur humain affronte **1 à 5 IA**, avec trois niveaux de difficulté. Les parties comportent plusieurs manches, des scores et une victoire finale ; l’état est sauvegardé après chaque décision.

Adaptation indépendante et non officielle. Love Letter et les marques associées appartiennent à leurs ayants droit. Le projet n’inclut aucune illustration, carte scannée, mise en page, logo ni règle officielle reproduite intégralement.

## Télécharger

- [Dernière release](https://github.com/FrancoisBasset/loveletter/releases/latest)
- APK de la première version : `loveletter-v0.1.0.apk`.
- Android **8.0 / API 26** minimum. Aucune permission réseau, aucun compte et aucune publicité.

Ouvrir l’APK sur Android et autoriser l’installation depuis l’application utilisée pour le télécharger. L’APK de cette préversion est signé avec un certificat de développement ; voir la section Signature.

## Édition et règles

Une seule édition : **Love Letter, édition française Z-Man Games 2019, 21 cartes Personnage, 2 à 6 joueurs**, incluant l’Espionne et le Chancelier. La variante classique à 16 cartes n’est pas implémentée. Sources, nomenclature et vérifications détaillées : [docs/rules.md](docs/rules.md).

| Valeur | Carte | Exemplaires |
|---:|---|---:|
| 0 | Espionne | 2 |
| 1 | Garde | 6 |
| 2 | Prêtre | 2 |
| 3 | Baron | 2 |
| 4 | Servante | 2 |
| 5 | Prince | 2 |
| 6 | Chancelier | 2 |
| 7 | Roi | 1 |
| 8 | Comtesse | 1 |
| 9 | Princesse | 1 |

Points essentiels :

- Une carte est écartée face cachée ; à deux joueurs, trois autres sont écartées face visible.
- À son tour, le joueur pioche puis joue une carte. L’application réalise automatiquement la pioche.
- La Servante protège jusqu’au début du prochain tour. Les joueurs éliminés et les adversaires protégés ne sont jamais des cibles proposées.
- Sans adversaire ciblable, Garde/Prêtre/Baron/Roi sont joués sans effet ; le Prince impose de se cibler.
- Le Prince utilise la carte écartée quand la pioche est vide. Défausser la Princesse élimine sans repioche.
- Le Chancelier permet de choisir la carte gardée **et l’ordre** des cartes remises au fond. Remettre la Princesse au fond ne l’active pas.
- La Comtesse est obligatoire avec le Roi ou le Prince lors du choix de carte normal, mais pas pendant la résolution du Chancelier.
- La fin de manche attend la résolution complète de l’effet. À égalité de valeur maximale, **chaque ex æquo gagne un pion**, sans somme des défausses.
- L’unique survivant ayant joué ou défaussé une Espionne gagne un bonus d’un pion, indépendamment du résultat de la manche.
- Objectifs : **6/5/4/3/3** pions à **2/3/4/5/6** joueurs. Les victoires simultanées sont possibles.
- Le gagnant de manche commence la suivante ; tirage au sort entre gagnants en cas d’égalité. Le bonus Espionne seul ne donne pas le premier tour.

Le premier joueur est choisi à la configuration pour transposer la règle de la dernière lettre manuscrite. Le choix aléatoire proposé est une commodité numérique explicite. Aucun effet de carte ni seuil de victoire ne change avec la difficulté.

## Jouer

Depuis l’accueil, créer une partie, choisir le nombre de joueurs et la difficulté. Dans sa main, toucher une carte pour lire son effet et son explication. Les choix affichés sont dérivés des actions légales du moteur : cible, annonce du Garde ou résolution du Chancelier. Les révélations privées restent affichées jusqu’à validation. Après élimination, observer les IA finir la manche puis passer à la suivante.

Les adversaires montrent leur score, leur protection, leur défausse publique et les informations que vous connaissez légitimement. L’historique permet de relire les actions. Les règles et l’encyclopédie des dix personnages sont accessibles depuis l’accueil et pendant la partie. Revenir à l’accueil met les IA en pause ; « Reprendre » restitue la sauvegarde.

## Architecture

```text
engine/
  engine/Models.kt         Cartes, état, actions, observation filtrée
  engine/GameEngine.kt     Transitions immuables et règles
  engine/ai/BotAi.kt       Stratégies sans accès à GameState
  src/test/               Effets, invariants, simulations et confidentialité
app/
  GameViewModel.kt         StateFlow, orchestration, délais Coroutines
  persistence/            Sauvegarde atomique dans le stockage privé
  ui/                     Écrans Compose, thème et cartes originales
docs/                     Sources, règles, ressources et release
```

Le module `engine` est du Kotlin/JVM pur, sans Android. `GameEngine.apply` refuse une action absente de `legalActions`. L’interface ne décide pas des règles : elle choisit parmi ces actions. Le `ViewModel` conserve l’état complet ; les Composables et les IA reçoivent une `PlayerObservation` filtrée. Même la graine du mélange est masquée dans cette observation, pour empêcher de reconstruire le paquet secret.

Le hasard du moteur utilise un état pseudo-aléatoire sérialisable. `GameConfig(seed = 1234)` reproduit le même mélange et les départages. Les tests peuvent donc rejouer exactement un scénario. Les décisions des IA utilisent une graine indépendante dérivée d’informations publiques.

### IA

- **Facile** : choisit parmi les actions autorisées avec une part importante de hasard.
- **Normale** : tient compte des cartes visibles, de la valeur conservée et des informations connues.
- **Difficile** : évalue les probabilités conditionnelles des cartes restantes et les conséquences des différentes actions ; raisonnement heuristique, sans promesse de jeu optimal.

`BotAi` ne reçoit ni pioche ordonnée, ni carte écartée cachée, ni mains adverses inconnues. Ses choix sont toujours issus de `observation.legalActions`. Les informations privées sont adressées uniquement à leurs destinataires ; les connaissances certaines sont invalidées quand une main change.

### Persistance

La sauvegarde comprend le paquet, la réserve, les mains, le score, le tour, la phase Chancelier éventuelle, l’état du hasard et une révélation encore non acquittée. Écriture atomique dans le répertoire privé de l’application, après chaque transition. Les IA s’arrêtent quand l’application passe en arrière-plan. Aucun secret n’est placé dans les logs ni envoyé sur un réseau. La sauvegarde Android dans le cloud est désactivée. Le format est versionné pour cette préversion ; une migration sera nécessaire si le modèle sérialisé change.

## Ouvrir et compiler

Prérequis : **JDK 17**, Android Studio compatible avec **AGP 8.9.3**, SDK Android **35**, Build Tools **35.0.0**. Versions épinglées : Kotlin **2.1.20**, Gradle **8.13**, Compose BOM **2025.04.01**. Le wrapper Gradle officiel et sa somme SHA-256 sont inclus.

```bash
git clone https://github.com/FrancoisBasset/loveletter.git
cd loveletter
# Ouvrir ce répertoire dans Android Studio, laisser synchroniser Gradle.
./gradlew :engine:test :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Sur Windows, utiliser `gradlew.bat`. En ligne de commande, définir `ANDROID_HOME` ou renseigner le chemin SDK dans `local.properties` (fichier ignoré par Git).

APK : `app/build/outputs/apk/debug/app-debug.apk` ou `app/build/outputs/apk/release/app-release.apk`.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Tests et publication continue

Le workflow [.github/workflows/android.yml](.github/workflows/android.yml) installe le SDK, exécute les tests du moteur et de l’application, effectue le lint, compile réellement l’APK et conserve les rapports. Sur la branche principale, la première exécution réussie de cette version crée le tag et la GitHub Release `v0.1.0`, avec l’APK et sa somme SHA-256. Une release existante n’est jamais écrasée automatiquement. Pour une nouvelle publication, incrémenter la version dans Gradle et le workflow, puis mettre à jour ses notes.

Trois tests de parcours Compose sont également exécutés sur un émulateur Android avant publication : navigation, configuration à six joueurs et jeu/sauvegarde/reprise. Les captures d’écran et résultats sont joints aux artefacts de la CI. Détail des **58 tests unitaires** et des **260 parties simulées** : [docs/verification.md](docs/verification.md).

La création de Release exige le droit GitHub Actions `contents: write` déclaré dans le job. Les contributions via pull request sont compilées et testées sans publication.

### Signature

La version `0.1.0` est une préversion installable, compilée en **release non débogable** et signée par la configuration de développement Android. Elle n’est pas destinée à une boutique. La clé privée n’est pas versionnée. Un certificat différent peut imposer de désinstaller l’ancienne application avant installation, avec perte de la sauvegarde. Pour des mises à jour durables, configurer une clé privée de production conservée par le propriétaire et ses secrets CI, puis remplacer `signingConfig`.

## Ressources et limites

Les cartes, dos de carte, ornements et icône sont des créations vectorielles/code natives originales. Les descriptions sont rédigées pour cette application. Les ressources officielles repérées sont **référencées uniquement**, car leur mise à disposition publique ne permet pas de conclure à une licence de redistribution dans un APK ou sur GitHub. [docs/assets-sources.md](docs/assets-sources.md) indique leur provenance et les remplacements possibles après autorisation.

Cette version propose le jeu solo contre IA ; elle ne comprend pas de multijoueur réseau ni de mode humain partagé. Les IA sont des stratégies heuristiques. Les règles officielles intégrales et les illustrations de l’éditeur ne sont pas embarquées.
