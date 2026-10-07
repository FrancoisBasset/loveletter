# Intelligence artificielle et confidentialité

L'IA reçoit uniquement `Observation`. L'API `AiPlayer.choose(observation, difficulty, seed)` ne reçoit jamais `GameState` : elle ne peut lire ni la pioche, ni la réserve, ni les mains adverses cachées. Elle choisit exclusivement dans `observation.legalActions`, produites par le moteur.

## Trois niveaux

- **Facile** : tire une carte autorisée au hasard, puis une cible et une annonce autorisées. Ce choix en plusieurs étapes évite de surreprésenter les Gardes, dont les annonces multiplient le nombre d'actions possibles. Elle choisit aussi au hasard la carte conservée par le Chancelier.
- **Normal** : compte les exemplaires de chaque personnage encore non observés, prend en compte les exclusions publiques issues des annonces de Garde et les connaissances personnelles encore valides du Prêtre ou du Roi. Elle évalue l'élimination, le risque du Baron, l'information du Prêtre, la protection, l'amélioration de main et le bonus d'Espionne.
- **Difficile** : conditionne conjointement les distributions des mains adverses aux nombres d'exemplaires restants. Une carte unique ne peut appartenir à plusieurs adversaires à la fois. Elle tient compte davantage du risque de conserver la Princesse, du score des adversaires, du dernier tour et de l'ordre des cartes replacées par le Chancelier.

## Comment les probabilités sont obtenues

Le paquet initial contient les 21 cartes de la seule édition Z-Man Games 2019. On retire du modèle probabiliste la main de l'IA, les défausses publiques, les trois cartes exposées dans une partie à deux joueurs et les cartes adverses personnellement connues. Une connaissance n'est exploitée que si elle appartient au `viewerId` de l'observation. Le moteur invalide les connaissances et exclusions devenues périmées.

En mode difficile, les affectations possibles des cartes encore inconnues aux adversaires sont énumérées. Une affectation est pondérée par le nombre d'exemplaires physiques disponibles à chaque étape. Une affectation incompatible avec une exclusion publique n'est pas retenue. Les poids donnent les probabilités marginales de chaque main adverse.

Pour une future pioche, les nombres moyens de cartes occupées par ces mains sont soustraits du pool inconnu. La réserve reste inconnue et aucun ordre de pioche n'est utilisé. Le Chancelier est évalué avant sa pioche à partir de ce modèle ; l'IA ne choisit sa carte conservée qu'après la phase privée réellement résolue par le moteur.

## Stratégie et limites

Les scores de décision sont des heuristiques explicites, pas un solveur optimal. L'évaluation du dernier tour multiplie certaines probabilités marginales ; cette approximation ne conserve pas toutes leurs corrélations. Les modes normal et difficile ont donc des comportements différents sans promettre que le difficile gagnera chaque partie.

Le choix aléatoire utilise une graine Kotlin locale. Pour les niveaux normal et difficile, elle ne départage que des actions de score égal. Une même observation, une même difficulté et une même graine donnent la même action. Ce générateur ne modifie jamais la graine de mélange du moteur.

Aucune simulation ne contourne les protections ou les obligations de Comtesse : ces règles sont appliquées en amont par la liste des actions légales. Le niveau facile peut volontairement commettre une erreur stratégique légale, comme jouer la Princesse ; les niveaux supérieurs pénalisent fortement cette auto-élimination.
