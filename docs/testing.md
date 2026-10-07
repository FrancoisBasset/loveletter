# Vérification du moteur et des IA

## Résultat vérifié

**75 tests unitaires JVM réussis, 0 échec, 0 erreur, 0 test ignoré.**

- `GameEngineTest` : 64 tests.
- `AiPlayerTest` : 11 tests.
- Commande : `./gradlew :core:test`.
- Rapports générés : `core/build/reports/tests/test/index.html` et `core/build/test-results/test/`.

Le workflow GitHub exécute les mêmes tests avant de produire l'APK. Ces résultats concernent le moteur, les observations, les décisions d'IA et la sérialisation des parties ; ils ne remplacent pas la vérification visuelle sur un téléphone.

## Cas vérifiés

| Domaine | Vérifications |
| --- | --- |
| Édition | Dix noms français, valeurs 0–9, multiplicité exacte, paquet de 21 cartes, 2–6 joueurs, seuils de victoire |
| Préparation | Réserve cachée, trois cartes retirées visibles à deux joueurs, distribution, pioche du joueur actif, premier siège choisi, graines reproductibles |
| Garde | Bonne et mauvaise annonce, Espionne autorisée, Garde interdit, exclusion publique après échec |
| Ciblage | Adversaire seulement lorsque requis, protection, éliminés, jeu sans effet faute de cible, Prince sur soi |
| Prêtre / Baron / Roi | Informations privées, victoire et défaite du Baron, égalité, échanges et invalidation des anciennes connaissances |
| Servante | Protection effective jusqu'au début du prochain tour, expiration avant nouvelle décision |
| Prince | Défausse sans résolution d'un effet actif, Princesse sans nouvelle pioche, réserve quand le paquet est vide, Espionne défaussée donnant droit au bonus |
| Comtesse / Princesse | Comtesse forcée avec Prince ou Roi et autorisée volontairement ; Princesse éliminant son propriétaire et défaussant la carte restante |
| Chancelier | Pioche de zéro, une ou deux cartes, ordre des cartes retournées, paquet temporairement vide, choix privé, cartes identiques, rejet des choix inventés, exemption de la Comtesse |
| Fin de manche | Résolution du dernier tour, unique survivant, carte de valeur maximale, égalités partagées indépendamment des défausses |
| Espionne / scores | Bonus indépendant du gain de manche, un seul bonus avec deux Espionnes, éliminés exclus du calcul, Espionne en main sans bonus, victoire finale simultanée |
| Manche suivante | Scores conservés, joueurs réintégrés, défausses réinitialisées, gagnant précédent premier, tirage parmi les gagnants ex æquo, bonus Espionne ne donnant pas le premier tour |
| Persistance | Aller-retour JSON d'une partie, reprise pendant un choix de Chancelier, préservation du hasard et du prochain mélange après égalité |
| IA | Trois difficultés, décisions légales et reproductibles, exploitation d'une information de Prêtre et des cartes publiques, protection et Comtesse imposée, absence de dépendance aux secrets inconnus |

## Simulations de parties complètes

Un test supplémentaire fait jouer **60 parties jusqu'à la victoire finale** : cinq nombres de joueurs (2 à 6), trois difficultés et quatre graines fixes. Il vérifie après chaque décision :

1. La conservation des 21 cartes et de leur multiplicité dans toutes les zones du jeu.
2. L'absence de carte en main chez les joueurs éliminés.
3. La main de deux cartes et le statut actif du siège qui doit jouer.
4. L'appartenance de la décision d'IA aux actions autorisées.
5. La terminaison de chaque partie avant 600 décisions.
6. La correspondance exacte entre les vainqueurs finaux et les joueurs ayant atteint le seuil officiel.

Les scénarios unitaires de bord utilisent des instantanés réduits pour isoler une interaction précise. Ils sont construits dans les tests ; les parties produites par le moteur sont, elles, soumises à l'invariant intégral des 21 cartes pendant les simulations.

## Contrôle de confidentialité

L'IA reçoit uniquement une `Observation`, sans référence au `GameState`, à l'ordre de la pioche ou à la réserve. Les tests modifient des mains adverses inconnues, inversent l'ordre caché du paquet et remplacent la réserve, puis vérifient que l'observation du même joueur et ses décisions restent identiques. Les tests du Prêtre, du Baron, du Roi et du Chancelier contrôlent également que les autres sièges ne reçoivent ni cartes privées ni actions du joueur actif.

Les heuristiques d'IA ne prétendent pas résoudre parfaitement ce jeu à information incomplète. La difficulté normale exploite les cartes visibles et les connaissances légitimes ; la difficulté difficile conditionne davantage ses probabilités sur les exemplaires encore disponibles et évalue les fins de manche.
