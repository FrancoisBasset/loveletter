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

Un des onze tests d'IA fait jouer **60 parties jusqu'à la victoire finale** : cinq nombres de joueurs (2 à 6), trois difficultés et quatre graines fixes. Il vérifie après chaque décision :

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

## Contrat de présentation des tours

La présentation ajoutée en 0.2.0 est distincte du moteur. Une action est d'abord validée et appliquée une seule fois par `GameEngine.apply`. Son résumé public (`UiPlayback`) reste ensuite affiché pendant la lecture. **Continuer acquitte ce résumé ; il ne rejoue pas l'action et ne modifie pas les règles.** La sauvegarde associe le résumé en attente à l'état qui résulte déjà de cette action.

| Situation | Condition avant la prochaine décision |
| --- | --- |
| Mode « À mon rythme » | Appui explicite sur Continuer après chaque présentation |
| Mode temporisé | Fin du temps de lecture, sauf pause ou information privée non acquittée |
| Prêtre, Baron ou Roi donnant une information au joueur humain | Lecture explicite de la notice privée ; le temps ne suffit pas à l'acquitter |
| Chancelier humain | Lecture de la carte jouée, puis choix de la carte gardée et de l'ordre des cartes retournées |
| Chancelier IA | Deux transitions du moteur ; la présentation du choix reste générique et ne révèle ni carte gardée ni ordre caché |
| Fin de manche | Acquittement des informations et de la présentation avant de proposer la manche suivante |
| Retour à l'accueil, règles ou encyclopédie | Arrêt du conducteur ; le résumé déjà enregistré reste disponible à la reprise |
| Passage de l'application en arrière-plan | Suspension de la lecture et de la réflexion ; retour sans rejouer l'action ni lever une pause manuelle |
| Reprise après sauvegarde | Lecture du résumé en attente, sans nouvelle application de son action |

Les cartes adverses restent masquées pendant une manche, sauf les connaissances acquises légalement par le joueur humain. Un résumé public peut nommer la carte jouée, la cible, l'annonce du Garde et les cartes effectivement défaussées face visible. Il ne doit contenir aucune carte vue par un Prêtre adverse, comparaison privée à laquelle le joueur humain ne participe pas, ni détail d'un échange ou choix privé de Chancelier. Les révélations de fin de manche appartiennent aux informations publiques prévues par les règles.

La suite de tests métier ci-dessus vérifie le jeu lui-même. Les tests du ViewModel et les parcours instrumentés vérifient séparément la lecture des actions, les pauses, les acquittements et l'affichage du plateau ; leurs résultats de la version publiée sont consignés dans [validation.md](validation.md).

## Couverture de l'application et de sa sauvegarde

La validation locale de la version 0.2.0 confirme **21 tests unitaires d'application réussis** : 15 dans `GameViewModelTest` et 6 dans `SessionStoreTest`, sans échec, erreur ou test ignoré. Avec les 75 tests du moteur inchangé, le total est de **96 tests unitaires réussis**. Commande de l'application : `./gradlew :app:testDebugUnitTest` ; rapports : `app/build/reports/tests/testDebugUnitTest/` et `app/build/test-results/testDebugUnitTest/`.

Les tests `GameViewModelTest` utilisent une horloge virtuelle et des graines d'IA fixes. Ils vérifient les durées de réflexion et de lecture sans attendre le temps réel : le mode guidé reste bloqué même après une minute simulée, le mode lent attend cinq secondes de lecture puis 1,8 seconde de réflexion, et la navigation ou une pause annule le conducteur en cours.

Les cas spécifiques vérifient aussi la reprise d'un résumé déjà commis, le rejet d'une action périmée, le changement d'un rythme automatique vers le mode guidé, la priorité d'une notice privée, le Chancelier humain et les deux étapes du Chancelier IA. Le duel terminal du Baron vérifie ensemble les trois barrières : lire l'information privée, acquitter le résumé public, puis seulement autoriser la manche suivante. Les tests d'arrière-plan couvrent une interruption pendant la lecture, pendant la réflexion et lorsqu'une pause manuelle était déjà active.

`SessionStoreTest` vérifie la sauvegarde atomique, la résistance à une écriture temporaire interrompue, le rejet des paramètres ou formats incompatibles, la reprise d'un Chancelier, le chargement des anciennes sauvegardes de format 1 et la conservation du rythme, de la pause et du dernier résumé public. Les données ajoutées en 0.2.0 sont optionnelles dans les anciennes sauvegardes ; leur valeur par défaut est le mode guidé.

Ces tests du conducteur ne se substituent pas aux tests de règles : ils s'assurent que la présentation ne fait jamais appliquer deux fois une action valide et qu'elle donne le temps nécessaire pour la comprendre.

## Parcours Android instrumentés

`UiSmokeTest` définit trois parcours dans une fenêtre Android de **360 × 800 dp**. Les assertions de plateau vérifient les limites de chaque siège et de la main humaine dans le même écran ; elles ne font pas défiler le plateau pour rendre artificiellement un élément visible. Seul le panneau de décision, volontairement borné et défilable, peut être déplacé pour sélectionner une action.

| Parcours | Vérifications prévues |
| --- | --- |
| Manche guidée | La main reste inactive pendant la lecture ; attendre ne joue aucun tour ; Continuer déclenche une seule progression ; décisions autorisées jusqu'à la fin de manche, puis distribution suivante |
| Accueil et six joueurs | Accueil, règles, encyclopédie, configuration à six joueurs ; tous les sièges et la main visibles avant et après sélection d'une carte |
| Pause en mode automatique | Attendre pendant la pause ne change ni le journal public ni le compteur de décisions IA ; la reprise relance effectivement les adversaires |

Les captures sont écrites pendant les tests et récupérées par Android Gradle Plugin avant désinstallation de l'application. La CI exige les images du plateau à deux et six joueurs, de la carte sélectionnée, de la lecture d'une action IA et de la fin de manche. Les résultats réellement exécutés et le lien du build publié figurent dans [validation.md](validation.md).
