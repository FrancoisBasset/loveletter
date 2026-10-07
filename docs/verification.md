# Vérifications de la version 0.1.0

Date : 7 octobre 2026.

## Moteur et sauvegarde

Résultat local : **58 tests unitaires réussis, zéro échec**.

| Suite | Nombre | Vérifications |
|---|---:|---|
| `GameEngineTest` | 38 | Composition, distribution, effets des dix cartes, protections, éliminations, égalités, scores, manche suivante, sérialisation ; 125 parties complètes simulées |
| `BotAiTest` | 11 | Trois difficultés, choix légaux, informations publiques, probabilités, reproductibilité, absence d’accès aux secrets ; 60 parties complètes |
| `EngineInvariantTest` | 6 | 75 parties complètes supplémentaires, conservation des 21 cartes, confidentialité, reprise à chaque transition, actions invalides, victoires simultanées |
| `GameRepositoryTest` | 3 | Absence initiale de sauvegarde, écriture/remplacement atomique, restauration des secrets non lus, fichier corrompu conservé |

Les 260 parties simulées couvrent tous les nombres de joueurs autorisés et les trois difficultés. Les tests ciblés couvrent notamment le Prince utilisant la réserve, la Princesse défaussée, le Chancelier en fin de pioche, les deux ordres de retour, la Comtesse obligatoire, l’Espionne d’un joueur éliminé et les gagnants conjoints.

## Compilation Android

Compilation réelle avec JDK 17, Gradle 8.13, AGP 8.9.3, Kotlin 2.1.20, SDK 35. APK debug, APK release et APK de tests instrumentés générés. La signature APK v2 du paquet release a été vérifiée avec `apksigner`. Le paquet release est non débogable ; sa signature est celle de développement décrite dans le README.

```bash
./gradlew :engine:test :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
```

## Parcours instrumentés

`UiSmokeTest` contient trois parcours avec de véritables actions Compose :

1. Accueil → règles → encyclopédie → détail du Chancelier → accueil.
2. Configuration à six joueurs, IA difficile, graine choisie et entrée dans la partie.
3. Sélection et jeu d’une Servante, réponse des IA, pause, contrôle du fichier enregistré, recréation de l’activité et reprise de la même partie.

La CI exécute ces tests sur un émulateur Android 10/API 29 avant de publier. Le statut de l’exécution et les rapports font foi : [GitHub Actions](https://github.com/FrancoisBasset/loveletter/actions). Elle conserve aussi des captures réelles de l’accueil, de la configuration et du jeu dans l’artefact `verification-reports`.

Une compilation ne remplace pas une validation sur toutes les tailles d’écran ou tous les téléphones. La version cible API 35 et prend en charge API 26 et suivants ; les parcours automatisés n’affirment pas couvrir toutes ces versions d’Android.
