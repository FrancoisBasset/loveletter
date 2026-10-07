# Love Letter — Android, français

Application **indépendante et non officielle**, en Kotlin et Jetpack Compose. Jouez hors ligne contre une à cinq IA, dans une partie complète avec plusieurs manches, faveurs et victoire finale.

## Édition implémentée

Une seule édition : **Love Letter, Z-Man Games, édition française révisée de 2019, 21 cartes Personnage, 2 à 6 joueurs**. Ni l'ancienne édition Filosofia, ni le paquet de démonstration à 16 cartes, ni les adaptations thématiques ne définissent les règles du moteur.

Les noms français ont été vérifiés dans un document officiel Z-Man ©2019 en français. La composition et les règles complètes ont été contrôlées dans le livret original ©2019 et comparées au livret distribué actuellement par l'éditeur. Les liens, les limites des sources et les anomalies écartées sont consignés dans [docs/rules-sources.md](docs/rules-sources.md).

| Valeur | Personnage | Nombre |
| ---: | --- | ---: |
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

Le moteur applique la réserve cachée, les trois cartes visibles retirées à deux joueurs, le ciblage et les protections, les éliminations, la Comtesse obligatoire, les choix secrets du Chancelier et l'ordre de retour sous le paquet. Le Prince utilise la réserve lorsque le paquet est vide. Une égalité de fin de manche attribue une faveur à chacun des gagnants : **aucun départage par la somme des défausses**. Le bonus d'Espionne est indépendant du gain de manche. Les victoires simultanées sont possibles.

| Joueurs | Faveurs pour gagner |
| ---: | ---: |
| 2 | 6 |
| 3 | 5 |
| 4 | 4 |
| 5 ou 6 | 3 |

La personne ayant écrit une lettre manuscrite le plus récemment commence normalement la première manche ; la configuration permet de désigner son siège. Le gagnant précédent commence la suivante, avec tirage au sort parmi les gagnants en cas d'égalité.

## Jouer

Installez l'APK de la [Release v0.2.0](https://github.com/FrancoisBasset/loveletter/releases/tag/v0.2.0) sur Android 8.0 ou plus récent. Android peut demander d'autoriser l'installation depuis l'application qui ouvre le fichier.

Depuis l'accueil, configurez le nombre de joueurs, la difficulté, le premier joueur et le rythme. Le plateau affiche tous les joueurs ensemble, la pioche, les défausses publiques, les protections et les faveurs. Votre main reste en bas de l'écran. La carte jouée est présentée avec son auteur, sa cible et le résultat public de son effet.

Le rythme **À mon rythme**, choisi par défaut, attend **Continuer** après chaque action. Vous gardez ainsi le temps de lire et de regarder le plateau avant le tour suivant. **Lent** et **Fluide** enchaînent automatiquement les actions après une période de lecture ; **Pause** permet de suspendre ce déroulement. Le rythme est indépendant de la difficulté des IA.

À votre tour, sélectionnez une carte, consultez son effet puis choisissez parmi les seules actions autorisées. Le Garde demande un personnage à annoncer. Le Chancelier propose la carte à conserver et l'ordre des cartes replacées sous le paquet. Les résultats privés restent privés et attendent votre acquittement. Après une élimination, vous pouvez suivre la suite de la manche au même rythme. Une sauvegarde locale conserve aussi l'action en cours de lecture, afin de reprendre sans sauter un tour.

L'encyclopédie présente les dix personnages, leurs valeurs, leurs quantités et des explications reformulées. L'écran des règles explique la préparation, le tour, les effets et le score.

## Architecture

```text
core/                       Module Kotlin/JVM indépendant d'Android
  src/main/kotlin/.../core/  Modèles, règles, moteur et IA
  src/test/kotlin/.../core/  Tests déterministes du moteur et des IA
app/                        Application Android
  src/main/java/.../        ViewModel, persistance et interface Compose
  src/main/res/             Icône et styles originaux
docs/                       Sources, règles, assets, API et validation
.github/workflows/          Tests, compilation, APK et publication
```

Les Composables rendent une projection de l'état et envoient des actions au ViewModel. Les effets des cartes, les actions légales, la fin des manches et le score appartiennent au moteur. Le ViewModel orchestre les tours des IA et leur présentation avec Coroutines, puis expose l'interface par StateFlow. Un effet n'est appliqué qu'une fois : la lecture et le bouton Continuer contrôlent sa présentation et le départ du tour suivant. L'état complet est sérialisable ; le générateur aléatoire est déterministe et son état est sauvegardé.

**Les IA ne reçoivent jamais l'état secret complet.** Leur entrée est une `Observation` contenant leur propre main, les défausses publiques, les cartes retirées visibles, les connaissances acquises légitimement et les actions autorisées. Les informations devenues périmées lors d'un changement de main sont invalidées.

- **Facile** : choix partiellement aléatoires parmi les actions légales.
- **Normale** : prise en compte des informations connues, des cartes publiques et du risque des actions.
- **Difficile** : estimation des probabilités restantes et évaluation plus poussée des effets, des faveurs et de la survie.

Le contrat précis du moteur se trouve dans [docs/engine-api.md](docs/engine-api.md).

## Android Studio et compilation

Ouvrez la racine du dépôt dans Android Studio et synchronisez Gradle. Prérequis : **JDK 17**, SDK Android **35**, Build Tools **35.0.0**. Le Gradle Wrapper **8.13** est fourni avec son empreinte de distribution. AGP **8.9.2**, Kotlin **2.1.20**, Compose BOM **2025.04.01** ; les versions sont figées dans les fichiers Gradle.

```bash
./gradlew :core:test :app:testDebugUnitTest
./gradlew :app:lintRelease :app:assembleRelease
```

Sous Windows, utilisez `gradlew.bat`. L'APK installable est produit dans :

```text
app/build/outputs/apk/release/app-release.apk
```

Le fichier `local.properties`, contenant le chemin du SDK propre à votre machine, n'est pas versionné. Android Studio le crée automatiquement.

### Signature

Les Releases téléchargeables sont des builds **Release non débogables**, signés avec le certificat de développement Android du runner. Elles s'installent directement ; elles ne sont pas destinées à Google Play. Les runners ne conservent pas cette clé entre les versions : pour installer la v0.2.0 si la v0.1.0 est déjà présente, désinstallez l'ancienne application, ce qui efface sa sauvegarde locale. La clé de développement ne constitue pas une garantie d'identité de l'éditeur.

Pour signer vos builds avec une clé durable personnelle, définissez les quatre variables d'environnement suivantes avant la compilation :

```text
LOVELETTER_KEYSTORE        chemin vers votre fichier .jks
LOVELETTER_STORE_PASSWORD  mot de passe du magasin
LOVELETTER_KEY_ALIAS       alias de la clé
LOVELETTER_KEY_PASSWORD    mot de passe de la clé
```

Aucune clé ni aucun mot de passe n'est stocké dans le dépôt. La configuration Gradle utilise ces variables si elles sont toutes présentes ; sinon elle utilise la signature de développement. Pour une distribution durable, conserver la même clé, protéger ses mots de passe et adapter la CI avec des secrets GitHub.

## Intégration continue et Releases

[Le workflow Android](.github/workflows/android.yml) exécute les tests unitaires, Android Lint et la compilation réelle. Il vérifie ensuite le parcours de jeu et la navigation dans un émulateur Android 35 : partie sauvegardée, manche complète, redistribution, configuration, règles, encyclopédie et visibilité du plateau. Les captures et rapports sont conservés comme artifacts. **La Release n'est créée qu'après la réussite de ces deux validations.** L'APK et son empreinte SHA-256 sont publiés comme artifacts puis joints à la Release correspondant à `versionName`, avec l'APK nommé `loveletter-v0.2.0.apk`. Une Release existante n'est jamais remplacée automatiquement.

Pour une nouvelle version, augmentez `versionName` et `versionCode` dans `app/build.gradle.kts`, mettez à jour les notes de Release, puis poussez sur `master`. Les pull requests sont compilées et testées sans publication.

## Ressources graphiques et droits

**Aucune carte, illustration, photographie, page de livret ou logo officiel n'est redistribué.** Les sources publiques officielles trouvées n'accordent pas une licence pour les incorporer dans cette application. Les Print & Play sont limités à l'usage personnel. La politique communautaire de Z-Man exclut les applications utilisant leur propriété intellectuelle.

Les visuels embarqués sont des remplacements originaux : cartes Compose avec symboles vectoriels, dos géométrique, enveloppe de lancement, compteur de faveurs et habillage bordeaux/ivoire/or. Les textes explicatifs sont reformulés. Les icônes Material sont des composants génériques sous licence Apache 2.0. [docs/assets-sources.md](docs/assets-sources.md) liste la provenance, les restrictions et les fichiers à remplacer si une autorisation explicite est obtenue.

Ce projet ne revendique aucune licence d'adaptation officielle ou de marque. **Love Letter est un jeu de Seiji Kanai ; Love Letter et Z-Man Games appartiennent à leurs ayants droit.** L'application n'est ni affiliée ni approuvée par Asmodee ou Z-Man Games.

## Validation

Le rapport de validation et le périmètre testé sont conservés dans [docs/validation.md](docs/validation.md). Les scénarios du moteur utilisent des graines fixes ; les tests d'IA comprennent des parties complètes et des vérifications de confidentialité des observations.
