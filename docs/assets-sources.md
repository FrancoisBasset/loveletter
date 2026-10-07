# Provenance des ressources visuelles

Audit du **7 octobre 2026**. L'APK et le dépôt n'embarquent aucune illustration, photo de carte, image de dos, icône, logo, carte Référence ou PDF provenant de Z-Man Games / Asmodee. Les ressources officielles ci-dessous ont été recherchées pour identifier l'édition et les conditions d'utilisation ; leurs liens ne valent pas permission de redistribution.

## Ressources effectivement utilisées

| Ressource | Origine | Emplacement exact |
|---|---|---|
| Présentation des cartes | Composition originale en Kotlin/Compose | `app/src/main/java/fr/francoisbasset/loveletter/ui/CourtComponents.kt`, fonction `PlayingCard` |
| Emblèmes des dix personnages | Dessins géométriques originaux réalisés avec `Canvas` et `Path` | Même fichier, fonction `CourtEmblem` |
| Dos de carte | Dessin original composé de formes et d'une lettre | Même fichier, fonction `CardBack` |
| Jetons et scores | Composants typographiques originaux | Même fichier, fonction `Token` |
| Palette, thème et typographie | Palette originale bordeaux, or, ivoire ; polices système Android | `app/src/main/java/fr/francoisbasset/loveletter/ui/Theme.kt` |
| Icône d'application | Lettre/enveloppe vectorielle originale | `app/src/main/res/drawable/ic_letter.xml` |
| Effets et explications françaises | Rédaction originale des mécanismes vérifiés | `engine/src/main/kotlin/fr/francoisbasset/loveletter/engine/Models.kt` |
| Règles et encyclopédie | Texte propre au projet fondé sur les mécanismes ; aucune page du livret reproduite | Écrans Compose et `docs/rules.md` |

Les composants officiels physiques sont crédités à leurs ayants droit ; les dessins de substitution n'en reproduisent pas les illustrations. Ces substitutions sont jouables et constituent le rendu livré, en attendant d'éventuels droits d'utilisation pour une autre présentation.

## Ressources officielles trouvées, non intégrées

| Catégorie | URL exacte | Constat |
|---|---|---|
| Fiche française, édition avec sac | https://www.asmodee.ca/product/love-letter/ | 2–6 joueurs, 27 cartes dont références, 13 pions |
| Photo française de cartes et pions | https://cdn.svc.asmodee.net/production-asmodeeca/uploads/image-converter/2025/03/ZMGLLS0101FR-LOVE_LETTER-FR-COMPONENTS_2.webp | Garde, Prince, Baron ; noms/valeurs visibles ; six marques d'exemplaires sur le Garde |
| Photo du sac | https://cdn.svc.asmodee.net/production-asmodeeca/uploads/image-converter/2025/03/ZMGLLS0101FR-LOVE_LETTER-FR-COMPONENTS_3.webp | Photographie officielle et logo propriétaire |
| Photo des pions | https://cdn.svc.asmodee.net/production-asmodeeca/uploads/image-converter/2025/03/ZMGLLS0101FR-LOVE_LETTER-FR-COMPONENTS_4.webp | Illustration officielle des pions Faveur |
| Disposition cartes/dos/aide | https://cdn.svc.asmodee.net/production-asmodeeca/uploads/image-converter/2025/03/ZMGLLS0101FR-LOVE_LETTER-FR-LAYOUT.webp | Malgré `FR` dans le nom de fichier, les cartes et aides visibles sont en anglais ; ne pas les présenter comme assets français |
| Fiche française Eco Box | https://www.asmodee.fr/product/love-letter-eco-box/ | Autre conditionnement ; annonce 15 pions ; pas la référence matérielle retenue |
| Photo française Eco Box | https://cdn.svc.asmodee.net/production-asmodeefrv2/uploads/image-converter/2026/04/ZMGLL02_ECLATE01_20250107.webp | Recoupement visuel uniquement ; aucune image intégrée |
| Produit Z-Man et accès au livret | https://www.zmangames.com/game/love-letter/ | Publication officielle |
| Livret officiel anglais actuel | https://cdn.svc.asmodee.net/production-zman/uploads/2026/04/LL_Rulebook_with_Bag.pdf | Livret 16 pages ©2025 ; règles recoupées avec le livret 2019 |
| Lien de livret fourni par Asmodee Belgique | https://ludos.brussels/ludo-cocof/opac_css/doc_num.php?explnum_id=985 | Redirection vers `https://cocof.ludos.brussels/doc_num.php?explnum_id=985` ; échec d'accès lors de l'audit |
| Print & Play officiel | https://print-and-play.asmodee.fun/fr/game/love-letter | Offre de démonstration, pas une licence d'assets pour logiciel |

Le [livret complet anglais ©2019](https://cdn.1j1ju.com/medias/c0/d4/2b-love-letter-2019-rulebook.pdf) et la [démo française ©2019](https://www.ludotheque-ajaccio.fr/Print-play/love-letter_regle_cartes_frpdf.pdf) sont des copies de documents de l'éditeur hébergées par des tiers. Leur provenance et leur usage limité sont détaillés dans `rules.md`. Aucun de ces fichiers, aucune extraction d'image et aucun long extrait de texte n'est inclus au dépôt.

## Conditions de redistribution observées

- [Politique de propriété intellectuelle Z-Man](https://www.zmangames.com/ip-policy/) : elle n'est pas une licence ; elle exclut notamment les applications utilisant la propriété intellectuelle de l'éditeur et les reproductions numériques substantielles de matériel de jeu.
- [Conditions Print & Play](https://print-and-play.asmodee.fun/fr/legal/terms) : usage personnel, absence de droit de partage/redistribution, restrictions sur les adaptations et l'exploitation concurrente.

Ainsi, la gratuité d'un téléchargement officiel et l'accès public à une image ne sont pas traités comme une autorisation d'inclusion dans un dépôt public ou un APK. **Le projet ne revendique aucune licence officielle.** Voir `legal.md` pour le périmètre.

## Correspondance des remplacements

Le projet ne contient pas de fichiers officiels manquants obligatoires : il fonctionne avec ses ressources originales. Si une permission écrite couvrant explicitement l'application et sa distribution est obtenue, le remplacement se fera aux points ci-dessous.

| Visuel concerné | Substitution actuelle à remplacer | Fichier de ressource possible après autorisation |
|---|---|---|
| Espionne, valeur 0 | `CourtEmblem(0)` | `app/src/main/res/drawable/card_spy.webp` |
| Garde, valeur 1 | `CourtEmblem(1)` | `app/src/main/res/drawable/card_guard.webp` |
| Prêtre, valeur 2 | `CourtEmblem(2)` | `app/src/main/res/drawable/card_priest.webp` |
| Baron, valeur 3 | `CourtEmblem(3)` | `app/src/main/res/drawable/card_baron.webp` |
| Servante, valeur 4 | `CourtEmblem(4)` | `app/src/main/res/drawable/card_handmaid.webp` |
| Prince, valeur 5 | `CourtEmblem(5)` | `app/src/main/res/drawable/card_prince.webp` |
| Chancelier, valeur 6 | `CourtEmblem(6)` | `app/src/main/res/drawable/card_chancellor.webp` |
| Roi, valeur 7 | `CourtEmblem(7)` | `app/src/main/res/drawable/card_king.webp` |
| Comtesse, valeur 8 | `CourtEmblem(8)` | `app/src/main/res/drawable/card_countess.webp` |
| Princesse, valeur 9 | `CourtEmblem(9)` | `app/src/main/res/drawable/card_princess.webp` |
| Dos | `CardBack` | `app/src/main/res/drawable/card_back.webp` |
| Aide de jeu | Encyclopédie Compose | `app/src/main/res/drawable/reference_front.webp`, `reference_back.webp` |
| Pion | `Token` | `app/src/main/res/drawable/favor_token.webp` |

Ces noms sont des emplacements proposés et **ne désignent pas des fichiers déjà livrés**. Il faudra adapter le composant Compose concerné, conserver les descriptions accessibles et joindre la permission ainsi que les crédits exigés. Ne pas extraire les ressources depuis une boutique, un scan non autorisé ou une application commerciale.
