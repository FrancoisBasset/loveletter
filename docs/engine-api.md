# Contrat du moteur Kotlin

Package `fr.francoisbasset.loveletter.core`, module JVM indépendant `core`.

## Entrées

`GameEngine.newGame(GameConfig(playerCount, difficulty, humanName, firstPlayer), seed)` crée une partie. `firstPlayer` est un indice, ou `null` pour un tirage au sort. L'ordre des sièges est celui du tour. `GameEngine.apply(state, action)` crée un nouvel état et rejette une action absente de `legalActions(state)` par `IllegalArgumentException`.

`GameAction.Play(card, target = null, guess = null)` joue une carte. `GameAction.ChancellorChoice(keep, bottom)` choisit la carte conservée et les cartes à placer sous la pioche, dans l'ordre du sommet vers le fond. `GameAction.NextRound` est proposé entre les manches, hors victoire finale.

`GameEngine.observe(state, viewerId)` est la projection à utiliser dans l'interface et l'IA. `AiPlayer.choose(observation, difficulty, seed)` n'accepte jamais un `GameState`.

## États

`GamePhase`: `PLAYING`, `CHANCELLOR`, `ROUND_OVER`, `GAME_OVER`.

`GameState` contient `config`, `players`, `drawPile`, `reserveCard`, `faceUpRemoved`, `currentPlayer`, `roundNumber`, `phase`, `result`, `winners`, `randomState`, `journal`, `knownCards`, `exclusions`, `privateNotes`. Cet état complet, sérialisable avec kotlinx.serialization, contient des secrets et doit rester dans le ViewModel / la persistance.

`PlayerState`: `id`, `name`, `hand`, `discard`, `isProtected`, `isEliminated`, `score`.

`Observation` contient `viewerId`, `config`, `players` (projections `PlayerView`), `ownHand`, `knownCards`, `exclusions`, `privateNotes`, `legalActions`, `deckSize`, `faceUpRemoved`, `currentPlayer`, `roundNumber`, `phase`, `result`, `winners`, `journal`. Les mains adverses ne sont présentes dans `PlayerView.visibleHand` qu'après la fin de manche. `handSize` est public. Les actions de jeu sont uniquement proposées au joueur actif. Le ViewModel peut appliquer `NextRound` après la consultation des résultats.

`RoundResult`: `winners`, `spyBonusPlayer`, `reason` (`LAST_SURVIVOR` / `HIGHEST_CARD`), `revealedHands`, `pointsAwarded`.

`Card` enum: `ESPIONNE`, `GARDE`, `PRETRE`, `BARON`, `SERVANTE`, `PRINCE`, `CHANCELIER`, `ROI`, `COMTESSE`, `PRINCESSE`; propriétés `value`, `frenchName`, `copies`, `effect`, `explanation`.

`Difficulty`: `EASY`, `NORMAL`, `HARD`.

## Règles importantes

Édition française Z-Man Games ©2019: 21 cartes, 2–6 joueurs. Seuils de victoire: 6 / 5 / 4 / 3 / 3 faveurs pour 2 / 3 / 4 / 5 / 6 joueurs. Une égalité finale de manche donne un point à chacun, sans somme des défausses. Le bonus d'Espionne revient à l'unique joueur encore actif en ayant joué ou défaussé une ou plusieurs. Le Chancelier est une phase privée en deux étapes: ses choix ne sont calculés qu'après sa pioche effective, jamais en regardant la pioche dans la génération d'actions ordinaires.

Les états et le générateur aléatoire sont déterministes et sérialisables. Aucune fonction de présentation ne doit résoudre un effet.
