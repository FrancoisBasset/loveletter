# Référentiel de règles

Vérification documentaire : **7 octobre 2026**. Édition de référence : **Love Letter, nouvelle édition Z-Man Games 2019, édition française, 21 cartes Personnage, 2 à 6 joueurs**. Les éditions historiques à 16 cartes, Premium et les adaptations thématiques ne sont pas des références de ce moteur. Cette note décrit les mécanismes avec une rédaction originale ; elle ne reproduit pas le livret.

## Sources et périmètre de vérification

| Source | Ce qui a été vérifié | Statut |
|---|---|---|
| [Asmodee Canada — Love Letter](https://www.asmodee.ca/product/love-letter/) | Édition française Z-Man, 2–6 joueurs, 27 cartes au total (21 Personnage + 6 Référence), 13 pions, sac | Site officiel |
| [Asmodee Belgique — Love Letter FR](https://www.asmodee.be/product/love-letter-fr/) | Même contenu et lien de téléchargement des règles | Site officiel |
| [Livret Z-Man 2019, Rules of Play](https://cdn.1j1ju.com/medias/c0/d4/2b-love-letter-2019-rulebook.pdf) | Toutes les règles du paquet de 21 cartes ; mention ©2019, crédits et 16 pages du document éditeur | Document primaire officiel, copie hébergée par 1jour-1jeu ; anglais |
| [Z-Man — page actuelle](https://www.zmangames.com/game/love-letter/) et son [livret actuel](https://cdn.svc.asmodee.net/production-zman/uploads/2026/04/LL_Rulebook_with_Bag.pdf) | Recoupement du paquet, des effets et des cas particuliers ; cette réimpression porte ©2025 | Site et fichier officiels ; anglais |
| [Démo française Z-Man ©2019](https://www.ludotheque-ajaccio.fr/Print-play/love-letter_regle_cartes_frpdf.pdf) | Les dix noms français et leurs valeurs, y compris Chancelier et Espionne explicitement rattachés à l'édition intégrale | Document primaire éditeur, miroir d'une ludothèque ; **nomenclature seulement** |

Le [lien de règles fourni par Asmodee Belgique](https://ludos.brussels/ludo-cocof/opac_css/doc_num.php?explnum_id=985) redirige vers [cocof.ludos.brussels](https://cocof.ludos.brussels/doc_num.php?explnum_id=985), inaccessible pendant cette vérification. **Le livret français intégral original n'a donc pas pu être lu sur cet hébergement.** Les mécanismes ont été contrôlés dans le livret complet original anglais 2019 et les noms français dans le document français de Z-Man. La démo française est un paquet réduit : ses quantités, son nombre de joueurs et ses règles ne sont pas importés dans le moteur.

Une transcription française trouvée chez Ludomia n'a pas été retenue comme référence : elle annonce 21 cartes tout en indiquant seulement cinq Gardes, ce qui produit 20 cartes. Le livret complet Z-Man indique bien **six Gardes**. Les scans de boutiques et les adaptations de fans ne sont pas utilisés.

## Paquet unique

| Valeur | Nom français | Identifiant Kotlin | Exemplaires | Fonction |
|---:|---|---|---:|---|
| 0 | Espionne | `SPY` | 2 | Bonus de fin de manche sous condition |
| 1 | Garde | `GUARD` | 6 | Annonce d'un personnage adverse ; Garde interdit |
| 2 | Prêtre | `PRIEST` | 2 | Consultation privée d'une main adverse |
| 3 | Baron | `BARON` | 2 | Comparaison privée de deux mains |
| 4 | Servante | `HANDMAID` | 2 | Protection temporaire contre le ciblage adverse |
| 5 | Prince | `PRINCE` | 2 | Défausse puis remplacement d'une main |
| 6 | Chancelier | `CHANCELLOR` | 2 | Choix d'une carte et retour sous la pioche |
| 7 | Roi | `KING` | 1 | Échange des mains |
| 8 | Comtesse | `COUNTESS` | 1 | Jeu obligatoire avec Roi ou Prince |
| 9 | Princesse | `PRINCESS` | 1 | Élimination si jouée ou défaussée |

Total : **21**, indépendamment du nombre de participants. La sélection à deux joueurs n'est pas un mode à 16 cartes.

## Cycle d'une manche

1. Mélanger les 21 cartes. En écarter une face cachée, inconnue de tous.
2. À deux joueurs, écarter trois cartes supplémentaires face visible. Elles restent consultables mais n'appartiennent à la défausse d'aucun joueur.
3. Distribuer une carte par personne. Lors du tour, retirer l'ancienne protection de la personne active, puis lui faire piocher une carte.
4. Jouer une des deux cartes, la rendre publique et résoudre son effet entièrement. La carte conservée demeure secrète.
5. Passer au prochain participant encore en lice ; les joueurs éliminés attendent la manche suivante.

Dans le jeu physique, la première manche commence par la personne ayant écrit une lettre manuscrite le plus récemment. L'application solo démarre avec l'humain ; le moteur expose `GameConfig.firstPlayer` pour modifier ce choix. C'est une adaptation de mise en place, pas une nouvelle règle de carte.

## Cibles et effets

Garde, Prêtre, Baron et Roi exigent **un autre joueur encore en lice et non protégé**. L'interface n'offre que les cibles légales. Si aucune n'existe, la carte se joue sans effet ; on ne choisit pas de cible protégée et on ne s'autocible pas. Le Prince accepte également le joueur actif. Si tous les adversaires sont protégés, son seul choix légal est lui-même. Un adversaire éliminé n'est jamais ciblable.

- **Garde** : tous les personnages sauf Garde peuvent être annoncés, y compris Espionne. Une bonne annonce élimine la cible ; une mauvaise annonce ne révèle pas sa carte. Annoncer une carte devenue improbable reste légal.
- **Prêtre** : seule la personne qui le joue voit la carte de la cible.
- **Baron** : comparer la carte conservée après avoir joué le Baron, pas la valeur du Baron joué. Les deux participants connaissent les cartes comparées ; les autres ne voient que les conséquences publiques. La valeur inférieure est éliminée. Une égalité n'élimine personne.
- **Servante** : sa protection expire au début du prochain tour de son propriétaire. Elle n'empêche pas de participer à la comparaison finale des mains.
- **Prince** : la cible défausse sa carte face visible, sans déclencher l'effet ordinaire de cette carte. Si c'est une Princesse, elle est éliminée et ne pioche pas de remplacement. Sinon, elle prend la carte supérieure du paquet ; si celui-ci est vide, elle prend la carte réservée face cachée au début. Une Espionne ainsi défaussée peut compter pour son bonus ultérieur.
- **Chancelier** : prendre jusqu'à deux cartes de la pioche, conserver une carte parmi toute sa main et remettre les autres sous le paquet, dans un ordre choisi et sans les révéler. Une carte disponible implique un retour ; zéro carte disponible signifie aucun effet. Le retour sous la pioche n'est pas une défausse : on peut y remettre la Princesse sans être éliminé. L'obligation de la Comtesse ne s'applique pas pendant ce choix intermédiaire.
- **Roi** : échanger les cartes conservées ; elles ne deviennent pas publiques. Les protections et défausses appartiennent aux joueurs et ne sont pas échangées.
- **Comtesse** : si l'autre carte de la main normale est Roi ou Prince, elle est l'unique carte jouable. Elle peut aussi être jouée volontairement avec toute autre carte. Le moteur ne publie pas si le choix était forcé.
- **Princesse** : la jouer ou la défausser élimine immédiatement son propriétaire. Lorsqu'une élimination survient, la main restante est défaussée publiquement sans résoudre d'effet supplémentaire.
- **Espionne** : aucun effet immédiat. À la fin, considérer uniquement les survivants ayant joué ou défaussé une Espionne. S'il existe exactement un tel joueur, il gagne un pion bonus. Deux Espionnes chez ce même joueur ne donnent qu'un bonus. Une Espionne encore en main, retirée à la mise en place, chez un éliminé ou remise dans le paquet ne lui vaut pas ce bonus.

## Fin de manche et score

Un unique survivant termine immédiatement la manche et gagne un pion Faveur. Sinon, on vérifie l'épuisement de la pioche **après la résolution complète du tour**. Les survivants dévoilent alors leur carte ; toutes les personnes détenant la plus grande valeur gagnent chacune un pion. **Aucune somme des défausses ne départage les égalités** dans cette édition.

Le bonus Espionne est ajouté indépendamment de cette victoire. Un joueur peut donc gagner deux pions, ou gagner le seul bonus sans avoir remporté la manche. Le prochain premier joueur est le vainqueur de la manche ; en cas de victoire partagée, le moteur choisit au hasard parmi les vainqueurs. Le bénéficiaire du seul bonus Espionne n'entre pas dans ce tirage.

| Participants au début de la partie | Pions nécessaires pour gagner |
|---:|---:|
| 2 | 6 |
| 3 | 5 |
| 4 | 4 |
| 5 ou 6 | 3 |

Ces seuils ne changent pas avec les éliminations. Le résultat de partie est calculé après l'ensemble des points de la manche. Plusieurs personnes peuvent atteindre leur seuil ensemble : elles gagnent ensemble, sans départage ajouté.

## Informations et hasard

Le moteur connaît l'état nécessaire à la simulation. Les IA et l'interface reçoivent une observation filtrée : main du joueur concerné, informations publiques, et informations privées acquises par des effets auxquels il a participé. Elles ne reçoivent ni ordre de pioche, ni réserve, ni mains adverses inconnues. Une observation ancienne n'autorise pas à connaître une carte nouvellement piochée ou choisie par un Chancelier.

La graine du moteur rend le mélange et les choix aléatoires reproductibles pour les tests. La conservation de cet état aléatoire permet aussi une reprise de partie cohérente.
