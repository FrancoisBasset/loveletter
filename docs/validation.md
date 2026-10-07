# Validation de la version 0.2.0

Vérification locale le **7 octobre 2026**. Le moteur et l'édition française Z-Man Games 2019 à 21 cartes sont inchangés ; cette version refait le plateau et la présentation des tours. Les rapports de la compilation publiée sont conservés dans [GitHub Actions](https://github.com/FrancoisBasset/loveletter/actions). La Release attend la réussite des jobs `build` et `android-ui`.

## Tests unitaires

**96 tests unitaires réussis**, sans échec, erreur ni test ignoré :

| Suite | Tests |
| --- | ---: |
| `GameEngineTest` | 64 |
| `AiPlayerTest` | 11 |
| `GameViewModelTest` | 15 |
| `SessionStoreTest` | 6 |

Les 75 résultats du moteur inchangé ont été réutilisés depuis le cache Gradle ; les 21 tests d'application ont été exécutés sur le code de cette version. La CI contrôle à nouveau les mêmes suites avant publication.

Les tests d'IA comprennent **60 parties complètes** à graines fixes : cinq nombres de joueurs, trois niveaux et quatre graines. Chaque décision doit être légale et conserver les 21 cartes jusqu'à la victoire finale. Les cas spécifiques couvrent les dix personnages, les protections, les éliminations, les égalités, les faveurs et les connaissances privées. Une permutation des secrets inconnus ne change ni l'observation ni la décision d'IA à graine identique.

Les tests du ViewModel contrôlent la lecture guidée, les durées automatiques, la pause, la navigation, l'arrière-plan et la reprise sans double application d'une action. Ils couvrent la priorité des informations privées, le Chancelier humain, les deux transitions du Chancelier IA et un duel terminal du Baron. Les tests de sauvegarde contrôlent l'écriture atomique, les interruptions, les formats et paramètres incompatibles, ainsi que la conservation du rythme, de la pause et du résumé déjà commis.

Voir [testing.md](testing.md) pour les scénarios et leurs limites.

## Parcours Android

Trois parcours instrumentés ont été **compilés localement**. Leur exécution dans un émulateur Android 35 accéléré est obligatoire dans le job `android-ui` avant publication :

- Manche guidée réellement jouée : aucune action supplémentaire sans Continuer, fin de manche et distribution suivante.
- Accueil, règles, encyclopédie et plateau à six joueurs : chaque siège et la main humaine entièrement visibles dans une fenêtre de 360 × 800 dp, y compris pendant un choix de carte.
- Pause en mode automatique : journal et décisions IA inchangés pendant l'attente, puis progression réelle après reprise.

Les captures sont récupérées avant désinstallation de l'application. La CI exige les images du plateau à deux et six joueurs, de la carte sélectionnée, de la lecture d'une action IA et de la fin de manche. La vérification locale ne revendique pas leur exécution : les résultats et les captures de l'émulateur figurent dans les artifacts du workflow publié.

## Compilation et APK

Le build local a terminé avec **`BUILD SUCCESSFUL`**, en exécutant les tâches suivantes :

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:assembleDebugAndroidTest
```

Android Lint : **0 erreur et 1 avertissement** `DataExtractionRules`, portant sur la déclaration des sauvegardes Android 12 et suivantes. Les APK Release et de test instrumenté ont été générés. Outils : JDK 17, Gradle 8.13, AGP 8.9.2 et SDK 35.

La signature APK v2 du fichier local a été vérifiée. Métadonnées :

| Propriété | Valeur |
| --- | --- |
| Application ID | `fr.francoisbasset.loveletter` |
| Version | `0.2.0` (code 2) |
| Android minimal | 8.0 / API 26 |
| SDK cible | API 35 |
| Build | Release, non débogable |
| Signature | Certificat Android de développement |
| Taille locale | 11 381 872 octets, environ 10,9 Mio |

La CI recompile le projet, contrôle les tests et le parcours Android, puis publie l'APK de ce build réussi sous le nom `loveletter-v0.2.0.apk`. Son certificat de développement et son empreinte peuvent différer du build local. Le fichier `SHA256SUMS.txt` joint à la Release permet de contrôler les octets effectivement publiés.

## Limites pratiques

- Les visuels officiels ne sont pas redistribués ; les assets de remplacement sont originaux et leur provenance est documentée.
- Les IA sont des heuristiques. Le niveau difficile utilise davantage de probabilités, sans prétendre résoudre optimalement le jeu.
- La clé privée de la v0.1.0 n'a pas été conservée. Pour installer la v0.2.0 si l'ancienne version est présente, il faut désinstaller celle-ci, ce qui efface sa sauvegarde locale. Les Releases actuelles utilisent une signature de développement ; une distribution durable nécessite une clé conservée et protégée.
- La sauvegarde est locale à l'application. Il n'y a ni réseau multijoueur, ni compte, ni synchronisation distante.
- Les parcours instrumentés ciblent Android 35 avec une taille de fenêtre et une taille de police déterminées. Ils ne remplacent pas des essais sur tous les modèles et réglages d'accessibilité.
