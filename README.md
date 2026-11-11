# Love Letter

Application de terminal avec Textual. Nécessite Python 3.14+ et `uv`.

## Lancement

Depuis la racine du dépôt, lancez le jeu avec `uv`, qui installe aussi les
dépendances du projet.

```bash
uv run loveletter
```

- `c` choisit le nombre de joueurs pour la prochaine partie, de 2 à 6.
- `n` lance une nouvelle partie.
- `q` quitte le jeu.

## Voix

À chaque partie, le jeu choisit les prénoms des bots au hasard et tire les voix
sans remise. Les bots se présentent en français, l’un après l’autre, avant le
début de la manche.

- **Piper** utilise les modèles fournis dans `src/loveletter/voices/` pour les
  voix Tom, SIWIS et les deux voix UPMC. Chaque modèle est chargé à la première
  utilisation et réutilisé en mémoire.
- **Supertonic 3** propose les voix `M1` à `M5` et `F1` à `F5`. À la première
  utilisation d’une de ces voix, le moteur télécharge environ 400 Mo si le
  modèle est absent de son cache (par défaut `~/.cache/supertonic3`). La synthèse
  fonctionne ensuite hors ligne. Le moteur et les profils utilisés sont gardés
  en mémoire. [Fiche du modèle](https://huggingface.co/Supertone/supertonic-3)

Les listes `VOICES_MEN` et `VOICES_WOMEN` définissent les voix disponibles.
La fonction `speak(voice, text)` accepte un identifiant Supertonic ou un couple
`(chemin_du_modèle, identifiant_du_locuteur)` pour Piper.
[`utils.py`](src/loveletter/utils.py)

```python
from loveletter.utils import VOICES_PATH, speak

speak("M4", "À toi de jouer !")
speak((VOICES_PATH / "fr_FR-tom-medium.onnx", None), "Je m'appelle Chochodile !")
```

## Structure

```text
src/loveletter/
├── __init__.py
├── main.py          # Point d’entrée de la commande loveletter
├── loveletter.py    # Application Textual
├── utils.py         # Sélection des voix et synthèse vocale
├── components/     # Plateau, joueurs et cartes
├── style.tcss       # Style de l'interface
├── assets/          # Images et liste des prénoms
└── voices/          # Modèles vocaux Piper et leurs configurations
```
