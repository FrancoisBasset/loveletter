# Validation de la version 0.3.0

Version préparée le **8 octobre 2026**. Le moteur et les règles françaises Z-Man Games 2019 à 21 cartes restent inchangés.

## Changements contrôlés

- Projection figée des effets : les scènes utilisent les cartes au moment de l’action, avant la pioche du joueur suivant.
- Baron : victoire, défaite et égalité ; deux cartes côte à côte si le joueur participe ; seuls les secrets autorisés sont révélés.
- Prêtre, Roi, Prince, Garde et Chancelier : confidentialité des révélations, échanges, choix et remplacements.
- Compatibilité des anciennes sauvegardes sans scène visuelle ; reprise sans appliquer de nouveau l’effet.
- Actions humaines toujours produites par les actions légales du moteur.

## Vérifications automatisées

Les suites JVM couvrent le moteur, les IA, l’orchestration, la sauvegarde et les nouvelles scènes. Le pipeline compile l’APK Release et les tests instrumentés, exécute Android Lint et bloque la publication si une étape échoue.

Les trois parcours Android existants restent requis : manche complète, plateau à six joueurs et pause/reprise. Quatre nouveaux parcours vérifient le Baron sur un écran 360 × 800 dp, un petit écran 320 × 640 dp, en paysage 640 × 360 dp et dans un duel entre IA. Ils contrôlent la géométrie côte à côte, les faces autorisées, le dos secret et les commandes de lecture.

Les captures requises incluent `duel-baron.png`, `duel-baron-petit.png`, `duel-baron-paysage.png` et `duel-baron-secret.png`, en plus des captures du plateau, de la sélection et de fin de manche. Les captures et rapports réels sont conservés dans les artifacts de [GitHub Actions](https://github.com/FrancoisBasset/loveletter/actions).

## APK

Version **0.3.0**, code **3**, Android 8.0 / API 26 minimum, cible API 35. Build Release non débogable. La Release publie uniquement l’APK du pipeline réussi avec son empreinte SHA-256.

La signature des Releases utilise une clé de développement propre au runner. Une clé différente de celle de l’installation existante oblige à désinstaller l’ancienne application, ce qui efface sa sauvegarde. Le format des sauvegardes v0.2 est compatible si l’application est mise à jour avec la même signature.

## Limites

Les tests d’affichage ciblent les dimensions et la taille de police par défaut ci-dessus. Les animations respectent l’échelle de durée Android ; les tests automatisés vérifient leurs états finaux et leur absence d’effet sur les règles. Ils ne constituent pas une mesure subjective de fluidité sur tous les appareils.

Application hors ligne et non officielle, illustrations vectorielles originales ; aucune ressource graphique officielle embarquée.
