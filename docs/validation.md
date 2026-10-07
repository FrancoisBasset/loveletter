# Validation de la version 0.1.0

Vérification le **7 octobre 2026**. Les rapports exécutables et leurs résultats sont disponibles dans [GitHub Actions](https://github.com/FrancoisBasset/loveletter/actions). La Release est bloquée tant que les jobs `build` et `android-ui` ne sont pas tous deux réussis.

## Moteur et IA

**75 tests unitaires exécutés et réussis** : 64 pour les règles et le moteur, 11 pour les IA. Aucun échec, erreur ou test ignoré. Les tests d'IA incluent **60 parties complètes** avec graines fixes : cinq nombres de joueurs, trois niveaux et quatre graines. À chaque action, les tests vérifient la légalité, la conservation des 21 cartes, les phases et la progression jusqu'à la victoire finale.

Les scénarios spécifiques couvrent tous les personnages, les cibles interdites, les protections, les éliminations, la réserve, la dernière pioche, les choix et l'ordre du Chancelier, les égalités, les faveurs et les victoires simultanées. Une permutation des secrets inconnus ne change ni l'observation ni la décision de l'IA à graine identique. Les snapshots sérialisés reprennent les décisions privées et le tirage aléatoire sans nouvelle pioche.

Voir [testing.md](testing.md) pour le détail des tests.

## Application

**Six tests unitaires supplémentaires exécutés et réussis** couvrent la sauvegarde atomique, une écriture interrompue, les formats incompatibles, la reprise d'un Chancelier et le conducteur des IA : annulation en quittant la table, reprise sans double tour et rejet d'une action périmée. Le total est donc de **81 tests unitaires réussis**, sans échec, erreur ou test ignoré.

Deux tests Android instrumentés vérifient une manche réellement jouée depuis une sauvegarde déterministe, la distribution suivante, l'accueil, la configuration jusqu'à six joueurs, les règles et l'encyclopédie. Ils s'exécutent dans un émulateur Android 35 accéléré sous GitHub Actions. Des captures PNG sont produites dans les artifacts pour vérifier la présentation.

## Compilation et installation

Un APK Release a été **réellement compilé** dans l'environnement de développement, avec JDK 17, Gradle 8.13, AGP 8.9.2 et SDK 35. Sa signature APK v2 a été vérifiée. Métadonnées :

| Propriété | Valeur |
| --- | --- |
| Application ID | `fr.francoisbasset.loveletter` |
| Version | `0.1.0` (code 1) |
| Android minimal | 8.0 / API 26 |
| SDK cible | API 35 |
| Build | Release, non débogable |
| Signature initiale | Certificat Android de développement |
| Taille approximative | 11 Mio |

La CI recompile le même projet, exécute les tests et Android Lint, puis publie exclusivement l'APK de ce build réussi dans la Release. Le fichier `SHA256SUMS.txt` joint à la Release correspond aux octets de l'APK publié.

## Limites pratiques

- Les visuels officiels ne sont pas redistribués : les assets de remplacement sont originaux et leur provenance est documentée.
- Les IA sont des heuristiques ; le niveau difficile utilise des probabilités plus élaborées, sans prétendre résoudre optimalement le jeu.
- La première signature permet une installation directe hors Google Play. Une autre clé de signature empêchera la mise à jour par-dessus cette version et nécessitera sa désinstallation.
- La sauvegarde est locale et privée à l'application. Il n'y a ni réseau multijoueur, ni compte, ni synchronisation distante.
- Le test du parcours sur émulateur vérifie un environnement Android 35 ; il ne remplace pas des essais sur tous les modèles de téléphone et toutes les tailles de police.
